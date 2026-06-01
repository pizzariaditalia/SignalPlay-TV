package com.signalplay.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class SettingsActivity : Activity() {

    private lateinit var db: FirebaseFirestore
    private var isParentalActive = false
    private var filterSD = false
    private var filterH265 = false
    private var filter4K = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        db = FirebaseFirestore.getInstance()

        val url = intent.getStringExtra("URL") ?: ""
        val user = intent.getStringExtra("USER") ?: ""
        val pass = intent.getStringExtra("PASS") ?: ""
        val username = intent.getStringExtra("USERNAME") ?: ""

        val tvNomeUsuario = findViewById<TextView>(R.id.tvNomeUsuario)
        val tvVencimento = findViewById<TextView>(R.id.tvVencimento)
        
        val btnParental = findViewById<LinearLayout>(R.id.btnParental)
        val tvStatusParental = findViewById<TextView>(R.id.tvStatusParental)
        
        val btnFilterSD = findViewById<LinearLayout>(R.id.btnFilterSD)
        val tvStatusSD = findViewById<TextView>(R.id.tvStatusSD)
        
        val btnFilterH265 = findViewById<LinearLayout>(R.id.btnFilterH265)
        val tvStatusH265 = findViewById<TextView>(R.id.tvStatusH265)
        
        val btnFilter4K = findViewById<LinearLayout>(R.id.btnFilter4K)
        val tvStatus4K = findViewById<TextView>(R.id.tvStatus4K)

        val btnLimparFavs = findViewById<LinearLayout>(R.id.btnLimparFavs)
        val btnLimparHist = findViewById<LinearLayout>(R.id.btnLimparHist)
        val btnAtualizarEPG = findViewById<LinearLayout>(R.id.btnAtualizarEPG)
        val btnSairConta = findViewById<Button>(R.id.btnSairConta)

        tvNomeUsuario.text = "Olá, $username!"

        val focusListener = View.OnFocusChangeListener { v, hasFocus ->
            if (hasFocus) v.animate().scaleX(1.05f).scaleY(1.05f).translationZ(10f).setDuration(150).start()
            else v.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(150).start()
        }
        btnParental.onFocusChangeListener = focusListener
        btnFilterSD.onFocusChangeListener = focusListener
        btnFilterH265.onFocusChangeListener = focusListener
        btnFilter4K.onFocusChangeListener = focusListener
        btnLimparFavs.onFocusChangeListener = focusListener
        btnLimparHist.onFocusChangeListener = focusListener
        btnAtualizarEPG.onFocusChangeListener = focusListener
        btnSairConta.onFocusChangeListener = focusListener

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val req = Request.Builder().url("$url/player_api.php?username=$user&password=$pass").build()
                val res = client.newCall(req).execute()
                val jsonStr = res.body?.string() ?: "{}"
                if (jsonStr.startsWith("{")) {
                    val json = JSONObject(jsonStr)
                    val userInfo = json.optJSONObject("user_info")
                    if (userInfo != null) {
                        val expStr = userInfo.optString("exp_date", "")
                        withContext(Dispatchers.Main) {
                            if (expStr.isNotEmpty() && expStr != "null") {
                                val expLong = expStr.toLongOrNull()
                                if (expLong != null) {
                                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                                    val date = Date(expLong * 1000)
                                    tvVencimento.text = "Vencimento: ${sdf.format(date)}"
                                } else tvVencimento.text = "Vencimento: Ilimitado"
                            } else tvVencimento.text = "Vencimento: Ilimitado"
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { tvVencimento.text = "Vencimento: Não detectado" }
            }
        }

        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
        isParentalActive = prefs.getBoolean("PARENTAL_CONTROL", false)
        filterSD = prefs.getBoolean("FILTER_SD", false)
        filterH265 = prefs.getBoolean("FILTER_H265", false)
        filter4K = prefs.getBoolean("FILTER_4K", false)

        atualizarStatus(tvStatusParental, isParentalActive)
        atualizarStatus(tvStatusSD, filterSD)
        atualizarStatus(tvStatusH265, filterH265)
        atualizarStatus(tvStatus4K, filter4K)

        btnParental.setOnClickListener {
            isParentalActive = !isParentalActive
            prefs.edit().putBoolean("PARENTAL_CONTROL", isParentalActive).apply()
            atualizarStatus(tvStatusParental, isParentalActive)
        }
        btnFilterSD.setOnClickListener {
            filterSD = !filterSD
            prefs.edit().putBoolean("FILTER_SD", filterSD).apply()
            atualizarStatus(tvStatusSD, filterSD)
        }
        btnFilterH265.setOnClickListener {
            filterH265 = !filterH265
            prefs.edit().putBoolean("FILTER_H265", filterH265).apply()
            atualizarStatus(tvStatusH265, filterH265)
        }
        btnFilter4K.setOnClickListener {
            filter4K = !filter4K
            prefs.edit().putBoolean("FILTER_4K", filter4K).apply()
            atualizarStatus(tvStatus4K, filter4K)
        }

        btnLimparFavs.setOnClickListener {
            if (username.isNotEmpty()) {
                db.collection("usuarios").whereEqualTo("usuario", username).get().addOnSuccessListener { snapshot ->
                    if (!snapshot.isEmpty) {
                        val docId = snapshot.documents[0].id
                        db.collection("usuarios").document(docId).update("favoritos", emptyList<String>())
                        Toast.makeText(this, "Canais Favoritos removidos!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnLimparHist.setOnClickListener {
            if (username.isNotEmpty()) {
                db.collection("usuarios").whereEqualTo("usuario", username).get().addOnSuccessListener { snapshot ->
                    if (!snapshot.isEmpty) {
                        val docId = snapshot.documents[0].id
                        db.collection("usuarios").document(docId).update("historico_vod", emptyMap<String, Any>())
                        Toast.makeText(this, "Histórico limpo com sucesso!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // ====================================================================
        // O MOTOR DE DOWNLOAD XMLTV (PLANO B DE EPG PARA A TV)
        // ====================================================================
        btnAtualizarEPG.setOnClickListener {
            Toast.makeText(this, "Baixando EPG... Isso pode demorar até 1 minuto.", Toast.LENGTH_LONG).show()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val client = OkHttpClient.Builder().connectTimeout(120, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()
                    val req = Request.Builder().url("$url/xmltv.php?username=$user&password=$pass").build()
                    val res = client.newCall(req).execute()
                    val inputStream = res.body?.byteStream()

                    if (inputStream != null) {
                        val reader = BufferedReader(InputStreamReader(inputStream))
                        val epgDb = mutableMapOf<String, MutableList<JSONObject>>()
                        
                        var line: String?
                        var currentChannel = ""
                        var currentStart = 0L
                        var currentStop = 0L
                        var currentTitle = ""

                        fun parseXmltvTime(str: String): Long {
                            if (str.length < 14) return 0L
                            try {
                                val y = str.substring(0, 4)
                                val m = str.substring(4, 6)
                                val d = str.substring(6, 8)
                                val h = str.substring(8, 10)
                                val min = str.substring(10, 12)
                                val s = str.substring(12, 14)
                                val tz = if (str.length >= 20) str.substring(15, 20) else "+0000"
                                val dateStr = "$y-$m-${d}T$h:$min:$s$tz"
                                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault())
                                val date = sdf.parse(dateStr)
                                return (date?.time ?: 0L) / 1000L
                            } catch (e: Exception) { return 0L }
                        }

                        // Leitura blindada linha por linha para não dar Crash de Memória na TV Box
                        while (reader.readLine().also { line = it } != null) {
                            val l = line!!
                            if (l.contains("<programme")) {
                                val chStart = l.indexOf("channel=\"")
                                if (chStart != -1) {
                                    val chEnd = l.indexOf("\"", chStart + 9)
                                    currentChannel = l.substring(chStart + 9, chEnd)
                                }
                                val stStart = l.indexOf("start=\"")
                                if (stStart != -1) {
                                    val stEnd = l.indexOf("\"", stStart + 7)
                                    currentStart = parseXmltvTime(l.substring(stStart + 7, stEnd))
                                }
                                val spStart = l.indexOf("stop=\"")
                                if (spStart != -1) {
                                    val spEnd = l.indexOf("\"", spStart + 6)
                                    currentStop = parseXmltvTime(l.substring(spStart + 6, spEnd))
                                }
                                currentTitle = "Programa"
                            } else if (l.contains("<title")) {
                                val tStart = l.indexOf(">")
                                val tEnd = l.indexOf("</title>")
                                if (tStart != -1 && tEnd != -1 && tEnd > tStart) {
                                    currentTitle = l.substring(tStart + 1, tEnd).replace("<![CDATA[", "").replace("]]>", "")
                                }
                            } else if (l.contains("</programme>")) {
                                if (currentChannel.isNotEmpty() && currentStart > 0L) {
                                    if (!epgDb.containsKey(currentChannel)) epgDb[currentChannel] = mutableListOf()
                                    val obj = JSONObject()
                                    obj.put("title", currentTitle)
                                    obj.put("start_timestamp", currentStart)
                                    obj.put("stop_timestamp", currentStop)
                                    epgDb[currentChannel]?.add(obj)
                                }
                            }
                        }

                        val finalJson = JSONObject()
                        for ((k, v) in epgDb) {
                            val arr = JSONArray()
                            for (item in v) arr.put(item)
                            finalJson.put(k, arr)
                        }

                        val file = File(filesDir, "epg_data.json")
                        file.writeText(finalJson.toString())

                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@SettingsActivity, "EPG Sincronizado com Sucesso!", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SettingsActivity, "Erro de Conexão ao Sincronizar EPG.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnSairConta.setOnClickListener {
            prefs.edit().clear().apply()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun atualizarStatus(tv: TextView, isActive: Boolean) {
        if (isActive) {
            tv.text = "ATIVADO"
            tv.setTextColor(Color.parseColor("#2ED573"))
        } else {
            tv.text = "DESATIVADO"
            tv.setTextColor(Color.parseColor("#FF4757"))
        }
    }
}
