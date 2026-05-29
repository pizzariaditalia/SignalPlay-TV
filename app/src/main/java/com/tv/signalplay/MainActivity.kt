package com.tv.signalplay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ItemCatalogo(
    val id: Int,
    val titulo: String,
    val capa: String?,
    val tipo: String, 
    val category_name: String = "Outros", 
    val isTop10: Boolean = false,
    val rankIndex: Int = 0,
    val tempoAtual: Long = 0L,
    val duracaoTotal: Long = 0L
)

class MainActivity : FragmentActivity() {

    private var masterFilmes: List<XtreamVod> = listOf()
    private var masterSeries: List<XtreamSerie> = listOf()
    private var masterCanais: List<XtreamLive> = listOf()
    
    private var xtUser = ""
    private var xtPass = ""
    private var urlServ = ""
    private var firebaseUser = ""
    private var isParentalOn = false
    
    private lateinit var loadingOverlay: RelativeLayout
    private lateinit var mainScrollView: ScrollView
    private lateinit var glideOptions: RequestOptions

    override fun onCreate(savedInstanceState: Bundle?) {
        window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadingOverlay = findViewById(R.id.loadingOverlay)
        mainScrollView = findViewById(R.id.mainScrollView)

        // OTIMIZAÇÃO DE MEMÓRIA: Configuração de redimensionamento e cache forçado de capas
        glideOptions = RequestOptions()
            .override(140, 210) // Reduz fisicamente o tamanho do arquivo na RAM da TV
            .diskCacheStrategy(DiskCacheStrategy.ALL) // Grava permanentemente no armazenamento
            .centerCrop()

        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setProjectId("signalplay-tv")
                    .setApplicationId("1:51000338902:web:61d77a44dd62c0353a1c77")
                    .setApiKey("AIzaSyBSYJYEFLlDwBYsQC0I76n9NfAph2oWuLI")
                    .build()
                FirebaseApp.initializeApp(this, options)
            }
        } catch (e: Exception) {}

        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
        firebaseUser = intent.getStringExtra("FIREBASE_USER") ?: prefs.getString("FIREBASE_USER", "Cliente") ?: "Cliente"
        xtUser = intent.getStringExtra("XTREAM_USER") ?: prefs.getString("XTREAM_USER", "") ?: ""
        xtPass = intent.getStringExtra("XTREAM_PASS") ?: prefs.getString("XTREAM_PASS", "") ?: ""
        urlServ = intent.getStringExtra("URL") ?: prefs.getString("URL", "") ?: ""
        isParentalOn = prefs.getBoolean("parental_control", false)

        findViewById<TextView>(R.id.navPerfil).text = extrairIniciais(firebaseUser)
        
        configurarFocoMenusTV()
        configurarModalPerfil()

        findViewById<ImageView>(R.id.navSearch).setOnClickListener {
            val intentBusca = Intent(this, BuscaActivity::class.java).apply {
                putExtra("XTREAM_USER", xtUser); putExtra("XTREAM_PASS", xtPass); putExtra("URL", urlServ)
            }
            startActivity(intentBusca)
        }

        findViewById<TextView>(R.id.navCanais).setOnClickListener {
            val intentCanais = Intent(this, CanaisActivity::class.java).apply {
                putExtra("XTREAM_USER", xtUser); putExtra("XTREAM_PASS", xtPass); putExtra("URL", urlServ)
            }
            startActivity(intentCanais)
        }

        findViewById<TextView>(R.id.navInicio).setOnClickListener { resetarCoresMenu(); findViewById<TextView>(R.id.navInicio).setTextColor(Color.WHITE); renderizarAbaHome() }
        findViewById<TextView>(R.id.navFilmes).setOnClickListener { resetarCoresMenu(); findViewById<TextView>(R.id.navFilmes).setTextColor(Color.WHITE); renderizarAbaFilmes() }
        findViewById<TextView>(R.id.navSeries).setOnClickListener { resetarCoresMenu(); findViewById<TextView>(R.id.navSeries).setTextColor(Color.WHITE); renderizarAbaSeries() }

        if (urlServ.isNotEmpty()) { 
            sincronizarFavoritosDoBanco()
            baixarCatalogoCompleto() 
        }
    }

    private fun configurarModalPerfil() {
        val overlay = findViewById<RelativeLayout>(R.id.modalPerfilOverlay)
        val txtNome = findViewById<TextView>(R.id.txtModalNome)
        val avatarPrincipal = findViewById<TextView>(R.id.txtModalAvatar)
        val avatarMenu = findViewById<TextView>(R.id.navPerfil)
        
        txtNome.text = "Olá, $firebaseUser!"
        avatarPrincipal.text = extrairIniciais(firebaseUser)
        
        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
        findViewById<TextView>(R.id.txtModalVencimento).text = "Vencimento: ${prefs.getString("EXP_DATE", "Ilimitado")}"
        findViewById<TextView>(R.id.txtModalTelas).text = "Telas: ${prefs.getString("ACTIVE_CONS", "1")} / ${prefs.getString("MAX_CONS", "Ilimitado")}"

        val btnParental = findViewById<LinearLayout>(R.id.btnModalParental)
        val switchParental = findViewById<android.widget.Switch>(R.id.switchParental)
        switchParental.isChecked = isParentalOn

        btnParental.setOnClickListener {
            isParentalOn = !isParentalOn
            switchParental.isChecked = isParentalOn
            getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE).edit().putBoolean("parental_control", isParentalOn).apply()
            baixarCatalogoCompleto() 
        }

        findViewById<Button>(R.id.btnModalLogout).setOnClickListener { 
            getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE).edit().clear().apply()
            startActivity(Intent(this, LoginActivity::class.java))
            finish() 
        }
        
        avatarMenu.setOnClickListener { overlay.visibility = View.VISIBLE; findViewById<Button>(R.id.btnModalFechar).requestFocus() }
        findViewById<Button>(R.id.btnModalFechar).setOnClickListener { overlay.visibility = View.GONE; avatarMenu.requestFocus() }
    }

    private fun sincronizarFavoritosDoBanco() {
        if(firebaseUser.isEmpty()) return
        try { FirebaseFirestore.getInstance().collection("usuarios").whereEqualTo("usuario", firebaseUser).get().addOnSuccessListener { snaps -> if(!snaps.isEmpty) { val favs = snaps.documents[0].get("favoritos") as? List<String> ?: emptyList(); getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE).edit().putString("favoritos_tv", Gson().toJson(favs)).apply() } } } catch (e: Exception) {}
    }

    private fun isAdult(cat: String?): Boolean {
        if (!isParentalOn) return false
        val c = cat?.lowercase() ?: ""
        return listOf("adulto", "adult", "18+", "xxx", "porn", "sensual", "hachutv").any { c.contains(it) }
    }

    private fun baixarCatalogoCompleto() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val api = XtreamClient.create(urlServ)
                
                // FAXINA: Removemos downloads repetidos de categorias e filtros inúteis do fluxo principal
                val respVod = api.getVodStreams(xtUser, xtPass)
                val respSeries = api.getSeriesStreams(xtUser, xtPass)
                val respCanais = api.getLiveStreams(xtUser, xtPass)

                withContext(Dispatchers.Main) {
                    if (respVod.isJsonArray) masterFilmes = Gson().fromJson(respVod, object : TypeToken<List<XtreamVod>>() {}.type)
                    if (respSeries.isJsonArray) masterSeries = Gson().fromJson(respSeries, object : TypeToken<List<XtreamSerie>>() {}.type)
                    if (respCanais.isJsonArray) masterCanais = Gson().fromJson(respCanais, object : TypeToken<List<XtreamLive>>() {}.type)
                    
                    loadingOverlay.visibility = View.GONE
                    mainScrollView.visibility = View.VISIBLE
                    
                    renderizarAbaHome()
                }
            } catch (e: Exception) { 
                withContext(Dispatchers.Main) { loadingOverlay.visibility = View.GONE; mainScrollView.visibility = View.VISIBLE }
            }
        }
    }

    private fun renderizarAbaHome() {
        val container = findViewById<LinearLayout>(R.id.containerTrilhos)
        container.removeAllViews()
        
        if (masterFilmes.isNotEmpty()) {
            val filmeAleatorio = masterFilmes.random()
            setHeroBanner(filmeAleatorio.name, filmeAleatorio.stream_icon, filmeAleatorio.stream_id, false)
        }

        val prefs = getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)

        // Trilho 1: Continuar Assistindo (Se houver)
        val contJson = prefs.getString("iptv_continuar_vod", "[]")
        val contList: List<Map<String, Any>> = try { Gson().fromJson(contJson, object : TypeToken<List<Map<String, Any>>>(){}.type) ?: emptyList() } catch (e: Exception) { emptyList() }
        val continuarItens = mutableListOf<ItemCatalogo>()
        for (item in contList) {
            val id = (item["id"] as? Number)?.toInt() ?: 0; val tipo = item["tipo"] as? String ?: ""; val tempo = (item["tempo"] as? Number)?.toLong() ?: 0L; val duracao = (item["duracao"] as? Number)?.toLong() ?: 0L
            if (tipo == "vod") { masterFilmes.find { it.stream_id == id }?.let { continuarItens.add(ItemCatalogo(it.stream_id, it.name, it.stream_icon, "vod", "Continuar", tempoAtual = tempo, duracaoTotal = duracao)) } } 
            else if (tipo == "series") { masterSeries.find { it.series_id == id }?.let { continuarItens.add(ItemCatalogo(it.series_id, it.name, it.cover, "series", "Continuar", tempoAtual = tempo, duracaoTotal = duracao)) } }
        }
        if (continuarItens.isNotEmpty()) injetarTrilho(container, "Continuar Assistindo", continuarItens)

        // Trilho 2: Favoritos
        val favsListJson = prefs.getString("favoritos_tv", "[]")
        val idsFavoritos: List<String> = try { Gson().fromJson(favsListJson, object : TypeToken<List<String>>(){}.type) ?: emptyList() } catch (e: Exception) { emptyList() }
        val canaisFavoritos = masterCanais.filter { idsFavoritos.contains(it.stream_id.toString()) }.map { ItemCatalogo(it.stream_id, it.name, it.stream_icon, "live", "Favoritos") }
        if(canaisFavoritos.isNotEmpty()) injetarTrilho(container, "Canais Favoritos", canaisFavoritos)

        // Trilho 3: Novidades (Filmes adicionados recentemente)
        val ultimosFilmes = masterFilmes.take(20).map { ItemCatalogo(it.stream_id, it.name, it.stream_icon, "vod", "Outros") }
        if(ultimosFilmes.isNotEmpty()) injetarTrilho(container, "Últimos Filmes Adicionados", ultimosFilmes)
        
        // Trilho 4: Novas Séries
        val seriesAlta = masterSeries.take(20).map { ItemCatalogo(it.series_id, it.name, it.cover, "series", "Outros") }
        if(seriesAlta.isNotEmpty()) injetarTrilho(container, "Séries Recentes", seriesAlta)
    }

    private fun renderizarAbaFilmes() { 
        val container = findViewById<LinearLayout>(R.id.containerTrilhos); container.removeAllViews()
        val limitados = masterFilmes.take(40).map { ItemCatalogo(it.stream_id, it.name, it.stream_icon, "vod", "Filmes") }
        injetarTrilho(container, "Todos os Filmes (Otimizado)", limitados)
    }

    private fun renderizarAbaSeries() { 
        val container = findViewById<LinearLayout>(R.id.containerTrilhos); container.removeAllViews()
        val limitadas = masterSeries.take(40).map { ItemCatalogo(it.series_id, it.name, it.cover, "series", "Séries") }
        injetarTrilho(container, "Todas as Séries (Otimizado)", limitadas)
    }

    private fun injetarTrilho(container: LinearLayout, titulo: String, itens: List<ItemCatalogo>) {
        val view = LayoutInflater.from(this).inflate(R.layout.trilho_vod_premium, container, false)
        view.findViewById<TextView>(R.id.txtTituloTrilho).text = titulo
        
        val rv = view.findViewById<RecyclerView>(R.id.rvTrilho).apply {
            layoutManager = LinearLayoutManager(this@MainActivity, RelativeLayout.HORIZONTAL, false)
            setHasFixedSize(true) // OTIMIZAÇÃO CRUCIAL: Evita que o RecyclerView recalcule o tamanho dos cards toda hora
            adapter = CatalogoAdapter(itens)
        }
        container.addView(view)
    }

    private fun setHeroBanner(titulo: String, imagem: String?, id: Int, isSeries: Boolean) {
        findViewById<TextView>(R.id.badgeDestaque).visibility = View.VISIBLE
        val btnAss = findViewById<Button>(R.id.btnAssistirDestaque).apply { visibility = View.VISIBLE }
        findViewById<TextView>(R.id.txtTituloDestaque).text = titulo
        findViewById<TextView>(R.id.txtDescDestaque).text = "Assista agora no SignalPlay"
        
        if(!imagem.isNullOrEmpty()) {
            Glide.with(this).load(imagem).diskCacheStrategy(DiskCacheStrategy.RESOURCE).into(findViewById<ImageView>(R.id.imgBackgroundDestaque))
        }
        btnAss.setOnClickListener { abrirMedia(id, isSeries, "vod", titulo) }
    }

    private fun abrirMedia(id: Int, isSeries: Boolean, tipo: String, tituloCanal: String = "") { 
        if (tipo == "live") {
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra("URL", urlServ); putExtra("XTREAM_USER", xtUser); putExtra("XTREAM_PASS", xtPass)
                putExtra("STREAM_ID", id); putExtra("TYPE", "live"); putExtra("TITLE", tituloCanal)
            }
            startActivity(intent)
        } else {
            val intent = Intent(this, DetalhesActivity::class.java).apply {
                putExtra("MEDIA_ID", id); putExtra("IS_SERIES", isSeries)
                putExtra("XTREAM_USER", xtUser); putExtra("XTREAM_PASS", xtPass); putExtra("URL", urlServ)
            }
            startActivity(intent)
        }
    }

    private fun configurarFocoMenusTV() { 
        val menus = listOf(R.id.navSearch, R.id.navInicio, R.id.navCanais, R.id.navFilmes, R.id.navSeries, R.id.navPerfil)
        for (id in menus) { 
            findViewById<View>(id)?.setOnFocusChangeListener { v, focus -> 
                if (focus) { 
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(100).start()
                    if(v is TextView && v.id != R.id.navPerfil) v.setTextColor(Color.WHITE) 
                } else { 
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    if(v is TextView && v.id != R.id.navPerfil) v.setTextColor(Color.parseColor("#888888")) 
                } 
            } 
        } 
    }

    private fun resetarCoresMenu() { listOf(R.id.navInicio, R.id.navCanais, R.id.navFilmes, R.id.navSeries).forEach { findViewById<TextView>(it).setTextColor(Color.parseColor("#888888")) } }
    private fun extrairIniciais(nome: String): String { if (nome.isBlank()) return "BR"; val p = nome.trim().split(" "); if (p.size >= 2) return (p[0].substring(0, 1) + p[1].substring(0, 1)).uppercase(); return if (nome.length >= 2) nome.substring(0, 2).uppercase() else nome.uppercase() }

    inner class CatalogoAdapter(private val lista: List<ItemCatalogo>) : RecyclerView.Adapter<CatalogoAdapter.HolderGenerico>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HolderGenerico {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.card_vod_premium, parent, false).apply {
                isFocusable = true
            }
            return HolderGenerico(view)
        }

        override fun onBindViewHolder(holder: HolderGenerico, position: Int) {
            val item = lista[position]
            holder.txtNome?.text = item.titulo

            if (holder.imgCapa != null && !item.capa.isNullOrEmpty()) {
                // CARREGAMENTO SEGURO: Injetando as regras de otimização de imagem
                Glide.with(holder.itemView.context)
                    .load(item.capa)
                    .apply(glideOptions)
                    .into(holder.imgCapa)
            } else {
                holder.imgCapa?.setImageDrawable(null)
            }

            holder.itemView.setOnClickListener { abrirMedia(item.id, item.tipo == "series", item.tipo, item.titulo) }
        }

        override fun getItemCount() = lista.size

        inner class HolderGenerico(val view: View) : RecyclerView.ViewHolder(view) {
            val txtNome: TextView? = view.findViewById(R.id.txtNomePremium)
            val imgCapa: ImageView? = view.findViewById(R.id.imgCapaPremium)
            init { 
                view.setOnFocusChangeListener { v, focus -> 
                    if (focus) { 
                        v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(100).start()
                    } else { 
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    } 
                } 
            }
        }
    }
}
