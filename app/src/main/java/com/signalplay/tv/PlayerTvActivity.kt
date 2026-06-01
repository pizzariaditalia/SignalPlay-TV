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

class PlayerTvActivity : Activity() {

    private var exoPlayer: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var progressBar: ProgressBar
    
    private lateinit var osdContainer: LinearLayout
    private lateinit var osdLogo: ImageView
    private lateinit var osdChannelName: TextView
    private lateinit var osdEpgText: TextView
    
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
        osdEpgText = findViewById(R.id.osdEpgText)
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
        osdEpgText.text = "Carregando programação..."
        Glide.with(this).load(canal.urlImagem).into(osdLogo)
        
        buscarEPGDinamico(canal)

        osdContainer.visibility = View.VISIBLE
        handlerOSD.removeCallbacks(osdRunnable)
        handlerOSD.postDelayed(osdRunnable, 5000)
        
        exoPlayer?.stop()
        exoPlayer?.setMediaItem(MediaItem.fromUri(canal.streamUrl))
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = true
    }

    // =========================================================================
    // MOTOR DE EPG BLINDADO (Base64 + Texto Normal)
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

                if (jsonStr.startsWith("{")) {
                    val json = JSONObject(jsonStr)
                    val listings = json.optJSONArray("epg_listings")
                    
                    if (listings != null && listings.length() > 0) {
                        for (i in 0 until listings.length()) {
                            val prog = listings.getJSONObject(i)
                            
                            // 1. BLINDAGEM DO TÍTULO
                            val rawTitle = prog.optString("title", "Programa")
                            var titleDecoded = rawTitle // Começa assumindo que é Texto Normal
                            
                            if (rawTitle.isNotEmpty()) {
                                try {
                                    // Tenta decodificar. Se for texto puro, vai falhar e o catch ignora.
                                    val decodedBytes = Base64.decode(rawTitle, Base64.DEFAULT)
                                    val tempString = String(decodedBytes)
                                    if (tempString.isNotBlank() && !tempString.contains("")) {
                                        titleDecoded = tempString // Sucesso, era Base64!
                                    }
                                } catch (e: Exception) {
                                    // Falhou a decodificação? Sem problema, mantém o texto original (rawTitle)
                                }
                            }

                            // 2. BLINDAGEM DO HORÁRIO
                            var horario = ""
                            val startTs = prog.optString("start_timestamp").toLongOrNull() ?: prog.optLong("start_timestamp", 0)
                            val stopTs = prog.optString("stop_timestamp").toLongOrNull() ?: prog.optLong("stop_timestamp", 0)

                            if (startTs > 0 && stopTs > 0) {
                                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                                val hInicio = sdf.format(Date(startTs * 1000))
                                val hFim = sdf.format(Date(stopTs * 1000))
                                horario = "$hInicio - $hFim"
                            } else {
                                // Se o painel não enviou Timestamp, recorta direto do campo "start" (Ex: "2026-05-31 14:00:00")
                                val startStr = prog.optString("start", "")
                                val endStr = prog.optString("end", "")
                                if (startStr.length > 11 && endStr.length > 11) {
                                    try {
                                        horario = "${startStr.substring(11, 16)} - ${endStr.substring(11, 16)}"
                                    } catch (e: Exception) {}
                                }
                            }
                            
                            listaEpgAtual.add(EpgItem(titleDecoded, horario, i == 0))
                        }
                    }
                }

                withContext(Dispatchers.Main) { 
                    if (listaEpgAtual.isNotEmpty()) {
                        val atual = listaEpgAtual[0]
                        val horaExibicao = if (atual.horario.isNotEmpty()) "${atual.horario} • " else ""
                        osdEpgText.text = "$horaExibicao${atual.titulo}"
                    } else {
                        osdEpgText.text = "Programação Indisponível"
                    }
                    recyclerPainelEpg.adapter = EpgAdapter(listaEpgAtual)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) { osdEpgText.text = "Programação Indisponível" }
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
                        handlerOSD.postDelayed(osdRunnable, 4000)
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
