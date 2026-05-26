package com.tv.signalplay

import android.content.Context
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
import java.util.Locale

class PlayerActivity : FragmentActivity() {

    private var exoPlayer: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var controlsOverlay: RelativeLayout
    private lateinit var panelPlaylist: LinearLayout
    private lateinit var panelEpg: LinearLayout
    private lateinit var rvPlaylist: RecyclerView
    private lateinit var rvEpg: RecyclerView
    
    private lateinit var btnAjustar: Button
    private lateinit var btnEsticar: Button
    private lateinit var btnModalPlaylist: Button
    private lateinit var btnModalEpg: Button

    private val handler = Handler(Looper.getMainLooper())
    private var isOverlayVisible = true

    private var urlServ = ""; private var xtUser = ""; private var xtPass = ""
    private var currentType = "live"
    private var currentStreamId = 0
    private var currentTitle = ""
    private var currentExt = "mp4"

    private var todosCanaisDoServidor: List<XtreamLive> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.playerView)
        controlsOverlay = findViewById(R.id.controlsOverlay)
        panelPlaylist = findViewById(R.id.panelPlaylist)
        panelEpg = findViewById(R.id.panelEpg)
        rvPlaylist = findViewById(R.id.rvPlaylist)
        rvEpg = findViewById(R.id.rvEpg)
        
        btnAjustar = findViewById(R.id.btnAjustar)
        btnEsticar = findViewById(R.id.btnEsticar)
        btnModalPlaylist = findViewById(R.id.btnModalPlaylist)
        btnModalEpg = findViewById(R.id.btnModalEpg)

        rvPlaylist.layoutManager = LinearLayoutManager(this)
        rvEpg.layoutManager = LinearLayoutManager(this)

        urlServ = intent.getStringExtra("URL") ?: ""
        xtUser = intent.getStringExtra("XTREAM_USER") ?: ""
        xtPass = intent.getStringExtra("XTREAM_PASS") ?: ""
        currentType = intent.getStringExtra("TYPE") ?: "live"
        currentStreamId = intent.getIntExtra("STREAM_ID", 0)
        currentTitle = intent.getStringExtra("TITLE") ?: ""
        currentExt = intent.getStringExtra("EXTENSION") ?: "mp4"

        findViewById<TextView>(R.id.txtPlayerTitulo).text = currentTitle

        configurarBotoesPremium()
        iniciarVideoExoPlayer()
        
        if (currentType == "live") {
            carregarDadosEmPlanoDeFundo()
        } else { 
            btnModalPlaylist.visibility = View.GONE
            btnModalEpg.visibility = View.GONE
            findViewById<TextView>(R.id.txtOsdEpgAtual).visibility = View.GONE 
        }
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
        
        // Pulo do Gato: Retomar o filme de onde parou
        val resumePos = intent.getLongExtra("RESUME_POSITION", 0L)
        if (resumePos > 0) exoPlayer?.seekTo(resumePos)

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
        atualizarLinhaDoTempoESalvarProgresso()
    }

    // A MÁGICA: Agora salva o tempo na memória da TV a cada 2 segundos!
    private fun atualizarLinhaDoTempoESalvarProgresso() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                exoPlayer?.let {
                    val duration = it.duration; val position = it.currentPosition
                    if (duration > 0) {
                        findViewById<ProgressBar>(R.id.progressBarTempo).apply { max = 100; progress = ((position * 100) / duration).toInt() }
                        findViewById<TextView>(R.id.txtTempoAtual).text = formatarTempo(position)
                        findViewById<TextView>(R.id.txtTempoTotal).text = formatarTempo(duration)
                        
                        // Salvar Progresso (se assistiu mais de 5s)
                        if ((currentType == "vod" || currentType == "series") && position > 5000) {
                            salvarProgressoVOD(currentStreamId, currentType, position, duration)
                        }
                    }
                }
                handler.postDelayed(this, 2000)
            }
        }, 2000)
    }

    private fun salvarProgressoVOD(id: Int, tipo: String, tempo: Long, duracao: Long) {
        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
        val histJson = prefs.getString("iptv_continuar_vod", "[]")
        val typeMap = object : TypeToken<MutableList<MutableMap<String, Any>>>(){}.type
        val history: MutableList<MutableMap<String, Any>> = Gson().fromJson(histJson, typeMap) ?: mutableListOf()
        
        val iterator = history.iterator()
        while(iterator.hasNext()) {
            val item = iterator.next()
            val itemId = (item["id"] as? Number)?.toInt() ?: 0
            if(itemId == id) iterator.remove()
        }
        
        history.add(0, mutableMapOf("id" to id, "tipo" to tipo, "tempo" to tempo, "duracao" to duracao))
        if(history.size > 20) history.removeAt(history.size - 1) // Mantém apenas os 20 últimos
        
        prefs.edit().putString("iptv_continuar_vod", Gson().toJson(history)).apply()
    }

    // A cor agora é no texto, não no fundo!
    private fun configurarBotoesPremium() {
        val listenerFoco = View.OnFocusChangeListener { v, focus -> 
            if(focus) { v.animate().scaleX(1.1f).start(); (v as Button).setTextColor(Color.parseColor("#ffcc00")) } 
            else { v.animate().scaleX(1.0f).start(); (v as Button).setTextColor(Color.WHITE) }
        }

        listOf(btnAjustar, btnEsticar, btnModalPlaylist, btnModalEpg).forEach { it.setOnFocusChangeListener(listenerFoco) }

        btnAjustar.setOnClickListener {
            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            btnAjustar.setTextColor(Color.parseColor("#ffcc00"))
            btnEsticar.setTextColor(Color.WHITE)
        }
        btnEsticar.setOnClickListener {
            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            btnEsticar.setTextColor(Color.parseColor("#ffcc00"))
            btnAjustar.setTextColor(Color.WHITE)
        }
        btnModalPlaylist.setOnClickListener {
            controlsOverlay.visibility = View.GONE; panelEpg.visibility = View.GONE; panelPlaylist.visibility = View.VISIBLE; rvPlaylist.requestFocus()
        }
        btnModalEpg.setOnClickListener {
            controlsOverlay.visibility = View.GONE; panelPlaylist.visibility = View.GONE; panelEpg.visibility = View.VISIBLE; rvEpg.requestFocus()
        }
    }

    private fun carregarDadosEmPlanoDeFundo() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val api = XtreamClient.create(urlServ)
                val respEpg = api.getShortEpg(xtUser, xtPass, id = currentStreamId)
                withContext(Dispatchers.Main) { processarEpg(respEpg) }

                if (todosCanaisDoServidor.isEmpty()) {
                    val respCanais = api.getLiveStreams(xtUser, xtPass)
                    if (respCanais.isJsonArray) todosCanaisDoServidor = Gson().fromJson(respCanais, object : TypeToken<List<XtreamLive>>() {}.type)
                }
                withContext(Dispatchers.Main) {
                    val canalAtual = todosCanaisDoServidor.find { it.stream_id == currentStreamId }
                    if (canalAtual != null) {
                        if (!canalAtual.stream_icon.isNullOrEmpty()) Glide.with(this@PlayerActivity).load(canalAtual.stream_icon).into(findViewById<ImageView>(R.id.imgOsdLogo))
                        rvPlaylist.adapter = PlaylistAdapter(todosCanaisDoServidor.filter { it.category_id == canalAtual.category_id })
                    }
                }
            } catch (e: Exception) { }
        }
    }

    private fun processarEpg(respEpg: com.google.gson.JsonElement) {
        try {
            if (respEpg.isJsonObject && respEpg.asJsonObject.has("epg_listings")) {
                val arrayEpg = respEpg.asJsonObject.getAsJsonArray("epg_listings")
                val listaEpg: List<XtreamEpgListing> = Gson().fromJson(arrayEpg, object : TypeToken<List<XtreamEpgListing>>() {}.type)
                if (listaEpg.isNotEmpty()) {
                    findViewById<TextView>(R.id.txtOsdEpgAtual).text = decodificarBase64(listaEpg[0].title)
                    if (listaEpg.size > 1) findViewById<TextView>(R.id.txtOsdEpgProx).text = "A seguir: " + decodificarBase64(listaEpg[1].title)
                    rvEpg.adapter = EpgAdapter(listaEpg)
                } else {
                    findViewById<TextView>(R.id.txtOsdEpgAtual).text = "Programação Indisponível"
                    rvEpg.adapter = EpgAdapter(emptyList())
                }
            }
        } catch (e: Exception) {}
    }

    private fun decodificarBase64(s: String?): String = try { String(Base64.decode(s?.trim() ?: "", Base64.DEFAULT)) } catch (e: Exception) { s ?: "Programa" }

    private fun formatarTempo(ms: Long): String {
        val tSec = ms / 1000; return String.format("%02d:%02d", tSec / 60, tSec % 60)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (panelPlaylist.visibility == View.VISIBLE || panelEpg.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                panelPlaylist.visibility = View.GONE; panelEpg.visibility = View.GONE; mostrarControles()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }
        mostrarControles(); esconderControlesAposDelay()
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { exoPlayer?.let { it.playWhenReady = !it.playWhenReady }; return true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { exoPlayer?.let { it.seekTo(it.currentPosition - 10000) }; mostrarAvisoTempo("⏪ -10s"); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { exoPlayer?.let { it.seekTo(it.currentPosition + 10000) }; mostrarAvisoTempo("+10s ⏩"); return true }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { if (isOverlayVisible) finish() else mostrarControles(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun mostrarAvisoTempo(texto: String) {
        val fb = findViewById<TextView>(R.id.txtFeedbackAvanco)
        fb.text = texto; fb.visibility = View.VISIBLE
        handler.postDelayed({ fb.visibility = View.GONE }, 1000)
    }

    private fun mostrarControles() { controlsOverlay.visibility = View.VISIBLE; isOverlayVisible = true }
    private val hideRunnable = Runnable { if (exoPlayer?.isPlaying == true && panelPlaylist.visibility == View.GONE && panelEpg.visibility == View.GONE) { controlsOverlay.visibility = View.GONE; isOverlayVisible = false } }
    private fun esconderControlesAposDelay() { handler.removeCallbacks(hideRunnable); handler.postDelayed(hideRunnable, 4000) }

    override fun onDestroy() { super.onDestroy(); handler.removeCallbacksAndMessages(null); exoPlayer?.release(); exoPlayer = null }

    inner class PlaylistAdapter(private val lista: List<XtreamLive>) : RecyclerView.Adapter<PlaylistAdapter.Holder>() {
        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val txtNome: TextView = view.findViewById(R.id.txtNomeCanalLista)
            val imgLogo: ImageView = view.findViewById(R.id.imgLogoCanalLista)
            init {
                view.setOnFocusChangeListener { v, focus -> if(focus) { v.setBackgroundColor(Color.parseColor("#33FFFFFF")); v.animate().scaleX(1.03f).start() } else { v.setBackgroundResource(R.drawable.bg_card_premium_normal); v.animate().scaleX(1.0f).start() } }
                view.setOnClickListener {
                    val c = lista[bindingAdapterPosition]; currentStreamId = c.stream_id; currentTitle = c.name; findViewById<TextView>(R.id.txtPlayerTitulo).text = currentTitle; findViewById<TextView>(R.id.txtOsdEpgAtual).text = "Sintonizando..."; findViewById<TextView>(R.id.txtOsdEpgProx).text = ""
                    panelPlaylist.visibility = View.GONE; mostrarControles(); iniciarVideoExoPlayer(); carregarDadosEmPlanoDeFundo()
                }
            }
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_player_playlist, parent, false))
        override fun onBindViewHolder(holder: Holder, position: Int) { val c = lista[position]; holder.txtNome.text = c.name; if (!c.stream_icon.isNullOrEmpty()) Glide.with(holder.itemView.context).load(c.stream_icon).into(holder.imgLogo) else holder.imgLogo.setImageDrawable(null) }
        override fun getItemCount() = lista.size
    }

    inner class EpgAdapter(private val lista: List<XtreamEpgListing>) : RecyclerView.Adapter<EpgAdapter.Holder>() {
        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val txtTitulo: TextView = view.findViewById(R.id.txtEpgTituloLista); val txtHora: TextView = view.findViewById(R.id.txtEpgHorarioLista)
            init { view.setOnFocusChangeListener { v, focus -> if(focus) v.setBackgroundColor(Color.parseColor("#33FFFFFF")) else v.setBackgroundResource(R.drawable.bg_card_premium_normal) } }
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_player_epg, parent, false))
        override fun onBindViewHolder(holder: Holder, position: Int) { val e = lista[position]; holder.txtTitulo.text = decodificarBase64(e.title); val fI = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()); val fO = SimpleDateFormat("HH:mm", Locale.getDefault()); try { val st = fI.parse(e.start_timestamp ?: ""); val en = fI.parse(e.stop_timestamp ?: ""); if(st != null && en != null) holder.txtHora.text = "${fO.format(st)} - ${fO.format(en)}" } catch(ex: Exception) {} }
        override fun getItemCount() = lista.size
    }
}
