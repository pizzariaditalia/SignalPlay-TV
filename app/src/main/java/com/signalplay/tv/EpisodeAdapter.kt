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

data class EpisodeItem(
    val id: String,
    val title: String,
    val episodeNum: String,
    val urlImage: String,
    val streamUrl: String
)

class EpisodeAdapter(
    private val listaEpisodios: List<EpisodeItem>,
    private val onClick: (EpisodeItem) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.EpisodeViewHolder>() {

    class EpisodeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val epImage: ImageView = view.findViewById(R.id.epImage)
        val epNumber: TextView = view.findViewById(R.id.epNumber)
        val epTitle: TextView = view.findViewById(R.id.epTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_episode, parent, false)
        return EpisodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        val ep = listaEpisodios[position]
        holder.epNumber.text = "Episódio ${ep.episodeNum}"
        holder.epTitle.text = ep.title

        val options = RequestOptions().transform(CenterCrop(), RoundedCorners(8))
        Glide.with(holder.itemView.context)
            .load(ep.urlImage)
            .apply(options)
            .placeholder(android.R.drawable.ic_menu_report_image)
            .into(holder.epImage)

        holder.itemView.setOnClickListener {
            onClick(ep)
        }
    }

    override fun getItemCount(): Int = listaEpisodios.size
}
