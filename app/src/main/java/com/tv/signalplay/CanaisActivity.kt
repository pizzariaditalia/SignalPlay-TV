package com.tv.signalplay

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CanaisActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_canais)

        val recyclerCanais = findViewById<RecyclerView>(R.id.gridCanais)
        // Configura uma grade com 5 colunas para a TV
        recyclerCanais.layoutManager = GridLayoutManager(this, 5)

        val usuarioLogado = intent.getStringExtra("USER") ?: ""
        val senhaLogada = intent.getStringExtra("PASS") ?: ""
        val urlDoServidor = intent.getStringExtra("URL") ?: ""

        if (urlDoServidor.isNotEmpty() && usuarioLogado.isNotEmpty() && senhaLogada.isNotEmpty()) {
            Toast.makeText(this, "A carregar canais...", Toast.LENGTH_SHORT).show()
            carregarCanaisAoVivo(urlDoServidor, usuarioLogado, senhaLogada, recyclerCanais)
        } else {
            Toast.makeText(this, "Erro de dados. Tente fazer login novamente.", Toast.LENGTH_LONG).show()
        }
    }

    private fun carregarCanaisAoVivo(url: String, user: String, pass: String, recycler: RecyclerView) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val api = XtreamClient.create(url)
                val response = api.getLiveStreams(user, pass)

                withContext(Dispatchers.Main) {
                    if (response.isJsonArray) {
                        val tipoLista = object : TypeToken<List<XtreamLive>>() {}.type
                        val canais: List<XtreamLive> = Gson().fromJson(response, tipoLista)

                        if (canais.isNotEmpty()) {
                            // Liga a lista de canais à tela usando o nosso Adapter
                            recycler.adapter = CanaisAdapter(canais)
                        } else {
                            Toast.makeText(this@CanaisActivity, "Nenhum canal encontrado.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@CanaisActivity, "Erro no Servidor. Verifique as credenciais.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CanaisActivity, "Falha na conexão: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // O "Motor" que constrói os quadradinhos um por um
    inner class CanaisAdapter(private val listaCanais: List<XtreamLive>) : RecyclerView.Adapter<CanaisAdapter.CanalViewHolder>() {

        inner class CanalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imgLogo: ImageView = itemView.findViewById(R.id.imgLogoCanal)
            val txtNome: TextView = itemView.findViewById(R.id.txtNomeCanal)

            init {
                // Efeito de Foco da TV: Cresce e fica com fundo vermelho ao ser selecionado!
                itemView.setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                        v.setBackgroundColor(Color.parseColor("#E50914")) // Vermelho Netflix
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                        v.setBackgroundColor(Color.parseColor("#1A1A24")) // Cor original
                    }
                }
                
                // Futuro: Clique para abrir o Player de Vídeo
                itemView.setOnClickListener {
                    val canalClicado = listaCanais[bindingAdapterPosition]
                    Toast.makeText(itemView.context, "Abrir canal: ${canalClicado.name}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CanalViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_canal, parent, false)
            return CanalViewHolder(view)
        }

        override fun onBindViewHolder(holder: CanalViewHolder, position: Int) {
            val canal = listaCanais[position]
            holder.txtNome.text = canal.name
            
            // Carrega a logo. Se falhar, deixa em branco.
            if (!canal.stream_icon.isNullOrEmpty()) {
                Glide.with(holder.itemView.context).load(canal.stream_icon).into(holder.imgLogo)
            } else {
                holder.imgLogo.setImageDrawable(null)
            }
        }

        override fun getItemCount(): Int = listaCanais.size
    }
}
