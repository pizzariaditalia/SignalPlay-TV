package com.signalplay.tv

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class DetailsActivity : Activity() {

    private val episodesMap = mutableMapOf<String, List<EpisodeItem>>()
    private val seasonsList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)

        val detBackdrop = findViewById<ImageView>(R.id.detBackdrop)
        val detTitle = findViewById<TextView>(R.id.detTitle)
        val detYear = findViewById<TextView>(R.id.detYear)
        val detRating = findViewById<TextView>(R.id.detRating)
        val detPlot = findViewById<TextView>(R.id.detPlot)
        val btnPlay = findViewById<Button>(R.id.btnPlay)
        
        val areaSeries = findViewById<LinearLayout>(R.id.areaSeries)
        val spinnerSeasons = findViewById<Spinner>(R.id.spinnerSeasons)
        val recyclerEpisodes = findViewById<RecyclerView>(R.id.recyclerEpisodes)
        
        recyclerEpisodes.layoutManager = LinearLayoutManager(this)

        val url = intent.getStringExtra("URL") ?: ""
        val user = intent.getStringExtra("USER") ?: ""
        val pass = intent.getStringExtra("PASS") ?: ""
        val idMedia = intent.getStringExtra("MEDIA_ID") ?: ""
        val tipoMedia = intent.getStringExtra("MEDIA_TIPO") ?: "" // "filme" ou "serie"
        val nomeMedia = intent.getStringExtra("MEDIA_NOME") ?: ""
        val capaMedia = intent.getStringExtra("MEDIA_CAPA") ?: ""

        detTitle.text = nomeMedia
        // Coloca a capinha como fundo provisório até baixar o backdrop gigante
        Glide.with(this).load(capaMedia).into(detBackdrop)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                
                if (tipoMedia == "filme") {
                    val req = Request.Builder().url("$url/player_api.php?username=$user&password=$pass&action=get_vod_info&vod_id=$idMedia").build()
                    val res = client.newCall(req).execute()
                    val jsonStr = res.body?.string() ?: "{}"
                    
                    if (jsonStr.startsWith("{")) {
                        val json = JSONObject(jsonStr)
                        val info = json.optJSONObject("info")
                        if (info != null) {
                            val plot = info.optString("plot", "Sem sinopse disponível.")
                            val year = info.optString("releasedate", "")
                            val rating = info.optString("rating", "N/A")
                            val backdrop = info.optString("movie_image", capaMedia)

                            withContext(Dispatchers.Main) {
                                detPlot.text = plot
                                detYear.text = year
                                detRating.text = "⭐ $rating"
                                Glide.with(this@DetailsActivity).load(backdrop).into(detBackdrop)
                                btnPlay.visibility = View.VISIBLE
                                areaSeries.visibility = View.GONE
                            }
                        }
                    }

                } else if (tipoMedia == "serie") {
                    val req = Request.Builder().url("$url/player_api.php?username=$user&password=$pass&action=get_series_info&series_id=$idMedia").build()
                    val res = client.newCall(req).execute()
                    val jsonStr = res.body?.string() ?: "{}"
                    
                    if (jsonStr.startsWith("{")) {
                        val json = JSONObject(jsonStr)
                        val info = json.optJSONObject("info")
                        val epsObj = json.optJSONObject("episodes")

                        var plot = "Sem sinopse disponível."
                        var year = ""
                        var rating = "N/A"
                        var backdrop = capaMedia

                        if (info != null) {
                            plot = info.optString("plot", plot)
                            year = info.optString("releaseDate", year)
                            rating = info.optString("rating", rating)
                            val backdropsArr = info.optJSONArray("backdrop_path")
                            if (backdropsArr != null && backdropsArr.length() > 0) {
                                backdrop = backdropsArr.getString(0)
                            }
                        }

                        // Lógica complexa para varrer as Temporadas e Episódios
                        if (epsObj != null) {
                            val keys = epsObj.keys()
                            while (keys.hasNext()) {
                                val seasonNum = keys.next()
                                seasonsList.add(seasonNum)
                                
                                val epArray = epsObj.optJSONArray(seasonNum)
                                val listEps = mutableListOf<EpisodeItem>()
                                
                                if (epArray != null) {
                                    for (i in 0 until epArray.length()) {
                                        val epData = epArray.getJSONObject(i)
                                        val epId = epData.optString("id")
                                        val epTitle = epData.optString("title", "Episódio ${i+1}")
                                        val epNum = epData.optString("episode_num", "${i+1}")
                                        val ext = epData.optString("container_extension", "mp4")
                                        val streamUrl = "$url/series/$user/$pass/$epId.$ext"
                                        
                                        var epImage = ""
                                        val epInfo = epData.optJSONObject("info")
                                        if (epInfo != null) epImage = epInfo.optString("movie_image", "")
                                        
                                        listEps.add(EpisodeItem(epId, epTitle, epNum, epImage, streamUrl))
                                    }
                                }
                                episodesMap[seasonNum] = listEps
                            }
                        }

                        withContext(Dispatchers.Main) {
                            detPlot.text = plot
                            detYear.text = year
                            detRating.text = "⭐ $rating"
                            Glide.with(this@DetailsActivity).load(backdrop).into(detBackdrop)
                            
                            btnPlay.visibility = View.GONE
                            areaSeries.visibility = View.VISIBLE

                            if (seasonsList.isNotEmpty()) {
                                // Organiza os números das temporadas (1, 2, 3...)
                                seasonsList.sortBy { it.toIntOrNull() ?: 0 }
                                
                                val adapter = ArrayAdapter(this@DetailsActivity, android.R.layout.simple_spinner_item, seasonsList.map { "Temporada $it" })
                                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                                spinnerSeasons.adapter = adapter

                                spinnerSeasons.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                                        val selectedSeason = seasonsList[position]
                                        val epsToDisplay = episodesMap[selectedSeason] ?: emptyList()
                                        
                                        recyclerEpisodes.adapter = EpisodeAdapter(epsToDisplay) { epClicado ->
                                            Toast.makeText(this@DetailsActivity, "Iniciando: ${epClicado.title}", Toast.LENGTH_SHORT).show()
                                            // (Futuro: Abrir o Player de Vídeo)
                                        }
                                    }
                                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DetailsActivity, "Erro ao carregar detalhes.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        btnPlay.setOnClickListener {
            Toast.makeText(this, "Iniciando Filme!", Toast.LENGTH_SHORT).show()
            // (Futuro: Abrir o Player de Vídeo)
        }
    }
}
