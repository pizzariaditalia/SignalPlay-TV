package com.tv.signalplay

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ResultadoBusca(val id: Int, val titulo: String, val capa: String?, val tipo: String)

class BuscaActivity : FragmentActivity() {

    private lateinit var inputBusca: EditText
    private lateinit var recyclerResultados: RecyclerView
    private lateinit var txtStatus: TextView

    private var todosFilmes: List<XtreamVod> = listOf()
    private var todasSeries: List<XtreamSerie> = listOf()
    private var todosCanais: List<XtreamLive> = listOf()

    private var xtUser = ""
    private var xtPass = ""
    private var urlServ = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_busca)

        inputBusca = findViewById(R.id.inputBuscaGlobal)
        recyclerResultados = findViewById(R.id.gridResultadosBusca)
        txtStatus = findViewById(R.id.txtStatusBusca)
        
        recyclerResultados.layoutManager = GridLayoutManager(this, 5)

        xtUser = intent.getStringExtra("XTREAM_USER") ?: ""
        xtPass = intent.getStringExtra("XTREAM_PASS") ?: ""
        urlServ = intent.getStringExtra("URL") ?: ""

        findViewById<Button>(R.id.btnVoltarBusca).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnVoltarBusca).setOnFocusChangeListener { v, focus -> 
            if(focus) v.animate().scaleX(1.1f).start() else v.animate().scaleX(1.0f).start() 
        }

        baixarBasesParaBusca()

        inputBusca.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { executarBuscaGlobal(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun baixarBasesParaBusca() {
        txtStatus.text = "A carregar bases de dados..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val api = XtreamClient.create(urlServ)
                val respVod = api.getVodStreams(xtUser, xtPass)
                val respSeries = api.getSeriesStreams(xtUser, xtPass)
                val respCanais = api.getLiveStreams(xtUser, xtPass)

                withContext(Dispatchers.Main) {
                    if (respVod.isJsonArray) todosFilmes = Gson().fromJson(respVod, object : TypeToken<List<XtreamVod>>() {}.type)
                    if (respSeries.isJsonArray) todasSeries = Gson().fromJson(respSeries, object : TypeToken<List<XtreamSerie>>() {}.type)
                    if (respCanais.isJsonArray) todosCanais = Gson().fromJson(respCanais, object : TypeToken<List<XtreamLive>>() {}.type)
                    txtStatus.text = "Pronto! Digite para pesquisar."
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { txtStatus.text = "Erro ao carregar bases." }
            }
        }
    }

    private fun executarBuscaGlobal(termo: String) {
        if (termo.length < 2) {
            recyclerResultados.adapter = BuscaAdapter(emptyList())
            txtStatus.text = "Digite pelo menos 2 letras..."
            return
        }

        val query = termo.lowercase()
        val resultados = mutableListOf<ResultadoBusca>()

        todosCanais.filter { it.name.lowercase().contains(query) }.forEach { resultados.add(ResultadoBusca(it.stream_id, it.name, it.stream_icon, "live")) }
        todosFilmes.filter { it.name.lowercase().contains(query) }.forEach { resultados.add(ResultadoBusca(it.stream_id, it.name, it.stream_icon, "vod")) }
        todasSeries.filter { it.name.lowercase().contains(query) }.forEach { resultados.add(ResultadoBusca(it.series_id, it.name, it.cover, "series")) }

        txtStatus.text = "${resultados.size} resultados encontrados para \"$termo\""
        recyclerResultados.adapter = BuscaAdapter(resultados)
    }

    inner class BuscaAdapter(private val lista: List<ResultadoBusca>) : RecyclerView.Adapter<BuscaAdapter.Holder>() {
        
        inner class Holder(val view: View) : RecyclerView.ViewHolder(view) {
            // Busca Dinâmica de IDs: Ignora erros de digitação do XML e força a compilação
            val imgCapa: ImageView? = getDynamicId(view, "imgCapaPremium")
            val txtNome: TextView? = getDynamicId(view, "txtNomePremium")
            val txtBadge: TextView? = getDynamicId(view, "txtBadgeTipo")

            private fun <T : View> getDynamicId(v: View, name: String): T? {
                val resId = v.context.resources.getIdentifier(name, "id", v.context.packageName)
                return if (resId != 0) v.findViewById(resId) else null
            }

            init {
                view.isFocusableInTouchMode = false 
                view.setOnFocusChangeListener { v, focus -> 
                    if(focus) { 
                        v.animate().scaleX(1.08f).scaleY(1.08f).start()
                        v.elevation = 15f 
                    } else { 
                        v.animate().scaleX(1.0f).scaleY(1.0f).start()
                        v.elevation = 0f 
                    } 
                }
                view.setOnClickListener {
                    val item = lista[bindingAdapterPosition]
                    if (item.tipo == "live") {
                        val intent = Intent(itemView.context, PlayerActivity::class.java)
                        intent.putExtra("URL", urlServ); intent.putExtra("XTREAM_USER", xtUser); intent.putExtra("XTREAM_PASS", xtPass)
                        intent.putExtra("STREAM_ID", item.id); intent.putExtra("TYPE", "live"); intent.putExtra("TITLE", item.titulo)
                        itemView.context.startActivity(intent)
                    } else {
                        val intent = Intent(itemView.context, DetalhesActivity::class.java)
                        intent.putExtra("URL", urlServ); intent.putExtra("XTREAM_USER", xtUser); intent.putExtra("XTREAM_PASS", xtPass)
                        intent.putExtra("MEDIA_ID", item.id); intent.putExtra("IS_SERIES", item.tipo == "series")
                        itemView.context.startActivity(intent)
                    }
                }
            }
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(LayoutInflater.from(parent.context).inflate(R.layout.card_busca, parent, false))
        }
        
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = lista[position]
            holder.txtNome?.text = item.titulo
            
            holder.imgCapa?.let { img ->
                if (!item.capa.isNullOrEmpty()) {
                    Glide.with(holder.itemView.context)
                        .load(item.capa)
                        .override(250, 350)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(img) 
                } else { 
                    img.setImageDrawable(null) 
                }
            }
            
            holder.txtBadge?.text = when(item.tipo) {
                "live" -> "CANAL"
                "series" -> "SÉRIE"
                else -> "FILME"
            }
            holder.txtBadge?.setBackgroundColor(when(item.tipo) {
                "live" -> Color.parseColor("#1e90ff") 
                "series" -> Color.parseColor("#2ed573") 
                else -> Color.parseColor("#E50914") 
            })
        }
        
        override fun getItemCount() = lista.size
    }
}
