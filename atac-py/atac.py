"""Ataç - Windows için yüzen sürükle-bırak rafı (Python sürümü).

Electron sürümüyle aynı görünümü hedefler ama saf Python + Win32 (ctypes) +
Pillow ile çalışır; içinde Chromium YOKTUR, bu yüzden antivirüslerin Electron
paketlerine taktığı yanlış alarmlara takılmaz.

Mimari: tek bir katmanlı (WS_EX_LAYERED) pencere. Tüm arayüz Pillow ile RGBA
görüntü olarak çizilir ve UpdateLayeredWindow ile piksel-başı saydamlıkla ekrana
basılır (gerçek buzlu-cam görünümü). Tıklama/sürükleme olayları pencerenin kendi
WndProc'unda ele alınır. Dosya bırakma OLE IDropTarget ile (mandalina'daki
kanıtlanmış yöntem). Tepsi simgesi pystray ile. GitHub'dan kendini günceller.
"""
import ctypes
import ctypes.wintypes as wt
import json
import os
import shutil
import subprocess
import sys
import threading
import time
import traceback

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont, ImageOps

# ---------------------------------------------------------------------------
# Günlük (sorun olursa Müdürüm bu dosyayı gönderebilsin)
# ---------------------------------------------------------------------------
APPDATA = os.path.join(os.environ.get("APPDATA", os.path.expanduser("~")), "Atac")
os.makedirs(APPDATA, exist_ok=True)
LOG = os.path.join(APPDATA, "atac.log")


def log(msg):
    try:
        with open(LOG, "a", encoding="utf-8") as f:
            f.write(time.strftime("%Y-%m-%d %H:%M:%S ") + str(msg) + "\n")
    except OSError:
        pass


def log_err():
    log(traceback.format_exc())


# ---------------------------------------------------------------------------
# Win32 ctypes kurulumu
# ---------------------------------------------------------------------------
user32 = ctypes.WinDLL("user32", use_last_error=True)
gdi32 = ctypes.WinDLL("gdi32", use_last_error=True)
kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
shell32 = ctypes.WinDLL("shell32", use_last_error=True)
ole32 = ctypes.WinDLL("ole32", use_last_error=True)

LRESULT = ctypes.c_ssize_t
ULONG_PTR = ctypes.c_size_t
WNDPROC = ctypes.WINFUNCTYPE(LRESULT, wt.HWND, wt.UINT, wt.WPARAM, wt.LPARAM)

WS_POPUP = 0x80000000
WS_EX_LAYERED = 0x00080000
WS_EX_TOPMOST = 0x00000008
WS_EX_TOOLWINDOW = 0x00000080
WS_EX_NOACTIVATE = 0x08000000
SWP_NOSIZE = 0x0001
SWP_NOMOVE = 0x0002
SWP_NOACTIVATE = 0x0010
SWP_SHOWWINDOW = 0x0040
HWND_TOPMOST = -1
ULW_ALPHA = 0x00000002
AC_SRC_OVER = 0x00
AC_SRC_ALPHA = 0x01
SW_HIDE = 0
SW_SHOWNOACTIVATE = 4

WM_DESTROY = 0x0002
WM_CLOSE = 0x0010
WM_TIMER = 0x0113
WM_LBUTTONDOWN = 0x0201
WM_LBUTTONUP = 0x0202
WM_LBUTTONDBLCLK = 0x0203
WM_RBUTTONUP = 0x0205
WM_MOUSEMOVE = 0x0200
WM_MOUSELEAVE = 0x02A3
WM_MOUSEWHEEL = 0x020A
WM_HOTKEY = 0x0312
WM_APP = 0x8000
WM_ATAC_SHOW = WM_APP + 1      # tepsiden "göster/gizle"
WM_ATAC_DROP = WM_APP + 2      # OLE kuyruğunda bırakılan dosya var
WM_ATAC_QUIT = WM_APP + 3
WM_ATAC_UPDATED = WM_APP + 4   # güncelleme indirildi

CS_DBLCLKS = 0x0008
IDC_ARROW = 32512
WHEEL_DELTA = 120

MOD_CONTROL = 0x0002
MOD_SHIFT = 0x0004
VK_SPACE = 0x20
HOTKEY_ID = 0xA7AC


class POINT(ctypes.Structure):
    _fields_ = [("x", ctypes.c_long), ("y", ctypes.c_long)]


class SIZE(ctypes.Structure):
    _fields_ = [("cx", ctypes.c_long), ("cy", ctypes.c_long)]


class RECT(ctypes.Structure):
    _fields_ = [("left", ctypes.c_long), ("top", ctypes.c_long),
                ("right", ctypes.c_long), ("bottom", ctypes.c_long)]


class BLENDFUNCTION(ctypes.Structure):
    _fields_ = [("BlendOp", ctypes.c_ubyte), ("BlendFlags", ctypes.c_ubyte),
                ("SourceConstantAlpha", ctypes.c_ubyte), ("AlphaFormat", ctypes.c_ubyte)]


class WNDCLASS(ctypes.Structure):
    _fields_ = [("style", wt.UINT), ("lpfnWndProc", WNDPROC), ("cbClsExtra", ctypes.c_int),
                ("cbWndExtra", ctypes.c_int), ("hInstance", wt.HINSTANCE), ("hIcon", wt.HICON),
                ("hCursor", wt.HANDLE), ("hbrBackground", wt.HBRUSH),
                ("lpszMenuName", wt.LPCWSTR), ("lpszClassName", wt.LPCWSTR)]


class BITMAPINFOHEADER(ctypes.Structure):
    _fields_ = [("biSize", wt.DWORD), ("biWidth", ctypes.c_long), ("biHeight", ctypes.c_long),
                ("biPlanes", wt.WORD), ("biBitCount", wt.WORD), ("biCompression", wt.DWORD),
                ("biSizeImage", wt.DWORD), ("biXPelsPerMeter", ctypes.c_long),
                ("biYPelsPerMeter", ctypes.c_long), ("biClrUsed", wt.DWORD),
                ("biClrImportant", wt.DWORD)]


class TRACKMOUSEEVENT(ctypes.Structure):
    _fields_ = [("cbSize", wt.DWORD), ("dwFlags", wt.DWORD), ("hwndTrack", wt.HWND),
                ("dwHoverTime", wt.DWORD)]


class MONITORINFO(ctypes.Structure):
    _fields_ = [("cbSize", wt.DWORD), ("rcMonitor", RECT), ("rcWork", RECT), ("dwFlags", wt.DWORD)]


