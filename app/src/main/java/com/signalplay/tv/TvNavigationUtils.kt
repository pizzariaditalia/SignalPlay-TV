package com.signalplay.tv

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.leanback.widget.HorizontalGridView

object TvNavigationUtils {

    fun aplicarModoImersivo(activity: Activity) {
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    fun configurarPrateleira(prateleira: HorizontalGridView) {
        // =========================================================================
        // A MÁGICA DA ÂNCORA DO GOOGLE TV USANDO LEANBACK
        // =========================================================================
        
        // Define que a "âncora" da lista ficará sempre no início (esquerda) da tela
        prateleira.windowAlignment = HorizontalGridView.WINDOW_ALIGN_LOW
        
        // O respiro: Desloca a âncora 48 pixels para a direita para não ficar colado na TV
        prateleira.windowAlignmentOffset = 48 
        prateleira.windowAlignmentOffsetPercent = HorizontalGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED
        
        // Garante que o próprio item se alinhe pela borda esquerda dele mesmo
        prateleira.itemAlignmentOffsetPercent = HorizontalGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED
        
        // Espaçamento entre os filmes (em pixels)
        prateleira.itemSpacing = 24
        
        // Permite que o zoom "vaze" para fora das bordas do item sem ser cortado
        prateleira.clipToPadding = false
        prateleira.clipChildren = false
        
        // Configuração opcional para remover a animação genérica de carregamento e manter limpo
        prateleira.itemAnimator = null
    }
}
