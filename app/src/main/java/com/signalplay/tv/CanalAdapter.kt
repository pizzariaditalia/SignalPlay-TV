package com.signalplay.tv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

data class CanalItem(
    val id: String,
    val nome: String,
    val urlImagem: String,
    val categoryId: String,
    val streamUrl: String
)

class CanalAdapter(
    private val listaCanais: List<CanalItem>,
    private val idsFavoritos: List<String>,
    private val onClick: (CanalItem) -> Unit,
    private val onLongClick: (CanalItem) -> Unit
) : RecyclerView.Adapter<CanalAdapter.CanalViewHolder>() {

    class CanalViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgLogo: ImageView = view.findViewById(R.id.canalImage)
        val tvNome: TextView = view.findViewById(R.id.canalTitle)
        val imgStar: ImageView = view.findViewById(R.id.canalStar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CanalViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_canal, parent, false)
        return CanalViewHolder(view)
    }

    override fun onBindViewHolder(holder: CanalViewHolder, position: Int) {
        val canal = listaCanais[position]
        holder.tvNome.text = canal.nome
        Glide.with(holder.itemView.context).load(canal.urlImagem).placeholder(android.R.drawable.ic_menu_report_image).into(holder.imgLogo)

        if (idsFavoritos.contains(canal.id)) {
            holder.imgStar.setImageResource(android.R.drawable.btn_star_big_on)
            holder.imgStar.visibility = View.VISIBLE
        } else {
            holder.imgStar.visibility = View.GONE
        }

        // EFEITO NETFLIX NO CANAL TAMBÉM
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate().scaleX(1.05f).scaleY(1.05f).translationZ(10f).setDuration(150).start()
            } else {
                view.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(150).start()
            }
        }

        holder.itemView.setOnClickListener { onClick(canal) }
        holder.itemView.setOnLongClickListener { onLongClick(canal); true }
    }

    override fun getItemCount(): Int = listaCanais.size
}
