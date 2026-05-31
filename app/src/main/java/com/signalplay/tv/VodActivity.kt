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

class VodActivity : Activity() {

    private val todasCategorias = mutableListOf<CategoriaItem>()
    private val todosFilmes = mutableListOf<FilmeItem>()
    private lateinit var db: FirebaseFirestore

    private lateinit var recyclerCanaisGrid: RecyclerView
    private lateinit var tvTituloCategoria: TextView
    
    private var url = ""
    private var user = ""
    private var pass = ""
    private var username = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv)

        db = FirebaseFirestore.getInstance()

        val recyclerCategories = findViewById<RecyclerView>(R.id.recyclerCategories)
        recyclerCanaisGrid = findViewById<RecyclerView>(R.id.recyclerCanaisGrid)
        tvTituloCategoria = findViewById<TextView>(R.id.tvTituloCategoria)

        recyclerCategories.layoutManager = LinearLayoutManager(this)
        recyclerCanaisGrid.layoutManager = GridLayoutManager(this, 5)

        url = intent.getStringExtra("URL") ?: ""
        user = intent.getStringExtra("USER") ?: ""
        pass = intent.getStringExtra("PASS") ?: ""
        username = intent.getStringExtra("USERNAME") ?: ""

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
                val isParentalActive = prefs.getBoolean("PARENTAL_CONTROL", false)
                val palavrasProibidas = listOf("adult", "+18", "18+", "xxx", "porn", "hachutv", "sensual", "sex")

                var historicoMap: Map<String, Map<String, Long>>? = null
                db.collection("usuarios").whereEqualTo("usuario", username).get()
                    .addOnSuccessListener { snapshot ->
                        if (!snapshot.isEmpty) {
                            val doc = snapshot.documents[0]
                            historicoMap = doc.get("historico_vod") as? Map<String, Map<String, Long>>
                        }
                    }
                
                withContext(Dispatchers.IO) { Thread.sleep(800) }

                fun getProgress(id: String): Int {
                    val progressoData = historicoMap?.get(id)
                    if (progressoData != null) {
                        val pos = progressoData["posicao"] ?: 0L
                        val dur = progressoData["duracao"] ?: 0L
                        if (dur > 0L) return ((pos.toDouble() / dur.toDouble()) * 100).toInt()
                    }
                    return 0
                }

                val client = OkHttpClient()

                val defCat = async { client.newCall(Request.Builder().url("$url/player_api.php?username=$user&password=$pass&action=get_vod_categories").build()).execute().body?.string() ?: "[]" }
                val defVod = async { client.newCall(Request.Builder().url("$url/player_api.php?username=$user&password=$pass&action=get_vod_streams").build()).execute().body?.string() ?: "[]" }

                val jsonCat = defCat.await()
                val jsonVod = defVod.await()
                
                if (jsonCat.startsWith("[")) {
                    val arr = JSONArray(jsonCat)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val catName = obj.optString("category_name", "")
                        val isAdult = palavrasProibidas.any { catName.lowercase().contains(it) }
                        if (!isParentalActive || !isAdult) todasCategorias.add(CategoriaItem(obj.optString("category_id"), catName))
                    }
                }
                
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
                        // INJETA A PORCENTAGEM
                        todosFilmes.add(FilmeItem(id, nome, icone, streamUrl, "filme", catId, getProgress(id)))
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
                            txt.setOnClickListener { exibirFilmesDaCategoria(cat.id, cat.nome) }
                        }
                        override fun getItemCount(): Int = todasCategorias.size
                    }

                    if (todasCategorias.isNotEmpty()) {
                        exibirFilmesDaCategoria(todasCategorias[0].id, todasCategorias[0].nome)
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    findViewById<RelativeLayout>(R.id.loadingOverlay).visibility = View.GONE
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
            intentDet.putExtra("USERNAME", username) // PASSA O USERNAME
            intentDet.putExtra("MEDIA_ID", filmeClicado.id)
            intentDet.putExtra("MEDIA_TIPO", filmeClicado.tipo)
            intentDet.putExtra("MEDIA_NOME", filmeClicado.nome)
            intentDet.putExtra("MEDIA_CAPA", filmeClicado.urlImagem)
            startActivity(intentDet)
        }
    }
}
