package com.signalplay.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        db = FirebaseFirestore.getInstance()

        val tvHeroTitle = findViewById<TextView>(R.id.heroTitle)
        val tvHeroDesc = findViewById<TextView>(R.id.heroDesc)
        val tvHeroBadge = findViewById<TextView>(R.id.heroBadge)
        val imgHero = findViewById<ImageView>(R.id.heroImage)

        val menuCanais = findViewById<TextView>(R.id.menuCanais)
        val menuFilmes = findViewById<TextView>(R.id.menuFilmes)
        val menuSeries = findViewById<TextView>(R.id.menuSeries)

        val recyclerFavoritos = findViewById<RecyclerView>(R.id.recyclerFavoritos)
        val recyclerUltimos = findViewById<RecyclerView>(R.id.recyclerUltimos)
        val recyclerTopFilmes = findViewById<RecyclerView>(R.id.recyclerTopFilmes)
        val recyclerTopSeries = findViewById<RecyclerView>(R.id.recyclerTopSeries)
        val recyclerSeriesAlta = findViewById<RecyclerView>(R.id.recyclerSeriesAlta)

        recyclerFavoritos.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerUltimos.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerTopFilmes.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerTopSeries.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerSeriesAlta.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        val urlOriginal = intent.getStringExtra("URL") ?: ""
        val url = if (urlOriginal.endsWith("/")) urlOriginal.dropLast(1) else urlOriginal
        val user = intent.getStringExtra("USER") ?: ""
        val pass = intent.getStringExtra("PASS") ?: ""
        val username = intent.getStringExtra("USERNAME") ?: ""

        // LÓGICA DE NAVEGAÇÃO CENTRALIZADA
        fun abrirDetalhes(itemClicado: FilmeItem) {
            val intentDet = Intent(this@HomeActivity, DetailsActivity::class.java)
            intentDet.putExtra("URL", url)
            intentDet.putExtra("USER", user)
            intentDet.putExtra("PASS", pass)
            intentDet.putExtra("MEDIA_ID", itemClicado.id)
            intentDet.putExtra("MEDIA_TIPO", itemClicado.tipo)
            intentDet.putExtra("MEDIA_NOME", itemClicado.nome)
            intentDet.putExtra("MEDIA_CAPA", itemClicado.urlImagem)
            startActivity(intentDet)
        }

        menuCanais.setOnClickListener {
            val intentTv = Intent(this@HomeActivity, TvActivity::class.java)
            intentTv.putExtra("URL", url)
            intentTv.putExtra("USER", user)
            intentTv.putExtra("PASS", pass)
            intentTv.putExtra("USERNAME", username)
            startActivity(intentTv)
        }

        menuFilmes.setOnClickListener {
            val intentVod = Intent(this@HomeActivity, VodActivity::class.java)
            intentVod.putExtra("URL", url)
            intentVod.putExtra("USER", user)
            intentVod.putExtra("PASS", pass)
            startActivity(intentVod)
        }

        menuSeries.setOnClickListener {
            val intentSeries = Intent(this@HomeActivity, SeriesActivity::class.java)
            intentSeries.putExtra("URL", url)
            intentSeries.putExtra("USER", user)
            intentSeries.putExtra("PASS", pass)
            startActivity(intentSeries)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val listaIdsFavoritos = mutableListOf<String>()
                
                db.collection("usuarios").whereEqualTo("usuario", username).get()
                    .addOnSuccessListener { snapshot ->
                        if (!snapshot.isEmpty) {
                            val favs = snapshot.documents[0].get("favoritos") as? List<*>
                            favs?.forEach { listaIdsFavoritos.add(it.toString()) }
                        }
                    }
                
                withContext(Dispatchers.IO) { Thread.sleep(500) }

                val reqLive = Request.Builder().url("$url/player_api.php?username=$user&password=$pass&action=get_live_streams").build()
                val resLive = client.newCall(reqLive).execute()
                val jsonLive = resLive.body?.string() ?: "[]"
                
                val listFavoritos = mutableListOf<FilmeItem>()
                if (jsonLive.startsWith("[")) {
                    val liveArray = JSONArray(jsonLive)
                    for (i in 0 until liveArray.length()) {
                        val obj = liveArray.getJSONObject(i)
                        val id = obj.optString("stream_id", "")
                        if (listaIdsFavoritos.contains(id)) {
                            val nome = obj.optString("name", "Canal")
                            val icone = obj.optString("stream_icon", "")
                            val streamUrl = "$url/live/$user/$pass/$id.ts"
                            listFavoritos.add(FilmeItem(id, nome, icone, streamUrl, "tv", ""))
                        }
                    }
                }

                val reqVod = Request.Builder().url("$url/player_api.php?username=$user&password=$pass&action=get_vod_streams").build()
                val resVod = client.newCall(reqVod).execute()
                val jsonVod = resVod.body?.string() ?: "[]"
                
                val listFilmes = mutableListOf<FilmeItem>()
                if (jsonVod.startsWith("[")) {
                    val vodArray = JSONArray(jsonVod)
                    val limite = if (vodArray.length() > 150) 150 else vodArray.length()
                    for (i in 0 until limite) {
                        val obj = vodArray.getJSONObject(i)
                        val id = obj.optString("stream_id", "")
                        val nome = obj.optString("name", "Sem Nome")
                        val icone = obj.optString("stream_icon", "")
                        val ext = obj.optString("container_extension", "mp4")
                        val streamUrl = "$url/movie/$user/$pass/$id.$ext"
                        listFilmes.add(FilmeItem(id, nome, icone, streamUrl, "filme", ""))
                    }
                }

                val reqSeries = Request.Builder().url("$url/player_api.php?username=$user&password=$pass&action=get_series").build()
                val resSeries = client.newCall(reqSeries).execute()
                val jsonSeries = resSeries.body?.string() ?: "[]"
                
                val listSeries = mutableListOf<FilmeItem>()
                if (jsonSeries.startsWith("[")) {
                    val seriesArray = JSONArray(jsonSeries)
                    val limiteS = if (seriesArray.length() > 150) 150 else seriesArray.length()
                    for (i in 0 until limiteS) {
                        val obj = seriesArray.getJSONObject(i)
                        val id = obj.optString("series_id", "")
                        val nome = obj.optString("name", "Sem Nome")
                        val icone = obj.optString("cover", "")
                        val streamUrl = "$url/player_api.php?username=$user&password=$pass&action=get_series_info&series_id=$id"
                        listSeries.add(FilmeItem(id, nome, icone, streamUrl, "serie", ""))
                    }
                }

                val ultimosFilmes = listFilmes.reversed().take(30)
                val topFilmes = listFilmes.take(15)
                val seriesAlta = listSeries.reversed().take(30)
                val topSeries = listSeries.take(15)

                withContext(Dispatchers.Main) {
                    recyclerFavoritos.adapter = CardAdapter(listFavoritos) {
                        // Canais abrem direto (será feito no player)
                    }
                    recyclerUltimos.adapter = CardAdapter(ultimosFilmes) { abrirDetalhes(it) }
                    recyclerTopFilmes.adapter = CardAdapter(topFilmes) { abrirDetalhes(it) }
                    recyclerTopSeries.adapter = CardAdapter(topSeries) { abrirDetalhes(it) }
                    recyclerSeriesAlta.adapter = CardAdapter(seriesAlta) { abrirDetalhes(it) }

                    if (ultimosFilmes.isNotEmpty()) {
                        val destaque = ultimosFilmes[0]
                        tvHeroTitle.text = destaque.nome
                        tvHeroDesc.text = "Aperte OK no controle para ver os detalhes."
                        tvHeroBadge.text = "NOVIDADE EM FILMES"
                        
                        Glide.with(this@HomeActivity)
                            .load(destaque.urlImagem)
                            .into(imgHero)
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HomeActivity, "Erro ao carregar o catálogo.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
