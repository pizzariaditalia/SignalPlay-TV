package com.signalplay.tv

import android.app.Activity
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

class HomeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Mapeando a área do Banner Destaque
        val tvHeroTitle = findViewById<TextView>(R.id.heroTitle)
        val tvHeroDesc = findViewById<TextView>(R.id.heroDesc)
        val tvHeroBadge = findViewById<TextView>(R.id.heroBadge)
        val imgHero = findViewById<ImageView>(R.id.heroImage)

        // Mapeando as Prateleiras (RecyclerViews)
        val recyclerUltimos = findViewById<RecyclerView>(R.id.recyclerUltimos)
        val recyclerTopFilmes = findViewById<RecyclerView>(R.id.recyclerTopFilmes)
        val recyclerTopSeries = findViewById<RecyclerView>(R.id.recyclerTopSeries)
        val recyclerSeriesAlta = findViewById<RecyclerView>(R.id.recyclerSeriesAlta)

        // Configurando para rolagem horizontal suave
        recyclerUltimos.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerTopFilmes.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerTopSeries.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerSeriesAlta.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        // Pegando os dados do Xtream que vieram do Login
        val urlOriginal = intent.getStringExtra("URL") ?: ""
        // Limpa a barra no final da URL, se existir, para não dar erro
        val url = if (urlOriginal.endsWith("/")) urlOriginal.dropLast(1) else urlOriginal
        val user = intent.getStringExtra("USER") ?: ""
        val pass = intent.getStringExtra("PASS") ?: ""

        if (url.isEmpty() || user.isEmpty()) {
            Toast.makeText(this, "Erro de credenciais Xtream.", Toast.LENGTH_LONG).show()
            return
        }

        // Inicia o motor de download em segundo plano (Para não travar a tela)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()

                // ==========================================
                // 1. BAIXAR FILMES (VOD)
                // ==========================================
                val reqVod = Request.Builder().url("$url/player_api.php?username=$user&password=$pass&action=get_vod_streams").build()
                val resVod = client.newCall(reqVod).execute()
                val jsonVod = resVod.body?.string() ?: "[]"
                
                val listFilmes = mutableListOf<FilmeItem>()
                if (jsonVod.startsWith("[")) {
                    val vodArray = JSONArray(jsonVod)
                    // Pega até 200 filmes para os testes não pesarem
                    val limite = if (vodArray.length() > 200) 200 else vodArray.length()
                    
                    for (i in 0 until limite) {
                        val obj = vodArray.getJSONObject(i)
                        val id = obj.optString("stream_id", "")
                        val nome = obj.optString("name", "Sem Nome")
                        val icone = obj.optString("stream_icon", "")
                        val ext = obj.optString("container_extension", "mp4")
                        val streamUrl = "$url/movie/$user/$pass/$id.$ext"
                        
                        listFilmes.add(FilmeItem(id, nome, icone, streamUrl, "filme"))
                    }
                }

                // ==========================================
                // 2. BAIXAR SÉRIES
                // ==========================================
                val reqSeries = Request.Builder().url("$url/player_api.php?username=$user&password=$pass&action=get_series").build()
                val resSeries = client.newCall(reqSeries).execute()
                val jsonSeries = resSeries.body?.string() ?: "[]"
                
                val listSeries = mutableListOf<FilmeItem>()
                if (jsonSeries.startsWith("[")) {
                    val seriesArray = JSONArray(jsonSeries)
                    val limiteS = if (seriesArray.length() > 200) 200 else seriesArray.length()
                    
                    for (i in 0 until limiteS) {
                        val obj = seriesArray.getJSONObject(i)
                        val id = obj.optString("series_id", "")
                        val nome = obj.optString("name", "Sem Nome")
                        val icone = obj.optString("cover", "")
                        val streamUrl = "$url/player_api.php?username=$user&password=$pass&action=get_series_info&series_id=$id"
                        
                        listSeries.add(FilmeItem(id, nome, icone, streamUrl, "serie"))
                    }
                }

                // ==========================================
                // 3. ORGANIZAR AS PRATELEIRAS
                // ==========================================
                val ultimosFilmes = listFilmes.reversed().take(30) // Pega os 30 do final da lista
                val topFilmes = listFilmes.take(15) // Pega os primeiros 15
                
                val seriesAlta = listSeries.reversed().take(30)
                val topSeries = listSeries.take(15)

                // Volta para a tela visível para injetar os dados (Thread Principal)
                withContext(Dispatchers.Main) {
                    
                    // Alimenta os "Operários" que vão desenhar as capas
                    recyclerUltimos.adapter = CardAdapter(ultimosFilmes)
                    recyclerTopFilmes.adapter = CardAdapter(topFilmes)
                    recyclerTopSeries.adapter = CardAdapter(topSeries)
                    recyclerSeriesAlta.adapter = CardAdapter(seriesAlta)

                    // Atualiza o Banner Gigante com o filme mais recente!
                    if (ultimosFilmes.isNotEmpty()) {
                        val destaque = ultimosFilmes[0]
                        tvHeroTitle.text = destaque.nome
                        tvHeroDesc.text = "Aperte OK no controle para assistir agora."
                        tvHeroBadge.text = "NOVIDADE EM FILMES"
                        
                        // Faz o Glide colocar a imagem gigante no fundo
                        Glide.with(this@HomeActivity)
                            .load(destaque.urlImagem)
                            .into(imgHero)
                    } else {
                        tvHeroTitle.text = "Catálogo Vazio"
                        tvHeroDesc.text = "O servidor Xtream não retornou dados de filmes."
                    }
                }

            } catch (e: Exception) {
                // Tratamento se a internet cair no meio do download
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HomeActivity, "Falha de conexão com o servidor Xtream.", Toast.LENGTH_LONG).show()
                    tvHeroTitle.text = "Erro de Sincronização"
                    tvHeroDesc.text = e.message
                }
            }
        }
    }
}
