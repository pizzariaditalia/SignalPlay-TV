    private fun configurarModalPerfil() {
        val overlay = findViewById<RelativeLayout>(R.id.modalPerfilOverlay)
        findViewById<TextView>(R.id.txtModalNome).text = "Olá, $firebaseUser!"
        val avatarPrincipal = findViewById<TextView>(R.id.txtModalAvatar)
        val avatarMenu = findViewById<TextView>(R.id.navPerfil)
        
        avatarPrincipal.text = extrairIniciais(firebaseUser)
        
        // Mapeamento dos Avatares Coloridos (Sem usar SharedPreferences complexas, aplica na hora)
        val coresAvatares = listOf(R.id.avatarColor1 to R.drawable.bg_perfil_amarelo, R.id.avatarColor2 to Color.parseColor("#ff4757"), R.id.avatarColor3 to Color.parseColor("#2ed573"), R.id.avatarColor4 to Color.parseColor("#1e90ff"))
        
        coresAvatares.forEach { par ->
            val view = findViewById<TextView>(par.first)
            view.setOnFocusChangeListener { v, focus -> if(focus) v.animate().scaleX(1.2f).scaleY(1.2f).start() else v.animate().scaleX(1.0f).scaleY(1.0f).start() }
            view.setOnClickListener {
                if (par.first == R.id.avatarColor1) { avatarPrincipal.setBackgroundResource(par.second as Int); avatarMenu.setBackgroundResource(par.second as Int) }
                else { avatarPrincipal.setBackgroundColor(par.second as Int); avatarMenu.setBackgroundColor(par.second as Int) }
            }
        }

        val listenerFocoConfig = View.OnFocusChangeListener { v, focus -> if(focus) { v.setBackgroundResource(R.drawable.bg_config_item); v.animate().scaleX(1.03f).start() } else { v.setBackgroundColor(Color.TRANSPARENT); v.animate().scaleX(1.0f).start() } }
        val btnHist = findViewById<LinearLayout>(R.id.btnModalLimparHist)
        val btnFav = findViewById<LinearLayout>(R.id.btnModalLimparFavs)
        btnHist.setOnFocusChangeListener(listenerFocoConfig)
        btnFav.setOnFocusChangeListener(listenerFocoConfig)

        findViewById<TextView>(R.id.navPerfil).setOnClickListener { 
            overlay.visibility = View.VISIBLE
            findViewById<Button>(R.id.btnModalFechar).requestFocus() 
        }

        findViewById<Button>(R.id.btnModalFechar).setOnClickListener { 
            overlay.visibility = View.GONE
            findViewById<TextView>(R.id.navPerfil).requestFocus() 
        }

        btnHist.setOnClickListener {
            getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE).edit().putString("iptv_continuar_vod", "[]").apply()
            Toast.makeText(this, "Histórico Removido!", Toast.LENGTH_SHORT).show()
            overlay.visibility = View.GONE; renderizarAbaHome()
        }

        btnFav.setOnClickListener {
            getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE).edit().putString("favoritos_tv", "[]").apply()
            Toast.makeText(this, "Favoritos Zerados!", Toast.LENGTH_SHORT).show()
            overlay.visibility = View.GONE; renderizarAbaHome()
        }

        findViewById<Button>(R.id.btnModalLogout).setOnClickListener {
            getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE).edit().clear().apply()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
