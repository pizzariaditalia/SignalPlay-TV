package com.signalplay.tv

import android.app.Activity
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        username = intent.getStringExtra("USERNAME") ?: ""

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Busca os favoritos no Firebase para manter sincronizado
                db.collection("usuarios")
                    .whereEqualTo("usuario", username)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        if (!snapshot.isEmpty) {
                            val favs = snapshot.documents[0].get("favoritos") as? List<*>
                            favs?.forEach { favoritosIds.add(it.toString()) }
                        }
                    }

                // 2. LÊ TUDO DO BANCO DE DADOS LOCAL (ROOM) - Ultra Rápido!
                val dao = AppDatabase.getDatabase(this@TvActivity).catalogoDao()
                
                val categoriasEntity = dao.getCategoriasPorTipo("live")
                todasCategorias.addAll(categoriasEntity.map { CategoriaItem(it.id, it.nome) })
                
                todasCategorias.sortBy { 
                    val n = it.nome.lowercase()
                    if (n.contains("jogos de hoje") || n.contains("casa do patrão") || n.contains("casa do patrao")) 1 else 0 
                }

                val canaisEntity = dao.getTodosCanais()
                for (canal in canaisEntity) {
                    if (canal.epgChannelId.isNotEmpty()) {
                        DataHolder.mapaEpgIds[canal.id] = canal.epgChannelId
                    }
                    todosCanais.add(CanalItem(canal.id, canal.nome, canal.urlImagem, canal.categoryId, canal.streamUrl))
                }

                // 3. Atualiza a tela
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
                    Toast.makeText(this@TvActivity, "Erro ao carregar canais do banco local.", Toast.LENGTH_SHORT).show()
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
