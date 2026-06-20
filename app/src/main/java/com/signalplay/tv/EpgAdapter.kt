package com.signalplay.tv

import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

data class EpgItem(
    val title: String, 
    val horario: String, 
    val duracao: String, 
    val isLive: Boolean, 
    val corTexto: String
)

class EpgAdapter(private val list: List<EpgItem>) : RecyclerView.Adapter<EpgAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(4001)
        val tvTime: TextView = view.findViewById(4002)
        val tvDuration: TextView = view.findViewById(4003)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            // CORREÇÃO AQUI: Usando RecyclerView.LayoutParams
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 4, 0, 8)
            }
            background = ContextCompat.getDrawable(parent.context, R.drawable.bg_glass)
            setPadding(20, 16, 20, 16)
        }

        val title = TextView(parent.context).apply {
            id = 4001
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val timeLayout = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 6
            }
        }

        val time = TextView(parent.context).apply {
            id = 4002
            textSize = 13f
        }

        val duration = TextView(parent.context).apply {
            id = 4003
            setTextColor(Color.parseColor("#888888"))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = 16
            }
        }

        timeLayout.addView(time)
        timeLayout.addView(duration)
        layout.addView(title)
        layout.addView(timeLayout)
        return ViewHolder(layout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.tvTitle.text = item.title
        holder.tvTime.text = item.horario
        holder.tvTime.setTextColor(Color.parseColor(item.corTexto))
        holder.tvDuration.text = "Duração: ${item.duracao}"

        if (item.isLive) {
            holder.itemView.setBackgroundColor(Color.parseColor("#1A2ED573"))
        } else {
            holder.itemView.setBackgroundResource(R.drawable.bg_glass)
        }
    }

    override fun getItemCount(): Int = list.size
}
