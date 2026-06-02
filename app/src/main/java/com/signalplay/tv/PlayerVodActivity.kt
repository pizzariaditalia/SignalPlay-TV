package com.signalplay.tv

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

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

    private lateinit var overlayNextEpisode: RelativeLayout
    private lateinit var tvNextCountdown: TextView
    private lateinit var tvNextEpisodeName: TextView
    private lateinit var btnPlayNext: Button
    private lateinit var btnCancelNext: Button
    private var countDownTimer: CountDownTimer? = null
    private var nextEpisodeToPlay: EpisodeItem? = null

    private lateinit var btnSkipIntro: Button

    private lateinit var db: FirebaseFirestore
    private var username: String = ""
    private var mediaId: String = ""
    private var parentSeriesId: String = ""

    private var streamUrlAtual = ""
    private var tipoMedia = ""
    
    private var hasRestoredPosition = false

    private val introHandler = Handler(Looper.getMainLooper())
    private val introRunnable = object : Runnable {
        override fun run() {
            val pos = exoPlayer?.currentPosition ?: 0L
            if (tipoMedia == "serie" && pos in 5000L..90000L) {
                if (btnSkipIntro.visibility == View.GONE) btnSkipIntro.visibility = View.VISIBLE
            } else {
                if (btnSkipIntro.visibility == View.VISIBLE) btnSkipIntro.visibility = View.GONE
            }
            introHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player_vod)

        db = FirebaseFirestore.getInstance()

        playerViewVod = findViewById(R.id.playerViewVod)
        progressBarVod = findViewById(R.id.progressBarVod)
        tvAvisoAspecto = findViewById(R.id.tvAvisoAspecto)
        painelEpisodios = findViewById(R.id.painelEpisodios)
        recyclerPainelEpisodios = findViewById(R.id.recyclerPainelEpisodios)
        tvPainelEpTitle = findViewById(R.id.tvPainelEpTitle)

        overlayNextEpisode = findViewById(R.id.overlayNextEpisode)
        tvNextCountdown = findViewById(R.id.tvNextCountdown)
        tvNextEpisodeName = findViewById(R.id.tvNextEpisodeName)
        btnPlayNext = findViewById(R.id.btnPlayNext)
        btnCancelNext = findViewById(R.id.btnCancelNext)
        
        btnSkipIntro = findViewById(R.id.btnSkipIntro)

        streamUrlAtual = intent.getStringExtra("STREAM_URL") ?: ""
        tipoMedia = intent.getStringExtra("TIPO") ?: "filme"
        username = intent.getStringExtra("USERNAME") ?: ""
        parentSeriesId = intent.getStringExtra("MEDIA_ID") ?: ""
        mediaId = parentSeriesId 

        if (tipoMedia == "serie") {
            recyclerPainelEpisodios.layoutManager = LinearLayoutManager(this)
            carregarTemporada()
        }

        btnPlayNext.setOnClickListener { playNextEpisode() }
        btnCancelNext.setOnClickListener { cancelNextEpisode() }

        btnSkipIntro.setOnClickListener {
            val atual = exoPlayer?.currentPosition ?: 0L
            exoPlayer?.seekTo(atual + 85000L) 
            btnSkipIntro.visibility = View.GONE
            playerViewVod.hideController() 
        }

        val focusListener = View.OnFocusChangeListener { v, hasFocus ->
            if (hasFocus) v.animate().scaleX(1.05f).scaleY(1.05f).translationZ(10f).setDuration(150).start()
            else v.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(150).start()
        }
        btnPlayNext.onFocusChangeListener = focusListener
        btnCancelNext.onFocusChangeListener = focusListener
        
        btnSkipIntro.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.setBackgroundColor(Color.parseColor("#FFC107"))
                (v as Button).setTextColor(Color.BLACK)
                v.animate().scaleX(1.05f).scaleY(1.05f).translationZ(10f).setDuration(150).start()
            } else {
                v.setBackgroundColor(Color.parseColor("#222222"))
                (v as Button).setTextColor(Color.WHITE)
                v.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(150).start()
            }
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
            mediaId = epClicado.id 
            hasRestoredPosition = false
            iniciarVideo()
        }
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
                    if (!hasRestoredPosition) {
                        hasRestoredPosition = true
                        restaurarProgressoDoFirebase()
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    checkAndShowNextEpisode()
                }
            }
        })
    }

    private fun iniciarVideo() {
        if (streamUrlAtual.isEmpty()) return
        exoPlayer?.stop()
        exoPlayer?.setMediaItem(MediaItem.fromUri(streamUrlAtual))
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = true
        
        introHandler.postDelayed(introRunnable, 1000) 
    }

    private fun checkAndShowNextEpisode() {
        if (tipoMedia != "serie" || VodDataHolder.seasonsList.isEmpty()) {
            finish() 
            return
        }

        val currentSeason = VodDataHolder.seasonsList[VodDataHolder.seasonAtualIndex]
        val eps = VodDataHolder.episodesMap[currentSeason] ?: emptyList()
        val currentIndex = eps.indexOfFirst { it.id == mediaId }

        if (currentIndex != -1 && currentIndex < eps.size - 1) {
            nextEpisodeToPlay = eps[currentIndex + 1]
            showNextEpisodeOverlay()
        } else if (VodDataHolder.seasonAtualIndex < VodDataHolder.seasonsList.size - 1) {
            VodDataHolder.seasonAtualIndex += 1
            val nextSeason = VodDataHolder.seasonsList[VodDataHolder.seasonAtualIndex]
            val nextSeasonEps = VodDataHolder.episodesMap[nextSeason] ?: emptyList()
            if (nextSeasonEps.isNotEmpty()) {
                nextEpisodeToPlay = nextSeasonEps[0]
                showNextEpisodeOverlay()
            } else {
                finish()
            }
        } else {
            finish() 
        }
    }

    private fun showNextEpisodeOverlay() {
        overlayNextEpisode.visibility = View.VISIBLE
        tvNextEpisodeName.text = nextEpisodeToPlay?.title ?: "Próximo Episódio"
        btnPlayNext.requestFocus() 

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                tvNextCountdown.text = (millisUntilFinished / 1000).toString()
            }
            override fun onFinish() {
                playNextEpisode()
            }
        }.start()
    }

    private fun playNextEpisode() {
        countDownTimer?.cancel()
        overlayNextEpisode.visibility = View.GONE
        
        nextEpisodeToPlay?.let { ep ->
            streamUrlAtual = ep.streamUrl
            mediaId = ep.id
            hasRestoredPosition = false
            iniciarVideo()
        }
    }

    private fun cancelNextEpisode() {
        countDownTimer?.cancel()
        overlayNextEpisode.visibility = View.GONE
        finish() 
    }

    private fun restaurarProgressoDoFirebase() {
        val idToLoad = if (tipoMedia == "serie") parentSeriesId else mediaId
        if (username.isEmpty() || idToLoad.isEmpty()) return
        
        db.collection("usuarios").whereEqualTo("usuario", username).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    val historico = doc.get("historico_vod") as? Map<String, Any>
                    
                    if (historico != null) {
                        val dadosMedia = historico[idToLoad] as? Map<String, Any>
                        if (dadosMedia != null) {
                            val posicaoSalva = dadosMedia["posicao"]?.toString()?.toLongOrNull() ?: 0L
                            
                            if (posicaoSalva > 0L) {
                                exoPlayer?.seekTo(posicaoSalva)
                            }
                        }
                    }
                }
            }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            
            if (overlayNextEpisode.visibility == View.VISIBLE) {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    cancelNextEpisode()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }

            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    if (painelEpisodios.visibility == View.VISIBLE) {
                        painelEpisodios.visibility = View.GONE
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (tipoMedia == "serie" && painelEpisodios.visibility == View.GONE) {
                        if (btnSkipIntro.visibility == View.VISIBLE && !btnSkipIntro.hasFocus()) {
                            btnSkipIntro.requestFocus()
                            return true
                        }
                        painelEpisodios.visibility = View.VISIBLE
                        recyclerPainelEpisodios.requestFocus()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (painelEpisodios.visibility == View.GONE && !btnSkipIntro.hasFocus()) {
                        val atual = exoPlayer?.currentPosition ?: 0L
                        exoPlayer?.seekTo(atual + 10000L)
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (painelEpisodios.visibility == View.GONE && !btnSkipIntro.hasFocus()) {
                        val atual = exoPlayer?.currentPosition ?: 0L
                        exoPlayer?.seekTo(if (atual - 10000L > 0) atual - 10000L else 0L)
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (painelEpisodios.visibility == View.GONE && !btnSkipIntro.hasFocus()) {
                        if (exoPlayer?.isPlaying == true) exoPlayer?.pause() else exoPlayer?.play()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // =========================================================================
    // MÁGICA: SALVAMENTO 100% BLINDADO USANDO ESTRUTURA DE MAPA SEGURO
    // =========================================================================
    private fun salvarProgressoNoFirebase() {
        val idToSave = if (tipoMedia == "serie") parentSeriesId else mediaId
        if (username.isEmpty() || idToSave.isEmpty()) return
        
        val position = exoPlayer?.currentPosition ?: 0L
        val duration = exoPlayer?.duration ?: 0L

        // Se assistiu mais de 5 segundos, salva imediatamente! Sem frescuras.
        if (position > 5000L && duration > 0L) { 
            db.collection("usuarios").whereEqualTo("usuario", username).get()
                .addOnSuccessListener { snapshot ->
                    if (!snapshot.isEmpty) {
                        val docId = snapshot.documents[0].id
                        
                        // Cria a estrutura exata exigida pelo Firestore para atualizar dados aninhados
                        val updateData = hashMapOf(
                            "historico_vod" to hashMapOf(
                                idToSave to hashMapOf(
                                    "posicao" to position,
                                    "duracao" to duration
                                )
                            )
                        )
                        // O SetOptions.merge() garante a atualização mesmo se a pasta não existir
                        db.collection("usuarios").document(docId).set(updateData, SetOptions.merge())
                    }
                }
        }
    }

    override fun onPause() {
        super.onPause()
        salvarProgressoNoFirebase()
        exoPlayer?.pause()
        countDownTimer?.cancel()
    }

    override fun onStop() {
        super.onStop()
        salvarProgressoNoFirebase()
    }

    override fun onDestroy() {
        super.onDestroy()
        salvarProgressoNoFirebase()
        introHandler.removeCallbacks(introRunnable)
        countDownTimer?.cancel()
        exoPlayer?.release()
    }
}