user32.DefWindowProcW.restype = LRESULT
user32.DefWindowProcW.argtypes = [wt.HWND, wt.UINT, wt.WPARAM, wt.LPARAM]
user32.CreateWindowExW.restype = wt.HWND
user32.CreateWindowExW.argtypes = [wt.DWORD, wt.LPCWSTR, wt.LPCWSTR, wt.DWORD, ctypes.c_int,
                                   ctypes.c_int, ctypes.c_int, ctypes.c_int, wt.HWND, wt.HMENU,
                                   wt.HINSTANCE, wt.LPVOID]
user32.UpdateLayeredWindow.argtypes = [wt.HWND, wt.HDC, ctypes.POINTER(POINT), ctypes.POINTER(SIZE),
                                       wt.HDC, ctypes.POINTER(POINT), wt.DWORD,
                                       ctypes.POINTER(BLENDFUNCTION), wt.DWORD]
user32.SetWindowPos.argtypes = [wt.HWND, wt.HWND, ctypes.c_int, ctypes.c_int, ctypes.c_int,
                                ctypes.c_int, wt.UINT]
user32.GetDC.restype = wt.HDC
user32.GetDC.argtypes = [wt.HWND]
user32.ReleaseDC.argtypes = [wt.HWND, wt.HDC]
user32.GetCursorPos.argtypes = [ctypes.POINTER(POINT)]
user32.SetTimer.argtypes = [wt.HWND, ULONG_PTR, wt.UINT, wt.LPVOID]
user32.KillTimer.argtypes = [wt.HWND, ULONG_PTR]
user32.SetCapture.argtypes = [wt.HWND]
user32.MonitorFromPoint.restype = wt.HANDLE
user32.MonitorFromPoint.argtypes = [POINT, wt.DWORD]
user32.GetMonitorInfoW.argtypes = [wt.HANDLE, ctypes.POINTER(MONITORINFO)]
user32.TrackMouseEvent.argtypes = [ctypes.POINTER(TRACKMOUSEEVENT)]
user32.LoadCursorW.restype = wt.HANDLE
user32.LoadCursorW.argtypes = [wt.HINSTANCE, ctypes.c_void_p]
user32.RegisterClassW.argtypes = [ctypes.POINTER(WNDCLASS)]
user32.PostMessageW.argtypes = [wt.HWND, wt.UINT, wt.WPARAM, wt.LPARAM]
user32.RegisterHotKey.argtypes = [wt.HWND, ctypes.c_int, wt.UINT, wt.UINT]

gdi32.CreateCompatibleDC.restype = wt.HDC
gdi32.CreateCompatibleDC.argtypes = [wt.HDC]
gdi32.CreateDIBSection.restype = wt.HBITMAP
gdi32.CreateDIBSection.argtypes = [wt.HDC, ctypes.c_void_p, wt.UINT, ctypes.POINTER(ctypes.c_void_p),
                                   wt.HANDLE, wt.DWORD]
gdi32.SelectObject.restype = wt.HGDIOBJ
gdi32.SelectObject.argtypes = [wt.HDC, wt.HGDIOBJ]
gdi32.DeleteObject.argtypes = [wt.HGDIOBJ]
gdi32.DeleteDC.argtypes = [wt.HDC]

# 64-bit'te handle/işaretçi döndüren veya alan her fonksiyonun türü bildirilmeli,
# yoksa ctypes bunları 32-bit int sanıp adresleri kırpar (pencere açılmaz).
kernel32.GetModuleHandleW.restype = wt.HMODULE
kernel32.GetModuleHandleW.argtypes = [wt.LPCWSTR]
kernel32.CreateMutexW.restype = wt.HANDLE
kernel32.CreateMutexW.argtypes = [ctypes.c_void_p, wt.BOOL, wt.LPCWSTR]
kernel32.GlobalAlloc.restype = wt.HGLOBAL
kernel32.GlobalAlloc.argtypes = [wt.UINT, ctypes.c_size_t]
kernel32.GlobalLock.restype = ctypes.c_void_p
kernel32.GlobalLock.argtypes = [wt.HGLOBAL]
kernel32.GlobalUnlock.argtypes = [wt.HGLOBAL]
user32.ShowWindow.argtypes = [wt.HWND, ctypes.c_int]
user32.GetMessageW.restype = ctypes.c_int
user32.GetMessageW.argtypes = [ctypes.c_void_p, wt.HWND, wt.UINT, wt.UINT]
user32.TranslateMessage.argtypes = [ctypes.c_void_p]
user32.DispatchMessageW.argtypes = [ctypes.c_void_p]
user32.PostQuitMessage.argtypes = [ctypes.c_int]
user32.PostMessageW.restype = wt.BOOL
user32.GetSystemMetrics.restype = ctypes.c_int
user32.GetSystemMetrics.argtypes = [ctypes.c_int]
user32.ReleaseCapture.argtypes = []
user32.OpenClipboard.argtypes = [wt.HWND]
user32.SetClipboardData.restype = wt.HANDLE
user32.SetClipboardData.argtypes = [wt.UINT, wt.HANDLE]
user32.RegisterClassW.restype = wt.ATOM
user32.ShowWindow.restype = wt.BOOL
shell32.ShellExecuteW.restype = ctypes.c_void_p
shell32.ShellExecuteW.argtypes = [wt.HWND, wt.LPCWSTR, wt.LPCWSTR, wt.LPCWSTR, wt.LPCWSTR, ctypes.c_int]


def work_area_at(x, y):
    """İmlecin bulunduğu ekranın çalışma alanı (görev çubuğu hariç)."""
    try:
        mon = user32.MonitorFromPoint(POINT(x, y), 2)  # MONITOR_DEFAULTTONEAREST
        mi = MONITORINFO()
        mi.cbSize = ctypes.sizeof(MONITORINFO)
        if user32.GetMonitorInfoW(mon, ctypes.byref(mi)):
            r = mi.rcWork
            return r.left, r.top, r.right, r.bottom
    except Exception:
        log_err()
    return 0, 0, user32.GetSystemMetrics(0), user32.GetSystemMetrics(1)


def cursor_pos():
    pt = POINT()
    user32.GetCursorPos(ctypes.byref(pt))
    return pt.x, pt.y


# ---------------------------------------------------------------------------
# Ayarlar (konum + autostart + SkipTag)  — registry + küçük json
# ---------------------------------------------------------------------------
SETTINGS_KEY = r"Software\gokhangeo\Atac"
RUN_KEY = r"Software\Microsoft\Windows\CurrentVersion\Run"
ANCHOR_FILE = os.path.join(APPDATA, "konum.json")


def load_anchor():
    try:
        with open(ANCHOR_FILE, encoding="utf-8") as f:
            d = json.load(f)
            return int(d["right"]), int(d["top"])
    except Exception:
        return None


