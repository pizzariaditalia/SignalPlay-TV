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
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class SearchItem(val id: String, val nome: String, val icone: String, val tipo: String, val urlStreamOrInfo: String)

class SearchActivity : Activity() {

    private val masterList = mutableListOf<SearchItem>()
    private val filteredList = mutableListOf<SearchItem>()
    private lateinit var adapter: SearchAdapter
    
    private var url = ""
    private var user = ""
    private var pass = ""
    private var username = ""

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

        CoroutineScope(Dispatchers.IO).launch {
            if (url.isEmpty() || !url.startsWith("http")) {
                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    tvStatus.text = "Erro: URL do servidor inválida."
                    Toast.makeText(this@SearchActivity, "Falha na conexão.", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            try {
                val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
                val filterSD = prefs.getBoolean("FILTER_SD", false)
                val filterH265 = prefs.getBoolean("FILTER_H265", false)
                val filter4K = prefs.getBoolean("FILTER_4K", false)

                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build()

                val reqLive = Request.Builder().url("$url/player_api.php?username=$user&password=$pass&action=get_live_streams").build()
                val reqVod = Request.Builder().url("$url/player_api.php?username=$user&password=$pass&action=get_vod_streams").build()
                val reqSeries = Request.Builder().url("$url/player_api.php?username=$user&password=$pass&action=get_series").build()

                val defLive = async { try { client.newCall(reqLive).execute().body?.string() ?: "[]" } catch (e: Exception) { "[]" } }
                val defVod = async { try { client.newCall(reqVod).execute().body?.string() ?: "[]" } catch (e: Exception) { "[]" } }
                val defSeries = async { try { client.newCall(reqSeries).execute().body?.string() ?: "[]" } catch (e: Exception) { "[]" } }

                val jsonLive = defLive.await()
                val jsonVod = defVod.await()
                val jsonSeries = defSeries.await()

                if (jsonLive.startsWith("[")) {
                    val arr = JSONArray(jsonLive)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val nome = obj.optString("name")

                        // MÁGICA: Filtros Agressivos na Pesquisa
                        val nUp = nome.uppercase()
                        val isSD = nUp.contains(" SD ") || nUp.endsWith(" SD") || nUp.startsWith("SD ") || nUp.contains("(SD)") || nUp.contains("[SD]") || nUp.contains("|SD|") || nUp.contains("- SD") || nUp == "SD"
                        val isH265 = nUp.contains("H265") || nUp.contains("HEVC") || nUp.contains("H.265")
                        val is4K = nUp.contains(" 4K ") || nUp.endsWith(" 4K") || nUp.startsWith("4K ") || nUp.contains("(4K)") || nUp.contains("[4K]") || nUp.contains("|4K|") || nUp.contains("- 4K") || nUp.contains("UHD") || nUp == "4K"
                        
                        if (filterSD && isSD) continue
                        if (filterH265 && isH265) continue
                        if (filter4K && is4K) continue

                        masterList.add(SearchItem(obj.optString("stream_id"), nome, obj.optString("stream_icon"), "TV", "$url/live/$user/$pass/${obj.optString("stream_id")}.ts"))
                    }
                }
                if (jsonVod.startsWith("[")) {
                    val arr = JSONArray(jsonVod)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val ext = obj.optString("container_extension", "mp4")
                        masterList.add(SearchItem(obj.optString("stream_id"), obj.optString("name"), obj.optString("stream_icon"), "FILME", "$url/movie/$user/$pass/${obj.optString("stream_id")}.$ext"))
                    }
                }
                if (jsonSeries.startsWith("[")) {
                    val arr = JSONArray(jsonSeries)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        masterList.add(SearchItem(obj.optString("series_id"), obj.optString("name"), obj.optString("cover"), "SÉRIE", "$url/player_api.php?username=$user&password=$pass&action=get_series_info&series_id=${obj.optString("series_id")}"))
                    }
                }

                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    tvStatus.text = "Busque em nosso acervo de ${masterList.size} conteúdos!"
                    edtSearch.requestFocus()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    tvStatus.text = "Erro ao carregar catálogo."
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
}
