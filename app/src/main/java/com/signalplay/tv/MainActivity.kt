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
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
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
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {

    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = FirebaseFirestore.getInstance()

        val edtUser = findViewById<EditText>(R.id.edtUser)
        val edtPass = findViewById<EditText>(R.id.edtPass)
        val btnEntrar = findViewById<Button>(R.id.btnEntrar)
        val loadingOverlay = findViewById<RelativeLayout>(R.id.loadingOverlay)
        val tvLoadingMsg = findViewById<TextView>(R.id.tvLoadingMsg)

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
            val user = edtUser.text.toString().trim()
            val pass = edtPass.text.toString().trim()

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Preencha usuário e senha!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loadingOverlay.visibility = View.VISIBLE
            tvLoadingMsg.text = "Validando Acesso..."

            db.collection("usuarios").whereEqualTo("usuario", user).whereEqualTo("senha", pass).get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.isEmpty) {
                        loadingOverlay.visibility = View.GONE
                        Toast.makeText(this, "Usuário ou senha incorretos!", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    val dadosUser = snapshot.documents[0]
                    val status = dadosUser.getString("status")
                    if (status != "ativo" && status != "teste") {
                        loadingOverlay.visibility = View.GONE
                        Toast.makeText(this, "Conta bloqueada ou expirada!", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    val serverId = dadosUser.getString("servidor_id")
                    if (serverId.isNullOrEmpty()) {
                        loadingOverlay.visibility = View.GONE
                        Toast.makeText(this, "Nenhum servidor vinculado à sua conta.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    db.collection("servidores").document(serverId).get().addOnSuccessListener { serverDoc ->
                        if (!serverDoc.exists()) {
                            loadingOverlay.visibility = View.GONE
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
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(this, "Erro ao conectar com o banco de dados.", Toast.LENGTH_SHORT).show()
                }
        }
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
        val overlay = findViewById<RelativeLayout>(R.id.loadingOverlay)
        val tvMsg = findViewById<TextView>(R.id.tvLoadingMsg)
        val progress = findViewById<ProgressBar>(R.id.progressBarLogin) // Supondo que tenha um ID pra progressBar no seu XML, se não, ignora

        overlay.visibility = View.VISIBLE
        tvMsg.text = "Nova Atualização Disponível!"
        
        // Esconde tudo e força o download
        baixarEInstalarApk(linkApk, tvMsg)
    }

    private fun baixarEInstalarApk(apkUrl: String, tvStatus: TextView) {
        tvStatus.text = "Baixando atualização... Por favor, aguarde."
        
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
                        tvStatus.text = "Iniciando Instalação..."
                        instalarApk(file)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Erro ao baixar atualização. Reinicie o App."
                }
            }
        }
    }

    private fun instalarApk(file: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            
            // O FileProvider usa exatamente aquele XML que nós criamos no Passo 2!
            val apkUri: Uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
            
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao tentar abrir o instalador.", Toast.LENGTH_LONG).show()
        }
    }
}
