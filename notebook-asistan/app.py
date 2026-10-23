"""Notebook Asistan - Gemini Notebook (NotebookLM) defterlerine tarayıcı açmadan soru sorar.

Arayüz: pywebview (Edge WebView2) + ui/index.html
Otomatik güncelleme: github.com/gokhangeo/rea sürümleri (etiket: notebook-v*)
"""
import asyncio
import json
import os
import shutil
import subprocess
import sys
import threading
import time
from pathlib import Path

os.environ.setdefault("NOTEBOOKLM_HL", "tr")

APP = "Notebook Asistan"
DEFAULT_NB = "3f678b22-546d-4b4c-a4a4-770ea15ff52c"
HOME = Path.home() / ".notebooklm"
STORAGE = HOME / "profiles" / "default" / "storage_state.json"
CFG = HOME / "notebook_asistan.json"
LOGIN_LOG = HOME / "notebook_asistan_login.log"
APP_LOG = HOME / "notebook_asistan.log"

TAG_PREFIX = "notebook-v"
ASSET_NAME = "NotebookAsistan.exe"
UPDATE_API = os.environ.get("NBA_UPDATE_URL",
                            "https://api.github.com/repos/gokhangeo/rea/releases?per_page=50")
BASE = Path(getattr(sys, "_MEIPASS", os.path.dirname(os.path.abspath(__file__))))


def log(msg):
    try:
        HOME.mkdir(parents=True, exist_ok=True)
        with open(APP_LOG, "a", encoding="utf-8") as f:
            f.write(time.strftime("%Y-%m-%d %H:%M:%S ") + str(msg) + "\n")
    except Exception:
        pass


def read_version():
    try:
        return (BASE / "version.txt").read_text(encoding="utf-8").strip() or "dev"
    except OSError:
        return "dev"


APP_VERSION = read_version()


def vtuple(s):
    try:
        return tuple(int(x) for x in s.strip().split("."))
    except (ValueError, AttributeError):
        return None


def load_cfg():
    try:
        return json.loads(CFG.read_text(encoding="utf-8"))
    except Exception:
        return {}


def save_cfg(cfg):
    try:
        HOME.mkdir(parents=True, exist_ok=True)
        CFG.write_text(json.dumps(cfg, ensure_ascii=False), encoding="utf-8")
    except Exception:
        pass


# ------------------------------------------------------------------ login (alt süreç)
def run_login_mode():
    HOME.mkdir(parents=True, exist_ok=True)
    f = open(LOGIN_LOG, "w", encoding="utf-8")
    sys.stdout = sys.stderr = f
    sys.argv = ["notebooklm", "login", "--browser", "chrome", "--browser-timeout", "600"]
    from notebooklm.notebooklm_cli import main
    code = 0
    try:
        main()
    except SystemExit as e:
        code = int(e.code or 0) if not isinstance(e.code, str) else 1
    f.flush()
    os._exit(code)


def self_cmd(*args):
    if getattr(sys, "frozen", False):
        return [sys.executable, *args]
    return [sys.executable, os.path.abspath(__file__), *args]


# ------------------------------------------------------------------ güncelleme
def find_update():
    import urllib.request
    req = urllib.request.Request(UPDATE_API, headers={"Accept": "application/vnd.github+json",
                                                      "User-Agent": "NotebookAsistan"})
    with urllib.request.urlopen(req, timeout=20) as r:
        releases = json.load(r)
    cur = vtuple(APP_VERSION)
    best = None
    for rel in releases:
        tag = rel.get("tag_name", "")
        if rel.get("draft") or rel.get("prerelease") or not tag.startswith(TAG_PREFIX):
            continue
        v = vtuple(tag[len(TAG_PREFIX):])
        asset = next((a for a in rel.get("assets", []) if a.get("name") == ASSET_NAME), None)
        if v and asset and cur and v > cur and (best is None or v > best[0]):
            best = (v, {"tag": tag, "version": tag[len(TAG_PREFIX):],
                        "notes": (rel.get("body") or "").strip(),
                        "url": asset["browser_download_url"], "size": asset["size"]})
    return best[1] if best else None


def finish_update_cleanup():
    exe = sys.executable
    old = os.path.splitext(exe)[0] + ".eski.exe"

    def worker():
        for _ in range(60):
            try:
                if os.path.exists(old):
                    os.remove(old)
                return
            except OSError:
                time.sleep(0.5)
    if getattr(sys, "frozen", False):
        threading.Thread(target=worker, daemon=True).start()


