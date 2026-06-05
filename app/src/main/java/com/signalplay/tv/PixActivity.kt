package com.signalplay.tv

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.ImageView
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

class PixActivity : Activity() {

    private lateinit var imgQrCode: ImageView
    private lateinit var tvPixStatus: TextView
    private lateinit var btnPixVoltar: Button
    private lateinit var db: FirebaseFirestore
    private var username = ""
    private var serverUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pix)

        imgQrCode = findViewById(R.id.imgQrCode)
        tvPixStatus = findViewById(R.id.tvPixStatus)
        btnPixVoltar = findViewById(R.id.btnPixVoltar)
        db = FirebaseFirestore.getInstance()

        // Recebe os dados de quem chamou a tela
        username = intent.getStringExtra("USERNAME") ?: ""
        serverUrl = intent.getStringExtra("SERVER_URL") ?: "" // Link de onde estão seus PHPs

        btnPixVoltar.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) v.animate().scaleX(1.05f).scaleY(1.05f).translationZ(10f).setDuration(150).start()
            else v.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(150).start()
        }

        btnPixVoltar.setOnClickListener {
            // Fecha a tela e volta pra onde estava
            finish()
        }

        if (username.isNotEmpty() && serverUrl.isNotEmpty()) {
            gerarCobrancaPix()
            iniciarRadarDePagamento()
        } else {
            tvPixStatus.text = "Erro: Dados do usuário não encontrados."
            tvPixStatus.setTextColor(android.graphics.Color.parseColor("#FF4757"))
        }
    }

    private fun gerarCobrancaPix() {
        tvPixStatus.text = "Gerando QR Code..."
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                // Pede o PIX pro seu servidor PHP
                val req = Request.Builder().url("$serverUrl/gerar_pix.php?usuario=$username").build()
                val res = client.newCall(req).execute()
                val jsonStr = res.body?.string() ?: ""

                withContext(Dispatchers.Main) {
                    if (jsonStr.contains("\"sucesso\":true") || jsonStr.contains("\"sucesso\": true")) {
                        val json = JSONObject(jsonStr)
                        val base64Qr = json.optString("qr_code_base64", "")
                        
                        if (base64Qr.isNotEmpty()) {
                            // Transforma a resposta do PHP em uma imagem na tela!
                            val decodedString: ByteArray = Base64.decode(base64Qr, Base64.DEFAULT)
                            val decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                            imgQrCode.setImageBitmap(decodedByte)
                            
                            tvPixStatus.text = "Aguardando pagamento..."
                            tvPixStatus.setTextColor(android.graphics.Color.parseColor("#FFC107")) // Amarelo
                        }
                    } else {
                        tvPixStatus.text = "Erro ao gerar PIX com o Banco."
                        tvPixStatus.setTextColor(android.graphics.Color.parseColor("#FF4757")) // Vermelho
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvPixStatus.text = "Erro de conexão com o servidor."
                    tvPixStatus.setTextColor(android.graphics.Color.parseColor("#FF4757"))
                }
            }
        }
    }

    private fun iniciarRadarDePagamento() {
        // Fica de olho mágico no Firebase
        db.collection("usuarios").whereEqualTo("usuario", username)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || snapshot.isEmpty) return@addSnapshotListener
                
                val doc = snapshot.documents[0]
                val status = doc.getString("status")?.uppercase() ?: ""
                
                // Se o Webhook lá no servidor PHP mudou o status pra ATIVO, a TV reage na hora!
                if (status == "ATIVO") {
                    tvPixStatus.text = "PAGAMENTO APROVADO!"
                    tvPixStatus.setTextColor(android.graphics.Color.parseColor("#2ED573")) // Verde
                    
                    Toast.makeText(this@PixActivity, "Acesso Renovado! Aproveite.", Toast.LENGTH_LONG).show()
                    
                    // Espera 3 segundos pra pessoa ver a mensagem feliz, e fecha a tela
                    imgQrCode.postDelayed({
                        finish()
                    }, 3000)
                }
            }
    }
}
