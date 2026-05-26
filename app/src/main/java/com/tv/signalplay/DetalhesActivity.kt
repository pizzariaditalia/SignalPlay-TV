package com.tv.signalplay

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetalhesActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalhes)

        val xtreamUser = intent.getStringExtra("XTREAM_USER") ?: ""
        val xtreamPass = intent.getStringExtra("XTREAM_PASS") ?: ""
        val urlDoServidor = intent.getStringExtra("URL") ?: ""
        val mediaId = intent.getIntExtra("MEDIA_ID", 0)
        val isSeries = intent.getBooleanExtra("IS_SERIES", false)

        val btnVoltar = findViewById<Button>(R.id.btnVoltarDetalhes)
        val btnAssistir = findViewById<Button>(R.id.btnAssistirDetalhes)

        // Efeito de Foco nos botões
        listOf(btnAssistir, btnVoltar).forEach { btn ->
            btn.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start()
                else v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
            }
        }

        btnVoltar.setOnClickListener { finish() }
        btnAssistir.setOnClickListener { Toast.makeText(this, "Abrindo Player...", Toast.LENGTH_SHORT).show() }

        if (mediaId != 0 && urlDoServidor.isNotEmpty()) {
            carregarSinopse(urlDoServidor, xtreamUser, xtreamPass, mediaId, isSeries)
        }
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
                            
                            val titulo = info.get("name")?.asString ?: "Desconhecido"
                            val sinopse = info.get("plot")?.asString ?: "Nenhuma sinopse disponível."
                            val nota = info.get("rating")?.asString ?: "0.0"
                            val ano = info.get("releasedate")?.asString ?: info.get("year")?.asString ?: ""
                            val capa = info.get("cover_big")?.asString ?: info.get("movie_image")?.asString ?: ""

                            findViewById<TextView>(R.id.txtTituloDetalhes).text = titulo
                            findViewById<TextView>(R.id.txtSinopseDetalhes).text = sinopse
                            findViewById<TextView>(R.id.txtMetaDetalhes).text = "⭐ $nota   📅 $ano"

                            if (capa.isNotEmpty()) {
                                Glide.with(this@DetalhesActivity).load(capa).into(findViewById<ImageView>(R.id.imgFundoDetalhes))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(this@DetalhesActivity, "Erro ao carregar detalhes.", Toast.LENGTH_SHORT).show() }
            }
        }
    }
}
