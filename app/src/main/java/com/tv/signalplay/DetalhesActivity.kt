package com.tv.signalplay

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetalhesActivity : FragmentActivity() {

    private var videoExt = "mp4"
    private var videoTitulo = ""
    private var positionToResume = 0L // Variável Mágica!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalhes)

        val xtUser = intent.getStringExtra("XTREAM_USER") ?: ""
        val xtPass = intent.getStringExtra("XTREAM_PASS") ?: ""
        val urlServ = intent.getStringExtra("URL") ?: ""
        val mediaId = intent.getIntExtra("MEDIA_ID", 0)
        val isSeries = intent.getBooleanExtra("IS_SERIES", false)

        val btnVoltar = findViewById<Button>(R.id.btnVoltarDetalhes)
        val btnAssistir = findViewById<Button>(R.id.btnAssistirDetalhes)

        // Verifica se tem progresso gravado!
        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
        val histJson = prefs.getString("iptv_continuar_vod", "[]")
        val history: List<Map<String, Any>> = Gson().fromJson(histJson, object : TypeToken<List<Map<String, Any>>>(){}.type) ?: emptyList()
        val historicoSalvo = history.find { (it["id"] as? Number)?.toInt() == mediaId }
        
        if (historicoSalvo != null) {
            positionToResume = (historicoSalvo["tempo"] as? Number)?.toLong() ?: 0L
            if (positionToResume > 5000L) btnAssistir.text = "▶ CONTINUAR ASSISTINDO"
        }

        listOf(btnAssistir, btnVoltar).forEach { btn ->
            btn.setOnFocusChangeListener { v, focus -> if (focus) v.animate().scaleX(1.05f).start() else v.animate().scaleX(1.0f).start() }
        }

        btnVoltar.setOnClickListener { finish() }
        btnAssistir.setOnClickListener {
            val intentPlayer = Intent(this, PlayerActivity::class.java)
            intentPlayer.putExtra("URL", urlServ); intentPlayer.putExtra("XTREAM_USER", xtUser); intentPlayer.putExtra("XTREAM_PASS", xtPass)
            intentPlayer.putExtra("STREAM_ID", mediaId); intentPlayer.putExtra("TYPE", if (isSeries) "series" else "vod"); intentPlayer.putExtra("TITLE", videoTitulo); intentPlayer.putExtra("EXTENSION", videoExt)
            intentPlayer.putExtra("RESUME_POSITION", positionToResume) // Envia o tempo para o reprodutor!
            startActivity(intentPlayer)
        }

        if (mediaId != 0 && urlServ.isNotEmpty()) carregarSinopse(urlServ, xtUser, xtPass, mediaId, isSeries)
    }

    private fun carregarSinopse(url: String, user: String, pass: String, id: Int, isSeries: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val api = XtreamClient.create(url)
                val response = if (isSeries) api.getSeriesInfo(user, pass, id = id) else api.getVodInfo(user, pass, id = id)
                withContext(Dispatchers.Main) {
                    if (response.isJsonObject) {
                        val obj = response.asJsonObject
                        if (obj.has("info")) {
                            val info = obj.getAsJsonObject("info")
                            videoTitulo = info.get("name")?.asString ?: "Desconhecido"
                            val sinopse = info.get("plot")?.asString ?: "Nenhuma sinopse disponível."
                            val nota = info.get("rating")?.asString ?: "0.0"
                            val ano = info.get("releasedate")?.asString ?: info.get("year")?.asString ?: ""
                            val capaGigante = info.get("movie_image")?.asString ?: info.get("cover_big")?.asString ?: ""
                            val capaFundo = info.get("backdrop_path")?.asJsonArray?.firstOrNull()?.asString ?: capaGigante
                            if (!isSeries && obj.has("movie_data")) videoExt = obj.getAsJsonObject("movie_data").get("container_extension")?.asString ?: "mp4"

                            findViewById<TextView>(R.id.txtTituloDetalhes).text = videoTitulo
                            findViewById<TextView>(R.id.txtSinopseDetalhes).text = sinopse
                            findViewById<TextView>(R.id.txtMetaDetalhes).text = "📅 $ano   ⭐ $nota   ${if(isSeries) "SÉRIE" else "FILME"}"
                            if (capaGigante.isNotEmpty()) Glide.with(this@DetalhesActivity).load(capaGigante).into(findViewById<ImageView>(R.id.imgPosterDetalhes))
                            if (capaFundo.isNotEmpty()) Glide.with(this@DetalhesActivity).load(capaFundo).into(findViewById<ImageView>(R.id.imgFundoDetalhes))
                        }
                    }
                }
            } catch (e: Exception) { }
        }
    }
}
