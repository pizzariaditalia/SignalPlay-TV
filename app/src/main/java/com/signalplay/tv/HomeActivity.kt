package com.signalplay.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        urlGlobal = if (urlOriginal.endsWith("/")) urlOriginal.dropLast(1) else urlOriginal
        userGlobal = intent.getStringExtra("USER") ?: ""
        passGlobal = intent.getStringExtra("PASS") ?: ""
        val username = intent.getStringExtra("USERNAME") ?: ""

        menuPesquisar.setOnClickListener {
            val intent = Intent(this@HomeActivity, SearchActivity::class.java)
            intent.putExtra("URL", urlGlobal)
            intent.putExtra("USER", userGlobal)
            intent.putExtra("PASS", passGlobal)
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
            startActivity(intent)
        }
        menuSeries.setOnClickListener {
            val intent = Intent(this@HomeActivity, SeriesActivity::class.java)
            intent.putExtra("URL", urlGlobal)
            intent.putExtra("USER", userGlobal)
            intent.putExtra("PASS", passGlobal)
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

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // LÊ O CONTROLE PARENTAL DA MEMÓRIA
                val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
                val isParentalActive = prefs.getBoolean("PARENTAL_CONTROL", false)
                val palavrasProibidas = listOf("adult", "+18", "18+", "xxx", "porn", "hachutv", "sensual", "sex")

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

                val liveCats = mutableListOf<CategoriaItem>()
                liveCats.add(CategoriaItem("FAV", "Canais Favoritos"))
                
                val reqCat = Request.Builder().url("$urlGlobal/player_api.php?username=$userGlobal&password=$passGlobal&action=get_live_categories").build()
                val resCat = client.newCall(reqCat).execute()
                val jsonCat = resCat.body?.string() ?: "[]"
                if (jsonCat.startsWith("[")) {
                    val arr = JSONArray(jsonCat)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val catName = obj.optString("category_name", "")
                        // FILTRO PARENTAL
                        val isAdult = palavrasProibidas.any { catName.lowercase().contains(it) }
                        if (!isParentalActive || !isAdult) {
                            liveCats.add(CategoriaItem(obj.optString("category_id"), catName))
                        }
                    }
                }

                val reqLive = Request.Builder().url("$urlGlobal/player_api.php?username=$userGlobal&password=$passGlobal&action=get_live_streams").build()
                val resLive = client.newCall(reqLive).execute()
                val jsonLive = resLive.body?.string() ?: "[]"
                
                val listTodosCanais = mutableListOf<CanalItem>()
                val listFavoritos = mutableListOf<CanalItem>()
                
                if (jsonLive.startsWith("[")) {
                    val liveArray = JSONArray(jsonLive)
                    for (i in 0 until liveArray.length()) {
                        val obj = liveArray.getJSONObject(i)
                        val id = obj.optString("stream_id", "")
                        val nome = obj.optString("name", "Canal")
                        val icone = obj.optString("stream_icon", "")
                        val catId = obj.optString("category_id")
                        val streamUrl = "$urlGlobal/live/$userGlobal/$passGlobal/$id.ts"
                        
                        val canal = CanalItem(id, nome, icone, catId, streamUrl)
                        listTodosCanais.add(canal)
                        if (listaIdsFavoritos.contains(id)) listFavoritos.add(canal)
                    }
                }

                val reqVod = Request.Builder().url("$urlGlobal/player_api.php?username=$userGlobal&password=$passGlobal&action=get_vod_streams").build()
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
                        val streamUrl = "$urlGlobal/movie/$userGlobal/$passGlobal/$id.$ext"
                        listFilmes.add(FilmeItem(id, nome, icone, streamUrl, "filme", ""))
                    }
                }

                val reqSeries = Request.Builder().url("$urlGlobal/player_api.php?username=$userGlobal&password=$passGlobal&action=get_series").build()
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
                        val streamUrl = "$urlGlobal/player_api.php?username=$userGlobal&password=$passGlobal&action=get_series_info&series_id=$id"
                        listSeries.add(FilmeItem(id, nome, icone, streamUrl, "serie", ""))
                    }
                }

                val ultimosFilmes = listFilmes.reversed().take(30)
                val topFilmes = listFilmes.take(10)
                val seriesAlta = listSeries.reversed().take(30)
                val topSeries = listSeries.take(10)

                withContext(Dispatchers.Main) {
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

                    if (listFilmes.isNotEmpty()) {
                        atualizarBanner(listFilmes.random())
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(this@HomeActivity, "Erro ao carregar o catálogo.", Toast.LENGTH_SHORT).show() }
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
        
        // MATRIZ TOPCROP: Essa magia garante que o topo do pôster sempre apareça na TV!
        Glide.with(this).load(filme.urlImagem).into(object : CustomViewTarget<ImageView, Drawable>(imageView) {
            override fun onLoadFailed(errorDrawable: Drawable?) {}
            override fun onResourceCleared(placeholder: Drawable?) {}
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                view.scaleType = ImageView.ScaleType.MATRIX
                val matrix = Matrix()
                val scale = view.width.toFloat() / resource.intrinsicWidth.toFloat()
                matrix.setScale(scale, scale)
                matrix.postTranslate(0f, 0f) // Alinha exato no TOPO!
                view.imageMatrix = matrix
                view.setImageDrawable(resource)
            }
        })
    }

    private fun abrirDetalhes(itemClicado: FilmeItem) {
        val intentDet = Intent(this@HomeActivity, DetailsActivity::class.java)
        intentDet.putExtra("URL", urlGlobal)
        intentDet.putExtra("USER", userGlobal)
        intentDet.putExtra("PASS", passGlobal)
        intentDet.putExtra("MEDIA_ID", itemClicado.id)
        intentDet.putExtra("MEDIA_TIPO", itemClicado.tipo)
        intentDet.putExtra("MEDIA_NOME", itemClicado.nome)
        intentDet.putExtra("MEDIA_CAPA", itemClicado.urlImagem)
        startActivity(intentDet)
    }
}
