package com.signalplay.tv

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class TvLinearLayoutManager(context: Context) : LinearLayoutManager(context, HORIZONTAL, false) {

    override fun requestChildRectangleOnScreen(
        parent: RecyclerView,
        child: View,
        rect: Rect,
        immediate: Boolean,
        focusedChildVisible: Boolean
    ): Boolean {
        // A MÁGICA HORIZONTAL DO GOOGLE TV
        // Em vez de ir para as bordas, força a lista a puxar o item focado para o ponto de âncora
        
        // Usamos o padding interno que você definiu (ex: 48dp) como nosso ponto de parada fixo
        val pontoDeAncora = parent.paddingStart
        
        // Calcula a diferença entre onde o item está e onde ele deveria ficar cravado
        val scrollAmount = child.left - pontoDeAncora

        if (scrollAmount != 0) {
            if (immediate) {
                parent.scrollBy(scrollAmount, 0)
            } else {
                parent.smoothScrollBy(scrollAmount, 0)
            }
            return true
        }
        return false
    }
}
