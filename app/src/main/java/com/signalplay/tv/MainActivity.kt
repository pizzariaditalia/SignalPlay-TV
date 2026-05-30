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

            // Passo 1: Busca o usuário no Firebase
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
                            val servidorId = documento.getString("servidor_id")
                            
                            if (servidorId != null) {
                                // Passo 2: Busca a URL, User e Pass do Xtream na coleção servidores
                                db.collection("servidores").document(servidorId).get()
                                    .addOnSuccessListener { serverDoc ->
                                        if (serverDoc.exists()) {
                                            val url = serverDoc.getString("url") ?: ""
                                            val xtreamUser = serverDoc.getString("xtream_user") ?: ""
                                            val xtreamPass = serverDoc.getString("xtream_pass") ?: ""

                                            Toast.makeText(this@MainActivity, "Conectando ao catálogo...", Toast.LENGTH_SHORT).show()

                                            // Envia os dados secretos do servidor para a tela Home
                                            val intent = Intent(this@MainActivity, HomeActivity::class.java)
                                            intent.putExtra("URL", url)
                                            intent.putExtra("USER", xtreamUser)
                                            intent.putExtra("PASS", xtreamPass)
                                            startActivity(intent)
                                            finish()
                                            
                                        } else {
                                            Toast.makeText(this@MainActivity, "Erro: Servidor não encontrado.", Toast.LENGTH_LONG).show()
                                            btnEntrar.isEnabled = true
                                            btnEntrar.text = "Entrar"
                                        }
                                    }
                            } else {
                                Toast.makeText(this@MainActivity, "Conta sem servidor vinculado.", Toast.LENGTH_LONG).show()
                                btnEntrar.isEnabled = true
                                btnEntrar.text = "Entrar"
                            }
                        } else {
                            Toast.makeText(this@MainActivity, "Acesso Suspenso: Conta expirada ou bloqueada!", Toast.LENGTH_LONG).show()
                            btnEntrar.isEnabled = true
                            btnEntrar.text = "Entrar"
                        }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this@MainActivity, "Erro ao conectar com o banco de dados.", Toast.LENGTH_LONG).show()
                    btnEntrar.isEnabled = true
                    btnEntrar.text = "Entrar"
                }
        }
    }
}
