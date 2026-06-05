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
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {

    // =======================================================
    // ATENÇÃO: ESSAS VARIÁVEIS AQUI NÃO PODEM SUMIR!
    // =======================================================
    private lateinit var edtUrl: EditText
    private lateinit var edtUser: EditText
    private lateinit var edtPass: EditText
    private lateinit var btnLogin: Button
    private lateinit var progressBarLogin: ProgressBar
    private lateinit var tvVersion: TextView
    private lateinit var db: FirebaseFirestore
    
    private var downloadId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = FirebaseFirestore.getInstance()

        edtUrl = findViewById(R.id.edtUrl)
        edtUser = findViewById(R.id.edtUser)
        edtPass = findViewById(R.id.edtPass)
        btnLogin = findViewById(R.id.btnLogin)
        progressBarLogin = findViewById(R.id.progressBarLogin)
        tvVersion = findViewById(R.id.tvVersion)
        
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "1.0"
        }
        tvVersion.text = "Versão: $versionName"

        verificarAtualizacao()

        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("URL", "")
        val savedUser = prefs.getString("USER", "")
        val savedPass = prefs.getString("PASS", "")

        if (!savedUrl.isNullOrEmpty() && !savedUser.isNullOrEmpty() && !savedPass.isNullOrEmpty()) {
            edtUrl.setText(savedUrl)
            edtUser.setText(savedUser)
            edtPass.setText(savedPass)
            fazerLogin(savedUrl, savedUser, savedPass)
        }

        btnLogin.setOnClickListener {
            val url = edtUrl.text.toString().trim()
            val user = edtUser.text.toString().trim()
            val pass = edtPass.text.toString().trim()

            if (url.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Preencha todos os campos!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            fazerLogin(url, user, pass)
        }
    }

    private fun fazerLogin(urlOriginal: String, user: String, pass: String) {
        btnLogin.isEnabled = false
        progressBarLogin.visibility = View.VISIBLE

        val url = if (urlOriginal.endsWith("/")) urlOriginal.dropLast(1) else urlOriginal

        db.collection("usuarios").whereEqualTo("usuario", user).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    val status = doc.getString("status")?.uppercase() ?: "ATIVO"

                    if (status == "BLOQUEADO") {
                        Toast.makeText(this@MainActivity, "Sua conta está bloqueada! Contate o suporte.", Toast.LENGTH_LONG).show()
                        btnLogin.isEnabled = true
                        progressBarLogin.visibility = View.GONE
                        return@addOnSuccessListener
                    }
                }
                validarNoPainelXtream(url, user, pass)
            }
            .addOnFailureListener {
                validarNoPainelXtream(url, user, pass)
            }
    }

    private fun validarNoPainelXtream(url: String, user: String, pass: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()

                val req = Request.Builder()
                    .url("$url/player_api.php?username=$user&password=$pass")
                    .build()

                val res = client.newCall(req).execute()
                val jsonStr = res.body?.string() ?: "{}"

                withContext(Dispatchers.Main) {
                    if (jsonStr.startsWith("{")) {
                        val json = JSONObject(jsonStr)
                        val userInfo = json.optJSONObject("user_info")

                        if (userInfo != null && userInfo.optInt("auth", 0) == 1) {
                            val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
                            prefs.edit()
                                .putString("URL", url)
                                .putString("USER", user)
                                .putString("PASS", pass)
                                .putString("USERNAME", user)
                                .apply()

                            val expDateLong = userInfo.optString("exp_date", "0").toLongOrNull() ?: 0L
                            val vencimentoFormatado = if (expDateLong > 0) {
                                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(expDateLong * 1000))
                            } else {
                                "Ilimitado"
                            }

                            val userData = hashMapOf(
                                "usuario" to user,
                                "status" to "ATIVO",
                                "vencimento" to vencimentoFormatado
                            )
                            db.collection("usuarios").document(user).set(userData, SetOptions.merge())

                            val intent = Intent(this@MainActivity, HomeActivity::class.java)
                            intent.putExtra("URL", url)
                            intent.putExtra("USER", user)
                            intent.putExtra("PASS", pass)
                            intent.putExtra("USERNAME", user)
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this@MainActivity, "Usuário ou Senha inválidos!", Toast.LENGTH_LONG).show()
                            btnLogin.isEnabled = true
                            progressBarLogin.visibility = View.GONE
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Erro no servidor. Verifique a URL.", Toast.LENGTH_LONG).show()
                        btnLogin.isEnabled = true
                        progressBarLogin.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Falha de conexão. Verifique sua internet.", Toast.LENGTH_LONG).show()
                    btnLogin.isEnabled = true
                    progressBarLogin.visibility = View.GONE
                }
            }
        }
    }

    private fun verificarAtualizacao() {
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
                    } catch (e: Exception) {
                        1L
                    }

                    if (versaoNuvem > versaoInstalada && linkApk.isNotEmpty()) {
                        Toast.makeText(this, "Atualização Obrigatória Encontrada! Baixando...", Toast.LENGTH_LONG).show()
                        btnLogin.isEnabled = false
                        btnLogin.text = "BAIXANDO ATUALIZAÇÃO..."
                        edtUrl.isEnabled = false
                        edtUser.isEnabled = false
                        edtPass.isEnabled = false
                        
                        baixarEInstalarAtualizacao(linkApk)
                    }
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