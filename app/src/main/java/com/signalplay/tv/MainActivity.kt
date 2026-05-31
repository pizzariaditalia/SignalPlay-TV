package com.signalplay.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : Activity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var progressBar: ProgressBar
    private lateinit var btnEntrar: Button
    private lateinit var edtUsuario: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = FirebaseFirestore.getInstance()
        
        edtUsuario = findViewById(R.id.edtUsuario)
        btnEntrar = findViewById(R.id.btnEntrar)
        progressBar = findViewById(R.id.progressBar)

        // 1. VERIFICA O LOGIN AUTOMÁTICO
        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
        val savedUser = prefs.getString("USERNAME", "")

        if (!savedUser.isNullOrEmpty()) {
            // Se já tem usuário salvo, esconde tudo, mostra o loading e faz o login fantasma
            edtUsuario.visibility = View.GONE
            btnEntrar.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
            fazerLogin(savedUser)
        } else {
            // Se não tem, mostra o campo normal
            progressBar.visibility = View.GONE
        }

        // 2. BOTÃO ENTRAR MANUAL
        btnEntrar.setOnClickListener {
            val userDigitado = edtUsuario.text.toString().trim()
            if (userDigitado.isNotEmpty()) {
                btnEntrar.visibility = View.GONE
                progressBar.visibility = View.VISIBLE
                fazerLogin(userDigitado)
            } else {
                Toast.makeText(this, "Digite seu usuário!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fazerLogin(username: String) {
        db.collection("usuarios")
            .whereEqualTo("usuario", username)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    val xtreamUrl = doc.getString("url") ?: ""
                    val xtreamUser = doc.getString("user") ?: ""
                    val xtreamPass = doc.getString("pass") ?: ""

                    // SALVA O USUÁRIO NA MEMÓRIA PARA AS PRÓXIMAS VEZES!
                    val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("USERNAME", username).apply()

                    val intent = Intent(this, HomeActivity::class.java)
                    intent.putExtra("URL", xtreamUrl)
                    intent.putExtra("USER", xtreamUser)
                    intent.putExtra("PASS", xtreamPass)
                    intent.putExtra("USERNAME", username)
                    startActivity(intent)
                    finish()
                } else {
                    falhaLogin("Usuário não encontrado.")
                }
            }
            .addOnFailureListener {
                falhaLogin("Erro ao conectar no banco de dados.")
            }
    }

    private fun falhaLogin(mensagem: String) {
        // Se der erro (ex: usuário deletado), limpa a memória e volta a tela
        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        
        progressBar.visibility = View.GONE
        edtUsuario.visibility = View.VISIBLE
        btnEntrar.visibility = View.VISIBLE
        Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show()
    }
}
