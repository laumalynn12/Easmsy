package com.easmsy.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.RecyclerView
import com.easmsy.R

data class FeaturedItem(
    val tag: String,
    val title: String,
    val description: String,
    @DrawableRes val icon: Int,
    val button: String,
    val onClick: () -> Unit
)

class FeaturedAdapter(
    private val context: Context,
    private var items: List<FeaturedItem> = emptyList()
) : RecyclerView.Adapter<FeaturedAdapter.Holder>() {

    fun submit(featured: List<FeaturedItem>) {
        items = featured
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(context).inflate(R.layout.featured_entry, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.featured_icon)
        private val title: TextView = view.findViewById(R.id.featured_title)
        private val description: TextView = view.findViewById(R.id.featured_description)
        private val button: Button = view.findViewById(R.id.featured_button)

        fun bind(item: FeaturedItem) {
            title.text = item.title
            description.text = item.description
            if (item.icon != 0) {
                icon.setImageResource(item.icon)
                icon.visibility = View.VISIBLE
            } else icon.visibility = View.GONE
            button.text = item.button
            button.setOnClickListener { item.onClick() }
            itemView.setOnClickListener { item.onClick() }
        }
    }
}
