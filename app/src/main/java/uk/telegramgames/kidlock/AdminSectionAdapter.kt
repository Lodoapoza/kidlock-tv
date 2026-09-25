package uk.telegramgames.kidlock

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class AdminSection(
    val id: Int,
    val title: String,
    var isSelected: Boolean = false
)

class AdminSectionAdapter(
    private val sections: List<AdminSection>,
    private val onSectionClick: (AdminSection) -> Unit
) : RecyclerView.Adapter<AdminSectionAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvSectionName: TextView = itemView.findViewById(R.id.tvSectionName)

        fun bind(section: AdminSection) {
            tvSectionName.text = section.title
            tvSectionName.isSelected = section.isSelected

            if (section.isSelected) {
                tvSectionName.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                tvSectionName.setTypeface(null, android.graphics.Typeface.NORMAL)
            }

            itemView.setOnClickListener {
                // Update selection state
                sections.forEach { it.isSelected = false }
                section.isSelected = true
                notifyDataSetChanged()
                onSectionClick(section)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_section, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(sections[position])
    }

    override fun getItemCount(): Int = sections.size
}
