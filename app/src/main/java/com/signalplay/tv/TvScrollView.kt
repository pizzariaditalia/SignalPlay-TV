package com.signalplay.tv

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.core.widget.NestedScrollView
import kotlin.math.abs

class TvScrollView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : NestedScrollView(context, attrs, defStyleAttr) {

    private var ultimoEixoY = -1

    override fun requestChildFocus(child: View?, focused: View?) {
        super.requestChildFocus(child, focused)
        if (focused != null) {
            val rect = Rect()
            focused.getDrawingRect(rect)
            offsetDescendantRectToMyCoords(focused, rect)

            val screenHeight = height
            val targetY = rect.top - (screenHeight / 2) + (focused.height / 2)

            // A VACINA CONTRA OS "PULOS":
            // Só move a tela verticalmente se a mudança for maior que 30 pixels (ou seja, você mudou de prateleira).
            // Se for menor que 30, significa que você só andou para o lado e o zoom alterou a altura em milímetros.
            if (abs(targetY - ultimoEixoY) > 30) {
                smoothScrollTo(0, targetY)
                ultimoEixoY = targetY
            }
        }
    }

    override fun requestChildRectangleOnScreen(child: View, rectangle: Rect, immediate: Boolean): Boolean {
        return false 
    }
}
