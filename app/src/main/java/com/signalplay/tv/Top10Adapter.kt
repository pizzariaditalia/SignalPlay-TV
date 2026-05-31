package com.signalplay.tv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions

class Top10Adapter(
    private val listaItens: List<FilmeItem>,
    private val onClick: (FilmeItem) -> Unit
) : RecyclerView.Adapter<Top10Adapter.Top10ViewHolder>() {

    class Top10ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val topNumber: TextView = view.findViewById(R.id.topNumber)
        val cardImage: ImageView = view.findViewById(R.id.cardImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Top10ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_card_top10, parent, false)
        return Top10ViewHolder(view)
    }

    override fun onBindViewHolder(holder: Top10ViewHolder, position: Int) {
        val item = listaItens[position]
        holder.topNumber.text = (position + 1).toString()
        
        val options = RequestOptions().transform(CenterCrop(), RoundedCorners(8))
        Glide.with(holder.itemView.context).load(item.urlImagem).apply(options).into(holder.cardImage)

        // EFEITO NETFLIX
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate().scaleX(1.05f).scaleY(1.05f).translationZ(10f).setDuration(150).start()
            } else {
                view.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(150).start()
            }
        }

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = if (listaItens.size > 10) 10 else listaItens.size
}
