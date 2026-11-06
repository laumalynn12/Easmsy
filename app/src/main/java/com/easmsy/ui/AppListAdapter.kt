package com.easmsy.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.easmsy.R
import com.easmsy.core.AppEntry
import com.easmsy.core.AppRepository

/**
 * Flat, recycler-based list.  The original project used data-binding with a custom
 * observable list; a plain adapter is both smaller and faster for this read-mostly screen.
 */
class AppListAdapter(
    private val context: Context,
    private val repository: AppRepository,
    private val onSelect: (AppEntry) -> Unit,
    private val onLaunch: (AppEntry) -> Unit
) : RecyclerView.Adapter<AppListAdapter.Holder>() {

    private var entries: List<AppEntry> = emptyList()
    private var selectedPackage: String? = null
    private val iconSize = context.resources.getDimensionPixelSize(R.dimen.app_icon_size)
    private val launchTouchWidth = (72 * context.resources.displayMetrics.density).toInt()

    fun submit(apps: List<AppEntry>) {
        entries = apps
        notifyDataSetChanged()
    }

    fun selected(packageName: String?) {
        if (selectedPackage == packageName) return
        selectedPackage = packageName
        notifyDataSetChanged()
    }

    fun entryAt(position: Int): AppEntry? = entries.getOrNull(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(context).inflate(R.layout.app_entry, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = entries.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = entries[position]
        holder.bind(entry)
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.entry_name)

        fun bind(entry: AppEntry) {
            val isSelected = entry.packageName == selectedPackage
            name.text = entry.label
            name.setTextColor(
                context.getColor(
                    when (entry.state) {
                        AppEntry.State.ALIVE -> R.color.state_alive
                        AppEntry.State.FROZEN -> R.color.state_frozen
                        else -> R.color.state_other
                    }
                )
            )
            name.setBackgroundColor(
                context.getColor(if (isSelected) R.color.background_highlight else R.color.background)
            )
            name.scaleX = if (isSelected) 1.01f else 1f
            name.scaleY = if (isSelected) 1.01f else 1f
            name.elevation = if (isSelected) {
                context.resources.getDimension(R.dimen.elevation_card_resting)
            } else 0f

            val icon = try {
                entry.info.loadIcon(repository.packageManagerFor(entry.user))
            } catch (_: RuntimeException) {
                null
            }
            if (icon != null) icon.setBounds(0, 0, iconSize, iconSize)
            val launch = if (isSelected && entry.launchable &&
                entry.state != AppEntry.State.DISABLED &&
                entry.state != AppEntry.State.UNINSTALLED
            ) context.getDrawable(R.drawable.ic_launch_24dp) else null
            launch?.setBounds(0, 0, iconSize, iconSize)
            name.setCompoundDrawablesRelative(icon, null, launch, null)

            name.setOnClickListener { onSelect(entry) }
            name.setOnTouchListener { view, event ->
                if (!isSelected || launch == null) return@setOnTouchListener false
                if (event.action == MotionEvent.ACTION_UP) {
                    val x = event.x
                    if (x >= view.width - launchTouchWidth) {
                        onLaunch(entry)
                        true
                    } else false
                } else false
            }
        }
    }
}
