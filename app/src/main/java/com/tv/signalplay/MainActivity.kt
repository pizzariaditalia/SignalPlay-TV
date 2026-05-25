package com.tv.signalplay

import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Por enquanto, vamos usar uma tela padrão do Android até desenharmos o layout dos canais
        setContentView(android.R.layout.activity_list_item)

        // Abrindo a "mochila" (Intent) e pegando os dados enviados pelo Login
        val usuarioLogado = intent.getStringExtra("USER") ?: ""
        val senhaLogada = intent.getStringExtra("PASS") ?: ""
        val urlDoServidor = intent.getStringExtra("URL") ?: ""

        // Uma mensagem rápida na tela da TV para confirmar que os dados chegaram com sucesso
        if (urlDoServidor.isNotEmpty()) {
            Toast.makeText(
                this, 
                "Conectado ao Servidor: $urlDoServidor\nUsuário: $usuarioLogado", 
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(this, "Erro: Dados do servidor não recebidos.", Toast.LENGTH_LONG).show()
        }

        // O PRÓXIMO PASSO ACONTECE AQUI: 
        // Com a URL, Usuário e Senha em mãos, vamos chamar as Categorias do Xtream Codes!
    }
}
