package com.signalplay.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

class HomeActivity : Activity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var btnAssistirDestaque: Button

    private var urlGlobal = ""
    private var userGlobal = ""
    private var passGlobal = ""
    private var username = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        db = FirebaseFirestore.getInstance()
        btnAssistirDestaque = findViewById(R.id.btnAssistirDestaque)
        btnAssistirDestaque.setBackgroundResource(R.drawable.bg_btn_white)

        btnAssistirDestaque.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) v.animate().scaleX(1.05f).scaleY(1.05f).translationZ(10f).setDuration(150).start()
            else v.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(150).start()
        }

        val menuPesquisar = findViewById<TextView>(R.id.menuPesquisar)
        val menuInicio = findViewById<TextView>(R.id.menuInicio)
        val menuCanais = findViewById<TextView>(R.id.menuCanais)
        val menuFilmes = findViewById<TextView>(R.id.menuFilmes)
        val menuSeries = findViewById<TextView>(R.id.menuSeries)
        val menuConfig = findViewById<TextView>(R.id.menuConfig)

        val menuFocusListener = View.OnFocusChangeListener { v, hasFocus ->
            val txt = v as TextView
            if (hasFocus) {
                txt.setTextColor(Color.BLACK)
                v.animate().scaleX(1.05f).scaleY(1.05f).translationZ(8f).setDuration(150).start()
            } else {
                txt.setTextColor(Color.WHITE)
                v.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(150).start()
            }
        }
        
        menuPesquisar.onFocusChangeListener = menuFocusListener
        menuInicio.onFocusChangeListener = menuFocusListener
        menuCanais.onFocusChangeListener = menuFocusListener
        menuFilmes.onFocusChangeListener = menuFocusListener
        menuSeries.onFocusChangeListener = menuFocusListener
        menuConfig.onFocusChangeListener = menuFocusListener

        val tvContinuarTitulo = findViewById<TextView>(R.id.tvContinuarTitulo)
        val recyclerContinuar = findViewById<RecyclerView>(R.id.recyclerContinuar)
        val recyclerFavoritos = findViewById<RecyclerView>(R.id.recyclerFavoritos)
        val recyclerUltimos = findViewById<RecyclerView>(R.id.recyclerUltimos)
        val recyclerTopFilmes = findViewById<RecyclerView>(R.id.recyclerTopFilmes)
        val recyclerTopSeries = findViewById<RecyclerView>(R.id.recyclerTopSeries)
        val recyclerSeriesAlta = findViewById<RecyclerView>(R.id.recyclerSeriesAlta)
        
        // NOVO: Recycler do YouTube
        val areaYoutube = findViewById<LinearLayout>(R.id.areaYoutube)
        val recyclerYoutube = findViewById<RecyclerView>(R.id.recyclerYoutube)

        recyclerContinuar.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerFavoritos.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerUltimos.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerTopFilmes.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerTopSeries.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerSeriesAlta.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerYoutube.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        val urlOriginal = intent.getStringExtra("URL") ?: ""
        urlGlobal = if (urlOriginal.endsWith("/")) urlOriginal.dropLast(1) else urlOriginal
        userGlobal = intent.getStringExtra("USER") ?: ""
        passGlobal = intent.getStringExtra("PASS") ?: ""
        username = intent.getStringExtra("USERNAME") ?: ""

        menuPesquisar.setOnClickListener {
            val intent = Intent(this@HomeActivity, SearchActivity::class.java)
            intent.putExtra("URL", urlGlobal)
            intent.putExtra("USER", userGlobal)
            intent.putExtra("PASS", passGlobal)
            intent.putExtra("USERNAME", username)
            startActivity(intent)
        }
        menuCanais.setOnClickListener {
            val intent = Intent(this@HomeActivity, TvActivity::class.java)
            intent.putExtra("URL", urlGlobal)
            intent.putExtra("USER", userGlobal)
            intent.putExtra("PASS", passGlobal)
            intent.putExtra("USERNAME", username)
            startActivity(intent)
        }
        menuFilmes.setOnClickListener {
            val intent = Intent(this@HomeActivity, VodActivity::class.java)
            intent.putExtra("URL", urlGlobal)
            intent.putExtra("USER", userGlobal)
            intent.putExtra("PASS", passGlobal)
            intent.putExtra("USERNAME", username)
            startActivity(intent)
        }
        menuSeries.setOnClickListener {
            val intent = Intent(this@HomeActivity, SeriesActivity::class.java)
            intent.putExtra("URL", urlGlobal)
            intent.putExtra("USER", userGlobal)
            intent.putExtra("PASS", passGlobal)
            intent.putExtra("USERNAME", username)
            startActivity(intent)
        }
        menuConfig.setOnClickListener {
            val intent = Intent(this@HomeActivity, SettingsActivity::class.java)
            intent.putExtra("URL", urlGlobal)
            intent.putExtra("USER", userGlobal)
            intent.putExtra("PASS", passGlobal)
            intent.putExtra("USERNAME", username)
            startActivity(intent)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
                val isParentalActive = prefs.getBoolean("PARENTAL_CONTROL", false)
                val filterSD = prefs.getBoolean("FILTER_SD", false)
                val filterH265 = prefs.getBoolean("FILTER_H265", false)
                val filter4K = prefs.getBoolean("FILTER_4K", false)
                
                val palavrasProibidas = listOf("adult", "+18", "18+", "xxx", "porn", "hachutv", "sensual", "sex")

                val client = OkHttpClient()
                val listaIdsFavoritos = mutableListOf<String>()
                var historicoMap: Map<String, Any>? = null
                
                db.collection("usuarios").whereEqualTo("usuario", username).get()
                    .addOnSuccessListener { snapshot ->
                        if (!snapshot.isEmpty) {
                            val doc = snapshot.documents[0]
                            val favs = doc.get("favoritos") as? List<*>
                            favs?.forEach { listaIdsFavoritos.add(it.toString()) }
                            historicoMap = doc.get("historico_vod") as? Map<String, Any>
                        }
                    }
                
                withContext(Dispatchers.IO) { Thread.sleep(800) }

                fun getProgress(id: String): Int {
                    if (historicoMap != null) {
                        val progressoData = historicoMap!![id] as? Map<String, Any>
                        if (progressoData != null) {
                            val pos = progressoData["posicao"]?.toString()?.toLongOrNull() ?: 0L
                            val dur = progressoData["duracao"]?.toString()?.toLongOrNull() ?: 0L
                            if (dur > 0L) return ((pos.toDouble() / dur.toDouble()) * 100).toInt()
                        }
                    }
                    return 0
                }

                val liveCats = mutableListOf<CategoriaItem>()
                val listTodosCanais = mutableListOf<CanalItem>()
                val listFavoritos = mutableListOf<CanalItem>()
                val listFilmes = mutableListOf<FilmeItem>()
                val listSeries = mutableListOf<FilmeItem>()
                val listYoutube = mutableListOf<FilmeItem>()

                // =========================================================
                // MÁGICA DO YOUTUBE: Baixa seus vídeos via RSS (Grátis e Rápido)
                // =========================================================
                try {
                    // ATENÇÃO: COLOQUE AQUI O ID DO SEU CANAL!
                    // Como descobrir: Vá no seu canal do YouTube, clique em "Sobre", depois "Compartilhar" -> "Copiar ID do Canal"
                    val myYoutubeChannelId = "DpsgaaYA33CmlzM3" // <-- TROQUE AQUI! (Padrão: MrBeast para testar)
                    
                    val ytReq = Request.Builder().url("https://www.youtube.com/feeds/videos.xml?channel_id=$myYoutubeChannelId").build()
                    val ytXml = client.newCall(ytReq).execute().body?.string() ?: ""
                    
                    val entryPattern = "<entry>(.*?)</entry>".toRegex(RegexOption.DOT_MATCHES_ALL)
                    val idPattern = "<yt:videoId>(.*?)</yt:videoId>".toRegex()
                    val titlePattern = "<title>(.*?)</title>".toRegex()
                    val thumbPattern = "<media:thumbnail url=\"(.*?)\"".toRegex()

                    val entries = entryPattern.findAll(ytXml)
                    for (entry in entries) {
                        val entryTxt = entry.value
                        val vId = idPattern.find(entryTxt)?.groupValues?.get(1) ?: ""
                        val vTitle = titlePattern.find(entryTxt)?.groupValues?.get(1) ?: "Vídeo"
                        val vThumb = thumbPattern.find(entryTxt)?.groupValues?.get(1) ?: ""
                        
                        if (vId.isNotEmpty() && vThumb.isNotEmpty()) {
                            // Usamos o formato "CardAdapter" de filme para desenhar a capinha bonita!
                            listYoutube.add(FilmeItem(vId, vTitle, vThumb, "youtube", "youtube", "", 0))
                        }
                    }
                } catch (e: Exception) {
                    // Se falhar a conexão com o Google, apenas ignora para não quebrar o IPTV
                }

                var req = Request.Builder().url("$urlGlobal/player_api.php?username=$userGlobal&password=$passGlobal&action=get_live_categories").build()
                var jsonStr = client.newCall(req).execute().body?.string() ?: "[]"
                if (jsonStr.startsWith("[")) {
                    val arr = JSONArray(jsonStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val catName = obj.optString("category_name", "")
                        val isAdult = palavrasProibidas.any { catName.lowercase().contains(it) }
                        if (!isParentalActive || !isAdult) liveCats.add(CategoriaItem(obj.optString("category_id"), catName))
                    }
                }
                
                liveCats.sortBy { 
                    val n = it.nome.lowercase()
                    if (n.contains("jogos de hoje") || n.contains("casa do patrão") || n.contains("casa do patrao")) 1 else 0 
                }
                liveCats.add(0, CategoriaItem("FAV", "Canais Favoritos"))
                
                jsonStr = ""

                req = Request.Builder().url("$urlGlobal/player_api.php?username=$userGlobal&password=$passGlobal&action=get_live_streams").build()
                jsonStr = client.newCall(req).execute().body?.string() ?: "[]"
                if (jsonStr.startsWith("[")) {
                    val arr = JSONArray(jsonStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optString("stream_id", "")
                        val nome = obj.optString("name", "Canal")
                        
                        val epgId = obj.optString("epg_channel_id", "")
                        if (epgId.isNotEmpty()) DataHolder.mapaEpgIds[id] = epgId
                        
                        val nUp = nome.uppercase()
                        val isSD = nUp.endsWith(" SD") || nUp.contains(" SD ") || nUp == "SD"
                        val isH265 = nUp.contains("H265") || nUp.contains("HEVC")
                        val is4K = nUp.endsWith(" 4K") || nUp.contains(" 4K ") || nUp.contains("UHD")
                        
                        if (filterSD && isSD) continue
                        if (filterH265 && isH265) continue
                        if (filter4K && is4K) continue

                        val canal = CanalItem(id, nome, obj.optString("stream_icon", ""), obj.optString("category_id"), "$urlGlobal/live/$userGlobal/$passGlobal/$id.ts")
                        listTodosCanais.add(canal)
                        if (listaIdsFavoritos.contains(id)) listFavoritos.add(canal)
                    }
                }
                jsonStr = ""

                req = Request.Builder().url("$urlGlobal/player_api.php?username=$userGlobal&password=$passGlobal&action=get_vod_streams").build()
                jsonStr = client.newCall(req).execute().body?.string() ?: "[]"
                if (jsonStr.startsWith("[")) {
                    val arr = JSONArray(jsonStr)
                    val limite = if (arr.length() > 150) 150 else arr.length()
                    for (i in 0 until limite) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optString("stream_id", "")
                        val ext = obj.optString("container_extension", "mp4")
                        listFilmes.add(FilmeItem(id, obj.optString("name", "Sem Nome"), obj.optString("stream_icon", ""), "$urlGlobal/movie/$userGlobal/$passGlobal/$id.$ext", "filme", "", getProgress(id)))
                    }
                }
                jsonStr = ""

                req = Request.Builder().url("$urlGlobal/player_api.php?username=$userGlobal&password=$passGlobal&action=get_series").build()
                jsonStr = client.newCall(req).execute().body?.string() ?: "[]"
                if (jsonStr.startsWith("[")) {
                    val arr = JSONArray(jsonStr)
                    val limiteS = if (arr.length() > 150) 150 else arr.length()
                    for (i in 0 until limiteS) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optString("series_id", "")
                        listSeries.add(FilmeItem(id, obj.optString("name", "Sem Nome"), obj.optString("cover", ""), "$urlGlobal/player_api.php?username=$userGlobal&password=$passGlobal&action=get_series_info&series_id=$id", "serie", "", getProgress(id)))
                    }
                }
                jsonStr = ""

                val ultimosFilmes = listFilmes.reversed().take(30)
                val topFilmes = listFilmes.take(10)
                val seriesAlta = listSeries.reversed().take(30)
                val topSeries = listSeries.take(10)
                val listContinuar = (listFilmes + listSeries).filter { it.progresso > 0 }

                withContext(Dispatchers.Main) {
                    findViewById<RelativeLayout>(R.id.loadingOverlay).visibility = View.GONE
                    
                    if (listContinuar.isNotEmpty()) {
                        tvContinuarTitulo.visibility = View.VISIBLE
                        recyclerContinuar.visibility = View.VISIBLE
                        recyclerContinuar.adapter = CardAdapter(listContinuar) { abrirDetalhes(it) }
                    } else {
                        tvContinuarTitulo.visibility = View.GONE
                        recyclerContinuar.visibility = View.GONE
                    }
                    
                    // ALIMENTA A PRATELEIRA DO YOUTUBE
                    if (listYoutube.isNotEmpty()) {
                        areaYoutube.visibility = View.VISIBLE
                        // Reutilizamos o adaptador de filmes horizontais para o YouTube (Fica Lindo!)
                        recyclerYoutube.adapter = CardAdapter(listYoutube) { videoClicado ->
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("vnd.youtube:${videoClicado.id}"))
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                startActivity(intent)
                            } catch (e: Exception) {
                                // Se a TV não tiver o App Oficial instalado, abre no navegador da TV
                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=${videoClicado.id}"))
                                startActivity(intent)
                            }
                        }
                    }
                    
                    recyclerFavoritos.adapter = CanalAdapter(listFavoritos, listaIdsFavoritos, { canalClicado ->
                        DataHolder.todasCategorias = liveCats
                        DataHolder.todosCanais = listTodosCanais
                        DataHolder.favoritosIds = listaIdsFavoritos
                        DataHolder.categoriaAtualId = "FAV"
                        startActivity(Intent(this@HomeActivity, PlayerTvActivity::class.java).apply {
                            putExtra("INDICE_CANAL", listFavoritos.indexOf(canalClicado))
                        })
                    }, { })
                    
                    recyclerUltimos.adapter = CardAdapter(ultimosFilmes) { abrirDetalhes(it) }
                    recyclerSeriesAlta.adapter = CardAdapter(seriesAlta) { abrirDetalhes(it) }
                    recyclerTopFilmes.adapter = Top10Adapter(topFilmes) { abrirDetalhes(it) }
                    recyclerTopSeries.adapter = Top10Adapter(topSeries) { abrirDetalhes(it) }

                    if (listFilmes.isNotEmpty()) atualizarBanner(listFilmes.random())
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) { 
                    findViewById<RelativeLayout>(R.id.loadingOverlay).visibility = View.GONE
                    Toast.makeText(this@HomeActivity, "Erro ao carregar o catálogo.", Toast.LENGTH_SHORT).show() 
                }
            }
        }
    }

    private fun atualizarBanner(filme: FilmeItem) {
        findViewById<TextView>(R.id.heroTitle).text = filme.nome
        findViewById<TextView>(R.id.heroBadge).text = "NOVIDADE EM FILMES"
        findViewById<TextView>(R.id.heroDesc).text = "Disponível agora no catálogo"
        btnAssistirDestaque.visibility = View.VISIBLE
        btnAssistirDestaque.setOnClickListener { abrirDetalhes(filme) }
        val imageView = findViewById<ImageView>(R.id.heroImage)
        imageView.scaleType = ImageView.ScaleType.MATRIX
        Glide.with(this).load(filme.urlImagem).into(object : CustomViewTarget<ImageView, Drawable>(imageView) {
            override fun onLoadFailed(errorDrawable: Drawable?) {}
            override fun onResourceCleared(placeholder: Drawable?) {}
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                view.setImageDrawable(resource)
                view.post {
                    val dWidth = resource.intrinsicWidth
                    val vWidth = view.width
                    if (dWidth > 0 && vWidth > 0) {
                        val matrix = Matrix()
                        val scale = vWidth.toFloat() / dWidth.toFloat()
                        matrix.setScale(scale, scale)
                        matrix.postTranslate(0f, 0f)
                        view.imageMatrix = matrix
                    }
                }
            }
        })
    }

    private fun abrirDetalhes(itemClicado: FilmeItem) {
        val intentDet = Intent(this@HomeActivity, DetailsActivity::class.java)
        intentDet.putExtra("URL", urlGlobal)
        intentDet.putExtra("USER", userGlobal)
        intentDet.putExtra("PASS", passGlobal)
        intentDet.putExtra("USERNAME", username) 
        intentDet.putExtra("MEDIA_ID", itemClicado.id)
        intentDet.putExtra("MEDIA_TIPO", itemClicado.tipo)
        intentDet.putExtra("MEDIA_NOME", itemClicado.nome)
        intentDet.putExtra("MEDIA_CAPA", itemClicado.urlImagem)
        startActivity(intentDet)
    }
}
