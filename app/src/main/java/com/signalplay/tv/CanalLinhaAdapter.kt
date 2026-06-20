package com.signalplay.tv

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class CanalLinhaAdapter(
    private val list: List<CanalItem>, 
    private val onClick: (CanalItem) -> Unit
) : RecyclerView.Adapter<CanalLinhaAdapter.ViewHolder>() {

    private val interpolator = OvershootInterpolator(1.2f)

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(3001)
        val txt: TextView = view.findViewById(3002)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // CORREÇÃO AQUI: Usando RecyclerView.LayoutParams
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(6, 6, 6, 6)
            }
            background = ContextCompat.getDrawable(parent.context, R.drawable.bg_glass)
            isFocusable = true
            isClickable = true
            setPadding(24, 16, 24, 16)
        }

        val img = ImageView(parent.context).apply {
            id = 3001
            layoutParams = LinearLayout.LayoutParams(60, 60)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val txt = TextView(parent.context).apply {
            id = 3002
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 16
            }
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 1
        }

        layout.addView(img)
        layout.addView(txt)
        return ViewHolder(layout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.txt.text = item.nome

        Glide.with(holder.itemView.context)
            .load(item.urlImagem)
            .placeholder(R.drawable.bg_glass)
            .into(holder.img)

        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.setBackgroundColor(Color.parseColor("#FFC107"))
                holder.txt.setTextColor(Color.BLACK)
                v.animate().scaleX(1.02f).scaleY(1.02f).setDuration(150).setInterpolator(interpolator).start()
            } else {
                v.setBackgroundResource(R.drawable.bg_glass)
                holder.txt.setTextColor(Color.WHITE)
                v.animate().scaleX(1f).scaleY(1f).setDuration(150).setInterpolator(interpolator).start()
            }
        }

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = list.size
}