# ------------------------------------------------------------------ async arka uç
class Backend:
    def __init__(self):
        self.loop = asyncio.new_event_loop()
        threading.Thread(target=self.loop.run_forever, daemon=True).start()
        self.ctx = None
        self.client = None

    def run(self, coro, timeout=300):
        return asyncio.run_coroutine_threadsafe(coro, self.loop).result(timeout)

    async def _connect(self):
        from notebooklm import NotebookLMClient
        if self.ctx is not None:
            try:
                await self.ctx.__aexit__(None, None, None)
            except Exception:
                pass
            self.ctx = self.client = None
        ctx = NotebookLMClient.from_storage(str(STORAGE))
        self.client = await ctx.__aenter__()
        self.ctx = ctx
        nbs = await self.client.notebooks.list()
        return [{"id": n.id, "title": n.title, "sources": getattr(n, "sources_count", 0) or 0}
                for n in nbs]

    def connect(self):
        return self.run(self._connect(), 90)


def is_auth_error(e):
    name = type(e).__name__
    s = str(e).lower()
    return ("Auth" in name or "login" in s or "auth" in s or "cookie" in s
            or "401" in s or "403" in s or "expired" in s)


# ------------------------------------------------------------------ JS API
class Api:
    def __init__(self):
        self._b = Backend()
        self._cfg = load_cfg()
        self._window = None
        self._update = None
        self._login_lock = threading.Lock()

    # yardımcılar
    def _js(self, code):
        try:
            if self._window:
                self._window.evaluate_js(code)
        except Exception:
            pass

    def _status(self, text):
        self._js(f"window.ui && ui.splashStatus({json.dumps(text)})")

    def _login(self):
        with self._login_lock:
            self._status("Google hesabına giriş yapılıyor… Açılan pencerede oturum aç.")
            rc = subprocess.call(self_cmd("--login"), creationflags=0x08000000)
            log(f"login rc={rc}")
            return rc == 0 and STORAGE.exists()

    # ---- JS tarafından çağrılanlar
    def boot(self):
        info = {"version": APP_VERSION, "theme": self._cfg.get("theme", "dark"),
                "autoask": os.environ.get("NBA_AUTOASK", "")}
        for attempt in range(2):
            if not STORAGE.exists() or attempt == 1:
                if not self._login():
                    return {**info, "ok": False,
                            "error": "Giriş tamamlanamadı. Tekrar denemek için uygulamayı yeniden aç."}
            try:
                self._status("Defterlerine bağlanılıyor…")
                nbs = self._b.connect()
                sel = self._cfg.get("notebook", DEFAULT_NB)
                if not any(n["id"] == sel for n in nbs) and nbs:
                    sel = nbs[0]["id"]
                return {**info, "ok": True, "notebooks": nbs, "selected": sel}
            except Exception as e:
                log(f"connect: {type(e).__name__}: {e}")
                if attempt == 0:
                    continue
                return {**info, "ok": False, "error": f"Bağlanılamadı: {e}"}

    def select(self, nb):
        self._cfg["notebook"] = nb
        save_cfg(self._cfg)
        return True

    def set_theme(self, theme):
        self._cfg["theme"] = theme
        save_cfg(self._cfg)
        return True

    def suggest(self, nb):
        try:
            s = self._b.run(self._b.client.notebooks.suggest_prompts(nb), 60)
            return [{"title": x.title, "prompt": x.prompt} for x in s][:6]
        except Exception as e:
            log(f"suggest: {e}")
            return []

    def ask(self, nb, question):
        for attempt in range(2):
            try:
                r = self._b.run(self._b.client.chat.ask(nb, question), 300)
                refs = {}
                for ref in (r.references or []):
                    n = getattr(ref, "citation_number", None)
                    if n is not None and n not in refs:
                        refs[n] = (ref.cited_text or "").strip()
                nxt = [getattr(x, "question", "") for x in (getattr(r, "next_steps", None) or [])]
                return {"ok": True, "answer": r.answer or "", "refs": refs,
                        "next": [q for q in nxt if q][:3]}
            except Exception as e:
                log(f"ask: {type(e).__name__}: {e}")
                if attempt == 0 and is_auth_error(e):
                    if self._login():
                        try:
                            self._b.connect()
                            continue
                        except Exception as e2:
                            e = e2
                return {"ok": False, "error": str(e) or type(e).__name__}

    def new_chat(self, nb):
        async def job():
            cid = await self._b.client.chat.get_conversation_id(nb)
            if cid:
                await self._b.client.chat.delete_conversation(nb, cid)
        try:
            self._b.run(job(), 60)
        except Exception as e:
            log(f"new_chat: {e}")
        return True

    def copy(self, text):
        try:
            import ctypes
            CF_UNICODETEXT = 13
            u32, k32 = ctypes.windll.user32, ctypes.windll.kernel32
            k32.GlobalAlloc.restype = ctypes.c_void_p
            k32.GlobalLock.restype = ctypes.c_void_p
            k32.GlobalLock.argtypes = [ctypes.c_void_p]
            k32.GlobalUnlock.argtypes = [ctypes.c_void_p]
            u32.SetClipboardData.argtypes = [ctypes.c_uint, ctypes.c_void_p]
            data = text.encode("utf-16-le") + b"\0\0"
            h = k32.GlobalAlloc(0x0042, len(data))
            p = k32.GlobalLock(h)
            ctypes.memmove(p, data, len(data))
            k32.GlobalUnlock(h)
            u32.OpenClipboard(0)
            u32.EmptyClipboard()
            u32.SetClipboardData(CF_UNICODETEXT, h)
            u32.CloseClipboard()
            return True
        except Exception as e:
            log(f"copy: {e}")
            return False

    def check_update(self):
        if not getattr(sys, "frozen", False) and not os.environ.get("NBA_UPDATE_TEST"):
            return None
        try:
            self._update = find_update()
        except Exception as e:
            log(f"update check: {e}")
            self._update = None
        if not self._update:
            return None
        u = self._update
        return {"version": u["version"], "notes": u["notes"][:600],
                "size": round(u["size"] / 1048576, 1), "current": APP_VERSION}

    def do_update(self):
        u = self._update
        if not u or not getattr(sys, "frozen", False):
            return {"ok": False, "error": "Güncelleme bulunamadı"}
        try:
            import urllib.request
            exe = sys.executable
            dest = os.path.splitext(exe)[0] + ".yeni.exe"
            part = dest + ".part"
            req = urllib.request.Request(u["url"], headers={"User-Agent": "NotebookAsistan"})
            done, last = 0, 0
            with urllib.request.urlopen(req, timeout=120) as r, open(part, "wb") as f:
                while True:
                    chunk = r.read(256 * 1024)
                    if not chunk:
                        break
                    f.write(chunk)
                    done += len(chunk)
                    pct = int(done * 100 / max(1, u["size"]))
                    if pct != last:
                        last = pct
                        self._js(f"ui.updateProgress({pct})")
            if os.path.getsize(part) != u["size"]:
                os.remove(part)
                raise RuntimeError("indirilen dosya eksik")
            os.replace(part, dest)
            old = os.path.splitext(exe)[0] + ".eski.exe"
            if os.path.exists(old):
                os.remove(old)
            os.replace(exe, old)
            try:
                os.replace(dest, exe)
            except OSError:
                os.replace(old, exe)
                raise
            log(f"güncellendi -> {u['tag']}")
            self._cfg["just_updated"] = u["version"]
            save_cfg(self._cfg)
            subprocess.Popen([exe], close_fds=True,
                             env=dict(os.environ, PYINSTALLER_RESET_ENVIRONMENT="1"),
                             creationflags=0x00000008 | 0x00000200)
            threading.Timer(0.8, lambda: self._window.destroy()).start()
            return {"ok": True}
        except Exception as e:
            log(f"update: {e}")
            return {"ok": False, "error": str(e)}

    def just_updated(self):
        v = self._cfg.pop("just_updated", None)
        if v:
            save_cfg(self._cfg)
        return v


def main():
    if "--login" in sys.argv:
        run_login_mode()
    import webview
    finish_update_cleanup()
    api = Api()
    title = APP if APP_VERSION == "dev" else f"{APP}  v{APP_VERSION}"
    win = webview.create_window(title, str(BASE / "ui" / "index.html"), js_api=api,
                                width=1080, height=680, min_size=(720, 480),
                                background_color="#0e0f14", text_select=True)
    api._window = win
    HOME.mkdir(parents=True, exist_ok=True)
    webview.start(gui="edgechromium", private_mode=False,
                  storage_path=str(HOME / "webview"), debug=bool(os.environ.get("NBA_DEBUG")))


if __name__ == "__main__":
    main()