def save_anchor(right, top):
    try:
        with open(ANCHOR_FILE, "w", encoding="utf-8") as f:
            json.dump({"right": right, "top": top}, f)
    except OSError:
        pass


def reg_get(name):
    import winreg
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, SETTINGS_KEY) as k:
            return winreg.QueryValueEx(k, name)[0]
    except OSError:
        return None


def reg_set(name, value):
    import winreg
    with winreg.CreateKey(winreg.HKEY_CURRENT_USER, SETTINGS_KEY) as k:
        winreg.SetValueEx(k, name, 0, winreg.REG_SZ, value)


def launch_command():
    if getattr(sys, "frozen", False):
        return f'"{sys.executable}" --gizli-baslat'
    pyw = os.path.join(os.path.dirname(sys.executable), "pythonw.exe")
    return f'"{pyw}" "{os.path.abspath(__file__)}" --gizli-baslat'


def autostart_enabled():
    import winreg
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY) as k:
            winreg.QueryValueEx(k, "Atac")
            return True
    except OSError:
        return False


def set_autostart(on):
    import winreg
    with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY, 0, winreg.KEY_SET_VALUE) as k:
        if on:
            winreg.SetValueEx(k, "Atac", 0, winreg.REG_SZ, launch_command())
        else:
            try:
                winreg.DeleteValue(k, "Atac")
            except OSError:
                pass


# ---------------------------------------------------------------------------
# Sürüm + otomatik güncelleme  (mandalina'dan uyarlandı)
# ---------------------------------------------------------------------------
TAG_PREFIX = "atac-py-v"
ASSET_NAME = "Atac.exe"
UPDATE_API = os.environ.get("ATAC_UPDATE_URL",
                            "https://api.github.com/repos/gokhangeo/rea/releases?per_page=50")


def read_app_version():
    base = getattr(sys, "_MEIPASS", os.path.dirname(os.path.abspath(__file__)))
    try:
        with open(os.path.join(base, "version.txt"), encoding="utf-8") as f:
            return f.read().strip() or "dev"
    except OSError:
        return "dev"


APP_VERSION = read_app_version()


def version_tuple(text):
    try:
        return tuple(int(x) for x in text.strip().split("."))
    except (ValueError, AttributeError):
        return None


def can_self_update():
    return getattr(sys, "frozen", False) and version_tuple(APP_VERSION) is not None


def find_update(current):
    import urllib.request
    req = urllib.request.Request(UPDATE_API, headers={"Accept": "application/vnd.github+json",
                                                      "User-Agent": "Atac"})
    with urllib.request.urlopen(req, timeout=20) as r:
        releases = json.load(r)
    cur = version_tuple(current)
    skipped = reg_get("SkipTag")
    best = None
    for rel in releases:
        tag = rel.get("tag_name", "")
        if rel.get("draft") or rel.get("prerelease") or not tag.startswith(TAG_PREFIX) or tag == skipped:
            continue
        v = version_tuple(tag[len(TAG_PREFIX):])
        asset = next((a for a in rel.get("assets", []) if a.get("name") == ASSET_NAME), None)
        if v and asset and cur and v > cur and (best is None or v > best[0]):
            best = (v, tag, asset["browser_download_url"], asset["size"])
    return best[1:] if best else None


def download_update(url, size):
    import urllib.request
    dest = os.path.join(os.path.dirname(sys.executable), "Atac.guncelleme.exe")
    part = dest + ".part"
    req = urllib.request.Request(url, headers={"User-Agent": "Atac"})
    with urllib.request.urlopen(req, timeout=120) as r, open(part, "wb") as f:
        shutil.copyfileobj(r, f, 1024 * 1024)
    if os.path.getsize(part) != size:
        os.remove(part)
        raise RuntimeError("indirilen dosya boyutu yanlış")
    os.replace(part, dest)
    return dest


def apply_update(new_exe, tag):
    exe = sys.executable
    old = os.path.splitext(exe)[0] + ".eski.exe"
    if os.path.exists(old):
        os.remove(old)
    os.replace(exe, old)
    try:
        os.replace(new_exe, exe)
    except OSError:
        os.replace(old, exe)
        raise
    reg_set("SkipTag", tag)
    log(f"güncelleme kuruldu: {tag}")
    subprocess.Popen([exe, "--updated", old], close_fds=True,
                     env=dict(os.environ, PYINSTALLER_RESET_ENVIRONMENT="1"),
                     creationflags=0x00000008 | 0x00000200)


def finish_update(old_exe):
    for _ in range(100):
        if single_instance():
            break
        time.sleep(0.2)
    else:
        return False
    for _ in range(50):
        try:
            if os.path.exists(old_exe):
                os.remove(old_exe)
            break
        except OSError:
            time.sleep(0.2)
    return True


# ---------------------------------------------------------------------------
# OLE IDropTarget — dosyaları Ataç'a bırakma (mandalina'daki kanıtlanmış yöntem)
# ---------------------------------------------------------------------------
DROPEFFECT_COPY = 1
HRESULT = ctypes.c_long
E_NOINTERFACE = -2147467262


class GUID(ctypes.Structure):
    _fields_ = [("d1", ctypes.c_uint32), ("d2", ctypes.c_uint16), ("d3", ctypes.c_uint16),
                ("d4", ctypes.c_ubyte * 8)]


def _iid(text):
    g = GUID()
    ole32.CLSIDFromString(ctypes.c_wchar_p(text), ctypes.byref(g))
    return bytes(g)


IID_IUNKNOWN = _iid("{00000000-0000-0000-C000-000000000046}")
IID_IDROPTARGET = _iid("{00000122-0000-0000-C000-000000000046}")


class FORMATETC(ctypes.Structure):
    _fields_ = [("cfFormat", wt.WORD), ("ptd", ctypes.c_void_p), ("dwAspect", wt.DWORD),
                ("lindex", ctypes.c_long), ("tymed", wt.DWORD)]


class STGMEDIUM(ctypes.Structure):
    _fields_ = [("tymed", wt.DWORD), ("hGlobal", ctypes.c_void_p), ("pUnkForRelease", ctypes.c_void_p)]


GETDATA = ctypes.WINFUNCTYPE(HRESULT, ctypes.c_void_p, ctypes.POINTER(FORMATETC), ctypes.POINTER(STGMEDIUM))
ole32.ReleaseStgMedium.argtypes = [ctypes.POINTER(STGMEDIUM)]
ole32.RegisterDragDrop.argtypes = [wt.HWND, ctypes.c_void_p]
ole32.OleInitialize.argtypes = [ctypes.c_void_p]
shell32.DragQueryFileW.argtypes = [ctypes.c_void_p, wt.UINT, wt.LPWSTR, wt.UINT]


