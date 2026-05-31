package com.signalplay.tv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions

data class FilmeItem(
    val id: String,
    val nome: String,
    val urlImagem: String,
    val streamUrl: String,
    val tipo: String,
    val categoryId: String = "" 
)

class CardAdapter(
    private val listaItens: List<FilmeItem>,
    private val onClick: ((FilmeItem) -> Unit)? = null 
) : RecyclerView.Adapter<CardAdapter.CardViewHolder>() {

    class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardImage: ImageView = view.findViewById(R.id.cardImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val itemAtual = listaItens[position]
        
        val options = RequestOptions().transform(CenterCrop(), RoundedCorners(8))
        Glide.with(holder.itemView.context).load(itemAtual.urlImagem).apply(options).into(holder.cardImage)

        // SUPER ZOOM E SOMBRA ATIVADOS
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                // Joga o item pra frente da tela para não ser engolido pelos vizinhos
                view.bringToFront()
                view.animate().scaleX(1.12f).scaleY(1.12f).translationZ(20f).setDuration(200).start()
            } else {
                view.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(200).start()
            }
        }

        holder.itemView.setOnClickListener { onClick?.invoke(itemAtual) }
    }

    override fun getItemCount(): Int = listaItens.size
}
