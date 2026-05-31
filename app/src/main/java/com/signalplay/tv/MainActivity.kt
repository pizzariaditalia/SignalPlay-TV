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

        // Animação no botão ao focar
        btnEntrar.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) v.animate().scaleX(1.05f).scaleY(1.05f).translationZ(10f).setDuration(150).start()
            else v.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(150).start()
        }

        // 1. VERIFICA O LOGIN AUTOMÁTICO NA MEMÓRIA DA TV
        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
        val savedUser = prefs.getString("USERNAME", "")
        val savedPass = prefs.getString("PASSWORD", "")
        val lembrar = prefs.getBoolean("LEMBRAR", false)

        if (lembrar && !savedUser.isNullOrEmpty() && !savedPass.isNullOrEmpty()) {
            edtUsuario.visibility = View.GONE
            edtSenha.visibility = View.GONE
            btnEntrar.visibility = View.GONE
            chkLembrar.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
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
                edtUsuario.visibility = View.GONE
                edtSenha.visibility = View.GONE
                btnEntrar.visibility = View.GONE
                chkLembrar.visibility = View.GONE
                progressBar.visibility = View.VISIBLE
                
                fazerLogin(userDigitado, passDigitada, querLembrar)
            } else {
                Toast.makeText(this, "Preencha o Usuário e a Senha!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fazerLogin(username: String, passDigitada: String, salvarLogin: Boolean) {
        db.collection("usuarios")
            .whereEqualTo("usuario", username)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    
                    // CORREÇÃO: Tenta ler o campo 'senha' (App) ou 'pass' (Xtream) do seu Firestore
                    val senhaBanco = doc.getString("senha") ?: ""
                    val xtreamPass = doc.getString("pass") ?: ""
                    
                    // Valida se a senha digitada bate com qualquer uma das duas
                    if (passDigitada == senhaBanco || passDigitada == xtreamPass) {
                        val xtreamUrl = doc.getString("url") ?: ""
                        val xtreamUser = doc.getString("user") ?: ""

                        // SALVA NA MEMÓRIA SE ESTIVER MARCADO
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

                        val intent = Intent(this, HomeActivity::class.java)
                        intent.putExtra("URL", xtreamUrl)
                        intent.putExtra("USER", xtreamUser)
                        intent.putExtra("PASS", xtreamPass) // Manda a senha do Xtream pro Player
                        intent.putExtra("USERNAME", username)
                        startActivity(intent)
                        finish()
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
