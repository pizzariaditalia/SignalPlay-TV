package com.signalplay.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {

    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = FirebaseFirestore.getInstance()

        // Lendo os IDs exatos do seu activity_main.xml
        val edtUsuario = findViewById<EditText>(R.id.edtUsuario)
        val edtSenha = findViewById<EditText>(R.id.edtSenha)
        val btnEntrar = findViewById<Button>(R.id.btnEntrar)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        // =========================================================================
        // MÁGICA: VERIFICADOR DE ATUALIZAÇÃO EM NUVEM (IN-APP UPDATER)
        // =========================================================================
        verificarAtualizacao()

        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("URL", "")
        val savedUser = prefs.getString("USER", "")
        val savedPass = prefs.getString("PASS", "")
        val savedUsername = prefs.getString("USERNAME", "")

        if (!savedUrl.isNullOrEmpty() && !savedUser.isNullOrEmpty() && !savedPass.isNullOrEmpty()) {
            val intent = Intent(this, HomeActivity::class.java)
            intent.putExtra("URL", savedUrl)
            intent.putExtra("USER", savedUser)
            intent.putExtra("PASS", savedPass)
            intent.putExtra("USERNAME", savedUsername)
            startActivity(intent)
            finish()
            return
        }

        btnEntrar.setOnClickListener {
            val user = edtUsuario.text.toString().trim()
            val pass = edtSenha.text.toString().trim()

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Preencha usuário e senha!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Ativa o carregamento visual usando os elementos que você já tem
            progressBar.visibility = View.VISIBLE
            btnEntrar.isEnabled = false
            btnEntrar.text = "Validando Acesso..."
            edtUsuario.isEnabled = false
            edtSenha.isEnabled = false

            db.collection("usuarios").whereEqualTo("usuario", user).whereEqualTo("senha", pass).get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.isEmpty) {
                        restaurarBotoes(progressBar, btnEntrar, edtUsuario, edtSenha)
                        Toast.makeText(this, "Usuário ou senha incorretos!", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    val dadosUser = snapshot.documents[0]
                    val status = dadosUser.getString("status")
                    if (status != "ativo" && status != "teste") {
                        restaurarBotoes(progressBar, btnEntrar, edtUsuario, edtSenha)
                        Toast.makeText(this, "Conta bloqueada ou expirada!", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    val serverId = dadosUser.getString("servidor_id")
                    if (serverId.isNullOrEmpty()) {
                        restaurarBotoes(progressBar, btnEntrar, edtUsuario, edtSenha)
                        Toast.makeText(this, "Nenhum servidor vinculado à sua conta.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    db.collection("servidores").document(serverId).get().addOnSuccessListener { serverDoc ->
                        if (!serverDoc.exists()) {
                            restaurarBotoes(progressBar, btnEntrar, edtUsuario, edtSenha)
                            Toast.makeText(this, "Servidor offline.", Toast.LENGTH_LONG).show()
                            return@addOnSuccessListener
                        }

                        val sUrl = serverDoc.getString("url") ?: ""
                        val sUser = serverDoc.getString("xtream_user") ?: ""
                        val sPass = serverDoc.getString("xtream_pass") ?: ""

                        prefs.edit()
                            .putString("URL", sUrl)
                            .putString("USER", sUser)
                            .putString("PASS", sPass)
                            .putString("USERNAME", user)
                            .apply()

                        val intent = Intent(this, HomeActivity::class.java)
                        intent.putExtra("URL", sUrl)
                        intent.putExtra("USER", sUser)
                        intent.putExtra("PASS", sPass)
                        intent.putExtra("USERNAME", user)
                        startActivity(intent)
                        finish()
                    }
                }
                .addOnFailureListener {
                    restaurarBotoes(progressBar, btnEntrar, edtUsuario, edtSenha)
                    Toast.makeText(this, "Erro ao conectar com o banco de dados.", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun restaurarBotoes(progress: ProgressBar, btn: Button, edtUser: EditText, edtPass: EditText) {
        progress.visibility = View.GONE
        btn.isEnabled = true
        btn.text = "Entrar"
        edtUser.isEnabled = true
        edtPass.isEnabled = true
    }

    // =========================================================================
    // LÓGICA DO DOWNLOAD E INSTALAÇÃO
    // =========================================================================
    private fun verificarAtualizacao() {
        db.collection("configuracoes").document("app").get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val versaoNuvem = doc.getLong("versao_atual")?.toInt() ?: 1
                val linkDownload = doc.getString("link_apk") ?: ""
                
                // Pega a versão instalada na TV do cliente
                val versaoApp = try {
                    packageManager.getPackageInfo(packageName, 0).versionCode
                } catch (e: Exception) { 1 }

                // Se a versão da Nuvem for maior, BLOQUEIA O APP E EXIGE ATUALIZAÇÃO!
                if (versaoNuvem > versaoApp && linkDownload.isNotEmpty()) {
                    mostrarTelaDeAtualizacaoForcada(linkDownload)
                }
            }
        }
    }

    private fun mostrarTelaDeAtualizacaoForcada(linkApk: String) {
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val btnEntrar = findViewById<Button>(R.id.btnEntrar)
        val edtUsuario = findViewById<EditText>(R.id.edtUsuario)
        val edtSenha = findViewById<EditText>(R.id.edtSenha)

        progressBar.visibility = View.VISIBLE
        btnEntrar.isEnabled = false
        edtUsuario.isEnabled = false
        edtSenha.isEnabled = false
        
        // Usa o botão principal para mostrar o status do download
        baixarEInstalarApk(linkApk, btnEntrar)
    }

    private fun baixarEInstalarApk(apkUrl: String, btnStatus: Button) {
        btnStatus.text = "Baixando atualização..."
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(apkUrl).build()
                val response = client.newCall(request).execute()
                val body = response.body
                
                if (body != null) {
                    val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "SignalPlay_Update.apk")
                    val inputStream = body.byteStream()
                    val outputStream = FileOutputStream(file)
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    
                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()

                    withContext(Dispatchers.Main) {
                        btnStatus.text = "Iniciando Instalação..."
                        instalarApk(file)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnStatus.text = "Erro ao atualizar. Reinicie o App."
                }
            }
        }
    }

    private fun instalarApk(file: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            
            val apkUri: Uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
            
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao tentar abrir o instalador.", Toast.LENGTH_LONG).show()
        }
    }
}
