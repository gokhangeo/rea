package com.gokhan.evekrani

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.util.concurrent.Executors

/**
 * Telefondan gönderilen fotoğraflar: <id>.jpg ve hazır olunca derinlik haritası <id>.depth.png.
 * Derinlik kutunun kendisinde, Depth Anything V2 (küçük) modeliyle bir kez çıkarılır; fotoğraf dışarı gitmez.
 */
class PhotoStore(context: Context) {
    val dir = File(context.filesDir, "photos").apply { mkdirs() }
    private val depth = DepthEngine(context, this)

    fun list(): JSONArray = JSONArray().apply {
        dir.listFiles { f -> f.name.endsWith(".jpg") }.orEmpty().sortedByDescending { it.lastModified() }.forEach { f ->
            val id = f.nameWithoutExtension
            put(JSONObject().put("id", id).put("depth", depthFile(id).exists()))
        }
    }

    fun photoFile(id: String) = File(dir, "$id.jpg")
    fun depthFile(id: String) = File(dir, "$id.depth.png")

    fun status(): String = depth.status

    /** Yüklenen dosyayı dik çevirip en fazla 1600 piksele küçültür ve kaydeder. */
    fun add(upload: File): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(upload.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
        var bmp = BitmapFactory.decodeFile(upload.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val rotation = when (runCatching { ExifInterface(upload.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val scale = minOf(1f, 1600f / maxOf(bmp.width, bmp.height))
        if (rotation != 0f || scale < 1f) {
            val m = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        val id = System.currentTimeMillis().toString(36)
        photoFile(id).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bmp.recycle()
        depth.enqueue(id)
        return id
    }

    fun delete(id: String) {
        if (!id.matches(Regex("[a-z0-9]+"))) return
        photoFile(id).delete()
        depthFile(id).delete()
    }

    /** Açılışta derinliği eksik kalan fotoğrafları sıraya koyar. */
    fun resume() {
        dir.listFiles { f -> f.name.endsWith(".jpg") }.orEmpty().map { it.nameWithoutExtension }
            .filter { !depthFile(it).exists() }.forEach(depth::enqueue)
    }
}

private class DepthEngine(private val context: Context, private val store: PhotoStore) {
    private val worker = Executors.newSingleThreadExecutor()
    private var session: OrtSession? = null
    @Volatile var status = "Hazır"

    fun enqueue(id: String) {
        worker.execute {
            try {
                if (!store.photoFile(id).exists() || store.depthFile(id).exists()) return@execute
                status = "3D hazırlanıyor"
                process(id)
                status = "Hazır"
            } catch (t: Throwable) {
                Log.e(TAG, "derinlik çıkarılamadı: $id", t)
                status = "3D hazırlanamadı: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

    private fun model(): OrtSession {
        session?.let { return it }
        val file = File(context.filesDir, "depth.onnx")
        if (!file.exists()) download(file)
        val env = OrtEnvironment.getEnvironment()
        return env.createSession(file.path, OrtSession.SessionOptions()).also { session = it }
    }

    private fun download(target: File) {
        val tmp = File(target.path + ".part")
        val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var lastShown = -1L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    val pct = if (total > 0) done * 100 / total else done / 1_000_000
                    if (pct != lastShown) {
                        status = if (total > 0) "3D modeli indiriliyor %$pct" else "3D modeli indiriliyor ${pct} MB"
                        lastShown = pct
                    }
                }
            }
        }
        if (total > 0 && tmp.length() != total) {
            tmp.delete()
            error("indirme yarım kaldı")
        }
        tmp.renameTo(target)
    }

    private fun process(id: String) {
        val photo = BitmapFactory.decodeFile(store.photoFile(id).path) ?: return
        val w = photo.width
        val h = photo.height
        val input = Bitmap.createScaledBitmap(photo, SIZE, SIZE, true)
        photo.recycle()
        val px = IntArray(SIZE * SIZE)
        input.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        input.recycle()
        val plane = SIZE * SIZE
        val data = FloatBuffer.allocate(3 * plane)
        for (i in 0 until plane) {
            val c = px[i]
            data.put(i, (((c shr 16) and 0xff) / 255f - MEAN[0]) / STD[0])
            data.put(plane + i, (((c shr 8) and 0xff) / 255f - MEAN[1]) / STD[1])
            data.put(2 * plane + i, ((c and 0xff) / 255f - MEAN[2]) / STD[2])
        }
        val s = model()
        val env = OrtEnvironment.getEnvironment()
        val out = OnnxTensor.createTensor(env, data, longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())).use { tensor ->
            s.run(mapOf(s.inputNames.first() to tensor)).use { result ->
                val fb = (result.get(0) as OnnxTensor).floatBuffer
                FloatArray(fb.remaining()).also { fb.get(it) }
            }
        }
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for (v in out) { if (v < lo) lo = v; if (v > hi) hi = v }
        val range = (hi - lo).takeIf { it > 1e-6f } ?: 1f
        for (i in 0 until plane) {
            val g = ((out[i] - lo) / range * 255f).toInt().coerceIn(0, 255)
            px[i] = (0xff shl 24) or (g shl 16) or (g shl 8) or g
        }
        val depthSquare = Bitmap.createBitmap(px, SIZE, SIZE, Bitmap.Config.ARGB_8888)
        // Fotoğrafın dörtte biri boyutuna yumuşak büyütme: kenarlarda yırtılmayı azaltan hafif bir bulanıklık verir.
        val depthMap = Bitmap.createScaledBitmap(depthSquare, maxOf(1, w / 4), maxOf(1, h / 4), true)
        depthSquare.recycle()
        val tmp = File(store.dir, "$id.depth.tmp")
        tmp.outputStream().use { depthMap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        depthMap.recycle()
        tmp.renameTo(store.depthFile(id))
    }

    companion object {
        private const val TAG = "EvEkrani"
        private const val SIZE = 518
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
        private const val MODEL_URL = "https://github.com/fabio-sim/Depth-Anything-ONNX/releases/download/v2.0.0/depth_anything_v2_vits.onnx"
    }
}
