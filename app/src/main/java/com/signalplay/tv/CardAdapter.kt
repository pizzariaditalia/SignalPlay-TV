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

// Molde de dados do que precisamos para preencher uma capa
data class FilmeItem(
    val id: String,
    val nome: String,
    val urlImagem: String,
    val streamUrl: String,
    val tipo: String // "filme", "serie" ou "tv"
)

// O "Operário" que constrói e recicla as capas na tela
class CardAdapter(private val listaItens: List<FilmeItem>) : RecyclerView.Adapter<CardAdapter.CardViewHolder>() {

    class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardImage: ImageView = view.findViewById(R.id.cardImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        // Pega o nosso XML de design e transforma em um objeto de tela
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val itemAtual = listaItens[position]
        
        // O Glide entra em ação: baixa a imagem em segundo plano e coloca no card
        // Se a imagem falhar, ele mantém o fundo escuro do próprio XML
        var options = RequestOptions().transform(CenterCrop(), RoundedCorners(8))
        
        Glide.with(holder.itemView.context)
            .load(itemAtual.urlImagem)
            .apply(options)
            .into(holder.cardImage)

        // Aqui nós colocaremos a ação de clique do controle remoto depois
        holder.itemView.setOnClickListener {
            // (Futuro: Abrir o player ou a tela de detalhes)
        }
    }

    override fun getItemCount(): Int {
        return listaItens.size
    }
}
