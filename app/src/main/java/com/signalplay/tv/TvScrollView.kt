package com.signalplay.tv

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.core.widget.NestedScrollView

class TvScrollView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : NestedScrollView(context, attrs, defStyleAttr) {

    // A MÁGICA DEFINITIVA DA TV:
    // Quando o controle remoto muda o foco, o Android pede para "pular" para o item (immediate = true).
    // Nós interceptamos essa ordem e forçamos o "immediate" a ser FALSE, obrigando a tela a deslizar suavemente.
    override fun requestChildRectangleOnScreen(child: View, rectangle: Rect, immediate: Boolean): Boolean {
        return super.requestChildRectangleOnScreen(child, rectangle, false)
    }
}
