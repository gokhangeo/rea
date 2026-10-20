'use strict';

const liste = document.getElementById('liste');
const bos = document.getElementById('bos');
const alt = document.getElementById('alt');
const sayac = document.getElementById('sayac');
const tumunuMetin = document.getElementById('tumunuMetin');
const atilan = document.getElementById('atilan');
const ekleBtn = document.getElementById('ekleBtn');
const ekleMenu = document.getElementById('ekleMenu');
const gizleBtn = document.getElementById('gizleBtn');
const temizle = document.getElementById('temizle');
const tumunuSuruk = document.getElementById('tumunuSuruk');

let ogeler = []; // { id, path, name, isDir, size }
let sayacId = 0;

const KLASOR_IKON = '📁';
function dosyaIkon(ad) {
  const e = (ad.split('.').pop() || '').toLowerCase();
  if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'tiff', 'svg', 'heic'].includes(e)) return '🖼️';
  if (['mp4', 'mov', 'mkv', 'webm', 'avi'].includes(e)) return '🎬';
  if (['mp3', 'wav', 'flac', 'aac', 'm4a', 'ogg'].includes(e)) return '🎵';
  if (['pdf'].includes(e)) return '📕';
  if (['doc', 'docx', 'txt', 'rtf', 'odt'].includes(e)) return '📝';
  if (['xls', 'xlsx', 'csv'].includes(e)) return '📊';
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(e)) return '🗜️';
  if (['exe', 'msi'].includes(e)) return '⚙️';
  return '📄';
}

function boyutYaz(b) {
  if (!b || b <= 0) return '';
  const u = ['B', 'KB', 'MB', 'GB', 'TB'];
  let i = 0;
  let n = b;
  while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; }
  return (i === 0 ? n : n.toFixed(1)) + ' ' + u[i];
}

function temelAd(p) {
  const parts = p.replace(/[\\/]+$/, '').split(/[\\/]/);
  return parts[parts.length - 1] || p;
}

function guncelle() {
  const n = ogeler.length;
  bos.classList.toggle('gizli', n > 0);
  alt.classList.toggle('gizli', n === 0);
  sayac.textContent = n === 0 ? 'Boş' : n + ' öğe ataçta';
  tumunuMetin.textContent = 'Tümünü sürükle (' + n + ')';
}

function ogeEkleDom(oge) {
  const li = document.createElement('li');
  li.className = 'oge';
  li.draggable = true;
  li.dataset.id = oge.id;
  li.title = oge.path;

  const ikon = document.createElement('div');
  ikon.className = 'oge-ikon';
  ikon.textContent = oge.isDir ? KLASOR_IKON : dosyaIkon(oge.name);

  const bilgi = document.createElement('div');
  bilgi.className = 'oge-bilgi';
  const ad = document.createElement('div');
  ad.className = 'oge-ad';
  ad.textContent = oge.name;
  const altMetin = document.createElement('div');
  altMetin.className = 'oge-alt';
  altMetin.textContent = oge.isDir ? 'Klasör' : boyutYaz(oge.size) || 'Dosya';
  bilgi.appendChild(ad);
  bilgi.appendChild(altMetin);

  const sil = document.createElement('button');
  sil.className = 'oge-sil';
  sil.textContent = '×';
  sil.title = 'Kaldır';
  sil.addEventListener('click', (e) => {
    e.stopPropagation();
    kaldir(oge.id, li);
  });

  li.appendChild(ikon);
  li.appendChild(bilgi);
  li.appendChild(sil);

  // Dışarı sürükle (Gezgin/uygulama) — tek öğe
  li.addEventListener('dragstart', (e) => {
    e.preventDefault();
    window.atac.startDrag([oge.path]);
  });

  // Çift tık = aç
  li.addEventListener('dblclick', () => window.atac.openPath(oge.path));

  liste.appendChild(li);
}

function kaldir(id, li) {
  ogeler = ogeler.filter((o) => o.id !== id);
  if (li) {
    li.classList.add('cikiyor');
    setTimeout(() => { li.remove(); guncelle(); }, 190);
  } else {
    guncelle();
  }
}

async function yollariEkle(yollar) {
  let eklendi = 0;
  for (const p of yollar) {
    if (!p) continue;
    if (ogeler.some((o) => o.path === p)) continue;
    const st = await window.atac.stat(p);
    const oge = {
      id: ++sayacId,
      path: p,
      name: temelAd(p),
      isDir: st.isDir,
      size: st.size,
    };
    ogeler.push(oge);
    ogeEkleDom(oge);
    eklendi++;
  }
  if (eklendi) guncelle();
}

// ---- Sürükle-bırak (içeri) ----
let suruDerinlik = 0;
window.addEventListener('dragenter', (e) => {
  e.preventDefault();
  suruDerinlik++;
  document.body.classList.add('suruk-ustunde');
});
window.addEventListener('dragover', (e) => {
  e.preventDefault();
  e.dataTransfer.dropEffect = 'copy';
});
window.addEventListener('dragleave', (e) => {
  e.preventDefault();
  suruDerinlik--;
  if (suruDerinlik <= 0) {
    suruDerinlik = 0;
    document.body.classList.remove('suruk-ustunde');
  }
});
window.addEventListener('drop', async (e) => {
  e.preventDefault();
  suruDerinlik = 0;
  document.body.classList.remove('suruk-ustunde');
  const yollar = [];
  for (const f of e.dataTransfer.files) {
    const p = window.atac.pathForFile(f);
    if (p) yollar.push(p);
  }
  await yollariEkle(yollar);
});

// ---- Tümünü dışarı sürükle ----
tumunuSuruk.addEventListener('dragstart', (e) => {
  e.preventDefault();
  if (ogeler.length) window.atac.startDrag(ogeler.map((o) => o.path));
});

// ---- + menü ----
function menuAc(g0ster) {
  ekleMenu.classList.toggle('gizli', !g0ster);
}
ekleBtn.addEventListener('click', (e) => {
  e.stopPropagation();
  menuAc(ekleMenu.classList.contains('gizli'));
});
document.addEventListener('click', () => menuAc(false));
ekleMenu.addEventListener('click', (e) => e.stopPropagation());
ekleMenu.querySelectorAll('button').forEach((b) => {
  b.addEventListener('click', async () => {
    menuAc(false);
    const yollar = await window.atac.pick(b.dataset.mode);
    await yollariEkle(yollar);
  });
});

// ---- Alt düğmeler ----
temizle.addEventListener('click', () => {
  ogeler = [];
  liste.innerHTML = '';
  guncelle();
});
gizleBtn.addEventListener('click', () => window.atac.hide());

guncelle();
