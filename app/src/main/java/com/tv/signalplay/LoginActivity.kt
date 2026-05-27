package com.tv.signalplay

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson

class LoginActivity : FragmentActivity() {

    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("isLogged", false)) {
            iniciarMainActivity(prefs.getString("FIREBASE_USER", "") ?: "", prefs.getString("XTREAM_USER", "") ?: "", prefs.getString("XTREAM_PASS", "") ?: "", prefs.getString("URL", "") ?: "")
            return
        }

        setContentView(R.layout.activity_login)

        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setProjectId("signalplay-tv")
                    .setApplicationId("1:51000338902:web:61d77a44dd62c0353a1c77")
                    .setApiKey("AIzaSyBSYJYEFLlDwBYsQC0I76n9NfAph2oWuLI")
                    .build()
                FirebaseApp.initializeApp(this, options)
            }
            db = FirebaseFirestore.getInstance()
        } catch (e: Exception) { Toast.makeText(this, "Erro ao iniciar Firebase", Toast.LENGTH_SHORT).show() }

        val btnLogin = findViewById<Button>(R.id.btn_login_entrar)
        val editUser = findViewById<EditText>(R.id.iptv_user_x)
        val editPass = findViewById<EditText>(R.id.iptv_pass_x)

        // Padronização do efeito de Foco da TV para os Inputs e Botão
        val focoListener = View.OnFocusChangeListener { v, focus -> 
            if(focus) {
                v.animate().scaleX(1.08f).scaleY(1.08f).start()
                v.elevation = 10f
            } else { 
                v.animate().scaleX(1.0f).scaleY(1.0f).start()
                v.elevation = 0f
            } 
        }

        editUser.onFocusChangeListener = focoListener
        editPass.onFocusChangeListener = focoListener
        btnLogin.onFocusChangeListener = focoListener

        btnLogin.setOnClickListener {
            val user = editUser.text.toString().trim()
            val pass = editPass.text.toString().trim()

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Preencha seu Usuário e Senha!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnLogin.text = "Validando..."
            btnLogin.isEnabled = false

            db.collection("usuarios").whereEqualTo("usuario", user).whereEqualTo("senha", pass).get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.isEmpty) { mostrarErro(btnLogin, "Usuário ou senha inválidos."); return@addOnSuccessListener }

                    val dadosFirebase = snapshot.documents[0]
                    val status = dadosFirebase.getString("status") ?: ""
                    if (status != "ativo" && status != "teste") { mostrarErro(btnLogin, "Acesso Suspenso: Conta expirada!"); return@addOnSuccessListener }
                    
                    val servidorId = dadosFirebase.getString("servidor_id") ?: ""
                    if (servidorId.isEmpty()) { mostrarErro(btnLogin, "Nenhum servidor associado."); return@addOnSuccessListener }

                    // Puxa Favoritos e Histórico do Banco na hora do Login
                    val favs = dadosFirebase.get("favoritos") as? List<String> ?: emptyList()
                    val hist = dadosFirebase.get("historico") as? List<Map<String, Any>> ?: emptyList()
                    prefs.edit().putString("favoritos_tv", Gson().toJson(favs)).putString("iptv_continuar_vod", Gson().toJson(hist)).apply()

                    btnLogin.text = "Conectando..."
                    db.collection("servidores").document(servidorId).get().addOnSuccessListener { sDoc ->
                        if (!sDoc.exists()) { mostrarErro(btnLogin, "Servidor excluído."); return@addOnSuccessListener }
                        
                        val tipoServer = sDoc.getString("tipo") ?: "xtream"
                        if(tipoServer == "xtream") {
                            val urlServidor = sDoc.getString("url")?.trimEnd('/') ?: ""
                            val masterUser = sDoc.getString("xtream_user") ?: ""
                            val masterPass = sDoc.getString("xtream_pass") ?: ""

                            prefs.edit().apply {
                                putBoolean("isLogged", true)
                                putString("FIREBASE_USER", user)
                                putString("XTREAM_USER", masterUser)
                                putString("XTREAM_PASS", masterPass)
                                putString("URL", urlServidor)
                                apply()
                            }
                            iniciarMainActivity(user, masterUser, masterPass, urlServidor)
                        } else {
                            mostrarErro(btnLogin, "Esta TV só suporta protocolo Xtream.")
                        }
                    }.addOnFailureListener { mostrarErro(btnLogin, "Falha na conexão do servidor.") }
                }.addOnFailureListener { mostrarErro(btnLogin, "Falha ao conectar com o banco de dados.") }
        }
    }

    private fun mostrarErro(btn: Button, msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        btn.text = "Entrar"
        btn.isEnabled = true
    }

    private fun iniciarMainActivity(fbUser: String, xtUser: String, xtPass: String, url: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("FIREBASE_USER", fbUser)
        intent.putExtra("XTREAM_USER", xtUser)
        intent.putExtra("XTREAM_PASS", xtPass)
        intent.putExtra("URL", url)
        startActivity(intent)
        finish()
    }
}
