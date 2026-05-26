package com.tv.signalplay

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
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

        val usuarioLogado = intent.getStringExtra("USER") ?: "Cliente"
        val senhaLogada = intent.getStringExtra("PASS") ?: ""
        val urlDoServidor = intent.getStringExtra("URL") ?: ""

        val navPerfil = findViewById<TextView>(R.id.navPerfil)
        navPerfil.text = extrairIniciais(usuarioLogado)

        configurarFocoTV()

        // Chama a função que bate no servidor Xtream para puxar as imagens
        if (urlDoServidor.isNotEmpty() && usuarioLogado.isNotEmpty() && senhaLogada.isNotEmpty()) {
            carregarFilmes(urlDoServidor, usuarioLogado, senhaLogada)
        } else {
            Toast.makeText(this, "Faltam dados de conexão.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun carregarFilmes(url: String, user: String, pass: String) {
        val card1 = findViewById<ImageView>(R.id.card1)
        val card2 = findViewById<ImageView>(R.id.card2)
        val card3 = findViewById<ImageView>(R.id.card3)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Instancia o mensageiro e bate no servidor
                val api = XtreamClient.create(url)
                val response = api.getVodStreams(user, pass)

                withContext(Dispatchers.Main) {
                    if (response.isJsonArray) {
                        // É a lista de filmes como esperado
                        val tipoLista = object : TypeToken<List<XtreamVod>>() {}.type
                        val filmes: List<XtreamVod> = Gson().fromJson(response, tipoLista)

                        if (filmes.isNotEmpty()) {
                            if (filmes.size > 0 && !filmes[0].stream_icon.isNullOrEmpty()) {
                                Glide.with(this@MainActivity).load(filmes[0].stream_icon).into(card1)
                            }
                            if (filmes.size > 1 && !filmes[1].stream_icon.isNullOrEmpty()) {
                                Glide.with(this@MainActivity).load(filmes[1].stream_icon).into(card2)
                            }
                            if (filmes.size > 2 && !filmes[2].stream_icon.isNullOrEmpty()) {
                                Glide.with(this@MainActivity).load(filmes[2].stream_icon).into(card3)
                            }
                        } else {
                            Toast.makeText(this@MainActivity, "Nenhum filme encontrado no servidor.", Toast.LENGTH_SHORT).show()
                        }
                    } else if (response.isJsonObject) {
                        // O servidor devolveu um objeto de erro (ex: Auth Failed)
                        Toast.makeText(this@MainActivity, "Erro Xtream: Usuário não autorizado no painel destino.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Erro de conexão: ${e.message}", Toast.LENGTH_LONG).show()
                }
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

    private fun configurarFocoTV() {
        val elementosParaAnimar = listOf(
            R.id.navSearch, R.id.navInicio, R.id.navCanais, R.id.navFilmes, 
            R.id.navSeries, R.id.navGuia, R.id.navConfig, R.id.navPerfil,
            R.id.btnAssistirDestaque, R.id.card1, R.id.card2, R.id.card3
        )

        for (id in elementosParaAnimar) {
            val view = findViewById<View>(id)
            view?.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                    if (v is TextView && v.id != R.id.navPerfil && v.id != R.id.btnAssistirDestaque) {
                        v.setTextColor(Color.WHITE)
                    }
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                    if (v is TextView && v.id != R.id.navInicio && v.id != R.id.navPerfil && v.id != R.id.btnAssistirDestaque) {
                        v.setTextColor(Color.parseColor("#888888"))
                    }
                }
            }
        }
    }
}
