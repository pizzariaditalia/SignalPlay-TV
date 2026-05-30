package com.signalplay.tv

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : Activity() {

    // Prepara a variável do banco de dados
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Instancia o Firestore (já configurado pelo google-services.json)
        db = FirebaseFirestore.getInstance()

        // Mapeia os elementos visuais que desenhamos no XML
        val inputUsuario = findViewById<EditText>(R.id.emailInput)
        val inputSenha = findViewById<EditText>(R.id.passwordInput)
        val btnEntrar = findViewById<Button>(R.id.loginButton)

        // Ação de clique no botão Entrar
        btnEntrar.setOnClickListener {
            val usuario = inputUsuario.text.toString().trim()
            val senha = inputSenha.text.toString().trim()

            // 1. Validação básica de segurança (campos vazios)
            if (usuario.isEmpty() || senha.isEmpty()) {
                Toast.makeText(this@MainActivity, "Preencha usuário e senha!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // 2. Trava o botão para o cliente não clicar várias vezes enquanto carrega
            btnEntrar.isEnabled = false
            btnEntrar.text = "Validando Acesso..."

            // 3. Busca no Firebase (Coleção 'usuarios')
            db.collection("usuarios")
                .whereEqualTo("usuario", usuario)
                .whereEqualTo("senha", senha)
                .get()
                .addOnSuccessListener { documentos ->
                    if (documentos.isEmpty) {
                        // O Firebase não achou essa combinação
                        Toast.makeText(this@MainActivity, "Usuário ou senha inválidos!", Toast.LENGTH_LONG).show()
                        btnEntrar.isEnabled = true
                        btnEntrar.text = "Entrar"
                    } else {
                        // O Firebase encontrou o usuário!
                        val documento = documentos.documents[0]
                        val status = documento.getString("status")

                        // 4. Verifica se a conta não está bloqueada/vencida
                        if (status == "ativo" || status == "teste") {
                            val servidorId = documento.getString("servidor_id")
                            
                            // Por enquanto, exibe um aviso de sucesso na tela.
                            // No próximo passo, vamos substituir esse aviso pelo código que abre a tela do catálogo.
                            Toast.makeText(this@MainActivity, "Acesso Liberado! Conectando ao servidor...", Toast.LENGTH_LONG).show()
                            
                        } else {
                            // Conta bloqueada
                            Toast.makeText(this@MainActivity, "Acesso Suspenso: Conta expirada ou bloqueada!", Toast.LENGTH_LONG).show()
                            btnEntrar.isEnabled = true
                            btnEntrar.text = "Entrar"
                        }
                    }
                }
                .addOnFailureListener { excecao ->
                    // Tratamento caso a TV esteja sem internet, por exemplo
                    Toast.makeText(this@MainActivity, "Erro de conexão. Verifique sua internet.", Toast.LENGTH_LONG).show()
                    btnEntrar.isEnabled = true
                    btnEntrar.text = "Entrar"
                }
        }
    }
}
