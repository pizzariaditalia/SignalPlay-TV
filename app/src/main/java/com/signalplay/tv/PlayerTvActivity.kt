package com.signalplay.tv

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DataHolder {
    var todasCategorias: List<CategoriaItem> = emptyList()
    var todosCanais: List<CanalItem> = emptyList()
    var favoritosIds: List<String> = emptyList()
    var categoriaAtualId: String = ""
    var canaisFiltrados: List<CanalItem> = emptyList()
}

// ADAPTADOR DO EPG EMBUTIDO AQUI PARA FACILITAR (ATUALIZADO PARA SUPORTAR O NOVO VISUAL)
data class EpgItem(val titulo: String, val horario: String, val duracao: String, val isAgora: Boolean, val textColor: String)

class EpgAdapter(private val lista: List<EpgItem>) : RecyclerView.Adapter<EpgAdapter.EpgViewHolder>() {
    class EpgViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitulo: TextView = view.findViewById(R.id.epgTitulo)
        val tvHorario: TextView = view.findViewById(R.id.epgHorario)
        val tvDuracao: TextView = view.findViewById(R.id.epgDuracao)
        val indAgora: View = view.findViewById(R.id.epgIndAgora)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpgViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_epg, parent, false)
        return EpgViewHolder(view)
    }
    override fun onBindViewHolder(holder: EpgViewHolder, position: Int) {
        val item = lista[position]
        holder.tvTitulo.text = item.titulo
        holder.tvHorario.text = item.horario
        holder.tvHorario.setTextColor(Color.parseColor(item.textColor))
        holder.tvDuracao.text = item.duracao
        holder.indAgora.visibility = if (item.isAgora) View.VISIBLE else View.INVISIBLE

        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) v.setBackgroundColor(Color.parseColor("#222222"))
            else v.setBackgroundColor(Color.TRANSPARENT)
        }
    }
    override fun getItemCount() = lista.size
}


class PlayerTvActivity : Activity() {

    private var exoPlayer: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var progressBar: ProgressBar
    
    // Elementos do Novo OSD (Banner do Player)
    private lateinit var osdContainer: LinearLayout
    private lateinit var osdLogo: ImageView
    private lateinit var osdChannelName: TextView
    private lateinit var osdCurrentProgram: TextView
    private lateinit var osdNextProgram: TextView
    private lateinit var osdProgressContainer: LinearLayout
    private lateinit var osdTimeStart: TextView
    private lateinit var osdTimeEnd: TextView
    private lateinit var osdProgressBar: ProgressBar
    
    private lateinit var painelCanais: LinearLayout
    private lateinit var recyclerPainelCanais: RecyclerView
    private lateinit var tvPainelTitulo: TextView
    
    private lateinit var painelEpg: LinearLayout
    private lateinit var recyclerPainelEpg: RecyclerView
    private var listaEpgAtual: MutableList<EpgItem> = mutableListOf()

    private var indiceCanalAtual: Int = 0
    private var indiceCategoriaAtual: Int = 0
    private val handlerOSD = Handler(Looper.getMainLooper())
    private val osdRunnable = Runnable { osdContainer.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player_tv)

        playerView = findViewById(R.id.playerView)
        progressBar = findViewById(R.id.progressBar)
        
        osdContainer = findViewById(R.id.osdContainer)
        osdLogo = findViewById(R.id.osdLogo)
        osdChannelName = findViewById(R.id.osdChannelName)
        osdCurrentProgram = findViewById(R.id.osdCurrentProgram)
        osdNextProgram = findViewById(R.id.osdNextProgram)
        osdProgressContainer = findViewById(R.id.osdProgressContainer)
        osdTimeStart = findViewById(R.id.osdTimeStart)
        osdTimeEnd = findViewById(R.id.osdTimeEnd)
        osdProgressBar = findViewById(R.id.osdProgressBar)
        
        painelCanais = findViewById(R.id.painelCanais)
        recyclerPainelCanais = findViewById(R.id.recyclerPainelCanais)
        tvPainelTitulo = findViewById(R.id.tvPainelTitulo)
        
        painelEpg = findViewById(R.id.painelEpg)
        recyclerPainelEpg = findViewById(R.id.recyclerPainelEpg)

