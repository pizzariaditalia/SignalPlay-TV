package com.signalplay.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

data class CategoriaItem(val id: String, val nome: String)

class TvActivity : Activity() {

    private lateinit var db: FirebaseFirestore
    private val favoritosIds = mutableListOf<String>()
    
    private val todosCanais = mutableListOf<CanalItem>()
    private val todasCategorias = mutableListOf<CategoriaItem>()

    private lateinit var recyclerCanaisGrid: RecyclerView
    private lateinit var tvTituloCategoria: TextView
    private var username: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv)

        db = FirebaseFirestore.getInstance()

        val recyclerCategories = findViewById<RecyclerView>(R.id.recyclerCategories)
        recyclerCanaisGrid = findViewById<RecyclerView>(R.id.recyclerCanaisGrid)
        tvTituloCategoria = findViewById<TextView>(R.id.tvTituloCategoria)

        recyclerCategories.layoutManager = LinearLayoutManager(this)
        recyclerCanaisGrid.layoutManager = GridLayoutManager(this, 5)

        val url = intent.getStringExtra("URL") ?: ""
        val user = intent.getStringExtra("USER") ?: ""
        val pass = intent.getStringExtra("PASS") ?: ""
        username = intent.getStringExtra("USERNAME") ?: ""

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
                val isParentalActive = prefs.getBoolean("PARENTAL_CONTROL", false)
                val filterSD = prefs.getBoolean("FILTER_SD", false)
                val filterH265 = prefs.getBoolean("FILTER_H265", false)
                val filter4K = prefs.getBoolean("FILTER_4K", false)
                
                val palavrasProibidas = listOf("adult", "+18", "18+", "xxx", "porn", "hachutv", "sensual", "sex")

                db.collection("usuarios")
                    .whereEqualTo("usuario", username)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        if (!snapshot.isEmpty) {
                            val favs = snapshot.documents[0].get("favoritos") as? List<*>
                            favs?.forEach { favoritosIds.add(it.toString()) }
                        }
                    }

                val client = OkHttpClient()

                val defCat = async { client.newCall(Request.Builder().url("$url/player_api.php?username=$user&password=$pass&action=get_live_categories").build()).execute().body?.string() ?: "[]" }
                val defLive = async { client.newCall(Request.Builder().url("$url/player_api.php?username=$user&password=$pass&action=get_live_streams").build()).execute().body?.string() ?: "[]" }

                val jsonCat = defCat.await()
                val jsonLive = defLive.await()

                if (jsonCat.startsWith("[")) {
                    val arr = JSONArray(jsonCat)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val catName = obj.optString("category_name", "")
                        
                        val isAdult = palavrasProibidas.any { catName.lowercase().contains(it) }
                        if (!isParentalActive || !isAdult) {
                            todasCategorias.add(CategoriaItem(obj.optString("category_id"), catName))
                        }
                    }
                }
                
                todasCategorias.sortBy { 
                    val n = it.nome.lowercase()
                    if (n.contains("jogos de hoje") || n.contains("casa do patrão") || n.contains("casa do patrao")) 1 else 0 
                }

                if (jsonLive.startsWith("[")) {
                    val arr = JSONArray(jsonLive)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optString("stream_id")
                        val nome = obj.optString("name")
                        
                        val epgId = obj.optString("epg_channel_id", "")
                        if (epgId.isNotEmpty()) DataHolder.mapaEpgIds[id] = epgId
                        
                        // MÁGICA: Filtro Agressivo também na TV
                        val nUp = nome.uppercase()
                        val isSD = nUp.contains(" SD ") || nUp.endsWith(" SD") || nUp.startsWith("SD ") || nUp.contains("(SD)") || nUp.contains("[SD]") || nUp.contains("|SD|") || nUp.contains("- SD") || nUp == "SD"
                        val isH265 = nUp.contains("H265") || nUp.contains("HEVC") || nUp.contains("H.265")
                        val is4K = nUp.contains(" 4K ") || nUp.endsWith(" 4K") || nUp.startsWith("4K ") || nUp.contains("(4K)") || nUp.contains("[4K]") || nUp.contains("|4K|") || nUp.contains("- 4K") || nUp.contains("UHD") || nUp == "4K"
                        
                        if (filterSD && isSD) continue
                        if (filterH265 && isH265) continue
                        if (filter4K && is4K) continue

                        val icone = obj.optString("stream_icon")
                        val catId = obj.optString("category_id")
                        val streamUrl = "$url/live/$user/$pass/$id.ts"
                        todosCanais.add(CanalItem(id, nome, icone, catId, streamUrl))
                    }
                }

                withContext(Dispatchers.Main) {
                    findViewById<RelativeLayout>(R.id.loadingOverlay).visibility = View.GONE

                    recyclerCategories.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
                            return object : RecyclerView.ViewHolder(v) {}
                        }
                        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                            val cat = todasCategorias[position]
                            val txt = holder.itemView as TextView
                            txt.text = cat.nome
                            txt.setOnClickListener { exibirCanaisDaCategoria(cat.id, cat.nome) }
                        }
                        override fun getItemCount(): Int = todasCategorias.size
                    }

                    if (todasCategorias.isNotEmpty()) {
                        exibirCanaisDaCategoria(todasCategorias[0].id, todasCategorias[0].nome)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    findViewById<RelativeLayout>(R.id.loadingOverlay).visibility = View.GONE
                    Toast.makeText(this@TvActivity, "Erro ao conectar com a API de TV.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun exibirCanaisDaCategoria(catId: String, catNome: String) {
        tvTituloCategoria.text = catNome
        val canaisFiltrados = todosCanais.filter { it.categoryId == catId }

        recyclerCanaisGrid.adapter = CanalAdapter(
            listaCanais = canaisFiltrados,
            idsFavoritos = favoritosIds,
            onClick = { canalClicado ->
                val liveCats = mutableListOf<CategoriaItem>()
                liveCats.add(CategoriaItem("FAV", "Canais Favoritos"))
                liveCats.addAll(todasCategorias)
                
                DataHolder.todasCategorias = liveCats
                DataHolder.todosCanais = todosCanais
                DataHolder.favoritosIds = favoritosIds
                DataHolder.categoriaAtualId = catId
                
                val indice = canaisFiltrados.indexOf(canalClicado)
                val intentPlayer = Intent(this, PlayerTvActivity::class.java)
                intentPlayer.putExtra("INDICE_CANAL", indice)
                startActivity(intentPlayer)
            },
            onLongClick = { canalClicado ->
                if (favoritosIds.contains(canalClicado.id)) {
                    favoritosIds.remove(canalClicado.id)
                    Toast.makeText(this, "Removido dos Favoritos", Toast.LENGTH_SHORT).show()
                } else {
                    favoritosIds.add(canalClicado.id)
                    Toast.makeText(this, "Adicionado aos Favoritos!", Toast.LENGTH_SHORT).show()
                }

                db.collection("usuarios")
                    .whereEqualTo("usuario", username)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        if (!snapshot.isEmpty) {
                            val docId = snapshot.documents[0].id
                            db.collection("usuarios").document(docId).update("favoritos", favoritosIds)
                        }
                    }
                recyclerCanaisGrid.adapter?.notifyDataSetChanged()
            }
        )
    }
}
