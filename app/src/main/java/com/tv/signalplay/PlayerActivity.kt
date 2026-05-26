package com.tv.signalplay

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView

class PlayerActivity : FragmentActivity() {

    private var exoPlayer: ExoPlayer? = null
    private lateinit var controlsOverlay: RelativeLayout
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var btnPlayPauseIcon: ImageView
    private lateinit var progressBarTempo: ProgressBar
    private lateinit var txtTempoAtual: TextView
    private lateinit var txtTempoTotal: TextView
    private lateinit var txtFeedbackAvanco: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var isOverlayVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        controlsOverlay = findViewById(R.id.controlsOverlay)
        loadingSpinner = findViewById(R.id.loadingSpinner)
        btnPlayPauseIcon = findViewById(R.id.btnPlayPauseIcon)
        progressBarTempo = findViewById(R.id.progressBarTempo)
        txtTempoAtual = findViewById(R.id.txtTempoAtual)
        txtTempoTotal = findViewById(R.id.txtTempoTotal)
        txtFeedbackAvanco = findViewById(R.id.txtFeedbackAvanco)

        val titulo = intent.getStringExtra("TITLE") ?: ""
        findViewById<TextView>(R.id.txtPlayerTitulo).text = titulo

        val urlServ = intent.getStringExtra("URL")?.trimEnd('/') ?: ""
        val xtUser = intent.getStringExtra("XTREAM_USER") ?: ""
        val xtPass = intent.getStringExtra("XTREAM_PASS") ?: ""
        val type = intent.getStringExtra("TYPE") ?: "live"
        val id = intent.getIntExtra("STREAM_ID", 0)
        val ext = intent.getStringExtra("EXTENSION") ?: "mp4"

        val videoUrl = when (type) {
            "live" -> "$urlServ/$xtUser/$xtPass/$id"
            "vod" -> "$urlServ/movie/$xtUser/$xtPass/$id.$ext"
            "series" -> "$urlServ/series/$xtUser/$xtPass/$id.$ext"
            else -> ""
        }

        if (videoUrl.isNotEmpty()) iniciarPlayer(videoUrl) else finish()
    }

    private fun iniciarPlayer(url: String) {
        val playerView = findViewById<PlayerView>(R.id.playerView)
        exoPlayer = ExoPlayer.Builder(this).build()
        playerView.player = exoPlayer

        val mediaItem = MediaItem.fromUri(Uri.parse(url))
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = true

        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                loadingSpinner.visibility = if (playbackState == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                btnPlayPauseIcon.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
                if (isPlaying) esconderControlesAposDelay() else mostrarControles()
            }
        })
        atualizarBarraDeProgresso()
    }

    private fun atualizarBarraDeProgresso() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                exoPlayer?.let {
                    val duration = it.duration
                    val position = it.currentPosition
                    if (duration > 0) {
                        progressBarTempo.max = 100
                        progressBarTempo.progress = ((position * 100) / duration).toInt()
                        txtTempoAtual.text = formatarTempo(position)
                        txtTempoTotal.text = formatarTempo(duration)
                    }
                }
                handler.postDelayed(this, 1000)
            }
        }, 1000)
    }

    private fun formatarTempo(ms: Long): String {
        val totalSegundos = ms / 1000
        val minutos = totalSegundos / 60
        val segundos = totalSegundos % 60
        return String.format("%02d:%02d", minutos, segundos)
    }

    // MAPA DE CONTROLE LIMPO
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        mostrarControles()
        esconderControlesAposDelay()

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                exoPlayer?.let { it.playWhenReady = !it.playWhenReady }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                exoPlayer?.let { it.seekTo(it.currentPosition - 10000) }
                mostrarAvisoTempo("⏪ -10s")
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                exoPlayer?.let { it.seekTo(it.currentPosition + 10000) }
                mostrarAvisoTempo("+10s ⏩")
                return true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                if (isOverlayVisible) finish() else mostrarControles()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun mostrarAvisoTempo(texto: String) {
        txtFeedbackAvanco.text = texto
        txtFeedbackAvanco.visibility = View.VISIBLE
        handler.postDelayed({ txtFeedbackAvanco.visibility = View.GONE }, 1000)
    }

    private fun mostrarControles() { controlsOverlay.visibility = View.VISIBLE; isOverlayVisible = true }
    private val hideRunnable = Runnable { if (exoPlayer?.isPlaying == true) { controlsOverlay.visibility = View.GONE; isOverlayVisible = false } }
    private fun esconderControlesAposDelay() { handler.removeCallbacks(hideRunnable); handler.postDelayed(hideRunnable, 3500) }

    override fun onDestroy() { super.onDestroy(); handler.removeCallbacksAndMessages(null); exoPlayer?.release(); exoPlayer = null }
}
