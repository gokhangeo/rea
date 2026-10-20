'use strict';

const serit = document.getElementById('serit');
const liste = document.getElementById('liste');
const bos = document.getElementById('bos');
const alt = document.getElementById('alt');
const sayac = document.getElementById('sayac');
const atilan = document.getElementById('atilan');
const ekleBtn = document.getElementById('ekle');
const ekleMenu = document.getElementById('ekleMenu');
const kapatBtn = document.getElementById('kapat');
const temizle = document.getElementById('temizle');
const tumunu = document.getElementById('tumunu');
const sayacSatir = document.getElementById('sayacSatir');

let ogeler = [];
let sayacId = 0;

const RESIM = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'avif', 'svg'];

function uzanti(ad) { return (ad.split('.').pop() || '').toLowerCase(); }

function glif(ad) {
  const e = uzanti(ad);
  if (['mp4', 'mov', 'mkv', 'webm', 'avi'].includes(e)) return '🎬';
  if (['mp3', 'wav', 'flac', 'aac', 'm4a', 'ogg'].includes(e)) return '🎵';
  if (e === 'pdf') return '📕';
  if (['doc', 'docx', 'txt', 'rtf', 'odt'].includes(e)) return '📝';
  if (['xls', 'xlsx', 'csv'].includes(e)) return '📊';
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(e)) return '🗜️';
  if (['exe', 'msi'].includes(e)) return '⚙️';
  return '📄';
}

function temelAd(p) {
  const parts = p.replace(/[\\/]+$/, '').split(/[\\/]/);
  return parts[parts.length - 1] || p;
}

// Uzun adları ortadan kısalt: "HU-mZ5bbl…ped.jpg"
function kisaAd(ad) {
  if (ad.length <= 18) return ad;
  return ad.slice(0, 9) + '…' + ad.slice(-7);
}

function dosyaUrl(p) {
  return encodeURI('file:///' + p.replace(/\\/g, '/')) + '?t=' + Date.now();
}

function guncelle() {
  const n = ogeler.length;
  bos.classList.toggle('gizli', n > 0);
  alt.classList.toggle('gizli', n === 0);
  sayac.textContent = n + ' Öğe';
}

function ogeEkleDom(oge) {
  const li = document.createElement('li');
  li.className = 'oge';
  li.draggable = true;
  li.dataset.id = oge.id;
  li.title = oge.path;

  const kck = document.createElement('div');
  kck.className = 'kck' + (oge.isDir ? ' klasor' : '');
  if (oge.isDir) {
    kck.innerHTML = '<span class="glif">📁</span>';
  } else if (RESIM.includes(uzanti(oge.name)) && uzanti(oge.name) !== 'svg') {
    const img = document.createElement('img');
    img.src = dosyaUrl(oge.path);
    img.onerror = () => { kck.innerHTML = '<span class="glif">' + glif(oge.name) + '</span>'; };
    kck.appendChild(img);
  } else {
    kck.innerHTML = '<span class="glif">' + glif(oge.name) + '</span>';
  }

  const ad = document.createElement('div');
  ad.className = 'ad';
  ad.textContent = kisaAd(oge.name);

  const sil = document.createElement('button');
  sil.className = 'sil';
  sil.textContent = '×';
  sil.title = 'Kaldır';
  sil.addEventListener('click', (e) => { e.stopPropagation(); kaldir(oge.id, li); });

  li.appendChild(kck);
  li.appendChild(ad);
  li.appendChild(sil);

  li.addEventListener('dragstart', (e) => { e.preventDefault(); window.atac.startDrag([oge.path]); });
  li.addEventListener('dblclick', () => window.atac.openPath(oge.path));

  liste.appendChild(li);
}

function kaldir(id, li) {
  ogeler = ogeler.filter((o) => o.id !== id);
  if (li) { li.classList.add('cikiyor'); setTimeout(() => { li.remove(); guncelle(); }, 190); }
  else guncelle();
}

async function yollariEkle(yollar) {
  let eklendi = 0;
  for (const p of yollar) {
    if (!p || ogeler.some((o) => o.path === p)) continue;
    const st = await window.atac.stat(p);
    const oge = { id: ++sayacId, path: p, name: temelAd(p), isDir: st.isDir, size: st.size };
    ogeler.push(oge);
    ogeEkleDom(oge);
    eklendi++;
  }
  if (eklendi) { serit.classList.remove('kapali'); guncelle(); }
}

// Sürükle-bırak (içeri)
let derinlik = 0;
window.addEventListener('dragenter', (e) => { e.preventDefault(); derinlik++; document.body.classList.add('suruk'); });
window.addEventListener('dragover', (e) => { e.preventDefault(); e.dataTransfer.dropEffect = 'copy'; });
window.addEventListener('dragleave', (e) => { e.preventDefault(); if (--derinlik <= 0) { derinlik = 0; document.body.classList.remove('suruk'); } });
window.addEventListener('drop', async (e) => {
  e.preventDefault(); derinlik = 0; document.body.classList.remove('suruk');
  const yollar = [];
  for (const f of e.dataTransfer.files) { const p = window.atac.pathForFile(f); if (p) yollar.push(p); }
  await yollariEkle(yollar);
});

// Tümünü dışarı sürükle
tumunu.addEventListener('dragstart', (e) => { e.preventDefault(); if (ogeler.length) window.atac.startDrag(ogeler.map((o) => o.path)); });

// + menü
function menu(goster) { ekleMenu.classList.toggle('gizli', !goster); }
ekleBtn.addEventListener('click', (e) => { e.stopPropagation(); menu(ekleMenu.classList.contains('gizli')); });
document.addEventListener('click', () => menu(false));
ekleMenu.addEventListener('click', (e) => e.stopPropagation());
ekleMenu.querySelectorAll('button').forEach((b) => {
  b.addEventListener('click', async () => { menu(false); const y = await window.atac.pick(b.dataset.mode); await yollariEkle(y); });
});

// Aç/kapat (liste)
sayacSatir.addEventListener('click', () => serit.classList.toggle('kapali'));

// Alt düğmeler
temizle.addEventListener('click', () => { ogeler = []; liste.innerHTML = ''; guncelle(); });
kapatBtn.addEventListener('click', () => window.atac.hide());

guncelle();
