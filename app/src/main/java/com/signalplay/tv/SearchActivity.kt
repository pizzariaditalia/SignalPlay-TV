package com.signalplay.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchItem(val id: String, val nome: String, val icone: String, val tipo: String, val urlStreamOrInfo: String)

class SearchActivity : Activity() {

    private val masterList = mutableListOf<SearchItem>()
    private val filteredList = mutableListOf<SearchItem>()
    private lateinit var adapter: SearchAdapter
    
    private var url = ""
    private var user = ""
    private var pass = ""
    private var username = ""

    // CORREÇÃO: Job para proteger a memória da Activity
    private val activityJob = Job()
    private val activityScope = CoroutineScope(Dispatchers.IO + activityJob)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        url = intent.getStringExtra("URL") ?: ""
        user = intent.getStringExtra("USER") ?: ""
        pass = intent.getStringExtra("PASS") ?: ""
        username = intent.getStringExtra("USERNAME") ?: ""

        val edtSearch = findViewById<EditText>(R.id.edtSearch)
        val recycler = findViewById<RecyclerView>(R.id.recyclerSearchResults)
        val progress = findViewById<ProgressBar>(R.id.progressSearch)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        recycler.layoutManager = GridLayoutManager(this, 6)
        
        adapter = SearchAdapter(filteredList) { itemClicado ->
            if (itemClicado.tipo == "TV") {
                val canaisTv = masterList.filter { it.tipo == "TV" }.map { CanalItem(it.id, it.nome, it.icone, "PESQUISA", it.urlStreamOrInfo) }
                DataHolder.todasCategorias = listOf(CategoriaItem("PESQUISA", "Resultado da Pesquisa"))
                DataHolder.todosCanais = canaisTv
                DataHolder.categoriaAtualId = "PESQUISA"
                
                val intentTv = Intent(this, PlayerTvActivity::class.java)
                intentTv.putExtra("INDICE_CANAL", canaisTv.indexOfFirst { it.id == itemClicado.id })
                startActivity(intentTv)
                
            } else {
                val intentDet = Intent(this, DetailsActivity::class.java)
                intentDet.putExtra("URL", url)
                intentDet.putExtra("USER", user)
                intentDet.putExtra("PASS", pass)
                intentDet.putExtra("USERNAME", username)
                intentDet.putExtra("MEDIA_ID", itemClicado.id)
                intentDet.putExtra("MEDIA_TIPO", if (itemClicado.tipo == "FILME") "filme" else "serie")
                intentDet.putExtra("MEDIA_NOME", itemClicado.nome)
                intentDet.putExtra("MEDIA_CAPA", itemClicado.icone)
                startActivity(intentDet)
            }
        }
        recycler.adapter = adapter

        activityScope.launch {
            try {
                val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
                val isParentalActive = prefs.getBoolean("PARENTAL_CONTROL", false)
                val filterSD = prefs.getBoolean("FILTER_SD", false)
                val filterHD = prefs.getBoolean("FILTER_HD", false)
                val filterFHD = prefs.getBoolean("FILTER_FHD", false)
                val filterH265 = prefs.getBoolean("FILTER_H265", false)
                val filter4K = prefs.getBoolean("FILTER_4K", false)

                val dao = AppDatabase.getDatabase(this@SearchActivity).catalogoDao()
                
                val mapLiveCats = dao.getCategoriasPorTipo("live").associate { it.id to it.nome }
                val mapVodCats = dao.getCategoriasPorTipo("vod").associate { it.id to it.nome }
                val mapSeriesCats = dao.getCategoriasPorTipo("series").associate { it.id to it.nome }

                val canais = dao.getTodosCanais()
                val filmes = dao.getTodosFilmes()
                val series = dao.getTodasSeries()

                for (c in canais) {
                    val nomeCategoria = mapLiveCats[c.categoryId] ?: ""
                    // Filtro inteligente e compacto
                    if (ContentFilterUtils.isContentBlocked(c.nome, nomeCategoria, isParentalActive, filterSD, filterHD, filterFHD, filterH265, filter4K)) continue
                    masterList.add(SearchItem(c.id, c.nome, c.urlImagem, "TV", c.streamUrl))
                }
                
                for (f in filmes) {
                    val nomeCategoria = mapVodCats[f.categoryId] ?: ""
                    if (ContentFilterUtils.isContentBlocked(f.nome, nomeCategoria, isParentalActive, false, false, false, false, false)) continue
                    masterList.add(SearchItem(f.id, f.nome, f.urlImagem, "FILME", f.streamUrl))
                }
                
                for (s in series) {
                    val nomeCategoria = mapSeriesCats[s.categoryId] ?: ""
                    if (ContentFilterUtils.isContentBlocked(s.nome, nomeCategoria, isParentalActive, false, false, false, false, false)) continue
                    masterList.add(SearchItem(s.id, s.nome, s.urlImagem, "SÉRIE", s.streamUrl))
                }

                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    tvStatus.text = "Busque em nosso acervo de ${masterList.size} conteúdos!"
                    edtSearch.requestFocus()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    tvStatus.text = "Erro ao carregar catálogo local."
                }
            }
        }

        edtSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().lowercase()
                filteredList.clear()
                
                if (query.length >= 2) {
                    filteredList.addAll(masterList.filter { it.nome.lowercase().contains(query) }.take(60))
                }
                
                adapter.notifyDataSetChanged()
                if (filteredList.isEmpty() && query.length >= 2) {
                    tvStatus.text = "Nenhum resultado encontrado."
                    tvStatus.visibility = View.VISIBLE
                } else {
                    tvStatus.visibility = View.GONE
                }
            }
        })
    }

    inner class SearchAdapter(
        private val list: List<SearchItem>,
        private val onClick: (SearchItem) -> Unit
    ) : RecyclerView.Adapter<SearchAdapter.SearchViewHolder>() {

        inner class SearchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val img = view.findViewById<ImageView>(R.id.cardImage)
            val title = view.findViewById<TextView>(R.id.cardTitle)
            val tag = view.findViewById<TextView>(R.id.cardTag)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_search_card, parent, false)
            return SearchViewHolder(v)
        }

        override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
            val item = list[position]
            holder.title.text = item.nome
            holder.tag.text = item.tipo
            
            if (item.tipo == "TV") holder.tag.setBackgroundColor(android.graphics.Color.parseColor("#0091EA"))
            else if (item.tipo == "FILME") holder.tag.setBackgroundColor(android.graphics.Color.parseColor("#FFC107"))
            else holder.tag.setBackgroundColor(android.graphics.Color.parseColor("#E50914"))

            val options = RequestOptions().transform(CenterCrop(), RoundedCorners(8))
            Glide.with(holder.itemView.context).load(item.icone).apply(options).into(holder.img)

            holder.itemView.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.bringToFront()
                    v.animate().scaleX(1.10f).scaleY(1.10f).translationZ(10f).setDuration(150).start()
                } else {
                    v.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(150).start()
                }
            }
            holder.itemView.setOnClickListener { onClick(item) }
        }
        override fun getItemCount(): Int = list.size
    }

    override fun onDestroy() {
        super.onDestroy()
        activityJob.cancel()
    }
}
