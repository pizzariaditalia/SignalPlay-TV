package com.signalplay.tv

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.core.widget.NestedScrollView

class TvScrollView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : NestedScrollView(context, attrs, defStyleAttr) {

    override fun requestChildFocus(child: View?, focused: View?) {
        super.requestChildFocus(child, focused)
        if (focused != null) {
            val rect = Rect()
            focused.getDrawingRect(rect)
            offsetDescendantRectToMyCoords(focused, rect)

            // A MÁGICA: Calcula o centro exato da tela da TV
            val screenHeight = height
            val targetY = rect.top - (screenHeight / 2) + (focused.height / 2)

            // Desliza a tela de forma super suave até o alvo central
            smoothScrollTo(0, targetY)
        }
    }

    // A VACINA: Isso aqui desativa o pulo "seco" padrão de celulares
    override fun requestChildRectangleOnScreen(child: View?, rectangle: Rect?, immediate: Boolean): Boolean {
        return false 
    }
}
