package com.signalplay.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.Window
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Button
import android.widget.ImageView
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
        
        // MÁGICA PARA ESCONDER A BARRA DO "SIGNALPLAY TV" DE FORMA FORÇADA
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

        // =================================================================================
        // MÁGICA DO CARROSSEL DE FUTEBOL INJETADO (CÓDIGO LIMPO DO '$')
        // =================================================================================
        val webViewFutebol = findViewById<WebView>(R.id.webViewFutebol)
        webViewFutebol.setBackgroundColor(Color.TRANSPARENT)
        val settings: WebSettings = webViewFutebol.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true

        val htmlFutebol = """
        <!DOCTYPE html>
        <html lang="pt-BR">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <script src="https://cdn.jsdelivr.net/npm/spatial-navigation-js@1.0.4/spatial_navigation.min.js"></script>
            <style>
                :root { --bg-color: transparent; --card-bg: #22222a; --accent-color: #00ff88; --text-color: #ffffff; --text-muted: #888888; }
                body { font-family: sans-serif; background-color: var(--bg-color); color: var(--text-color); margin: 0; padding: 0; overflow-x: hidden; }
                h1 { color: #fff; margin-bottom: 5px; font-size: 20px; text-transform: uppercase; letter-spacing: 1px;}
                .carousel { display: flex; overflow-x: auto; gap: 15px; padding: 10px 0; scrollbar-width: none; }
                .carousel::-webkit-scrollbar { display: none; }
                .league-card { background-color: var(--card-bg); border-radius: 12px; min-width: 130px; padding: 15px; text-align: center; cursor: pointer; border: 2px solid transparent; transition: 0.2s; outline: none; }
                .league-card:focus, .league-card:hover { border-color: var(--accent-color); transform: scale(1.05); background-color: #2a2a35; }
                .league-name { margin: 0; font-size: 13px; font-weight: bold; }
                .content-area { margin-top: 15px; display: none; background-color: rgba(34, 34, 42, 0.8); padding: 20px; border-radius: 12px; border: 1px solid #333; }
                .tabs { display: flex; gap: 10px; margin-bottom: 15px; border-bottom: 1px solid #333; padding-bottom: 10px; }
                .tab-btn { background: transparent; color: var(--text-muted); border: 2px solid transparent; padding: 8px 20px; border-radius: 8px; cursor: pointer; font-size: 14px; outline: none; }
                .tab-btn:focus, .tab-btn:hover { border-color: var(--text-color); color: var(--text-color); }
                .tab-btn.active { background-color: var(--accent-color); color: #000; font-weight: bold; }
                table { width: 100%; border-collapse: collapse; text-align: left; font-size: 14px; }
                th, td { padding: 10px; border-bottom: 1px solid #333; }
                th { color: var(--accent-color); font-weight: bold; text-transform: uppercase; font-size: 12px; }
                .loader { padding: 20px; font-size: 14px; color: var(--accent-color); display: none; }
                .panel { display: none; } .panel.active { display: block; }
            </style>
        </head>
        <body>
            <h1>🏆 Esportes em Destaque</h1>
            <div class="carousel" id="league-carousel"></div>
            <div class="loader" id="loader">Buscando dados na Europa... ⏳</div>
            <div class="content-area" id="content-area">
                <h2 id="league-title" style="margin-top: 0; font-size: 18px;">Campeonato</h2>
                <div class="tabs">
                    <button class="tab-btn sn-focusable active" onclick="switchTab('standings', this)">Classificação</button>
                    <button class="tab-btn sn-focusable" onclick="switchTab('fixtures', this)">Próximos Jogos</button>
                </div>
                <div id="standings" class="panel active">
                    <table>
                        <thead><tr><th>Pos</th><th>Equipe</th><th>Pts</th><th>J</th><th>V</th><th>E</th><th>D</th><th>SG</th></tr></thead>
                        <tbody id="standings-body"></tbody>
                    </table>
                </div>
                <div id="fixtures" class="panel">
                    <table>
                        <thead><tr><th>Data</th><th style="text-align: right;">Mandante</th><th style="text-align: center;">X</th><th>Visitante</th><th>Status</th></tr></thead>
                        <tbody id="fixtures-body"></tbody>
                    </table>
                </div>
            </div>

            <script>
                window.addEventListener('load', function() {
                    SpatialNavigation.init();
                    SpatialNavigation.add({ selector: '.sn-focusable' });
                    SpatialNavigation.makeFocusable();
                    SpatialNavigation.focus();
                });

                // SUA CHAVE INSERIDA AQUI:
                const API_KEY = '16b1f41884eb47bcaa3f299f97ca680c'; 
                
                const BASE_URL = 'https://api.football-data.org/v4/competitions/';
                const leagues = [
                    { id: 'PL', name: 'Premier League', logo: 'https://crests.football-data.org/PL.png' },
                    { id: 'PD', name: 'La Liga', logo: 'https://crests.football-data.org/PD.png' },
                    { id: 'SA', name: 'Serie A', logo: 'https://crests.football-data.org/SA.png' },
                    { id: 'BL1', name: 'Bundesliga', logo: 'https://crests.football-data.org/BL1.png' },
                    { id: 'CL', name: 'Champions League', logo: 'https://crests.football-data.org/CL.png' },
                    { id: 'BSA', name: 'Brasileirão', logo: 'https://crests.football-data.org/BSA.png' }
                ];

                const carousel = document.getElementById('league-carousel');
                leagues.forEach(league => {
                    const card = document.createElement('div');
                    card.className = 'league-card sn-focusable'; 
                    card.innerHTML = '<img src="' + league.logo + '" style="width: 45px; height: 45px; object-fit: contain; margin-bottom: 8px;"><br><span class="league-name">' + league.name + '</span>';
                    card.onclick = () => fetchLeagueData(league.id, league.name);
                    card.addEventListener('keydown', (e) => { if (e.key === 'Enter') fetchLeagueData(league.id, league.name); });
                    carousel.appendChild(card);
                });

                async function fetchLeagueData(leagueId, leagueName) {
                    document.getElementById('content-area').style.display = 'none';
                    document.getElementById('loader').style.display = 'block';
                    document.getElementById('league-title').innerText = leagueName;

                    try {
                        const config = { headers: { 'X-Auth-Token': API_KEY } };
                        const [standingsRes, fixturesRes] = await Promise.all([
                            fetch(BASE_URL + leagueId + '/standings', config),
                            fetch(BASE_URL + leagueId + '/matches?status=SCHEDULED', config)
                        ]);

                        if (!standingsRes.ok) throw new Error("API Falhou");

                        const standingsData = await standingsRes.json();
                        const fixturesData = await fixturesRes.json();

                        renderStandings(standingsData);
                        renderFixtures(fixturesData);

                        document.getElementById('loader').style.display = 'none';
                        document.getElementById('content-area').style.display = 'block';
                        switchTab('standings', document.querySelector('.tab-btn'));
                        SpatialNavigation.makeFocusable(); 
                    } catch (error) {
                        document.getElementById('loader').innerText = '❌ Erro de Conexão ou Limite da API atingido.';
                    }
                }

                function renderStandings(data) {
                    const tbody = document.getElementById('standings-body');
                    tbody.innerHTML = '';
                    if (!data.standings || data.standings.length === 0) return tbody.innerHTML = '<tr><td colspan="8">Sem dados.</td></tr>';
                    data.standings[0].table.forEach(row => {
                        tbody.innerHTML += '<tr><td>' + row.position + 'º</td>' +
                        '<td style="display:flex; align-items:center; gap:8px;"><img src="' + row.team.crest + '" width="20" height="20" style="object-fit:contain;"> ' + row.team.shortName + '</td>' +
                        '<td style="color:#00ff88; font-weight:bold;">' + row.points + '</td><td>' + row.playedGames + '</td><td>' + row.won + '</td><td>' + row.draw + '</td><td>' + row.lost + '</td><td>' + row.goalDifference + '</td></tr>';
                    });
                }

                function renderFixtures(data) {
                    const tbody = document.getElementById('fixtures-body');
                    tbody.innerHTML = '';
                    if (!data.matches || data.matches.length === 0) return tbody.innerHTML = '<tr><td colspan="5">Sem jogos agendados.</td></tr>';
                    data.matches.slice(0, 15).forEach(m => {
                        const d = new Date(m.utcDate).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute:'2-digit' });
                        tbody.innerHTML += '<tr><td>' + d + '</td><td style="text-align:right;">' + m.homeTeam.shortName + ' <img src="' + m.homeTeam.crest + '" width="20" height="20" style="vertical-align:middle; margin-left:5px;"></td>' +
                        '<td style="text-align:center;">X</td><td><img src="' + m.awayTeam.crest + '" width="20" height="20" style="vertical-align:middle; margin-right:5px;"> ' + m.awayTeam.shortName + '</td><td style="color:#00ff88;">Agendado</td></tr>';
                    });
                }

                function switchTab(tabId, btnElement) {
                    document.querySelectorAll('.panel').forEach(p => p.classList.remove('active'));
                    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
                    document.getElementById(tabId).classList.add('active');
                    btnElement.classList.add('active');
                }
            </script>
        </body>
        </html>
        """
        
        webViewFutebol.loadDataWithBaseURL(null, htmlFutebol, "text/html", "UTF-8", null)
        // =================================================================================

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
}
