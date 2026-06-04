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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SeriesActivity : Activity() {

    private val todasCategorias = mutableListOf<CategoriaItem>()
    private val todasSeries = mutableListOf<FilmeItem>()

    private lateinit var recyclerCanaisGrid: RecyclerView
    private lateinit var tvTituloCategoria: TextView
    
    private var url = ""
    private var user = ""
    private var pass = ""
    private var username = ""

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
        username = intent.getStringExtra("USERNAME") ?: ""

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
                val isParentalActive = prefs.getBoolean("PARENTAL_CONTROL", false)
                val palavrasProibidas = listOf("adult", "+18", "18+", "xxx", "porn", "hachutv", "sensual", "sex", "playboy")

                val dao = AppDatabase.getDatabase(this@SeriesActivity).catalogoDao()

                val categoriasEntity = dao.getCategoriasPorTipo("series")
                val catMap = mutableMapOf<String, String>()
                
                for (cat in categoriasEntity) {
                    val catNameLower = cat.nome.lowercase()
                    if (isParentalActive && palavrasProibidas.any { catNameLower.contains(it) }) continue
                    
                    todasCategorias.add(CategoriaItem(cat.id, cat.nome))
                    catMap[cat.id] = cat.nome
                }

                val seriesEntity = dao.getTodasSeries()
                for (serie in seriesEntity) {
                    val catNameLower = catMap[serie.categoryId]?.lowercase() ?: ""
                    val nLower = serie.nome.lowercase()
                    
                    if (isParentalActive && (palavrasProibidas.any { catNameLower.contains(it) } || palavrasProibidas.any { nLower.contains(it) })) continue
                    
                    todasSeries.add(FilmeItem(
                        id = serie.id,
                        nome = serie.nome,
                        urlImagem = serie.urlImagem,
                        streamUrl = serie.streamUrl,
                        tipo = serie.tipo,
                        categoryId = serie.categoryId,
                        progresso = 0
                    ))
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
                            txt.setOnClickListener { exibirSeriesDaCategoria(cat.id, cat.nome) }
                        }
                        override fun getItemCount(): Int = todasCategorias.size
                    }

                    if (todasCategorias.isNotEmpty()) {
                        exibirSeriesDaCategoria(todasCategorias[0].id, todasCategorias[0].nome)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    findViewById<RelativeLayout>(R.id.loadingOverlay).visibility = View.GONE
                    Toast.makeText(this@SeriesActivity, "Erro ao carregar séries locais.", Toast.LENGTH_SHORT).show()
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
            intentDet.putExtra("USERNAME", username)
            intentDet.putExtra("MEDIA_ID", serieClicada.id)
            intentDet.putExtra("MEDIA_TIPO", serieClicada.tipo)
            intentDet.putExtra("MEDIA_NOME", serieClicada.nome)
            intentDet.putExtra("MEDIA_CAPA", serieClicada.urlImagem)
            startActivity(intentDet)
        }
    }
}