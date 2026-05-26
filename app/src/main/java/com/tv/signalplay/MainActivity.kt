package com.tv.signalplay

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Pega as Chaves Mestras ocultas vindas do Login
        val usuarioFirebase = intent.getStringExtra("FIREBASE_USER") ?: "Cliente"
        val xtreamUser = intent.getStringExtra("XTREAM_USER") ?: ""
        val xtreamPass = intent.getStringExtra("XTREAM_PASS") ?: ""
        val urlXtream = intent.getStringExtra("URL") ?: ""

        // Coloca o nome do cliente na bola amarela do perfil
        val navPerfil = findViewById<TextView>(R.id.navPerfil)
        navPerfil.text = extrairIniciais(usuarioFirebase)

        configurarFocoTV()

        // 2. Chama o motor premium de renderização
        if (urlXtream.isNotEmpty() && xtreamUser.isNotEmpty() && xtreamPass.isNotEmpty()) {
            Toast.makeText(this, "Carregando visual premium...", Toast.LENGTH_SHORT).show()
            carregarFilmesPremium(urlXtream, xtreamUser, xtreamPass)
        }
    }

    private fun carregarFilmesPremium(url: String, user: String, pass: String) {
        val containerTrilhos = findViewById<LinearLayout>(R.id.containerTrilhos)
        containerTrilhos.removeAllViews() // Limpa antes de carregar

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Instancia o mensageiro da API
                val api = XtreamClient.create(url)
                val response = api.getVodStreams(user, pass)

                withContext(Dispatchers.Main) {
                    if (response.isJsonArray) {
                        val tipoLista = object : TypeToken<List<XtreamVod>>() {}.type
                        val filmes: List<XtreamVod> = Gson().fromJson(response, tipoLista)

                        if (filmes.isNotEmpty()) {
                            // 🚀 MOTOR DE RENDERIZAÇÃO PREMIUM 
                            // Replicando o design do mobile para TV

                            // 1. Atualiza o Hero Banner Principal com o primeiro filme
                            if (filmes[0].stream_icon != null) {
                                findViewById<TextView>(R.id.txtTituloDestaque).text = filmes[0].name
                                val heroImg = findViewById<ImageView>(R.id.imgBackgroundDestaque)
                               Glide.with(this@MainActivity).load(filmes[0].stream_icon).into(heroImg)
                            }

                            // 2. Trilho 1: "Filmes Adicionados Recentemente" (Sempre em primeiro)
                            renderizarTrilho(containerTrilhos, "Últimos Filmes Adicionados", filmes.take(30), false)

                            // 3. Trilho 2: "Central da Copa 2026" (Replicando o mobile)
                            // Aqui o Kotlin busca se existe essa categoria no seu servidor
                            val copaFilms = filmes.filter { it.category_name?.contains("Copa") == true }
                            if (copaFilms.isNotEmpty()) {
                                renderizarTrilho(containerTrilhos, "🏆 Central da Copa 2026", copaFilms, false)
                            }

                            // 4. Trilho 3: "Dramas" (Replicando o mobile)
                            val dramasFilms = filmes.filter { it.category_name?.contains("Dramas") == true }
                            if (dramasFilms.isNotEmpty()) {
                                renderizarTrilho(containerTrilhos, " Doramas | Dramas Coreanos", dramasFilms, false)
                            }

                        } else {
                            Toast.makeText(this@MainActivity, "Nenhum filme no catálogo Xtream.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Falha ao renderizar catálogo premium: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // A função mágica que constrói um trilho premium completo na TV
    private fun renderizarTrilho(container: LinearLayout, titulo: String, listaFilmes: List<XtreamVod>, isCanalSquare: Boolean) {
        val inflater = LayoutInflater.from(this)
        
        // Carrega o esqueleto do Trilho (Título + ScrollView)
        val trilhoView = inflater.inflate(R.layout.trilho_vod_premium, container, false)
        
        // Aplica o título (com o Vermelho Netflix que você pediu)
        val txtTitulo = trilhoView.findViewById<TextView>(R.id.txtTituloTrilho)
        txtTitulo.text = titulo
        
        // Pega o LinearLayout interno onde os cards vão morar
        val linearInterno = trilhoView.findViewById<LinearLayout>(R.id.linearInternoTrilho)
        
        // Pega o ID do molde do card (Pôster ou Quadrado do Canal)
        val cardLayoutId = if (isCanalSquare) R.layout.card_canal_premium else R.layout.card_vod_premium

        // Loop que constrói os cards um por um
        for (filme in listaFilmes) {
            val cardView = inflater.inflate(cardLayoutId, linearInterno, false)
            val imgCapa = cardView.findViewById<ImageView>(R.id.imgCapaPremium)
            val txtNome = cardView.findViewById<TextView>(R.id.txtNomePremium)
            
            txtNome.text = filme.name
            if (filme.stream_icon != null) {
                Glide.with(this).load(filme.stream_icon).into(imgCapa)
            }
            
            // Configura o efeito de Foco da TV para este card
            configurarEfeitoFocoCard(cardView)
            
            linearInterno.addView(cardView)
        }
        
        container.addView(trilhoView)
    }

    // A função de ouro da TV: Animação e Foco Premium (Glassmorphism)
    private fun configurarEfeitoFocoCard(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                // Efeito de crescer (zoom)
                v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                // Coloca a borda grossa Amarela Vibrante que o mobile usa
                v.findViewById<LinearLayout>(R.id.boxCardInterno).setBackgroundResource(R.drawable.bg_card_premium_focado)
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                // Volta ao visual original (sem graça, mas focado no Amarelo)
                v.findViewById<LinearLayout>(R.id.boxCardInterno).setBackgroundResource(R.drawable.bg_card_premium_normal)
            }
        }
    }

    private fun extrairIniciais(nome: String): String {
        if (nome.isBlank()) return "BR"
        val partes = nome.trim().split(" ")
        if (partes.size >= 2) {
            return (partes[0].substring(0, 1) + partes[1].substring(0, 1)).uppercase()
        }
        return if (nome.length >= 2) nome.substring(0, 2).uppercase() else nome.uppercase()
    }

    // Efeito de Foco para o Menu do Topo (não muda a cor, só brilha)
    private fun configurarFocoTV() {
        val elementosParaAnimar = listOf(
            R.id.navSearch, R.id.navInicio, R.id.navCanais, R.id.navFilmes, 
            R.id.navSeries, R.id.navConfig, R.id.navPerfil, R.id.btnAssistirDestaque
        )

        for (id in elementosParaAnimar) {
            val view = findViewById<View>(id)
            view?.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                    if (v is TextView && v.id != R.id.navPerfil) {
                        v.setTextColor(Color.WHITE)
                    }
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                    if (v is TextView && v.id != R.id.navInicio && v.id != R.id.navPerfil) {
                        v.setTextColor(Color.parseColor("#888888"))
                    }
                }
            }
        }
    }
}
