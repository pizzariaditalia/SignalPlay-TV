package com.tv.signalplay

import android.content.Context
import android.content.Intent
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

    private var xtUser = ""
    private var xtPass = ""
    private var urlServ = ""
    private var isParentalOn = false

    // A ordem oficial do seu JS
    private val ordemFixaMobile = listOf("Jogos de Hoje", "Casa do Patrão", "Canais | Abertos", "Canais | Notícias", "Canais | Globo", "Canais | SBT", "Canais | RecordTV", "Canais | Band", "Canais | Esportes", "Canais | Premiere", "Canais | ESPN", "Canais | SporTV", "Canais | Prime Video", "Canais | Brasileirão", "Canais | MAX", "Canais | DAZN", "Canais | UFC Fight Pass", "Canais | Paramount+", "Canais | Disney+", "Canais | Estaduais", "Canais | Futsal", "Canais | NBA League Pass", "Canais | Legendados", "Canais | Documentários", "Canais | Filmes e Séries", "Canais | Telecine", "Canais | HBO", "Canais | TNT", "Canais | Variedades", "Canais | Religiosos", "Canais | Infantil", "Canais | Diversos", "Canais | Pluto TV", "Canais | Dual Áudio", "Canais | 24h Infantil", "Canais | 24h Variados", "Canais | Cine Bit", "Canais | Adultos", "Canais | HachuTV Adultos", "Canais | Adultos [4K]", "Canais | Dormir e Relaxar", "Vídeos Educativos", "Treinos, Aulas e Receitas", "Câmeras", "Rádios", "Shows", "Outros", "Canais | COMÉDIA")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_canais)

        recyclerCanais = findViewById(R.id.gridCanais)
        sidebarCategorias = findViewById(R.id.sidebarCategorias)
        
        // 5 colunas para aproveitar bem o ecrã grande da TV
        recyclerCanais.layoutManager = GridLayoutManager(this, 5) 

        xtUser = intent.getStringExtra("XTREAM_USER") ?: ""
        xtPass = intent.getStringExtra("XTREAM_PASS") ?: ""
        urlServ = intent.getStringExtra("URL") ?: ""
        
        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
        isParentalOn = prefs.getBoolean("parental_control", false)

        if (urlServ.isNotEmpty() && xtUser.isNotEmpty()) {
            baixarCanaisEOrganizar(urlServ, xtUser, xtPass)
        }
    }

    private fun isAdult(cat: String?): Boolean {
        if (!isParentalOn) return false
        val c = cat?.lowercase() ?: ""
        return listOf("adulto", "adult", "18+", "xxx", "porn", "sensual", "hachutv").any { c.contains(it) }
    }

    private fun baixarCanaisEOrganizar(url: String, user: String, pass: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val api = XtreamClient.create(url)
                val respCat = api.getLiveCategories(user, pass)
                val respCanais = api.getLiveStreams(user, pass)

                withContext(Dispatchers.Main) {
                    val mapCategorias = mutableMapOf<String, String>()
                    
                    if (respCat.isJsonArray) {
                        val typeCat = object : TypeToken<List<XtreamCategory>>() {}.type
                        val categorias: List<XtreamCategory> = Gson().fromJson(respCat, typeCat)
                        categorias.forEach { mapCategorias[it.category_id] = it.category_name }
                    }

                    if (respCanais.isJsonArray) {
                        val tipoLista = object : TypeToken<List<XtreamLive>>() {}.type
                        val canaisBrutos: List<XtreamLive> = Gson().fromJson(respCanais, tipoLista)
                        
                        // Associa o nome da pasta a cada canal
                        canaisBrutos.forEach { canal ->
                            canal.category_name = mapCategorias[canal.category_id ?: ""] ?: "Outros"
                        }

                        // Filtra o conteúdo +18 se o Controlo Parental estiver LIGADO
                        todosCanais = canaisBrutos.filter { !isAdult(it.category_name) }
                        
                        if (todosCanais.isNotEmpty()) montarMenuLateral()
                    }
                }
            } catch (e: Exception) { }
        }
    }

    private fun montarMenuLateral() {
        val canaisAgrupados = todosCanais.groupBy { it.category_name ?: "Outros" }
        val chavesOrdenadas = canaisAgrupados.keys.sortedBy { cat ->
            val idx = ordemFixaMobile.indexOf(cat)
            if (idx == -1) 9999 else idx
        }

        sidebarCategorias.removeAllViews()
        var primeiroBotao: TextView? = null

        for (categoria in chavesOrdenadas) {
            val btnCat = TextView(this)
            btnCat.text = categoria.uppercase()
            btnCat.textSize = 14f
            btnCat.setTextColor(Color.parseColor("#f5f5f7"))
            btnCat.setPadding(20, 25, 20, 25)
            btnCat.isFocusable = true
            btnCat.isFocusableInTouchMode = true
            btnCat.setBackgroundResource(R.drawable.bg_cat_btn) // O novo Drawable que criámos!

            val layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            layoutParams.setMargins(0, 0, 0, 15)
            btnCat.layoutParams = layoutParams

            // Efeito TV: Foca e carrega a grelha de canais automaticamente
            btnCat.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start()
                    (v as TextView).setTextColor(Color.parseColor("#ffcc00")) 
                    carregarGradeDeCanais(canaisAgrupados[categoria] ?: emptyList())
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                    (v as TextView).setTextColor(Color.parseColor("#f5f5f7"))
                }
            }
            if (primeiroBotao == null) primeiroBotao = btnCat
            sidebarCategorias.addView(btnCat)
        }
        
        primeiroBotao?.requestFocus() // Dá o foco na primeira categoria ao abrir a tela
    }

    private fun carregarGradeDeCanais(lista: List<XtreamLive>) {
        recyclerCanais.adapter = CanaisAdapter(lista)
    }

    inner class CanaisAdapter(private val listaCanais: List<XtreamLive>) : RecyclerView.Adapter<CanaisAdapter.CanalViewHolder>() {
        inner class CanalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imgLogo: ImageView = itemView.findViewById(R.id.imgCapaPremium)
            val txtNome: TextView = itemView.findViewById(R.id.txtNomePremium)

            init {
                itemView.setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                        v.elevation = 10f
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                        v.elevation = 0f
                    }
                }
                
                // Clique simples para abrir o Reprodutor
                itemView.setOnClickListener {
                    val canal = listaCanais[bindingAdapterPosition]
                    val intentPlayer = Intent(itemView.context, PlayerActivity::class.java)
                    intentPlayer.putExtra("URL", urlServ)
                    intentPlayer.putExtra("XTREAM_USER", xtUser)
                    intentPlayer.putExtra("XTREAM_PASS", xtPass)
                    intentPlayer.putExtra("STREAM_ID", canal.stream_id)
                    intentPlayer.putExtra("TYPE", "live")
                    intentPlayer.putExtra("TITLE", canal.name)
                    itemView.context.startActivity(intentPlayer)
                }

                // Segurar botão OK para Favoritos (SEM EMOJIS, Textos Profissionais)
                itemView.setOnLongClickListener {
                    val canal = listaCanais[bindingAdapterPosition]
                    val prefs = itemView.context.getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
                    val favsJson = prefs.getString("favoritos_tv", "[]")
                    val type = object : TypeToken<MutableList<String>>(){}.type
                    
                    val favs: MutableList<String> = try {
                        Gson().fromJson(favsJson, type) ?: mutableListOf()
                    } catch (e: Exception) {
                        prefs.edit().putString("favoritos_tv", "[]").apply()
                        mutableListOf()
                    }
                    
                    val stringId = canal.stream_id.toString()
                    if(favs.contains(stringId)) {
                        favs.remove(stringId)
                        Toast.makeText(itemView.context, "Canal removido dos favoritos", Toast.LENGTH_SHORT).show()
                    } else {
                        favs.add(stringId)
                        Toast.makeText(itemView.context, "Canal guardado nos favoritos", Toast.LENGTH_SHORT).show()
                    }
                    prefs.edit().putString("favoritos_tv", Gson().toJson(favs)).apply()
                    true
                }
            }
        }

        // Reutiliza o molde de Canal que já construímos com Fundo Branco na Etapa Anterior
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CanalViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.card_canal_premium, parent, false)
            return CanalViewHolder(view)
        }

        override fun onBindViewHolder(holder: CanalViewHolder, position: Int) {
            val canal = listaCanais[position]
            holder.txtNome.text = canal.name
            holder.txtNome.setTextColor(Color.WHITE) 
            
            if (!canal.stream_icon.isNullOrEmpty()) {
                Glide.with(holder.itemView.context).load(canal.stream_icon).into(holder.imgLogo)
            } else {
                holder.imgLogo.setImageDrawable(null)
            }
        }
        override fun getItemCount(): Int = listaCanais.size
    }
}
