package com.signalplay.tv

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

object DataHolder {
    var canaisZapping: List<CanalItem> = emptyList()
    var categoriaAtualNome: String = ""
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

        tvPainelTitulo.text = DataHolder.categoriaAtualNome
        recyclerPainelCanais.layoutManager = LinearLayoutManager(this)
        
        recyclerPainelCanais.adapter = CanalAdapter(
            listaCanais = DataHolder.canaisZapping,
            idsFavoritos = emptyList(),
            onClick = { canalClicado ->
                val novoIndice = DataHolder.canaisZapping.indexOf(canalClicado)
                if (novoIndice != -1) {
                    indiceCanalAtual = novoIndice
                    painelCanais.visibility = View.GONE
                    iniciarCanal()
                }
            },
            onLongClick = { }
        )

        indiceCanalAtual = intent.getIntExtra("INDICE_CANAL", 0)

        inicializarPlayer()
        iniciarCanal()
    }

    private fun inicializarPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build()
        playerView.player = exoPlayer

        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING) {
                    progressBar.visibility = View.VISIBLE
                } else if (playbackState == Player.STATE_READY) {
                    progressBar.visibility = View.GONE
                }
            }
        })
    }

    private fun iniciarCanal() {
        if (DataHolder.canaisZapping.isEmpty()) return

        val canal = DataHolder.canaisZapping[indiceCanalAtual]
        
        osdChannelName.text = canal.nome
        osdEpgText.text = "Programação em breve..." 
        Glide.with(this).load(canal.urlImagem).into(osdLogo)
        
        osdContainer.visibility = View.VISIBLE
        
        handlerOSD.removeCallbacks(osdRunnable)
        handlerOSD.postDelayed(osdRunnable, 4000)

        exoPlayer?.stop()
        val mediaItem = MediaItem.fromUri(canal.streamUrl)
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (painelCanais.visibility == View.GONE) {
                        mudarCanal(1)
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (painelCanais.visibility == View.GONE) {
                        mudarCanal(-1)
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (painelCanais.visibility == View.GONE) {
                        painelCanais.visibility = View.VISIBLE
                        
                        // CORREÇÃO: Força o sistema a colocar a luz do controle no canal atual!
                        recyclerPainelCanais.post {
                            recyclerPainelCanais.scrollToPosition(indiceCanalAtual)
                            val viewToFocus = recyclerPainelCanais.layoutManager?.findViewByPosition(indiceCanalAtual)
                            if (viewToFocus != null) {
                                viewToFocus.requestFocus()
                            } else {
                                recyclerPainelCanais.requestFocus()
                            }
                        }
                    } else {
                        painelCanais.visibility = View.GONE
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun mudarCanal(direcao: Int) {
        indiceCanalAtual += direcao
        
        if (indiceCanalAtual >= DataHolder.canaisZapping.size) {
            indiceCanalAtual = 0
        } else if (indiceCanalAtual < 0) {
            indiceCanalAtual = DataHolder.canaisZapping.size - 1
        }
        
        iniciarCanal()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
}
