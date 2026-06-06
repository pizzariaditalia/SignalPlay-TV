package com.signalplay.tv

import android.app.Activity
import android.app.AlertDialog
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
    private var serverUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pix)

        imgQrCode = findViewById(R.id.imgQrCode)
        tvPixStatus = findViewById(R.id.tvPixStatus)
        btnPixVoltar = findViewById(R.id.btnPixVoltar)
        db = FirebaseFirestore.getInstance()

        username = intent.getStringExtra("USERNAME") ?: ""
        serverUrl = intent.getStringExtra("SERVER_URL") ?: "" 

        btnPixVoltar.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) v.animate().scaleX(1.05f).scaleY(1.05f).translationZ(10f).setDuration(150).start()
            else v.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(150).start()
        }

        btnPixVoltar.setOnClickListener {
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

    // =========================================================================
    // GERADOR DE PIX - COM DIAGNÓSTICO RAIO-X INCLUÍDO
    // =========================================================================
    private fun gerarCobrancaPix() {
        tvPixStatus.text = "Gerando QR Code..."
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Força a troca do espaço por Underline (_) como você sugeriu que o PHP aceita!
                val usuarioFormatado = username.replace(" ", "_")
                val urlCompleta = "$serverUrl/gerar_pix.php?usuario=$usuarioFormatado"

                val client = OkHttpClient()
                val req = Request.Builder().url(urlCompleta).build()
                val res = client.newCall(req).execute()
                val jsonStrBruto = res.body?.string() ?: ""

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
                                val cleanBase64 = base64Qr.replace("\n", "").replace("\r", "")
                                val decodedString: ByteArray = Base64.decode(cleanBase64, Base64.DEFAULT)
                                val decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                                imgQrCode.setImageBitmap(decodedByte)
                                
                                tvPixStatus.text = "Aguardando pagamento..."
                                tvPixStatus.setTextColor(android.graphics.Color.parseColor("#FFC107")) 
                            } else {
                                tvPixStatus.text = "O servidor não conseguiu gerar a cobrança."
                                tvPixStatus.setTextColor(android.graphics.Color.parseColor("#FF4757")) 
                                exibirDebug("Falha na Resposta", "O PHP até retornou um formato válido, mas o sucesso foi FALSE ou veio sem o Base64.\n\nJSON Lido:\n$jsonLimpo")
                            }
                        } catch (e: Exception) {
                            tvPixStatus.text = "Erro na leitura dos dados do Banco."
                            tvPixStatus.setTextColor(android.graphics.Color.parseColor("#FF4757")) 
                            exibirDebug("Erro no Formato JSON", "O aplicativo não conseguiu traduzir os dados que o seu site enviou.\n\nTexto recebido:\n$jsonLimpo")
                        }
                    } else {
                        tvPixStatus.text = "Resposta inválida do servidor."
                        tvPixStatus.setTextColor(android.graphics.Color.parseColor("#FF4757")) 
                        exibirDebug("Página Vazia ou Erro 404", "O aplicativo chamou o seu PHP, mas não encontrou o bloco { } do JSON na página.\n\nURL Tentada:\n$urlCompleta\n\nTexto Bruto do Servidor:\n$jsonStrBruto")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvPixStatus.text = "Erro de conexão com o servidor."
                    tvPixStatus.setTextColor(android.graphics.Color.parseColor("#FF4757"))
                    exibirDebug("Falha de Rede", "A TV não conseguiu alcançar o site. Pode ser o https ou falta de internet.\n\nErro interno: ${e.message}")
                }
            }
        }
    }

    private fun exibirDebug(titulo: String, mensagem: String) {
        AlertDialog.Builder(this@PixActivity)
            .setTitle("🐛 DIAGNÓSTICO DO PIX: $titulo")
            .setMessage(mensagem)
            .setPositiveButton("FECHAR", null)
            .show()
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
