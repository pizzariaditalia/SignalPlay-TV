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
                val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
                val isParentalActive = prefs.getBoolean("PARENTAL_CONTROL", false)
                val filterSD = prefs.getBoolean("FILTER_SD", false)
                val filterHD = prefs.getBoolean("FILTER_HD", false)
                val filterFHD = prefs.getBoolean("FILTER_FHD", false)
                val filterH265 = prefs.getBoolean("FILTER_H265", false)
                val filter4K = prefs.getBoolean("FILTER_4K", false)
                val palavrasProibidas = listOf("adult", "+18", "18+", "xxx", "porn", "hachutv", "sensual", "sex", "playboy")

                db.collection("usuarios").whereEqualTo("usuario", username).get()
                    .addOnSuccessListener { snapshot ->
                        if (!snapshot.isEmpty) {
                            val favs = snapshot.documents[0].get("favoritos") as? List<*>
                            favs?.forEach { favoritosIds.add(it.toString()) }
                        }
                    }

                val dao = AppDatabase.getDatabase(this@TvActivity).catalogoDao()
                val categoriasEntity = dao.getCategoriasPorTipo("live")
                val catMap = mutableMapOf<String, String>()
                
                for (cat in categoriasEntity) {
                    val catNameLower = cat.nome.lowercase()
                    if (isParentalActive && palavrasProibidas.any { catNameLower.contains(it) }) continue
                    
                    todasCategorias.add(CategoriaItem(cat.id, cat.nome))
                    catMap[cat.id] = cat.nome
                }
                
                todasCategorias.sortBy { 
                    val n = it.nome.lowercase()
                    if (n.contains("jogos de hoje") || n.contains("casa do patrão") || n.contains("casa do patrao")) 1 else 0 
                }

                val canaisEntity = dao.getTodosCanais()
                for (canal in canaisEntity) {
                    val catNameLower = catMap[canal.categoryId]?.lowercase() ?: ""
                    val nLower = canal.nome.lowercase()
                    
                    if (isParentalActive && (palavrasProibidas.any { catNameLower.contains(it) } || palavrasProibidas.any { nLower.contains(it) })) continue
                    
                    val nUp = canal.nome.uppercase()
                    val isExplicitSD = nUp.contains(" SD ") || nUp.endsWith(" SD") || nUp.startsWith("SD ") || nUp.contains("(SD)") || nUp.contains("[SD]") || nUp.contains("|SD|") || nUp.contains("- SD") || nUp == "SD"
                    val hasFHD = nUp.contains(" FHD ") || nUp.endsWith(" FHD") || nUp.startsWith("FHD ") || nUp.contains("(FHD)") || nUp.contains("[FHD]") || nUp.contains("|FHD|") || nUp.contains("- FHD") || nUp == "FHD"
                    val hasHD = (nUp.contains(" HD ") || nUp.endsWith(" HD") || nUp.startsWith("HD ") || nUp.contains("(HD)") || nUp.contains("[HD]") || nUp.contains("|HD|") || nUp.contains("- HD") || nUp == "HD") && !hasFHD
                    val has4K = nUp.contains(" 4K ") || nUp.endsWith(" 4K") || nUp.startsWith("4K ") || nUp.contains("(4K)") || nUp.contains("[4K]") || nUp.contains("|4K|") || nUp.contains("- 4K") || nUp.contains("UHD") || nUp == "4K"
                    val hasH265 = nUp.contains("H265") || nUp.contains("HEVC") || nUp.contains("H.265")
                    
                    var shouldHide = false
                    if (filterSD) {
                        if (isExplicitSD) shouldHide = true
                        else if (!hasHD && !hasFHD && !has4K && !hasH265) {
                            val isSafeCat = catNameLower.contains("24h") || catNameLower.contains("24 horas") || catNameLower.contains("infantil") || catNameLower.contains("kids") || catNameLower.contains("desenho") || catNameLower.contains("religi") || catNameLower.contains("notícia") || catNameLower.contains("news") || catNameLower.contains("document") || catNameLower.contains("educa") || catNameLower.contains("música") || catNameLower.contains("rádio")
                            if (!isSafeCat) shouldHide = true
                        }
                    }
                    if (filterHD && hasHD) shouldHide = true
                    if (filterFHD && hasFHD) shouldHide = true
                    if (filterH265 && hasH265) shouldHide = true
                    if (filter4K && has4K) shouldHide = true
                    
                    if (shouldHide) continue

                    if (canal.epgChannelId.isNotEmpty()) DataHolder.mapaEpgIds[canal.id] = canal.epgChannelId
                    todosCanais.add(CanalItem(canal.id, canal.nome, canal.urlImagem, canal.categoryId, canal.streamUrl))
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
                    if (todasCategorias.isNotEmpty()) exibirCanaisDaCategoria(todasCategorias[0].id, todasCategorias[0].nome)
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

        recyclerCanaisGrid.adapter = CanalAdapter(canaisFiltrados, favoritosIds, { canalClicado ->
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
        }, { canalClicado ->
            if (favoritosIds.contains(canalClicado.id)) {
                favoritosIds.remove(canalClicado.id)
                Toast.makeText(this, "Removido dos Favoritos", Toast.LENGTH_SHORT).show()
            } else {
                favoritosIds.add(canalClicado.id)
                Toast.makeText(this, "Adicionado aos Favoritos!", Toast.LENGTH_SHORT).show()
            }
            db.collection("usuarios").whereEqualTo("usuario", username).get().addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val docId = snapshot.documents[0].id
                    db.collection("usuarios").document(docId).update("favoritos", favoritosIds)
                }
            }
            recyclerCanaisGrid.adapter?.notifyDataSetChanged()
        })
    }
}