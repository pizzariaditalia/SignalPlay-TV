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
import com.google.firebase.firestore.FirebaseFirestore

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

    private var modoAspectoAtual = 0
    private var tipoMedia = ""
    private var streamUrlAtual = ""
    
    private var isSeeking = false
    private var currentSeekPos = 0L
    private var hasRestoredPosition = false

    private val handlerAviso = Handler(Looper.getMainLooper())
    private val avisoRunnable = Runnable { tvAvisoAspecto.visibility = View.GONE }

    // Salvamento periódico a cada 10 segundos
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
        mediaId = intent.getStringExtra("MEDIA_ID") ?: ""

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
            mediaId = epClicado.id // Atualiza o ID para o episódio correto
            hasRestoredPosition = false
            iniciarVideo()
        }
    }

    private fun inicializarPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build()
        playerViewVod.player = exoPlayer
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING) progressBarVod.visibility = View.VISIBLE
                else if (playbackState == Player.STATE_READY) {
                    progressBarVod.visibility = View.GONE
                    
                    // Restaura a posição na primeira vez que o vídeo carrega
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

    // ==========================================
    // MAGIA DO CONTINUAR ASSISTINDO (FIREBASE)
    // ==========================================
    private fun salvarProgressoNoFirebase() {
        if (username.isEmpty() || mediaId.isEmpty()) return
        val position = exoPlayer?.currentPosition ?: 0L
        val duration = exoPlayer?.duration ?: 0L

        if (position > 5000L && duration > 0L) { // Só salva se passou de 5 segundos
            db.collection("usuarios").whereEqualTo("usuario", username).get()
                .addOnSuccessListener { snapshot ->
                    if (!snapshot.isEmpty) {
                        val docId = snapshot.documents[0].id
                        // Salva num mapa usando o ID do filme/episódio como chave
                        db.collection("usuarios").document(docId)
                            .update("historico_vod.$mediaId.posicao", position,
                                    "historico_vod.$mediaId.duracao", duration)
                    }
                }
        }
    }

    private fun restaurarProgressoDoFirebase() {
        if (username.isEmpty() || mediaId.isEmpty()) return
        db.collection("usuarios").whereEqualTo("usuario", username).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    val historico = doc.get("historico_vod") as? Map<String, Map<String, Long>>
                    val dadosMedia = historico?.get(mediaId)
                    
                    if (dadosMedia != null) {
                        val posicaoSalva = dadosMedia["posicao"] ?: 0L
                        if (posicaoSalva > 0L) {
                            exoPlayer?.seekTo(posicaoSalva)
                        }
                    }
                }
            }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // [O seu código de dispatchKeyEvent original permanece intocado aqui]
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        super.onPause()
        salvarProgressoNoFirebase() // Salva antes de sair
    }

    override fun onDestroy() {
        super.onDestroy()
        saveProgressHandler.removeCallbacks(saveProgressRunnable)
        salvarProgressoNoFirebase()
        exoPlayer?.release()
    }
}
