package com.signalplay.tv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions

// MÁGICA: A classe FilmeItem voltou para o seu lugar de direito!
data class FilmeItem(
    val id: String,
    val nome: String,
    val urlImagem: String,
    val streamUrl: String,
    val tipo: String,
    val categoryId: String = "",
    var progresso: Int = 0
)

class CardAdapter(
    private val listaItens: List<FilmeItem>,
    private val onClick: ((FilmeItem) -> Unit)? = null 
) : RecyclerView.Adapter<CardAdapter.CardViewHolder>() {

    private val interpolator = OvershootInterpolator(1.2f)

    class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardImage: ImageView = view.findViewById(R.id.cardImage)
        val progressBar: ProgressBar? = view.findViewById(R.id.progressFilme)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val itemAtual = listaItens[position]
        
        val options = RequestOptions().transform(CenterCrop(), RoundedCorners(8))
        Glide.with(holder.itemView.context).load(itemAtual.urlImagem).apply(options).into(holder.cardImage)

        if (holder.progressBar != null) {
            if (itemAtual.progresso > 0) {
                holder.progressBar.visibility = View.VISIBLE
                holder.progressBar.progress = itemAtual.progresso
            } else {
                holder.progressBar.visibility = View.GONE
            }
        }

        // MÁGICA: Animação elástica 
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.bringToFront()
                view.animate().scaleX(1.12f).scaleY(1.12f).translationZ(20f).setDuration(250).setInterpolator(interpolator).start()
            } else {
                view.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(250).setInterpolator(interpolator).start()
            }
        }

        holder.itemView.setOnClickListener { onClick?.invoke(itemAtual) }
    }

    override fun getItemCount(): Int = listaItens.size
}
