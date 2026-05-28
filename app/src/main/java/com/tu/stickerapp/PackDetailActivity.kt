package com.tu.stickerapp

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.graphics.BitmapFactory
import com.google.android.material.button.MaterialButton
import java.io.File

class PackDetailActivity : AppCompatActivity() {

    private lateinit var storage: PackStorage
    private lateinit var recycler: RecyclerView
    private lateinit var tvTitle: TextView
    private lateinit var tvCount: TextView
    private lateinit var tvEmpty: TextView
    private var packId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pack_detail)

        storage  = PackStorage(this)
        packId   = intent.getStringExtra("pack_id") ?: run { finish(); return }

        tvTitle  = findViewById(R.id.tvPackDetailName)
        tvCount  = findViewById(R.id.tvPackDetailCount)
        tvEmpty  = findViewById(R.id.tvPackDetailEmpty)
        recycler = findViewById(R.id.recyclerStickers)

        recycler.layoutManager = GridLayoutManager(this, 3)

        findViewById<View>(R.id.btnPackDetailBack).setOnClickListener { finish() }

        loadStickers()
    }

    override fun onResume() {
        super.onResume()
        loadStickers()
    }

    private fun loadStickers() {
        val pack = storage.loadPacks().find { it.identifier == packId }
            ?: run { finish(); return }

        tvTitle.text = pack.name
        tvCount.text = "${pack.stickers.size} / 30 stickers"

        tvEmpty.visibility  = if (pack.stickers.isEmpty()) View.VISIBLE else View.GONE
        recycler.visibility = if (pack.stickers.isEmpty()) View.GONE   else View.VISIBLE

        recycler.adapter = StickerGridAdapter(
            context   = this,
            packDir   = storage.getPackDir(packId),
            stickers  = pack.stickers,
            onDelete  = { filename ->
                AlertDialog.Builder(this)
                    .setTitle("Eliminar sticker")
                    .setMessage("¿Eliminar este sticker del paquete?")
                    .setPositiveButton("Eliminar") { _, _ ->
                        storage.deleteSticker(packId, filename)
                        loadStickers()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        )
    }
}

// ── Adapter interno del grid ──────────────────────────────────────────────────

class StickerGridAdapter(
    private val context: android.content.Context,
    private val packDir: File,
    private val stickers: List<Sticker>,
    private val onDelete: (String) -> Unit
) : RecyclerView.Adapter<StickerGridAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.imgSticker)
        val btnDel: MaterialButton = view.findViewById(R.id.btnDeleteSticker)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(context).inflate(R.layout.item_sticker_grid, parent, false))

    override fun getItemCount() = stickers.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val sticker = stickers[position]
        val file    = File(packDir, sticker.filename)

        if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)?.let { holder.img.setImageBitmap(it) }
        }

        holder.btnDel.setOnClickListener { onDelete(sticker.filename) }
    }
}