def hdrop_files(pdata):
    vtbl = ctypes.cast(pdata, ctypes.POINTER(ctypes.POINTER(ctypes.c_void_p)))[0]
    get_data = GETDATA(vtbl[3])
    fe = FORMATETC(15, None, 1, -1, 1)  # CF_HDROP
    sm = STGMEDIUM()
    if get_data(pdata, ctypes.byref(fe), ctypes.byref(sm)) != 0:
        return []
    try:
        h = sm.hGlobal
        n = shell32.DragQueryFileW(h, 0xFFFFFFFF, None, 0)
        files = []
        for i in range(n):
            buf = ctypes.create_unicode_buffer(shell32.DragQueryFileW(h, i, None, 0) + 1)
            shell32.DragQueryFileW(h, i, buf, len(buf))
            files.append(buf.value)
        return files
    finally:
        ole32.ReleaseStgMedium(ctypes.byref(sm))


QI_FUNC = ctypes.WINFUNCTYPE(HRESULT, ctypes.c_void_p, ctypes.c_void_p, ctypes.POINTER(ctypes.c_void_p))
REF_FUNC = ctypes.WINFUNCTYPE(ctypes.c_ulong, ctypes.c_void_p)
ENTER_FUNC = ctypes.WINFUNCTYPE(HRESULT, ctypes.c_void_p, ctypes.c_void_p, wt.DWORD, ctypes.c_int64,
                                ctypes.POINTER(wt.DWORD))
OVER_FUNC = ctypes.WINFUNCTYPE(HRESULT, ctypes.c_void_p, wt.DWORD, ctypes.c_int64, ctypes.POINTER(wt.DWORD))
LEAVE_FUNC = ctypes.WINFUNCTYPE(HRESULT, ctypes.c_void_p)


class _DropVtbl(ctypes.Structure):
    _fields_ = [("QueryInterface", QI_FUNC), ("AddRef", REF_FUNC), ("Release", REF_FUNC),
                ("DragEnter", ENTER_FUNC), ("DragOver", OVER_FUNC), ("DragLeave", LEAVE_FUNC),
                ("Drop", ENTER_FUNC)]


class _DropObj(ctypes.Structure):
    _fields_ = [("lpVtbl", ctypes.POINTER(_DropVtbl))]


class OleDropTarget:
    def __init__(self, on_drop):
        self.on_drop = on_drop
        self.vtbl = _DropVtbl(QI_FUNC(self._qi), REF_FUNC(lambda this: 1), REF_FUNC(lambda this: 1),
                              ENTER_FUNC(self._enter), OVER_FUNC(self._over), LEAVE_FUNC(lambda this: 0),
                              ENTER_FUNC(self._drop))
        self.obj = _DropObj(ctypes.pointer(self.vtbl))
        self.ptr = ctypes.addressof(self.obj)

    def _qi(self, this, riid, ppv):
        if ctypes.string_at(riid, 16) in (IID_IUNKNOWN, IID_IDROPTARGET):
            ppv[0] = this
            return 0
        ppv[0] = None
        return E_NOINTERFACE

    def _enter(self, this, pdata, keys, pt, effect):
        effect[0] = DROPEFFECT_COPY
        return 0

    def _over(self, this, keys, pt, effect):
        effect[0] = DROPEFFECT_COPY
        return 0

    def _drop(self, this, pdata, keys, pt, effect):
        try:
            self.on_drop(hdrop_files(pdata))
        except Exception:
            log_err()
        effect[0] = DROPEFFECT_COPY
        return 0


# ---------------------------------------------------------------------------
# Panoya dosya kopyalama (CF_HDROP) — dışarı "taşıma" için (sürükle-bırak v2'de)
# ---------------------------------------------------------------------------
CF_HDROP = 15
GMEM_MOVEABLE = 0x0002


class DROPFILES(ctypes.Structure):
    _fields_ = [("pFiles", wt.DWORD), ("pt", POINT), ("fNC", wt.BOOL), ("fWide", wt.BOOL)]


def copy_to_clipboard(paths):
    paths = [p for p in paths if p]
    if not paths:
        return False
    try:
        data = ("\0".join(paths) + "\0\0").encode("utf-16-le")
        head = DROPFILES(ctypes.sizeof(DROPFILES), POINT(0, 0), 0, 1)
        blob = bytes(head) + data
        kernel32.GlobalAlloc.restype = wt.HGLOBAL
        h = kernel32.GlobalAlloc(GMEM_MOVEABLE, len(blob))
        kernel32.GlobalLock.restype = ctypes.c_void_p
        ptr = kernel32.GlobalLock(h)
        ctypes.memmove(ptr, blob, len(blob))
        kernel32.GlobalUnlock(h)
        if not user32.OpenClipboard(None):
            return False
        user32.EmptyClipboard()
        user32.SetClipboardData(CF_HDROP, h)
        user32.CloseClipboard()
        return True
    except Exception:
        log_err()
        return False


def open_path(path):
    try:
        shell32.ShellExecuteW(None, "open", path, None, None, 1)
    except Exception:
        log_err()


# ---------------------------------------------------------------------------
# Görünüm ölçüleri ve renkler (Electron sürümüyle aynı)
# ---------------------------------------------------------------------------
COL_W, COL_H = 66, 82
EXP_W, EXP_H = 150, 426
MARGIN = 8
RADIUS = 28
CLIP_W_CLOSED = 52
CLIP_W_OPEN = 54

VURGU = (255, 107, 74)
METIN = (74, 51, 39)
SAYAC_RENK = (92, 59, 43)

FONT_DIR = os.path.join(os.environ.get("WINDIR", r"C:\Windows"), "Fonts")


def _font(name, size):
    try:
        return ImageFont.truetype(os.path.join(FONT_DIR, name), size)
    except OSError:
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            return ImageFont.load_default()


F_AD = _font("segoeui.ttf", 15)
F_AD_K = _font("segoeui.ttf", 14)
F_SAYAC = _font("seguisb.ttf", 16)
F_BTN = _font("segoeui.ttf", 20)
try:
    F_EMOJI = ImageFont.truetype(os.path.join(FONT_DIR, "seguiemj.ttf"), 44)
    F_EMOJI_K = ImageFont.truetype(os.path.join(FONT_DIR, "seguiemj.ttf"), 38)
except OSError:
    F_EMOJI = F_EMOJI_K = None

try:
    CLIP_IMG = Image.open(os.path.join(getattr(sys, "_MEIPASS",
                          os.path.dirname(os.path.abspath(__file__))), "assets", "atac-clip.png")).convert("RGBA")
