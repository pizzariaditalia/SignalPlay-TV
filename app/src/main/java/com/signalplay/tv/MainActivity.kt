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
        // Buscamos as credenciais do CLIENTE para o Auto-Login
        val savedClientUser = prefs.getString("USERNAME", "")
        val savedClientPass = prefs.getString("CLIENT_PASS", "")

        if (!savedClientUser.isNullOrEmpty() && !savedClientPass.isNullOrEmpty()) {
            edtUser.setText(savedClientUser)
            edtPass.setText(savedClientPass)
            fazerLogin(savedClientUser, savedClientPass)
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

    private fun mostrarErro(mensagem: String) {
        Toast.makeText(this@MainActivity, mensagem, Toast.LENGTH_LONG).show()
        btnLogin.isEnabled = true
        progressBarLogin.visibility = View.GONE
    }

    private fun fazerLogin(userDigitado: String, passDigitada: String) {
        btnLogin.isEnabled = false
        progressBarLogin.visibility = View.VISIBLE

        // PASSO 1: Busca o usuário e senha do cliente no Firebase
        db.collection("usuarios")
            .whereEqualTo("usuario", userDigitado)
            .whereEqualTo("senha", passDigitada)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    mostrarErro("Usuário ou senha inválidos!")
                    return@addOnSuccessListener
                }

                val doc = snapshot.documents[0]
                val docId = doc.id // Usado depois para atualizar o vencimento
                val status = doc.getString("status")?.lowercase() ?: ""

                if (status != "ativo" && status != "teste") {
                    mostrarErro("Acesso Suspenso: Conta expirada ou bloqueada!")
                    return@addOnSuccessListener
                }

                val servidorId = doc.getString("servidor_id")
                if (servidorId.isNullOrEmpty()) {
                    mostrarErro("Nenhum servidor associado no painel.")
                    return@addOnSuccessListener
                }

                // PASSO 2: Pega os dados do Servidor Oculto (A Ponte)
                db.collection("servidores").document(servidorId).get()
                    .addOnSuccessListener { serverDoc ->
                        if (!serverDoc.exists()) {
                            mostrarErro("O servidor vinculado foi excluído do sistema.")
                            return@addOnSuccessListener
                        }

                        val urlServidor = serverDoc.getString("url") ?: ""
                        val masterUser = serverDoc.getString("xtream_user") ?: ""
                        val masterPass = serverDoc.getString("xtream_pass") ?: ""
                        val tipoServer = serverDoc.getString("tipo") ?: "xtream"

                        if (tipoServer != "xtream") {
                            mostrarErro("Esta versão da TV suporta apenas servidores Xtream.")
                            return@addOnSuccessListener
                        }

                        val cleanUrl = if (urlServidor.endsWith("/")) urlServidor.dropLast(1) else urlServidor
                        
                        // PASSO 3: Logar usando a conta Master Oficial
                        validarNoPainelXtream(cleanUrl, masterUser, masterPass, userDigitado, passDigitada, docId)
                    }
                    .addOnFailureListener {
                        mostrarErro("Erro ao buscar as informações do servidor.")
                    }
            }
            .addOnFailureListener {
                mostrarErro("Erro ao conectar ao banco de dados.")
            }
    }

    private fun validarNoPainelXtream(url: String, masterUser: String, masterPass: String, clientUser: String, clientPass: String, userDocId: String) {
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
                            
                            // MÁGICA: Salvamos a conta Master para o Player, e o ClientUser para o Firebase!
                            val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
                            prefs.edit()
                                .putString("URL", url)
                                .putString("USER", masterUser) 
                                .putString("PASS", masterPass) 
                                .putString("USERNAME", clientUser) 
                                .putString("CLIENT_PASS", clientPass) 
                                .apply()

                            val expDateLong = userInfo.optString("exp_date", "0").toLongOrNull() ?: 0L
                            val vencimentoFormatado = if (expDateLong > 0) {
                                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(expDateLong * 1000))
                            } else {
                                "Ilimitado"
                            }

                            // Atualiza os dados de vencimento lá no Firebase do Cliente
                            val userData = hashMapOf(
                                "status" to "ATIVO",
                                "vencimento" to vencimentoFormatado
                            )
                            db.collection("usuarios").document(userDocId).set(userData, SetOptions.merge())

                            val intent = Intent(this@MainActivity, HomeActivity::class.java)
                            intent.putExtra("URL", url)
                            intent.putExtra("USER", masterUser)
                            intent.putExtra("PASS", masterPass)
                            intent.putExtra("USERNAME", clientUser)
                            startActivity(intent)
                            finish()
                        } else {
                            mostrarErro("O servidor Mestre IPTV recusou a conexão.")
                        }
                    } else {
                        mostrarErro("Erro no servidor Xtream. Resposta inválida.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    mostrarErro("Falha de conexão. Verifique a sua internet.")
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
