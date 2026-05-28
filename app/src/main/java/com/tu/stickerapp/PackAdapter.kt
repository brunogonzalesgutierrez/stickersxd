package com.tu.stickerapp

import android.app.AlertDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.io.File

class PackAdapter(
    private val context: Context,
    private val storage: PackStorage,
    private val packs: MutableList<StickerPack>,
    private val pendingStickerPath: String?,
    private val onChanged: () -> Unit,
    private val onSendToWA: (packId: String, packName: String) -> Unit,
    private val onOpenDetail: (packId: String) -> Unit        // ← coma en la línea anterior
) : RecyclerView.Adapter<PackAdapter.PackVH>() {

    inner class PackVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName:    TextView     = view.findViewById(R.id.tvPackName)
        val tvCount:   TextView     = view.findViewById(R.id.tvStickerCount)
        val stickerRow: LinearLayout = view.findViewById(R.id.stickerRow)
        val tvNoStickers: TextView  = view.findViewById(R.id.tvNoStickers)
        val btnDelete: ImageButton  = view.findViewById(R.id.btnDeletePack)
        val btnSend:   MaterialButton = view.findViewById(R.id.btnSendPack)
        val btnAdd:    MaterialButton = view.findViewById(R.id.btnAddSticker)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        PackVH(LayoutInflater.from(context).inflate(R.layout.item_pack_card, parent, false))

    override fun getItemCount() = packs.size

    override fun onBindViewHolder(holder: PackVH, position: Int) {
        val pack = packs[position]

        holder.tvName.text  = pack.name
        holder.tvCount.text = "${pack.stickers.size} sticker${if (pack.stickers.size != 1) "s" else ""}"

        // ── Thumbnails ──────────────────────────────────────────────
        holder.stickerRow.removeAllViews()
        if (pack.stickers.isEmpty()) {
            holder.tvNoStickers.visibility = View.VISIBLE
            holder.stickerRow.visibility   = View.GONE
        } else {
            holder.tvNoStickers.visibility = View.GONE
            holder.stickerRow.visibility   = View.VISIBLE

            pack.stickers.forEach { sticker ->
                val file = File(storage.getPackDir(pack.identifier), sticker.filename)
                val size = 80.dp

                val img = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = 8.dp }
                    scaleType    = ImageView.ScaleType.CENTER_CROP
                    setBackgroundResource(R.drawable.bg_sticker_thumb)
                    clipToOutline = true
                    if (file.exists()) {
                        BitmapFactory.decodeFile(file.absolutePath)?.let { setImageBitmap(it) }
                    }
                    // Mantener presionado → eliminar sticker
                    setOnLongClickListener {
                        AlertDialog.Builder(context)
                            .setTitle("Eliminar sticker")
                            .setMessage("¿Eliminar este sticker del paquete?")
                            .setPositiveButton("Eliminar") { _, _ ->
                                storage.deleteSticker(pack.identifier, sticker.filename)
                                onChanged()
                            }
                            .setNegativeButton("Cancelar", null)
                            .show()
                        true
                    }
                }
                holder.stickerRow.addView(img)
            }
        }

        // ── Eliminar pack ───────────────────────────────────────────
        holder.btnDelete.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("Eliminar paquete")
                .setMessage("¿Eliminar \"${pack.name}\" y todos sus stickers?")
                .setPositiveButton("Eliminar") { _, _ ->
                    storage.deletePack(pack.identifier)
                    onChanged()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        // ── Enviar a WhatsApp ───────────────────────────────────────
        holder.btnSend.isEnabled = pack.stickers.isNotEmpty()
        holder.btnSend.setOnClickListener { onSendToWA(pack.identifier, pack.name) }

        // ── Agregar sticker pendiente ───────────────────────────────
        if (pendingStickerPath != null && pack.stickers.size < 30) {
            holder.btnAdd.visibility = View.VISIBLE
            holder.btnAdd.setOnClickListener {
                val updated = storage.addStickerToPack(pack.identifier, pendingStickerPath)
                if (updated != null) {
                    Toast.makeText(context, "✓ Sticker agregado", Toast.LENGTH_SHORT).show()
                    onChanged()
                }
            }
        } else {
            holder.btnAdd.visibility = View.GONE
        }
                // Tap en el card → ver detalle
        holder.itemView.setOnClickListener { onOpenDetail(pack.identifier) }
    }

    private val Int.dp get() = (this * context.resources.displayMetrics.density).toInt()

}