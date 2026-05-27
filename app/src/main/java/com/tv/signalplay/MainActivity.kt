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
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ItemCatalogo(
    val id: Int,
    val titulo: String,
    val capa: String?,
    val tipo: String, 
    val isTop10: Boolean = false,
    val rankIndex: Int = 0,
    val tempoAtual: Long = 0L,
    val duracaoTotal: Long = 0L
)

class MainActivity : FragmentActivity() {

    private var masterFilmes: List<XtreamVod> = listOf()
    private var masterSeries: List<XtreamSerie> = listOf()
    private var masterCanais: List<XtreamLive> = listOf()
    
    private var xtUser = ""
    private var xtPass = ""
    private var urlServ = ""
    private var firebaseUser = ""
    private var isParentalOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setProjectId("signalplay-tv")
                    .setApplicationId("1:51000338902:web:61d77a44dd62c0353a1c77")
                    .setApiKey("AIzaSyBSYJYEFLlDwBYsQC0I76n9NfAph2oWuLI")
                    .build()
                FirebaseApp.initializeApp(this, options)
            }
        } catch (e: Exception) {}

        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
        firebaseUser = intent.getStringExtra("FIREBASE_USER") ?: prefs.getString("FIREBASE_USER", "Cliente") ?: "Cliente"
        xtUser = intent.getStringExtra("XTREAM_USER") ?: prefs.getString("XTREAM_USER", "") ?: ""
        xtPass = intent.getStringExtra("XTREAM_PASS") ?: prefs.getString("XTREAM_PASS", "") ?: ""
        urlServ = intent.getStringExtra("URL") ?: prefs.getString("URL", "") ?: ""
        
        isParentalOn = prefs.getBoolean("parental_control", false)

        val navPerfil = findViewById<TextView>(R.id.navPerfil)
        navPerfil.text = extrairIniciais(firebaseUser)
        
        configurarFocoMenusTV()
        configurarModalPerfil()

        findViewById<ImageView>(R.id.navSearch).setOnClickListener {
            val intentBusca = Intent(this, BuscaActivity::class.java)
            intentBusca.putExtra("XTREAM_USER", xtUser); intentBusca.putExtra("XTREAM_PASS", xtPass); intentBusca.putExtra("URL", urlServ)
            startActivity(intentBusca)
        }

        findViewById<TextView>(R.id.navCanais).setOnClickListener {
            val intentCanais = Intent(this, CanaisActivity::class.java)
            intentCanais.putExtra("XTREAM_USER", xtUser); intentCanais.putExtra("XTREAM_PASS", xtPass); intentCanais.putExtra("URL", urlServ)
            startActivity(intentCanais)
        }

        findViewById<TextView>(R.id.navInicio).setOnClickListener { resetarCoresMenu(); findViewById<TextView>(R.id.navInicio).setTextColor(Color.WHITE); renderizarAbaHome() }
        findViewById<TextView>(R.id.navFilmes).setOnClickListener { resetarCoresMenu(); findViewById<TextView>(R.id.navFilmes).setTextColor(Color.WHITE); renderizarAbaFilmes() }
        findViewById<TextView>(R.id.navSeries).setOnClickListener { resetarCoresMenu(); findViewById<TextView>(R.id.navSeries).setTextColor(Color.WHITE); renderizarAbaSeries() }

        if (urlServ.isNotEmpty()) { 
            sincronizarFavoritosDoBanco()
            baixarCatalogoCompleto() 
        }
    }

    override fun onResume() { 
        super.onResume()
        if (masterFilmes.isNotEmpty()) renderizarAbaHome() 
    }

    private fun configurarModalPerfil() {
        val overlay = findViewById<RelativeLayout>(R.id.modalPerfilOverlay)
        val txtNome = findViewById<TextView>(R.id.txtModalNome)
        val avatarPrincipal = findViewById<TextView>(R.id.txtModalAvatar)
        val avatarMenu = findViewById<TextView>(R.id.navPerfil)
        
        txtNome.text = "Olá, $firebaseUser!"
        avatarPrincipal.text = extrairIniciais(firebaseUser)
        
        val coresAvatares = listOf(R.id.avatarColor1 to R.drawable.bg_perfil_amarelo, R.id.avatarColor2 to Color.parseColor("#ff4757"), R.id.avatarColor3 to Color.parseColor("#2ed573"), R.id.avatarColor4 to Color.parseColor("#1e90ff"))
        for (par in coresAvatares) {
            val view = findViewById<TextView>(par.first)
            view.setOnFocusChangeListener { v, focus -> if (focus) v.animate().scaleX(1.2f).scaleY(1.2f).start() else v.animate().scaleX(1.0f).scaleY(1.0f).start() }
            view.setOnClickListener {
                if (par.first == R.id.avatarColor1) { avatarPrincipal.setBackgroundResource(par.second as Int); avatarMenu.setBackgroundResource(par.second as Int) } 
                else { avatarPrincipal.setBackgroundColor(par.second as Int); avatarMenu.setBackgroundColor(par.second as Int) }
            }
        }

        val listenerFocoConfig = View.OnFocusChangeListener { v, focus -> if (focus) { v.setBackgroundResource(R.drawable.bg_config_item); v.animate().scaleX(1.03f).start() } else { v.setBackgroundColor(Color.TRANSPARENT); v.animate().scaleX(1.0f).start() } }
        val btnParental = findViewById<LinearLayout>(R.id.btnModalParental)
        val txtStatusParental = findViewById<TextView>(R.id.txtStatusParental)
        
        txtStatusParental.text = if (isParentalOn) "LIGADO" else "DESLIGADO"
        txtStatusParental.setTextColor(if (isParentalOn) Color.parseColor("#2ed573") else Color.parseColor("#ff4757"))

        btnParental.setOnFocusChangeListener(listenerFocoConfig)
        btnParental.setOnClickListener {
            isParentalOn = !isParentalOn
            getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE).edit().putBoolean("parental_control", isParentalOn).apply()
            txtStatusParental.text = if (isParentalOn) "LIGADO" else "DESLIGADO"
            txtStatusParental.setTextColor(if (isParentalOn) Color.parseColor("#2ed573") else Color.parseColor("#ff4757"))
            Toast.makeText(this, "Controle Parental Atualizado", Toast.LENGTH_SHORT).show()
            baixarCatalogoCompleto() 
        }

        findViewById<LinearLayout>(R.id.btnModalLimparHist).apply { setOnFocusChangeListener(listenerFocoConfig); setOnClickListener { getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE).edit().putString("iptv_continuar_vod", "[]").apply(); Toast.makeText(this@MainActivity, "Histórico Removido!", Toast.LENGTH_SHORT).show(); overlay.visibility = View.GONE; renderizarAbaHome() } }
        findViewById<LinearLayout>(R.id.btnModalLimparFavs).apply { setOnFocusChangeListener(listenerFocoConfig); setOnClickListener { getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE).edit().putString("favoritos_tv", "[]").apply(); Toast.makeText(this@MainActivity, "Favoritos Removidos!", Toast.LENGTH_SHORT).show(); overlay.visibility = View.GONE; renderizarAbaHome() } }
        findViewById<Button>(R.id.btnModalLogout).setOnClickListener { getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE).edit().clear().apply(); startActivity(Intent(this, LoginActivity::class.java)); finish() }
        navPerfil.setOnClickListener { overlay.visibility = View.VISIBLE; findViewById<Button>(R.id.btnModalFechar).requestFocus() }
        findViewById<Button>(R.id.btnModalFechar).setOnClickListener { overlay.visibility = View.GONE; navPerfil.requestFocus() }
    }

    private fun sincronizarFavoritosDoBanco() {
        if(firebaseUser.isEmpty()) return
        try { FirebaseFirestore.getInstance().collection("usuarios").whereEqualTo("usuario", firebaseUser).get().addOnSuccessListener { snaps -> if(!snaps.isEmpty) { val favs = snaps.documents[0].get("favoritos") as? List<String> ?: emptyList(); getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE).edit().putString("favoritos_tv", Gson().toJson(favs)).apply() } } } catch (e: Exception) {}
    }

    private fun isAdult(cat: String?): Boolean {
        if (!isParentalOn) return false
        val c = cat?.lowercase() ?: ""
        return listOf("adulto", "adult", "18+", "xxx", "porn", "sensual", "hachutv").any { c.contains(it) }
    }

    private fun baixarCatalogoCompleto() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val api = XtreamClient.create(urlServ)
                val respVodCat = api.getVodCategories(xtUser, xtPass)
                val respSeriesCat = api.getSeriesCategories(xtUser, xtPass)
                val typeCat = object : TypeToken<List<XtreamCategory>>() {}.type
                val mapVod = mutableMapOf<String, String>()
                val mapSeries = mutableMapOf<String, String>()
                
                if (respVodCat.isJsonArray) Gson().fromJson<List<XtreamCategory>>(respVodCat, typeCat).forEach { mapVod[it.category_id] = it.category_name }
                if (respSeriesCat.isJsonArray) Gson().fromJson<List<XtreamCategory>>(respSeriesCat, typeCat).forEach { mapSeries[it.category_id] = it.category_name }

                val respVod = api.getVodStreams(xtUser, xtPass)
                val respSeries = api.getSeriesStreams(xtUser, xtPass)
                val respCanais = api.getLiveStreams(xtUser, xtPass)

                withContext(Dispatchers.Main) {
                    if (respVod.isJsonArray) { val brutos = Gson().fromJson<List<XtreamVod>>(respVod, object : TypeToken<List<XtreamVod>>() {}.type); brutos.forEach { it.category_name = mapVod[it.category_id ?: ""] ?: "Outros" }; masterFilmes = brutos.filter { !isAdult(it.category_name) } }
                    if (respSeries.isJsonArray) { val brutos = Gson().fromJson<List<XtreamSerie>>(respSeries, object : TypeToken<List<XtreamSerie>>() {}.type); brutos.forEach { it.category_name = mapSeries[it.category_id ?: ""] ?: "Outros" }; masterSeries = brutos.filter { !isAdult(it.category_name) } }
                    if (respCanais.isJsonArray) { masterCanais = Gson().fromJson(respCanais, object : TypeToken<List<XtreamLive>>() {}.type) }
                    
                    renderizarAbaHome()
                }
            } catch (e: Exception) { }
        }
    }

    private fun renderizarAbaHome() {
        val container = findViewById<LinearLayout>(R.id.containerTrilhos)
        container.removeAllViews()
        
        if (masterFilmes.isNotEmpty()) {
            val filmeAleatorio = masterFilmes.random()
            setHeroBanner(filmeAleatorio.name, filmeAleatorio.stream_icon, filmeAleatorio.stream_id, false)
        }

        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)

        val contJson = prefs.getString("iptv_continuar_vod", "[]")
        val contList: List<Map<String, Any>> = try { Gson().fromJson(contJson, object : TypeToken<List<Map<String, Any>>>(){}.type) ?: emptyList() } catch (e: Exception) { emptyList() }
        val continuarItens = mutableListOf<ItemCatalogo>()
        for (item in contList) {
            val id = (item["id"] as? Number)?.toInt() ?: 0; val tipo = item["tipo"] as? String ?: ""; val tempo = (item["tempo"] as? Number)?.toLong() ?: 0L; val duracao = (item["duracao"] as? Number)?.toLong() ?: 0L
            if (tipo == "vod") { masterFilmes.find { it.stream_id == id }?.let { continuarItens.add(ItemCatalogo(it.stream_id, it.name, it.stream_icon, "vod", tempoAtual = tempo, duracaoTotal = duracao)) } } 
            else if (tipo == "series") { masterSeries.find { it.series_id == id }?.let { continuarItens.add(ItemCatalogo(it.series_id, it.name, it.cover, "series", tempoAtual = tempo, duracaoTotal = duracao)) } }
        }
        if (continuarItens.isNotEmpty()) injetarTrilho(container, "Continuar Assistindo", continuarItens)

        val favsListJson = prefs.getString("favoritos_tv", "[]")
        val idsFavoritos: List<String> = try { Gson().fromJson(favsListJson, object : TypeToken<List<String>>(){}.type) ?: emptyList() } catch (e: Exception) { emptyList() }
        val canaisFavoritos = masterCanais.filter { idsFavoritos.contains(it.stream_id.toString()) }.map { ItemCatalogo(it.stream_id, it.name, it.stream_icon, "live") }
        if(canaisFavoritos.isNotEmpty()) injetarTrilho(container, "Canais Favoritos", canaisFavoritos)

        val top10Filmes = masterFilmes.sortedByDescending { it.rating ?: 0.0 }.take(10).mapIndexed { idx, it -> ItemCatalogo(it.stream_id, it.name, it.stream_icon, "vod", isTop10 = true, rankIndex = idx + 1) }
        if(top10Filmes.isNotEmpty()) injetarTrilho(container, "Top 10 Filmes", top10Filmes)
        
        val ultimosFilmes = masterFilmes.sortedByDescending { it.stream_id }.take(30).map { ItemCatalogo(it.stream_id, it.name, it.stream_icon, "vod") }
        if(ultimosFilmes.isNotEmpty()) injetarTrilho(container, "Últimos Filmes Adicionados", ultimosFilmes)
        
        val top10Series = masterSeries.sortedByDescending { it.rating ?: 0.0 }.take(10).mapIndexed { idx, it -> ItemCatalogo(it.series_id, it.name, it.cover, "series", isTop10 = true, rankIndex = idx + 1) }
        if(top10Series.isNotEmpty()) injetarTrilho(container, "Top 10 Séries", top10Series)
        
        val seriesAlta = masterSeries.sortedByDescending { it.series_id }.take(30).map { ItemCatalogo(it.series_id, it.name, it.cover, "series") }
        if(seriesAlta.isNotEmpty()) injetarTrilho(container, "Séries em Alta", seriesAlta)
    }

    private fun renderizarAbaFilmes() { 
        val container = findViewById<LinearLayout>(R.id.containerTrilhos); container.removeAllViews()
        if (masterFilmes.isNotEmpty()) setHeroBanner(masterFilmes[0].name, masterFilmes[0].stream_icon, masterFilmes[0].stream_id, false)
        val categorias = masterFilmes.groupBy { it.category_name ?: "Outros" }
        for ((catNome, lista) in categorias) { if(lista.isNotEmpty()) injetarTrilho(container, catNome, lista.take(30).map { ItemCatalogo(it.stream_id, it.name, it.stream_icon, "vod") }) } 
    }

    private fun renderizarAbaSeries() { 
        val container = findViewById<LinearLayout>(R.id.containerTrilhos); container.removeAllViews()
        if (masterSeries.isNotEmpty()) setHeroBanner(masterSeries[0].name, masterSeries[0].cover, masterSeries[0].series_id, true)
        val categorias = masterSeries.groupBy { it.category_name ?: "Outros" }
        for ((catNome, lista) in categorias) { if(lista.isNotEmpty()) injetarTrilho(container, catNome, lista.take(30).map { ItemCatalogo(it.series_id, it.name, it.cover, "series") }) } 
    }

    private fun injetarTrilho(container: LinearLayout, titulo: String, itens: List<ItemCatalogo>) {
        val view = LayoutInflater.from(this).inflate(R.layout.trilho_vod_premium, container, false)
        val txtTitulo = view.findViewById<TextView>(R.id.txtTituloTrilho)
        txtTitulo.text = titulo
        if (titulo == "Canais Favoritos") txtTitulo.setTextColor(Color.parseColor("#ffcc00"))
        
        val rv = view.findViewById<RecyclerView>(R.id.rvTrilho)
        rv.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        rv.adapter = CatalogoAdapter(itens)
        
        container.addView(view)
    }

    private fun setHeroBanner(titulo: String, imagem: String?, id: Int, isSeries: Boolean) {
        findViewById<TextView>(R.id.badgeDestaque).visibility = View.VISIBLE
        val btnAss = findViewById<Button>(R.id.btnAssistirDestaque)
        btnAss.visibility = View.VISIBLE
        findViewById<TextView>(R.id.txtTituloDestaque).text = titulo
        findViewById<TextView>(R.id.txtDescDestaque).text = "Disponível no Catálogo"
        
        if(imagem != null) Glide.with(this).load(imagem).diskCacheStrategy(DiskCacheStrategy.ALL).into(findViewById<ImageView>(R.id.imgBackgroundDestaque))
        
        btnAss.setOnClickListener { abrirSinopse(id, isSeries, "vod") }
    }

    private fun abrirSinopse(id: Int, isSeries: Boolean, tipo: String, tituloCanal: String = "") { 
        if (tipo == "live") {
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra("URL", urlServ); intent.putExtra("XTREAM_USER", xtUser); intent.putExtra("XTREAM_PASS", xtPass)
            intent.putExtra("STREAM_ID", id); intent.putExtra("TYPE", "live"); intent.putExtra("TITLE", tituloCanal)
            startActivity(intent)
        } else {
            val intent = Intent(this, DetalhesActivity::class.java)
            intent.putExtra("MEDIA_ID", id); intent.putExtra("IS_SERIES", isSeries)
            intent.putExtra("XTREAM_USER", xtUser); intent.putExtra("XTREAM_PASS", xtPass); intent.putExtra("URL", urlServ)
            startActivity(intent) 
        }
    }

    private fun configurarFocoMenusTV() { 
        val menus = listOf(R.id.navSearch, R.id.navInicio, R.id.navCanais, R.id.navFilmes, R.id.navSeries, R.id.navPerfil)
        for (id in menus) { 
            findViewById<View>(id)?.setOnFocusChangeListener { v, focus -> 
                if (focus) { v.animate().scaleX(1.1f).start(); if(v is TextView && v.id != R.id.navPerfil) v.setTextColor(Color.WHITE) } 
                else { v.animate().scaleX(1.0f).start(); if(v is TextView && v.id != R.id.navPerfil) v.setTextColor(Color.parseColor("#888888")) } 
            } 
        } 
    }

    private fun resetarCoresMenu() { listOf(R.id.navInicio, R.id.navCanais, R.id.navFilmes, R.id.navSeries).forEach { findViewById<TextView>(it).setTextColor(Color.parseColor("#888888")) } }
    private fun extrairIniciais(nome: String): String { if (nome.isBlank()) return "BR"; val p = nome.trim().split(" "); if (p.size >= 2) return (p[0].substring(0, 1) + p[1].substring(0, 1)).uppercase(); return if (nome.length >= 2) nome.substring(0, 2).uppercase() else nome.uppercase() }

    inner class CatalogoAdapter(private val lista: List<ItemCatalogo>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int {
            val item = lista[position]
            return when {
                item.isTop10 -> 1
                item.duracaoTotal > 0 -> 2 
                item.tipo == "live" -> 3 
                else -> 0 
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val layoutId = when (viewType) {
                1 -> R.layout.card_top10_premium
                2 -> R.layout.card_continuar_premium
                3 -> R.layout.card_canal_premium
                else -> R.layout.card_vod_premium
            }
            val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
            view.isFocusable = true
            view.isFocusableInTouchMode = false 
            return HolderGenerico(view)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = lista[position]
            val h = holder as HolderGenerico
            
            h.view.findViewById<TextView>(R.id.txtNomePremium)?.text = item.titulo
            if (item.isTop10) h.view.findViewById<TextView>(R.id.txtRankTop10)?.text = item.rankIndex.toString()

            val img = h.view.findViewById<ImageView>(R.id.imgCapaPremium)
            if (img != null && !item.capa.isNullOrEmpty()) {
                Glide.with(h.view.context)
                    .load(item.capa)
                    .override(250, 350) 
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(img)
            } else {
                img?.setImageDrawable(null)
            }

            if (item.duracaoTotal > 0) {
                h.view.findViewById<ProgressBar>(R.id.progressoFilme)?.apply {
                    max = 100
                    progress = ((item.tempoAtual * 100) / item.duracaoTotal).toInt()
                }
            }

            h.view.setOnClickListener { abrirSinopse(item.id, item.tipo == "series", item.tipo, item.titulo) }
        }

        override fun getItemCount() = lista.size

        inner class HolderGenerico(val view: View) : RecyclerView.ViewHolder(view) {
            init {
                view.setOnFocusChangeListener { v, focus -> 
                    if (focus) { 
                        v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(150).start()
                        v.elevation = 15f 
                    } else { 
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                        v.elevation = 0f 
                    } 
                }
            }
        }
    }
}
