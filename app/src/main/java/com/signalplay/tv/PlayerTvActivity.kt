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

        recyclerPainelCanais.layoutManager = LinearLayoutManager(this)
        
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
        
        // MÁGICA: Bate na API para puxar o EPG dinâmico!
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
    // O MESMO MOTOR DE EPG DINÂMICO USADO NO JS (apptv.js)
    // =========================================================================
    private fun buscarEPGDinamico(canal: CanalItem) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Fatiamos o link para recuperar os dados e montar a rota de EPG
                val urlLiveIdx = canal.streamUrl.indexOf("/live/")
                if (urlLiveIdx == -1) return@launch

                val baseUrl = canal.streamUrl.substring(0, urlLiveIdx)
                val parts = canal.streamUrl.split("/")
                val pass = parts[parts.size - 2]
                val user = parts[parts.size - 3]
                val streamId = canal.id

                val apiUrl = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_short_epg&stream_id=$streamId&limit=2"

                val client = OkHttpClient()
                val req = Request.Builder().url(apiUrl).build()
                val res = client.newCall(req).execute()
                val jsonStr = res.body?.string() ?: ""

                if (jsonStr.startsWith("{")) {
                    val json = JSONObject(jsonStr)
                    val listings = json.optJSONArray("epg_listings")
                    
                    if (listings != null && listings.length() > 0) {
                        val atual = listings.getJSONObject(0)
                        val titleB64 = atual.optString("title", "")
                        var titleDecoded = "Programa Local"

                        if (titleB64.isNotEmpty()) {
                            try {
                                titleDecoded = String(Base64.decode(titleB64, Base64.DEFAULT))
                            } catch (e: Exception) {}
                        }

                        // Formatação do Relógio (Hora de Início - Fim)
                        val startTs = atual.optLong("start_timestamp", 0)
                        val stopTs = atual.optLong("stop_timestamp", 0)
                        var horario = ""

                        if (startTs > 0 && stopTs > 0) {
                            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                            val hInicio = sdf.format(Date(startTs * 1000))
                            val hFim = sdf.format(Date(stopTs * 1000))
                            horario = "$hInicio - $hFim • "
                        }

                        withContext(Dispatchers.Main) { osdEpgText.text = "$horario$titleDecoded" }
                    } else {
                        withContext(Dispatchers.Main) { osdEpgText.text = "Sem programação" }
                    }
                } else {
                    withContext(Dispatchers.Main) { osdEpgText.text = "Sem programação" }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) { osdEpgText.text = "Sem programação" }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (painelCanais.visibility == View.VISIBLE) {
                        mudarCategoria(1)
                        return true
                    } else {
                        mudarCanal(1)
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (painelCanais.visibility == View.VISIBLE) {
                        mudarCategoria(-1)
                        return true
                    } else {
                        mudarCanal(-1)
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (painelCanais.visibility == View.VISIBLE) {
                        val child = recyclerPainelCanais.focusedChild
                        if (child != null && recyclerPainelCanais.getChildAdapterPosition(child) == 0) {
                            return true
                        }
                        return super.dispatchKeyEvent(event)
                    }
                    painelCanais.visibility = View.VISIBLE
                    recyclerPainelCanais.post {
                        recyclerPainelCanais.findViewHolderForAdapterPosition(indiceCanalAtual)?.itemView?.requestFocus()
                            ?: recyclerPainelCanais.requestFocus()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (painelCanais.visibility == View.VISIBLE) {
                        val child = recyclerPainelCanais.focusedChild
                        if (child != null && recyclerPainelCanais.getChildAdapterPosition(child) == DataHolder.canaisFiltrados.size - 1) {
                            return true
                        }
                        return super.dispatchKeyEvent(event)
                    }
                }
                KeyEvent.KEYCODE_BACK -> {
                    if (painelCanais.visibility == View.VISIBLE) {
                        painelCanais.visibility = View.GONE
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (painelCanais.visibility == View.GONE) {
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
