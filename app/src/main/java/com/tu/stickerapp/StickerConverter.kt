package com.tu.stickerapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class StickerConverter {

    companion object {
        private const val TAG = "StickerConverter"
        // WhatsApp rechaza animados > 500 KB; dejamos margen de 10 KB
        private const val MAX_BYTES = 490 * 1024
        private const val CANVAS_PX = 512
    }

    fun convertToAnimatedWebP(
        context: Context,
        inputUri: Uri,
        maxDurationMs: Long = 3000L,
        fps: Int = 10,
        sizePx: Int = CANVAS_PX,
        keepAspectRatio: Boolean = false
    ): String? {
        return try {
            Log.d(TAG, "Iniciando conversión")
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, inputUri)

            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: maxDurationMs

            val clipMs = minOf(durationMs, maxDurationMs)
            val totalFrames = (clipMs * fps / 1000L).toInt().coerceAtLeast(1)
            val frameDelayMs = (1000.0 / fps).toLong()

            Log.d(TAG, "Duration: ${durationMs}ms, clip: ${clipMs}ms, frames: $totalFrames")

            val frames = mutableListOf<Bitmap>()
            for (i in 0 until totalFrames) {
                val timeUs = i.toLong() * 1_000_000L / fps
                val raw = retriever.getFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST
                ) ?: continue

                val frame = if (keepAspectRatio) scaleKeepAspectOnCanvas(raw, sizePx)
                            else scaleCenterCrop(raw, sizePx)

                raw.recycle()
                frames.add(frame)
            }
            retriever.release()

            Log.d(TAG, "Frames extraídos: ${frames.size}")
            if (frames.isEmpty()) {
                Log.e(TAG, "Sin frames, abortando")
                return null
            }

            val outFile = File(context.cacheDir, "sticker_animated.webp")
            val ok = buildAnimatedWebP(frames, outFile, frameDelayMs.toInt())
            frames.forEach { it.recycle() }

            if (!ok) {
                Log.e(TAG, "No se pudo generar WebP dentro del límite de tamaño")
                return null
            }

            Log.d(TAG, "WebP escrito: ${outFile.absolutePath} (${outFile.length()} bytes)")
            outFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error en conversión", e)
            null
        }
    }

    // ── Scaling helpers ───────────────────────────────────────────────────────

    private fun scaleCenterCrop(src: Bitmap, sizePx: Int): Bitmap {
        val scale = maxOf(sizePx.toFloat() / src.width, sizePx.toFloat() / src.height)
        val w = (src.width * scale).toInt().coerceAtLeast(sizePx)
        val h = (src.height * scale).toInt().coerceAtLeast(sizePx)
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        val x = ((w - sizePx) / 2).coerceIn(0, w - sizePx)
        val y = ((h - sizePx) / 2).coerceIn(0, h - sizePx)
        val cropped = Bitmap.createBitmap(scaled, x, y, sizePx, sizePx)
        if (scaled != src) scaled.recycle()
        return cropped
    }

    private fun scaleKeepAspectOnCanvas(src: Bitmap, sizePx: Int): Bitmap {
        val scale = minOf(sizePx.toFloat() / src.width, sizePx.toFloat() / src.height)
        val sw = (src.width * scale).toInt().coerceAtLeast(2)
        val sh = (src.height * scale).toInt().coerceAtLeast(2)
        val scaled = Bitmap.createScaledBitmap(src, sw, sh, true)

        val canvas = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val cv = Canvas(canvas)
        cv.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        cv.drawBitmap(scaled, ((sizePx - sw) / 2f), ((sizePx - sh) / 2f), null)
        if (scaled != src) scaled.recycle()
        return canvas
    }

    /**
     * Aplana un bitmap ARGB_8888 sobre fondo negro para poder usar WEBP_LOSSY
     * (que no soporta canal alpha). Solo se llama cuando keepAspectRatio=true.
     */
    private fun flattenAlpha(src: Bitmap): Bitmap {
        val flat = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val cv = Canvas(flat)
        cv.drawColor(Color.BLACK)
        cv.drawBitmap(src, 0f, 0f, null)
        return flat
    }

    // ── WebP builder ──────────────────────────────────────────────────────────

    /**
     * Construye el WebP animado lossy. Retorna false si no puede quedar bajo MAX_BYTES.
     *
     * Estrategia de reducción: bajar fps efectivo saltando frames (1→6).
     * WEBP_LOSSY en vez de WEBP_LOSSLESS → ~6x menos peso por frame.
     */
    private fun buildAnimatedWebP(
        frames: List<Bitmap>,
        outFile: File,
        frameDelayMs: Int
    ): Boolean {
        Log.d(TAG, "Construyendo WebP animado con ${frames.size} frames, delay: ${frameDelayMs}ms")

        val attempts = listOf(
            Pair(1, 80),
            Pair(2, 80),
            Pair(3, 80),
            Pair(4, 80),
            Pair(5, 80),
            Pair(6, 80),
        )

        for ((skipFrames, quality) in attempts) {
            val selectedFrames = frames.filterIndexed { i, _ -> i % skipFrames == 0 }
            val effectiveDelay = frameDelayMs * skipFrames

            // WEBP_LOSSY no soporta alpha → aplanar transparencia antes de comprimir
            val readyFrames = selectedFrames.map { bmp ->
                if (bmp.hasAlpha()) flattenAlpha(bmp) else bmp
            }

            val vp8Chunks = try {
                readyFrames.map { bmp ->
                    val baos = ByteArrayOutputStream()
                    // FIX: WEBP_LOSSY en lugar de WEBP_LOSSLESS
                    bmp.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, baos)
                    stripToVP8Chunk(baos.toByteArray())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error codificando frames skip=$skipFrames q=$quality", e)
                readyFrames.forEach { if (it !== selectedFrames[readyFrames.indexOf(it)]) it.recycle() }
                continue
            } finally {
                // liberar bitmaps aplanados (no los originales)
                readyFrames.forEachIndexed { idx, bmp ->
                    if (bmp !== selectedFrames[idx]) bmp.recycle()
                }
            }

            val estimatedSize = vp8Chunks.sumOf { it.size } + 256
            Log.d(TAG, "Intento skip=$skipFrames q=$quality → ~${estimatedSize / 1024} KB")

            if (estimatedSize <= MAX_BYTES) {
                val width  = selectedFrames[0].width
                val height = selectedFrames[0].height
                writeRiff(vp8Chunks, width, height, effectiveDelay, outFile)
                Log.d(TAG, "Éxito con skip=$skipFrames q=$quality")
                return true
            }
        }

        return false
    }

    private fun writeRiff(
        vp8Chunks: List<ByteArray>,
        width: Int,
        height: Int,
        frameDelayMs: Int,
        outFile: File
    ) {
        Log.d(TAG, "Escribiendo RIFF ${width}x${height}, ${vp8Chunks.size} frames")

        fun le32(v: Int) = byteArrayOf(
            (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte()
        )
        fun le24(v: Int) = byteArrayOf(
            (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte()
        )
        fun le16(v: Int) = byteArrayOf(
            (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte()
        )
        fun tag(s: String) = s.toByteArray(Charsets.US_ASCII)

        val payload = ByteArrayOutputStream()

        fun chunk(name: String, data: ByteArray) {
            payload.write(tag(name))
            payload.write(le32(data.size))
            payload.write(data)
            if (data.size % 2 != 0) payload.write(0)
        }

        // VP8X: solo bit de animación (bit 1). Sin alpha (lossy no tiene canal alpha).
        chunk("VP8X",
            le32(0x00000002) +   // flags: animation only
            le24(width - 1) +
            le24(height - 1)
        )

        // ANIM: fondo negro opaco, loop infinito
        chunk("ANIM",
            le32(0xFF000000.toInt()) +  // background color: negro opaco
            le16(0)                      // loop count: 0 = infinito
        )

        for (vp8 in vp8Chunks) {
            // ANMF flags: bit 1 = no-blending (0x02). Sin dispose (no alpha).
            val anmfData =
                le24(0) +               // X / 2
                le24(0) +               // Y / 2
                le24(width - 1) +
                le24(height - 1) +
                le24(frameDelayMs) +    // delay ms
                byteArrayOf(0x02) +     // flags: no-blending
                vp8

            chunk("ANMF", anmfData)
        }

        val payloadBytes = payload.toByteArray()
        val riff = ByteArrayOutputStream()
        riff.write(tag("RIFF"))
        riff.write(le32(4 + payloadBytes.size))
        riff.write(tag("WEBP"))
        riff.write(payloadBytes)

        FileOutputStream(outFile).use { it.write(riff.toByteArray()) }
        Log.d(TAG, "RIFF total: ${riff.size()} bytes")
    }

    private fun stripToVP8Chunk(webpBytes: ByteArray): ByteArray {
        var i = 12
        while (i + 8 <= webpBytes.size) {
            val t = String(webpBytes, i, 4, Charsets.US_ASCII)
            val sz = (webpBytes[i+4].toInt() and 0xFF) or
                    ((webpBytes[i+5].toInt() and 0xFF) shl 8) or
                    ((webpBytes[i+6].toInt() and 0xFF) shl 16) or
                    ((webpBytes[i+7].toInt() and 0xFF) shl 24)
            if (t == "VP8 " || t == "VP8L") {
                return webpBytes.copyOfRange(i, i + 8 + sz + (sz % 2))
            }
            i += 8 + sz + (sz % 2)
        }
        throw IllegalStateException("No VP8/VP8L chunk found in WebP")
    }
}

private operator fun ByteArray.plus(other: ByteArray): ByteArray {
    val r = ByteArray(size + other.size)
    copyInto(r); other.copyInto(r, size)
    return r
}