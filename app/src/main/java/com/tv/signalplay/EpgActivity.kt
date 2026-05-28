package com.tv.signalplay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EpgActivity : FragmentActivity() {

    private lateinit var rvEpgChannels: RecyclerView
    private lateinit var epgLoading: ProgressBar
    
    private var xtUser = ""; private var xtPass = ""; private var urlServ = ""
    private var isParentalOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_epg)

        xtUser = intent.getStringExtra("XTREAM_USER") ?: ""
        xtPass = intent.getStringExtra("XTREAM_PASS") ?: ""
        urlServ = intent.getStringExtra("URL") ?: ""

        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
        isParentalOn = prefs.getBoolean("parental_control", false)

        rvEpgChannels = findViewById(R.id.rvEpgChannels)
        epgLoading = findViewById(R.id.epgLoading)
        rvEpgChannels.layoutManager = LinearLayoutManager(this)

        val sdf = SimpleDateFormat("EEEE, dd 'de' MMMM", Locale("pt", "BR"))
        findViewById<TextView>(R.id.txtEpgDate).text = sdf.format(Date()).uppercase()

        carregarGradeEpg()
    }

    private fun isAdult(cat: String?): Boolean {
        if (!isParentalOn) return false
        val c = cat?.lowercase() ?: ""
        return listOf("adulto", "adult", "18+", "xxx", "porn", "sensual", "hachutv").any { c.contains(it) }
    }

    private fun carregarGradeEpg() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Carrega os Canais
                val api = XtreamClient.create(urlServ)
                val respCanais = api.getLiveStreams(xtUser, xtPass)
                val respCat = api.getLiveCategories(xtUser, xtPass)
                
                val mapCategorias = mutableMapOf<String, String>()
                if (respCat.isJsonArray) { Gson().fromJson<List<XtreamCategory>>(respCat, object : TypeToken<List<XtreamCategory>>() {}.type).forEach { mapCategorias[it.category_id] = it.category_name } }
                
                var canaisValidos = emptyList<XtreamLive>()
                if (respCanais.isJsonArray) {
                    val brutos: List<XtreamLive> = Gson().fromJson(respCanais, object : TypeToken<List<XtreamLive>>() {}.type)
                    brutos.forEach { it.category_name = mapCategorias[it.category_id ?: ""] ?: "Outros" }
                    canaisValidos = brutos.filter { !isAdult(it.category_name) }.take(100) // Pega os primeiros 100 pra não explodir a RAM
                }

                // 2. Lê o EPG Offline do arquivo
                var epgMap: Map<String, List<XtreamEpgListing>> = emptyMap()
                val file = File(filesDir, "epg_offline.json")
                if (file.exists()) {
                    try { epgMap = Gson().fromJson(file.readText(), object : TypeToken<Map<String, List<XtreamEpgListing>>>(){}.type) } catch (e: Exception) {}
                }

                // 3. Junta os dados
                val gradeFinal = mutableListOf<CanalComEpg>()
                val agora = System.currentTimeMillis() / 1000

                for (canal in canaisValidos) {
                    val idEpg = canal.epg_channel_id ?: ""
                    val progList = epgMap[idEpg] ?: emptyList()
                    
                    // Filtra apenas programas de agora para frente (ignora o passado)
                    val progFuturos = progList.filter { (it.stop_timestamp?.toLong() ?: 0L) > agora }.sortedBy { it.start_timestamp?.toLong() ?: 0L }
                    gradeFinal.add(CanalComEpg(canal, progFuturos))
                }

                withContext(Dispatchers.Main) {
                    epgLoading.visibility = View.GONE
                    rvEpgChannels.adapter = ChannelRowAdapter(gradeFinal)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) { epgLoading.visibility = View.GONE }
            }
        }
    }

    private fun decodificarBase64(s: String?): String = try { String(Base64.decode(s?.trim() ?: "", Base64.DEFAULT)) } catch (e: Exception) { s ?: "Programa Local" }
    
    data class CanalComEpg(val canal: XtreamLive, val programas: List<XtreamEpgListing>)

    // Adapter Vertical (Linhas dos Canais)
    inner class ChannelRowAdapter(private val lista: List<CanalComEpg>) : RecyclerView.Adapter<ChannelRowAdapter.Holder>() {
        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val imgLogo: ImageView = view.findViewById(R.id.imgEpgChannelLogo)
            val rvPrograms: RecyclerView = view.findViewById(R.id.rvEpgPrograms)
            init { rvPrograms.layoutManager = LinearLayoutManager(view.context, RecyclerView.HORIZONTAL, false) }
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_epg_row, parent, false))
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = lista[position]
            if (!item.canal.stream_icon.isNullOrEmpty()) {
                Glide.with(holder.itemView.context).load(item.canal.stream_icon).override(150, 150).diskCacheStrategy(DiskCacheStrategy.ALL).into(holder.imgLogo)
            } else { holder.imgLogo.setImageDrawable(null) }

            // Injeta a lista de programas horizontais
            holder.rvPrograms.adapter = ProgramAdapter(item.programas, item.canal)
        }
        override fun getItemCount() = lista.size
    }

    // Adapter Horizontal (Cards de Programas)
    inner class ProgramAdapter(private val programas: List<XtreamEpgListing>, val canalPai: XtreamLive) : RecyclerView.Adapter<ProgramAdapter.Holder>() {
        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val txtHora: TextView = view.findViewById(R.id.txtProgTime)
            val txtTitulo: TextView = view.findViewById(R.id.txtProgTitle)
            init {
                view.isFocusable = true
                view.isFocusableInTouchMode = false
                view.setOnFocusChangeListener { v, focus -> if(focus) { v.animate().scaleX(1.05f).scaleY(1.05f).start(); v.elevation=10f } else { v.animate().scaleX(1.0f).scaleY(1.0f).start(); v.elevation=0f } }
                
                // Clicou no programa? Vai pro player do canal!
                view.setOnClickListener {
                    val intentPlayer = Intent(itemView.context, PlayerActivity::class.java)
                    intentPlayer.putExtra("URL", urlServ); intentPlayer.putExtra("XTREAM_USER", xtUser); intentPlayer.putExtra("XTREAM_PASS", xtPass)
                    intentPlayer.putExtra("STREAM_ID", canalPai.stream_id); intentPlayer.putExtra("TYPE", "live"); intentPlayer.putExtra("TITLE", canalPai.name)
                    itemView.context.startActivity(intentPlayer)
                }
            }
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_epg_program, parent, false))
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val prog = programas[position]
            holder.txtTitulo.text = decodificarBase64(prog.title)
            
            val fI = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val fO = SimpleDateFormat("HH:mm", Locale.getDefault())
            try { 
                val st = fI.parse(prog.start_timestamp ?: "")
                val en = fI.parse(prog.stop_timestamp ?: "")
                if(st != null && en != null) holder.txtHora.text = "${fO.format(st)} - ${fO.format(en)}" 
            } catch(ex: Exception) { 
                try { 
                    val dF = java.util.Date((prog.start_timestamp?.toLong() ?: 0L) * 1000)
                    val dT = java.util.Date((prog.stop_timestamp?.toLong() ?: 0L) * 1000)
                    holder.txtHora.text = "${fO.format(dF)} - ${fO.format(dT)}" 
                } catch(e2: Exception){} 
            }
            
            // Destaque visual: O programa "AGORA" (índice 0) ganha texto vermelho no horário
            if (position == 0) {
                holder.txtHora.text = "AGORA • ${holder.txtHora.text}"
                holder.txtHora.setTextColor(Color.parseColor("#ff4757"))
            } else {
                holder.txtHora.setTextColor(Color.parseColor("#ffcc00"))
            }
        }
        // Se o canal não tem EPG, mostra um card vazio genérico para poder clicar e assistir
        override fun getItemCount() = if (programas.isEmpty()) 1 else programas.size
    }
}
