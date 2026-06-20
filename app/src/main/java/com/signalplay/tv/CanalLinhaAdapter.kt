package com.signalplay.tv

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class CanalLinhaAdapter(
    private val listaCanais: List<CanalItem>,
    private val onClick: (CanalItem) -> Unit
) : RecyclerView.Adapter<CanalLinhaAdapter.LinhaViewHolder>() {

    class LinhaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val linhaAmarela: View = view.findViewById(R.id.linhaAmarela)
        val imgLogo: ImageView = view.findViewById(R.id.canalImage)
        val tvNome: TextView = view.findViewById(R.id.canalTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LinhaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_canal_linha, parent, false)
        return LinhaViewHolder(view)
    }

    override fun onBindViewHolder(holder: LinhaViewHolder, position: Int) {
        val canal = listaCanais[position]
        holder.tvNome.text = canal.nome
        
        Glide.with(holder.itemView.context)
            .load(canal.urlImagem)
            .placeholder(android.R.drawable.ic_menu_report_image)
            .into(holder.imgLogo)

        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.setBackgroundColor(Color.parseColor("#222222"))
                holder.linhaAmarela.visibility = View.VISIBLE
                holder.tvNome.setTextColor(Color.parseColor("#FFC107"))
            } else {
                view.setBackgroundColor(Color.TRANSPARENT)
                holder.linhaAmarela.visibility = View.INVISIBLE
                holder.tvNome.setTextColor(Color.WHITE)
            }
        }

        holder.itemView.setOnClickListener { onClick(canal) }
    }

    override fun getItemCount(): Int = listaCanais.size
}
