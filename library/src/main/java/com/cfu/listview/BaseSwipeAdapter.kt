package com.cfu.listview

import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter


abstract class BaseSwipeAdapter : BaseAdapter(), ISwipeItemManager, ISwipeAdapter {

    protected var mItemManger = SwipeItemManager(this)


    abstract override fun getSwipeLayoutResourceId(position: Int): Int

    abstract fun generateView(position: Int, parent: ViewGroup): View


    abstract fun fillValues(position: Int, convertView: View)

    override fun notifyDataSetChanged() {
        super.notifyDataSetChanged()
    }


    override fun getView(position: Int, convertView: View, parent: ViewGroup): View {
        var v: View? = convertView
        if (v == null) {
            v = generateView(position, parent)
        }
        mItemManger.bind(v, position)
        fillValues(position, v)
        return v
    }

    override fun openItem(position: Int) {
        mItemManger.openItem(position)
    }

    override fun closeItem(position: Int) {
        mItemManger.closeItem(position)
    }

    override fun closeAllExcept(layout: SwipeLayout) {
        mItemManger.closeAllExcept(layout)
    }

    override fun closeAllItems() {
        mItemManger.closeAllItems()
    }

    override val openItems: List<Int>
        get() = mItemManger.openItems

    override val openLayouts: List<SwipeLayout>
        get() = mItemManger.openLayouts

    override fun removeShownLayouts(layout: SwipeLayout) {
        mItemManger.removeShownLayouts(layout)
    }

    override fun isOpen(position: Int): Boolean {
        return mItemManger.isOpen(position)
    }

    override var mode: Attributes.Mode
        get() = mItemManger.mode
        set(mode) {
            mItemManger.mode = mode
        }
}