package com.signalplay.tv

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// Memória rápida para a lista de episódios sem pesar a intent
object VodDataHolder {
    var listaEpisodios: List<EpisodeItem> = emptyList()
}

class PlayerVodActivity : Activity() {

    private var exoPlayer: ExoPlayer? = null
    private lateinit var playerViewVod: PlayerView
    private lateinit var progressBarVod: ProgressBar
    private lateinit var tvAvisoAspecto: TextView
    
    private lateinit var painelEpisodios: LinearLayout
    private lateinit var recyclerPainelEpisodios: RecyclerView

    private var modoAspectoAtual = 0
    private var tipoMedia = ""
    private var streamUrlAtual = ""
    
    private val handlerAviso = Handler(Looper.getMainLooper())
    private val avisoRunnable = Runnable { tvAvisoAspecto.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player_vod)

        playerViewVod = findViewById(R.id.playerViewVod)
        progressBarVod = findViewById(R.id.progressBarVod)
        tvAvisoAspecto = findViewById(R.id.tvAvisoAspecto)
        
        painelEpisodios = findViewById(R.id.painelEpisodios)
        recyclerPainelEpisodios = findViewById(R.id.recyclerPainelEpisodios)

        streamUrlAtual = intent.getStringExtra("STREAM_URL") ?: ""
        tipoMedia = intent.getStringExtra("TIPO") ?: "filme"

        // Configura o painel se for Série
        if (tipoMedia == "serie") {
            recyclerPainelEpisodios.layoutManager = LinearLayoutManager(this)
            recyclerPainelEpisodios.adapter = EpisodeAdapter(VodDataHolder.listaEpisodios) { epClicado ->
                painelEpisodios.visibility = View.GONE
                streamUrlAtual = epClicado.streamUrl
                iniciarVideo()
            }
        }

        inicializarPlayer()
        iniciarVideo()
    }

    private fun inicializarPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build()
        playerViewVod.player = exoPlayer

        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING) {
                    progressBarVod.visibility = View.VISIBLE
                } else if (playbackState == Player.STATE_READY) {
                    progressBarVod.visibility = View.GONE
                }
            }
        })
    }

    private fun iniciarVideo() {
        if (streamUrlAtual.isEmpty()) return
        
        exoPlayer?.stop()
        val mediaItem = MediaItem.fromUri(streamUrlAtual)
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = true
    }

    // Intercepta os botões do controle para funções especiais do VOD
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                // Seta para CIMA: Abre os episódios se for série
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (tipoMedia == "serie" && !playerViewVod.isControllerFullyVisible) {
                        if (painelEpisodios.visibility == View.GONE) {
                            painelEpisodios.visibility = View.VISIBLE
                            recyclerPainelEpisodios.requestFocus()
                        } else {
                            painelEpisodios.visibility = View.GONE
                        }
                        return true
                    }
                }
                // Seta para BAIXO: Atalho para esticar/ajustar a tela
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (!playerViewVod.isControllerFullyVisible && painelEpisodios.visibility == View.GONE) {
                        alternarAspectoTela()
                        return true
                    }
                }
                // Voltar: Fecha painéis antes de sair
                KeyEvent.KEYCODE_BACK -> {
                    if (painelEpisodios.visibility == View.VISIBLE) {
                        painelEpisodios.visibility = View.GONE
                        return true
                    }
                }
            }
        }
        // Deixa o resto (Direita/Esquerda/OK) passar pro ExoPlayer nativo controlar a timeline!
        return super.dispatchKeyEvent(event)
    }

    private fun alternarAspectoTela() {
        modoAspectoAtual++
        if (modoAspectoAtual > 2) modoAspectoAtual = 0

        when (modoAspectoAtual) {
            0 -> {
                playerViewVod.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                mostrarAviso("Tela: Original (Encaixado)")
            }
            1 -> {
                playerViewVod.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                mostrarAviso("Tela: Esticado")
            }
            2 -> {
                playerViewVod.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                mostrarAviso("Tela: Preencher (Zoom)")
            }
        }
    }

    private fun mostrarAviso(texto: String) {
        tvAvisoAspecto.text = texto
        tvAvisoAspecto.visibility = View.VISIBLE
        handlerAviso.removeCallbacks(avisoRunnable)
        handlerAviso.postDelayed(avisoRunnable, 2000)
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
}
