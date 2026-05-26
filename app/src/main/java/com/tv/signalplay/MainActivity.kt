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
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {

    private var masterFilmes: List<XtreamVod> = listOf()
    private var masterSeries: List<XtreamSerie> = listOf()
    private var masterCanais: List<XtreamLive> = listOf()
    private var xtUser = ""
    private var xtPass = ""
    private var urlServ = ""
    private var firebaseUser = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        firebaseUser = intent.getStringExtra("FIREBASE_USER") ?: "Cliente"
        xtUser = intent.getStringExtra("XTREAM_USER") ?: ""
        xtPass = intent.getStringExtra("XTREAM_PASS") ?: ""
        urlServ = intent.getStringExtra("URL") ?: ""

        findViewById<TextView>(R.id.navPerfil).text = extrairIniciais(firebaseUser)
        configurarFocoMenusTV()

        findViewById<TextView>(R.id.navCanais).setOnClickListener {
            val intentCanais = Intent(this, CanaisActivity::class.java)
            intentCanais.putExtra("XTREAM_USER", xtUser)
            intentCanais.putExtra("XTREAM_PASS", xtPass)
            intentCanais.putExtra("URL", urlServ)
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

    // Puxa os favoritos salvos no Firebase na hora que abre o app!
    private fun sincronizarFavoritosDoBanco() {
        if(firebaseUser.isEmpty()) return
        val db = FirebaseFirestore.getInstance()
        db.collection("usuarios").whereEqualTo("usuario", firebaseUser).get().addOnSuccessListener { snaps ->
            if(!snaps.isEmpty) { // <-- CORRIGIDO AQUI: "snaps" em vez de "snap"
                val favs = snaps.documents[0].get("favoritos") as? List<String> ?: emptyList()
                val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
                prefs.edit().putString("favoritos_tv", Gson().toJson(favs)).apply()
            }
        }
    }

    private fun baixarCatalogoCompleto() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val api = XtreamClient.create(urlServ)
                val respVod = api.getVodStreams(xtUser, xtPass)
                val respSeries = api.getSeriesStreams(xtUser, xtPass)
                val respCanais = api.getLiveStreams(xtUser, xtPass)

                withContext(Dispatchers.Main) {
                    if (respVod.isJsonArray) masterFilmes = Gson().fromJson(respVod, object : TypeToken<List<XtreamVod>>() {}.type)
                    if (respSeries.isJsonArray) masterSeries = Gson().fromJson(respSeries, object : TypeToken<List<XtreamSerie>>() {}.type)
                    if (respCanais.isJsonArray) masterCanais = Gson().fromJson(respCanais, object : TypeToken<List<XtreamLive>>() {}.type)
                    renderizarAbaHome()
                }
            } catch (e: Exception) { }
        }
    }

    // A SEQUÊNCIA EXATA SOLICITADA!
    private fun renderizarAbaHome() {
        val container = findViewById<LinearLayout>(R.id.containerTrilhos)
        container.removeAllViews()

        if (masterFilmes.isNotEmpty()) setHeroBanner(masterFilmes.random().name, masterFilmes.random().stream_icon, masterFilmes.random().stream_id, false)

        // 1. Continuar Assistindo (Para VOD - Filmes e Séries)
        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
        
        // 2. Canais Favoritos (Puxados do Banco)
        val favsListJson = prefs.getString("favoritos_tv", "[]")
        val idsFavoritos: List<String> = Gson().fromJson(favsListJson, object : TypeToken<List<String>>(){}.type) ?: emptyList()
        val canaisFavoritos = masterCanais.filter { idsFavoritos.contains(it.stream_id.toString()) }
        if(canaisFavoritos.isNotEmpty()) renderizarTrilhoCanais(container, "Canais Favoritos", canaisFavoritos)

        // 3. Top 10 Filmes (Com Layout Novo de Números Gigantes)
        val top10Filmes = masterFilmes.sortedByDescending { it.rating ?: 0.0 }.take(10)
        if(top10Filmes.isNotEmpty()) renderizarTrilhoTop10(container, "Top 10 Filmes", top10Filmes, false)

        // 4. Últimos Filmes Adicionados
        val ultimosFilmes = masterFilmes.sortedByDescending { it.stream_id }.take(30)
        if(ultimosFilmes.isNotEmpty()) renderizarTrilho(container, "Últimos Filmes Adicionados", ultimosFilmes)

        // 5. Top 10 Séries (Com Layout Novo de Números Gigantes)
        val top10Series = masterSeries.sortedByDescending { it.rating ?: 0.0 }.take(10)
        if(top10Series.isNotEmpty()) renderizarTrilhoTop10(container, "Top 10 Séries", top10Series, true)

        // 6. Séries em Alta
        val seriesAlta = masterSeries.sortedByDescending { it.series_id }.take(30)
        if(seriesAlta.isNotEmpty()) renderizarTrilhoSeries(container, "Séries em Alta", seriesAlta)
    }

    // Função Exclusiva para o Top 10 estilo Netflix
    private fun renderizarTrilhoTop10(container: LinearLayout, titulo: String, lista: List<Any>, isSeries: Boolean) {
        val view = LayoutInflater.from(this).inflate(R.layout.trilho_vod_premium, container, false)
        view.findViewById<TextView>(R.id.txtTituloTrilho).text = titulo
        val linear = view.findViewById<LinearLayout>(R.id.linearInternoTrilho)
        
        for ((index, item) in lista.withIndex()) {
            val card = LayoutInflater.from(this).inflate(R.layout.card_top10_premium, linear, false)
            card.findViewById<TextView>(R.id.txtRankTop10).text = (index + 1).toString()
            
            val capaUrl = if(isSeries) (item as XtreamSerie).cover else (item as XtreamVod).stream_icon
            val itemId = if(isSeries) (item as XtreamSerie).series_id else (item as XtreamVod).stream_id
            
            if (capaUrl != null) Glide.with(this).load(capaUrl).into(card.findViewById(R.id.imgCapaPremium))
            
            configurarZoomCard(card)
            card.setOnClickListener { abrirSinopse(itemId, isSeries) }
            linear.addView(card)
        }
        container.addView(view)
    }

    private fun renderizarAbaFilmes() {
        val container = findViewById<LinearLayout>(R.id.containerTrilhos)
        container.removeAllViews()
        if (masterFilmes.isNotEmpty()) setHeroBanner(masterFilmes[0].name, masterFilmes[0].stream_icon, masterFilmes[0].stream_id, false)
        val categorias = masterFilmes.groupBy { it.category_name ?: "Outros" }
        for ((catNome, lista) in categorias) { if(lista.isNotEmpty()) renderizarTrilho(container, catNome, lista.take(30)) }
    }

    private fun renderizarAbaSeries() {
        val container = findViewById<LinearLayout>(R.id.containerTrilhos)
        container.removeAllViews()
        if (masterSeries.isNotEmpty()) setHeroBanner(masterSeries[0].name, masterSeries[0].cover, masterSeries[0].series_id, true)
        val categorias = masterSeries.groupBy { it.category_name ?: "Outros" }
        for ((catNome, lista) in categorias) { if(lista.isNotEmpty()) renderizarTrilhoSeries(container, catNome, lista.take(30)) }
    }

    private fun setHeroBanner(titulo: String, imagem: String?, id: Int, isSeries: Boolean) {
        findViewById<TextView>(R.id.badgeDestaque).visibility = View.VISIBLE
        val btnAss = findViewById<Button>(R.id.btnAssistirDestaque)
        btnAss.visibility = View.VISIBLE
        findViewById<TextView>(R.id.txtTituloDestaque).text = titulo
        findViewById<TextView>(R.id.txtDescDestaque).text = "Disponível no Catálogo"
        if(imagem != null) Glide.with(this).load(imagem).into(findViewById<ImageView>(R.id.imgBackgroundDestaque))
        btnAss.setOnClickListener { abrirSinopse(id, isSeries) }
    }

    private fun abrirSinopse(id: Int, isSeries: Boolean) {
        val intent = Intent(this, DetalhesActivity::class.java)
        intent.putExtra("MEDIA_ID", id)
        intent.putExtra("IS_SERIES", isSeries)
        intent.putExtra("XTREAM_USER", xtUser)
        intent.putExtra("XTREAM_PASS", xtPass)
        intent.putExtra("URL", urlServ)
        startActivity(intent)
    }

    private fun renderizarTrilho(container: LinearLayout, titulo: String, lista: List<XtreamVod>) {
        val view = LayoutInflater.from(this).inflate(R.layout.trilho_vod_premium, container, false)
        view.findViewById<TextView>(R.id.txtTituloTrilho).text = titulo
        val linear = view.findViewById<LinearLayout>(R.id.linearInternoTrilho)
        for (filme in lista) {
            val card = LayoutInflater.from(this).inflate(R.layout.card_vod_premium, linear, false)
            card.findViewById<TextView>(R.id.txtNomePremium).text = filme.name
            if (filme.stream_icon != null) Glide.with(this).load(filme.stream_icon).into(card.findViewById(R.id.imgCapaPremium))
            configurarZoomCard(card)
            card.setOnClickListener { abrirSinopse(filme.stream_id, false) }
            linear.addView(card)
        }
        container.addView(view)
    }

    private fun renderizarTrilhoSeries(container: LinearLayout, titulo: String, lista: List<XtreamSerie>) {
        val view = LayoutInflater.from(this).inflate(R.layout.trilho_vod_premium, container, false)
        view.findViewById<TextView>(R.id.txtTituloTrilho).text = titulo
        val linear = view.findViewById<LinearLayout>(R.id.linearInternoTrilho)
        for (serie in lista) {
            val card = LayoutInflater.from(this).inflate(R.layout.card_vod_premium, linear, false)
            card.findViewById<TextView>(R.id.txtNomePremium).text = serie.name
            if (serie.cover != null) Glide.with(this).load(serie.cover).into(card.findViewById(R.id.imgCapaPremium))
            configurarZoomCard(card)
            card.setOnClickListener { abrirSinopse(serie.series_id, true) }
            linear.addView(card)
        }
        container.addView(view)
    }

    private fun renderizarTrilhoCanais(container: LinearLayout, titulo: String, lista: List<XtreamLive>) {
        val view = LayoutInflater.from(this).inflate(R.layout.trilho_vod_premium, container, false)
        view.findViewById<TextView>(R.id.txtTituloTrilho).text = titulo
        view.findViewById<TextView>(R.id.txtTituloTrilho).setTextColor(Color.parseColor("#ffcc00")) // Destaca amarelo
        val linear = view.findViewById<LinearLayout>(R.id.linearInternoTrilho)
        for (canal in lista) {
            val card = LayoutInflater.from(this).inflate(R.layout.card_canal_premium, linear, false)
            card.findViewById<TextView>(R.id.txtNomePremium).text = canal.name
            if (canal.stream_icon != null) Glide.with(this).load(canal.stream_icon).into(card.findViewById(R.id.imgCapaPremium))
            configurarZoomCard(card)
            card.setOnClickListener { 
                val intent = Intent(this, PlayerActivity::class.java)
                intent.putExtra("URL", urlServ)
                intent.putExtra("XTREAM_USER", xtUser)
                intent.putExtra("XTREAM_PASS", xtPass)
                intent.putExtra("STREAM_ID", canal.stream_id)
                intent.putExtra("TYPE", "live")
                intent.putExtra("TITLE", canal.name)
                startActivity(intent)
            }
            linear.addView(card)
        }
        container.addView(view)
    }

    private fun configurarZoomCard(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
            else v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
        }
    }

    private fun configurarFocoMenusTV() {
        val menus = listOf(R.id.navInicio, R.id.navCanais, R.id.navFilmes, R.id.navSeries)
        for (id in menus) {
            val view = findViewById<TextView>(id)
            view?.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) (v as TextView).setTextColor(Color.WHITE)
                else (v as TextView).setTextColor(Color.parseColor("#888888"))
            }
        }
    }

    private fun resetarCoresMenu() {
        listOf(R.id.navInicio, R.id.navCanais, R.id.navFilmes, R.id.navSeries).forEach { findViewById<TextView>(it).setTextColor(Color.parseColor("#888888")) }
    }

    private fun extrairIniciais(nome: String): String {
        if (nome.isBlank()) return "BR"
        val partes = nome.trim().split(" ")
        if (partes.size >= 2) return (partes[0].substring(0, 1) + partes[1].substring(0, 1)).uppercase()
        return if (nome.length >= 2) nome.substring(0, 2).uppercase() else nome.uppercase()
    }
}
