package com.signalplay.tv

import android.app.Activity
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PixActivity : Activity() {

    private lateinit var imgQrCode: ImageView
    private lateinit var tvPixStatus: TextView
    private lateinit var btnPixVoltar: Button
    private lateinit var db: FirebaseFirestore
    private var username = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pix)

        imgQrCode = findViewById(R.id.imgQrCode)
        tvPixStatus = findViewById(R.id.tvPixStatus)
        btnPixVoltar = findViewById(R.id.btnPixVoltar)
        db = FirebaseFirestore.getInstance()

        // Recebe o nome do utilizador vindo da tela de login
        username = intent.getStringExtra("USERNAME") ?: ""

        btnPixVoltar.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) v.animate().scaleX(1.05f).scaleY(1.05f).translationZ(10f).setDuration(150).start()
            else v.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(150).start()
        }

        btnPixVoltar.setOnClickListener {
            finish()
        }

        if (username.isNotEmpty()) {
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
                // Substitui espaços por underline (_) conforme exigido pela sua API PHP
                val usuarioFormatado = username.replace(" ", "_")
                
                // CORREÇÃO DEFINITIVA DA URL: Agora aponta diretamente para o seu site estável
                val urlCompleta = "http://signalplay.pro/gerar_pix.php?usuario=$usuarioFormatado"

                val client = OkHttpClient()
                val req = Request.Builder().url(urlCompleta).build()
                val res = client.newCall(req).execute()
                val jsonStrBruto = res.body?.string() ?: ""

                // Limpa quaisquer impurezas ou textos extras fora do objeto JSON
                val startIndex = jsonStrBruto.indexOf("{")
                val endIndex = jsonStrBruto.lastIndexOf("}")

                withContext(Dispatchers.Main) {
                    if (startIndex != -1 && endIndex != -1 && startIndex <= endIndex) {
                        val jsonLimpo = jsonStrBruto.substring(startIndex, endIndex + 1)
                        
                        try {
                            val json = JSONObject(jsonLimpo)
                            val sucesso = json.optBoolean("sucesso", false)
                            val base64Qr = json.optString("qr_code_base64", "")
                            
                            if (sucesso && base64Qr.isNotEmpty()) {
                                // Remove quebras de linha acidentais do Base64
                                val cleanBase64 = base64Qr.replace("\n", "").replace("\r", "")
                                
                                val decodedString: ByteArray = Base64.decode(cleanBase64, Base64.DEFAULT)
                                val decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                                imgQrCode.setImageBitmap(decodedByte)
                                
                                tvPixStatus.text = "Aguardando pagamento..."
                                tvPixStatus.setTextColor(android.graphics.Color.parseColor("#FFC107")) 
                            } else {
                                tvPixStatus.text = "O servidor não conseguiu gerar a cobrança."
                                tvPixStatus.setTextColor(android.graphics.Color.parseColor("#FF4757")) 
                            }
                        } catch (e: Exception) {
                            tvPixStatus.text = "Erro na leitura dos dados do Banco."
                            tvPixStatus.setTextColor(android.graphics.Color.parseColor("#FF4757")) 
                        }
                    } else {
                        tvPixStatus.text = "Resposta inválida do servidor."
                        tvPixStatus.setTextColor(android.graphics.Color.parseColor("#FF4757")) 
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
        db.collection("usuarios").whereEqualTo("usuario", username)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || snapshot.isEmpty) return@addSnapshotListener
                
                val doc = snapshot.documents[0]
                val status = doc.getString("status")?.uppercase() ?: ""
                
                var isVencido = false
                val vencObj = doc.get("vencimento")

                if (vencObj != null) {
                    try {
                        var dataVencimento: Date? = null

                        if (vencObj is com.google.firebase.Timestamp) {
                            dataVencimento = vencObj.toDate()
                        } else {
                            val strData = vencObj.toString().trim()
                            if (strData.lowercase() != "ilimitado" && strData.isNotEmpty()) {
                                dataVencimento = try {
                                    if (strData.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(strData)
                                    } else {
                                        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(strData.replace("-", "/"))
                                    }
                                } catch (e: Exception) { null }
                            }
                        }

                        if (dataVencimento != null) {
                            val dataAtual = Date()
                            if (dataAtual.after(dataVencimento)) {
                                isVencido = true
                            }
                        }
                    } catch (e: Exception) {
                        isVencido = true 
                    }
                }

                // O Radar só liberta o utilizador se o status for ATIVO e não estiver com data vencida
                if (status == "ATIVO" && !isVencido) {
                    tvPixStatus.text = "PAGAMENTO APROVADO!"
                    tvPixStatus.setTextColor(android.graphics.Color.parseColor("#2ED573")) 
                    
                    Toast.makeText(this@PixActivity, "Acesso Renovado! Aproveite.", Toast.LENGTH_LONG).show()
                    
                    imgQrCode.postDelayed({
                        finish()
                    }, 3000)
                }
            }
    }
}
