// FotoRea yapay zekâ ara sunucusu (Cloudflare Worker).
// Replicate API anahtarı yalnızca burada, Cloudflare'in gizli değişkeninde durur; uygulamanın içine konmaz.
//
// Cloudflare'de tanımlanacak değişkenler (Settings > Variables and Secrets):
//   REPLICATE_API_TOKEN  (Secret) : replicate.com > Account > API tokens
//   UYGULAMA_ANAHTARI    (Secret) : kendi belirlediğiniz parola; uygulamadaki Ayarlar'a da aynısı yazılır
//
// Uygulama POST /uret ile { istem, foto (JPEG data URL), tohum } gönderir, karşılığında JPEG görüntü alır.

const MODEL = "black-forest-labs/flux-kontext-pro";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, X-Uygulama-Anahtari",
};

function hata(mesaj, durum) {
  return new Response(JSON.stringify({ hata: mesaj }), {
    status: durum, headers: { ...CORS, "Content-Type": "application/json; charset=utf-8" },
  });
}

export default {
  async fetch(istek, env) {
    if (istek.method === "OPTIONS") return new Response(null, { headers: CORS });
    const url = new URL(istek.url);
    if (istek.method === "GET" && url.pathname === "/") return new Response("FotoRea AI sunucusu çalışıyor.", { headers: CORS });
    if (istek.method !== "POST" || url.pathname !== "/uret") return hata("Bulunamadı", 404);

    if (!env.REPLICATE_API_TOKEN) return hata("Sunucuda REPLICATE_API_TOKEN tanımlı değil.", 500);
    if (env.UYGULAMA_ANAHTARI && istek.headers.get("X-Uygulama-Anahtari") !== env.UYGULAMA_ANAHTARI) {
      return hata("Uygulama anahtarı hatalı. Ayarlar'daki anahtarı kontrol edin.", 401);
    }

    let govde;
    try { govde = await istek.json(); } catch (e) { return hata("Geçersiz istek.", 400); }
    const { istem, foto, tohum } = govde || {};
    if (!istem || typeof foto !== "string" || !foto.startsWith("data:image/")) return hata("İstem ve fotoğraf gerekli.", 400);
    if (foto.length > 8_000_000) return hata("Fotoğraf çok büyük.", 413);

    const basliklar = { Authorization: "Bearer " + env.REPLICATE_API_TOKEN, "Content-Type": "application/json" };
    let tahmin = await (await fetch(`https://api.replicate.com/v1/models/${MODEL}/predictions`, {
      method: "POST",
      headers: { ...basliklar, Prefer: "wait=60" },
      body: JSON.stringify({
        input: { prompt: istem, input_image: foto, output_format: "jpg", safety_tolerance: 2, seed: Number(tohum) || undefined },
      }),
    })).json();

    // 60 saniyede bitmediyse bitene kadar (en çok ~3 dk) sor
    for (let i = 0; i < 90 && tahmin.status && !["succeeded", "failed", "canceled"].includes(tahmin.status); i++) {
      await new Promise((r) => setTimeout(r, 2000));
      tahmin = await (await fetch(tahmin.urls.get, { headers: basliklar })).json();
    }
    if (tahmin.status !== "succeeded") {
      return hata("Yapay zekâ sonucu üretilemedi: " + (tahmin.error || tahmin.detail || tahmin.status || "bilinmeyen hata"), 502);
    }

    const cikti = Array.isArray(tahmin.output) ? tahmin.output[0] : tahmin.output;
    const gorsel = await fetch(cikti);
    if (!gorsel.ok) return hata("Sonuç görüntüsü indirilemedi.", 502);
    return new Response(gorsel.body, { headers: { ...CORS, "Content-Type": gorsel.headers.get("Content-Type") || "image/jpeg" } });
  },
};
