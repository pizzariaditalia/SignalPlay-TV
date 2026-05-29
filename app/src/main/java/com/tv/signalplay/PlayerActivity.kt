package com.tv.signalplay

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.VideoView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Timer
import java.util.TimerTask

class PlayerActivity : FragmentActivity() {

    private lateinit var videoView: VideoView
    private lateinit var playerLoading: ProgressBar
    private lateinit var panelCanaisLateral: LinearLayout
    private lateinit var rvCanaisLaterais: RecyclerView
    private lateinit var txtPlayerTitulo: TextView
    private lateinit var boxInfoOverlay: LinearLayout

    private var listaCanais: List<XtreamLive> = listOf()
    private var canalAtualIndex = 0
    
    private var urlServ = ""
    private var xtUser = ""
    private var xtPass = ""
    private var tipoMidia = "live"
    
    private var timerControles: Timer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        videoView = findViewById(R.id.videoView)
        playerLoading = findViewById(R.id.playerLoading)
        panelCanaisLateral = findViewById(R.id.panelCanaisLateral)
        rvCanaisLaterais = findViewById(R.id.rvCanaisLaterais)
        txtPlayerTitulo = findViewById(R.id.txtPlayerTitulo)
        boxInfoOverlay = findViewById(R.id.boxInfoOverlay)

        // Resgata os parâmetros do fluxo de navegação
        urlServ = intent.getStringExtra("URL") ?: ""
        xtUser = intent.getStringExtra("XTREAM_USER") ?: ""
        xtPass = intent.getStringExtra("XTREAM_PASS") ?: ""
        tipoMidia = intent.getStringExtra("TYPE") ?: "live"
        val streamId = intent.getIntExtra("STREAM_ID", 0)
        val titulo = intent.getStringExtra("TITLE") ?: "Canal"

        txtPlayerTitulo.text = titulo

        // Recupera a lista de canais em cache local para a troca rápida de canais (Zapping)
        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
        val listaJson = prefs.getString("iptv_canais_cache", "[]")
        
        try {
            listaCanais = Gson().fromJson(listaJson, object : TypeToken<List<XtreamLive>>() {}.type) ?: listOf()
        } catch (e: Exception) {
            listaCanais = listOf()
        }

        canalAtualIndex = listaCanais.indexOfFirst { it.stream_id == streamId }.let { if (it == -1) 0 else it }

        // Configuração ultra-leve da lista lateral de canais
        rvCanaisLaterais.layoutManager = LinearLayoutManager(this)
        rvCanaisLaterais.setHasFixedSize(true)
        rvCanaisLaterais.adapter = CanalLateralAdapter(listaCanais)

        // Listeners nativos de erro e buffer para evitar congelamentos de tela
        videoView.setOnPreparedListener { mp ->
            mp.setOnInfoListener { _, what, _ ->
                when (what) {
                    android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START -> playerLoading.visibility = View.VISIBLE
                    android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END -> playerLoading.visibility = View.GONE
                }
                true
            }
            playerLoading.visibility = View.GONE
            videoView.start()
        }

        videoView.setOnErrorListener { _, _, _ ->
            playerLoading.visibility = View.GONE
            Toast.makeText(this, "Erro ao reproduzir este sinal.", Toast.LENGTH_SHORT).show()
            true
        }

        prepararEPlay(streamId, titulo)
        mostrarControlesTemporarios()
    }

    private fun prepararEPlay(streamId: Int, titulo: String) {
        playerLoading.visibility = View.VISIBLE
        txtPlayerTitulo.text = titulo
        
        // Montagem limpa do link de streaming direto do servidor Xtream
        val sufixo = if (tipoMidia == "series") "series" else if (tipoMidia == "vod") "movie" else "live"
        val extensao = if (tipoMidia == "live") ".ts" else ".mp4"
        
        val urlFinal = "$urlServ/$sufixo/$xtUser/$xtPass/$streamId$extensao"
        
        videoView.stopPlayback()
        videoView.setVideoURI(Uri.parse(urlFinal))
    }

    private fun mostrarControlesTemporarios() {
        timerControles?.cancel()
        boxInfoOverlay.visibility = View.VISIBLE
        
        timerControles = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    runOnUiThread {
                        boxInfoOverlay.visibility = View.GONE
                    }
                }
            }, 4000) // Some em 4 segundos automaticamente
        }
    }

    // MAPEAMENTO DE TECLAS DO CONTROLE REMOTO (NATIVO E ULTRA RÁPIDO)
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> { // Canal Anterior (Zapping)
                if (listaCanais.isNotEmpty() && tipoMidia == "live") {
                    canalAtualIndex = if (canalAtualIndex - 1 < 0) listaCanais.size - 1 else canalAtualIndex - 1
                    val prox = listaCanais[canalAtualIndex]
                    prepararEPlay(prox.stream_id, prox.name)
                    mostrarControlesTemporarios()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> { // Próximo Canal (Zapping)
                if (listaCanais.isNotEmpty() && tipoMidia == "live") {
                    canalAtualIndex = if (canalAtualIndex + 1 >= listaCanais.size) 0 else canalAtualIndex + 1
                    val prox = listaCanais[canalAtualIndex]
                    prepararEPlay(prox.stream_id, prox.name)
                    mostrarControlesTemporarios()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> { 
                // Abre a lista rápida na lateral da tela
                if (panelCanaisLateral.visibility == View.GONE && tipoMidia == "live") {
                    panelCanaisLateral.visibility = View.VISIBLE
                    rvCanaisLaterais.requestFocus()
                    rvCanaisLaterais.scrollToPosition(canalAtualIndex)
                }
                return true
            }
            KeyEvent.KEYCODE_BACK -> { // Botão Voltar
                if (panelCanaisLateral.visibility == View.VISIBLE) {
                    panelCanaisLateral.visibility = View.GONE
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        timerControles?.cancel()
        videoView.stopPlayback()
        super.onDestroy()
    }

    // ADAPTER ULTRA LEVE DA REYCLERVIEW LATERAL
    inner class CanalLateralAdapter(private val canais: List<XtreamLive>) : 
        RecyclerView.Adapter<CanalLateralAdapter.CanalHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CanalHolder {
            val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
            view.isFocusable = true
            return CanalHolder(view)
        }

        override fun onBindViewHolder(holder: CanalHolder, position: Int) {
            val canal = canais[position]
            val text = holder.itemView.findViewById<TextView>(android.R.id.text1)
            text.text = canal.name
            text.setTextColor(Color.parseColor("#CCCCCC"))
            text.textSize = 14sp

            holder.itemView.setOnClickListener {
                canalAtualIndex = position
                prepararEPlay(canal.stream_id, canal.name)
                panelCanaisLateral.visibility = View.GONE
                mostrarControlesTemporarios()
            }

            holder.itemView.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.setBackgroundColor(Color.parseColor("#FFCC00"))
                    text.setTextColor(Color.BLACK)
                } else {
                    v.setBackgroundColor(Color.TRANSPARENT)
                    text.setTextColor(Color.parseColor("#CCCCCC"))
                }
            }
        }

        override fun getItemCount() = canais.size

        inner class CanalHolder(view: View) : RecyclerView.ViewHolder(view)
    }
}
