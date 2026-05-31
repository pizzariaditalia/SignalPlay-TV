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

// NOVO DATAHOLDER COM TODAS AS PASTAS
object DataHolder {
    var todasCategorias: List<CategoriaItem> = emptyList()
    var todosCanais: List<CanalItem> = emptyList()
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
        
        // Pega a categoria em que o usuário entrou
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
        
        // Se for a categoria especial "Favoritos" que criamos
        DataHolder.canaisFiltrados = if (cat.id == "FAV") {
            DataHolder.todosCanais // (Na Home passamos só os favoritos no DataHolder)
        } else {
            DataHolder.todosCanais.filter { it.categoryId == cat.id }
        }

        recyclerPainelCanais.adapter = CanalAdapter(
            listaCanais = DataHolder.canaisFiltrados,
            idsFavoritos = emptyList(),
            onClick = { canalClicado ->
                val novoIndice = DataHolder.canaisFiltrados.indexOf(canalClicado)
                if (novoIndice != -1) {
                    indiceCanalAtual = novoIndice
                    painelCanais.visibility = View.GONE
                    iniciarCanal()
                }
            },
            onLongClick = { }
        )
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
        osdEpgText.text = "Ao Vivo"
        Glide.with(this).load(canal.urlImagem).into(osdLogo)
        osdContainer.visibility = View.VISIBLE
        handlerOSD.removeCallbacks(osdRunnable)
        handlerOSD.postDelayed(osdRunnable, 4000)
        exoPlayer?.stop()
        exoPlayer?.setMediaItem(MediaItem.fromUri(canal.streamUrl))
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (painelCanais.visibility == View.VISIBLE) {
                        mudarCategoria(1) // Muda de pasta e atualiza a lista
                        return true
                    } else {
                        mudarCanal(1) // Faz zapping rápido
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
                    if (painelCanais.visibility == View.GONE) {
                        painelCanais.visibility = View.VISIBLE
                        recyclerPainelCanais.post {
                            recyclerPainelCanais.scrollToPosition(indiceCanalAtual)
                            recyclerPainelCanais.layoutManager?.findViewByPosition(indiceCanalAtual)?.requestFocus()
                        }
                        return true // Intercepta para o Android não fazer besteira
                    }
                    // SE O PAINEL JÁ ESTÁ ABERTO, RETORNA FALSE PARA O RECYCLERVIEW ROLAR PRA CIMA NORMALMENTE!
                    return super.dispatchKeyEvent(event)
                }
                KeyEvent.KEYCODE_BACK -> {
                    if (painelCanais.visibility == View.VISIBLE) {
                        painelCanais.visibility = View.GONE
                        return true
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
