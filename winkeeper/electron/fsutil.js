const fs = require('fs');
const fsp = fs.promises;
const path = require('path');

// Klasör boyutunu hesaplar; sembolik bağlantı ve junction'ları izlemez.
async function dirSize(root) {
  let size = 0;
  let count = 0;
  const stack = [root];
  while (stack.length) {
    const dir = stack.pop();
    let ents;
    try { ents = await fsp.readdir(dir, { withFileTypes: true }); } catch { continue; }
    const files = [];
    for (const e of ents) {
      if (e.isSymbolicLink()) continue;
      const p = path.join(dir, e.name);
      if (e.isDirectory()) stack.push(p);
      else if (e.isFile()) files.push(p);
    }
    const stats = await Promise.all(files.map((f) => fsp.lstat(f).catch(() => null)));
    for (const s of stats) if (s) { size += s.size; count++; }
  }
  return { size, count };
}

async function entrySize(p) {
  let st;
  try { st = await fsp.lstat(p); } catch { return null; }
  if (st.isSymbolicLink()) return null;
  if (st.isDirectory()) return { ...(await dirSize(p)), isDir: true };
  return { size: st.size, count: 1, isDir: false };
}

// "C:\Users\*\Cache" gibi tek '*' içeren segmentleri genişletir.
async function expand(pattern) {
  const parts = pattern.split(/[\\/]/).filter(Boolean);
  let bases = [parts[0] + path.sep];
  for (let i = 1; i < parts.length; i++) {
    const seg = parts[i];
    const next = [];
    for (const b of bases) {
      if (seg.includes('*')) {
        const re = new RegExp('^' + seg.replace(/[.+^${}()|[\]\\]/g, '\\$&').replace(/\*/g, '.*') + '$', 'i');
        let ents = [];
        try { ents = await fsp.readdir(b); } catch {}
        for (const n of ents) if (re.test(n)) next.push(path.join(b, n));
      } else {
        const p = path.join(b, seg);
        if (fs.existsSync(p)) next.push(p);
      }
    }
    bases = next;
  }
  return bases;
}

// Kilitli dosyaları atlayarak elden geldiğince siler; serbest kalan bayt sayısını döndürür.
async function rmBest(p) {
  let st;
  try { st = await fsp.lstat(p); } catch { return 0; }
  if (st.isSymbolicLink()) return 0;
  if (st.isDirectory()) {
    let freed = 0;
    let names = [];
    try { names = await fsp.readdir(p); } catch {}
    for (const n of names) freed += await rmBest(path.join(p, n));
    try { await fsp.rmdir(p); } catch {}
    return freed;
  }
  try { await fsp.unlink(p); return st.size; } catch { return 0; }
}

module.exports = { dirSize, entrySize, expand, rmBest };
