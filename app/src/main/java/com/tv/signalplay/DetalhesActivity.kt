package com.tv.signalplay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetalhesActivity : FragmentActivity() {

    private var videoExt = "mp4"
    private var videoTitulo = ""
    private var positionToResume = 0L

    // Variáveis para a Série
    private val mapTemporadas = mutableMapOf<String, JsonArray>()
    private lateinit var rvTemporadas: RecyclerView
    private lateinit var rvEpisodios: RecyclerView

    private var xtUser = ""; private var xtPass = ""; private var urlServ = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalhes)

        xtUser = intent.getStringExtra("XTREAM_USER") ?: ""
        xtPass = intent.getStringExtra("XTREAM_PASS") ?: ""
        urlServ = intent.getStringExtra("URL") ?: ""
        val mediaId = intent.getIntExtra("MEDIA_ID", 0)
        val isSeries = intent.getBooleanExtra("IS_SERIES", false)

        val btnVoltar = findViewById<Button>(R.id.btnVoltarDetalhes)
        val btnAssistir = findViewById<Button>(R.id.btnAssistirDetalhes)
        
        rvTemporadas = findViewById(R.id.rvTemporadas)
        rvEpisodios = findViewById(R.id.rvEpisodios)
        rvTemporadas.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        rvEpisodios.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)

        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
        val histJson = prefs.getString("iptv_continuar_vod", "[]")
        val history: List<Map<String, Any>> = Gson().fromJson(histJson, object : TypeToken<List<Map<String, Any>>>(){}.type) ?: emptyList()
        val historicoSalvo = history.find { (it["id"] as? Number)?.toInt() == mediaId }
        
        if (historicoSalvo != null) {
            positionToResume = (historicoSalvo["tempo"] as? Number)?.toLong() ?: 0L
            if (positionToResume > 5000L) btnAssistir.text = "▶ CONTINUAR ASSISTINDO"
        }

        listOf(btnAssistir, btnVoltar).forEach { btn ->
            btn.isFocusableInTouchMode = false
            btn.setOnFocusChangeListener { v, focus -> if (focus) v.animate().scaleX(1.05f).start() else v.animate().scaleX(1.0f).start() }
        }

        btnVoltar.setOnClickListener { finish() }
        
        btnAssistir.setOnClickListener {
            val intentPlayer = Intent(this, PlayerActivity::class.java)
            intentPlayer.putExtra("URL", urlServ); intentPlayer.putExtra("XTREAM_USER", xtUser); intentPlayer.putExtra("XTREAM_PASS", xtPass)
            intentPlayer.putExtra("STREAM_ID", mediaId); intentPlayer.putExtra("TYPE", if (isSeries) "series" else "vod"); intentPlayer.putExtra("TITLE", videoTitulo); intentPlayer.putExtra("EXTENSION", videoExt)
            intentPlayer.putExtra("RESUME_POSITION", positionToResume) 
            startActivity(intentPlayer)
        }

        if (mediaId != 0 && urlServ.isNotEmpty()) carregarSinopse(urlServ, xtUser, xtPass, mediaId, isSeries)
    }

    private fun carregarSinopse(url: String, user: String, pass: String, id: Int, isSeries: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val api = XtreamClient.create(url)
                val response = if (isSeries) api.getSeriesInfo(user, pass, id = id) else api.getVodInfo(user, pass, id = id)
                
                withContext(Dispatchers.Main) {
                    if (response.isJsonObject) {
                        val obj = response.asJsonObject
                        if (obj.has("info")) {
                            val info = obj.getAsJsonObject("info")
                            videoTitulo = info.get("name")?.asString ?: "Desconhecido"
                            val sinopse = info.get("plot")?.asString ?: "Nenhuma sinopse disponível."
                            val nota = info.get("rating")?.asString ?: "0.0"
                            val ano = info.get("releasedate")?.asString ?: info.get("year")?.asString ?: ""
                            
                            val capaGigante = info.get("movie_image")?.asString ?: info.get("cover_big")?.asString ?: ""
                            val capaFundo = info.get("backdrop_path")?.asJsonArray?.firstOrNull()?.asString ?: capaGigante
                            
                            if (!isSeries && obj.has("movie_data")) videoExt = obj.getAsJsonObject("movie_data").get("container_extension")?.asString ?: "mp4"

                            findViewById<TextView>(R.id.txtTituloDetalhes).text = videoTitulo
                            findViewById<TextView>(R.id.txtSinopseDetalhes).text = sinopse
                            findViewById<TextView>(R.id.txtMetaDetalhes).text = "📅 $ano   ⭐ $nota   ${if(isSeries) "SÉRIE" else "FILME"}"
                            
                            if (capaGigante.isNotEmpty()) {
                                Glide.with(this@DetalhesActivity).load(capaGigante).override(350, 500).diskCacheStrategy(DiskCacheStrategy.ALL).into(findViewById<ImageView>(R.id.imgPosterDetalhes))
                            }
                            if (capaFundo.isNotEmpty()) {
                                Glide.with(this@DetalhesActivity).load(capaFundo).override(1280, 720).diskCacheStrategy(DiskCacheStrategy.ALL).into(findViewById<ImageView>(R.id.imgFundoDetalhes))
                            }
                        }

                        // LÓGICA DAS SÉRIES: Extrai Episódios e Esconde o botão "Assistir"
                        if (isSeries && obj.has("episodes")) {
                            findViewById<Button>(R.id.btnAssistirDetalhes).visibility = View.GONE
                            findViewById<LinearLayout>(R.id.layoutSeriesExtras).visibility = View.VISIBLE
                            
                            val epsObj = obj.getAsJsonObject("episodes")
                            mapTemporadas.clear()
                            
                            for (key in epsObj.keySet()) {
                                mapTemporadas[key] = epsObj.getAsJsonArray(key)
                            }
                            
                            val chavesOrdenadas = mapTemporadas.keys.toList()
                            rvTemporadas.adapter = TemporadaAdapter(chavesOrdenadas)
                            
                            // Força a primeira temporada a abrir por padrão
                            if (chavesOrdenadas.isNotEmpty()) {
                                carregarEpisodios(chavesOrdenadas[0])
                            }
                        }
                    }
                }
            } catch (e: Exception) { }
        }
    }

    private fun carregarEpisodios(temporadaKey: String) {
        val listaEps = mapTemporadas[temporadaKey]
        if (listaEps != null) {
            rvEpisodios.adapter = EpisodioAdapter(listaEps)
        }
    }

    // --- ADAPTER DE TEMPORADAS ---
    inner class TemporadaAdapter(private val chaves: List<String>) : RecyclerView.Adapter<TemporadaAdapter.Holder>() {
        inner class Holder(val view: View) : RecyclerView.ViewHolder(view) {
            val txtNome: TextView = view.findViewById(R.id.txtNomeTemporada)
            init {
                view.isFocusableInTouchMode = false
                view.setOnFocusChangeListener { v, focus -> 
                    if (focus) { 
                        v.animate().scaleX(1.08f).scaleY(1.08f).start()
                        (v.findViewById<TextView>(R.id.txtNomeTemporada)).setTextColor(Color.parseColor("#ffcc00"))
                        // Ao passar por cima, já atualiza os episódios!
                        carregarEpisodios(chaves[bindingAdapterPosition])
                    } else { 
                        v.animate().scaleX(1.0f).scaleY(1.0f).start()
                        (v.findViewById<TextView>(R.id.txtNomeTemporada)).setTextColor(Color.WHITE)
                    } 
                }
                view.setOnClickListener { carregarEpisodios(chaves[bindingAdapterPosition]) }
            }
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.card_temporada, parent, false))
        override fun onBindViewHolder(holder: Holder, position: Int) { holder.txtNome.text = "Temporada ${chaves[position]}" }
        override fun getItemCount() = chaves.size
    }

    // --- ADAPTER DE EPISÓDIOS ---
    inner class EpisodioAdapter(private val episodiosArray: JsonArray) : RecyclerView.Adapter<EpisodioAdapter.Holder>() {
        inner class Holder(val view: View) : RecyclerView.ViewHolder(view) {
            val imgCapa: ImageView = view.findViewById(R.id.imgCapaEpisodio)
            val txtNome: TextView = view.findViewById(R.id.txtNomeEpisodio)
            init {
                view.isFocusableInTouchMode = false
                view.setOnFocusChangeListener { v, focus -> 
                    if (focus) { v.animate().scaleX(1.08f).scaleY(1.08f).start(); v.elevation = 15f } 
                    else { v.animate().scaleX(1.0f).scaleY(1.0f).start(); v.elevation = 0f } 
                }
                view.setOnClickListener {
                    try {
                        val epObj = episodiosArray[bindingAdapterPosition].asJsonObject
                        val epId = if (epObj.has("id")) epObj.get("id").asInt else epObj.get("stream_id").asInt
                        val epExt = if (epObj.has("container_extension")) epObj.get("container_extension").asString else "mp4"
                        val epNome = if (epObj.has("title")) epObj.get("title").asString else "Episódio"

                        val intentPlayer = Intent(itemView.context, PlayerActivity::class.java)
                        intentPlayer.putExtra("URL", urlServ); intentPlayer.putExtra("XTREAM_USER", xtUser); intentPlayer.putExtra("XTREAM_PASS", xtPass)
                        intentPlayer.putExtra("STREAM_ID", epId); intentPlayer.putExtra("TYPE", "series"); intentPlayer.putExtra("TITLE", epNome); intentPlayer.putExtra("EXTENSION", epExt)
                        itemView.context.startActivity(intentPlayer)
                    } catch (e: Exception) {}
                }
            }
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.card_episodio, parent, false))
        override fun onBindViewHolder(holder: Holder, position: Int) {
            try {
                val epObj = episodiosArray[position].asJsonObject
                holder.txtNome.text = "${position + 1}. " + (if (epObj.has("title")) epObj.get("title").asString else "Episódio")
                
                if (epObj.has("info") && epObj.getAsJsonObject("info").has("movie_image")) {
                    val urlImg = epObj.getAsJsonObject("info").get("movie_image").asString
                    Glide.with(holder.itemView.context).load(urlImg).override(300, 170).diskCacheStrategy(DiskCacheStrategy.ALL).into(holder.imgCapa)
                } else {
                    holder.imgCapa.setImageDrawable(null)
                }
            } catch (e: Exception) {}
        }
        override fun getItemCount() = episodiosArray.size()
    }
}
