package com.orbit.iptv.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.orbit.iptv.R
import com.orbit.iptv.data.Channel
import com.orbit.iptv.databinding.ItemCategoryBinding
import com.orbit.iptv.databinding.ItemChannelBinding

class CategoryAdapter(
    private val horizontal: Boolean,
    private val onSelect: (position: Int, name: String) -> Unit,
) : RecyclerView.Adapter<CategoryAdapter.VH>() {

    class VH(val b: ItemCategoryBinding) : RecyclerView.ViewHolder(b.root)

    private var items: List<String> = emptyList()
    private var selected = 0

    /** Ne recharge la liste que si elle a changé, pour garder le focus télécommande. */
    fun submit(newItems: List<String>, newSelected: Int) {
        if (newItems == items) {
            if (newSelected != selected) {
                val old = selected
                selected = newSelected
                notifyItemChanged(old)
                notifyItemChanged(newSelected)
            }
            return
        }
        items = newItems
        selected = newSelected
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        if (!horizontal) {
            val m = (3 * parent.resources.displayMetrics.density).toInt()
            b.root.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(m, m, m, m) }
        }
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.b.root.text = items[position]
        holder.b.root.isSelected = position == selected
        holder.b.root.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p == RecyclerView.NO_POSITION) return@setOnClickListener
            val old = selected
            selected = p
            notifyItemChanged(old)
            notifyItemChanged(p)
            onSelect(p, items[p])
        }
    }

    override fun getItemCount() = items.size
}

class ChannelAdapter(
    private val onClick: (position: Int) -> Unit,
    private val onLongClick: (position: Int) -> Unit,
) : RecyclerView.Adapter<ChannelAdapter.VH>() {

    class VH(val b: ItemChannelBinding) : RecyclerView.ViewHolder(b.root)

    private var items: List<Channel> = emptyList()
    private var favorites: Set<String> = emptySet()

    fun submit(newItems: List<Channel>, favs: Set<String>) {
        items = newItems
        favorites = favs
        notifyDataSetChanged()
    }

    fun updateFavorites(favs: Set<String>, position: Int) {
        favorites = favs
        if (position in items.indices) notifyItemChanged(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ch = items[position]
        holder.b.name.text = ch.name
        holder.b.group.text = ch.group
        holder.b.fav.visibility = if (ch.id in favorites) android.view.View.VISIBLE else android.view.View.GONE
        if (ch.logo.isNullOrBlank()) {
            holder.b.logo.setImageResource(R.drawable.ic_tv)
        } else {
            holder.b.logo.load(ch.logo) {
                placeholder(R.drawable.ic_tv)
                error(R.drawable.ic_tv)
                size(160)
            }
        }
        holder.b.root.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(p)
        }
        holder.b.root.setOnLongClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onLongClick(p)
            true
        }
    }

    override fun getItemCount() = items.size
}
