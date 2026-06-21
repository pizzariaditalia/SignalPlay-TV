package com.signalplay.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.widget.HorizontalGridView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AppItem(val nome: String, val icone: Drawable, val pacote: String)

data class FilmeContinuar(val filme: FilmeItem, val progressoPerc: Int, val timestamp: Long, val restanteMinutos: Int)

class HomeActivity : Activity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var btnAssistirDestaque: Button
    private lateinit var heroImage: ImageView
    
    private lateinit var tvClock: TextView
    private lateinit var tvNetworkStatus: TextView
    private var isClockRunning = true

    private val suaveOvershoot = OvershootInterpolator(1.2f)

    private var urlGlobal = ""
    private var userGlobal = ""
    private var passGlobal = ""
    private var username = ""
    
    private var isLowEndMode = false

    private var listFilmesGlobais = listOf<FilmeItem>()
    private var listSeriesGlobais = listOf<FilmeItem>()
    private var listCanaisGlobais = listOf<CanalItem>()
    private var liveCatsGlobal = listOf<CategoriaItem>()
    
    private var mapVodCatsGlobal: Map<String, String> = emptyMap()
    private var mapSeriesCatsGlobal: Map<String, String> = emptyMap()
    
    private var listaIdsFavoritosGlobais = mutableListOf<String>()
    private var historicoMapGlobal: Map<String, Any> = emptyMap()

    private val activityJob = Job()
    private val activityScope = CoroutineScope(Dispatchers.IO + activityJob)
    private var heartbeatJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        actionBar?.hide()
        TvNavigationUtils.aplicarModoImersivo(this)
        
        setContentView(R.layout.activity_home)
        
        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
        isLowEndMode = prefs.getBoolean("LOW_END_MODE", false)

        db = FirebaseFirestore.getInstance()
        btnAssistirDestaque = findViewById(R.id.btnAssistirDestaque)
        heroImage = findViewById(R.id.heroImage)
        tvClock = findViewById(R.id.tvClock)
        tvNetworkStatus = findViewById(R.id.tvNetworkStatus)
        
        val mainScrollView = findViewById<TvScrollView>(R.id.mainScrollView)

        btnAssistirDestaque.setBackgroundResource(R.drawable.bg_btn_white)

        btnAssistirDestaque.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                if(!isLowEndMode) v.animate().scaleX(1.05f).scaleY(1.05f).translationZ(10f).setDuration(250).setInterpolator(suaveOvershoot).start()
                else v.setBackgroundColor(Color.parseColor("#FFC107"))
            } else {
                if(!isLowEndMode) v.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(250).setInterpolator(suaveOvershoot).start()
                else v.setBackgroundResource(R.drawable.bg_btn_white)
            }
        }

        val menuPesquisar = findViewById<TextView>(R.id.menuPesquisar)
        val menuInicio = findViewById<TextView>(R.id.menuInicio)
        val menuCanais = findViewById<TextView>(R.id.menuCanais)
        val menuFilmes = findViewById<TextView>(R.id.menuFilmes)
        val menuSeries = findViewById<TextView>(R.id.menuSeries)
        val menuEsportes = findViewById<TextView>(R.id.menuEsportes)
        val menuConfig = findViewById<TextView>(R.id.menuConfig)

        val menuFocusListener = View.OnFocusChangeListener { v, hasFocus ->
            val txt = v as TextView
            if (hasFocus) {
                txt.setTextColor(Color.BLACK)
                mainScrollView.post { mainScrollView.smoothScrollTo(0, 0) }
                
                if(!isLowEndMode) v.animate().scaleX(1.08f).scaleY(1.08f).translationZ(10f).setDuration(250).setInterpolator(suaveOvershoot).start()
                else v.setBackgroundColor(Color.WHITE)
            } else {
                txt.setTextColor(Color.WHITE)
                if(!isLowEndMode) v.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(250).setInterpolator(suaveOvershoot).start()
                else v.setBackgroundResource(R.drawable.bg_menu_focus)
            }
        }
        
        menuPesquisar.onFocusChangeListener = menuFocusListener
        menuInicio.onFocusChangeListener = menuFocusListener
        menuCanais.onFocusChangeListener = menuFocusListener
        menuFilmes.onFocusChangeListener = menuFocusListener
        menuSeries.onFocusChangeListener = menuFocusListener
        menuEsportes.onFocusChangeListener = menuFocusListener
        menuConfig.onFocusChangeListener = menuFocusListener

        val recyclerContinuar = findViewById<HorizontalGridView>(R.id.recyclerContinuar)
        val recyclerFavoritos = findViewById<HorizontalGridView>(R.id.recyclerFavoritos)
        val recyclerUltimos = findViewById<HorizontalGridView>(R.id.recyclerUltimos)
        val recyclerTopFilmes = findViewById<HorizontalGridView>(R.id.recyclerTopFilmes)
        val recyclerTopSeries = findViewById<HorizontalGridView>(R.id.recyclerTopSeries)
        val recyclerSeriesAlta = findViewById<HorizontalGridView>(R.id.recyclerSeriesAlta)
        val recyclerApps = findViewById<HorizontalGridView>(R.id.recyclerApps)
        
        TvNavigationUtils.configurarPrateleira(recyclerContinuar)
        TvNavigationUtils.configurarPrateleira(recyclerFavoritos)
        TvNavigationUtils.configurarPrateleira(recyclerUltimos)
        TvNavigationUtils.configurarPrateleira(recyclerTopFilmes)
        TvNavigationUtils.configurarPrateleira(recyclerTopSeries)
        TvNavigationUtils.configurarPrateleira(recyclerSeriesAlta)
        TvNavigationUtils.configurarPrateleira(recyclerApps)

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
        menuEsportes.setOnClickListener { startActivity(Intent(this, SportsActivity::class.java).apply { putExtra("URL_LIGA", "https://m.sofascore.com/pt/") }) }

        val btnGuiaEpg = findViewById<LinearLayout>(R.id.btnGuiaEpg)
        if (btnGuiaEpg != null) {
            btnGuiaEpg.setOnClickListener { 
                startActivity(Intent(this, EpgGuideActivity::class.java).apply { putExtras(intent) }) 
            }
            btnGuiaEpg.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    if(!isLowEndMode) v.animate().scaleX(1.02f).scaleY(1.02f).translationZ(15f).setDuration(200).setInterpolator(suaveOvershoot).start()
                    v.setBackgroundResource(R.drawable.bg_card_outline)
                } else {
                    if(!isLowEndMode) v.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(200).setInterpolator(suaveOvershoot).start()
                    v.setBackgroundResource(R.drawable.bg_glass)
                }
            }
        }

        db.collection("usuarios").whereEqualTo("usuario", username)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null && !snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    val favs = doc.get("favoritos") as? List<*>
                    listaIdsFavoritosGlobais.clear()
                    favs?.forEach { listaIdsFavoritosGlobais.add(it.toString()) }
                    historicoMapGlobal = doc.get("historico_vod") as? Map<String, Any> ?: emptyMap()
                    
                    runOnUiThread {
                        if (listFilmesGlobais.isNotEmpty() || listSeriesGlobais.isNotEmpty()) renderizarContinuarAssistindo()
                        if (listCanaisGlobais.isNotEmpty()) renderizarFavoritos()
                    }
                }
            }

        iniciarRelogioERede()
        iniciarBatimentoCardiaco()
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            TvNavigationUtils.aplicarModoImersivo(this)
        }
    }

    private fun iniciarBatimentoCardiaco() {
        heartbeatJob?.cancel()
        heartbeatJob = activityScope.launch {
            val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
            val sessionId = prefs.getString("SESSION_ID", "") ?: ""
            val docId = prefs.getString("USER_DOC_ID", "") ?: ""
            
            if (sessionId.isNotEmpty() && docId.isNotEmpty()) {
                while (isActive) {
                    try {
                        db.collection("usuarios").document(docId)
                            .update("sessoes.$sessionId", System.currentTimeMillis())
                    } catch (e: Exception) {}
                    delay(45000)
                }
            }
        }
    }

    private fun iniciarRelogioERede() {
        Thread {
            while (isClockRunning) {
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val network = cm.activeNetwork
                val capabilities = cm.getNetworkCapabilities(network)
                
                var netStatus = "Offline"
                var netColor = "#FF4757"
                
                if (capabilities != null) {
                    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        netStatus = "Wi-Fi"
                        netColor = "#2ED573"
                    } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        netStatus = "Cabo (LAN)"
                        netColor = "#0091EA"
                    } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        netStatus = "4G/5G"
                        netColor = "#FFC107"
                    }
                }

                runOnUiThread {
                    tvClock.text = time
                    tvNetworkStatus.text = netStatus
                    tvNetworkStatus.setTextColor(Color.parseColor(netColor))
                }
                Thread.sleep(1000)
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        carregarAplicativosDaTV()
        if (listFilmesGlobais.isEmpty()) {
            carregarCatalogoDaAPI()
        } else {
            carregarDestaqueAleatorio()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isClockRunning = false
        activityJob.cancel()
    }

    private fun carregarCatalogoDaAPI() {
        activityScope.launch {
            try {
                val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
                val isParentalActive = prefs.getBoolean("PARENTAL_CONTROL", false)
                val filterSD = prefs.getBoolean("FILTER_SD", false)
                val filterHD = prefs.getBoolean("FILTER_HD", false)
                val filterFHD = prefs.getBoolean("FILTER_FHD", false)
                val filterH265 = prefs.getBoolean("FILTER_H265", false)
                val filter4K = prefs.getBoolean("FILTER_4K", false)

                val forcedHide4K = prefs.getBoolean("SERVER_FORCED_HIDE_4K", false)
                val forcedHideFHD = prefs.getBoolean("SERVER_FORCED_HIDE_FHD", false)

                // =========================================================================
                // MÁGICA DA CORREÇÃO 1: Evita que o app bloqueie tudo se a string vier vazia!
                // =========================================================================
                val bloqueadosCanais = prefs.getString("BLOQUEIOS_CANAIS", "")?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() } ?: emptyList()
                val bloqueadosFilmes = prefs.getString("BLOQUEIOS_FILMES", "")?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() } ?: emptyList()
                val bloqueadosSeries = prefs.getString("BLOQUEIOS_SERIES", "")?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() } ?: emptyList()

                val dao = AppDatabase.getDatabase(this@HomeActivity).catalogoDao()
                
                // =========================================================================
                // MÁGICA DA CORREÇÃO 2: Timeout aumentado para 60s para evitar travamentos
                // =========================================================================
                val client = OkHttpClient.Builder().connectTimeout(60, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()

                val reqLiveCat = Request.Builder().url("$urlGlobal/player_api.php?username=$userGlobal&password=$passGlobal&action=get_live_categories").build()
                val reqVodCat = Request.Builder().url("$urlGlobal/player_api.php?username=$userGlobal&password=$passGlobal&action=get_vod_categories").build()
                val reqSeriesCat = Request.Builder().url("$urlGlobal/player_api.php?username=$userGlobal&password=$passGlobal&action=get_series_categories").build()
                val reqLive = Request.Builder().url("$urlGlobal/player_api.php?username=$userGlobal&password=$passGlobal&action=get_live_streams").build()
                val reqVod = Request.Builder().url("$urlGlobal/player_api.php?username=$userGlobal&password=$passGlobal&action=get_vod_streams").build()
                val reqSeries = Request.Builder().url("$urlGlobal/player_api.php?username=$userGlobal&password=$passGlobal&action=get_series").build()

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

                val categoriasParaSalvar = mutableListOf<CategoriaEntity>()
                val canaisParaSalvar = mutableListOf<CanalEntity>()
                val filmesSeriesParaSalvar = mutableListOf<FilmeEntity>()

                if (jsonLiveCat.startsWith("[")) {
                    val arr = JSONArray(jsonLiveCat)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val catName = obj.optString("category_name", "")
                        
                        if (!ContentFilterUtils.isContentBlocked("", catName, isParentalActive, false, false, false, false, false, false, false, bloqueadosCanais)) {
                            categoriasParaSalvar.add(CategoriaEntity(obj.optString("category_id"), catName, "live", i))
                        }
                    }
                }
                
                if (jsonVodCat.startsWith("[")) {
                    val arr = JSONArray(jsonVodCat)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val catName = obj.optString("category_name", "")
                        
                        if (!ContentFilterUtils.isContentBlocked("", catName, isParentalActive, false, false, false, false, false, false, false, bloqueadosFilmes)) {
                            categoriasParaSalvar.add(CategoriaEntity(obj.optString("category_id"), catName, "vod", i))
                        }
                    }
                }
                
                if (jsonSeriesCat.startsWith("[")) {
                    val arr = JSONArray(jsonSeriesCat)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val catName = obj.optString("category_name", "")
                        
                        if (!ContentFilterUtils.isContentBlocked("", catName, isParentalActive, false, false, false, false, false, false, false, bloqueadosSeries)) {
                            categoriasParaSalvar.add(CategoriaEntity(obj.optString("category_id"), catName, "series", i))
                        }
                    }
                }

                if (jsonLive.startsWith("[")) {
                    val arr = JSONArray(jsonLive)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        canaisParaSalvar.add(CanalEntity(
                            id = obj.optString("stream_id", ""),
                            nome = obj.optString("name", "Canal"),
                            urlImagem = obj.optString("stream_icon", ""),
                            categoryId = obj.optString("category_id", ""),
                            streamUrl = "$urlGlobal/live/$userGlobal/$passGlobal/${obj.optString("stream_id", "")}.ts",
                            epgChannelId = obj.optString("epg_channel_id", "")
                        ))
                    }
                }

                if (jsonVod.startsWith("[")) {
                    val arr = JSONArray(jsonVod)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optString("stream_id", "")
                        val ext = obj.optString("container_extension", "mp4")
                        filmesSeriesParaSalvar.add(FilmeEntity(
                            id = id,
                            nome = obj.optString("name", "Sem Nome"),
                            urlImagem = obj.optString("stream_icon", ""),
                            streamUrl = "$urlGlobal/movie/$userGlobal/$passGlobal/$id.$ext",
                            tipo = "filme",
                            categoryId = obj.optString("category_id", "")
                        ))
                    }
                }

                if (jsonSeries.startsWith("[")) {
                    val arr = JSONArray(jsonSeries)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optString("series_id", "")
                        filmesSeriesParaSalvar.add(FilmeEntity(
                            id = id,
                            nome = obj.optString("name", "Sem Nome"),
                            urlImagem = obj.optString("cover", ""),
                            streamUrl = "$urlGlobal/player_api.php?username=$userGlobal&password=$passGlobal&action=get_series_info&series_id=$id",
                            tipo = "serie",
                            categoryId = obj.optString("category_id", "")
                        ))
                    }
                }

                dao.limparCategorias()
                dao.limparCanais()
                dao.limparFilmesSeries()
                
                if(categoriasParaSalvar.isNotEmpty()) dao.inserirCategorias(categoriasParaSalvar)
                if(canaisParaSalvar.isNotEmpty()) dao.inserirCanais(canaisParaSalvar)
                if(filmesSeriesParaSalvar.isNotEmpty()) dao.inserirFilmesSeries(filmesSeriesParaSalvar)

                val mapLiveCats = mutableMapOf<String, String>()
                val mapVodCats = mutableMapOf<String, String>()
                val mapSeriesCats = mutableMapOf<String, String>()

                val liveCats = mutableListOf<CategoriaItem>()
                for (cat in dao.getCategoriasPorTipo("live")) {
                    liveCats.add(CategoriaItem(cat.id, cat.nome))
                    mapLiveCats[cat.id] = cat.nome
                }
                for (cat in dao.getCategoriasPorTipo("vod")) mapVodCats[cat.id] = cat.nome
                for (cat in dao.getCategoriasPorTipo("series")) mapSeriesCats[cat.id] = cat.nome
                
                mapVodCatsGlobal = mapVodCats
                mapSeriesCatsGlobal = mapSeriesCats

                liveCats.add(0, CategoriaItem("FAV", "Canais Favoritos"))
                liveCatsGlobal = liveCats
                
                val canaisFiltrados = mutableListOf<CanalItem>()
                for (canal in canaisParaSalvar) {
                    val nomeCategoria = mapLiveCats[canal.categoryId] ?: continue 
                    
                    val shouldHide = ContentFilterUtils.isContentBlocked(
                        nomeItem = canal.nome,
                        nomeCategoria = nomeCategoria,
                        isParentalActive = isParentalActive,
                        filterSD = filterSD, filterHD = filterHD, filterFHD = filterFHD, filterH265 = filterH265, filter4K = filter4K,
                        forcedHide4K = forcedHide4K, forcedHideFHD = forcedHideFHD
                    )
                    
                    if (shouldHide) continue
                    canaisFiltrados.add(CanalItem(canal.id, canal.nome, canal.urlImagem, canal.categoryId, canal.streamUrl))
                }
                listCanaisGlobais = canaisFiltrados

                val fFiltrados = mutableListOf<FilmeItem>()
                val sFiltradas = mutableListOf<FilmeItem>()
                
                for (media in filmesSeriesParaSalvar) {
                    val mapCorreto = if (media.tipo == "filme") mapVodCats else mapSeriesCats
                    val nomeCategoria = mapCorreto[media.categoryId] ?: continue
                    
                    val shouldHide = ContentFilterUtils.isContentBlocked(
                        nomeItem = media.nome,
                        nomeCategoria = nomeCategoria,
                        isParentalActive = isParentalActive,
                        filterSD = false, filterHD = false, filterFHD = false, filterH265 = false, filter4K = false
                    )
                    
                    if (shouldHide) continue
                    
                    if (media.tipo == "filme") fFiltrados.add(FilmeItem(media.id, media.nome, media.urlImagem, media.streamUrl, media.tipo, media.categoryId, 0))
                    else sFiltradas.add(FilmeItem(media.id, media.nome, media.urlImagem, media.streamUrl, media.tipo, media.categoryId, 0))
                }
                
                listFilmesGlobais = fFiltrados
                listSeriesGlobais = sFiltradas

                withContext(Dispatchers.Main) {
                    findViewById<RelativeLayout>(R.id.loadingOverlay).visibility = View.GONE
                    
                    renderizarContinuarAssistindo()
                    renderizarFavoritos()
                    
                    val recyclerUltimos = findViewById<HorizontalGridView>(R.id.recyclerUltimos)
                    val recyclerTopFilmes = findViewById<HorizontalGridView>(R.id.recyclerTopFilmes)
                    val recyclerTopSeries = findViewById<HorizontalGridView>(R.id.recyclerTopSeries)
                    val recyclerSeriesAlta = findViewById<HorizontalGridView>(R.id.recyclerSeriesAlta)

                    recyclerUltimos.adapter = CardAdapter(listFilmesGlobais.reversed().take(30)) { abrirDetalhes(it) }
                    recyclerSeriesAlta.adapter = CardAdapter(listSeriesGlobais.reversed().take(30)) { abrirDetalhes(it) }
                    recyclerTopFilmes.adapter = Top10Adapter(listFilmesGlobais.take(10)) { abrirDetalhes(it) }
                    recyclerTopSeries.adapter = Top10Adapter(listSeriesGlobais.take(10)) { abrirDetalhes(it) }

                    if (listFilmesGlobais.isNotEmpty()) {
                        carregarDestaqueAleatorio()
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

    private fun carregarDestaqueAleatorio() {
        if (listFilmesGlobais.isEmpty()) return
        val filmeAleatorio = listFilmesGlobais.random()
        
        val mapTodasCats = mapVodCatsGlobal.toMutableMap()
        mapTodasCats.putAll(mapSeriesCatsGlobal)
        
        atualizarBanner(filmeAleatorio, mapTodasCats)
    }

    private fun atualizarBanner(filme: FilmeItem, mapCategorias: Map<String, String>) {
        val tvTitle = findViewById<TextView>(R.id.heroTitle)
        val tvBadge = findViewById<TextView>(R.id.heroBadge)
        val tvDesc = findViewById<TextView>(R.id.heroDesc)
        val containerTextos = findViewById<LinearLayout>(R.id.textosDestaqueContainer)
        
        containerTextos.animate().alpha(0f).setDuration(300).withEndAction {
            tvTitle.text = filme.nome
            val nomeDaPasta = mapCategorias[filme.categoryId] ?: "DESTAQUE"
            tvBadge.text = "PASTA: ${nomeDaPasta.uppercase()}"
            tvDesc.text = "Buscando informações..."

            activityScope.launch {
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
                    withContext(Dispatchers.Main) { 
                        tvDesc.text = plot 
                        containerTextos.animate().alpha(1f).setDuration(400).start()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { 
                        tvDesc.text = "Sinopse não disponível." 
                        containerTextos.animate().alpha(1f).setDuration(400).start()
                    }
                }
            }

            btnAssistirDestaque.visibility = View.VISIBLE
            btnAssistirDestaque.setOnClickListener { abrirDetalhes(filme) }
        }.start()
        
        Glide.with(this)
            .load(filme.urlImagem)
            .format(com.bumptech.glide.load.DecodeFormat.PREFER_ARGB_8888)
            .transition(DrawableTransitionOptions.withCrossFade(800))
            .into(heroImage)
    }

    private fun renderizarContinuarAssistindo() {
        val listContinuar = mutableListOf<FilmeContinuar>()
        
        val processarItem = { item: FilmeItem ->
            val progData = historicoMapGlobal[item.id] as? Map<String, Any>
            if (progData != null) {
                val pos = progData["posicao"]?.toString()?.toLongOrNull() ?: 0L
                val dur = progData["duracao"]?.toString()?.toLongOrNull() ?: 0L
                val ts = progData["timestamp"]?.toString()?.toLongOrNull() ?: 0L
                
                if (dur > 0L && pos > 5000L) {
                    var perc = ((pos.toDouble() / dur.toDouble()) * 100).toInt()
                    if (perc <= 0) perc = 1 
                    if (perc > 99) perc = 99
                    
                    val faltamMs = dur - pos
                    val faltamMin = (faltamMs / 60000).toInt()
                    
                    listContinuar.add(FilmeContinuar(item, perc, ts, faltamMin))
                }
            }
        }
        
        listFilmesGlobais.forEach(processarItem)
        listSeriesGlobais.forEach(processarItem)
        
        val recyclerContinuar = findViewById<HorizontalGridView>(R.id.recyclerContinuar)
        val tvContinuarTitulo = findViewById<TextView>(R.id.tvContinuarTitulo)
        
        if (listContinuar.isNotEmpty()) {
            val listOrdenada = listContinuar.sortedByDescending { it.timestamp }.toMutableList()
            
            tvContinuarTitulo.visibility = View.VISIBLE
            recyclerContinuar.visibility = View.VISIBLE
            
            val filmesRestantes = listOrdenada.map { 
                FilmeItem(it.filme.id, it.filme.nome, it.filme.urlImagem, it.filme.streamUrl, it.filme.tipo, it.filme.categoryId, it.progressoPerc) 
            }
            recyclerContinuar.adapter = CardAdapter(filmesRestantes) { abrirDetalhes(it) }
            
        } else {
            tvContinuarTitulo.visibility = View.GONE
            recyclerContinuar.visibility = View.GONE
        }
    }

    private fun renderizarFavoritos() {
        val recyclerFavoritos = findViewById<HorizontalGridView>(R.id.recyclerFavoritos)
        val tvFavoritosTitulo = findViewById<TextView>(R.id.tvFavoritosTitulo)
        
        val listFavoritos = listaIdsFavoritosGlobais.mapNotNull { favId -> listCanaisGlobais.find { it.id == favId } }

        if (listFavoritos.isNotEmpty()) {
            tvFavoritosTitulo.visibility = View.VISIBLE
            recyclerFavoritos.visibility = View.VISIBLE
            recyclerFavoritos.adapter = CanalAdapter(listFavoritos, listaIdsFavoritosGlobais, { canalClicado ->
                DataHolder.todasCategorias = liveCatsGlobal
                DataHolder.todosCanais = listCanaisGlobais
                DataHolder.favoritosIds = listaIdsFavoritosGlobais
                DataHolder.categoriaAtualId = "FAV"
                
                val indiceCorretoProPlayer = listFavoritos.indexOf(canalClicado)
                
                startActivity(Intent(this@HomeActivity, PlayerTvActivity::class.java).apply { putExtra("INDICE_CANAL", if (indiceCorretoProPlayer != -1) indiceCorretoProPlayer else 0) })
            }, { })
        } else {
            tvFavoritosTitulo.visibility = View.GONE
            recyclerFavoritos.visibility = View.GONE
        }
    }

    private fun carregarAplicativosDaTV() {
        val pm = packageManager
        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
        val hiddenApps = prefs.getStringSet("HIDDEN_APPS", emptySet()) ?: emptySet()
        val installedApps = mutableMapOf<String, AppItem>()

        try {
            val intentLeanback = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) }
            val leanbackApps = pm.queryIntentActivities(intentLeanback, 0)
            for (resolveInfo in leanbackApps) {
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg == applicationContext.packageName || hiddenApps.contains(pkg)) continue
                val nome = resolveInfo.loadLabel(pm).toString()
                val icone = resolveInfo.loadIcon(pm)
                installedApps[pkg] = AppItem(nome, icone, pkg)
            }

            val intentLauncher = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val launcherApps = pm.queryIntentActivities(intentLauncher, 0)
            for (resolveInfo in launcherApps) {
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg == applicationContext.packageName || installedApps.containsKey(pkg) || hiddenApps.contains(pkg)) continue
                val nome = resolveInfo.loadLabel(pm).toString()
                val icone = resolveInfo.loadIcon(pm)
                installedApps[pkg] = AppItem(nome, icone, pkg)
            }
        } catch (e: Exception) {}

        val listaFinal = installedApps.values.toMutableList()
        listaFinal.sortBy { it.nome }

        val settingsPkg = "android.settings.SETTINGS"
        if (!hiddenApps.contains(settingsPkg)) {
            val settingsIcon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_preferences)
            if (settingsIcon != null) listaFinal.add(0, AppItem("Configurações", settingsIcon, settingsPkg))
        }

        val recyclerApps = findViewById<HorizontalGridView>(R.id.recyclerApps)
        recyclerApps.adapter = AppAdapter(listaFinal) { app ->
            if (app.pacote == "android.settings.SETTINGS") startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            else {
                val launchIntent = pm.getLaunchIntentForPackage(app.pacote)
                if (launchIntent != null) startActivity(launchIntent)
                else Toast.makeText(this, "Não foi possível abrir.", Toast.LENGTH_SHORT).show()
            }
        }
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

    inner class AppAdapter(private val list: List<AppItem>, private val onClick: (AppItem) -> Unit) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {
        private val interpolator = OvershootInterpolator(1.2f)
        inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val img = view.findViewById<ImageView>(2001)
            val txt = view.findViewById<TextView>(2002)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val density = parent.context.resources.displayMetrics.density
            val w = (160 * density).toInt()
            val h = (80 * density).toInt()
            val m = (8 * density).toInt()
            val p = (12 * density).toInt()
            
            val layout = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                layoutParams = ViewGroup.MarginLayoutParams(w, h).apply { setMargins(m, m, m, m) }
                background = ContextCompat.getDrawable(parent.context, R.drawable.bg_app_card)
                isFocusable = true
                isClickable = true
                setPadding(p, p, p, p)
            }
            val img = ImageView(parent.context).apply { 
                id = 2001
                layoutParams = LinearLayout.LayoutParams((32 * density).toInt(), (32 * density).toInt())
                scaleType = ImageView.ScaleType.FIT_CENTER 
            }
            val txt = TextView(parent.context).apply { 
                id = 2002
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (4 * density).toInt() }
                setTextColor(Color.WHITE)
                textSize = 11f
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END 
            }
            layout.addView(img)
            layout.addView(txt)
            return AppViewHolder(layout)
        }
        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            val item = list[position]
            holder.txt.text = item.nome
            holder.img.setImageDrawable(item.icone)
            
            holder.itemView.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) { 
                    v.bringToFront()
                    if(!isLowEndMode) v.animate().scaleX(1.10f).scaleY(1.10f).translationZ(15f).setDuration(250).setInterpolator(interpolator).start() 
                    v.setBackgroundResource(R.drawable.bg_card_outline)
                } else { 
                    if(!isLowEndMode) v.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(250).setInterpolator(interpolator).start()
                    v.setBackgroundResource(R.drawable.bg_app_card)
                }
            }
            holder.itemView.setOnClickListener { onClick(item) }
        }
        override fun getItemCount(): Int = list.size
    }
}
