package com.signalplay.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : Activity() {

    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = FirebaseFirestore.getInstance()

        val inputUsuario = findViewById<EditText>(R.id.emailInput)
        val inputSenha = findViewById<EditText>(R.id.passwordInput)
        val btnEntrar = findViewById<Button>(R.id.loginButton)

        btnEntrar.setOnClickListener {
            val usuario = inputUsuario.text.toString().trim()
            val senha = inputSenha.text.toString().trim()

            if (usuario.isEmpty() || senha.isEmpty()) {
                Toast.makeText(this@MainActivity, "Preencha usuário e senha!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            btnEntrar.isEnabled = false
            btnEntrar.text = "Validando Acesso..."

            db.collection("usuarios")
                .whereEqualTo("usuario", usuario)
                .whereEqualTo("senha", senha)
                .get()
                .addOnSuccessListener { documentos ->
                    if (documentos.isEmpty) {
                        Toast.makeText(this@MainActivity, "Usuário ou senha inválidos!", Toast.LENGTH_LONG).show()
                        btnEntrar.isEnabled = true
                        btnEntrar.text = "Entrar"
                    } else {
                        val documento = documentos.documents[0]
                        val status = documento.getString("status")

                        if (status == "ativo" || status == "teste") {
                            // SUCESSO! Abre a nova tela Home
                            Toast.makeText(this@MainActivity, "Acesso Liberado!", Toast.LENGTH_SHORT).show()
                            
                            val intent = Intent(this@MainActivity, HomeActivity::class.java)
                            startActivity(intent)
                            
                            // Fecha a tela de login para o usuário não voltar pra ela ao apertar "Voltar"
                            finish()
                            
                        } else {
                            Toast.makeText(this@MainActivity, "Acesso Suspenso: Conta expirada ou bloqueada!", Toast.LENGTH_LONG).show()
                            btnEntrar.isEnabled = true
                            btnEntrar.text = "Entrar"
                        }
                    }
                }
                .addOnFailureListener { excecao ->
                    Toast.makeText(this@MainActivity, "Erro ao conectar. Verifique sua internet.", Toast.LENGTH_LONG).show()
                    btnEntrar.isEnabled = true
                    btnEntrar.text = "Entrar"
                }
        }
    }
}
