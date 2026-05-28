package com.tu.stickerapp

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MyPacksActivity : AppCompatActivity() {

    private lateinit var storage: PackStorage
    private lateinit var recycler: RecyclerView
    private lateinit var tvEmpty: TextView
    var pendingStickerPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_packs)

        storage = PackStorage(this)
        pendingStickerPath = intent.getStringExtra("webp_path")

        recycler = findViewById(R.id.recyclerPacks)
        tvEmpty  = findViewById(R.id.tvEmpty)
        recycler.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        // FAB: crear nuevo pack
        findViewById<FloatingActionButton>(R.id.fabNewPack).setOnClickListener {
            showCreatePackDialog()
        }

        loadPacks()
    }

    override fun onResume() {
        super.onResume()
        loadPacks()
    }

    fun loadPacks() {
        val packs = storage.loadPacks()
        tvEmpty.visibility  = if (packs.isEmpty()) View.VISIBLE else View.GONE
        recycler.visibility = if (packs.isEmpty()) View.GONE   else View.VISIBLE

        recycler.adapter = PackAdapter(
            context            = this,
            storage            = storage,
            packs              = packs.toMutableList(),
            pendingStickerPath = pendingStickerPath,
            onChanged          = { loadPacks() },
            onSendToWA         = { id, name -> sendPackToWA(id, name) },
            onOpenDetail       = { packId ->
                startActivity(
                    Intent(this, PackDetailActivity::class.java)
                        .putExtra("pack_id", packId)
                )
            }
        )
    }

    private fun showCreatePackDialog() {
        val input = EditText(this).apply {
            hint = "Nombre del paquete"
            setPadding(48, 32, 48, 16)
        }
        AlertDialog.Builder(this)
            .setTitle("Nuevo paquete")
            .setView(input)
            .setPositiveButton("Crear") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    storage.createPack(name)
                    loadPacks()
                    Toast.makeText(this, "Pack \"$name\" creado", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun sendPackToWA(packId: String, packName: String) {
        val waInstalled = listOf("com.whatsapp", "com.whatsapp.w4b").any {
            try { packageManager.getPackageInfo(it, 0); true } catch (e: Exception) { false }
        }
        if (!waInstalled) {
            Toast.makeText(this, "WhatsApp no está instalado", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK").apply {
            putExtra("sticker_pack_id",             packId)
            putExtra("sticker_pack_name",           packName)
            putExtra("sticker_pack_authority",      StickerProvider.AUTHORITY)
            putExtra("sticker_pack_localized_name", packName)
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, 200)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir WhatsApp", Toast.LENGTH_LONG).show()
        }
    }
}