except Exception:
    CLIP_IMG = Image.new("RGBA", (120, 170), (0, 0, 0, 0))
    log_err()


RESIM = {"png", "jpg", "jpeg", "gif", "webp", "bmp", "avif"}


def uzanti(ad):
    return (ad.rsplit(".", 1)[-1] if "." in ad else "").lower()


def glif(ad, isdir):
    if isdir:
        return "📁"
    e = uzanti(ad)
    if e in ("mp4", "mov", "mkv", "webm", "avi"):
        return "🎬"
    if e in ("mp3", "wav", "flac", "aac", "m4a", "ogg"):
        return "🎵"
    if e == "pdf":
        return "📕"
    if e in ("doc", "docx", "txt", "rtf", "odt"):
        return "📝"
    if e in ("xls", "xlsx", "csv"):
        return "📊"
    if e in ("zip", "rar", "7z", "tar", "gz"):
        return "🗜"
    if e in ("exe", "msi"):
        return "⚙"
    return "📄"


def kisa_ad(ad):
    if len(ad) <= 16:
        return ad
    return ad[:9] + "…" + ad[-6:]


def rounded_mask(size, radius):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius, fill=255)
    return m


_CLIP_CACHE = {}


def clip_resized(width):
    c = _CLIP_CACHE.get(width)
    if c is None:
        h = round(CLIP_IMG.height * width / CLIP_IMG.width)
        c = CLIP_IMG.resize((width, h), Image.LANCZOS)
        _CLIP_CACHE[width] = c
    return c


def thumb(path, size):
    try:
        im = Image.open(path).convert("RGBA")
        im = ImageOps.exif_transpose(im)
        im = ImageOps.fit(im, (size, size), Image.LANCZOS)
        im.putalpha(rounded_mask((size, size), 14))
        return im
    except Exception:
        return None


def draw_glyph(canvas, cx, cy, ch, font):
    if font is None:
        return
    try:
        d = ImageDraw.Draw(canvas)
        d.text((cx, cy), ch, font=font, anchor="mm", embedded_color=True)
    except Exception:
        try:
            ImageDraw.Draw(canvas).text((cx, cy), ch, font=font, anchor="mm", fill=(255, 255, 255, 255))
        except Exception:
            pass


