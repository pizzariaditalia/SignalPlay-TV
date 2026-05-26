package com.tv.signalplay

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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {

    private var masterFilmes: List<XtreamVod> = listOf()
    private var masterSeries: List<XtreamSerie> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val usuarioFirebase = intent.getStringExtra("FIREBASE_USER") ?: "Cliente"
        val xtreamUser = intent.getStringExtra("XTREAM_USER") ?: ""
        val xtreamPass = intent.getStringExtra("XTREAM_PASS") ?: ""
        val urlXtream = intent.getStringExtra("URL") ?: ""

        findViewById<TextView>(R.id.navPerfil).text = extrairIniciais(usuarioFirebase)
        configurarFocoMenusTV()

        // NAVEGAÇÃO DE ABAS
        findViewById<TextView>(R.id.navCanais).setOnClickListener {
            val intentCanais = Intent(this, CanaisActivity::class.java)
            intentCanais.putExtra("XTREAM_USER", xtreamUser)
            intentCanais.putExtra("XTREAM_PASS", xtreamPass)
            intentCanais.putExtra("URL", urlXtream)
            startActivity(intentCanais)
        }
        findViewById<TextView>(R.id.navInicio).setOnClickListener { 
            resetarCoresMenu(); findViewById<TextView>(R.id.navInicio).setTextColor(Color.WHITE)
            renderizarAbaHome() 
        }
        findViewById<TextView>(R.id.navFilmes).setOnClickListener { 
            resetarCoresMenu(); findViewById<TextView>(R.id.navFilmes).setTextColor(Color.WHITE)
            renderizarAbaFilmes() 
        }
        findViewById<TextView>(R.id.navSeries).setOnClickListener { 
            resetarCoresMenu(); findViewById<TextView>(R.id.navSeries).setTextColor(Color.WHITE)
            renderizarAbaSeries() 
        }

        if (urlXtream.isNotEmpty() && xtreamUser.isNotEmpty() && xtreamPass.isNotEmpty()) {
            baixarCatalogoCompleto(urlXtream, xtreamUser, xtreamPass)
        }
    }

    private fun baixarCatalogoCompleto(url: String, user: String, pass: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val api = XtreamClient.create(url)
                val respVod = api.getVodStreams(user, pass)
                val respSeries = api.getSeriesStreams(user, pass)

                withContext(Dispatchers.Main) {
                    if (respVod.isJsonArray) {
                        val tipoFilme = object : TypeToken<List<XtreamVod>>() {}.type
                        masterFilmes = Gson().fromJson(respVod, tipoFilme)
                    }
                    if (respSeries.isJsonArray) {
                        val tipoSerie = object : TypeToken<List<XtreamSerie>>() {}.type
                        masterSeries = Gson().fromJson(respSeries, tipoSerie)
                    }
                    renderizarAbaHome()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "Erro ao conectar catálogo", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    // --- RENDERIZADORES DAS ESTRUTURAS ---
    private fun renderizarAbaHome() {
        val container = findViewById<LinearLayout>(R.id.containerTrilhos)
        container.removeAllViews()

        if (masterFilmes.isNotEmpty()) setHeroBanner(masterFilmes.random().name, masterFilmes.random().stream_icon)

        // Estrutura EXATA do seu Mobile:
        val top10Filmes = masterFilmes.sortedByDescending { it.rating ?: 0.0 }.take(10)
        if(top10Filmes.isNotEmpty()) renderizarTrilho(container, "Top 10 Filmes", top10Filmes)

        val ultimosFilmes = masterFilmes.sortedByDescending { it.stream_id }.take(30)
        if(ultimosFilmes.isNotEmpty()) renderizarTrilho(container, "Últimos Filmes Adicionados", ultimosFilmes)

        val top10Series = masterSeries.sortedByDescending { it.rating ?: 0.0 }.take(10)
        if(top10Series.isNotEmpty()) renderizarTrilhoSeries(container, "Top 10 Séries", top10Series)

        val seriesAlta = masterSeries.sortedByDescending { it.series_id }.take(30)
        if(seriesAlta.isNotEmpty()) renderizarTrilhoSeries(container, "Séries em Alta", seriesAlta)
    }

    private fun renderizarAbaFilmes() {
        val container = findViewById<LinearLayout>(R.id.containerTrilhos)
        container.removeAllViews()
        if (masterFilmes.isNotEmpty()) setHeroBanner(masterFilmes[0].name, masterFilmes[0].stream_icon)
        val ultimosFilmes = masterFilmes.sortedByDescending { it.stream_id }.take(50)
        if(ultimosFilmes.isNotEmpty()) renderizarTrilho(container, "Todos os Filmes", ultimosFilmes)
    }

    private fun renderizarAbaSeries() {
        val container = findViewById<LinearLayout>(R.id.containerTrilhos)
        container.removeAllViews()
        if (masterSeries.isNotEmpty()) setHeroBanner(masterSeries[0].name, masterSeries[0].cover)
        val seriesAlta = masterSeries.sortedByDescending { it.series_id }.take(50)
        if(seriesAlta.isNotEmpty()) renderizarTrilhoSeries(container, "Todas as Séries", seriesAlta)
    }

    private fun setHeroBanner(titulo: String, imagem: String?) {
        findViewById<TextView>(R.id.badgeDestaque).visibility = View.VISIBLE
        findViewById<Button>(R.id.btnAssistirDestaque).visibility = View.VISIBLE
        findViewById<TextView>(R.id.txtTituloDestaque).text = titulo
        findViewById<TextView>(R.id.txtDescDestaque).text = "Disponível no Catálogo"
        if(imagem != null) Glide.with(this).load(imagem).into(findViewById<ImageView>(R.id.imgBackgroundDestaque))
    }

    private fun renderizarTrilho(container: LinearLayout, titulo: String, lista: List<XtreamVod>) {
        val inflater = LayoutInflater.from(this)
        val trilhoView = inflater.inflate(R.layout.trilho_vod_premium, container, false)
        trilhoView.findViewById<TextView>(R.id.txtTituloTrilho).text = titulo
        val linearInterno = trilhoView.findViewById<LinearLayout>(R.id.linearInternoTrilho)
        
        for (filme in lista) {
            val cardView = inflater.inflate(R.layout.card_vod_premium, linearInterno, false)
            cardView.findViewById<TextView>(R.id.txtNomePremium).text = filme.name
            if (filme.stream_icon != null) Glide.with(this).load(filme.stream_icon).into(cardView.findViewById(R.id.imgCapaPremium))
            configurarZoomCard(cardView)
            linearInterno.addView(cardView)
        }
        container.addView(trilhoView)
    }

    private fun renderizarTrilhoSeries(container: LinearLayout, titulo: String, lista: List<XtreamSerie>) {
        val inflater = LayoutInflater.from(this)
        val trilhoView = inflater.inflate(R.layout.trilho_vod_premium, container, false)
        trilhoView.findViewById<TextView>(R.id.txtTituloTrilho).text = titulo
        val linearInterno = trilhoView.findViewById<LinearLayout>(R.id.linearInternoTrilho)
        
        for (serie in lista) {
            val cardView = inflater.inflate(R.layout.card_vod_premium, linearInterno, false)
            cardView.findViewById<TextView>(R.id.txtNomePremium).text = serie.name
            if (serie.cover != null) Glide.with(this).load(serie.cover).into(cardView.findViewById(R.id.imgCapaPremium))
            configurarZoomCard(cardView)
            linearInterno.addView(cardView)
        }
        container.addView(trilhoView)
    }

    // A MÁGICA QUE EVITA O BUG DE SOBREPOSIÇÃO (Sem zoom no menu)
    private fun configurarFocoMenusTV() {
        val menus = listOf(R.id.navSearch, R.id.navInicio, R.id.navCanais, R.id.navFilmes, R.id.navSeries, R.id.navConfig)
        for (id in menus) {
            val view = findViewById<TextView>(id)
            view?.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) (v as TextView).setTextColor(Color.WHITE)
                else (v as TextView).setTextColor(Color.parseColor("#888888"))
            }
        }
    }

    private fun configurarZoomCard(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
            else v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
        }
    }

    private fun resetarCoresMenu() {
        val menus = listOf(R.id.navInicio, R.id.navCanais, R.id.navFilmes, R.id.navSeries)
        menus.forEach { findViewById<TextView>(it).setTextColor(Color.parseColor("#888888")) }
    }

    private fun extrairIniciais(nome: String): String {
        if (nome.isBlank()) return "BR"
        val partes = nome.trim().split(" ")
        if (partes.size >= 2) return (partes[0].substring(0, 1) + partes[1].substring(0, 1)).uppercase()
        return if (nome.length >= 2) nome.substring(0, 2).uppercase() else nome.uppercase()
    }
}
