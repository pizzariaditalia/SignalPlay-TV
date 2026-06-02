package com.signalplay.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class LigaItem(val nome: String, val logo: String, val url: String)
data class AppItem(val nome: String, val icone: Drawable, val pacote: String)

class HomeActivity : Activity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var btnAssistirDestaque: Button

    private var urlGlobal = ""
    private var userGlobal = ""
    private var passGlobal = ""
    private var username = ""

    // Bancos de Memória para atualização instantânea
    private var listFilmesGlobais = listOf<FilmeItem>()
    private var listSeriesGlobais = listOf<FilmeItem>()
    private var listCanaisGlobais = listOf<CanalItem>()
    private var liveCatsGlobal = listOf<CategoriaItem>()
    
    private var listaIdsFavoritosGlobais = mutableListOf<String>()
    private var historicoMapGlobal: Map<String, Any> = emptyMap()

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

        val recyclerContinuar = findViewById<RecyclerView>(R.id.recyclerContinuar)
        val recyclerFavoritos = findViewById<RecyclerView>(R.id.recyclerFavoritos)
        val recyclerUltimos = findViewById<RecyclerView>(R.id.recyclerUltimos)
        val recyclerTopFilmes = findViewById<RecyclerView>(R.id.recyclerTopFilmes)
        val recyclerTopSeries = findViewById<RecyclerView>(R.id.recyclerTopSeries)
        val recyclerSeriesAlta = findViewById<RecyclerView>(R.id.recyclerSeriesAlta)
        val recyclerEsportes = findViewById<RecyclerView>(R.id.recyclerEsportes)
        val recyclerApps = findViewById<RecyclerView>(R.id.recyclerApps)
        
        recyclerEsportes.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerContinuar.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerFavoritos.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerUltimos.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerTopFilmes.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerTopSeries.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerSeriesAlta.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerApps.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

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

        val listaLigas = listOf(
            LigaItem("Brasileirão A", "https://api.sofascore.app/api/v1/unique-tournament/325/image/dark", "https://m.sofascore.com/pt/torneio/futebol/brazil/brasileirao-serie-a/325"),
            LigaItem("Brasileirão B", "https://api.sofascore.app/api/v1/unique-tournament/390/image/dark", "https://m.sofascore.com/pt/torneio/futebol/brazil/brasileirao-serie-b/390"),
            LigaItem("Bundesliga", "https://api.sofascore.app/api/v1/unique-tournament/35/image/dark", "https://m.sofascore.com/pt/torneio/futebol/germany/bundesliga/35"),
            LigaItem("Champions League", "https://api.sofascore.app/api/v1/unique-tournament/7/image/dark", "https://m.sofascore.com/pt/torneio/futebol/europe/uefa-champions-league/7"),
            LigaItem("Série A (Itália)", "https://api.sofascore.app/api/v1/unique-tournament/23/image/dark", "https://m.sofascore.com/pt/torneio/futebol/italy/serie-a/23")
        )

        recyclerEsportes.adapter = LigaAdapter(listaLigas) { liga ->
            val intent = Intent(this@HomeActivity, SportsActivity::class.java)
            intent.putExtra("URL_LIGA", liga.url)
            startActivity(intent)
        }

        carregarAplicativosDaTV()

        // OUVINTE EM TEMPO REAL
        db.collection("usuarios").whereEqualTo("usuario", username)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null && !snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    
                    val favs = doc.get("favoritos") as? List<*>
                    listaIdsFavoritosGlobais.clear()
                    favs?.forEach { listaIdsFavoritosGlobais.add(it.toString()) }
                    
                    historicoMapGlobal = doc.get("historico_vod") as? Map<String, Any> ?: emptyMap()
                    
                    runOnUiThread {
                        if (listFilmesGlobais.isNotEmpty() || listSeriesGlobais.isNotEmpty()) {
                            renderizarContinuarAssistindo()
                        }
                        if (listCanaisGlobais.isNotEmpty()) {
                            renderizarFavoritos()
                        }
                    }
                }
            }

        // CARREGAMENTO DA API COM PROTEÇÃO ANTI-CRASH E TIMEOUT DE 60s
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
                val isParentalActive = prefs.getBoolean("PARENTAL_CONTROL", false)
                val filterSD = prefs.getBoolean("FILTER_SD", false)
                val filterH265 = prefs.getBoolean("FILTER_H265", false)
                val filter4K = prefs.getBoolean("FILTER_4K", false)
                
                val palavrasProibidas = listOf("adult", "+18", "18+", "xxx", "porn", "hachutv", "sensual", "sex")
                
                // MÁGICA: Aumentando o tempo de espera do servidor para não crashar com catálogos gigantes
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build()

                val liveCats = mutableListOf<CategoriaItem>()
                val mapCategorias = mutableMapOf<String, String>() 
                
                val listTodosCanais = mutableListOf<CanalItem>()
                val listFilmes = mutableListOf<FilmeItem>()
                val listSeries = mutableListOf<FilmeItem>()

                val reqLiveCat = Request.Builder().url("$urlGlobal/player_api.php?username=$userGlobal&password=$passGlobal&action=get_live_categories").build()
                val reqVodCat = Request.Builder().url("$urlGlobal/player_api.php?username=$userGlobal&password=$passGlobal&action=get_vod_categories").build()
                val reqSeriesCat = Request.Builder().url("$urlGlobal/player_api.php?username=$userGlobal&password=$passGlobal&action=get_series_categories").build()
                val reqLive = Request.Builder().url("$urlGlobal/player_api.php?username=$userGlobal&password=$passGlobal&action=get_live_streams").build()
                val reqVod = Request.Builder().url("$urlGlobal/player_api.php?username=$userGlobal&password=$passGlobal&action=get_vod_streams").build()
                val reqSeries = Request.Builder().url("$urlGlobal/player_api.php?username=$userGlobal&password=$passGlobal&action=get_series").build()

                // MÁGICA: Colocamos um escudo (try/catch) em cada chamada para o app nunca fechar
                val defLiveCat = async { try { client.newCall(reqLiveCat).execute().body?.string() ?: "[]" } catch (e: Exception) { "[]" } }
                val defVodCat = async { try { client.newCall(reqVodCat).execute().body?.string() ?: "[]" } catch (e: Exception) { "[]" } }
                val defSeriesCat = async { try { client.newCall(reqSeriesCat).execute().body?.string() ?: "[]" } catch (e: Exception) { "[]" } }
                val defLive = async { try { client.newCall(reqLive).execute().body?.string() ?: "[]" } catch (e: Exception) { "[]" } }
                val defVod = async { try { client.newCall(reqVod).execute().body?.string() ?: "[]" } catch (e: Exception) { "[]" } }
                val defSeries = async { try { client.newCall(reqSeries).execute().body?.string() ?: "[]" } catch (e: Exception) { "[]" } }

                val jsonLiveCat = defLiveCat.await()
                val jsonVodCat = defVodCat.await()
                val jsonSeriesCat = defSeriesCat.await()
                val jsonLive = defLive.await()
                val jsonVod = defVod.await()
                val jsonSeries = defSeries.await()

                if (jsonLiveCat.startsWith("[")) {
                    val arr = JSONArray(jsonLiveCat)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val catName = obj.optString("category_name", "")
                        val isAdult = palavrasProibidas.any { catName.lowercase().contains(it) }
                        if (!isParentalActive || !isAdult) liveCats.add(CategoriaItem(obj.optString("category_id"), catName))
                    }
                }
                
                if (jsonVodCat.startsWith("[")) {
                    val arr = JSONArray(jsonVodCat)
                    for (i in 0 until arr.length()) mapCategorias[arr.getJSONObject(i).optString("category_id")] = arr.getJSONObject(i).optString("category_name")
                }
                if (jsonSeriesCat.startsWith("[")) {
                    val arr = JSONArray(jsonSeriesCat)
                    for (i in 0 until arr.length()) mapCategorias[arr.getJSONObject(i).optString("category_id")] = arr.getJSONObject(i).optString("category_name")
                }
                
                liveCats.sortBy { 
                    val n = it.nome.lowercase()
                    if (n.contains("jogos de hoje") || n.contains("casa do patrão") || n.contains("casa do patrao")) 1 else 0 
                }
                liveCats.add(0, CategoriaItem("FAV", "Canais Favoritos"))
                liveCatsGlobal = liveCats

                if (jsonLive.startsWith("[")) {
                    val arr = JSONArray(jsonLive)
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

                        listTodosCanais.add(CanalItem(id, nome, obj.optString("stream_icon", ""), obj.optString("category_id"), "$urlGlobal/live/$userGlobal/$passGlobal/$id.ts"))
                    }
                }

                if (jsonVod.startsWith("[")) {
                    val arr = JSONArray(jsonVod)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optString("stream_id", "")
                        val ext = obj.optString("container_extension", "mp4")
                        listFilmes.add(FilmeItem(id, obj.optString("name", "Sem Nome"), obj.optString("stream_icon", ""), "$urlGlobal/movie/$userGlobal/$passGlobal/$id.$ext", "filme", obj.optString("category_id"), 0))
                    }
                }

                if (jsonSeries.startsWith("[")) {
                    val arr = JSONArray(jsonSeries)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optString("series_id", "")
                        listSeries.add(FilmeItem(id, obj.optString("name", "Sem Nome"), obj.optString("cover", ""), "$urlGlobal/player_api.php?username=$userGlobal&password=$passGlobal&action=get_series_info&series_id=$id", "serie", obj.optString("category_id"), 0))
                    }
                }
                
                listCanaisGlobais = listTodosCanais
                listFilmesGlobais = listFilmes
                listSeriesGlobais = listSeries

                withContext(Dispatchers.Main) {
                    findViewById<RelativeLayout>(R.id.loadingOverlay).visibility = View.GONE
                    
                    renderizarContinuarAssistindo()
                    renderizarFavoritos()
                    
                    val recyclerUltimos = findViewById<RecyclerView>(R.id.recyclerUltimos)
                    val recyclerTopFilmes = findViewById<RecyclerView>(R.id.recyclerTopFilmes)
                    val recyclerTopSeries = findViewById<RecyclerView>(R.id.recyclerTopSeries)
                    val recyclerSeriesAlta = findViewById<RecyclerView>(R.id.recyclerSeriesAlta)

                    recyclerUltimos.adapter = CardAdapter(listFilmes.reversed().take(30)) { abrirDetalhes(it) }
                    recyclerSeriesAlta.adapter = CardAdapter(listSeries.reversed().take(30)) { abrirDetalhes(it) }
                    recyclerTopFilmes.adapter = Top10Adapter(listFilmes.take(10)) { abrirDetalhes(it) }
                    recyclerTopSeries.adapter = Top10Adapter(listSeries.take(10)) { abrirDetalhes(it) }

                    if (listFilmes.isNotEmpty()) {
                        atualizarBanner(listFilmes.random(), mapCategorias)
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) { 
                    findViewById<RelativeLayout>(R.id.loadingOverlay).visibility = View.GONE
                    Toast.makeText(this@HomeActivity, "Erro ao conectar com o servidor.", Toast.LENGTH_SHORT).show() 
                }
            }
        }
    }

    private fun renderizarContinuarAssistindo() {
        val listContinuar = mutableListOf<FilmeItem>()
        
        val processarItem = { item: FilmeItem ->
            val progData = historicoMapGlobal[item.id] as? Map<String, Any>
            if (progData != null) {
                val pos = progData["posicao"]?.toString()?.toLongOrNull() ?: 0L
                val dur = progData["duracao"]?.toString()?.toLongOrNull() ?: 0L
                
                if (dur > 0L && pos > 5000L) {
                    var perc = ((pos.toDouble() / dur.toDouble()) * 100).toInt()
                    if (perc <= 0) perc = 1 
                    if (perc > 99) perc = 99
                    listContinuar.add(FilmeItem(item.id, item.nome, item.urlImagem, item.streamUrl, item.tipo, item.categoryId, perc))
                }
            }
        }
        
        listFilmesGlobais.forEach(processarItem)
        listSeriesGlobais.forEach(processarItem)
        
        val recyclerContinuar = findViewById<RecyclerView>(R.id.recyclerContinuar)
        val tvContinuarTitulo = findViewById<TextView>(R.id.tvContinuarTitulo)
        
        if (listContinuar.isNotEmpty()) {
            tvContinuarTitulo.visibility = View.VISIBLE
            recyclerContinuar.visibility = View.VISIBLE
            recyclerContinuar.adapter = CardAdapter(listContinuar.sortedByDescending { it.progresso }) { abrirDetalhes(it) }
        } else {
            tvContinuarTitulo.visibility = View.GONE
            recyclerContinuar.visibility = View.GONE
        }
    }

    private fun renderizarFavoritos() {
        val recyclerFavoritos = findViewById<RecyclerView>(R.id.recyclerFavoritos)
        val tvFavoritosTitulo = findViewById<TextView>(R.id.tvFavoritosTitulo)
        val listFavoritos = listCanaisGlobais.filter { listaIdsFavoritosGlobais.contains(it.id) }

        if (listFavoritos.isNotEmpty()) {
            tvFavoritosTitulo.visibility = View.VISIBLE
            recyclerFavoritos.visibility = View.VISIBLE
            recyclerFavoritos.adapter = CanalAdapter(listFavoritos, listaIdsFavoritosGlobais, { canalClicado ->
                DataHolder.todasCategorias = liveCatsGlobal
                DataHolder.todosCanais = listCanaisGlobais
                DataHolder.favoritosIds = listaIdsFavoritosGlobais
                DataHolder.categoriaAtualId = "FAV"
                startActivity(Intent(this@HomeActivity, PlayerTvActivity::class.java).apply {
                    putExtra("INDICE_CANAL", listFavoritos.indexOf(canalClicado))
                })
            }, { })
        } else {
            tvFavoritosTitulo.visibility = View.GONE
            recyclerFavoritos.visibility = View.GONE
        }
    }

    private fun carregarAplicativosDaTV() {
        val pm = packageManager
        val installedApps = mutableMapOf<String, AppItem>()

        try {
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in packages) {
                val pkg = appInfo.packageName
                if (pkg == applicationContext.packageName) continue
                
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    val nome = appInfo.loadLabel(pm).toString()
                    val icone = appInfo.loadIcon(pm)
                    installedApps[pkg] = AppItem(nome, icone, pkg)
                }
            }
        } catch (e: Exception) {}

        val listaFinal = installedApps.values.toMutableList()
        listaFinal.sortBy { it.nome }

        val recyclerApps = findViewById<RecyclerView>(R.id.recyclerApps)
        recyclerApps.adapter = AppAdapter(listaFinal) { app ->
            val launchIntent = pm.getLaunchIntentForPackage(app.pacote)
            if (launchIntent != null) startActivity(launchIntent)
            else Toast.makeText(this, "Não foi possível abrir o aplicativo.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun atualizarBanner(filme: FilmeItem, mapCategorias: Map<String, String>) {
        val tvTitle = findViewById<TextView>(R.id.heroTitle)
        val tvBadge = findViewById<TextView>(R.id.heroBadge)
        val tvDesc = findViewById<TextView>(R.id.heroDesc)
        
        tvTitle.text = filme.nome
        
        val nomeDaPasta = mapCategorias[filme.categoryId] ?: "DESTAQUE"
        tvBadge.text = "PASTA: ${nomeDaPasta.uppercase()}"
        tvDesc.text = "Buscando informações..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
                val action = if (filme.tipo == "serie") "get_series_info&series_id=${filme.id}" else "get_vod_info&vod_id=${filme.id}"
                val req = Request.Builder().url("$urlGlobal/player_api.php?username=$userGlobal&password=$passGlobal&action=$action").build()
                val res = client.newCall(req).execute().body?.string() ?: "{}"
                
                var plot = "Sinopse não disponível para este conteúdo."
                if (res.startsWith("{")) {
                    val json = JSONObject(res)
                    val info = json.optJSONObject("info")
                    if (info != null) {
                        plot = info.optString("plot", "Sinopse não disponível.")
                        if (plot.isEmpty() || plot == "null") plot = "Sinopse não disponível."
                    }
                }
                withContext(Dispatchers.Main) { tvDesc.text = plot }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { tvDesc.text = "Sinopse não disponível." }
            }
        }

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

    inner class LigaAdapter(private val list: List<LigaItem>, private val onClick: (LigaItem) -> Unit) : RecyclerView.Adapter<LigaAdapter.LigaViewHolder>() {
        inner class LigaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val img = view.findViewById<ImageView>(1001)
            val txt = view.findViewById<TextView>(1002)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LigaViewHolder {
            val layout = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                layoutParams = ViewGroup.MarginLayoutParams(280, 280).apply { setMargins(16, 16, 16, 16) }
                background = ContextCompat.getDrawable(parent.context, R.drawable.bg_menu_focus)
                isFocusable = true; isClickable = true; setPadding(16, 16, 16, 16)
            }
            val img = ImageView(parent.context).apply { id = 1001; layoutParams = LinearLayout.LayoutParams(110, 110) }
            val txt = TextView(parent.context).apply { 
                id = 1002
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 16 }
                setTextColor(Color.WHITE); textSize = 12f; textAlignment = View.TEXT_ALIGNMENT_CENTER
                maxLines = 2; setLines(2)
            }
            layout.addView(img); layout.addView(txt)
            return LigaViewHolder(layout)
        }
        override fun onBindViewHolder(holder: LigaViewHolder, position: Int) {
            val item = list[position]
            holder.txt.text = item.nome
            Glide.with(holder.itemView.context).load(item.logo).into(holder.img)
            holder.itemView.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) { v.bringToFront(); v.animate().scaleX(1.10f).scaleY(1.10f).translationZ(10f).setDuration(150).start() } 
                else { v.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(150).start() }
            }
            holder.itemView.setOnClickListener { onClick(item) }
        }
        override fun getItemCount(): Int = list.size
    }

    inner class AppAdapter(private val list: List<AppItem>, private val onClick: (AppItem) -> Unit) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {
        inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val img = view.findViewById<ImageView>(2001)
            val txt = view.findViewById<TextView>(2002)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val layout = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                layoutParams = ViewGroup.MarginLayoutParams(240, 240).apply { setMargins(16, 16, 16, 16) }
                background = ContextCompat.getDrawable(parent.context, R.drawable.bg_menu_focus)
                isFocusable = true; isClickable = true; setPadding(16, 16, 16, 16)
            }
            val img = ImageView(parent.context).apply { id = 2001; layoutParams = LinearLayout.LayoutParams(120, 120); scaleType = ImageView.ScaleType.FIT_CENTER }
            val txt = TextView(parent.context).apply { id = 2002; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 12 }; setTextColor(Color.WHITE); textSize = 12f; textAlignment = View.TEXT_ALIGNMENT_CENTER; maxLines = 1 }
            layout.addView(img); layout.addView(txt)
            return AppViewHolder(layout)
        }
        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            val item = list[position]
            holder.txt.text = item.nome
            holder.img.setImageDrawable(item.icone)
            holder.itemView.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) { v.bringToFront(); v.animate().scaleX(1.10f).scaleY(1.10f).translationZ(10f).setDuration(150).start() } 
                else { v.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(150).start() }
            }
            holder.itemView.setOnClickListener { onClick(item) }
        }
        override fun getItemCount(): Int = list.size
    }
}
