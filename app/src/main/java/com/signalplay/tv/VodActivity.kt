package com.signalplay.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

class VodActivity : Activity() {

    private val todasCategorias = mutableListOf<CategoriaItem>()
    private val todosFilmes = mutableListOf<FilmeItem>()

    private lateinit var recyclerCanaisGrid: RecyclerView
    private lateinit var tvTituloCategoria: TextView
    
    private var url = ""
    private var user = ""
    private var pass = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv)

        val recyclerCategories = findViewById<RecyclerView>(R.id.recyclerCategories)
        recyclerCanaisGrid = findViewById<RecyclerView>(R.id.recyclerCanaisGrid)
        tvTituloCategoria = findViewById<TextView>(R.id.tvTituloCategoria)

        recyclerCategories.layoutManager = LinearLayoutManager(this)
        recyclerCanaisGrid.layoutManager = GridLayoutManager(this, 5)

        url = intent.getStringExtra("URL") ?: ""
        user = intent.getStringExtra("USER") ?: ""
        pass = intent.getStringExtra("PASS") ?: ""

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // FILTRO PARENTAL ATIVADO
                val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
                val isParentalActive = prefs.getBoolean("PARENTAL_CONTROL", false)
                val palavrasProibidas = listOf("adult", "+18", "18+", "xxx", "porn", "hachutv", "sensual", "sex")

                val client = OkHttpClient()

                val reqCat = Request.Builder().url("$url/player_api.php?username=$user&password=$pass&action=get_vod_categories").build()
                val resCat = client.newCall(reqCat).execute()
                val jsonCat = resCat.body?.string() ?: "[]"
                
                if (jsonCat.startsWith("[")) {
                    val arr = JSONArray(jsonCat)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val catName = obj.optString("category_name", "")
                        
                        // Verifica se o nome da pasta tem palavra proibida
                        val isAdult = palavrasProibidas.any { catName.lowercase().contains(it) }
                        if (!isParentalActive || !isAdult) {
                            todasCategorias.add(CategoriaItem(obj.optString("category_id"), catName))
                        }
                    }
                }

                val reqVod = Request.Builder().url("$url/player_api.php?username=$user&password=$pass&action=get_vod_streams").build()
                val resVod = client.newCall(reqVod).execute()
                val jsonVod = resVod.body?.string() ?: "[]"
                
                if (jsonVod.startsWith("[")) {
                    val arr = JSONArray(jsonVod)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optString("stream_id")
                        val nome = obj.optString("name")
                        val icone = obj.optString("stream_icon")
                        val catId = obj.optString("category_id")
                        val ext = obj.optString("container_extension", "mp4")
                        val streamUrl = "$url/movie/$user/$pass/$id.$ext"
                        todosFilmes.add(FilmeItem(id, nome, icone, streamUrl, "filme", catId))
                    }
                }

                withContext(Dispatchers.Main) {
                    recyclerCategories.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
                            return object : RecyclerView.ViewHolder(v) {}
                        }

                        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                            val cat = todasCategorias[position]
                            val txt = holder.itemView as TextView
                            txt.text = cat.nome
                            txt.setOnClickListener {
                                exibirFilmesDaCategoria(cat.id, cat.nome)
                            }
                        }

                        override fun getItemCount(): Int = todasCategorias.size
                    }

                    if (todasCategorias.isNotEmpty()) {
                        exibirFilmesDaCategoria(todasCategorias[0].id, todasCategorias[0].nome)
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VodActivity, "Erro ao carregar filmes.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun exibirFilmesDaCategoria(catId: String, catNome: String) {
        tvTituloCategoria.text = catNome
        val filmesFiltrados = todosFilmes.filter { it.categoryId == catId }

        recyclerCanaisGrid.adapter = CardAdapter(filmesFiltrados) { filmeClicado ->
            val intentDet = Intent(this, DetailsActivity::class.java)
            intentDet.putExtra("URL", url)
            intentDet.putExtra("USER", user)
            intentDet.putExtra("PASS", pass)
            intentDet.putExtra("MEDIA_ID", filmeClicado.id)
            intentDet.putExtra("MEDIA_TIPO", filmeClicado.tipo)
            intentDet.putExtra("MEDIA_NOME", filmeClicado.nome)
            intentDet.putExtra("MEDIA_CAPA", filmeClicado.urlImagem)
            startActivity(intentDet)
        }
    }
}
