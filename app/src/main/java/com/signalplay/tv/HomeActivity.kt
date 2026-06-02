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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

data class LigaItem(val nome: String, val logo: String, val url: String)
data class AppItem(val nome: String, val icone: Drawable, val pacote: String)

class HomeActivity : Activity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var btnAssistirDestaque: Button

    private var urlGlobal = ""
    private var userGlobal = ""
    private var passGlobal = ""
    private var username = ""

    private var listFilmesGlobais = listOf<FilmeItem>()
    private var listSeriesGlobais = listOf<FilmeItem>()

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
        val tvFavoritosTitulo = findViewById<TextView>(R.id.tvFavoritosTitulo)
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
            LigaItem("Camp. Argentino", "https://api.sofascore.app/api/v1/unique-tournament/155/image/dark", "https://m.sofascore.com/pt/torneio/futebol/argentina/liga-profesional/155"),
            LigaItem("Camp. Carioca", "https://api.sofascore.app/api/v1/unique-tournament/376/image/dark", "https://m.sofascore.com/pt/torneio/futebol/brazil/carioca/376"),
            LigaItem("Camp. Paulista", "https://api.sofascore.app/api/v1/unique-tournament/374/image/dark", "https://m.sofascore.com/pt/torneio/futebol/brazil/paulista-serie-a1/374"),
            LigaItem("Camp. Saudita", "https://api.sofascore.app/api/v1/unique-tournament/2939/image/dark", "https://m.sofascore.com/pt/torneio/futebol/saudi-arabia/saudi-pro-league/2939"),
            LigaItem("Champions League", "https://api.sofascore.app/api/v1/unique-tournament/7/image/dark", "https://m.sofascore.com/pt/torneio/futebol/europe/uefa-champions-league/7"),
            LigaItem("Copa América", "https://api.sofascore.app/api/v1/unique-tournament/133/image/dark", "https://m.sofascore.com/pt/torneio/futebol/south-america/copa-america/133"),
            LigaItem("Copa da Inglaterra", "https://api.sofascore.app/api/v1/unique-tournament/19/image/dark", "https://m.sofascore.com/pt/torneio/futebol/england/fa-cup/19"),
            LigaItem("Copa do Brasil", "https://api.sofascore.app/api/v1/unique-tournament/373/image/dark", "https://m.sofascore.com/pt/torneio/futebol/brazil/copa-do-brasil/373"),
            LigaItem("Copa do Mundo", "https://api.sofascore.app/api/v1/unique-tournament/16/image/dark", "https://m.sofascore.com/pt/torneio/futebol/world/world-cup/16"),
            LigaItem("Copa do Nordeste", "https://api.sofascore.app/api/v1/unique-tournament/2841/image/dark", "https://m.sofascore.com/pt/torneio/futebol/brazil/copa-do-nordeste/2841"),
            LigaItem("Copa do Rei", "https://api.sofascore.app/api/v1/unique-tournament/116/image/dark", "https://m.sofascore.com/pt/torneio/futebol/spain/copa-del-rey/116"),
            LigaItem("Eredivisie", "https://api.sofascore.app/api/v1/unique-tournament/37/image/dark", "https://m.sofascore.com/pt/torneio/futebol/netherlands/eredivisie/37"),
            LigaItem("Eurocopa", "https://api.sofascore.app/api/v1/unique-tournament/1/image/dark", "https://m.sofascore.com/pt/torneio/futebol/europe/european-championship/1"),
            LigaItem("Europa League", "https://api.sofascore.app/api/v1/unique-tournament/679/image/dark", "https://m.sofascore.com/pt/torneio/futebol/europe/uefa-europa-league/679"),
            LigaItem("La Liga", "https://api.sofascore.app/api/v1/unique-tournament/8/image/dark", "https://m.sofascore.com/pt/torneio/futebol/spain/laliga/8"),
            LigaItem("Libertadores", "https://api.sofascore.app/api/v1/unique-tournament/384/image/dark", "https://m.sofascore.com/pt/torneio/futebol/south-america/copa-libertadores/384"),
            LigaItem("Ligue 1", "https://api.sofascore.app/api/v1/unique-tournament/34/image/dark", "https://m.sofascore.com/pt/torneio/futebol/france/ligue-1/34"),
            LigaItem("MLS", "https://api.sofascore.app/api/v1/unique-tournament/242/image/dark", "https://m.sofascore.com/pt/torneio/futebol/usa/mls/242"),
            LigaItem("Mundial de Clubes", "https://api.sofascore.app/api/v1/unique-tournament/569/image/dark", "https://m.sofascore.com/pt/torneio/futebol/world/club-world-cup/569"),
            LigaItem("Nations League", "https://api.sofascore.app/api/v1/unique-tournament/10469/image/dark", "https://m.sofascore.com/pt/torneio/futebol/europe/uefa-nations-league/10469"),
            LigaItem("Premier League", "https://api.sofascore.app/api/v1/unique-tournament/17/image/dark", "https://m.sofascore.com/pt/torneio/futebol/england/premier-league/17"),
            LigaItem("Primeira Liga", "https://api.sofascore.app/api/v1/unique-tournament/238/image/dark", "https://m.sofascore.com/pt/torneio/futebol/portugal/liga-portugal/238"),
            LigaItem("Série A (Itália)", "https://api.sofascore.app/api/v1/unique-tournament/23/image/dark", "https://m.sofascore.com/pt/torneio/futebol/italy/serie-a/23"),
            LigaItem("Sul-Americana", "https://api.sofascore.app/api/v1/unique-tournament/383/image/dark", "https://m.sofascore.com/pt/torneio/futebol/south-america/copa-sudamericana/383")
        )

        recyclerEsportes.adapter = LigaAdapter(listaLigas) { liga ->
            val intent = Intent(this@HomeActivity, SportsActivity::class.java)
            intent.putExtra("URL_LIGA", liga.url)
            startActivity(intent)
        }

        carregarAplicativosDaTV()

        db.collection("usuarios").whereEqualTo("usuario", username).get()
            .addOnSuccessListener { snapshot ->
                val listaIdsFavoritos = mutableListOf<String>()
                var historicoMap: Map<String, Any>? = null
                
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    val favs = doc.get("favoritos") as? List<*>
                    favs?.forEach { listaIdsFavoritos.add(it.toString()) }
                    historicoMap = doc.get("historico_vod") as? Map<String, Any>
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

                        fun getProgress(id: String): Int {
                            if (historicoMap != null) {
                                val progressoData = historicoMap!![id] as? Map<String, Any>
                                if (progressoData != null) {
                                    val pos = progressoData["posicao"]?.toString()?.toLongOrNull() ?: 0L
                                    val dur = progressoData["duracao"]?.toString()?.toLongOrNull() ?: 0L
                                    
                                    // Liberado! Assistiu 5 segundos, já vai mostrar a porcentagem no card
                                    if (dur > 0L && pos > 5000L) {
                                        var perc = ((pos.toDouble() / dur.toDouble()) * 100).toInt()
                                        if (perc == 0) perc = 1
                                        if (perc > 99) perc = 99
                                        return perc
                                    }
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
                        
                        listFilmesGlobais = listFilmes
                        listSeriesGlobais = listSeries

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
                            
                            if (listFavoritos.isNotEmpty()) {
                                tvFavoritosTitulo.visibility = View.VISIBLE
                                recyclerFavoritos.visibility = View.VISIBLE
                                recyclerFavoritos.adapter = CanalAdapter(listFavoritos, listaIdsFavoritos, { canalClicado ->
                                    DataHolder.todasCategorias = liveCats
                                    DataHolder.todosCanais = listTodosCanais
                                    DataHolder.favoritosIds = listaIdsFavoritos
                                    DataHolder.categoriaAtualId = "FAV"
                                    startActivity(Intent(this@HomeActivity, PlayerTvActivity::class.java).apply {
                                        putExtra("INDICE_CANAL", listFavoritos.indexOf(canalClicado))
                                    })
                                }, { })
                            } else {
                                tvFavoritosTitulo.visibility = View.GONE
                                recyclerFavoritos.visibility = View.GONE
                            }
                            
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
            .addOnFailureListener {
                Toast.makeText(this, "Erro ao conectar com o banco de dados.", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onResume() {
        super.onResume()
        if (listFilmesGlobais.isNotEmpty() || listSeriesGlobais.isNotEmpty()) {
            atualizarContinuarAssistindoSilenciosamente()
        }
    }

    private fun atualizarContinuarAssistindoSilenciosamente() {
        if (username.isEmpty()) return
        
        db.collection("usuarios").whereEqualTo("usuario", username).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    val historicoMap = doc.get("historico_vod") as? Map<String, Any> ?: return@addOnSuccessListener
                    
                    val listContinuar = mutableListOf<FilmeItem>()
                    
                    val processarItem = { item: FilmeItem ->
                        val progData = historicoMap[item.id] as? Map<String, Any>
                        if (progData != null) {
                            val pos = progData["posicao"]?.toString()?.toLongOrNull() ?: 0L
                            val dur = progData["duracao"]?.toString()?.toLongOrNull() ?: 0L
                            
                            // Sem matemática restritiva! Se tem mais de 5s, mostra na tela.
                            if (dur > 0L && pos > 5000L) {
                                var perc = ((pos.toDouble() / dur.toDouble()) * 100).toInt()
                                if (perc == 0) perc = 1 
                                if (perc > 99) perc = 99
                                listContinuar.add(FilmeItem(item.id, item.nome, item.urlImagem, "", item.tipo, "", perc))
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
                        recyclerContinuar.adapter = CardAdapter(listContinuar) { abrirDetalhes(it) }
                    } else {
                        tvContinuarTitulo.visibility = View.GONE
                        recyclerContinuar.visibility = View.GONE
                    }
                }
            }
    }

    // =================================================================================
    // MÁGICA ATUALIZADA: RADAR DUPLO PARA PEGAR TODOS OS APLICATIVOS (TV + CELULAR)
    // =================================================================================
    private fun carregarAplicativosDaTV() {
        val pm = packageManager
        val installedApps = mutableMapOf<String, AppItem>()

        // 1. Puxa os aplicativos otimizados para Android TV (Ex: Netflix)
        val intentTv = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) }
        pm.queryIntentActivities(intentTv, 0).forEach { info ->
            val pkg = info.activityInfo.packageName
            if (pkg != applicationContext.packageName) {
                installedApps[pkg] = AppItem(info.loadLabel(pm).toString(), info.loadIcon(pm), pkg)
            }
        }

        // 2. Puxa os aplicativos comuns (Ex: Google Chrome, Gerenciador de Arquivos, etc)
        val intentMobile = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        pm.queryIntentActivities(intentMobile, 0).forEach { info ->
            val pkg = info.activityInfo.packageName
            // Se o app já não estiver na lista e não for o nosso próprio aplicativo, adiciona
            if (pkg != applicationContext.packageName && !installedApps.containsKey(pkg)) {
                installedApps[pkg] = AppItem(info.loadLabel(pm).toString(), info.loadIcon(pm), pkg)
            }
        }

        // Transforma o mapa em lista e organiza em ordem alfabética
        val listaFinal = installedApps.values.toMutableList()
        listaFinal.sortBy { it.nome }

        val recyclerApps = findViewById<RecyclerView>(R.id.recyclerApps)
        recyclerApps.adapter = AppAdapter(listaFinal) { app ->
            val launchIntent = pm.getLaunchIntentForPackage(app.pacote)
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                Toast.makeText(this, "Não foi possível abrir o aplicativo.", Toast.LENGTH_SHORT).show()
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
            val img = ImageView(context).apply { id = 1001; layoutParams = LinearLayout.LayoutParams(110, 110) }
            val txt = TextView(context).apply { 
                id = 1002
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 16 }
                setTextColor(Color.WHITE)
                textSize = 12f
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                maxLines = 2
                setLines(2)
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

    inner class AppAdapter(
        private val list: List<AppItem>,
        private val onClick: (AppItem) -> Unit
    ) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

        inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val img = view.findViewById<ImageView>(2001)
            val txt = view.findViewById<TextView>(2002)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val context = parent.context
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                layoutParams = ViewGroup.MarginLayoutParams(240, 240).apply { setMargins(16, 16, 16, 16) }
                background = ContextCompat.getDrawable(context, R.drawable.bg_menu_focus)
                isFocusable = true
                isClickable = true
                setPadding(16, 16, 16, 16)
            }
            val img = ImageView(context).apply { 
                id = 2001
                layoutParams = LinearLayout.LayoutParams(120, 120) 
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val txt = TextView(context).apply { 
                id = 2002
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 12 }
                setTextColor(Color.WHITE)
                textSize = 12f
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                maxLines = 1
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
