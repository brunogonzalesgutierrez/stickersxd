package com.tu.stickerapp

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.io.File
import android.widget.LinearLayout

class MainActivity : AppCompatActivity() {

    private lateinit var imgPreview:    ImageView
    private lateinit var tvPlaceholder: TextView
    private lateinit var tvStatus:      TextView
    private lateinit var progressBar:   ProgressBar
    private lateinit var btnPick:       Button
    private lateinit var btnConvert:    Button
    private lateinit var btnSend:       Button
    private lateinit var seekDuration:  SeekBar
    private lateinit var tvDuration:    TextView
    private lateinit var layoutPlaceholder: LinearLayout


    private lateinit var etVideoUrl: EditText
    private lateinit var btnDownloadUrl: Button

    private var selectedUri:    Uri?    = null
    private var outputWebPPath: String? = null
    private var keepAspectRatio: Boolean = false
    private var imageDataVersion: Long = 1L
    private val converter = StickerConverter()

    private val durationSteps = floatArrayOf(1f, 1.5f, 2f, 2.5f, 3f)

    private val pickMedia = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@registerForActivityResult
        selectedUri = uri
        outputWebPPath = null
        btnSend.isEnabled = false
        btnConvert.isEnabled = true
        setStatus("Video seleccionado ✓ — Tocá CONVERTIR")
        layoutPlaceholder.visibility = View.VISIBLE
        imgPreview.setImageDrawable(null)
    }

    private val tenorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val path = result.data?.getStringExtra("webp_path") ?: return@registerForActivityResult
            outputWebPPath   = path
            imageDataVersion = System.currentTimeMillis()
            val sizeKb = java.io.File(path).length() / 1024
            setStatus("✅ Sticker listo ($sizeKb KB)")
            layoutPlaceholder.visibility = View.GONE
            imgPreview.setImageURI(Uri.fromFile(java.io.File(path)))
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                (imgPreview.drawable as? android.graphics.drawable.AnimatedImageDrawable)?.start()
            }
            btnSend.isEnabled = true
        }
    }




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imgPreview    = findViewById(R.id.imgPreview)
        tvPlaceholder = findViewById(R.id.tvPlaceholder)
        layoutPlaceholder = findViewById(R.id.layoutPlaceholder)
        tvStatus      = findViewById(R.id.tvStatus)
        progressBar   = findViewById(R.id.progressBar)
        btnPick       = findViewById(R.id.btnPick)
        btnConvert    = findViewById(R.id.btnConvert)
        btnSend       = findViewById(R.id.btnSend)
        seekDuration  = findViewById(R.id.seekDuration)
        tvDuration    = findViewById(R.id.tvDuration)


        etVideoUrl      = findViewById(R.id.etVideoUrl)
        btnDownloadUrl  = findViewById(R.id.btnDownloadUrl)
        // DESPUÉS:
        btnDownloadUrl.setOnClickListener {
            val url = etVideoUrl.text.toString().trim()
            if (url.startsWith("http")) downloadAndConvert(url)
            else setStatus("❌ Pegá un link válido")
        }

        seekDuration.max = durationSteps.size - 1
        seekDuration.progress = durationSteps.size - 1
        tvDuration.text = "${durationSteps.last().toInt()}s"

        seekDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                val s = durationSteps[p]
                tvDuration.text = if (s == s.toInt().toFloat()) "${s.toInt()}s" else "${s}s"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        btnPick.setOnClickListener    { pickMedia.launch("video/*") }
        btnConvert.setOnClickListener { startConvert() }
        btnSend.setOnClickListener    { sendToWhatsApp() }


        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnMyPacks)
            .setOnClickListener {
                val intent = Intent(this, MyPacksActivity::class.java).apply {
                    outputWebPPath?.let { path -> putExtra("webp_path", path) }
                }
                startActivity(intent)
            }


        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSearchTenor)
            .setOnClickListener {
                tenorLauncher.launch(
                    Intent(this, TenorSearchActivity::class.java).apply {
                        putExtra("duration_ms", (durationSteps[seekDuration.progress] * 1000).toLong())
                        putExtra("keep_aspect", keepAspectRatio)
                    }
                )
            }

        val btnToggleShape = findViewById<Button>(R.id.btnToggleShape)
        btnToggleShape.setOnClickListener {
            keepAspectRatio = !keepAspectRatio
            btnToggleShape.text = if (keepAspectRatio) "Forma: Original" else "Forma: Cuadrado"
        }
    }

    private fun startConvert() {
        val uri = selectedUri ?: return
        val durationMs = (durationSteps[seekDuration.progress] * 1000).toLong()

        lifecycleScope.launch {
            setLoading(true)
            setStatus("Extrayendo frames...")

            val result = withContext(Dispatchers.IO) {
                converter.convertToAnimatedWebP(
                    context         = applicationContext,
                    inputUri        = uri,
                    maxDurationMs   = durationMs,
                    fps             = 10,
                    sizePx = 512,
                    keepAspectRatio = keepAspectRatio
                )
            }

            setLoading(false)

            if (result != null) {
                outputWebPPath = result
                imageDataVersion = System.currentTimeMillis()
                val sizeKb = File(result).length() / 1024
                setStatus("Sticker listo ✓  ($sizeKb KB)")
                layoutPlaceholder.visibility = View.GONE
                imgPreview.setImageURI(Uri.fromFile(File(result)))
                if (Build.VERSION.SDK_INT >= 28) {
                    (imgPreview.drawable as? AnimatedImageDrawable)?.start()
                }
                btnSend.isEnabled = true
            } else {
                setStatus("❌ Error al convertir. Probá con otro video.")
            }
        }
    }

    private fun sendToWhatsApp() {
        val path = outputWebPPath ?: return

        val storage = PackStorage(applicationContext)

        // Reusar el pack "Mi Pack" si existe, o crearlo
        val pack = storage.loadPacks().firstOrNull { it.name == "Mi Pack" }
            ?: storage.createPack("Mi Pack")

        val updated = storage.addStickerToPack(pack.identifier, path)
        if (updated == null) {
            Toast.makeText(this, "El pack está lleno (máx. 30 stickers)", Toast.LENGTH_LONG).show()
            return
        }

        val waInstalled = listOf("com.whatsapp", "com.whatsapp.w4b").any {
            try { packageManager.getPackageInfo(it, 0); true } catch (e: Exception) { false }
        }
        if (!waInstalled) {
            Toast.makeText(this, "WhatsApp no está instalado", Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK").apply {
            putExtra("sticker_pack_id",             pack.identifier)
            putExtra("sticker_pack_name",           pack.name)
            putExtra("sticker_pack_authority",      StickerProvider.AUTHORITY)
            putExtra("sticker_pack_localized_name", pack.name)
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_CODE_ADD_PACK)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir WhatsApp", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_ADD_PACK) {
            if (resultCode == Activity.RESULT_CANCELED) {
                val error = data?.getStringExtra("validation_error")
                setStatus(if (error != null) "WA: $error" else "Cancelado")
            } else {
                setStatus("✅ ¡Pack agregado a WhatsApp!")
                Toast.makeText(this, "¡Sticker agregado!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnPick.isEnabled    = !loading
        btnConvert.isEnabled = !loading
        btnSend.isEnabled    = false
    }

    private fun setStatus(msg: String) { tvStatus.text = msg }


    private fun downloadAndConvert(url: String) {
        val durationMs = (durationSteps[seekDuration.progress] * 1000).toLong()
        val isGif = url.contains(".gif", ignoreCase = true)

        lifecycleScope.launch {
            setLoading(true)
            setStatus("⬇️ Descargando...")

            val filePath = withContext(Dispatchers.IO) {
                try {
                    var currentUrl = url
                    var connection: java.net.HttpURLConnection
                    var redirects = 0
                    while (true) {
                        connection = java.net.URL(currentUrl).openConnection()
                                as java.net.HttpURLConnection
                        connection.instanceFollowRedirects = false
                        connection.connectTimeout = 15_000
                        connection.readTimeout    = 30_000
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                        connection.connect()
                        val code = connection.responseCode
                        if (code in 300..399 && redirects < 6) {
                            currentUrl = connection.getHeaderField("Location") ?: break
                            connection.disconnect()
                            redirects++
                        } else break
                    }
                    if (connection.responseCode != 200) return@withContext null
                    val ext = if (isGif) "gif" else "mp4"
                    val tmp = java.io.File(applicationContext.cacheDir, "url_video.$ext")
                    connection.inputStream.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
                    connection.disconnect()
                    tmp.absolutePath
                } catch (e: Exception) { null }
            }

            if (filePath == null) {
                setLoading(false)
                setStatus("❌ No se pudo descargar.")
                return@launch
            }

            setStatus("⚡ Convirtiendo...")

            val result = withContext(Dispatchers.IO) {
                if (isGif) {
                    converter.convertGifToAnimatedWebP(
                        context         = applicationContext,
                        gifFile         = java.io.File(filePath),
                        maxDurationMs   = durationMs,
                        fps             = 10,
                        sizePx          = 512,
                        keepAspectRatio = keepAspectRatio
                    )
                } else {
                    converter.convertToAnimatedWebP(
                        context         = applicationContext,
                        inputUri        = Uri.fromFile(java.io.File(filePath)),
                        maxDurationMs   = durationMs,
                        fps             = 10,
                        sizePx          = 512,
                        keepAspectRatio = keepAspectRatio
                    )
                }
            }

            setLoading(false)

            if (result != null) {
                outputWebPPath = result
                imageDataVersion = System.currentTimeMillis()
                val sizeKb = java.io.File(result).length() / 1024
                setStatus("✅ Sticker listo ($sizeKb KB)")
                layoutPlaceholder.visibility = View.GONE
                imgPreview.setImageURI(Uri.fromFile(java.io.File(result)))
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    (imgPreview.drawable as? android.graphics.drawable.AnimatedImageDrawable)?.start()
                }
                btnSend.isEnabled = true
                etVideoUrl.text.clear()
            } else {
                setStatus("❌ Error al convertir.")
            }
        }
    }



    companion object {
        private const val REQUEST_CODE_ADD_PACK = 200
    }
}
