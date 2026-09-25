package uk.telegramgames.kidlock

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CodeAdapter(
    private var codes: List<Code>,
    private val onDeleteClick: (Code) -> Unit
) : RecyclerView.Adapter<CodeAdapter.CodeViewHolder>() {

    class CodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCode: TextView = itemView.findViewById(R.id.tvCode)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        val btnDelete: Button = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CodeViewHolder {
        Log.d("KidLock", "CodeAdapter.onCreateViewHolder() - создается новый ViewHolder")
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_code, parent, false)
        return CodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: CodeViewHolder, position: Int) {
        val code = codes[position]
        Log.d("KidLock", "CodeAdapter.onBindViewHolder() - position $position, minutes: ${code.addedTimeMinutes}, used: ${code.isUsed}")
        holder.tvCode.text = code.value
        val context = holder.itemView.context
        holder.tvStatus.text = if (code.isUsed) {
            context.getString(R.string.code_status_used)
        } else {
            context.getString(R.string.code_status_unused)
        }
        holder.tvStatus.setTextColor(
            if (code.isUsed) {
                context.getColor(R.color.status_bad)
            } else {
                context.getColor(R.color.status_good)
            }
        )
        holder.tvTime.text = if (code.addedTimeMinutes > 0) {
            context.getString(R.string.time_added_minutes_format, code.addedTimeMinutes)
        } else {
            ""
        }

        holder.btnDelete.setOnClickListener {
            onDeleteClick(code)
        }

        holder.btnDelete.visibility = if (code.isUsed) View.GONE else View.VISIBLE
        Log.d("KidLock", "CodeAdapter.onBindViewHolder() - элемент отрисован для позиции $position")
    }

    override fun getItemCount(): Int {
        val count = codes.size
        Log.d("KidLock", "CodeAdapter.getItemCount() - возвращает $count")
        return count
    }

    fun updateCodes(newCodes: List<Code>) {
        Log.d("KidLock", "CodeAdapter.updateCodes() - received ${newCodes.size} codes")
        codes = newCodes
        Log.d("KidLock", "CodeAdapter.updateCodes() - коды обновлены, вызываем notifyDataSetChanged(), getItemCount=${codes.size}")
        notifyDataSetChanged()
        Log.d("KidLock", "CodeAdapter.updateCodes() - notifyDataSetChanged() вызван")
    }
}