# ---------------------------------------------------------------------------
# Ana uygulama
# ---------------------------------------------------------------------------
class Atac:
    def __init__(self, start_hidden=False):
        self.items = []          # {path, name, isdir, thumb}
        self.expanded = False
        self.visible = not start_hidden
        self.hover = False
        self.hover_key = None
        self.idle = False
        self.scroll = 0
        self.hit = []            # [(x0,y0,x1,y1, action, data)]
        self.ole_drops = []      # OLE kuyruğu
        self.drag = None         # ataç taşıma durumu
        self.last_activity = time.time()
        self.fade_alpha = 255
        self.menu_open = False
        self._wndproc_ref = None
        self._drop_ref = None
        self._panel_cache = {}
        self._tracking = False

        a = load_anchor()
        rx, ry, rr, rb = work_area_at(*cursor_pos())
        if a:
            self.anchor_right, self.anchor_top = a
        else:
            self.anchor_right = rr - 20
            self.anchor_top = ry + (rb - ry - COL_H) // 2

        self._make_window()

    # ---- pencere ----
    def _make_window(self):
        hinst = kernel32.GetModuleHandleW(None)
        cls = WNDCLASS()
        cls.style = CS_DBLCLKS
        self._wndproc_ref = WNDPROC(self._wndproc)
        cls.lpfnWndProc = self._wndproc_ref
        cls.hInstance = hinst
        cls.hCursor = user32.LoadCursorW(None, IDC_ARROW)  # MAKEINTRESOURCE
        cls.lpszClassName = "AtacLayered"
        user32.RegisterClassW(ctypes.byref(cls))

        ex = WS_EX_LAYERED | WS_EX_TOPMOST | WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE
        self.hwnd = user32.CreateWindowExW(ex, "AtacLayered", "Ataç", WS_POPUP,
                                           0, 0, COL_W, COL_H, None, None, hinst, None)
        if not self.hwnd:
            raise ctypes.WinError(ctypes.get_last_error())

        # OLE dosya bırakma
        try:
            ole32.OleInitialize(None)
            self._drop_ref = OleDropTarget(lambda files: self._queue_drop(files))
            hr = ole32.RegisterDragDrop(self.hwnd, self._drop_ref.ptr)
            if hr != 0:
                log(f"RegisterDragDrop başarısız: {hr & 0xFFFFFFFF:#x}")
        except Exception:
            log_err()

        try:
            user32.RegisterHotKey(self.hwnd, HOTKEY_ID, MOD_CONTROL | MOD_SHIFT, VK_SPACE)
        except Exception:
            log_err()

        user32.SetTimer(self.hwnd, 1, 120, None)  # idle/animasyon zamanlayıcı
        if self.visible:
            self.render()
            user32.ShowWindow(self.hwnd, SW_SHOWNOACTIVATE)

    def _queue_drop(self, files):
        self.ole_drops.append(list(files))
        user32.PostMessageW(self.hwnd, WM_ATAC_DROP, 0, 0)

    # ---- boyut / konum ----
    def _geom(self):
        w, h = (EXP_W, EXP_H) if self.expanded else (COL_W, COL_H)
        x = self.anchor_right - w
        rx, ry, rr, rb = work_area_at(x + w // 2, self.anchor_top)
        top = self.anchor_top
        if top + h > rb:
            top = rb - h
        if top < ry:
            top = ry
        return x, top, w, h

    # ---- çizim ----
    def render(self):
        try:
            img = self._compose()
            x, y, w, h = self._geom()
            self._blit(img, x, y)
        except Exception:
            log_err()

    def _compose(self):
        self.hit = []
        if not self.expanded:
            return self._compose_closed()
        return self._compose_open()

    def _compose_closed(self):
        img = Image.new("RGBA", (COL_W, COL_H), (0, 0, 0, 0))
        clip = clip_resized(CLIP_W_CLOSED)
        cx = (COL_W - clip.width) // 2
        cy = (COL_H - clip.height) // 2
        # yumuşak gölge
        shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
        sh = Image.new("RGBA", clip.size, (0, 0, 0, 0))
        sh.paste((60, 30, 25, 110), (0, 0), clip.split()[3])
        shadow.paste(sh, (cx, cy + 4), sh)
        shadow = shadow.filter(ImageFilter.GaussianBlur(4))
        img.alpha_composite(shadow)
        img.alpha_composite(clip, (cx, cy))
        self.hit.append((0, 0, COL_W, COL_H, "clip", None))
        return img

    def _panel(self, w, h):
        """Buzlu-cam görünümlü yuvarlak panel (gölge + degrade + kenar + iç parlama).
        Boyuta göre önbelleğe alınır; her karede yeniden hesaplanmaz."""
        key = (w, h)
        cached = self._panel_cache.get(key)
        if cached is not None:
            return cached.copy()

        img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        inner = (MARGIN, MARGIN, w - MARGIN, h - MARGIN)
        iw, ih = inner[2] - inner[0], inner[3] - inner[1]

        # dış gölge
        shadow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        ImageDraw.Draw(shadow).rounded_rectangle(inner, RADIUS, fill=(80, 40, 40, 120))
        shadow = shadow.filter(ImageFilter.GaussianBlur(9))
        img.alpha_composite(shadow)

        # degrade dolgu (üst şeftali -> orta mercan -> alt pembe), yarı saydam.
        # Hız için tek sütun çizilip genişliğe yayılır.
        top, mid, bot = (255, 215, 168), (255, 159, 126), (255, 143, 174)
        col = Image.new("RGB", (1, ih))
        cpx = col.load()
        for yy in range(ih):
            t = yy / max(1, ih - 1)
            if t < 0.5:
                k = t / 0.5
                a, b_ = top, mid
            else:
                k = (t - 0.5) / 0.5
                a, b_ = mid, bot
            cpx[0, yy] = (int(a[0] + (b_[0] - a[0]) * k),
                          int(a[1] + (b_[1] - a[1]) * k),
                          int(a[2] + (b_[2] - a[2]) * k))
        grad = col.resize((iw, ih)).convert("RGBA")
        grad.putalpha(rounded_mask((iw, ih), RADIUS).point(lambda v: int(v * 150 / 255)))
        img.alpha_composite(grad, (inner[0], inner[1]))

        # kenar + iç üst parlama
        d = ImageDraw.Draw(img)
        d.rounded_rectangle(inner, RADIUS, outline=(255, 255, 255, 110), width=1)
        d.rounded_rectangle((inner[0] + 1, inner[1] + 1, inner[2] - 1, inner[1] + RADIUS),
                            RADIUS, outline=(255, 255, 255, 70), width=1)
        self._panel_cache[key] = img.copy()
        return img

    def _compose_open(self):
        w, h = EXP_W, EXP_H
        img = self._panel(w, h)
        d = ImageDraw.Draw(img)
        cxmid = w // 2

        # üst düğmeler: × (sol) ve + (sağ)
        by = MARGIN + 14
        for key, gx, glyph, col in (("hide", MARGIN + 20, "×", METIN), ("add", w - MARGIN - 20, "+", VURGU)):
            d.ellipse((gx - 12, by - 12, gx + 12, by + 12), fill=(255, 255, 255, 150))
            d.text((gx, by - 1), glyph, font=F_BTN, anchor="mm", fill=col + (255,))
            self.hit.append((gx - 14, by - 14, gx + 14, by + 14, key, None))

        # ataç amblemi
        clip = clip_resized(CLIP_W_OPEN)
        clx = cxmid - clip.width // 2
        cly = by + 14
        img.alpha_composite(clip, (clx, cly))
        self.hit.append((clx, cly, clx + clip.width, cly + clip.height, "clip", None))
        y = cly + clip.height + 2

        # sayaç satırı
        n = len(self.items)
        txt = f"⌃ {n} Öğe"
        d.text((cxmid, y + 10), txt, font=F_SAYAC, anchor="mm", fill=SAYAC_RENK + (255,))
        self.hit.append((MARGIN + 6, y, w - MARGIN - 6, y + 22, "sayac", None))
        y += 26

        # ayraç
        d.line((w * 0.15, y, w * 0.85, y), fill=(120, 60, 45, 60), width=1)
        y += 8

        # liste alanı
        list_top = y
        list_bottom = h - MARGIN - 46
        self._draw_list(img, d, list_top, list_bottom, w)

        # alt düğmeler
        ay = h - MARGIN - 34
        if n > 0:
            # Tümü
            d.rounded_rectangle((MARGIN + 6, ay, w - MARGIN - 44, ay + 30), 12, fill=(255, 255, 255, 230))
            d.text(((MARGIN + 6 + w - MARGIN - 44) / 2, ay + 15), "⇪ Tümü", font=F_AD_K, anchor="mm",
                   fill=(178, 58, 40, 255))
            self.hit.append((MARGIN + 6, ay, w - MARGIN - 44, ay + 30, "tumu", None))
            # temizle
            d.rounded_rectangle((w - MARGIN - 40, ay, w - MARGIN - 6, ay + 30), 12, fill=(255, 255, 255, 140))
            d.text((w - MARGIN - 23, ay + 15), "🗑", font=F_EMOJI_K or F_AD_K, anchor="mm",
                   embedded_color=bool(F_EMOJI_K))
            self.hit.append((w - MARGIN - 40, ay, w - MARGIN - 6, ay + 30, "temizle", None))
        return img

    def _draw_list(self, img, d, top, bottom, w):
        n = len(self.items)
        cxmid = w // 2
        if n == 0:
            cy = (top + bottom) // 2
            d.ellipse((cxmid - 27, cy - 34, cxmid + 27, cy + 20), outline=(255, 255, 255, 180), width=2)
            d.text((cxmid, cy - 7), "+", font=F_BTN, anchor="mm", fill=(255, 255, 255, 220))
            d.text((cxmid, cy + 34), "Sürükle", font=F_AD_K, anchor="mm", fill=(255, 255, 255, 230))
            d.text((cxmid, cy + 52), "ya da + ile ekle", font=F_AD_K, anchor="mm", fill=(255, 255, 255, 230))
            return
        card = 72
        gap = 12
        step = card + 28 + gap
        # kaydırma sınırı
        total = n * step
        avail = bottom - top
        maxscroll = max(0, total - avail)
        if self.scroll > maxscroll:
            self.scroll = maxscroll
        if self.scroll < 0:
            self.scroll = 0
        y = top - self.scroll
        for it in self.items:
            if y + card < top - 2 or y > bottom + 2:
                y += step
                continue
            cardx = cxmid - card // 2
            # kart gölgesi + zemin
            d.rounded_rectangle((cardx, y, cardx + card, y + card), 14, fill=(255, 255, 255, 255))
            if it["isdir"]:
                d.rounded_rectangle((cardx, y, cardx + card, y + card), 14, fill=(255, 190, 90, 255))
            if it.get("thumb") is not None:
                img.alpha_composite(it["thumb"], (cardx, y))
            else:
                draw_glyph(img, cardx + card // 2, y + card // 2, glif(it["name"], it["isdir"]), F_EMOJI_K)
            # ad
            d.text((cxmid, y + card + 13), kisa_ad(it["name"]), font=F_AD_K, anchor="mm",
                   fill=(255, 255, 255, 255), stroke_width=2, stroke_fill=(120, 50, 40, 160))
            # kaldır düğmesi
            rx0 = cardx + card - 8
            d.ellipse((rx0, y - 8, rx0 + 18, y + 10), fill=(255, 255, 255, 255))
            d.text((rx0 + 9, y + 1), "×", font=F_AD_K, anchor="mm", fill=(214, 74, 58, 255))
            self.hit.append((rx0, y - 8, rx0 + 18, y + 10, "sil", it["path"]))
            self.hit.append((cardx, y, cardx + card, y + card, "oge", it["path"]))
            y += step

    def _blit(self, img, x, y):
        w, h = img.size
        # premultiply + BGRA
        r, g, b, a = img.split()
        r = ImageChops.multiply(r, a)
        g = ImageChops.multiply(g, a)
        b = ImageChops.multiply(b, a)
        bgra = Image.merge("RGBA", (b, g, r, a)).tobytes("raw", "RGBA")

        screen = user32.GetDC(None)
        memdc = gdi32.CreateCompatibleDC(screen)
        bmi = BITMAPINFOHEADER()
        bmi.biSize = ctypes.sizeof(BITMAPINFOHEADER)
        bmi.biWidth = w
        bmi.biHeight = -h  # top-down
        bmi.biPlanes = 1
        bmi.biBitCount = 32
        bmi.biCompression = 0
        bits = ctypes.c_void_p()
        hbmp = gdi32.CreateDIBSection(memdc, ctypes.byref(bmi), 0, ctypes.byref(bits), None, 0)
        ctypes.memmove(bits, bgra, len(bgra))
        old = gdi32.SelectObject(memdc, hbmp)

        ptdst = POINT(x, y)
        size = SIZE(w, h)
        ptsrc = POINT(0, 0)
        blend = BLENDFUNCTION(AC_SRC_OVER, 0, self.fade_alpha, AC_SRC_ALPHA)
        user32.SetWindowPos(self.hwnd, HWND_TOPMOST, 0, 0, 0, 0,
                            SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE)
        user32.UpdateLayeredWindow(self.hwnd, screen, ctypes.byref(ptdst), ctypes.byref(size),
                                   memdc, ctypes.byref(ptsrc), 0, ctypes.byref(blend), ULW_ALPHA)
        gdi32.SelectObject(memdc, old)
        gdi32.DeleteObject(hbmp)
        gdi32.DeleteDC(memdc)
        user32.ReleaseDC(None, screen)

    # ---- davranış ----
    def set_expanded(self, v):
        self.expanded = v
        self.scroll = 0
        self.wake()
        self.render()

    def toggle(self):
        self.set_expanded(not self.expanded)

    def wake(self):
        self.idle = False
        self.fade_alpha = 255
        self.last_activity = time.time()

    def add_paths(self, paths):
        eklendi = 0
        for p in paths:
            p = p.rstrip("\\/")
            if not p or any(o["path"] == p for o in self.items):
                continue
            try:
                isdir = os.path.isdir(p)
            except OSError:
                isdir = False
            name = os.path.basename(p) or p
            t = None if isdir else (thumb(p, 72) if uzanti(name) in RESIM else None)
            self.items.append({"path": p, "name": name, "isdir": isdir, "thumb": t})
            eklendi += 1
        if eklendi:
            if not self.expanded:
                self.set_expanded(True)
            else:
                self.render()

    def remove(self, path):
        self.items = [o for o in self.items if o["path"] != path]
        self.render()

    def clear(self):
        self.items = []
        self.render()

    def hit_test(self, x, y):
        for (x0, y0, x1, y1, action, data) in reversed(self.hit):
            if x0 <= x <= x1 and y0 <= y <= y1:
                return action, data
        return None, None

    # ---- menüler / seçiciler ----
    def show_add_menu(self):
        try:
            import tkinter as tk
            from tkinter import filedialog
            root = tk.Tk()
            root.withdraw()
            root.attributes("-topmost", True)
            paths = filedialog.askopenfilenames(parent=root, title="Dosya ekle")
            root.destroy()
            if paths:
                self.add_paths(list(paths))
        except Exception:
            log_err()

    def act(self, action, data, dbl=False):
        self.wake()
        if action == "clip" or action == "sayac":
            self.toggle()
        elif action == "hide":
            self.hide()
        elif action == "add":
            self.show_add_menu()
        elif action == "temizle":
            self.clear()
        elif action == "sil":
            self.remove(data)
        elif action == "tumu":
            if copy_to_clipboard([o["path"] for o in self.items]):
                log("tümü panoya kopyalandı")
        elif action == "oge":
            if dbl:
                open_path(data)
            else:
                copy_to_clipboard([data])

    def hide(self):
        self.visible = False
        user32.ShowWindow(self.hwnd, SW_HIDE)

    def show(self):
        self.visible = True
        self.wake()
        self.render()
        user32.ShowWindow(self.hwnd, SW_SHOWNOACTIVATE)

    def toggle_visible(self):
        if self.visible:
            self.hide()
        else:
            self.show()

    # ---- WndProc ----
    def _wndproc(self, hwnd, msg, wp, lp):
        try:
            if msg == WM_TIMER:
                self._on_timer()
                return 0
            if msg == WM_ATAC_DROP:
                while self.ole_drops:
                    self.add_paths(self.ole_drops.pop(0))
                return 0
            if msg == WM_ATAC_SHOW:
                self.toggle_visible()
                return 0
            if msg == WM_ATAC_UPDATED:
                self.render()
                return 0
            if msg == WM_ATAC_QUIT:
                user32.PostQuitMessage(0)
                return 0
            if msg == WM_HOTKEY:
                self.toggle_visible()
                return 0
            if msg in (WM_LBUTTONDOWN, WM_LBUTTONUP, WM_LBUTTONDBLCLK, WM_MOUSEMOVE, WM_MOUSELEAVE,
                       WM_MOUSEWHEEL, WM_RBUTTONUP):
                self._on_mouse(msg, wp, lp)
                return 0
            if msg == WM_CLOSE:
                self.hide()
                return 0
            if msg == WM_DESTROY:
                return 0
        except Exception:
            log_err()
        return user32.DefWindowProcW(hwnd, msg, wp, lp)

    def _loc(self, lp):
        x = ctypes.c_short(lp & 0xFFFF).value
        y = ctypes.c_short((lp >> 16) & 0xFFFF).value
        return x, y

    def _on_mouse(self, msg, wp, lp):
        if msg == WM_MOUSELEAVE:
            self.hover = False
            self.hover_key = None
            self._tracking = False
            return
        x, y = self._loc(lp)
        if msg == WM_MOUSEWHEEL and self.expanded:
            delta = ctypes.c_short((wp >> 16) & 0xFFFF).value
            self.scroll -= int(delta / WHEEL_DELTA * 40)
            self.wake()
            self.render()
            return
        # hover takibi
        if not getattr(self, "_tracking", False):
            tme = TRACKMOUSEEVENT(ctypes.sizeof(TRACKMOUSEEVENT), 0x00000002, self.hwnd, 0)
            user32.TrackMouseEvent(ctypes.byref(tme))
            self._tracking = True
        self.hover = True
        self.wake()

        if msg == WM_LBUTTONDOWN:
            action, data = self.hit_test(x, y)
            self.drag = {"sx": x, "sy": y, "moved": False, "action": action, "data": data,
                         "gx": self._geom()[0], "gy": self._geom()[1], "px": None}
            gx, gy, _, _ = self._geom()
            px, py = cursor_pos()
            self.drag["px"] = px
            self.drag["py"] = py
            self.drag["gx"] = gx
            self.drag["gy"] = gy
            user32.SetCapture(self.hwnd)
            return
        if msg == WM_MOUSEMOVE:
            if self.drag:
                px, py = cursor_pos()
                dx = px - self.drag["px"]
                dy = py - self.drag["py"]
                if abs(dx) + abs(dy) > 4:
                    self.drag["moved"] = True
                if self.drag["moved"] and self.drag["action"] in ("clip", None, "hide", "add", "sayac"):
                    nx = self.drag["gx"] + dx
                    ny = self.drag["gy"] + dy
                    w, h = (EXP_W, EXP_H) if self.expanded else (COL_W, COL_H)
                    self.anchor_right = nx + w
                    self.anchor_top = ny
                    self.render()
            elif self.fade_alpha != 255:
                # boştan uyanırken bir kez tazele (her harekette değil)
                self.fade_alpha = 255
                self.idle = False
                self.render()
            return
        if msg == WM_LBUTTONUP:
            user32.ReleaseCapture()
            dg = self.drag
            self.drag = None
            if not dg:
                return
            if dg["moved"]:
                if dg["action"] in ("clip", None, "hide", "add", "sayac"):
                    save_anchor(self.anchor_right, self.anchor_top)
                return
            self.act(dg["action"], dg["data"], dbl=False)
            return
        if msg == WM_LBUTTONDBLCLK:
            action, data = self.hit_test(x, y)
            if action == "oge":
                open_path(data)
            return
        if msg == WM_RBUTTONUP:
            action, data = self.hit_test(x, y)
            if action == "oge":
                self.remove(data)
            return

    def _on_timer(self):
        # boşta saydamlaş
        if not self.hover and not self.idle and time.time() - self.last_activity > 2.6:
            self.idle = True
            self.fade_alpha = 102
            self.render()
        elif self.hover and self.fade_alpha != 255:
            self.fade_alpha = 255
            self.render()


# ---------------------------------------------------------------------------
# Tepsi simgesi
# ---------------------------------------------------------------------------
def app_tray_icon():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    img.alpha_composite(CLIP_IMG.resize((44, int(44 * CLIP_IMG.height / CLIP_IMG.width)), Image.LANCZOS),
                        (10, 4))
    return img


def start_tray(app):
    try:
        import pystray
    except ImportError:
        return None

    def on_show(icon, item):
        user32.PostMessageW(app.hwnd, WM_ATAC_SHOW, 0, 0)

    def on_autostart(icon, item):
        set_autostart(not autostart_enabled())

    def on_update(icon, item):
        threading.Thread(target=lambda: manual_update(app), daemon=True).start()

    def on_quit(icon, item):
        icon.stop()
        user32.PostMessageW(app.hwnd, WM_ATAC_QUIT, 0, 0)

    menu = pystray.Menu(
        pystray.MenuItem(f"Ataç — sürüm {APP_VERSION}", None, enabled=False),
        pystray.MenuItem("Ataç'ı göster / gizle", on_show, default=True),
        pystray.MenuItem("Windows açılışında başlat", on_autostart, checked=lambda it: autostart_enabled()),
        pystray.MenuItem("Güncellemeleri denetle", on_update),
        pystray.MenuItem("Çıkış", on_quit),
    )
    icon = pystray.Icon("Atac", app_tray_icon(), "Ataç — sürükle-bırak rafı", menu)
    icon.run_detached()
    return icon


def manual_update(app):
    if not can_self_update():
        log("elle güncelleme: paketlenmiş sürüm değil")
        return
    try:
        upd = find_update(APP_VERSION)
        if not upd:
            log("elle güncelleme: güncel")
            return
        tag, url, size = upd
        new_exe = download_update(url, size)
        apply_update(new_exe, tag)
        user32.PostMessageW(app.hwnd, WM_ATAC_QUIT, 0, 0)
    except Exception:
        log_err()


def auto_update_worker(app):
    if not can_self_update():
        return
    time.sleep(6)
    try:
        upd = find_update(APP_VERSION)
        if upd:
            tag, url, size = upd
            new_exe = download_update(url, size)
            apply_update(new_exe, tag)
            user32.PostMessageW(app.hwnd, WM_ATAC_QUIT, 0, 0)
    except Exception:
        log_err()


# ---------------------------------------------------------------------------
# Tek örnek + giriş
# ---------------------------------------------------------------------------
def single_instance():
    single_instance.handle = kernel32.CreateMutexW(None, False, "Atac-Float-Shelf-gokhangeo")
    return ctypes.get_last_error() != 183  # ERROR_ALREADY_EXISTS


def main():
    try:
        ctypes.windll.shcore.SetProcessDpiAwareness(2)
    except Exception:
        pass

    args = sys.argv[1:]
    start_hidden = "--gizli-baslat" in args

    if args[:1] == ["--updated"] and len(args) >= 2:
        if not finish_update(args[1]):
            sys.exit(0)
    elif not single_instance():
        sys.exit(0)

    app = Atac(start_hidden=start_hidden)
    start_tray(app)
    threading.Thread(target=lambda: auto_update_worker(app), daemon=True).start()

    # mesaj döngüsü
    msg = wt.MSG()
    while user32.GetMessageW(ctypes.byref(msg), None, 0, 0) > 0:
        user32.TranslateMessage(ctypes.byref(msg))
        user32.DispatchMessageW(ctypes.byref(msg))


if __name__ == "__main__":
    try:
        main()
    except Exception:
        log_err()
        raise
