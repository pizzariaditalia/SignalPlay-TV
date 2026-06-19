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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    // CORREÇÃO: Job para proteger a memória da Activity
    private val activityJob = Job()
    private val activityScope = CoroutineScope(Dispatchers.IO + activityJob)

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

        activityScope.launch {
            try {
                val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
                val isParentalActive = prefs.getBoolean("PARENTAL_CONTROL", false)

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

                val dao = AppDatabase.getDatabase(this@VodActivity).catalogoDao()
                val categoriasEntity = dao.getCategoriasPorTipo("vod")
                val catMap = mutableMapOf<String, String>()
                
                for (cat in categoriasEntity) {
                    // CORREÇÃO: Utilizando a nova classe utilitária (Filmes não filtram resolução pelo nome, só parental)
                    if (ContentFilterUtils.isContentBlocked("", cat.nome, isParentalActive, false, false, false, false, false)) continue
                    
                    todasCategorias.add(CategoriaItem(cat.id, cat.nome))
                    catMap[cat.id] = cat.nome
                }

                val filmesEntity = dao.getTodosFilmes()
                for (filme in filmesEntity) {
                    val catName = catMap[filme.categoryId] ?: ""
                    
                    // CORREÇÃO: Filtro compacto
                    if (ContentFilterUtils.isContentBlocked(filme.nome, catName, isParentalActive, false, false, false, false, false)) continue
                    
                    todosFilmes.add(FilmeItem(
                        id = filme.id,
                        nome = filme.nome,
                        urlImagem = filme.urlImagem,
                        streamUrl = filme.streamUrl,
                        tipo = filme.tipo,
                        categoryId = filme.categoryId,
                        progresso = getProgress(filme.id)
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
                    Toast.makeText(this@VodActivity, "Erro ao carregar filmes locais.", Toast.LENGTH_SHORT).show()
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
            intentDet.putExtra("USERNAME", username) 
            intentDet.putExtra("MEDIA_ID", filmeClicado.id)
            intentDet.putExtra("MEDIA_TIPO", filmeClicado.tipo)
            intentDet.putExtra("MEDIA_NOME", filmeClicado.nome)
            intentDet.putExtra("MEDIA_CAPA", filmeClicado.urlImagem)
            startActivity(intentDet)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityJob.cancel()
    }
}
