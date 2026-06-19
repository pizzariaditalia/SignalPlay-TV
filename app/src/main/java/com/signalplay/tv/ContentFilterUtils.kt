package com.signalplay.tv

object ContentFilterUtils {
    
    val PALAVRAS_PROIBIDAS = listOf("adult", "+18", "18+", "xxx", "porn", "hachutv", "sensual", "sex", "playboy")

    fun isContentBlocked(
        nomeItem: String,
        nomeCategoria: String,
        isParentalActive: Boolean,
        filterSD: Boolean,
        filterHD: Boolean,
        filterFHD: Boolean,
        filterH265: Boolean,
        filter4K: Boolean,
        forcedHide4K: Boolean = false,
        forcedHideFHD: Boolean = false,
        firebaseBlockedList: List<String> = emptyList()
    ): Boolean {
        val catNameLower = nomeCategoria.lowercase()
        val nLower = nomeItem.lowercase()
        val nUp = nomeItem.uppercase()

        // 1. Verificação do Firebase e Parental
        if (firebaseBlockedList.contains(catNameLower) || firebaseBlockedList.contains(nLower)) return true
        if (isParentalActive && (PALAVRAS_PROIBIDAS.any { catNameLower.contains(it) } || PALAVRAS_PROIBIDAS.any { nLower.contains(it) })) return true

        // 2. Verificação de Tags de Qualidade
        val isExplicitSD = nUp.contains(" SD ") || nUp.endsWith(" SD") || nUp.startsWith("SD ") || nUp.contains("(SD)") || nUp.contains("[SD]") || nUp.contains("|SD|") || nUp.contains("- SD") || nUp == "SD"
        val hasFHD = nUp.contains(" FHD ") || nUp.endsWith(" FHD") || nUp.startsWith("FHD ") || nUp.contains("(FHD)") || nUp.contains("[FHD]") || nUp.contains("|FHD|") || nUp.contains("- FHD") || nUp == "FHD"
        val hasHD = (nUp.contains(" HD ") || nUp.endsWith(" HD") || nUp.startsWith("HD ") || nUp.contains("(HD)") || nUp.contains("[HD]") || nUp.contains("|HD|") || nUp.contains("- HD") || nUp == "HD") && !hasFHD
        val has4K = nUp.contains(" 4K ") || nUp.endsWith(" 4K") || nUp.startsWith("4K ") || nUp.contains("(4K)") || nUp.contains("[4K]") || nUp.contains("|4K|") || nUp.contains("- 4K") || nUp.contains("UHD") || nUp == "4K"
        val hasH265 = nUp.contains("H265") || nUp.contains("HEVC") || nUp.contains("H.265")

        // 3. Aplicação dos Filtros de Painel
        if (forcedHide4K && has4K) return true
        if (forcedHideFHD && hasFHD) return true

        // 4. Aplicação dos Filtros Locais (Usuário)
        if (filterSD) {
            if (isExplicitSD) return true
            else if (!hasHD && !hasFHD && !has4K && !hasH265) {
                val isSafeCat = catNameLower.contains("24h") || catNameLower.contains("24 horas") || catNameLower.contains("infantil") || catNameLower.contains("kids") || catNameLower.contains("desenho") || catNameLower.contains("religi") || catNameLower.contains("notícia") || catNameLower.contains("news") || catNameLower.contains("document") || catNameLower.contains("educa") || catNameLower.contains("música") || catNameLower.contains("rádio")
                if (!isSafeCat) return true
            }
        }
        if (filterHD && hasHD) return true
        if (filterFHD && hasFHD) return true
        if (filterH265 && hasH265) return true
        if (filter4K && has4K) return true

        return false // Se passou por tudo, o conteúdo é limpo e deve ser exibido!
    }
}
