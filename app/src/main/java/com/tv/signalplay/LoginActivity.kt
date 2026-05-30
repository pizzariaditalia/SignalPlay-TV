package com.tv.signalplay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LoginActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val inputUser = findViewById<EditText>(R.id.inputUser)
        val inputPass = findViewById<EditText>(R.id.inputPass)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val loginLoading = findViewById<ProgressBar>(R.id.loginLoading)
        
        val btnFalar = findViewById<TextView>(R.id.btnFalarConosco)
        val btnAssinatura = findViewById<TextView>(R.id.btnAssinatura)

        // URL Mestra do seu Servidor (Motor)
        val urlServidor = "http://tv.hdtv1.top:80"

        // Foco dos menus superiores (Fica amarelo ao passar o controle da TV)
        val menuFocusListener = View.OnFocusChangeListener { v, hasFocus ->
            val txt = v as TextView
            if (hasFocus) txt.setTextColor(Color.parseColor("#FFCC00")) else txt.setTextColor(Color.WHITE)
        }
        btnFalar.setOnFocusChangeListener(menuFocusListener)
        btnAssinatura.setOnFocusChangeListener(menuFocusListener)

        btnLogin.setOnClickListener {
            val user = inputUser.text.toString().trim()
            val pass = inputPass.text.toString().trim()

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Preencha usuário e senha", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            loginLoading.visibility = View.VISIBLE

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val api = XtreamClient.create(urlServidor)
                    val response = api.authenticate(user, pass)
                    
                    withContext(Dispatchers.Main) {
                        loginLoading.visibility = View.GONE
                        btnLogin.isEnabled = true
                        
                        if (response.isJsonObject) {
                            val userObj = response.asJsonObject.getAsJsonObject("user_info")
                            if (userObj != null && userObj.has("auth") && userObj.get("auth").asInt == 1) {
                                
                                // Salva as chaves no motor do app
                                val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
                                prefs.edit()
                                    .putString("XTREAM_USER", user)
                                    .putString("XTREAM_PASS", pass)
                                    .putString("URL", urlServidor)
                                    .apply()

                                // Joga para a tela preta vazia
                                val intent = Intent(this@LoginActivity, MainActivity::class.java)
                                startActivity(intent)
                                finish()
                            } else {
                                Toast.makeText(this@LoginActivity, "Usuário ou senha incorretos.", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(this@LoginActivity, "Erro no servidor.", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        loginLoading.visibility = View.GONE
                        btnLogin.isEnabled = true
                        Toast.makeText(this@LoginActivity, "Falha de conexão com o servidor.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
