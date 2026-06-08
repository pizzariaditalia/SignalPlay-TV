package com.signalplay.tv

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {

    private lateinit var edtUser: EditText
    private lateinit var edtPass: EditText
    private lateinit var btnLogin: Button
    private lateinit var chkLembrar: CheckBox
    private lateinit var progressBarLogin: ProgressBar
    private lateinit var tvVersion: TextView
    private lateinit var loginOverlay: RelativeLayout
    private lateinit var tvLoadingStatus: TextView
    
    private lateinit var db: FirebaseFirestore
    private var downloadId: Long = -1L
    private var currentSessionId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = FirebaseFirestore.getInstance()

        edtUser = findViewById(R.id.edtUser)
        edtPass = findViewById(R.id.edtPass)
        btnLogin = findViewById(R.id.btnLogin)
        chkLembrar = findViewById(R.id.chkLembrar)
        progressBarLogin = findViewById(R.id.progressBarLogin)
        tvVersion = findViewById(R.id.tvVersion)
        loginOverlay = findViewById(R.id.loginOverlay)
        tvLoadingStatus = findViewById(R.id.tvLoadingStatus)
        
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "1.0"
        }
        tvVersion.text = "Versão: $versionName"

        verificarAtualizacao()

        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
        val savedFbUser = prefs.getString("FIREBASE_USER", "")
        val savedFbPass = prefs.getString("FIREBASE_PASS", "")

        if (!savedFbUser.isNullOrEmpty() && !savedFbPass.isNullOrEmpty()) {
            edtUser.setText(savedFbUser)
            edtPass.setText(savedFbPass)
            chkLembrar.isChecked = true
            fazerLogin(savedFbUser, savedFbPass)
        }

        btnLogin.setOnClickListener {
            val user = edtUser.text.toString().trim()
            val pass = edtPass.text.toString().trim()

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Preencha o Usuário e a Senha!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            fazerLogin(user, pass)
        }
    }

    private fun showError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        btnLogin.isEnabled = true
        loginOverlay.visibility = View.GONE
    }

    private fun fazerLogin(userDigitado: String, passDigitada: String) {
        btnLogin.isEnabled = false
        // Exibe o Banner de Intro imediatamente ao clicar
        loginOverlay.visibility = View.VISIBLE
        tvLoadingStatus.text = "Validando acesso..."

        db.collection("usuarios")
            .whereEqualTo("usuario", userDigitado)
            .whereEqualTo("senha", passDigitada)
            .get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    showError("Usuário ou senha inválidos.")
                    return@addOnSuccessListener
                }

                val dadosFirebase = snapshot.documents[0]
                val docId = dadosFirebase.id
                val status = dadosFirebase.getString("status")?.lowercase() ?: ""
                
                var isVencido = false
                val vencObj = dadosFirebase.get("vencimento")

                if (vencObj != null) {
                    try {
                        var dataVencimento: Date? = null

                        if (vencObj is com.google.firebase.Timestamp) {
                            dataVencimento = vencObj.toDate()
                        } else {
                            val strData = vencObj.toString().trim()
                            if (strData.lowercase() != "ilimitado" && strData.isNotEmpty()) {
                                dataVencimento = try {
                                    if (strData.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(strData)
                                    } else {
                                        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(strData.replace("-", "/"))
                                    }
                                } catch (e: Exception) { null }
                            }
                        }

                        if (dataVencimento != null) {
                            if (Date().after(dataVencimento)) isVencido = true
                        }
                    } catch (e: Exception) { isVencido = true }
                }

                val maxTelas = dadosFirebase.getLong("telas")?.toInt() ?: 1
                val sessoes = dadosFirebase.get("sessoes") as? Map<String, Long> ?: emptyMap()
                val agora = System.currentTimeMillis()
                
                var contagemAtivas = 0
                val sessoesValidas = mutableMapOf<String, Long>()

                for ((idSessao, ultimoPing) in sessoes) {
                    if (agora - ultimoPing < 120000) {
                        sessoesValidas[idSessao] = ultimoPing
                        contagemAtivas++
                    }
                }

                if (contagemAtivas >= maxTelas) {
                    showError("Limite de $maxTelas tela(s) excedido! Feche o app em outro aparelho.")
                    return@addOnSuccessListener
                }

                currentSessionId = "tv_${UUID.randomUUID().toString().substring(0, 8)}"
                sessoesValidas[currentSessionId] = agora
                
                val bloqueios = dadosFirebase.get("bloqueios") as? Map<String, List<String>>
                val bCanais = bloqueios?.get("canais")?.joinToString(",") ?: ""
                val bFilmes = bloqueios?.get("filmes")?.joinToString(",") ?: ""
                val bSeries = bloqueios?.get("series")?.joinToString(",") ?: ""

                val ocultar4kForcado = dadosFirebase.getBoolean("ocultar_4k") ?: false
                val ocultarFhdForcado = dadosFirebase.getBoolean("ocultar_fhd") ?: false

                val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("SESSION_ID", currentSessionId)
                    .putString("USER_DOC_ID", docId)
                    .putString("BLOQUEIOS_CANAIS", bCanais)
                    .putString("BLOQUEIOS_FILMES", bFilmes)
                    .putString("BLOQUEIOS_SERIES", bSeries)
                    .putBoolean("SERVER_FORCED_HIDE_4K", ocultar4kForcado)
                    .putBoolean("SERVER_FORCED_HIDE_FHD", ocultarFhdForcado)
                    .apply()

                db.collection("usuarios").document(docId).update("sessoes", sessoesValidas)

                val servidorId = dadosFirebase.getString("servidor_id")
                if (servidorId.isNullOrEmpty()) {
                    showError("Nenhum servidor associado no painel.")
                    return@addOnSuccessListener
                }

                db.collection("servidores").document(servidorId).get(Source.SERVER)
                    .addOnSuccessListener { serverDoc ->
                        if (!serverDoc.exists()) {
                            showError("O servidor vinculado foi excluído.")
                            return@addOnSuccessListener
                        }
                        
                        val sData = serverDoc.data
                        val urlServidor = sData?.get("url")?.toString() ?: ""
                        val masterUser = sData?.get("xtream_user")?.toString() ?: ""
                        val masterPass = sData?.get("xtream_pass")?.toString() ?: ""

                        val cleanUrl = if (urlServidor.endsWith("/")) urlServidor.dropLast(1) else urlServidor

                        if (status == "bloqueado" || status == "pendente_pagamento" || isVencido) {
                            btnLogin.isEnabled = true
                            loginOverlay.visibility = View.GONE
                            val intent = Intent(this@MainActivity, PixActivity::class.java)
                            intent.putExtra("USERNAME", userDigitado)
                            intent.putExtra("SERVER_URL", cleanUrl) 
                            startActivity(intent)
                            return@addOnSuccessListener
                        }

                        if (status != "ativo" && status != "teste") {
                            showError("Acesso Suspenso: Status de conta inválido.")
                            return@addOnSuccessListener
                        }
                        
                        validarNoPainelXtream(cleanUrl, masterUser, masterPass, userDigitado, passDigitada)
                    }
                    .addOnFailureListener { showError("Erro ao buscar dados do servidor.") }
            }
            .addOnFailureListener { showError("Erro de conexão com o banco de dados.") }
    }

    private fun validarNoPainelXtream(url: String, masterUser: String, masterPass: String, firebaseUser: String, firebasePass: String) {
        tvLoadingStatus.text = "Conectando ao provedor..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()

                val req = Request.Builder()
                    .url("$url/player_api.php?username=$masterUser&password=$masterPass")
                    .build()

                val res = client.newCall(req).execute()
                val jsonStr = res.body?.string() ?: "{}"

                withContext(Dispatchers.Main) {
                    if (jsonStr.startsWith("{")) {
                        val json = JSONObject(jsonStr)
                        val userInfo = json.optJSONObject("user_info")

                        if (userInfo != null && userInfo.optInt("auth", 0) == 1) {
                            tvLoadingStatus.text = "Abrindo aplicativo..."
                            iniciarApp(url, masterUser, masterPass, firebaseUser, firebasePass)
                        } else {
                            showError("O Servidor IPTV recusou a conexão mestre.")
                        }
                    } else {
                        showError("Erro no servidor IPTV. Verifique a URL do painel Firebase.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showError("O servidor de canais não está respondendo.")
                }
            }
        }
    }

    private fun iniciarApp(url: String, masterUser: String, masterPass: String, firebaseUser: String, firebasePass: String) {
        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
        
        if (chkLembrar.isChecked) {
            prefs.edit()
                .putString("FIREBASE_USER", firebaseUser)
                .putString("FIREBASE_PASS", firebasePass)
                .putString("URL", url)
                .putString("USER", masterUser)
                .putString("PASS", masterPass)
                .putString("USERNAME", firebaseUser)
                .apply()
        } else {
            prefs.edit().remove("FIREBASE_USER").remove("FIREBASE_PASS").apply()
            prefs.edit()
                .putString("URL", url)
                .putString("USER", masterUser)
                .putString("PASS", masterPass)
                .putString("USERNAME", firebaseUser)
                .apply()
        }

        val intent = Intent(this@MainActivity, HomeActivity::class.java)
        intent.putExtra("URL", url)
        intent.putExtra("USER", masterUser)
        intent.putExtra("PASS", masterPass)
        intent.putExtra("USERNAME", firebaseUser)
        startActivity(intent)
        finish()
    }

    private fun verificarAtualizacao() {
        db.collection("configuracoes").document("app").get(Source.SERVER)
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    try {
                        val objVersao = doc.get("versao_atual")
                        val versaoNuvem: Long = when (objVersao) {
                            is Number -> objVersao.toLong()
                            is String -> objVersao.substringBefore(".").toLongOrNull() ?: 1L
                            else -> 1L
                        }

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
                            Toast.makeText(this, "Atualização Obrigatória Encontrada! Baixando...", Toast.LENGTH_LONG).show()
                            btnLogin.isEnabled = false
                            btnLogin.text = "BAIXANDO ATUALIZAÇÃO..."
                            edtUser.isEnabled = false
                            edtPass.isEnabled = false
                            chkLembrar.isEnabled = false
                            
                            baixarEInstalarAtualizacao(linkApk)
                        }
                    } catch (e: Exception) { }
                }
            }
    }

    private fun baixarEInstalarAtualizacao(url: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
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
                        btnLogin.text = "INSTALAR ATUALIZAÇÃO"
                        btnLogin.isEnabled = true
                        btnLogin.setOnClickListener { instalarApk() }
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
            Toast.makeText(this, "Erro ao iniciar o download. Verifique o link.", Toast.LENGTH_LONG).show()
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
                Toast.makeText(this, "Erro ao abrir o instalador. Por favor, dê as permissões necessárias.", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Arquivo da atualização não foi encontrado.", Toast.LENGTH_SHORT).show()
        }
    }
}
