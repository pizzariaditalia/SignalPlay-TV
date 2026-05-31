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

object VodDataHolder {
    var seasonsList: List<String> = emptyList()
    var episodesMap: Map<String, List<EpisodeItem>> = emptyMap()
    var seasonAtualIndex: Int = 0
}

class PlayerVodActivity : Activity() {

    private var exoPlayer: ExoPlayer? = null
    private lateinit var playerViewVod: PlayerView
    private lateinit var progressBarVod: ProgressBar
    private lateinit var tvAvisoAspecto: TextView
    private lateinit var painelEpisodios: LinearLayout
    private lateinit var recyclerPainelEpisodios: RecyclerView
    private lateinit var tvPainelEpTitle: TextView

    private var modoAspectoAtual = 0
    private var tipoMedia = ""
    private var streamUrlAtual = ""
    
    // Variáveis para o Avanço Contínuo Inteligente
    private var isSeeking = false
    private var currentSeekPos = 0L

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
        tvPainelEpTitle = findViewById(R.id.tvPainelEpTitle)

        streamUrlAtual = intent.getStringExtra("STREAM_URL") ?: ""
        tipoMedia = intent.getStringExtra("TIPO") ?: "filme"

        if (tipoMedia == "serie") {
            recyclerPainelEpisodios.layoutManager = LinearLayoutManager(this)
            carregarTemporada()
        }

        inicializarPlayer()
        iniciarVideo()
    }

    private fun carregarTemporada() {
        if (VodDataHolder.seasonsList.isEmpty()) return
        val season = VodDataHolder.seasonsList[VodDataHolder.seasonAtualIndex]
        tvPainelEpTitle.text = "Temporada $season"
        val eps = VodDataHolder.episodesMap[season] ?: emptyList()

        recyclerPainelEpisodios.adapter = EpisodeAdapter(eps) { epClicado ->
            painelEpisodios.visibility = View.GONE
            streamUrlAtual = epClicado.streamUrl
            iniciarVideo()
        }
    }

    private fun inicializarPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build()
        playerViewVod.player = exoPlayer
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING) progressBarVod.visibility = View.VISIBLE
                else if (playbackState == Player.STATE_READY) progressBarVod.visibility = View.GONE
            }
        })
    }

    private fun iniciarVideo() {
        if (streamUrlAtual.isEmpty()) return
        exoPlayer?.stop()
        exoPlayer?.setMediaItem(MediaItem.fromUri(streamUrlAtual))
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Se a interface nativa do player estiver visível, deixa o Android tratar normal
        if (playerViewVod.isControllerFullyVisible && painelEpisodios.visibility == View.GONE) {
            return super.dispatchKeyEvent(event)
        }

        val keyCode = event.keyCode
        
        // EVENTOS QUANDO SOLTA O BOTÃO (Fim do Avanço)
        if (event.action == KeyEvent.ACTION_UP) {
            if (isSeeking && (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT)) {
                isSeeking = false
                exoPlayer?.seekTo(currentSeekPos)
                exoPlayer?.play()
                tvAvisoAspecto.visibility = View.GONE
                return true
            }
        }

        // EVENTOS QUANDO APERTA/SEGURA O BOTÃO
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                // AVANÇO/RETROCESSO PERSONALIZADO (INTERCEPTA O EXOPLAYER)
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (painelEpisodios.visibility == View.VISIBLE) {
                        // Se o painel estiver aberto, navega nas temporadas
                        if (event.repeatCount == 0) {
                            mudarTemporada(if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1)
                        }
                        return true
                    } else {
                        // Se estiver assistindo, faz o avanço sem mostrar controles
                        if (!isSeeking) {
                            isSeeking = true
                            exoPlayer?.pause()
                            currentSeekPos = exoPlayer?.currentPosition ?: 0L
                        }
                        
                        val pulo = 10000L // Pula de 10 em 10 segundos
                        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) currentSeekPos += pulo
                        else currentSeekPos -= pulo
                        
                        if (currentSeekPos < 0) currentSeekPos = 0
                        
                        // Mostra o tempo visualmente na tela
                        val minutos = (currentSeekPos / 1000) / 60
                        val segundos = (currentSeekPos / 1000) % 60
                        tvAvisoAspecto.text = String.format("%02d:%02d", minutos, segundos)
                        tvAvisoAspecto.visibility = View.VISIBLE
                        return true
                    }
                }
                
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (tipoMedia == "serie") {
                        if (painelEpisodios.visibility == View.GONE) {
                            painelEpisodios.visibility = View.VISIBLE
                            recyclerPainelEpisodios.requestFocus()
                            return true
                        }
                    }
                    return super.dispatchKeyEvent(event) // Deixa a lista rolar
                }
                
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (painelEpisodios.visibility == View.GONE) {
                        alternarAspectoTela()
                        return true
                    }
                    return super.dispatchKeyEvent(event)
                }
                
                KeyEvent.KEYCODE_BACK -> {
                    if (painelEpisodios.visibility == View.VISIBLE) {
                        painelEpisodios.visibility = View.GONE
                        return true
                    }
                }
                
                // OK -> Play/Pause apenas (abre o controlador rapidamente se precisar)
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (painelEpisodios.visibility == View.GONE) {
                        playerViewVod.showController()
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun mudarTemporada(direcao: Int) {
        if (VodDataHolder.seasonsList.isEmpty()) return
        VodDataHolder.seasonAtualIndex += direcao
        if (VodDataHolder.seasonAtualIndex >= VodDataHolder.seasonsList.size) VodDataHolder.seasonAtualIndex = 0
        else if (VodDataHolder.seasonAtualIndex < 0) VodDataHolder.seasonAtualIndex = VodDataHolder.seasonsList.size - 1
        carregarTemporada()
        recyclerPainelEpisodios.requestFocus()
    }

    private fun alternarAspectoTela() {
        modoAspectoAtual++
        if (modoAspectoAtual > 2) modoAspectoAtual = 0
        when (modoAspectoAtual) {
            0 -> { playerViewVod.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; mostrarAviso("Encaixado") }
            1 -> { playerViewVod.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL; mostrarAviso("Esticado") }
            2 -> { playerViewVod.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; mostrarAviso("Preencher") }
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
    }
}
