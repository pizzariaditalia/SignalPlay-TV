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
        val keyCode = event.keyCode
        
        // =======================================================
        // MÁGICA 1: O SOLTAR DO BOTÃO (Bloqueando o ExoPlayer)
        // =======================================================
        if (event.action == KeyEvent.ACTION_UP) {
            
            // Finaliza o avanço se estiver avançando
            if (isSeeking && (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT)) {
                isSeeking = false
                exoPlayer?.seekTo(currentSeekPos)
                exoPlayer?.play()
                tvAvisoAspecto.visibility = View.GONE
                return true
            }

            // CORREÇÃO DEFINITIVA: Se o menu nativo não está na tela e a barra de episódios tá fechada, 
            // a gente "engole" a ação de soltar os direcionais para o ExoPlayer não abrir!
            if (painelEpisodios.visibility == View.GONE && !playerViewVod.isControllerFullyVisible) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                    keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    return true 
                }
            }
        }

        // =======================================================
        // MÁGICA 2: O APERTAR DO BOTÃO
        // =======================================================
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (painelEpisodios.visibility == View.VISIBLE) {
                        if (event.repeatCount == 0) mudarTemporada(if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1)
                        return true
                    }
                    
                    // Se o menu do ExoPlayer estiver aberto na tela, deixa o usuário navegar por ele!
                    if (playerViewVod.isControllerFullyVisible) {
                        return super.dispatchKeyEvent(event)
                    }
                    
                    if (!isSeeking) {
                        isSeeking = true
                        exoPlayer?.pause()
                        currentSeekPos = exoPlayer?.currentPosition ?: 0L
                    }
                    
                    val basePulo = 10000L
                    val multiplicador = if (event.repeatCount > 8) 4 else if (event.repeatCount > 3) 2 else 1
                    val pulo = basePulo * multiplicador
                    
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) currentSeekPos += pulo
                    else currentSeekPos -= pulo
                    
                    if (currentSeekPos < 0) currentSeekPos = 0
                    val duration = exoPlayer?.duration ?: 0L
                    if (duration > 0 && currentSeekPos > duration) currentSeekPos = duration
                    
                    val min = (currentSeekPos / 1000) / 60
                    val seg = (currentSeekPos / 1000) % 60
                    val tMin = (duration / 1000) / 60
                    val tSeg = (duration / 1000) % 60
                    tvAvisoAspecto.text = String.format("%02d:%02d / %02d:%02d", min, seg, tMin, tSeg)
                    tvAvisoAspecto.visibility = View.VISIBLE
                    return true
                }
                
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (tipoMedia == "serie") {
                        if (painelEpisodios.visibility == View.VISIBLE) {
                            if (!recyclerPainelEpisodios.canScrollVertically(-1)) return true
                            return super.dispatchKeyEvent(event)
                        } else if (!playerViewVod.isControllerFullyVisible) {
                            painelEpisodios.visibility = View.VISIBLE
                            recyclerPainelEpisodios.requestFocus()
                            return true
                        }
                    }
                    if (playerViewVod.isControllerFullyVisible) return super.dispatchKeyEvent(event)
                }
                
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (painelEpisodios.visibility == View.VISIBLE) {
                        if (!recyclerPainelEpisodios.canScrollVertically(1)) return true
                        return super.dispatchKeyEvent(event)
                    } else if (!playerViewVod.isControllerFullyVisible) {
                        alternarAspectoTela()
                        return true
                    }
                    if (playerViewVod.isControllerFullyVisible) return super.dispatchKeyEvent(event)
                }
                
                KeyEvent.KEYCODE_BACK -> {
                    if (painelEpisodios.visibility == View.VISIBLE) {
                        painelEpisodios.visibility = View.GONE
                        return true
                    }
                }
                
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (painelEpisodios.visibility == View.GONE) {
                        if (playerViewVod.isControllerFullyVisible) playerViewVod.hideController()
                        else playerViewVod.showController()
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