        recyclerPainelCanais.layoutManager = LinearLayoutManager(this)
        recyclerPainelEpg.layoutManager = LinearLayoutManager(this)
        
        indiceCategoriaAtual = DataHolder.todasCategorias.indexOfFirst { it.id == DataHolder.categoriaAtualId }
        if (indiceCategoriaAtual == -1) indiceCategoriaAtual = 0
        
        carregarCanaisDaCategoria()
        
        indiceCanalAtual = intent.getIntExtra("INDICE_CANAL", 0)

        inicializarPlayer()
        iniciarCanal()
    }

    private fun carregarCanaisDaCategoria() {
        if (DataHolder.todasCategorias.isEmpty()) return
        val cat = DataHolder.todasCategorias[indiceCategoriaAtual]
        tvPainelTitulo.text = "Canais | ${cat.nome}"
        
        DataHolder.canaisFiltrados = if (cat.id == "FAV") {
            DataHolder.todosCanais.filter { DataHolder.favoritosIds.contains(it.id) }
        } else {
            DataHolder.todosCanais.filter { it.categoryId == cat.id }
        }

        recyclerPainelCanais.adapter = CanalLinhaAdapter(DataHolder.canaisFiltrados) { canalClicado ->
            val novoIndice = DataHolder.canaisFiltrados.indexOf(canalClicado)
            if (novoIndice != -1) {
                indiceCanalAtual = novoIndice
                painelCanais.visibility = View.GONE
                painelEpg.visibility = View.GONE
                iniciarCanal()
            }
        }
    }

    private fun inicializarPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build()
        playerView.player = exoPlayer
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING) progressBar.visibility = View.VISIBLE
                else if (playbackState == Player.STATE_READY) progressBar.visibility = View.GONE
            }
        })
    }

    private fun iniciarCanal() {
        if (DataHolder.canaisFiltrados.isEmpty()) return
        val canal = DataHolder.canaisFiltrados[indiceCanalAtual]
        
        osdChannelName.text = canal.nome
        osdCurrentProgram.text = "Buscando Guia de TV..."
        osdNextProgram.text = ""
        osdProgressContainer.visibility = View.GONE
        Glide.with(this).load(canal.urlImagem).into(osdLogo)
        
        buscarEPGDinamico(canal)

        osdContainer.visibility = View.VISIBLE
        handlerOSD.removeCallbacks(osdRunnable)
        handlerOSD.postDelayed(osdRunnable, 6000) // 6 Segundos na tela para dar tempo de ler
        
        exoPlayer?.stop()
        exoPlayer?.setMediaItem(MediaItem.fromUri(canal.streamUrl))
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = true
    }

    // =========================================================================
    // O VERDADEIRO MOTOR MATEMÁTICO DO EPG (PROGRESSO E PROGRAMA ATUAL)
    // =========================================================================
    private fun buscarEPGDinamico(canal: CanalItem) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val urlLiveIdx = canal.streamUrl.indexOf("/live/")
                if (urlLiveIdx == -1) return@launch

                val baseUrl = canal.streamUrl.substring(0, urlLiveIdx)
                val parts = canal.streamUrl.split("/")
                val pass = parts[parts.size - 2]
                val user = parts[parts.size - 3]
                val streamId = canal.id

                val apiUrl = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_short_epg&stream_id=$streamId&limit=10"

                val client = OkHttpClient()
                val req = Request.Builder().url(apiUrl).build()
                val res = client.newCall(req).execute()
                val jsonStr = res.body?.string() ?: ""

                listaEpgAtual.clear()
                
                var programaAtualTitulo = "Programação Indisponível"
                var programaSeguinteTitulo = ""
                var horaInicioOSD = ""
                var horaFimOSD = ""
                var barraProgressoValor = 0
                var encontrouAoVivo = false

                if (jsonStr.startsWith("{")) {
                    val json = JSONObject(jsonStr)
                    val listings = json.optJSONArray("epg_listings")
                    val agoraTs = System.currentTimeMillis() / 1000 // Relógio da TV em Segundos
                    
                    if (listings != null && listings.length() > 0) {
                        for (i in 0 until listings.length()) {
                            val prog = listings.getJSONObject(i)
                            
                            // 1. Título Blindado
                            val rawTitle = prog.optString("title", "Programa")
                            var titleDecoded = rawTitle 
                            if (rawTitle.isNotEmpty()) {
                                try {
                                    val decodedBytes = Base64.decode(rawTitle, Base64.DEFAULT)
                                    val tempString = String(decodedBytes)
                                    if (tempString.isNotBlank() && !tempString.contains("")) titleDecoded = tempString
                                } catch (e: Exception) {}
                            }

                            // 2. Horários Blindados
                            val startTs = prog.optString("start_timestamp").toLongOrNull() ?: prog.optLong("start_timestamp", 0)
                            val stopTs = prog.optString("stop_timestamp").toLongOrNull() ?: prog.optLong("stop_timestamp", 0)

                            var horarioLista = ""
                            var duracaoLista = ""
                            var isLive = false
                            var corTextoLista = "#888888" // Cinza

                            if (startTs > 0 && stopTs > 0) {
                                val sdfHora = SimpleDateFormat("HH:mm", Locale.getDefault())
                                val hInicio = sdfHora.format(Date(startTs * 1000))
                                val hFim = sdfHora.format(Date(stopTs * 1000))
                                
                                // Calcula a Duração
                                val duracaoMinutos = (stopTs - startTs) / 60
                                val h = duracaoMinutos / 60
                                val m = duracaoMinutos % 60
                                duracaoLista = if (h > 0 && m > 0) "$h hora e $m minutos" else if (h > 0) "$h horas" else "$m minutos"

                                // É O PROGRAMA DE AGORA? (MÁGICA DA MATEMÁTICA)
                                if (agoraTs in startTs until stopTs) {
                                    isLive = true
                                    encontrouAoVivo = true
                                    horarioLista = "Ao Vivo / $hInicio - $hFim"
                                    corTextoLista = "#2ED573" // Verde
                                    
                                    // Alimenta as variáveis do OSD (Banner do Player)
                                    programaAtualTitulo = titleDecoded
                                    horaInicioOSD = hInicio
                                    horaFimOSD = hFim
                                    
                                    // Calcula a porcentagem exata da Barra (0 a 100)
                                    val totalTempo = stopTs - startTs
                                    val tempoDecorrido = agoraTs - startTs
                                    if (totalTempo > 0) {
                                        barraProgressoValor = ((tempoDecorrido.toDouble() / totalTempo.toDouble()) * 100).toInt()
                                    }
                                    
                                    // Se esse é o Agora, o próximo do Array será o Seguinte!
                                    if (i + 1 < listings.length()) {
                                        val nextProg = listings.getJSONObject(i + 1)
                                        val nRaw = nextProg.optString("title", "")
                                        programaSeguinteTitulo = nRaw
                                        try { programaSeguinteTitulo = String(Base64.decode(nRaw, Base64.DEFAULT)) } catch (e: Exception) {}
                                    }
                                    
                                } else if (startTs > agoraTs) {
                                    // Programa Futuro
                                    horarioLista = "Hoje / $hInicio - $hFim"
                                } else {
                                    // Programa Passado
                                    horarioLista = "Encerrado / $hInicio - $hFim"
                                }
                            }
                            
                            // Adiciona na prateleira lateral
                            listaEpgAtual.add(EpgItem(titleDecoded, horarioLista, duracaoLista, isLive, corTextoLista))
                        }
                    }
                }

                // ATUALIZA A TELA NA MESMA HORA
                withContext(Dispatchers.Main) { 
                    osdCurrentProgram.text = programaAtualTitulo
                    
                    if (encontrouAoVivo) {
                        osdNextProgram.text = if (programaSeguinteTitulo.isNotEmpty()) "$programaSeguinteTitulo a seguir" else ""
                        osdTimeStart.text = horaInicioOSD
                        osdTimeEnd.text = horaFimOSD
                        osdProgressBar.progress = barraProgressoValor
                        osdProgressContainer.visibility = View.VISIBLE
                    } else {
                        // Se por algum motivo o painel não tiver o programa de agora, esconde a barra
                        osdNextProgram.text = ""
                        osdProgressContainer.visibility = View.GONE
                    }
                    
                    recyclerPainelEpg.adapter = EpgAdapter(listaEpgAtual)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) { 
                    osdCurrentProgram.text = "Programação Indisponível"
                    osdNextProgram.text = ""
                    osdProgressContainer.visibility = View.GONE
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (painelCanais.visibility == View.VISIBLE) { mudarCategoria(1); return true } 
                    else if (painelEpg.visibility == View.VISIBLE) { return true } 
                    else { mudarCanal(1); return true }
                }
                
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (painelCanais.visibility == View.VISIBLE) { mudarCategoria(-1); return true } 
                    else if (painelEpg.visibility == View.VISIBLE) { return true } 
                    else { mudarCanal(-1); return true }
                }

                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (painelCanais.visibility == View.VISIBLE) {
                        val child = recyclerPainelCanais.focusedChild
                        if (child != null && recyclerPainelCanais.getChildAdapterPosition(child) == 0) return true
                        return super.dispatchKeyEvent(event)
                    } 
                    else if (painelEpg.visibility == View.VISIBLE) {
                        val child = recyclerPainelEpg.focusedChild
                        if (child != null && recyclerPainelEpg.getChildAdapterPosition(child) == 0) return true
                        return super.dispatchKeyEvent(event)
                    } 
                    else {
                        painelCanais.visibility = View.VISIBLE
                        recyclerPainelCanais.post {
                            recyclerPainelCanais.findViewHolderForAdapterPosition(indiceCanalAtual)?.itemView?.requestFocus()
                                ?: recyclerPainelCanais.requestFocus()
                        }
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (painelCanais.visibility == View.VISIBLE) {
                        val child = recyclerPainelCanais.focusedChild
                        if (child != null && recyclerPainelCanais.getChildAdapterPosition(child) == DataHolder.canaisFiltrados.size - 1) return true
                        return super.dispatchKeyEvent(event)
                    } 
                    else if (painelEpg.visibility == View.VISIBLE) {
                        val child = recyclerPainelEpg.focusedChild
                        if (child != null && recyclerPainelEpg.getChildAdapterPosition(child) == listaEpgAtual.size - 1) return true
                        return super.dispatchKeyEvent(event)
                    } 
                    else {
                        painelEpg.visibility = View.VISIBLE
                        if (listaEpgAtual.isNotEmpty()) {
                            recyclerPainelEpg.adapter = EpgAdapter(listaEpgAtual)
                            recyclerPainelEpg.requestFocus()
                        } else {
                            Toast.makeText(this, "Guia indisponível para este canal.", Toast.LENGTH_SHORT).show()
                            painelEpg.visibility = View.GONE
                        }
                        return true
                    }
                }

                KeyEvent.KEYCODE_BACK -> {
                    if (painelCanais.visibility == View.VISIBLE) {
                        painelCanais.visibility = View.GONE
                        return true
                    }
                    if (painelEpg.visibility == View.VISIBLE) {
                        painelEpg.visibility = View.GONE
                        return true
                    }
                }
                
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (painelCanais.visibility == View.GONE && painelEpg.visibility == View.GONE) {
                        osdContainer.visibility = View.VISIBLE
                        handlerOSD.removeCallbacks(osdRunnable)
                        handlerOSD.postDelayed(osdRunnable, 6000)
                    } else {
                        return super.dispatchKeyEvent(event)
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun mudarCategoria(direcao: Int) {
        if (DataHolder.todasCategorias.isEmpty()) return
        indiceCategoriaAtual += direcao
        if (indiceCategoriaAtual >= DataHolder.todasCategorias.size) indiceCategoriaAtual = 0
        else if (indiceCategoriaAtual < 0) indiceCategoriaAtual = DataHolder.todasCategorias.size - 1
        carregarCanaisDaCategoria()
        recyclerPainelCanais.requestFocus()
    }

    private fun mudarCanal(direcao: Int) {
        indiceCanalAtual += direcao
        if (indiceCanalAtual >= DataHolder.canaisFiltrados.size) indiceCanalAtual = 0
        else if (indiceCanalAtual < 0) indiceCanalAtual = DataHolder.canaisFiltrados.size - 1
        iniciarCanal()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
    }
}
