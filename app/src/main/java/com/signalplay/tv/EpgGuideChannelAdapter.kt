package com.signalplay.tv

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class EpgGuideChannelAdapter(
    private val list: List<CanalItem>,
    private val onClick: (CanalItem) -> Unit,
    private val onFocus: (CanalItem) -> Unit // NOVIDADE: Comunicação direta de foco!
) : RecyclerView.Adapter<EpgGuideChannelAdapter.ViewHolder>() {

    private val interpolator = OvershootInterpolator(1.2f)

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: LinearLayout = view.findViewById(R.id.containerEpgGuide)
        val img: ImageView = view.findViewById(R.id.imgGuideChannel)
        val txt: TextView = view.findViewById(R.id.tvGuideChannelName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_epg_guide_channel, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.txt.text = item.nome

        Glide.with(holder.itemView.context)
            .load(item.urlImagem)
            .placeholder(R.drawable.bg_glass)
            .into(holder.img)

        // Quando a TV Box focar neste item específico, ele se pinta de amarelo e avisa a Activity!
        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                holder.container.setBackgroundColor(Color.parseColor("#FFC107"))
                holder.txt.setTextColor(Color.BLACK)
                v.animate().scaleX(1.02f).scaleY(1.02f).setDuration(150).setInterpolator(interpolator).start()
                
                // Dispara o carregamento do guia da direita
                onFocus(item) 
            } else {
                holder.container.setBackgroundResource(R.drawable.bg_glass)
                holder.txt.setTextColor(Color.WHITE)
                v.animate().scaleX(1f).scaleY(1f).setDuration(150).setInterpolator(interpolator).start()
            }
        }

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = list.size
}
