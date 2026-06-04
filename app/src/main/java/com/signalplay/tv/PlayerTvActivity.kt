package com.signalplay.tv

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultAllocator
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DataHolder {
    var todasCategorias: List<CategoriaItem> = emptyList()
    var todosCanais: List<CanalItem> = emptyList()
    var favoritosIds: List<String> = emptyList()
    var categoriaAtualId: String = ""
    var canaisFiltrados: List<CanalItem> = emptyList()
    var mapaEpgIds: MutableMap<String, String> = mutableMapOf() 
}

class PlayerTvActivity : Activity() {

    private var exoPlayer: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var progressBar: ProgressBar
    
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
        // MÁGICA: Buffer Inteligente para TV Ao Vivo
        // Prioriza abrir o canal rápido, mas estoca até 50 segundos de vídeo para evitar travamentos
        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, 64 * 1024))
            .setBufferDurationsMs(
                15000, // Min buffer (15 seg)
                50000, // Max buffer (50 seg)
                1500,  // Inicia o vídeo com apenas 1.5 seg baixado (Zapping rápido)
                3000   // Se travar, volta rápido com 3 seg
            )
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()
            
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
        handlerOSD.postDelayed(osdRunnable, 6000) 
        
        exoPlayer?.stop()
        exoPlayer?.setMediaItem(MediaItem.fromUri(canal.streamUrl))
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = true
    }

    private fun decodificarTexto(raw: String): String {
        if (raw.isEmpty()) return ""
        try {
            val decodedBytes = Base64.decode(raw, Base64.DEFAULT)
            val txt = String(decodedBytes, Charsets.UTF_8)
            if (txt.isNotBlank() && !txt.contains("")) {
                return txt
            }
        } catch (e: Exception) {}
        return raw
    }

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

                var listings: JSONArray? = null

                try {
                    val apiUrl = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_short_epg&stream_id=$streamId&limit=10"
                    val client = OkHttpClient()
                    val req = Request.Builder().url(apiUrl).build()
                    val res = client.newCall(req).execute()
                    val jsonStr = res.body?.string() ?: ""
                    if (jsonStr.startsWith("{")) {
                        val json = JSONObject(jsonStr)
                        val l = json.optJSONArray("epg_listings")
                        if (l != null && l.length() > 0) listings = l
                    }
                } catch (e: Exception) {}

                if (listings == null || listings.length() == 0) {
                    try {
                        val epgChannelId = DataHolder.mapaEpgIds[canal.id]
                        if (epgChannelId != null && epgChannelId.isNotEmpty()) {
                            val file = File(filesDir, "epg_data.json")
                            if (file.exists()) {
                                val dbJson = JSONObject(file.readText())
                                val localList = dbJson.optJSONArray(epgChannelId)
                                if (localList != null && localList.length() > 0) {
                                    val agora = System.currentTimeMillis() / 1000
                                    val futuros = mutableListOf<JSONObject>()
                                    
                                    for (i in 0 until localList.length()) {
                                        val prog = localList.getJSONObject(i)
                                        if (prog.optLong("stop_timestamp", 0) > agora) {
                                            futuros.add(prog)
                                        }
                                    }
                                    
                                    futuros.sortBy { it.optLong("start_timestamp", 0) }
                                    
                                    val newArr = JSONArray()
                                    for (i in 0 until Math.min(10, futuros.size)) newArr.put(futuros[i])
                                    if (newArr.length() > 0) listings = newArr
                                }
                            }
                        }
                    } catch (e: Exception) {}
                }

                listaEpgAtual.clear()
                
                var programaAtualTitulo = "Programação Indisponível"
                var programaSeguinteTitulo = ""
                var horaInicioOSD = ""
                var horaFimOSD = ""
                var barraProgressoValor = 0
                var encontrouAoVivo = false

                if (listings != null && listings.length() > 0) {
                    val agoraTs = System.currentTimeMillis() / 1000 
                    
                    for (i in 0 until listings.length()) {
                        val prog = listings.getJSONObject(i)
                        
                        val titleDecoded = decodificarTexto(prog.optString("title", "Programa"))

                        val startTs = prog.optString("start_timestamp").toLongOrNull() ?: prog.optLong("start_timestamp", 0)
                        val stopTs = prog.optString("stop_timestamp").toLongOrNull() ?: prog.optLong("stop_timestamp", 0)

                        var horarioLista = ""
                        var duracaoLista = ""
                        var isLive = false
                        var corTextoLista = "#888888" 

                        if (startTs > 0 && stopTs > 0) {
                            val sdfHora = SimpleDateFormat("HH:mm", Locale.getDefault())
                            val hInicio = sdfHora.format(Date(startTs * 1000))
                            val hFim = sdfHora.format(Date(stopTs * 1000))
                            
                            val duracaoMinutos = (stopTs - startTs) / 60
                            val h = duracaoMinutos / 60
                            val m = duracaoMinutos % 60
                            duracaoLista = if (h > 0 && m > 0) "$h hora e $m minutos" else if (h > 0) "$h horas" else "$m minutos"

                            if (agoraTs in startTs until stopTs) {
                                isLive = true
                                encontrouAoVivo = true
                                horarioLista = "Ao Vivo / $hInicio - $hFim"
                                corTextoLista = "#2ED573" 
                                
                                programaAtualTitulo = titleDecoded
                                horaInicioOSD = hInicio
                                horaFimOSD = hFim
                                
                                val totalTempo = stopTs - startTs
                                val tempoDecorrido = agoraTs - startTs
                                if (totalTempo > 0) {
                                    barraProgressoValor = ((tempoDecorrido.toDouble() / totalTempo.toDouble()) * 100).toInt()
                                }
                                
                                if (i + 1 < listings.length()) {
                                    val nextProg = listings.getJSONObject(i + 1)
                                    programaSeguinteTitulo = decodificarTexto(nextProg.optString("title", ""))
                                }
                                
                            } else if (startTs > agoraTs) {
                                horarioLista = "Hoje / $hInicio - $hFim"
                            } else {
                                horarioLista = "Encerrado / $hInicio - $hFim"
                            }
                        }
                        
                        listaEpgAtual.add(EpgItem(titleDecoded, horarioLista, duracaoLista, isLive, corTextoLista))
                    }
                }

                withContext(Dispatchers.Main) { 
                    osdCurrentProgram.text = programaAtualTitulo
                    
                    if (encontrouAoVivo) {
                        osdNextProgram.text = if (programaSeguinteTitulo.isNotEmpty()) "$programaSeguinteTitulo a seguir" else ""
                        osdTimeStart.text = horaInicioOSD
                        osdTimeEnd.text = horaFimOSD
                        osdProgressBar.progress = barraProgressoValor
                        osdProgressContainer.visibility = View.VISIBLE
                    } else {
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
                    if (painelCanais.visibility == View.VISIBLE) { painelCanais.visibility = View.GONE; return true }
                    if (painelEpg.visibility == View.VISIBLE) { painelEpg.visibility = View.GONE; return true }
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
