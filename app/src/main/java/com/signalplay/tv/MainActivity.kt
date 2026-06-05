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
        val savedUser = prefs.getString("USER", "")
        val savedPass = prefs.getString("PASS", "")

        // Login Automático usando as credenciais salvas
        if (!savedUser.isNullOrEmpty() && !savedPass.isNullOrEmpty()) {
            edtUser.setText(savedUser)
            edtPass.setText(savedPass)
            fazerLogin(savedUser, savedPass)
        }

        btnLogin.setOnClickListener {
            val user = edtUser.text.toString().trim()
            val pass = edtPass.text.toString().trim()

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Preencha usuário e senha!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            fazerLogin(user, pass)
        }
    }

    private fun fazerLogin(user: String, pass: String) {
        btnLogin.isEnabled = false
        progressBarLogin.visibility = View.VISIBLE

        // 1. Vai no Firebase conferir o Usuário
        db.collection("usuarios").whereEqualTo("usuario", user).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    val status = doc.getString("status")?.uppercase() ?: "ATIVO"
                    
                    // MÁGICA: Busca a URL que você gravou lá no documento do Firebase (campo "url")
                    val urlNuvem = doc.getString("url") ?: ""

                    if (status == "BLOQUEADO") {
                        Toast.makeText(this@MainActivity, "Sua conta está bloqueada! Contate o suporte.", Toast.LENGTH_LONG).show()
                        btnLogin.isEnabled = true
                        progressBarLogin.visibility = View.GONE
                        return@addOnSuccessListener
                    }

                    if (urlNuvem.isEmpty()) {
                        Toast.makeText(this@MainActivity, "Nenhum servidor vinculado a este usuário.", Toast.LENGTH_LONG).show()
                        btnLogin.isEnabled = true
                        progressBarLogin.visibility = View.GONE
                        return@addOnSuccessListener
                    }

                    // Prepara a URL removendo a barra no final (se houver) e valida no Painel
                    val urlFormatada = if (urlNuvem.endsWith("/")) urlNuvem.dropLast(1) else urlNuvem
                    validarNoPainelXtream(urlFormatada, user, pass)
                    
                } else {
                    Toast.makeText(this@MainActivity, "Usuário não encontrado no sistema!", Toast.LENGTH_LONG).show()
                    btnLogin.isEnabled = true
                    progressBarLogin.visibility = View.GONE
                }
            }
            .addOnFailureListener {
                Toast.makeText(this@MainActivity, "Erro ao conectar ao banco de dados.", Toast.LENGTH_LONG).show()
                btnLogin.isEnabled = true
                progressBarLogin.visibility = View.GONE
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
                                .putString("URL", url) // Salva a URL para o app usar, mas o cliente não vê
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

                            // Atualiza os dados de vencimento lá no Firebase
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
                            Toast.makeText(this@MainActivity, "Usuário ou Senha inválidos no servidor!", Toast.LENGTH_LONG).show()
                            btnLogin.isEnabled = true
                            progressBarLogin.visibility = View.GONE
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Erro no servidor Xtream.", Toast.LENGTH_LONG).show()
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
