package com.signalplay.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EpgGuideActivity : Activity() {

    private lateinit var recyclerCanaisEpg: RecyclerView
    private lateinit var recyclerProgramacao: RecyclerView
    private lateinit var tvNomeCanalEpg: TextView
    private lateinit var layoutGuia: LinearLayout
    private lateinit var layoutEpgVazio: LinearLayout
    private lateinit var progressLoadingEpg: ProgressBar

    private val activityJob = Job()
    private val activityScope = CoroutineScope(Dispatchers.IO + activityJob)

    private val listaCanaisUnicos = mutableListOf<CanalItem>()
    private val mapaIdParaEpg = mutableMapOf<String, String>()
    private val epgDataMap = mutableMapOf<String, JSONArray>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_epg_guide)

        recyclerCanaisEpg = findViewById(R.id.recyclerCanaisEpg)
        recyclerProgramacao = findViewById(R.id.recyclerProgramacao)
        tvNomeCanalEpg = findViewById(R.id.tvNomeCanalEpg)
        layoutGuia = findViewById(R.id.layoutGuia)
        layoutEpgVazio = findViewById(R.id.layoutEpgVazio)
        progressLoadingEpg = findViewById(R.id.progressLoadingEpg)
        val btnIrParaConfig = findViewById<Button>(R.id.btnIrParaConfig)

        recyclerCanaisEpg.layoutManager = LinearLayoutManager(this)
        recyclerProgramacao.layoutManager = LinearLayoutManager(this)

        btnIrParaConfig.setOnClickListener {
            val intentConfig = Intent(this, SettingsActivity::class.java)
            intentConfig.putExtras(intent)
            startActivity(intentConfig)
            finish()
        }

        carregarGuia()
    }

    // SISTEMA DE DEBUG VISUAL (O app não fecha mais sozinho, ele exibe o erro)
    private fun mostrarErroDebug(e: Exception, local: String) {
        val stackTrace = Log.getStackTraceString(e)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Erro no Debug ($local)")
            .setMessage("Motivo: ${e.message}\n\nRastreio:\n$stackTrace")
            .setPositiveButton("Fechar Tela") { _, _ -> finish() }
            .setCancelable(false)
            .create()
        dialog.show()
    }

    private fun decodificarTexto(raw: String): String {
        if (raw.isEmpty()) return ""
        try {
            val decodedBytes = Base64.decode(raw, Base64.DEFAULT)
            val txt = String(decodedBytes, Charsets.UTF_8)
            if (txt.isNotBlank() && !txt.contains("")) {
                return txt
            }
        } catch (e: Exception) {}
        return raw
    }

    private fun carregarGuia() {
        activityScope.launch {
            try {
                val file = File(filesDir, "epg_data.json")
                if (!file.exists() || file.length() < 10) {
                    withContext(Dispatchers.Main) {
                        progressLoadingEpg.visibility = View.GONE
                        layoutEpgVazio.visibility = View.VISIBLE
                    }
                    return@launch
                }

                val fileContent = file.readText()
                val conteudoLimpo = fileContent.trim()

                try {
                    if (conteudoLimpo.startsWith("[")) {
                        val jsonArr = JSONArray(conteudoLimpo)
                        for (i in 0 until jsonArr.length()) {
                            val prog = jsonArr.optJSONObject(i) ?: continue
                            val eId = prog.optString("epg_id")
                            if (eId.isNotEmpty()) {
                                val list = epgDataMap.getOrPut(eId) { JSONArray() }
                                list.put(prog)
                            }
                        }
                    } else {
                        val jsonObj = JSONObject(conteudoLimpo)
                        if (jsonObj.has("epg_listings")) {
                            val arr = jsonObj.optJSONArray("epg_listings")
                            if (arr != null) {
                                for (i in 0 until arr.length()) {
                                    val prog = arr.optJSONObject(i) ?: continue
                                    val eId = prog.optString("epg_id")
                                    if (eId.isNotEmpty()) {
                                        val list = epgDataMap.getOrPut(eId) { JSONArray() }
                                        list.put(prog)
                                    }
                                }
                            }
                        } else {
                            val keys = jsonObj.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                val arr = jsonObj.optJSONArray(key)
                                if (arr != null) epgDataMap[key] = arr
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        progressLoadingEpg.visibility = View.GONE
                        mostrarErroDebug(e, "Parser do JSON")
                    }
                    return@launch
                }

                val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
                val isParentalActive = prefs.getBoolean("PARENTAL_CONTROL", false)
                val filterSD = prefs.getBoolean("FILTER_SD", false)
                val filterHD = prefs.getBoolean("FILTER_HD", false)
                val filterFHD = prefs.getBoolean("FILTER_FHD", false)
                val filterH265 = prefs.getBoolean("FILTER_H265", false)
                val filter4K = prefs.getBoolean("FILTER_4K", false)

                val dao = AppDatabase.getDatabase(this@EpgGuideActivity).catalogoDao()
                val categoriasEntity = dao.getCategoriasPorTipo("live")
                val catMap = categoriasEntity.associate { it.id to it.nome }
                
                val todosCanais = dao.getTodosCanais()
                val mapEpgToCanal = mutableMapOf<String, CanalEntity>()

                for (canal in todosCanais) {
                    val epgId = canal.epgChannelId
                    if (epgId.isEmpty()) continue

                    val nomeCategoria = catMap[canal.categoryId] ?: ""
                    val isBlocked = ContentFilterUtils.isContentBlocked(
                        canal.nome, nomeCategoria, isParentalActive, filterSD, filterHD, filterFHD, filterH265, filter4K
                    )
                    if (isBlocked) continue

                    if (mapEpgToCanal.containsKey(epgId)) {
                        val canalSalvo = mapEpgToCanal[epgId]!!
                        val nomeSalvo = canalSalvo.nome.uppercase()
                        val nomeNovo = canal.nome.uppercase()

                        val novoEhHD = nomeNovo.contains(" HD") || nomeNovo.contains("- HD") || nomeNovo.endsWith("HD")
                        val salvoEhHD = nomeSalvo.contains(" HD") || nomeSalvo.contains("- HD") || nomeSalvo.endsWith("HD")

                        if (novoEhHD && !salvoEhHD) mapEpgToCanal[epgId] = canal
                    } else {
                        mapEpgToCanal[epgId] = canal
                    }
                }

                val agoraTs = System.currentTimeMillis() / 1000
                for ((epgId, canalEntity) in mapEpgToCanal) {
                    val localList = epgDataMap[epgId]
                    if (localList != null && localList.length() > 0) {
                        var temProgramacaoFutura = false
                        for (i in 0 until localList.length()) {
                            val prog = localList.getJSONObject(i)
                            var stopTs = prog.optLong("stop_timestamp", 0)
                            
                            if (stopTs == 0L) {
                                val endStr = prog.optString("end", "")
                                if (endStr.isNotEmpty()) {
                                    try {
                                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                        stopTs = sdf.parse(endStr)?.time?.div(1000) ?: 0L
                                    } catch (e: Exception) {}
                                }
                            }

                            if (stopTs > agoraTs) {
                                temProgramacaoFutura = true
                                break
                            }
                        }

                        if (temProgramacaoFutura) {
                            val canalItem = CanalItem(canalEntity.id, canalEntity.nome, canalEntity.urlImagem, canalEntity.categoryId, canalEntity.streamUrl)
                            listaCanaisUnicos.add(canalItem)
                            mapaIdParaEpg[canalEntity.id] = epgId
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    progressLoadingEpg.visibility = View.GONE
                    if (listaCanaisUnicos.isEmpty()) {
                        layoutEpgVazio.visibility = View.VISIBLE
                    } else {
                        layoutGuia.visibility = View.VISIBLE
                        
                        // Chamando o Adapter original perfeitamente
                        recyclerCanaisEpg.adapter = CanalLinhaAdapter(listaCanaisUnicos) { canalClicado ->
                            abrirCanalNoPlayer(canalClicado)
                        }

                        recyclerCanaisEpg.viewTreeObserver.addOnGlobalFocusChangeListener { oldFocus, newFocus ->
                            if (newFocus != null) {
                                val pos = recyclerCanaisEpg.getChildAdapterPosition(newFocus)
                                if (pos in 0 until listaCanaisUnicos.size) {
                                    val canalFocado = listaCanaisUnicos[pos]
                                    carregarProgramacaoDoCanal(canalFocado)
                                }
                            }
                        }

                        carregarProgramacaoDoCanal(listaCanaisUnicos[0])
                        recyclerCanaisEpg.requestFocus()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressLoadingEpg.visibility = View.GONE
                    mostrarErroDebug(e, "Bloco Principal (carregarGuia)")
                }
            }
        }
    }

    private fun carregarProgramacaoDoCanal(canal: CanalItem) {
        tvNomeCanalEpg.text = canal.nome
        activityScope.launch {
            try {
                val listaEpgExibir = mutableListOf<EpgItem>()
                val agoraTs = System.currentTimeMillis() / 1000
                val epgId = mapaIdParaEpg[canal.id] ?: ""

                if (epgId.isNotEmpty()) {
                    val progList = epgDataMap[epgId]
                    if (progList != null) {
                        val futuros = mutableListOf<JSONObject>()
                        for (i in 0 until progList.length()) {
                            val prog = progList.getJSONObject(i)
                            var stopTs = prog.optLong("stop_timestamp", 0)
                            if (stopTs == 0L) {
                                val endStr = prog.optString("end", "")
                                if (endStr.isNotEmpty()) {
                                    try {
                                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                        stopTs = sdf.parse(endStr)?.time?.div(1000) ?: 0L
                                    } catch (e: Exception) {}
                                }
                            }

                            if (stopTs > agoraTs) futuros.add(prog)
                        }
                        
                        futuros.sortBy { 
                            var sTs = it.optLong("start_timestamp", 0)
                            if (sTs == 0L) {
                                val stStr = it.optString("start", "")
                                if (stStr.isNotEmpty()) {
                                    try {
                                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                        sTs = sdf.parse(stStr)?.time?.div(1000) ?: 0L
                                    } catch (e: Exception) {}
                                }
                            }
                            sTs 
                        }

                        for (i in 0 until Math.min(15, futuros.size)) {
                            val prog = futuros[i]
                            val titleDecoded = decodificarTexto(prog.optString("title", "Programa"))
                            
                            var startTs = prog.optLong("start_timestamp", 0)
                            var stopTs = prog.optLong("stop_timestamp", 0)

                            if (startTs == 0L) {
                                val startStr = prog.optString("start", "")
                                if (startStr.isNotEmpty()) {
                                    try {
                                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                        startTs = sdf.parse(startStr)?.time?.div(1000) ?: 0L
                                    } catch (e: Exception) {}
                                }
                            }
                            if (stopTs == 0L) {
                                val endStr = prog.optString("end", "")
                                if (endStr.isNotEmpty()) {
                                    try {
                                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                        stopTs = sdf.parse(endStr)?.time?.div(1000) ?: 0L
                                    } catch (e: Exception) {}
                                }
                            }

                            val sdfHora = SimpleDateFormat("HH:mm", Locale.getDefault())
                            val hInicio = sdfHora.format(Date(startTs * 1000))
                            val hFim = sdfHora.format(Date(stopTs * 1000))
                            
                            val duracaoMinutos = (stopTs - startTs) / 60
                            val h = duracaoMinutos / 60
                            val m = duracaoMinutos % 60
                            val duracaoLista = if (h > 0 && m > 0) "$h hora e $m min" else if (h > 0) "$h horas" else "$m min"

                            var isLive = false
                            var corTexto = "#888888"
                            var horarioStr = "Hoje / $hInicio - $hFim"

                            if (agoraTs in startTs until stopTs) {
                                isLive = true
                                corTexto = "#2ED573"
                                horarioStr = "Ao Vivo / $hInicio - $hFim"
                            }

                            // Construindo usando as variáveis do SEU EpgAdapter
                            listaEpgExibir.add(EpgItem(
                                titulo = titleDecoded, 
                                horario = horarioStr, 
                                duracao = duracaoLista, 
                                isAgora = isLive, 
                                textColor = corTexto
                            ))
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    recyclerProgramacao.adapter = EpgAdapter(listaEpgExibir)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    mostrarErroDebug(e, "Carregar Programação Direita")
                }
            }
        }
    }

    private fun abrirCanalNoPlayer(canalClicado: CanalItem) {
        DataHolder.todasCategorias = listOf(CategoriaItem("EPG_GUIDE", "Guia de Programação"))
        DataHolder.todosCanais = listaCanaisUnicos
        DataHolder.categoriaAtualId = "EPG_GUIDE"
        DataHolder.canaisFiltrados = listaCanaisUnicos
        
        val indice = listaCanaisUnicos.indexOf(canalClicado)
        
        val intentPlayer = Intent(this, PlayerTvActivity::class.java)
        intentPlayer.putExtras(intent)
        intentPlayer.putExtra("INDICE_CANAL", if (indice != -1) indice else 0)
        startActivity(intentPlayer)
    }

    override fun onDestroy() {
        super.onDestroy()
        activityJob.cancel()
    }
}
