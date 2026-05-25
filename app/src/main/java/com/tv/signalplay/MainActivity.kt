package com.tv.signalplay

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Receber os dados do Login
        val usuarioLogado = intent.getStringExtra("USER") ?: "Cliente"
        val urlDoServidor = intent.getStringExtra("URL") ?: ""

        // 2. Mapear o ícone de Perfil e colocar as iniciais do usuário
        val navPerfil = findViewById<TextView>(R.id.navPerfil)
        navPerfil.text = extrairIniciais(usuarioLogado)

        // Mostrar aviso de boas-vindas
        Toast.makeText(this, "Conectado: $usuarioLogado", Toast.LENGTH_SHORT).show()

        // 3. Aplicar o Efeito de "Foco de TV" (Zoom ao selecionar com controle)
        configurarFocoTV()
    }

    // Função que pega "João Silva" e transforma em "JS", ou "admin" em "AD"
    private fun extrairIniciais(nome: String): String {
        if (nome.isBlank()) return "BR"
        val partes = nome.trim().split(" ")
        if (partes.size >= 2) {
            return (partes[0].substring(0, 1) + partes[1].substring(0, 1)).uppercase()
        }
        return if (nome.length >= 2) nome.substring(0, 2).uppercase() else nome.uppercase()
    }

    // O segredo do App de TV: Animar os botões quando o controle remoto passa por eles
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
                    // Cresce a view e, se for texto do menu, fica branco
                    v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                    if (v is TextView && v.id != R.id.navPerfil && v.id != R.id.btnAssistirDestaque) {
                        v.setTextColor(Color.WHITE)
                    }
                } else {
                    // Volta ao tamanho normal e, se for texto do menu, fica cinza
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                    if (v is TextView && v.id != R.id.navInicio && v.id != R.id.navPerfil && v.id != R.id.btnAssistirDestaque) {
                        v.setTextColor(Color.parseColor("#888888"))
                    }
                }
            }
        }
    }
}
