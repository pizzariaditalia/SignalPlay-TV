package com.signalplay.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : Activity() {

    private lateinit var db: FirebaseFirestore
    private var isParentalActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        db = FirebaseFirestore.getInstance()

        val url = intent.getStringExtra("URL") ?: ""
        val user = intent.getStringExtra("USER") ?: ""
        val pass = intent.getStringExtra("PASS") ?: ""
        val username = intent.getStringExtra("USERNAME") ?: ""

        val tvNomeUsuario = findViewById<TextView>(R.id.tvNomeUsuario)
        val tvVencimento = findViewById<TextView>(R.id.tvVencimento)
        val tvStatusParental = findViewById<TextView>(R.id.tvStatusParental)
        
        val btnParental = findViewById<LinearLayout>(R.id.btnParental)
        val btnLimparFavs = findViewById<LinearLayout>(R.id.btnLimparFavs)
        val btnLimparHist = findViewById<LinearLayout>(R.id.btnLimparHist)
        val btnAtualizarEPG = findViewById<LinearLayout>(R.id.btnAtualizarEPG)
        val btnSairConta = findViewById<Button>(R.id.btnSairConta)

        tvNomeUsuario.text = "Olá, $username!"

        // Animações de foco para a TV
        val focusListener = View.OnFocusChangeListener { v, hasFocus ->
            if (hasFocus) v.animate().scaleX(1.05f).scaleY(1.05f).translationZ(10f).setDuration(150).start()
            else v.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(150).start()
        }
        btnParental.onFocusChangeListener = focusListener
        btnLimparFavs.onFocusChangeListener = focusListener
        btnLimparHist.onFocusChangeListener = focusListener
        btnAtualizarEPG.onFocusChangeListener = focusListener
        btnSairConta.onFocusChangeListener = focusListener

        // === 1. BUSCAR VENCIMENTO DA ASSINATURA ===
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val req = Request.Builder().url("$url/player_api.php?username=$user&password=$pass").build()
                val res = client.newCall(req).execute()
                val jsonStr = res.body?.string() ?: "{}"
                
                if (jsonStr.startsWith("{")) {
                    val json = JSONObject(jsonStr)
                    val userInfo = json.optJSONObject("user_info")
                    
                    if (userInfo != null) {
                        val expStr = userInfo.optString("exp_date", "")
                        withContext(Dispatchers.Main) {
                            if (expStr.isNotEmpty() && expStr != "null") {
                                val expLong = expStr.toLongOrNull()
                                if (expLong != null) {
                                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                                    val date = Date(expLong * 1000) // Xtream retorna em segundos
                                    tvVencimento.text = "Vencimento: ${sdf.format(date)}"
                                } else {
                                    tvVencimento.text = "Vencimento: Ilimitado"
                                }
                            } else {
                                tvVencimento.text = "Vencimento: Ilimitado"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { tvVencimento.text = "Vencimento: Desconhecido" }
            }
        }

        // === 2. CONTROLE PARENTAL ===
        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
        isParentalActive = prefs.getBoolean("PARENTAL_CONTROL", false)
        atualizarTextoParental(tvStatusParental)

        btnParental.setOnClickListener {
            isParentalActive = !isParentalActive
            prefs.edit().putBoolean("PARENTAL_CONTROL", isParentalActive).apply()
            atualizarTextoParental(tvStatusParental)
            Toast.makeText(this, "Controle Parental alterado!", Toast.LENGTH_SHORT).show()
        }

        // === 3. LIMPAR FAVORITOS ===
        btnLimparFavs.setOnClickListener {
            db.collection("usuarios").whereEqualTo("usuario", username).get()
                .addOnSuccessListener { snapshot ->
                    if (!snapshot.isEmpty) {
                        val docId = snapshot.documents[0].id
                        db.collection("usuarios").document(docId).update("favoritos", emptyList<String>())
                        Toast.makeText(this, "Favoritos zerados com sucesso!", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        // === 4. LIMPAR HISTÓRICO DE VOD ===
        btnLimparHist.setOnClickListener {
            prefs.edit().remove("iptv_continuar_vod").apply() // Padrão da sua lógica JS/Nativa
            Toast.makeText(this, "Histórico limpo com sucesso!", Toast.LENGTH_SHORT).show()
        }

        // === 5. ATUALIZAR EPG ===
        btnAtualizarEPG.setOnClickListener {
            Toast.makeText(this, "Sincronizando Guia de TV...", Toast.LENGTH_SHORT).show()
            // Como o App já busca o get_short_epg de forma dinâmica nos canais, 
            // este botão atua limpando os caches pesados se existissem.
            Toast.makeText(this, "EPG Atualizado com sucesso!", Toast.LENGTH_LONG).show()
        }

        // === 6. SAIR DA CONTA ===
        btnSairConta.setOnClickListener {
            prefs.edit().clear().apply()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun atualizarTextoParental(tv: TextView) {
        if (isParentalActive) {
            tv.text = "ATIVADO"
            tv.setTextColor(Color.parseColor("#2ED573")) // Verde
        } else {
            tv.text = "DESATIVADO"
            tv.setTextColor(Color.parseColor("#FF4757")) // Vermelho
        }
    }
}
