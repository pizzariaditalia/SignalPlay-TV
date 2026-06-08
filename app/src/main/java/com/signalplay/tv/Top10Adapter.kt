package com.signalplay.tv

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions

class Top10Adapter(
    private val listaItens: List<FilmeItem>,
    private val onClick: (FilmeItem) -> Unit
) : RecyclerView.Adapter<Top10Adapter.Top10ViewHolder>() {

    private val interpolator = OvershootInterpolator(1.2f)

    class Top10ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val topNumber: TextView = view.findViewById(R.id.topNumber)
        val cardImage: ImageView = view.findViewById(R.id.cardImage)
        val cardContainer: View = view.findViewById(R.id.cardContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Top10ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_card_top10, parent, false)
        return Top10ViewHolder(view)
    }

    override fun onBindViewHolder(holder: Top10ViewHolder, position: Int) {
        val item = listaItens[position]
        holder.topNumber.text = (position + 1).toString()
        
        val context = holder.itemView.context
        val prefs = context.getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
        val isLowEndMode = prefs.getBoolean("LOW_END_MODE", false)
        
        // MÁGICA: Corta a resolução e o peso da imagem pela metade na memória RAM
        val options = RequestOptions()
            .transform(CenterCrop(), RoundedCorners(8))
            .format(DecodeFormat.PREFER_RGB_565)
            .override(250, 350) 
            
        Glide.with(context).load(item.urlImagem).apply(options).into(holder.cardImage)

        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.bringToFront()
                if(!isLowEndMode) {
                    view.animate().scaleX(1.12f).scaleY(1.12f).translationZ(20f).setDuration(250).setInterpolator(interpolator).start()
                } else {
                    holder.cardContainer.setBackgroundColor(Color.parseColor("#FFC107"))
                }
            } else {
                if(!isLowEndMode) {
                    view.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(250).setInterpolator(interpolator).start()
                } else {
                    holder.cardContainer.setBackgroundResource(R.drawable.bg_card_focus)
                }
            }
        }

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = if (listaItens.size > 10) 10 else listaItens.size
}
