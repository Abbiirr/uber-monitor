package com.example.uber_monitor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.uber_monitor.databinding.ItemRecentActivityBinding

class RecentActivityAdapter(
    private val activities: List<DashboardActivity.RideActivity>
) : RecyclerView.Adapter<RecentActivityAdapter.ViewHolder>() {

    class ViewHolder(private val binding: ItemRecentActivityBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(activity: DashboardActivity.RideActivity) {
            binding.tvDescription.text = activity.description
            binding.tvTime.text = activity.time

            if (activity.amount != null) {
                binding.tvAmount.visibility = View.VISIBLE
                binding.tvAmount.text = activity.amount
                binding.tvAmount.setTextColor(ContextCompat.getColor(binding.root.context, R.color.green))
            } else {
                binding.tvAmount.visibility = View.GONE
            }

            // Set icon based on description
            val iconRes = when {
                activity.description.contains("Completed", true) -> R.drawable.ic_check_circle
                activity.description.contains("Started", true) -> R.drawable.ic_car
                activity.description.contains("Accepted", true) -> R.drawable.ic_call_received
                else -> R.drawable.ic_activity
            }
            binding.ivIcon.setImageResource(iconRes)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentActivityBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(activities[position])
    }

    override fun getItemCount() = activities.size
}