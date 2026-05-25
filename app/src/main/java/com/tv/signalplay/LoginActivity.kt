package com.tv.signalplay

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.FragmentActivity

class LoginActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val editUser = findViewById<EditText>(R.id.editUser)
        val editPass = findViewById<EditText>(R.id.editPass)

        // Estilo de foco para TV (fica maior quando selecionado)
        btnLogin.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) v.scaleX = 1.05f else v.scaleX = 1.0f
        }

        btnLogin.setOnClickListener {
            val user = editUser.text.toString()
            val pass = editPass.text.toString()
            
            if (user.isNotEmpty() && pass.isNotEmpty()) {
                Toast.makeText(this, "Autenticando...", Toast.LENGTH_SHORT).show()
                // Aqui no próximo passo chamaremos a XtreamApi!
            } else {
                Toast.makeText(this, "Preencha os dados", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
