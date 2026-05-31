package com.signalplay.tv

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class EpgItem(val titulo: String, val horario: String, val isAgora: Boolean)

class EpgAdapter(private val lista: List<EpgItem>) : RecyclerView.Adapter<EpgAdapter.EpgViewHolder>() {

    class EpgViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitulo: TextView = view.findViewById(R.id.epgTitulo)
        val tvHorario: TextView = view.findViewById(R.id.epgHorario)
        val indAgora: View = view.findViewById(R.id.epgIndAgora)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpgViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_epg, parent, false)
        return EpgViewHolder(view)
    }

    override fun onBindViewHolder(holder: EpgViewHolder, position: Int) {
        val item = lista[position]
        holder.tvTitulo.text = item.titulo
        holder.tvHorario.text = item.horario
        
        // Acende a barrinha verde se for o primeiro programa (Ao Vivo)
        holder.indAgora.visibility = if (item.isAgora) View.VISIBLE else View.INVISIBLE

        // Muda o fundo para cinza escuro quando o controle passar por cima
        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) v.setBackgroundColor(Color.parseColor("#222222"))
            else v.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    override fun getItemCount() = lista.size
}
