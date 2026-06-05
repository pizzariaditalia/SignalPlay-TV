package com.signalplay.tv

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
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

    private var isParentalActive = false
    private var filterSD = false
    private var filterHD = false
    private var filterFHD = false
    private var filterH265 = false
    private var filter4K = false

    private var downloadId: Long = -1L

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
        
        val btnCheckUpdate = findViewById<LinearLayout>(R.id.btnCheckUpdate)
        val btnAtualizarEPG = findViewById<LinearLayout>(R.id.btnAtualizarEPG)
        val tvStatusEpg = findViewById<TextView>(R.id.tvStatusEpg)

        val btnHideApps = findViewById<LinearLayout>(R.id.btnHideApps)

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

        val btnSpeedTest = findViewById<LinearLayout>(R.id.btnSpeedTest)
        val btnLimparCache = findViewById<LinearLayout>(R.id.btnLimparCache)

        val btnLimparFavs = findViewById<LinearLayout>(R.id.btnLimparFavs)
        val btnLimparHist = findViewById<LinearLayout>(R.id.btnLimparHist)
        
        val btnModoLauncher = findViewById<LinearLayout>(R.id.btnModoLauncher)
        val switchLauncher = findViewById<Switch>(R.id.switchLauncher)
        
        val btnSairConta = findViewById<Button>(R.id.btnSairConta)

        tvNomeUsuario.text = "Olá, $username!"

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

        isParentalActive = prefs.getBoolean("PARENTAL_CONTROL", false)
        filterSD = prefs.getBoolean("FILTER_SD", false)
        filterHD = prefs.getBoolean("FILTER_HD", false)
        filterFHD = prefs.getBoolean("FILTER_FHD", false)
        filterH265 = prefs.getBoolean("FILTER_H265", false)
        filter4K = prefs.getBoolean("FILTER_4K", false)

        switchParental.isChecked = isParentalActive
        switchSD.isChecked = filterSD
        switchHD.isChecked = filterHD
        switchFHD.isChecked = filterFHD
        switchH265.isChecked = filterH265
        switch4K.isChecked = filter4K

        val aliasName = ComponentName(this, "com.signalplay.tv.LauncherAlias")
        var isLauncherEnabled = packageManager.getComponentEnabledSetting(aliasName) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        switchLauncher.isChecked = isLauncherEnabled

        val focusListener = View.OnFocusChangeListener { v, hasFocus ->
            val vg = v as? ViewGroup
            val tvTitulo = vg?.getChildAt(0) as? TextView
            val tvSub = vg?.getChildAt(1) as? TextView

            if (hasFocus) {
                v.animate().scaleX(1.03f).scaleY(1.03f).translationZ(15f).setDuration(250).setInterpolator(interpolator).start()
                v.setBackgroundResource(R.drawable.bg_menu_focus)
                tvTitulo?.setTextColor(Color.BLACK)
                tvSub?.setTextColor(Color.DKGRAY)
            } else {
                v.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(250).setInterpolator(interpolator).start()
                v.setBackgroundResource(R.drawable.bg_glass)
                tvTitulo?.setTextColor(Color.WHITE)
                tvSub?.setTextColor(Color.parseColor("#E0E0E0"))
            }
        }
        
        btnCheckUpdate?.onFocusChangeListener = focusListener
        btnAtualizarEPG.onFocusChangeListener = focusListener
        btnHideApps.onFocusChangeListener = focusListener
        btnParental.onFocusChangeListener = focusListener
        btnFilterSD.onFocusChangeListener = focusListener
        btnFilterHD.onFocusChangeListener = focusListener
        btnFilterFHD.onFocusChangeListener = focusListener
        btnFilterH265.onFocusChangeListener = focusListener
        btnFilter4K.onFocusChangeListener = focusListener
        btnSpeedTest.onFocusChangeListener = focusListener
        btnLimparCache.onFocusChangeListener = focusListener
        btnLimparFavs.onFocusChangeListener = focusListener
        btnLimparHist.onFocusChangeListener = focusListener
        btnModoLauncher.onFocusChangeListener = focusListener

        btnSairConta.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            if (hasFocus) v.animate().scaleX(1.03f).scaleY(1.03f).translationZ(15f).setDuration(250).setInterpolator(interpolator).start()
            else v.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(250).setInterpolator(interpolator).start()
        }

        btnCheckUpdate?.setOnClickListener {
            Toast.makeText(this, "Procurando novas atualizações...", Toast.LENGTH_SHORT).show()
            db.collection("configuracoes").document("app").get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val versaoNuvem = doc.getLong("versao_atual") ?: 1L
                        val linkApk = doc.getString("link_apk") ?: ""
                        
                        val versaoInstalada = try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                packageManager.getPackageInfo(packageName, 0).longVersionCode
                            } else {
                                @Suppress("DEPRECATION")
                                packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
                            }
                        } catch (e: Exception) { 1L }

                        if (versaoNuvem > versaoInstalada && linkApk.isNotEmpty()) {
                            showCustomDialog("Nova Versão Encontrada!", "A versão $versaoNuvem está disponível. Deseja baixar e instalar agora?", false) {
                                baixarEInstalarAtualizacao(linkApk)
                            }
                        } else {
                            Toast.makeText(this, "O sistema já está atualizado com a versão mais recente!", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Falha ao consultar o servidor.", Toast.LENGTH_SHORT).show()
                }
        }

        btnHideApps.setOnClickListener { abrirDialogoOcultarApps() }
        btnFilterSD.setOnClickListener { filterSD = !switchSD.isChecked; prefs.edit().putBoolean("FILTER_SD", filterSD).apply(); switchSD.isChecked = filterSD }
        btnFilterHD.setOnClickListener { filterHD = !switchHD.isChecked; prefs.edit().putBoolean("FILTER_HD", filterHD).apply(); switchHD.isChecked = filterHD }
        btnFilterFHD.setOnClickListener { filterFHD = !switchFHD.isChecked; prefs.edit().putBoolean("FILTER_FHD", filterFHD).apply(); switchFHD.isChecked = filterFHD }
        btnFilterH265.setOnClickListener { filterH265 = !switchH265.isChecked; prefs.edit().putBoolean("FILTER_H265", filterH265).apply(); switchH265.isChecked = filterH265 }
        btnFilter4K.setOnClickListener { filter4K = !switch4K.isChecked; prefs.edit().putBoolean("FILTER_4K", filter4K).apply(); switch4K.isChecked = filter4K }
        btnSpeedTest.setOnClickListener { executarTesteVelocidade() }

        btnLimparCache.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    cacheDir.deleteRecursively()
                    withContext(Dispatchers.Main) { Toast.makeText(this@SettingsActivity, "Cache limpo! TV Box otimizada.", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) {}
            }
        }

        btnParental.setOnClickListener {
            val savedPin = prefs.getString("PARENTAL_PIN", "")
            if (!switchParental.isChecked) {
                if (savedPin.isNullOrEmpty()) {
                    showCustomDialog("Criar PIN Parental", "Digite 4 números para proteger o conteúdo adulto:", true) { inputPin ->
                        if (inputPin.length == 4) {
                            prefs.edit().putString("PARENTAL_PIN", inputPin).putBoolean("PARENTAL_CONTROL", true).apply()
                            switchParental.isChecked = true
                            Toast.makeText(this, "Controle Parental Ativado!", Toast.LENGTH_SHORT).show()
                        } else Toast.makeText(this, "O PIN deve ter 4 dígitos.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    prefs.edit().putBoolean("PARENTAL_CONTROL", true).apply()
                    switchParental.isChecked = true
                }
            } else {
                showCustomDialog("Desativar Controle Parental", "Digite seu PIN de 4 números:", true) { inputPin ->
                    if (inputPin == savedPin) {
                        prefs.edit().putBoolean("PARENTAL_CONTROL", false).apply()
                        switchParental.isChecked = false
                        Toast.makeText(this, "Controle Parental Desativado!", Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(this, "PIN Incorreto!", Toast.LENGTH_SHORT).show()
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
                    withContext(Dispatchers.Main) { Toast.makeText(this@SettingsActivity, "Erro ao baixar EPG.", Toast.LENGTH_SHORT).show() }
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

    private fun baixarEInstalarAtualizacao(urlLink: String) {
        Toast.makeText(this, "Iniciando download...", Toast.LENGTH_SHORT).show()
        try {
            val request = DownloadManager.Request(Uri.parse(urlLink))
            request.setTitle("Atualização SignalPlay TV")
            request.setDescription("Baixando a versão mais recente do sistema...")
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "SignalPlay_Update.apk")
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)

            val fileAntigo = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SignalPlay_Update.apk")
            if (fileAntigo.exists()) fileAntigo.delete()

            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = manager.enqueue(request)

            val onComplete = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        instalarApk()
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao iniciar o download.", Toast.LENGTH_LONG).show()
        }
    }

    private fun instalarApk() {
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SignalPlay_Update.apk")
        if (file.exists()) {
            val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Autorize a instalação de fontes desconhecidas.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun executarTesteVelocidade() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_custom)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle = dialog.findViewById<TextView>(R.id.dialogTitle)
        val tvMessage = dialog.findViewById<TextView>(R.id.dialogMessage)
        val edtInput = dialog.findViewById<EditText>(R.id.dialogInput)
        val btnCancel = dialog.findViewById<Button>(R.id.btnDialogCancel)
        val btnConfirm = dialog.findViewById<Button>(R.id.btnDialogConfirm)

        tvTitle.text = "Teste de Velocidade"
        tvMessage.text = "Calculando a capacidade de download real da sua rede...\n\nIsso pode levar alguns segundos."
        edtInput.visibility = View.GONE
        btnCancel.visibility = View.GONE
        btnConfirm.text = "Aguarde..."
        btnConfirm.isEnabled = false

        dialog.show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
                val req = Request.Builder().url("https://speed.cloudflare.com/__down?bytes=15000000").build()
                
                val startTime = System.currentTimeMillis()
                val res = client.newCall(req).execute()
                res.body?.bytes() 
                val endTime = System.currentTimeMillis()

                val timeInSeconds = (endTime - startTime) / 1000.0
                if (timeInSeconds > 0) {
                    val megabytes = 15.0
                    val mbps = (megabytes * 8) / timeInSeconds 

                    withContext(Dispatchers.Main) {
                        tvMessage.text = String.format("A velocidade de conexão de entrada na TV Box é de:\n\n%.1f Mbps", mbps)
                        btnConfirm.text = "Fechar"
                        btnConfirm.isEnabled = true
                        btnConfirm.setOnClickListener { dialog.dismiss() }
                        btnConfirm.requestFocus()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvMessage.text = "Não foi possível testar a conexão.\nVerifique se o aparelho possui acesso à internet."
                    btnConfirm.text = "Fechar"
                    btnConfirm.isEnabled = true
                    btnConfirm.setOnClickListener { dialog.dismiss() }
                    btnConfirm.requestFocus()
                }
            }
        }
    }

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

    private fun abrirDialogoOcultarApps() {
        val pm = packageManager
        val installedApps = mutableMapOf<String, String>() 

        try {
            val intentLeanback = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) }
            val leanbackApps = pm.queryIntentActivities(intentLeanback, 0)
            for (resolveInfo in leanbackApps) {
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg == applicationContext.packageName) continue
                installedApps[pkg] = resolveInfo.loadLabel(pm).toString()
            }

            val intentLauncher = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val launcherApps = pm.queryIntentActivities(intentLauncher, 0)
            for (resolveInfo in launcherApps) {
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg == applicationContext.packageName || installedApps.containsKey(pkg)) continue
                installedApps[pkg] = resolveInfo.loadLabel(pm).toString()
            }
        } catch (e: Exception) {}

        val appList = installedApps.toList().sortedBy { it.second }
        val appNames = appList.map { it.second }.toTypedArray()
        val appPackages = appList.map { it.first }

        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
        val hiddenAppsSet = prefs.getStringSet("HIDDEN_APPS", mutableSetOf()) ?: mutableSetOf()
        
        val checkedItems = BooleanArray(appList.size) { i -> hiddenAppsSet.contains(appPackages[i]) }

        val builder = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        builder.setTitle("Ocultar Aplicativos da Tela Inicial")
        builder.setMultiChoiceItems(appNames, checkedItems) { _, which, isChecked ->
            checkedItems[which] = isChecked
        }
        builder.setPositiveButton("Salvar") { _, _ ->
            val newHiddenApps = mutableSetOf<String>()
            for (i in checkedItems.indices) {
                if (checkedItems[i]) newHiddenApps.add(appPackages[i])
            }
            prefs.edit().putStringSet("HIDDEN_APPS", newHiddenApps).apply()
            Toast.makeText(this, "Lista de aplicativos atualizada!", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }
}
