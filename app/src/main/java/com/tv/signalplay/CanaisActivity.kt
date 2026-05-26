package com.tv.signalplay

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
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

    private lateinit var recyclerCanais: RecyclerView
    private lateinit var sidebarCategorias: LinearLayout
    private var todosCanais: List<XtreamLive> = listOf()

    // A exata ordem do seu App Mobile
    private val ordemFixaMobile = listOf("Jogos de Hoje", "Casa do Patrão", "Canais | Abertos", "Canais | Notícias", "Canais | Globo", "Canais | SBT", "Canais | RecordTV", "Canais | Band", "Canais | Esportes", "Canais | Premiere", "Canais | ESPN", "Canais | SporTV", "Canais | Prime Video", "Canais | Brasileirão", "Canais | MAX", "Canais | DAZN", "Canais | UFC Fight Pass", "Canais | Paramount+", "Canais | Disney+", "Canais | Estaduais", "Canais | Futsal", "Canais | NBA League Pass", "Canais | Legendados", "Canais | Documentários", "Canais | Filmes e Séries", "Canais | Telecine", "Canais | HBO", "Canais | TNT", "Canais | Variedades", "Canais | Religiosos", "Canais | Infantil", "Canais | Diversos", "Canais | Pluto TV", "Canais | Dual Áudio", "Canais | 24h Infantil", "Canais | 24h Variados", "Canais | Cine Bit", "Canais | Adultos", "Canais | HachuTV Adultos", "Canais | Adultos [4K]", "Canais | Dormir e Relaxar", "Vídeos Educativos", "Treinos, Aulas e Receitas", "Câmeras", "Rádios", "Shows", "Outros", "Canais | COMÉDIA")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_canais)

        recyclerCanais = findViewById(R.id.gridCanais)
        sidebarCategorias = findViewById(R.id.sidebarCategorias)
        recyclerCanais.layoutManager = GridLayoutManager(this, 4) // 4 colunas para caber com a sidebar

        val xtreamUser = intent.getStringExtra("XTREAM_USER") ?: ""
        val xtreamPass = intent.getStringExtra("XTREAM_PASS") ?: ""
        val urlDoServidor = intent.getStringExtra("URL") ?: ""

        if (urlDoServidor.isNotEmpty() && xtreamUser.isNotEmpty()) {
            Toast.makeText(this, "Organizando pastas...", Toast.LENGTH_SHORT).show()
            baixarCanaisEOrganizar(urlDoServidor, xtreamUser, xtreamPass)
        }
    }

    private fun baixarCanaisEOrganizar(url: String, user: String, pass: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val api = XtreamClient.create(url)
                val response = api.getLiveStreams(user, pass)

                withContext(Dispatchers.Main) {
                    if (response.isJsonArray) {
                        val tipoLista = object : TypeToken<List<XtreamLive>>() {}.type
                        todosCanais = Gson().fromJson(response, tipoLista)

                        if (todosCanais.isNotEmpty()) {
                            montarMenuLateral()
                        } else {
                            Toast.makeText(this@CanaisActivity, "Nenhum canal encontrado.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(this@CanaisActivity, "Falha na conexão", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun montarMenuLateral() {
        val canaisAgrupados = todosCanais.groupBy { it.category_name ?: "Outros" }
        
        // Ordena as chaves usando a sua lista VIP
        val chavesOrdenadas = canaisAgrupados.keys.sortedBy { cat ->
            val idx = ordemFixaMobile.indexOf(cat)
            if (idx == -1) 9999 else idx
        }

        sidebarCategorias.removeAllViews()

        var primeiroBotao: TextView? = null

        for (categoria in chavesOrdenadas) {
            val btnCat = TextView(this)
            btnCat.text = categoria
            btnCat.textSize = 14f
            btnCat.setTextColor(Color.parseColor("#8e8e93")) // Cor inativa (Cinza)
            btnCat.setPadding(20, 20, 20, 20)
            btnCat.isFocusable = true
            btnCat.isFocusableInTouchMode = true
            btnCat.setBackgroundResource(R.drawable.bg_card_premium_normal) // Reutiliza o shape invisível

            val layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            layoutParams.setMargins(0, 0, 0, 10)
            btnCat.layoutParams = layoutParams

            // Efeito de Foco (Fica Branco e Amarelo igual mobile)
            btnCat.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start()
                    (v as TextView).setTextColor(Color.parseColor("#ffcc00")) // Fica Amarelo
                    // Quando passa pela categoria, já carrega a grade do lado direito automaticamente!
                    carregarGradeDeCanais(canaisAgrupados[categoria] ?: emptyList())
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                    (v as TextView).setTextColor(Color.parseColor("#8e8e93")) // Volta pro cinza
                }
            }

            if (primeiroBotao == null) primeiroBotao = btnCat
            sidebarCategorias.addView(btnCat)
        }

        // Foca na primeira pasta automaticamente
        primeiroBotao?.requestFocus()
    }

    private fun carregarGradeDeCanais(lista: List<XtreamLive>) {
        recyclerCanais.adapter = CanaisAdapter(lista)
    }

    inner class CanaisAdapter(private val listaCanais: List<XtreamLive>) : RecyclerView.Adapter<CanaisAdapter.CanalViewHolder>() {

        inner class CanalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imgLogo: ImageView = itemView.findViewById(R.id.imgLogoCanal)
            val txtNome: TextView = itemView.findViewById(R.id.txtNomeCanal)

            init {
                // FIM DO VERMELHO! Apenas o zoom limpo e elegante
                itemView.setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                    }
                }
                
                // Clique simples para abrir o player (Futuro)
                itemView.setOnClickListener {
                    Toast.makeText(itemView.context, "Abrir: ${listaCanais[bindingAdapterPosition].name}", Toast.LENGTH_SHORT).show()
                }

                // SEGURAR O BOTÃO OK (LONG CLICK) PARA SALVAR FAVORITOS
                itemView.setOnLongClickListener {
                    val canal = listaCanais[bindingAdapterPosition]
                    val prefs = itemView.context.getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
                    val favsJson = prefs.getString("favoritos_tv", "[]")
                    val type = object : TypeToken<MutableList<XtreamLive>>(){}.type
                    val favs: MutableList<XtreamLive> = Gson().fromJson(favsJson, type) ?: mutableListOf()
                    
                    val exists = favs.find { it.stream_id == canal.stream_id }
                    if(exists != null) {
                        favs.remove(exists)
                        Toast.makeText(itemView.context, "❌ Removido dos Favoritos", Toast.LENGTH_SHORT).show()
                    } else {
                        favs.add(canal)
                        Toast.makeText(itemView.context, "⭐ Salvo nos Favoritos!", Toast.LENGTH_SHORT).show()
                    }
                    prefs.edit().putString("favoritos_tv", Gson().toJson(favs)).apply()
                    true // Indica que o long click foi processado
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
            holder.txtNome.setTextColor(Color.WHITE) // Garante que o texto seja branco (não vermelho)
            
            if (!canal.stream_icon.isNullOrEmpty()) {
                Glide.with(holder.itemView.context).load(canal.stream_icon).into(holder.imgLogo)
            } else {
                holder.imgLogo.setImageDrawable(null)
            }
        }

        override fun getItemCount(): Int = listaCanais.size
    }
}
