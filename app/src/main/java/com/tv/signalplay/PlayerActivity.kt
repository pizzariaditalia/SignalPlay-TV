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
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.ui.TrackSelectionDialogBuilder
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class PlayerActivity : FragmentActivity() {

    private var exoPlayer: ExoPlayer? = null; private lateinit var playerView: PlayerView; private lateinit var controlsOverlay: RelativeLayout
    private lateinit var panelPlaylist: LinearLayout; private lateinit var panelEpg: LinearLayout; private lateinit var rvPlaylist: RecyclerView; private lateinit var rvEpg: RecyclerView
    private lateinit var btnAjustar: Button; private lateinit var btnEsticar: Button; private lateinit var btnModalPlaylist: Button; private lateinit var btnModalEpg: Button
    private lateinit var btnAudio: Button; private lateinit var btnLegenda: Button; private lateinit var sepAudioAjuste: TextView
    private lateinit var btnPlayPauseIcon: ImageView
    private lateinit var btnPrev: ImageView; private lateinit var btnNext: ImageView
    
    private lateinit var txtCatAtual: TextView; private lateinit var btnCatPrev: Button; private lateinit var btnCatNext: Button
    
    private lateinit var panelNextEpisode: LinearLayout
    private lateinit var txtNextEpTitle: TextView
    private lateinit var btnAssistirProximo: Button
    private var episodiosArray: JsonArray? = null
    private var currentEpIndex: Int = -1
    private var isNextEpPanelShowing = false

    private val handler = Handler(Looper.getMainLooper()); private var isOverlayVisible = true; private var isFit = true 

    private var urlServ = ""; private var xtUser = ""; private var xtPass = ""; private var currentType = "live"
    private var currentStreamId = 0; private var currentTitle = ""; private var currentExt = "mp4"
    
    private var todosCanaisDoServidor: List<XtreamLive> = listOf()
    private var categoryContext = "Outros"
    private var categoryList = mutableListOf<String>()
    private var currentCatIndex = 0
    private var canaisDaPastaAtual: List<XtreamLive> = listOf()

    private lateinit var trackSelector: DefaultTrackSelector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.playerView); controlsOverlay = findViewById(R.id.controlsOverlay)
        panelPlaylist = findViewById(R.id.panelPlaylist); panelEpg = findViewById(R.id.panelEpg)
        rvPlaylist = findViewById(R.id.rvPlaylist); rvEpg = findViewById(R.id.rvEpg)
        
        btnAjustar = findViewById(R.id.btnAjustar); btnEsticar = findViewById(R.id.btnEsticar)
        btnModalPlaylist = findViewById(R.id.btnModalPlaylist); btnModalEpg = findViewById(R.id.btnModalEpg)
        btnAudio = findViewById(R.id.btnAudio); btnLegenda = findViewById(R.id.btnLegenda); sepAudioAjuste = findViewById(R.id.sepAudioAjuste)
        btnPlayPauseIcon = findViewById(R.id.btnPlayPauseIcon)
        btnPrev = findViewById(R.id.btnPrev); btnNext = findViewById(R.id.btnNext)
        
        txtCatAtual = findViewById(R.id.txtCatAtual); btnCatPrev = findViewById(R.id.btnCatPrev); btnCatNext = findViewById(R.id.btnCatNext)

        panelNextEpisode = findViewById(R.id.panelNextEpisode)
        txtNextEpTitle = findViewById(R.id.txtNextEpTitle)
        btnAssistirProximo = findViewById(R.id.btnAssistirProximo)

        urlServ = intent.getStringExtra("URL") ?: ""; xtUser = intent.getStringExtra("XTREAM_USER") ?: ""
        xtPass = intent.getStringExtra("XTREAM_PASS") ?: ""; currentType = intent.getStringExtra("TYPE") ?: "live"
        currentStreamId = intent.getIntExtra("STREAM_ID", 0); currentTitle = intent.getStringExtra("TITLE") ?: ""
        currentExt = intent.getStringExtra("EXTENSION") ?: "mp4"
        categoryContext = intent.getStringExtra("CATEGORY_CONTEXT") ?: "Outros"

        val epJsonString = intent.getStringExtra("EPISODES_JSON") ?: ""
        currentEpIndex = intent.getIntExtra("EP_INDEX", -1)
        if (epJsonString.isNotEmpty()) {
            try { episodiosArray = JsonParser.parseString(epJsonString).asJsonArray } catch (e: Exception) {}
        }

        findViewById<TextView>(R.id.txtPlayerTitulo).text = currentTitle
        
        if (currentType != "live") {
            btnAudio.visibility = View.VISIBLE
            btnLegenda.visibility = View.VISIBLE
            sepAudioAjuste.visibility = View.VISIBLE
        }
        
        configurarBotoesPremiumSemFundo()
        iniciarVideoExoPlayer()
        
        if (currentType == "live") { carregarDadosEmPlanoDeFundo() } 
        else { btnModalPlaylist.visibility = View.GONE; btnModalEpg.visibility = View.GONE; findViewById<TextView>(R.id.txtOsdEpgAtual).visibility = View.GONE }
    }

    private fun iniciarVideoExoPlayer() {
        val videoUrl = when (currentType) { "live" -> "$urlServ/$xtUser/$xtPass/$currentStreamId"; "vod" -> "$urlServ/movie/$xtUser/$xtPass/$currentStreamId.$currentExt"; "series" -> "$urlServ/series/$xtUser/$xtPass/$currentStreamId.$currentExt"; else -> "" }
        if (exoPlayer != null) { exoPlayer?.release(); exoPlayer = null }
        
        trackSelector = DefaultTrackSelector(this)
        exoPlayer = ExoPlayer.Builder(this).setTrackSelector(trackSelector).build()
        
        playerView.player = exoPlayer
        exoPlayer?.setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
        val resumePos = intent.getLongExtra("RESUME_POSITION", 0L); if (resumePos > 0) exoPlayer?.seekTo(resumePos)
        exoPlayer?.prepare(); exoPlayer?.playWhenReady = true

        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) { 
                findViewById<ProgressBar>(R.id.loadingSpinner).visibility = if (playbackState == Player.STATE_BUFFERING) View.VISIBLE else View.GONE 
                if (playbackState == Player.STATE_ENDED && isNextEpPanelShowing) playNextEpisode()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { btnPlayPauseIcon.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play); if (isPlaying) esconderControlesAposDelay() else mostrarControles() }
        })
        atualizarLinhaDoTempoESalvarProgresso()
    }

    private fun atualizarLinhaDoTempoESalvarProgresso() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                exoPlayer?.let {
                    val dur = it.duration; val pos = it.currentPosition
                    if (dur > 0) {
                        findViewById<ProgressBar>(R.id.progressBarTempo).apply { max = 100; progress = ((pos * 100) / dur).toInt() }
                        findViewById<TextView>(R.id.txtTempoCompleto).text = "${formatarTempo(pos)} / ${formatarTempo(dur)}"
                        
                        if (currentType == "series" && episodiosArray != null && currentEpIndex != -1 && currentEpIndex < (episodiosArray!!.size() - 1)) {
                            val timeLeft = dur - pos
                            if (timeLeft in 1..15000) {
                                if (!isNextEpPanelShowing) {
                                    val nextEp = episodiosArray!!.get(currentEpIndex + 1).asJsonObject
                                    val title = if (nextEp.has("title")) nextEp.get("title").asString else "Episódio ${currentEpIndex + 2}"
                                    txtNextEpTitle.text = title
                                    panelNextEpisode.visibility = View.VISIBLE
                                    isNextEpPanelShowing = true
                                    btnAssistirProximo.requestFocus()
                                }
                                val secs = timeLeft / 1000
                                btnAssistirProximo.text = "Assistir Agora (${secs}s)"
                            } else if (timeLeft > 15000 && isNextEpPanelShowing) {
                                panelNextEpisode.visibility = View.GONE
                                isNextEpPanelShowing = false
                            }
                        }

                        if ((currentType == "vod" || currentType == "series") && pos > 5000) {
                            val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
                            val histJson = prefs.getString("iptv_continuar_vod", "[]")
                            val history: MutableList<MutableMap<String, Any>> = try { Gson().fromJson(histJson, object : TypeToken<MutableList<MutableMap<String, Any>>>(){}.type) ?: mutableListOf() } catch (e: Exception) { mutableListOf() }
                            val iterator = history.iterator(); while(iterator.hasNext()) { val item = iterator.next(); val itemId = (item["id"] as? Number)?.toInt() ?: 0; if(itemId == currentStreamId) iterator.remove() }
                            history.add(0, mutableMapOf("id" to currentStreamId, "tipo" to currentType, "tempo" to pos, "duracao" to dur))
                            if(history.size > 20) history.removeAt(history.size - 1)
                            prefs.edit().putString("iptv_continuar_vod", Gson().toJson(history)).apply()
                        }
                    }
                }
                handler.postDelayed(this, 1000)
            }
        }, 1000)
    }

    private fun playNextEpisode() {
        if (episodiosArray == null || currentEpIndex >= (episodiosArray!!.size() - 1)) return
        currentEpIndex++
        val nextEp = episodiosArray!!.get(currentEpIndex).asJsonObject
        currentStreamId = if (nextEp.has("id")) nextEp.get("id").asInt else nextEp.get("stream_id").asInt
        currentExt = if (nextEp.has("container_extension")) nextEp.get("container_extension").asString else "mp4"
        currentTitle = if (nextEp.has("title")) nextEp.get("title").asString else "Episódio ${currentEpIndex + 1}"
        
        findViewById<TextView>(R.id.txtPlayerTitulo).text = currentTitle
        panelNextEpisode.visibility = View.GONE
        isNextEpPanelShowing = false
        intent.putExtra("RESUME_POSITION", 0L)
        iniciarVideoExoPlayer()
    }

    private fun configurarBotoesPremiumSemFundo() {
        val listenerCor = View.OnFocusChangeListener { v, focus -> 
            if(focus) { v.animate().scaleX(1.15f).scaleY(1.15f).start()
                if (v is Button) v.setTextColor(Color.parseColor("#ffcc00")) else if (v is ImageView) v.setColorFilter(Color.parseColor("#ffcc00"))
            } else { v.animate().scaleX(1.0f).scaleY(1.0f).start()
                if (v is Button) { if ((v.id == R.id.btnAjustar && isFit) || (v.id == R.id.btnEsticar && !isFit)) v.setTextColor(Color.parseColor("#ffcc00")) else v.setTextColor(Color.WHITE) } else if (v is ImageView) v.setColorFilter(Color.WHITE)
            }
        }
        listOf(btnAjustar, btnEsticar, btnModalPlaylist, btnModalEpg, btnPlayPauseIcon, btnPrev, btnNext, btnCatPrev, btnCatNext, btnAudio, btnLegenda).forEach { it.setOnFocusChangeListener(listenerCor) }

        btnPlayPauseIcon.setOnClickListener { exoPlayer?.let { it.playWhenReady = !it.playWhenReady } }
        btnPrev.setOnClickListener { exoPlayer?.let { it.seekTo(it.currentPosition - 20000) }; mostrarAvisoTempo("-20s") }
        btnNext.setOnClickListener { exoPlayer?.let { it.seekTo(it.currentPosition + 20000) }; mostrarAvisoTempo("+20s") }
        btnAjustar.setOnClickListener { isFit = true; playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; btnAjustar.setTextColor(Color.parseColor("#ffcc00")); btnEsticar.setTextColor(Color.WHITE) }
        btnEsticar.setOnClickListener { isFit = false; playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL; btnEsticar.setTextColor(Color.parseColor("#ffcc00")); btnAjustar.setTextColor(Color.WHITE) }
        
        btnModalPlaylist.setOnClickListener { controlsOverlay.visibility = View.GONE; panelEpg.visibility = View.GONE; panelPlaylist.visibility = View.VISIBLE; rvPlaylist.requestFocus() }
        btnModalEpg.setOnClickListener { controlsOverlay.visibility = View.GONE; panelPlaylist.visibility = View.GONE; panelEpg.visibility = View.VISIBLE; rvEpg.requestFocus() }

        btnCatPrev.setOnClickListener { if(categoryList.isNotEmpty()){ currentCatIndex--; if(currentCatIndex < 0) currentCatIndex = categoryList.size - 1; atualizarListaCategoria() } }
        btnCatNext.setOnClickListener { if(categoryList.isNotEmpty()){ currentCatIndex++; if(currentCatIndex >= categoryList.size) currentCatIndex = 0; atualizarListaCategoria() } }

        // MÁGICA: Tema de Diálogo de Alerta NATIVO para contraste perfeito
        btnAudio.setOnClickListener { exoPlayer?.let { player -> TrackSelectionDialogBuilder(this@PlayerActivity, "Selecione o Idioma (Áudio)", player, C.TRACK_TYPE_AUDIO).setTheme(android.R.style.Theme_DeviceDefault_Dialog_Alert).build().show() } }
        btnLegenda.setOnClickListener { exoPlayer?.let { player -> TrackSelectionDialogBuilder(this@PlayerActivity, "Selecione a Legenda", player, C.TRACK_TYPE_TEXT).setTheme(android.R.style.Theme_DeviceDefault_Dialog_Alert).build().show() } }
        
        btnAssistirProximo.setOnFocusChangeListener { v, focus -> if(focus) v.animate().scaleX(1.05f).start() else v.animate().scaleX(1.0f).start() }
        btnAssistirProximo.setOnClickListener { playNextEpisode() }
    }

    private fun atualizarListaCategoria() {
        val cat = categoryList[currentCatIndex]
        txtCatAtual.text = cat
        val fatiado = if (cat == "Favoritos") {
            val favs = try { Gson().fromJson<List<String>>(getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE).getString("favoritos_tv", "[]"), object : TypeToken<List<String>>(){}.type) ?: emptyList() } catch (e: Exception) { emptyList() }
            todosCanaisDoServidor.filter { favs.contains(it.stream_id.toString()) }
        } else {
            todosCanaisDoServidor.filter { it.category_name == cat }
        }
        canaisDaPastaAtual = fatiado
        rvPlaylist.adapter = PlaylistAdapter(fatiado)
    }

    private fun mudarCanalLive(step: Int) {
        if (canaisDaPastaAtual.isEmpty()) return
        var idx = canaisDaPastaAtual.indexOfFirst { it.stream_id == currentStreamId }
        if (idx == -1) idx = 0
        idx += step
        if (idx >= canaisDaPastaAtual.size) idx = 0
        if (idx < 0) idx = canaisDaPastaAtual.size - 1

        val proximoCanal = canaisDaPastaAtual[idx]
        currentStreamId = proximoCanal.stream_id
        currentTitle = proximoCanal.name
        findViewById<TextView>(R.id.txtPlayerTitulo).text = currentTitle
        findViewById<TextView>(R.id.txtOsdEpgAtual).text = "Sintonizando..."
        
        iniciarVideoExoPlayer()
        carregarDadosEmPlanoDeFundo()
    }

    private fun carregarDadosEmPlanoDeFundo() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val api = XtreamClient.create(urlServ)
                
                // MÁGICA: Isolando a requisição dos canais em um bloco protegido,
                // para que a Playlist seja preenchida mesmo que o Guia EPG dê erro depois!
                if (todosCanaisDoServidor.isEmpty()) {
                    try {
                        val respCat = api.getLiveCategories(xtUser, xtPass)
                        val respCanais = api.getLiveStreams(xtUser, xtPass)
                        val mapCategorias = mutableMapOf<String, String>()
                        if (respCat.isJsonArray) { Gson().fromJson<List<XtreamCategory>>(respCat, object : TypeToken<List<XtreamCategory>>() {}.type).forEach { mapCategorias[it.category_id] = it.category_name } }
                        if (respCanais.isJsonArray) {
                            val brutos: List<XtreamLive> = Gson().fromJson(respCanais, object : TypeToken<List<XtreamLive>>() {}.type)
                            brutos.forEach { it.category_name = mapCategorias[it.category_id ?: ""] ?: "Outros" }
                            val isParentalOn = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE).getBoolean("parental_control", false)
                            todosCanaisDoServidor = brutos.filter { canal -> 
                                if (!isParentalOn) true else !listOf("adulto", "adult", "18+", "xxx", "porn", "sensual", "hachutv").any { (canal.category_name?.lowercase() ?: "").contains(it) } 
                            }
                        }
                    } catch (e: Exception) { }
                }

                withContext(Dispatchers.Main) {
                    val catUnicas = todosCanaisDoServidor.map { it.category_name ?: "Outros" }.distinct().sorted().toMutableList()
                    catUnicas.add(0, "Favoritos")
                    categoryList = catUnicas
                    currentCatIndex = categoryList.indexOf(categoryContext)
                    if(currentCatIndex == -1) currentCatIndex = 0
                    atualizarListaCategoria()
                }

                try {
                    val respEpg = api.getShortEpg(xtUser, xtPass, id = currentStreamId)
                    var encontrouEpg = false
                    if (respEpg.isJsonObject && respEpg.asJsonObject.has("epg_listings") && !respEpg.asJsonObject.get("epg_listings").isJsonNull && respEpg.asJsonObject.get("epg_listings").isJsonArray) {
                        val listaEpg: List<XtreamEpgListing> = Gson().fromJson(respEpg.asJsonObject.get("epg_listings"), object : TypeToken<List<XtreamEpgListing>>() {}.type)
                        if (listaEpg.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                findViewById<TextView>(R.id.txtOsdEpgAtual).text = decodificarBase64(listaEpg[0].title)
                                rvEpg.adapter = EpgAdapter(listaEpg)
                            }
                            encontrouEpg = true
                        }
                    }

                    if (!encontrouEpg) {
                        val file = java.io.File(filesDir, "epg_offline.json")
                        if (file.exists()) {
                            val epgMap: Map<String, List<XtreamEpgListing>> = Gson().fromJson(file.readText(), object : TypeToken<Map<String, List<XtreamEpgListing>>>(){}.type)
                            val canalAtual = todosCanaisDoServidor.find { it.stream_id == currentStreamId }
                            val epgId = canalAtual?.epg_channel_id ?: ""
                            val pList = epgMap[epgId]
                            if (pList != null && pList.isNotEmpty()) {
                                val agora = System.currentTimeMillis() / 1000
                                val futuros = pList.filter { (it.stop_timestamp?.toLong() ?: 0L) > agora }.sortedBy { it.start_timestamp?.toLong() ?: 0L }.take(10)
                                if (futuros.isNotEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        findViewById<TextView>(R.id.txtOsdEpgAtual).text = decodificarBase64(futuros[0].title)
                                        rvEpg.adapter = EpgAdapter(futuros)
                                    }
                                    encontrouEpg = true
                                }
                            }
                        }
                    }
                    
                    if (!encontrouEpg) {
                        withContext(Dispatchers.Main) {
                            findViewById<TextView>(R.id.txtOsdEpgAtual).text = "Sem Guia (Clique Atualizar Guia no Perfil)"
                            rvEpg.adapter = EpgAdapter(emptyList())
                        }
                    }
                } catch (e: Exception) { }
            } catch (e: Exception) { }
        }
    }

    private fun decodificarBase64(s: String?): String = try { String(Base64.decode(s?.trim() ?: "", Base64.DEFAULT)) } catch (e: Exception) { s ?: "Programa" }
    private fun formatarTempo(ms: Long): String { val tSec = ms / 1000; return String.format("%02d:%02d", tSec / 60, tSec % 60) }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (panelPlaylist.visibility == View.VISIBLE || panelEpg.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) { 
                panelPlaylist.visibility = View.GONE
                panelEpg.visibility = View.GONE
                mostrarControles()
                return true 
            }
            return super.onKeyDown(keyCode, event)
        }

        mostrarControles()
        esconderControlesAposDelay()

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { 
                if (isNextEpPanelShowing && btnAssistirProximo.hasFocus()) {
                    btnAssistirProximo.performClick()
                } else {
                    exoPlayer?.let { it.playWhenReady = !it.playWhenReady }
                }
                return true 
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { if (isOverlayVisible) finish() else mostrarControles(); return true }
        }

        if (currentType == "live") {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> { mudarCanalLive(1); return true }
                KeyEvent.KEYCODE_DPAD_LEFT -> { mudarCanalLive(-1); return true }
                KeyEvent.KEYCODE_DPAD_UP -> { btnModalEpg.performClick(); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { btnModalPlaylist.performClick(); return true }
            }
        } else {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { exoPlayer?.let { it.seekTo(it.currentPosition + 20000) }; mostrarAvisoTempo("+20s"); return true }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { exoPlayer?.let { it.seekTo(it.currentPosition - 20000) }; mostrarAvisoTempo("-20s"); return true }
                KeyEvent.KEYCODE_DPAD_UP -> { btnModalPlaylist.performClick(); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { return true } 
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun mostrarAvisoTempo(texto: String) { val fb = findViewById<TextView>(R.id.txtFeedbackAvanco); fb.text = texto; fb.visibility = View.VISIBLE; handler.postDelayed({ fb.visibility = View.GONE }, 1000) }
    private fun mostrarControles() { controlsOverlay.visibility = View.VISIBLE; isOverlayVisible = true }
    private val hideRunnable = Runnable { if (exoPlayer?.isPlaying == true && panelPlaylist.visibility == View.GONE && panelEpg.visibility == View.GONE && !isNextEpPanelShowing) { controlsOverlay.visibility = View.GONE; isOverlayVisible = false } }
    private fun esconderControlesAposDelay() { handler.removeCallbacks(hideRunnable); handler.postDelayed(hideRunnable, 4000) }
    override fun onDestroy() { super.onDestroy(); handler.removeCallbacksAndMessages(null); exoPlayer?.release(); exoPlayer = null }

    inner class PlaylistAdapter(private val lista: List<XtreamLive>) : RecyclerView.Adapter<PlaylistAdapter.Holder>() {
        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val txtNome: TextView = view.findViewById(R.id.txtNomeCanalLista); val imgLogo: ImageView = view.findViewById(R.id.imgLogoCanalLista)
            init {
                view.isFocusable = true
                view.isFocusableInTouchMode = false
                view.setOnFocusChangeListener { v, focus -> if(focus) { v.setBackgroundColor(Color.parseColor("#33FFFFFF")); v.animate().scaleX(1.03f).start() } else { v.setBackgroundResource(R.drawable.bg_card_premium_normal); v.animate().scaleX(1.0f).start() } }
                view.setOnClickListener { val c = lista[bindingAdapterPosition]; currentStreamId = c.stream_id; currentTitle = c.name; findViewById<TextView>(R.id.txtPlayerTitulo).text = currentTitle; findViewById<TextView>(R.id.txtOsdEpgAtual).text = "Sintonizando..."; panelPlaylist.visibility = View.GONE; mostrarControles(); iniciarVideoExoPlayer(); carregarDadosEmPlanoDeFundo() }
            }
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_player_playlist, parent, false))
        
        override fun onBindViewHolder(holder: Holder, position: Int) { 
            val c = lista[position]; holder.txtNome.text = c.name; 
            if (!c.stream_icon.isNullOrEmpty()) {
                Glide.with(holder.itemView.context).load(c.stream_icon).override(200, 200).diskCacheStrategy(DiskCacheStrategy.ALL).into(holder.imgLogo) 
            } else {
                holder.imgLogo.setImageDrawable(null) 
            }
        }
        
        override fun getItemCount() = lista.size
    }

    inner class EpgAdapter(private val lista: List<XtreamEpgListing>) : RecyclerView.Adapter<EpgAdapter.Holder>() {
        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val txtTitulo: TextView = view.findViewById(R.id.txtEpgTituloLista); val txtHora: TextView = view.findViewById(R.id.txtEpgHorarioLista)
            init { 
                view.isFocusable = true
                view.isFocusableInTouchMode = false
                view.setOnFocusChangeListener { v, focus -> if(focus) v.setBackgroundColor(Color.parseColor("#33FFFFFF")) else v.setBackgroundResource(R.drawable.bg_card_premium_normal) } 
            }
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_player_epg, parent, false))
        override fun onBindViewHolder(holder: Holder, position: Int) { val e = lista[position]; holder.txtTitulo.text = decodificarBase64(e.title); val fI = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()); val fO = SimpleDateFormat("HH:mm", Locale.getDefault()); try { val st = fI.parse(e.start_timestamp ?: ""); val en = fI.parse(e.stop_timestamp ?: ""); if(st != null && en != null) holder.txtHora.text = "${fO.format(st)} - ${fO.format(en)}" } catch(ex: Exception) { try { val dF = java.util.Date((e.start_timestamp?.toLong() ?: 0L) * 1000); val dT = java.util.Date((e.stop_timestamp?.toLong() ?: 0L) * 1000); holder.txtHora.text = "${fO.format(dF)} - ${fO.format(dT)}" } catch(e2: Exception){} } }
        override fun getItemCount() = lista.size
    }
}
