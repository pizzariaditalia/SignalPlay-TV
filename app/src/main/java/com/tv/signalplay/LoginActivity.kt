package com.tv.signalplay

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : FragmentActivity() {

    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // 1. Iniciando o Firebase com as exatas chaves do seu admin.js
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
        } catch (e: Exception) {
            Toast.makeText(this, "Erro de inicialização do banco", Toast.LENGTH_SHORT).show()
        }

        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val editUser = findViewById<EditText>(R.id.editUser)
        val editPass = findViewById<EditText>(R.id.editPass)

        // Efeito de crescer ao focar com o controle da TV
        btnLogin.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) v.scaleX = 1.05f else v.scaleX = 1.0f
        }

        btnLogin.setOnClickListener {
            val user = editUser.text.toString().trim()
            val pass = editPass.text.toString().trim()

            if (user.isNotEmpty() && pass.isNotEmpty()) {
                btnLogin.text = "VERIFICANDO..."
                btnLogin.isEnabled = false

                // 2. Bate na tabela "usuarios" do seu painel
                db.collection("usuarios")
                    .whereEqualTo("usuario", user)
                    .whereEqualTo("senha", pass)
                    .get()
                    .addOnSuccessListener { documents ->
                        if (!documents.isEmpty) {
                            val cliente = documents.documents[0]
                            val status = cliente.getString("status")

                            if (status == "ativo" || status == "teste") {
                                // NOVO: Pegar o ID do servidor atrelado a este cliente
                                val servidorId = cliente.getString("servidor_id") ?: ""
                                
                                if (servidorId.isNotEmpty()) {
                                    btnLogin.text = "CONECTANDO..."
                                    // 3. Buscar a URL do servidor na tabela 'servidores'
                                    db.collection("servidores").document(servidorId).get()
                                        .addOnSuccessListener { servidorDoc ->
                                            if (servidorDoc.exists()) {
                                                // Assumindo que o campo no seu Firebase se chama "url"
                                                val urlServidor = servidorDoc.getString("url") ?: ""
                                                
                                                Toast.makeText(this, "Acesso Autorizado!", Toast.LENGTH_SHORT).show()
                                                
                                                // 4. Ir para a tela principal carregando os dados na "mochila" (Intent)
                                                val intent = Intent(this, MainActivity::class.java)
                                                intent.putExtra("USER", user)
                                                intent.putExtra("PASS", pass)
                                                intent.putExtra("URL", urlServidor)
                                                startActivity(intent)
                                                
                                                // Fecha a tela de login para o cliente não voltar ao apertar "Voltar" no controle
                                                finish() 
                                            } else {
                                                Toast.makeText(this, "Servidor não encontrado no banco.", Toast.LENGTH_LONG).show()
                                                btnLogin.text = "ENTRAR"
                                                btnLogin.isEnabled = true
                                            }
                                        }
                                        .addOnFailureListener {
                                            Toast.makeText(this, "Erro ao buscar servidor.", Toast.LENGTH_LONG).show()
                                            btnLogin.text = "ENTRAR"
                                            btnLogin.isEnabled = true
                                        }
                                } else {
                                    Toast.makeText(this, "Cliente sem servidor vinculado.", Toast.LENGTH_LONG).show()
                                    btnLogin.text = "ENTRAR"
                                    btnLogin.isEnabled = true
                                }
                            } else {
                                Toast.makeText(this, "Conta suspensa ou inativa.", Toast.LENGTH_LONG).show()
                                btnLogin.text = "ENTRAR"
                                btnLogin.isEnabled = true
                            }
                        } else {
                            Toast.makeText(this, "Credenciais inválidas.", Toast.LENGTH_LONG).show()
                            btnLogin.text = "ENTRAR"
                            btnLogin.isEnabled = true
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Falha de conexão com o banco.", Toast.LENGTH_LONG).show()
                        btnLogin.text = "ENTRAR"
                        btnLogin.isEnabled = true
                    }
            } else {
                Toast.makeText(this, "Preencha os campos", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
