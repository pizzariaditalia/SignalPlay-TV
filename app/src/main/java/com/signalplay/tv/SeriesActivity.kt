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

class SeriesActivity : Activity() {

    private val todasCategorias = mutableListOf<CategoriaItem>()
    private val todasSeries = mutableListOf<FilmeItem>()

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

                val reqCat = Request.Builder().url("$url/player_api.php?username=$user&password=$pass&action=get_series_categories").build()
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

                val reqSeries = Request.Builder().url("$url/player_api.php?username=$user&password=$pass&action=get_series").build()
                val resSeries = client.newCall(reqSeries).execute()
                val jsonSeries = resSeries.body?.string() ?: "[]"
                
                if (jsonSeries.startsWith("[")) {
                    val arr = JSONArray(jsonSeries)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optString("series_id")
                        val nome = obj.optString("name")
                        val icone = obj.optString("cover") 
                        val catId = obj.optString("category_id")
                        val streamUrl = "$url/player_api.php?username=$user&password=$pass&action=get_series_info&series_id=$id"
                        
                        todasSeries.add(FilmeItem(id, nome, icone, streamUrl, "serie", catId))
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
                                exibirSeriesDaCategoria(cat.id, cat.nome)
                            }
                        }

                        override fun getItemCount(): Int = todasCategorias.size
                    }

                    if (todasCategorias.isNotEmpty()) {
                        exibirSeriesDaCategoria(todasCategorias[0].id, todasCategorias[0].nome)
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SeriesActivity, "Erro ao carregar séries.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun exibirSeriesDaCategoria(catId: String, catNome: String) {
        tvTituloCategoria.text = catNome
        val seriesFiltradas = todasSeries.filter { it.categoryId == catId }

        recyclerCanaisGrid.adapter = CardAdapter(seriesFiltradas) { serieClicada ->
            val intentDet = Intent(this, DetailsActivity::class.java)
            intentDet.putExtra("URL", url)
            intentDet.putExtra("USER", user)
            intentDet.putExtra("PASS", pass)
            intentDet.putExtra("MEDIA_ID", serieClicada.id)
            intentDet.putExtra("MEDIA_TIPO", serieClicada.tipo)
            intentDet.putExtra("MEDIA_NOME", serieClicada.nome)
            intentDet.putExtra("MEDIA_CAPA", serieClicada.urlImagem)
            startActivity(intentDet)
        }
    }
}
