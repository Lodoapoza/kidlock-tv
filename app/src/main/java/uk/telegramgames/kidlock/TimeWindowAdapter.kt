package uk.telegramgames.kidlock

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TimeWindowAdapter(
    private var windows: List<TimeWindow>,
    private val onEdit: (TimeWindow) -> Unit,
    private val onDelete: (TimeWindow) -> Unit
) : RecyclerView.Adapter<TimeWindowAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTimeRange: TextView = view.findViewById(R.id.tvTimeRange)
        val tvTypeBadge: TextView = view.findViewById(R.id.tvTypeBadge)
        val tvAppsInfo: TextView = view.findViewById(R.id.tvAppsInfo)
        val tvDaysInfo: TextView = view.findViewById(R.id.tvDaysInfo)
        val btnEdit: Button = view.findViewById(R.id.btnEditWindow)
        val btnDelete: Button = view.findViewById(R.id.btnDeleteWindow)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_time_window, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val window = windows[position]
        holder.tvTimeRange.text = window.formatTime()

        if (window.type == TimeWindowType.BLOCK) {
            holder.tvTypeBadge.text = "Blocage"
            holder.tvTypeBadge.setBackgroundColor(holder.itemView.context.getColor(R.color.status_bad))
        } else {
            holder.tvTypeBadge.text = "Autorisation"
            holder.tvTypeBadge.setBackgroundColor(holder.itemView.context.getColor(R.color.status_good))
        }

        if (window.type == TimeWindowType.BLOCK) {
            holder.tvAppsInfo.text = holder.itemView.context.getString(R.string.schedule_global_block_label)
            holder.tvAppsInfo.visibility = View.VISIBLE
        } else {
            holder.tvAppsInfo.visibility = View.GONE
        }

        val daysText = window.formatDays()
        if (daysText.isNotEmpty() && daysText != "Tous les jours") {
            holder.tvDaysInfo.text = daysText
            holder.tvDaysInfo.visibility = View.VISIBLE
        } else {
            holder.tvDaysInfo.visibility = View.GONE
        }

        holder.btnEdit.setOnClickListener { onEdit(window) }
        holder.btnDelete.setOnClickListener { onDelete(window) }
    }

    override fun getItemCount(): Int = windows.size

    fun updateWindows(newWindows: List<TimeWindow>) {
        windows = newWindows
        notifyDataSetChanged()
    }
}
