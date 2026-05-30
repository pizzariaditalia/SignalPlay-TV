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
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
        // Grade com 2 colunas de canais de TV deitados
        recyclerCanaisGrid.layoutManager = GridLayoutManager(this, 2)

        val url = intent.getStringExtra("URL") ?: ""
        val user = intent.getStringExtra("USER") ?: ""
        val pass = intent.getStringExtra("PASS") ?: ""
        username = intent.getStringExtra("USERNAME") ?: ""

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Carrega favoritos atuais do Firebase
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

                // 2. Baixar Categorias
                val reqCat = Request.Builder().url("$url/player_api.php?username=$user&password=$pass&action=get_live_categories").build()
                val resCat = client.newCall(reqCat).execute()
                val jsonCat = resCat.body?.string() ?: "[]"
                if (jsonCat.startsWith("[")) {
                    val arr = JSONArray(jsonCat)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        todasCategorias.add(CategoriaItem(obj.optString("category_id"), obj.optString("category_name")))
                    }
                }

                // 3. Baixar Canais
                val reqLive = Request.Builder().url("$url/player_api.php?username=$user&password=$pass&action=get_live_streams").build()
                val resLive = client.newCall(reqLive).execute()
                val jsonLive = resLive.body?.string() ?: "[]"
                if (jsonLive.startsWith("[")) {
                    val arr = JSONArray(jsonLive)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optString("stream_id")
                        val nome = obj.optString("name")
                        val icone = obj.optString("stream_icon")
                        val catId = obj.optString("category_id")
                        val streamUrl = "$url/live/$user/$pass/$id.ts"
                        todosCanais.add(CanalItem(id, nome, icone, catId, streamUrl))
                    }
                }

                withContext(Dispatchers.Main) {
                    // Renderiza a barra lateral de categorias
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
                                exibirCanaisDaCategoria(cat.id, cat.nome)
                            }
                        }

                        override fun getItemCount(): Int = todasCategorias.size
                    }

                    // Abre a primeira categoria por padrão
                    if (todasCategorias.isNotEmpty()) {
                        exibirCanaisDaCategoria(todasCategorias[0].id, todasCategorias[0].nome)
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TvActivity, "Erro ao conectar com a API de TV.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun exibirCanaisDaCategoria(catId: String, catNome: String) {
        tvTituloCategoria.text = catNome
        val canaisFiltrados = todosCanais.filter { it.categoryId == catId }

        recyclerCanaisGrid.adapter = CanalAdapter(canaisFiltrados, favoritosIds) { canalClicado ->
            // LÓGICA DO CLIQUE LONGO: Alternar Favorito no Firebase
            if (favoritosIds.contains(canalClicado.id)) {
                favoritosIds.remove(canalClicado.id)
                Toast.makeText(this, "Removido dos Favoritos", Toast.LENGTH_SHORT).show()
            } else {
                favoritosIds.add(canalClicado.id)
                Toast.makeText(this, "Adicionado aos Favoritos!", Toast.LENGTH_SHORT).show()
            }

            // Sincroniza em tempo real com a Nuvem
            db.collection("usuarios")
                .whereEqualTo("usuario", username)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (!snapshot.isEmpty) {
                        val docId = snapshot.documents[0].id
                        db.collection("usuarios").document(docId).update("favoritos", favoritosIds)
                    }
                }

            // Atualiza a grade visual para acender ou apagar a estrela
            recyclerCanaisGrid.adapter?.notifyDataSetChanged()
        }
    }
}
