package com.signalplay.tv

import android.app.Activity
import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
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
    private val interpolator = OvershootInterpolator(1.2f)
    private var username = ""
    private var url = ""
    private var user = ""
    private var pass = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        db = FirebaseFirestore.getInstance()

        url = intent.getStringExtra("URL") ?: ""
        user = intent.getStringExtra("USER") ?: ""
        pass = intent.getStringExtra("PASS") ?: ""
        username = intent.getStringExtra("USERNAME") ?: ""

        val tvNomeUsuario = findViewById<TextView>(R.id.tvNomeUsuario)
        val tvStatusPlano = findViewById<TextView>(R.id.tvStatusPlano)
        val tvVencimento = findViewById<TextView>(R.id.tvVencimento)
        
        val btnAtualizarEPG = findViewById<LinearLayout>(R.id.btnAtualizarEPG)
        val tvStatusEpg = findViewById<TextView>(R.id.tvStatusEpg)

        val btnShowApps = findViewById<LinearLayout>(R.id.btnShowApps)
        val switchApps = findViewById<Switch>(R.id.switchApps)

        val btnParental = findViewById<LinearLayout>(R.id.btnParental)
        val switchParental = findViewById<Switch>(R.id.switchParental)
        
        val btnFilterSD = findViewById<LinearLayout>(R.id.btnFilterSD)
        val switchSD = findViewById<Switch>(R.id.switchSD)
        val btnFilterHD = findViewById<LinearLayout>(R.id.btnFilterHD)
        val switchHD = findViewById<Switch>(R.id.switchHD)
        val btnFilterFHD = findViewById<LinearLayout>(R.id.btnFilterFHD)
        val switchFHD = findViewById<Switch>(R.id.switchFHD)
        val btnFilterH265 = findViewById<LinearLayout>(R.id.btnFilterH265)
        val switchH265 = findViewById<Switch>(R.id.switchH265)
        val btnFilter4K = findViewById<LinearLayout>(R.id.btnFilter4K)
        val switch4K = findViewById<Switch>(R.id.switch4K)

        val btnLimparFavs = findViewById<LinearLayout>(R.id.btnLimparFavs)
        val btnLimparHist = findViewById<LinearLayout>(R.id.btnLimparHist)
        
        val btnModoLauncher = findViewById<LinearLayout>(R.id.btnModoLauncher)
        val switchLauncher = findViewById<Switch>(R.id.switchLauncher)
        
        val btnSairConta = findViewById<Button>(R.id.btnSairConta)

        tvNomeUsuario.text = "Olá, $username!"

        // Busca dados do Firebase (Plano do Usuário)
        db.collection("usuarios").whereEqualTo("usuario", username).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    val status = doc.getString("status")?.uppercase() ?: "DESCONHECIDO"
                    val vencimento = doc.getString("vencimento") ?: "Ilimitado"

                    tvStatusPlano.text = status
                    when (status) {
                        "ATIVO" -> { tvStatusPlano.setBackgroundColor(Color.parseColor("#2ED573")); tvStatusPlano.setTextColor(Color.BLACK) }
                        "TESTE" -> { tvStatusPlano.setBackgroundColor(Color.parseColor("#FFC107")); tvStatusPlano.setTextColor(Color.BLACK) }
                        "BLOQUEADO" -> { tvStatusPlano.setBackgroundColor(Color.parseColor("#FF4757")); tvStatusPlano.setTextColor(Color.WHITE) }
                    }
                    tvVencimento.text = "Vencimento: $vencimento"
                }
            }

        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
        
        tvStatusEpg.text = "Última atualização: ${prefs.getString("LAST_EPG_UPDATE", "Nunca")}"

        switchApps.isChecked = prefs.getBoolean("SHOW_APPS", true)
        switchSD.isChecked = prefs.getBoolean("FILTER_SD", false)
        switchHD.isChecked = prefs.getBoolean("FILTER_HD", false)
        switchFHD.isChecked = prefs.getBoolean("FILTER_FHD", false)
        switchH265.isChecked = prefs.getBoolean("FILTER_H265", false)
        switch4K.isChecked = prefs.getBoolean("FILTER_4K", false)
        switchParental.isChecked = prefs.getBoolean("PARENTAL_CONTROL", false)

        val aliasName = ComponentName(this, "com.signalplay.tv.LauncherAlias")
        var isLauncherEnabled = packageManager.getComponentEnabledSetting(aliasName) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        switchLauncher.isChecked = isLauncherEnabled

        val focusListener = View.OnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.bringToFront()
                v.animate().scaleX(1.03f).scaleY(1.03f).translationZ(15f).setDuration(250).setInterpolator(interpolator).start()
                v.setBackgroundResource(R.drawable.bg_menu_focus)
            } else {
                v.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(250).setInterpolator(interpolator).start()
                v.setBackgroundResource(R.drawable.bg_glass)
            }
        }
        
        btnAtualizarEPG.onFocusChangeListener = focusListener
        btnShowApps.onFocusChangeListener = focusListener
        btnParental.onFocusChangeListener = focusListener
        btnFilterSD.onFocusChangeListener = focusListener
        btnFilterHD.onFocusChangeListener = focusListener
        btnFilterFHD.onFocusChangeListener = focusListener
        btnFilterH265.onFocusChangeListener = focusListener
        btnFilter4K.onFocusChangeListener = focusListener
        btnLimparFavs.onFocusChangeListener = focusListener
        btnLimparHist.onFocusChangeListener = focusListener
        btnModoLauncher.onFocusChangeListener = focusListener

        btnSairConta.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            if (hasFocus) v.animate().scaleX(1.03f).scaleY(1.03f).translationZ(15f).setDuration(250).setInterpolator(interpolator).start()
            else v.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(250).setInterpolator(interpolator).start()
        }

        // =========================================================================
        // AÇÕES DOS BOTÕES E SWITCHES
        // =========================================================================

        btnShowApps.setOnClickListener {
            val newState = !switchApps.isChecked
            prefs.edit().putBoolean("SHOW_APPS", newState).apply()
            switchApps.isChecked = newState
        }

        btnFilterSD.setOnClickListener { val st = !switchSD.isChecked; prefs.edit().putBoolean("FILTER_SD", st).apply(); switchSD.isChecked = st }
        btnFilterHD.setOnClickListener { val st = !switchHD.isChecked; prefs.edit().putBoolean("FILTER_HD", st).apply(); switchHD.isChecked = st }
        btnFilterFHD.setOnClickListener { val st = !switchFHD.isChecked; prefs.edit().putBoolean("FILTER_FHD", st).apply(); switchFHD.isChecked = st }
        btnFilterH265.setOnClickListener { val st = !switchH265.isChecked; prefs.edit().putBoolean("FILTER_H265", st).apply(); switchH265.isChecked = st }
        btnFilter4K.setOnClickListener { val st = !switch4K.isChecked; prefs.edit().putBoolean("FILTER_4K", st).apply(); switch4K.isChecked = st }

        btnParental.setOnClickListener {
            val isAtualmenteAtivo = switchParental.isChecked
            val savedPin = prefs.getString("PARENTAL_PIN", "")

            if (!isAtualmenteAtivo) {
                // Vai ATIVAR. Precisamos criar o PIN se não existir.
                if (savedPin.isNullOrEmpty()) {
                    showCustomDialog("Criar PIN Parental", "Digite 4 números para proteger o conteúdo adulto:", true) { inputPin ->
                        if (inputPin.length == 4) {
                            prefs.edit().putString("PARENTAL_PIN", inputPin).putBoolean("PARENTAL_CONTROL", true).apply()
                            switchParental.isChecked = true
                            Toast.makeText(this, "Controle Parental Ativado!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "O PIN deve ter 4 dígitos.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    prefs.edit().putBoolean("PARENTAL_CONTROL", true).apply()
                    switchParental.isChecked = true
                }
            } else {
                // Vai DESATIVAR. Exige o PIN.
                showCustomDialog("Desativar Controle Parental", "Digite seu PIN de 4 números:", true) { inputPin ->
                    if (inputPin == savedPin) {
                        prefs.edit().putBoolean("PARENTAL_CONTROL", false).apply()
                        switchParental.isChecked = false
                        Toast.makeText(this, "Controle Parental Desativado!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "PIN Incorreto!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnLimparFavs.setOnClickListener {
            showCustomDialog("Zerar Favoritos", "Tem certeza que deseja apagar todos os canais salvos?", false) {
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
        }

        btnLimparHist.setOnClickListener {
            showCustomDialog("Limpar Histórico", "Isso apagará a prateleira 'Continuar Assistindo'. Confirma?", false) {
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
        }

        btnModoLauncher.setOnClickListener {
            isLauncherEnabled = !isLauncherEnabled
            val newState = if (isLauncherEnabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            packageManager.setComponentEnabledSetting(aliasName, newState, PackageManager.DONT_KILL_APP)
            switchLauncher.isChecked = isLauncherEnabled
            
            if(isLauncherEnabled) Toast.makeText(this, "Modo TV Box ATIVADO! Aperte o botão da Casinha (Home).", Toast.LENGTH_LONG).show()
            else Toast.makeText(this, "Modo TV Box DESATIVADO!", Toast.LENGTH_SHORT).show()
        }

        btnAtualizarEPG.setOnClickListener {
            Toast.makeText(this, "Baixando Guia (EPG)... Aguarde.", Toast.LENGTH_LONG).show()
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

                        val sdfAg = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
                        val dataHora = sdfAg.format(Date())
                        prefs.edit().putString("LAST_EPG_UPDATE", dataHora).apply()

                        withContext(Dispatchers.Main) {
                            tvStatusEpg.text = "Última atualização: $dataHora"
                            Toast.makeText(this@SettingsActivity, "EPG Sincronizado!", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SettingsActivity, "Erro ao baixar EPG.", Toast.LENGTH_SHORT).show()
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

    // =========================================================================
    // POP-UP UNIVERSAL DE VIDRO (PIN E CONFIRMAÇÕES)
    // =========================================================================
    private fun showCustomDialog(titulo: String, mensagem: String, isPinMode: Boolean, onConfirm: (String) -> Unit) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_custom)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle = dialog.findViewById<TextView>(R.id.dialogTitle)
        val tvMessage = dialog.findViewById<TextView>(R.id.dialogMessage)
        val edtInput = dialog.findViewById<EditText>(R.id.dialogInput)
        val btnCancel = dialog.findViewById<Button>(R.id.btnDialogCancel)
        val btnConfirm = dialog.findViewById<Button>(R.id.btnDialogConfirm)

        tvTitle.text = titulo
        tvMessage.text = mensagem

        if (isPinMode) {
            edtInput.visibility = View.VISIBLE
            edtInput.requestFocus()
        } else {
            edtInput.visibility = View.GONE
            btnConfirm.requestFocus()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            val inputResult = edtInput.text.toString()
            if (isPinMode && inputResult.isEmpty()) {
                Toast.makeText(this, "Digite o PIN!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            onConfirm(inputResult)
            dialog.dismiss()
        }

        dialog.show()
    }
}
