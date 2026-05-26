package com.tv.signalplay

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlayerActivity : FragmentActivity() {

    private var exoPlayer: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var controlsOverlay: RelativeLayout
    private lateinit var panelPlaylist: LinearLayout
    private lateinit var panelEpg: LinearLayout
    private lateinit var rvPlaylist: RecyclerView
    private lateinit var rvEpg: RecyclerView

    private lateinit var txtPlayerTitulo: TextView
    private lateinit var txtOsdEpgAtual: TextView
    private lateinit var txtOsdEpgProx: TextView
    private lateinit var imgOsdLogo: ImageView
    
    private lateinit var btnAjustar: Button
    private lateinit var btnEsticar: Button
    private lateinit var btnModalPlaylist: Button
    private lateinit var btnModalEpg: Button

    private val handler = Handler(Looper.getMainLooper())
    private var isOverlayVisible = true

    // Dados do Canal Atual
    private var urlServ = ""; private var xtUser = ""; private var xtPass = ""
    private var currentType = "live"
    private var currentStreamId = 0
    private var currentTitle = ""
    private var currentExt = "mp4"

    // Memória da Playlist
    private var todosCanaisDoServidor: List<XtreamLive> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        // Bind UI
        playerView = findViewById(R.id.playerView)
        controlsOverlay = findViewById(R.id.controlsOverlay)
        panelPlaylist = findViewById(R.id.panelPlaylist)
        panelEpg = findViewById(R.id.panelEpg)
        rvPlaylist = findViewById(R.id.rvPlaylist)
        rvEpg = findViewById(R.id.rvEpg)
        
        txtPlayerTitulo = findViewById(R.id.txtPlayerTitulo)
        txtOsdEpgAtual = findViewById(R.id.txtOsdEpgAtual)
        txtOsdEpgProx = findViewById(R.id.txtOsdEpgProx)
        imgOsdLogo = findViewById(R.id.imgOsdLogo)

        btnAjustar = findViewById(R.id.btnAjustar)
        btnEsticar = findViewById(R.id.btnEsticar)
        btnModalPlaylist = findViewById(R.id.btnModalPlaylist)
        btnModalEpg = findViewById(R.id.btnModalEpg)

        rvPlaylist.layoutManager = LinearLayoutManager(this)
        rvEpg.layoutManager = LinearLayoutManager(this)

        urlServ = intent.getStringExtra("URL")?.trimEnd('/') ?: ""
        xtUser = intent.getStringExtra("XTREAM_USER") ?: ""
        xtPass = intent.getStringExtra("XTREAM_PASS") ?: ""
        currentType = intent.getStringExtra("TYPE") ?: "live"
        currentStreamId = intent.getIntExtra("STREAM_ID", 0)
        currentTitle = intent.getStringExtra("TITLE") ?: ""
        currentExt = intent.getStringExtra("EXTENSION") ?: "mp4"

        txtPlayerTitulo.text = currentTitle

        configurarBotoes()
        iniciarVideoExoPlayer()
        if (currentType == "live") carregarDadosEmPlanoDeFundo() else { btnModalPlaylist.visibility = View.GONE; btnModalEpg.visibility = View.GONE; txtOsdEpgAtual.visibility = View.GONE }
    }

    private fun iniciarVideoExoPlayer() {
        val videoUrl = when (currentType) {
            "live" -> "$urlServ/$xtUser/$xtPass/$currentStreamId"
            "vod" -> "$urlServ/movie/$xtUser/$xtPass/$currentStreamId.$currentExt"
            "series" -> "$urlServ/series/$xtUser/$xtPass/$currentStreamId.$currentExt"
            else -> ""
        }

        if (exoPlayer != null) { exoPlayer?.release(); exoPlayer = null }
        exoPlayer = ExoPlayer.Builder(this).build()
        playerView.player = exoPlayer

        val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = true

        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                findViewById<ProgressBar>(R.id.loadingSpinner).visibility = if (playbackState == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                findViewById<ImageView>(R.id.btnPlayPauseIcon).setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
                if (isPlaying) esconderControlesAposDelay() else mostrarControles()
            }
        })
        atualizarLinhaDoTempo()
    }

    // A MÁGICA: Baixa Playlist e EPG em segundo plano sem travar o vídeo
    private fun carregarDadosEmPlanoDeFundo() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val api = XtreamClient.create(urlServ)
                
                // 1. OSD EPG: Busca a programação do canal
                val respEpg = api.getShortEpg(xtUser, xtPass, id = currentStreamId)
                withContext(Dispatchers.Main) { processarEpg(respEpg) }

                // 2. Playlist: Se ainda não baixou os canais, baixa agora
                if (todosCanaisDoServidor.isEmpty()) {
                    val respCanais = api.getLiveStreams(xtUser, xtPass)
                    if (respCanais.isJsonArray) {
                        val tipoLista = object : TypeToken<List<XtreamLive>>() {}.type
                        todosCanaisDoServidor = Gson().fromJson(respCanais, tipoLista)
                    }
                }

                withContext(Dispatchers.Main) {
                    val canalAtual = todosCanaisDoServidor.find { it.stream_id == currentStreamId }
                    if (canalAtual != null) {
                        if (!canalAtual.stream_icon.isNullOrEmpty()) Glide.with(this@PlayerActivity).load(canalAtual.stream_icon).into(imgOsdLogo)
                        
                        // Filtra a playlist apenas com a categoria do canal atual
                        val playlistFiltrada = todosCanaisDoServidor.filter { it.category_id == canalAtual.category_id }
                        rvPlaylist.adapter = PlaylistAdapter(playlistFiltrada)
                    }
                }
            } catch (e: Exception) { }
        }
    }

    private fun processarEpg(respEpg: com.google.gson.JsonElement) {
        try {
            if (respEpg.isJsonObject && respEpg.asJsonObject.has("epg_listings")) {
                val arrayEpg = respEpg.asJsonObject.getAsJsonArray("epg_listings")
                val tipoEpg = object : TypeToken<List<XtreamEpgListing>>() {}.type
                val listaEpg: List<XtreamEpgListing> = Gson().fromJson(arrayEpg, tipoEpg)

                if (listaEpg.isNotEmpty()) {
                    val tituloAtual = decodificarBase64(listaEpg[0].title)
                    txtOsdEpgAtual.text = tituloAtual
                    if (listaEpg.size > 1) txtOsdEpgProx.text = "A seguir: " + decodificarBase64(listaEpg[1].title)
                    rvEpg.adapter = EpgAdapter(listaEpg)
                } else {
                    txtOsdEpgAtual.text = "Programação Indisponível"
                    rvEpg.adapter = EpgAdapter(emptyList())
                }
            }
        } catch (e: Exception) {}
    }

    private fun decodificarBase64(base64Str: String?): String {
        if (base64Str.isNullOrEmpty()) return "Programa"
        return try { String(Base64.decode(base64Str.trim(), Base64.DEFAULT)) } catch (e: Exception) { base64Str }
    }

    // --- CONFIGURAÇÃO DOS BOTÕES E MODAIS ---
    private fun configurarBotoes() {
        btnAjustar.setOnFocusChangeListener { v, focus -> if(focus) v.animate().scaleX(1.1f).start() else v.animate().scaleX(1.0f).start() }
        btnEsticar.setOnFocusChangeListener { v, focus -> if(focus) v.animate().scaleX(1.1f).start() else v.animate().scaleX(1.0f).start() }
        btnModalPlaylist.setOnFocusChangeListener { v, focus -> if(focus) v.animate().scaleX(1.1f).start() else v.animate().scaleX(1.0f).start() }
        btnModalEpg.setOnFocusChangeListener { v, focus -> if(focus) v.animate().scaleX(1.1f).start() else v.animate().scaleX(1.0f).start() }

        btnAjustar.setOnClickListener {
            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            btnAjustar.setTextColor(Color.BLACK); btnAjustar.setBackgroundColor(Color.parseColor("#ffcc00"))
            btnEsticar.setTextColor(Color.WHITE); btnEsticar.setBackgroundResource(R.drawable.bg_card_premium_normal)
        }

        btnEsticar.setOnClickListener {
            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            btnEsticar.setTextColor(Color.BLACK); btnEsticar.setBackgroundColor(Color.parseColor("#ffcc00"))
            btnAjustar.setTextColor(Color.WHITE); btnAjustar.setBackgroundResource(R.drawable.bg_card_premium_normal)
        }

        btnModalPlaylist.setOnClickListener {
            controlsOverlay.visibility = View.GONE
            panelEpg.visibility = View.GONE
            panelPlaylist.visibility = View.VISIBLE
            rvPlaylist.requestFocus() // Joga o foco pro controle remoto
        }

        btnModalEpg.setOnClickListener {
            controlsOverlay.visibility = View.GONE
            panelPlaylist.visibility = View.GONE
            panelEpg.visibility = View.VISIBLE
            rvEpg.requestFocus() // Joga o foco pro controle remoto
        }
    }

    // --- ADAPTERS DOS MODAIS ---
    inner class PlaylistAdapter(private val lista: List<XtreamLive>) : RecyclerView.Adapter<PlaylistAdapter.Holder>() {
        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val txtNome: TextView = view.findViewById(R.id.txtNomeCanalLista)
            val imgLogo: ImageView = view.findViewById(R.id.imgLogoCanalLista)
            init {
                view.setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) { v.setBackgroundColor(Color.parseColor("#33FFFFFF")); v.animate().scaleX(1.03f).scaleY(1.03f).start() }
                    else { v.setBackgroundResource(R.drawable.bg_card_premium_normal); v.animate().scaleX(1.0f).scaleY(1.0f).start() }
                }
                view.setOnClickListener {
                    val canal = lista[bindingAdapterPosition]
                    // Troca de canal sem sair da tela!
                    currentStreamId = canal.stream_id
                    currentTitle = canal.name
                    txtPlayerTitulo.text = currentTitle
                    txtOsdEpgAtual.text = "Sintonizando..."
                    txtOsdEpgProx.text = ""
                    
                    panelPlaylist.visibility = View.GONE
                    mostrarControles()
                    iniciarVideoExoPlayer() // Reinicia o motor de vídeo
                    carregarDadosEmPlanoDeFundo() // Baixa o novo EPG
                }
            }
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_player_playlist, parent, false))
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val c = lista[position]; holder.txtNome.text = c.name
            if (!c.stream_icon.isNullOrEmpty()) Glide.with(holder.itemView.context).load(c.stream_icon).into(holder.imgLogo) else holder.imgLogo.setImageDrawable(null)
        }
        override fun getItemCount() = lista.size
    }

    inner class EpgAdapter(private val lista: List<XtreamEpgListing>) : RecyclerView.Adapter<EpgAdapter.Holder>() {
        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val txtTitulo: TextView = view.findViewById(R.id.txtEpgTituloLista)
            val txtHora: TextView = view.findViewById(R.id.txtEpgHorarioLista)
            init {
                view.setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) v.setBackgroundColor(Color.parseColor("#33FFFFFF")) else v.setBackgroundResource(R.drawable.bg_card_premium_normal)
                }
            }
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_player_epg, parent, false))
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val e = lista[position]
            holder.txtTitulo.text = decodificarBase64(e.title)
            val formatInput = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val formatOutput = SimpleDateFormat("HH:mm", Locale.getDefault())
            try {
                val start = formatInput.parse(e.start_timestamp ?: "")
                val end = formatInput.parse(e.stop_timestamp ?: "")
                if (start != null && end != null) holder.txtHora.text = "${formatOutput.format(start)} - ${formatOutput.format(end)}" else holder.txtHora.text = ""
            } catch (ex: Exception) { holder.txtHora.text = "" }
        }
        override fun getItemCount() = lista.size
    }

    // --- MAPA DO CONTROLE REMOTO (D-PAD) ---
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Se um dos modais estiver aberto, o botão Voltar (BACK) apenas fecha o modal!
        if (panelPlaylist.visibility == View.VISIBLE || panelEpg.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                panelPlaylist.visibility = View.GONE
                panelEpg.visibility = View.GONE
                mostrarControles()
                return true
            }
            return super.onKeyDown(keyCode, event) // Deixa as setas navegarem nas listas
        }

        // Comportamento normal do vídeo
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
        val txtFeedbackAvanco = findViewById<TextView>(R.id.txtFeedbackAvanco)
        txtFeedbackAvanco.text = texto; txtFeedbackAvanco.visibility = View.VISIBLE
        handler.postDelayed({ txtFeedbackAvanco.visibility = View.GONE }, 1000)
    }

    // --- LINHA DO TEMPO E CLEANUP ---
    private fun atualizarLinhaDoTempo() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                exoPlayer?.let {
                    val duration = it.duration; val position = it.currentPosition
                    if (duration > 0) {
                        val pb = findViewById<ProgressBar>(R.id.progressBarTempo)
                        pb.max = 100; pb.progress = ((position * 100) / duration).toInt()
                        findViewById<TextView>(R.id.txtTempoAtual).text = formatarTempo(position)
                        findViewById<TextView>(R.id.txtTempoTotal).text = formatarTempo(duration)
                    }
                }
                handler.postDelayed(this, 1000)
            }
        }, 1000)
    }

    private fun formatarTempo(ms: Long): String {
        val tSec = ms / 1000; val min = tSec / 60; val sec = tSec % 60
        return String.format("%02d:%02d", min, sec)
    }

    private fun mostrarControles() { controlsOverlay.visibility = View.VISIBLE; isOverlayVisible = true }
    private val hideRunnable = Runnable { if (exoPlayer?.isPlaying == true && panelPlaylist.visibility == View.GONE && panelEpg.visibility == View.GONE) { controlsOverlay.visibility = View.GONE; isOverlayVisible = false } }
    private fun esconderControlesAposDelay() { handler.removeCallbacks(hideRunnable); handler.postDelayed(hideRunnable, 4000) }

    override fun onDestroy() { super.onDestroy(); handler.removeCallbacksAndMessages(null); exoPlayer?.release(); exoPlayer = null }
}
