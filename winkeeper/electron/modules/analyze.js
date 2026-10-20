const fsp = require('fs').promises;
const path = require('path');
const { shell } = require('electron');
const si = require('systeminformation');

const TOP_FILES = 40;
const LARGEST = 150;
let tree = null;
let index = new Map(); // küçük harfli yol -> düğüm
let cancelled = false;
let largest = [];
let types = new Map();

const TYPE_GROUPS = {
  Video: ['.mp4', '.mkv', '.avi', '.mov', '.wmv', '.webm', '.m4v', '.flv'],
  'Ses': ['.mp3', '.wav', '.flac', '.aac', '.ogg', '.m4a', '.wma'],
  'Görsel': ['.jpg', '.jpeg', '.png', '.gif', '.bmp', '.webp', '.heic', '.raw', '.psd', '.tif', '.tiff', '.svg'],
  'Belge': ['.pdf', '.doc', '.docx', '.xls', '.xlsx', '.ppt', '.pptx', '.txt', '.odt', '.csv', '.md', '.epub'],
  'Arşiv': ['.zip', '.rar', '.7z', '.tar', '.gz', '.iso', '.cab', '.xz', '.bz2'],
  'Program': ['.exe', '.dll', '.msi', '.sys', '.so', '.appx', '.msix', '.jar'],
  'Kod': ['.js', '.ts', '.py', '.c', '.cpp', '.h', '.cs', '.java', '.go', '.rs', '.json', '.html', '.css', '.map'],
  'Disk görüntüsü': ['.vhd', '.vhdx', '.vmdk', '.img', '.wim', '.esd'],
};
const EXT_GROUP = new Map(Object.entries(TYPE_GROUPS).flatMap(([g, exts]) => exts.map((e) => [e, g])));

async function drives() {
  const list = await si.fsSize().catch(() => []);
  return list.filter((d) => /^[A-Z]:$/i.test(d.mount)).map((d) => ({ mount: d.mount + '\\', size: d.size, used: d.used }));
}

function addLargest(f) {
  if (largest.length >= LARGEST && f.size <= largest[largest.length - 1].size) return;
  largest.push(f);
  largest.sort((a, b) => b.size - a.size);
  if (largest.length > LARGEST) largest.pop();
}

async function scan(root, onProgress) {
  cancelled = false;
  index = new Map();
  largest = [];
  types = new Map();
  let files = 0;
  let bytes = 0;
  let last = 0;

  async function walk(dir, name) {
    const node = { name, path: dir, size: 0, count: 0, dirs: [], files: [], other: { count: 0, size: 0 } };
    index.set(dir.toLowerCase(), node);
    if (cancelled) return node;
    let ents;
    try { ents = await fsp.readdir(dir, { withFileTypes: true }); } catch { return node; }
    const filePaths = [];
    const subdirs = [];
    for (const e of ents) {
      if (e.isSymbolicLink()) continue;
      if (e.isDirectory()) subdirs.push(e.name);
      else if (e.isFile()) filePaths.push(e.name);
    }
    const stats = await Promise.all(filePaths.map((n) => fsp.lstat(path.join(dir, n)).then((s) => ({ name: n, size: s.size, mtime: s.mtimeMs })).catch(() => null)));
    const fl = stats.filter(Boolean).sort((a, b) => b.size - a.size);
    for (const f of fl) {
      const g = EXT_GROUP.get(path.extname(f.name).toLowerCase()) || 'Diğer';
      const t = types.get(g) || { size: 0, count: 0 };
      t.size += f.size; t.count++;
      types.set(g, t);
      addLargest({ path: path.join(dir, f.name), name: f.name, size: f.size, mtime: f.mtime });
    }
    node.files = fl.slice(0, TOP_FILES).map(({ name: n, size }) => ({ name: n, size }));
    for (const f of fl.slice(TOP_FILES)) { node.other.count++; node.other.size += f.size; }
    node.size = fl.reduce((s, f) => s + f.size, 0);
    node.count = fl.length;
    files += fl.length;
    bytes += node.size;
    const now = Date.now();
    if (now - last > 200) { last = now; onProgress({ files, bytes, current: dir }); }
    for (const sd of subdirs) {
      const child = await walk(path.join(dir, sd), sd);
      node.dirs.push(child);
      node.size += child.size;
      node.count += child.count;
    }
    node.dirs.sort((a, b) => b.size - a.size);
    return node;
  }

  const resolved = path.resolve(root);
  tree = await walk(resolved, resolved);
  return { cancelled, root: resolved, size: tree.size, files };
}

function cancel() { cancelled = true; }

function view(node, depth) {
  const children = [
    ...node.dirs.map((d) => ({ type: 'dir', name: d.name, path: d.path, size: d.size, count: d.count, children: depth > 0 ? view(d, depth - 1).children.slice(0, 24) : undefined })),
    ...node.files.map((f) => ({ type: 'file', name: f.name, path: path.join(node.path, f.name), size: f.size })),
  ];
  if (node.other.count) children.push({ type: 'other', name: `${node.other.count} küçük dosya`, path: null, size: node.other.size });
  children.sort((a, b) => b.size - a.size);
  return { name: node.name, path: node.path, size: node.size, count: node.count, children: children.slice(0, 70) };
}

function getNode(p) {
  const n = p ? index.get(path.resolve(p).toLowerCase()) : tree;
  return n ? view(n, 1) : null;
}

function summary() {
  return {
    largest: largest.slice(0, 100),
    types: [...types.entries()].map(([name, v]) => ({ name, ...v })).sort((a, b) => b.size - a.size),
  };
}

function inTree(p) {
  if (!tree || !p) return false;
  const rel = path.relative(tree.path, path.resolve(p));
  return !!rel && !rel.startsWith('..') && !path.isAbsolute(rel);
}

function reveal(p) {
  if (inTree(p) || (tree && path.resolve(p) === tree.path)) shell.showItemInFolder(p);
}

function openItem(p) {
  if (inTree(p)) shell.openPath(p);
}

async function trash(p) {
  if (!inTree(p)) return { ok: false, error: 'Bu yol taranan klasörün içinde değil' };
  try {
    await shell.trashItem(p);
  } catch (e) {
    return { ok: false, error: e.message };
  }
  const full = path.resolve(p);
  const parentPath = path.dirname(full);
  const parent = index.get(parentPath.toLowerCase());
  let removed = 0;
  if (parent) {
    const di = parent.dirs.findIndex((d) => d.path === full);
    if (di >= 0) { removed = parent.dirs[di].size; parent.dirs.splice(di, 1); index.delete(full.toLowerCase()); }
    const fi = parent.files.findIndex((f) => path.join(parent.path, f.name) === full);
    if (fi >= 0) { removed = parent.files[fi].size; parent.files.splice(fi, 1); }
    let cur = parentPath;
    while (true) {
      const n = index.get(cur.toLowerCase());
      if (!n) break;
      n.size -= removed;
      if (n === tree) break;
      cur = path.dirname(cur);
    }
  }
  largest = largest.filter((f) => f.path !== full && !f.path.toLowerCase().startsWith(full.toLowerCase() + path.sep));
  return { ok: true, removed };
}

module.exports = { drives, scan, cancel, getNode, summary, reveal, openItem, trash };
