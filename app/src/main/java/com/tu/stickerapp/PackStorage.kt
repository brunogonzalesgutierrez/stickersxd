package com.tu.stickerapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class PackStorage(private val context: Context) {

    companion object {
        private const val PREFS_NAME  = "pack_storage_v2"
        private const val KEY_PACKS   = "packs"
        private const val PREFS_VER   = "sticker_prefs"
    }

    private val prefs get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── CRUD ──────────────────────────────────────────────────────────────────

    fun loadPacks(): List<StickerPack> {
        val json = prefs.getString(KEY_PACKS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                runCatching { arr.getJSONObject(i).toStickerPack() }.getOrNull()
            }
        } catch (e: Exception) { emptyList() }
    }

    fun createPack(name: String, publisher: String = "StickerApp"): StickerPack {
        val id = "pack_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        File(context.cacheDir, "packs/$id").mkdirs()
        val pack = StickerPack(
            identifier        = id,
            name              = name,
            publisher         = publisher,
            trayImageFileName = "tray.webp",
            stickers          = emptyList(),
            isAnimated        = true
        )
        savePacks(loadPacks() + pack)
        return pack
    }

    /**
     * Copies [webpSourcePath] into the pack folder and appends a new Sticker entry.
     * Returns the updated pack, or null if the pack wasn't found or is already full (30).
     */
    fun addStickerToPack(packId: String, webpSourcePath: String, emoji: String = "😄"): StickerPack? {
        val packs = loadPacks().toMutableList()
        val idx = packs.indexOfFirst { it.identifier == packId }
        if (idx < 0 || packs[idx].stickers.size >= 30) return null

        val packDir  = File(context.cacheDir, "packs/$packId").also { it.mkdirs() }
        val num      = packs[idx].stickers.size + 1
        val filename = "sticker_$num.webp"

        File(webpSourcePath).copyTo(File(packDir, filename), overwrite = true)
        if (num == 1) generateTray(webpSourcePath, File(packDir, "tray.webp"))

        val updated = packs[idx].copy(
            stickers = packs[idx].stickers + Sticker(filename, listOf(emoji))
        )
        packs[idx] = updated
        savePacks(packs)
        bumpVersion(packId)
        return updated
    }

    fun deletePack(packId: String) {
        savePacks(loadPacks().filter { it.identifier != packId })
        File(context.cacheDir, "packs/$packId").deleteRecursively()
    }

    fun deleteSticker(packId: String, filename: String): StickerPack? {
        val packs = loadPacks().toMutableList()
        val idx   = packs.indexOfFirst { it.identifier == packId }
        if (idx < 0) return null
        File(context.cacheDir, "packs/$packId/$filename").delete()
        val updated = packs[idx].copy(
            stickers = packs[idx].stickers.filter { it.filename != filename }
        )
        packs[idx] = updated
        savePacks(packs)
        bumpVersion(packId)
        return updated
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun getPackDir(packId: String): File = File(context.cacheDir, "packs/$packId")

    fun getVersion(packId: String): Long =
        context.getSharedPreferences(PREFS_VER, Context.MODE_PRIVATE)
            .getLong("ver_$packId", 1L)

    private fun savePacks(packs: List<StickerPack>) {
        prefs.edit().putString(KEY_PACKS, JSONArray(packs.map { it.toJson() }).toString()).apply()
    }

    private fun bumpVersion(packId: String) {
        context.getSharedPreferences(PREFS_VER, Context.MODE_PRIVATE)
            .edit().putLong("ver_$packId", System.currentTimeMillis()).apply()
    }

    private fun generateTray(srcPath: String, out: File) = runCatching {
        val bmp  = BitmapFactory.decodeFile(srcPath)
        val tray = Bitmap.createScaledBitmap(bmp, 96, 96, true)
        out.outputStream().use { tray.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, it) }
        bmp.recycle(); tray.recycle()
    }

    // ── JSON serialization ────────────────────────────────────────────────────

    private fun JSONObject.toStickerPack(): StickerPack {
        val stickersArr = getJSONArray("stickers")
        return StickerPack(
            identifier        = getString("identifier"),
            name              = getString("name"),
            publisher         = optString("publisher", "StickerApp"),
            trayImageFileName = optString("trayImageFileName", "tray.webp"),
            isAnimated        = optBoolean("isAnimated", true),
            stickers          = (0 until stickersArr.length()).map { i ->
                val s      = stickersArr.getJSONObject(i)
                val emArr  = s.optJSONArray("emojis") ?: JSONArray().apply { put("😄") }
                Sticker(s.getString("filename"), (0 until emArr.length()).map { emArr.getString(it) })
            }
        )
    }

    private fun StickerPack.toJson(): JSONObject = JSONObject().apply {
        put("identifier",        identifier)
        put("name",              name)
        put("publisher",         publisher)
        put("trayImageFileName", trayImageFileName)
        put("isAnimated",        isAnimated)
        put("stickers", JSONArray(stickers.map { s ->
            JSONObject().apply {
                put("filename", s.filename)
                put("emojis",   JSONArray(s.emojis))
            }
        }))
    }
}