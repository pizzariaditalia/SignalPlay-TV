package com.signalplay.tv

import android.app.Activity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // REAPROVEITAMOS O LAYOUT DA TV! 
        setContentView(R.layout.activity_tv)

        val recyclerCategories = findViewById<RecyclerView>(R.id.recyclerCategories)
        recyclerCanaisGrid = findViewById<RecyclerView>(R.id.recyclerCanaisGrid)
        tvTituloCategoria = findViewById<TextView>(R.id.tvTituloCategoria)

        recyclerCategories.layoutManager = LinearLayoutManager(this)
        
        // Pôsteres de filmes são mais finos que de TV, então cabem 5 por linha
        recyclerCanaisGrid.layoutManager = GridLayoutManager(this, 5)

        val url = intent.getStringExtra("URL") ?: ""
        val user = intent.getStringExtra("USER") ?: ""
        val pass = intent.getStringExtra("PASS") ?: ""

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()

                // 1. Baixar Categorias de Filmes
                val reqCat = Request.Builder().url("$url/player_api.php?username=$user&password=$pass&action=get_vod_categories").build()
                val resCat = client.newCall(reqCat).execute()
                val jsonCat = resCat.body?.string() ?: "[]"
                
                if (jsonCat.startsWith("[")) {
                    val arr = JSONArray(jsonCat)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        todasCategorias.add(CategoriaItem(obj.optString("category_id"), obj.optString("category_name")))
                    }
                }

                // 2. Baixar todos os Filmes
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
            Toast.makeText(this, "Clicou no filme: ${filmeClicado.nome}", Toast.LENGTH_SHORT).show()
            // (Próximo passo: Abrir a tela de Detalhes do Filme)
        }
    }
}
