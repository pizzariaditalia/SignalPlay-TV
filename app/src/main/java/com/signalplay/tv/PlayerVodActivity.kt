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

    private lateinit var db: FirebaseFirestore
    private var username: String = ""
    private var mediaId: String = ""
    private var parentSeriesId: String = "" // MÁGICA: Guarda o ID da Capa da Série!

    private var streamUrlAtual = ""
    private var tipoMedia = ""
    
    private var hasRestoredPosition = false

    private val saveProgressHandler = Handler(Looper.getMainLooper())
    private val saveProgressRunnable = object : Runnable {
        override fun run() {
            salvarProgressoNoFirebase()
            saveProgressHandler.postDelayed(this, 10000)
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

        streamUrlAtual = intent.getStringExtra("STREAM_URL") ?: ""
        tipoMedia = intent.getStringExtra("TIPO") ?: "filme"
        username = intent.getStringExtra("USERNAME") ?: ""
        parentSeriesId = intent.getStringExtra("MEDIA_ID") ?: ""
        mediaId = parentSeriesId // Por padrão, começa igual

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
            mediaId = epClicado.id // Atualiza o ID interno pro episódio rodar
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
        
        saveProgressHandler.postDelayed(saveProgressRunnable, 10000)
    }

    private fun salvarProgressoNoFirebase() {
        // Se for série, salva na Capa (parentSeriesId). Se for filme, salva normal (mediaId).
        val idToSave = if (tipoMedia == "serie") parentSeriesId else mediaId
        
        if (username.isEmpty() || idToSave.isEmpty()) return
        val position = exoPlayer?.currentPosition ?: 0L
        val duration = exoPlayer?.duration ?: 0L

        if (position > 5000L && duration > 0L) { 
            db.collection("usuarios").whereEqualTo("usuario", username).get()
                .addOnSuccessListener { snapshot ->
                    if (!snapshot.isEmpty) {
                        val docId = snapshot.documents[0].id
                        val dados = mapOf("historico_vod" to mapOf(idToSave to mapOf("posicao" to position, "duracao" to duration)))
                        db.collection("usuarios").document(docId).set(dados, SetOptions.merge())
                    }
                }
        }
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
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    if (painelEpisodios.visibility == View.VISIBLE) {
                        painelEpisodios.visibility = View.GONE
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (tipoMedia == "serie" && painelEpisodios.visibility == View.GONE) {
                        painelEpisodios.visibility = View.VISIBLE
                        recyclerPainelEpisodios.requestFocus()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (painelEpisodios.visibility == View.GONE) {
                        val atual = exoPlayer?.currentPosition ?: 0L
                        exoPlayer?.seekTo(atual + 10000L)
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (painelEpisodios.visibility == View.GONE) {
                        val atual = exoPlayer?.currentPosition ?: 0L
                        exoPlayer?.seekTo(if (atual - 10000L > 0) atual - 10000L else 0L)
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (painelEpisodios.visibility == View.GONE) {
                        if (exoPlayer?.isPlaying == true) exoPlayer?.pause() else exoPlayer?.play()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        super.onPause()
        salvarProgressoNoFirebase()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        saveProgressHandler.removeCallbacks(saveProgressRunnable)
        salvarProgressoNoFirebase()
        exoPlayer?.release()
    }
}
