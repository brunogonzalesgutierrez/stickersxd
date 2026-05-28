package com.tu.stickerapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.URL

class TenorSearchActivity : AppCompatActivity() {

    companion object {
        private const val GIPHY_KEY = "iSwfC9y1c9tHNKz3XyRz3YVLHbOvsNa3"
    }

    private lateinit var etSearch:    EditText
    private lateinit var btnSearch:   com.google.android.material.button.MaterialButton
    private lateinit var recycler:    RecyclerView
    private lateinit var progressBar: com.google.android.material.progressindicator.LinearProgressIndicator
    private lateinit var tvEmpty:     TextView

    private var durationMs = 3000L
    private var keepAspect = false
    private val converter  = StickerConverter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tenor_search)

        durationMs = intent.getLongExtra("duration_ms", 3000L)
        keepAspect = intent.getBooleanExtra("keep_aspect", false)

        etSearch    = findViewById(R.id.etTenorSearch)
        btnSearch   = findViewById(R.id.btnTenorSearch)
        recycler    = findViewById(R.id.recyclerTenor)
        progressBar = findViewById(R.id.tenorProgress)
        tvEmpty     = findViewById(R.id.tvTenorEmpty)

        recycler.layoutManager = GridLayoutManager(this, 2)

        findViewById<View>(R.id.btnTenorBack).setOnClickListener { finish() }

        btnSearch.setOnClickListener {
            val q = etSearch.text.toString().trim()
            if (q.isNotEmpty()) searchGiphy(q)
        }

        // Búsqueda por defecto
        searchGiphy("meme")
    }

    private fun searchGiphy(query: String) {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            tvEmpty.visibility     = View.GONE
            recycler.visibility    = View.GONE

            val results = withContext(Dispatchers.IO) {
                try {
                    val url = "https://api.giphy.com/v1/gifs/search" +
                        "?q=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                        "&api_key=$GIPHY_KEY&limit=24&rating=g"
                    val json = JSONObject(URL(url).readText())
                    val arr  = json.getJSONArray("data")
                    (0 until arr.length()).mapNotNull { i ->
                        runCatching {
                            val images  = arr.getJSONObject(i).getJSONObject("images")
                            val preview = images.getJSONObject("fixed_height_small").getString("url")
                            val full    = images.getJSONObject("original").getString("url")
                            Pair(preview, full)
                        }.getOrNull()
                    }
                } catch (e: Exception) { emptyList() }
            }

            progressBar.visibility = View.GONE

            if (results.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
            } else {
                recycler.visibility = View.VISIBLE
                recycler.adapter = TenorAdapter(results) { gifUrl ->
                    downloadAndConvertGif(gifUrl)
                }
            }
        }
    }

    private fun downloadAndConvertGif(gifUrl: String) {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            setResult(Activity.RESULT_CANCELED)

            val webpPath = withContext(Dispatchers.IO) {
                try {
                    val tmp = File(cacheDir, "giphy_download.gif")
                    URL(gifUrl).openStream().use { it.copyTo(tmp.outputStream()) }
                    converter.convertGifToAnimatedWebP(
                        context         = applicationContext,
                        gifFile         = tmp,
                        maxDurationMs   = durationMs,
                        keepAspectRatio = keepAspect
                    )
                } catch (e: Exception) { null }
            }

            progressBar.visibility = View.GONE

            if (webpPath != null) {
                setResult(Activity.RESULT_OK, Intent().putExtra("webp_path", webpPath))
                finish()
            } else {
                Toast.makeText(this@TenorSearchActivity, "❌ Error al convertir", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// ── Adapter ───────────────────────────────────────────────────────────────────

class TenorAdapter(
    private val items: List<Pair<String, String>>,   // preview, fullUrl
    private val onPick: (String) -> Unit
) : RecyclerView.Adapter<TenorAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.imgTenorItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_tenor_gif, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (preview, full) = items[position]
        Glide.with(holder.img).asGif().load(preview).into(holder.img)
        holder.img.setOnClickListener { onPick(full) }
    }
}