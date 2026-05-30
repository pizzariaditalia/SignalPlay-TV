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
    val categoryId: String = "" // Essencial para as pastas laterais
)

class CardAdapter(
    private val listaItens: List<FilmeItem>,
    private val onClick: ((FilmeItem) -> Unit)? = null // Esperando o clique no filme
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
        
        Glide.with(holder.itemView.context)
            .load(itemAtual.urlImagem)
            .apply(options)
            .into(holder.cardImage)

        // Aciona o clique no pôster e manda o dado pra frente
        holder.itemView.setOnClickListener {
            onClick?.invoke(itemAtual)
        }
    }

    override fun getItemCount(): Int {
        return listaItens.size
    }
}
