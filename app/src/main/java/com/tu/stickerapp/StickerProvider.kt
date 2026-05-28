package com.tu.stickerapp

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.content.res.AssetFileDescriptor
import android.os.ParcelFileDescriptor
import java.io.File

class StickerProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.tu.stickerapp.stickercontentprovider"
        private const val METADATA_CODE            = 1
        private const val METADATA_CODE_FOR_SINGLE = 2
        private const val STICKERS_CODE            = 3
        private const val STICKER_FILE_CODE        = 4
    }

    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).also {
        it.addURI(AUTHORITY, "metadata",           METADATA_CODE)
        it.addURI(AUTHORITY, "metadata/*",         METADATA_CODE_FOR_SINGLE)
        it.addURI(AUTHORITY, "stickers/*",         STICKERS_CODE)
        it.addURI(AUTHORITY, "stickers_asset/*/*", STICKER_FILE_CODE)
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri, projection: Array<String>?,
        selection: String?, selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor {
        android.util.Log.d("StickerProvider", "query: $uri")
        return when (uriMatcher.match(uri)) {
            METADATA_CODE            -> buildMetadataCursor(null)
            METADATA_CODE_FOR_SINGLE -> buildMetadataCursor(uri.lastPathSegment)
            STICKERS_CODE -> {
                val packId = uri.lastPathSegment ?: return MatrixCursor(arrayOf())
                buildStickersCursor(packId)
            }
            else -> MatrixCursor(arrayOf())
        }
    }

    private fun buildMetadataCursor(filterPackId: String?): MatrixCursor {
        val cols = arrayOf(
            "sticker_pack_identifier", "sticker_pack_name", "sticker_pack_publisher",
            "sticker_pack_icon", "android_play_store_link", "ios_app_download_link",
            "sticker_pack_publisher_email", "sticker_pack_publisher_website",
            "sticker_pack_privacy_policy_website", "sticker_pack_license_agreement_website",
            "image_data_version", "whatsapp_will_not_cache_stickers", "animated_sticker_pack"
        )
        val cursor  = MatrixCursor(cols)
        val storage = PackStorage(context!!)
        val packs   = storage.loadPacks()
            .let { all -> if (filterPackId != null) all.filter { it.identifier == filterPackId } else all }

        for (pack in packs) {
            cursor.addRow(arrayOf(
                pack.identifier, pack.name, pack.publisher, pack.trayImageFileName,
                "", "", "", "", "", "",
                storage.getVersion(pack.identifier).toString(),
                "0",
                if (pack.isAnimated) "1" else "0"
            ))
        }
        android.util.Log.d("StickerProvider", "metadata devuelta: ${cursor.count} pack(s) para filtro=$filterPackId")
        return cursor
    }

    private fun buildStickersCursor(packId: String): MatrixCursor {
        val cursor = MatrixCursor(arrayOf("sticker_file_name", "sticker_emoji"))
        val pack = PackStorage(context!!).loadPacks().find { it.identifier == packId }
            ?: return cursor

        for (sticker in pack.stickers) {
            cursor.addRow(arrayOf(sticker.filename, sticker.emojis.firstOrNull() ?: "😄"))
        }
        android.util.Log.d("StickerProvider", "stickers para $packId: ${cursor.count}")
        return cursor
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        android.util.Log.d("StickerProvider", "openAssetFile: $uri")
        if (uriMatcher.match(uri) != STICKER_FILE_CODE) return null

        // URI: stickers_asset/{packId}/{filename}
        val segments = uri.pathSegments
        if (segments.size < 3) return null
        val packId   = segments[segments.size - 2]
        val filename = segments[segments.size - 1]

        val packDir = PackStorage(context!!).getPackDir(packId)
        val file = File(packDir, filename).takeIf { it.exists() }
            ?: run {
                android.util.Log.e("StickerProvider", "No encontrado: $packDir/$filename")
                return null
            }

        android.util.Log.d("StickerProvider", "sirviendo: ${file.absolutePath}")
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun getType(uri: Uri): String = "image/webp"
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0
}