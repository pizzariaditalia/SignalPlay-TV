package com.signalplay.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
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

data class LigaItem(val nome: String, val logo: String, val url: String)

class HomeActivity : Activity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var btnAssistirDestaque: Button

    private var urlGlobal = ""
    private var userGlobal = ""
    private var passGlobal = ""
    private var username = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        actionBar?.hide()
        
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
        
        val recyclerEsportes = findViewById<RecyclerView>(R.id.recyclerEsportes)
        
        recyclerEsportes.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerContinuar.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerFavoritos.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerUltimos.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerTopFilmes.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerTopSeries.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerSeriesAlta.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        val urlOriginal = intent.getStringExtra("URL") ?: ""
        urlGlobal = if (urlOriginal.endsWith("/")) urlOriginal.dropLast(1) else urlOriginal
        userGlobal = intent.getStringExtra("USER") ?: ""
        passGlobal = intent.getStringExtra("PASS") ?: ""
        username = intent.getStringExtra("USERNAME") ?: ""

        menuPesquisar.setOnClickListener { startActivity(Intent(this, SearchActivity::class.java).apply { putExtras(intent) }) }
        menuCanais.setOnClickListener { startActivity(Intent(this, TvActivity::class.java).apply { putExtras(intent) }) }
        menuFilmes.setOnClickListener { startActivity(Intent(this, VodActivity::class.java).apply { putExtras(intent) }) }
        menuSeries.setOnClickListener { startActivity(Intent(this, SeriesActivity::class.java).apply { putExtras(intent) }) }
        menuConfig.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java).apply { putExtras(intent) }) }

        // =================================================================================
        // LISTA GIGANTE DE ESPORTES (27 Ligas em Ordem Alfabética + Link Direto do App)
        // =================================================================================
        val listaLigas = listOf(
            LigaItem("Brasileirão A", "https://a.espncdn.com/i/leaguelogos/soccer/500/85.png", "https://m.flashscore.com.br/futebol/brasil/serie-a/classificacao/"),
            LigaItem("Brasileirão B", "https://upload.wikimedia.org/wikipedia/pt/f/f4/Campeonato_Brasileiro_S%C3%A9rie_B_logo.png", "https://m.flashscore.com.br/futebol/brasil/serie-b/classificacao/"),
            LigaItem("Bundesliga", "https://a.espncdn.com/i/leaguelogos/soccer/500/10.png", "https://m.flashscore.com.br/futebol/alemanha/bundesliga/classificacao/"),
            LigaItem("Camp. Argentino", "https://a.espncdn.com/i/leaguelogos/soccer/500/1.png", "https://m.flashscore.com.br/futebol/argentina/liga-profissional/classificacao/"),
            LigaItem("Camp. Carioca", "https://upload.wikimedia.org/wikipedia/pt/1/13/Campeonato_Carioca_de_Futebol_-_Logotipo.png", "https://m.flashscore.com.br/futebol/brasil/campeonato-carioca/classificacao/"),
            LigaItem("Camp. Paulista", "https://upload.wikimedia.org/wikipedia/pt/7/77/Campeonato_Paulista.png", "https://m.flashscore.com.br/futebol/brasil/campeonato-paulista/classificacao/"),
            LigaItem("Camp. Saudita", "https://upload.wikimedia.org/wikipedia/en/thumb/e/e0/Saudi_Pro_League_logo.svg/512px-Saudi_Pro_League_logo.svg.png", "https://m.flashscore.com.br/futebol/arabia-saudita/liga-profissional-saudita/classificacao/"),
            LigaItem("Champions League", "https://a.espncdn.com/i/leaguelogos/soccer/500/2.png", "https://m.flashscore.com.br/futebol/europa/liga-dos-campeoes/classificacao/"),
            LigaItem("Copa América", "https://upload.wikimedia.org/wikipedia/en/thumb/0/03/2024_Copa_Am%C3%A9rica_logo.svg/512px-2024_Copa_Am%C3%A9rica_logo.svg.png", "https://m.flashscore.com.br/futebol/america-do-sul/copa-america/classificacao/"),
            LigaItem("Copa da Inglaterra", "https://upload.wikimedia.org/wikipedia/en/thumb/0/05/FA_Cup_logo.svg/512px-FA_Cup_logo.svg.png", "https://m.flashscore.com.br/futebol/inglaterra/copa-da-inglaterra/resultados/"),
            LigaItem("Copa do Brasil", "https://a.espncdn.com/i/leaguelogos/soccer/500/90.png", "https://m.flashscore.com.br/futebol/brasil/copa-do-brasil/resultados/"),
            LigaItem("Copa do Mundo", "https://upload.wikimedia.org/wikipedia/en/thumb/e/e3/2026_FIFA_World_Cup_logo.svg/512px-2026_FIFA_World_Cup_logo.svg.png", "https://m.flashscore.com.br/futebol/mundo/copa-do-mundo/classificacao/"),
            LigaItem("Copa do Nordeste", "https://upload.wikimedia.org/wikipedia/pt/thumb/a/a2/Copa_do_Nordeste.svg/512px-Copa_do_Nordeste.svg.png", "https://m.flashscore.com.br/futebol/brasil/copa-do-nordeste/classificacao/"),
            LigaItem("Copa do Rei", "https://upload.wikimedia.org/wikipedia/en/thumb/1/13/Copa_del_Rey_logo.svg/512px-Copa_del_Rey_logo.svg.png", "https://m.flashscore.com.br/futebol/espanha/copa-do-rei/resultados/"),
            LigaItem("Eredivisie", "https://a.espncdn.com/i/leaguelogos/soccer/500/11.png", "https://m.flashscore.com.br/futebol/holanda/eredivisie/classificacao/"),
            LigaItem("Eurocopa", "https://upload.wikimedia.org/wikipedia/en/thumb/7/74/UEFA_Euro_2024_logo.svg/512px-UEFA_Euro_2024_logo.svg.png", "https://m.flashscore.com.br/futebol/europa/eurocopa/classificacao/"),
            LigaItem("Europa League", "https://a.espncdn.com/i/leaguelogos/soccer/500/2310.png", "https://m.flashscore.com.br/futebol/europa/liga-europa/classificacao/"),
            LigaItem("La Liga", "https://a.espncdn.com/i/leaguelogos/soccer/500/15.png", "https://m.flashscore.com.br/futebol/espanha/laliga/classificacao/"),
            LigaItem("Libertadores", "https://a.espncdn.com/i/leaguelogos/soccer/500/14.png", "https://m.flashscore.com.br/futebol/america-do-sul/copa-libertadores/classificacao/"),
            LigaItem("Ligue 1", "https://a.espncdn.com/i/leaguelogos/soccer/500/9.png", "https://m.flashscore.com.br/futebol/franca/ligue-1/classificacao/"),
            LigaItem("MLS", "https://a.espncdn.com/i/leaguelogos/soccer/500/19.png", "https://m.flashscore.com.br/futebol/eua/mls/classificacao/"),
            LigaItem("Mundial de Clubes", "https://a.espncdn.com/i/leaguelogos/soccer/500/125.png", "https://m.flashscore.com.br/futebol/mundo/mundial-de-clubes/resultados/"),
            LigaItem("Nations League", "https://upload.wikimedia.org/wikipedia/en/thumb/a/a2/UEFA_Nations_League_logo.svg/512px-UEFA_Nations_League_logo.svg.png", "https://m.flashscore.com.br/futebol/europa/liga-das-nacoes/classificacao/"),
            LigaItem("Premier League", "https://a.espncdn.com/i/leaguelogos/soccer/500/23.png", "https://m.flashscore.com.br/futebol/inglaterra/premier-league/classificacao/"),
            LigaItem("Primeira Liga", "https://a.espncdn.com/i/leaguelogos/soccer/500/12.png", "https://m.flashscore.com.br/futebol/portugal/liga-portugal/classificacao/"),
            LigaItem("Série A (Itália)", "https://a.espncdn.com/i/leaguelogos/soccer/500/13.png", "https://m.flashscore.com.br/futebol/italia/serie-a/classificacao/"),
            LigaItem("Sul-Americana", "https://a.espncdn.com/i/leaguelogos/soccer/500/16.png", "https://m.flashscore.com.br/futebol/america-do-sul/copa-sul-americana/classificacao/")
        )

        recyclerEsportes.adapter = LigaAdapter(listaLigas) { liga ->
            val intent = Intent(this@HomeActivity, SportsActivity::class.java)
            intent.putExtra("URL_LIGA", liga.url)
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
                        val isSD = nUp.contains(" SD ") || nUp.endsWith(" SD") || nUp.startsWith("SD ") || nUp.contains("(SD)") || nUp.contains("[SD]") || nUp.contains("|SD|") || nUp.contains("- SD") || nUp == "SD"
                        val isH265 = nUp.contains("H265") || nUp.contains("HEVC") || nUp.contains("H.265")
                        val is4K = nUp.contains(" 4K ") || nUp.endsWith(" 4K") || nUp.startsWith("4K ") || nUp.contains("(4K)") || nUp.contains("[4K]") || nUp.contains("|4K|") || nUp.contains("- 4K") || nUp.contains("UHD") || nUp == "4K"
                        
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

    // ADAPTADOR INTERNO QUE CRIA OS QUADRADINHOS DE CADA LIGA NO CARROSSEL
    inner class LigaAdapter(
        private val list: List<LigaItem>,
        private val onClick: (LigaItem) -> Unit
    ) : RecyclerView.Adapter<LigaAdapter.LigaViewHolder>() {

        inner class LigaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val img = view.findViewById<ImageView>(1001)
            val txt = view.findViewById<TextView>(1002)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LigaViewHolder {
            val context = parent.context
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                layoutParams = ViewGroup.MarginLayoutParams(280, 280).apply { setMargins(16, 16, 16, 16) }
                background = ContextCompat.getDrawable(context, R.drawable.bg_menu_focus)
                isFocusable = true
                isClickable = true
                setPadding(16, 16, 16, 16)
            }
            val img = ImageView(context).apply { id = 1001; layoutParams = LinearLayout.LayoutParams(130, 130) }
            val txt = TextView(context).apply { 
                id = 1002
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 16 }
                setTextColor(Color.WHITE)
                textSize = 14f
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                maxLines = 1
            }
            layout.addView(img)
            layout.addView(txt)
            return LigaViewHolder(layout)
        }

        override fun onBindViewHolder(holder: LigaViewHolder, position: Int) {
            val item = list[position]
            holder.txt.text = item.nome
            Glide.with(holder.itemView.context).load(item.logo).into(holder.img)

            holder.itemView.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.bringToFront()
                    v.animate().scaleX(1.10f).scaleY(1.10f).translationZ(10f).setDuration(150).start()
                } else {
                    v.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(150).start()
                }
            }
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = list.size
    }
}
