package com.signalplay.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : Activity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var progressBar: ProgressBar
    private lateinit var btnEntrar: Button
    private lateinit var edtUsuario: EditText
    private lateinit var edtSenha: EditText
    private lateinit var chkLembrar: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = FirebaseFirestore.getInstance()
        
        edtUsuario = findViewById(R.id.edtUsuario)
        edtSenha = findViewById(R.id.edtSenha)
        btnEntrar = findViewById(R.id.btnEntrar)
        chkLembrar = findViewById(R.id.chkLembrar)
        progressBar = findViewById(R.id.progressBar)

        // Animação de botão
        btnEntrar.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) v.animate().scaleX(1.05f).scaleY(1.05f).translationZ(10f).setDuration(150).start()
            else v.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(150).start()
        }

        // 1. VERIFICA O LOGIN AUTOMÁTICO
        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
        val savedUser = prefs.getString("USERNAME", "")
        val savedPass = prefs.getString("PASSWORD", "")
        val lembrar = prefs.getBoolean("LEMBRAR", false)

        if (lembrar && !savedUser.isNullOrEmpty() && !savedPass.isNullOrEmpty()) {
            esconderFormulario()
            fazerLogin(savedUser, savedPass, true)
        } else {
            if (!savedUser.isNullOrEmpty()) edtUsuario.setText(savedUser)
            if (!savedPass.isNullOrEmpty()) edtSenha.setText(savedPass)
        }

        // 2. BOTÃO ENTRAR MANUAL
        btnEntrar.setOnClickListener {
            val userDigitado = edtUsuario.text.toString().trim()
            val passDigitada = edtSenha.text.toString().trim()
            val querLembrar = chkLembrar.isChecked

            if (userDigitado.isNotEmpty() && passDigitada.isNotEmpty()) {
                esconderFormulario()
                fazerLogin(userDigitado, passDigitada, querLembrar)
            } else {
                Toast.makeText(this, "Preencha o Usuário e a Senha!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun esconderFormulario() {
        edtUsuario.visibility = View.GONE
        edtSenha.visibility = View.GONE
        btnEntrar.visibility = View.GONE
        chkLembrar.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
    }

    private fun fazerLogin(username: String, passDigitada: String, salvarLogin: Boolean) {
        // =========================================================
        // ETAPA 1: Vai na tabela "usuarios" e valida o acesso
        // =========================================================
        db.collection("usuarios")
            .whereEqualTo("usuario", username)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val userDoc = snapshot.documents[0]
                    val senhaBanco = userDoc.getString("senha") ?: ""
                    
                    if (passDigitada == senhaBanco) {
                        
                        val servidorId = userDoc.getString("servidor_id") ?: ""
                        
                        if (servidorId.isNotEmpty()) {
                            // =========================================================
                            // ETAPA 2: Vai na tabela "servidores" buscar URL e Xtream
                            // =========================================================
                            db.collection("servidores")
                                .document(servidorId)
                                .get()
                                .addOnSuccessListener { serverDoc ->
                                    if (serverDoc.exists()) {
                                        
                                        val rawUrl = serverDoc.getString("url") ?: ""
                                        var xtreamUrl = rawUrl.trim()
                                        
                                        // Blindagem para não deixar o link dar erro no OkHttp
                                        if (xtreamUrl.isNotEmpty() && !xtreamUrl.startsWith("http")) {
                                            xtreamUrl = "http://$xtreamUrl"
                                        }
                                        if (xtreamUrl.endsWith("/")) {
                                            xtreamUrl = xtreamUrl.dropLast(1)
                                        }

                                        val xtreamUser = serverDoc.getString("xtream_user") ?: ""
                                        val xtreamPass = serverDoc.getString("xtream_pass") ?: ""

                                        // Salva na memória do Android
                                        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
                                        if (salvarLogin) {
                                            prefs.edit()
                                                .putString("USERNAME", username)
                                                .putString("PASSWORD", passDigitada)
                                                .putBoolean("LEMBRAR", true)
                                                .apply()
                                        } else {
                                            prefs.edit().clear().apply()
                                        }

                                        // Inicia a HomeActivity com tudo certinho!
                                        val intent = Intent(this, HomeActivity::class.java)
                                        intent.putExtra("URL", xtreamUrl)
                                        intent.putExtra("USER", xtreamUser)
                                        intent.putExtra("PASS", xtreamPass)
                                        intent.putExtra("USERNAME", username)
                                        startActivity(intent)
                                        finish()

                                    } else {
                                        falhaLogin("O Servidor vinculado não foi encontrado.")
                                    }
                                }
                                .addOnFailureListener {
                                    falhaLogin("Erro ao buscar dados do Servidor.")
                                }
                        } else {
                            falhaLogin("Nenhum servidor vinculado a este usuário.")
                        }
                    } else {
                        falhaLogin("Senha incorreta.")
                    }
                } else {
                    falhaLogin("Usuário não encontrado.")
                }
            }
            .addOnFailureListener {
                falhaLogin("Erro ao conectar no banco de dados.")
            }
    }

    private fun falhaLogin(mensagem: String) {
        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        
        progressBar.visibility = View.GONE
        edtUsuario.visibility = View.VISIBLE
        edtSenha.visibility = View.VISIBLE
        btnEntrar.visibility = View.VISIBLE
        chkLembrar.visibility = View.VISIBLE
        
        Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show()
    }
}
