package com.cfu.listview

import android.view.ViewGroup
import android.support.v7.widget.RecyclerView

abstract class RecyclerSwipeAdapter<VH : RecyclerView.ViewHolder> : RecyclerView.Adapter<VH>(), ISwipeItemManager, ISwipeAdapter {

    var itemManger = SwipeItemManager(this)

    abstract override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH

    abstract override fun onBindViewHolder(viewHolder: VH, position: Int)

    //    @Override
    //    public void ISwipeAdapter.notifyDataSetChanged()  {
    //        super.notifyDataSetChanged();
    //    }



    override fun openItem(position: Int) {
        itemManger.openItem(position)
    }

    override fun closeItem(position: Int) {
        itemManger.closeItem(position)
    }

    override fun closeAllExcept(layout: SwipeLayout) {
        itemManger.closeAllExcept(layout)
    }

    override fun closeAllItems() {
        itemManger.closeAllItems()
    }

    override val openItems: List<Int>
        get() = itemManger.openItems

    override val openLayouts: List<SwipeLayout>
        get() = itemManger.openLayouts

    override fun removeShownLayouts(layout: SwipeLayout) {
        itemManger.removeShownLayouts(layout)
    }

    override fun isOpen(position: Int): Boolean {
        return itemManger.isOpen(position)
    }

    override var mode: Attributes.Mode
        get() = itemManger.mode
        set(mode) {
            itemManger.mode = mode
        }
}
