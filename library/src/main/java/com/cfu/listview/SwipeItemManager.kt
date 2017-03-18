package com.cfu.listview


import android.view.View

import java.util.ArrayList
import java.util.HashSet

class SwipeItemManager(private var adapter: ISwipeAdapter?) : ISwipeItemManager {

    override var mode: Attributes.Mode = Attributes.Mode.Single
        set(mode) {
            field = mode
            mOpenPositions.clear()
            shownLayouts.clear()
            mOpenPosition = INVALID_POSITION
        }
    val INVALID_POSITION = -1

    protected var mOpenPosition = INVALID_POSITION

    protected var mOpenPositions: MutableSet<Int> = HashSet()
    protected var shownLayouts: MutableSet<SwipeLayout> = HashSet()

    init {
        if (adapter == null)
            throw IllegalArgumentException("SwipeAdapterInterface can not be null")
    }

    fun bind(view: View, position: Int) {
        val resId = adapter!!.getSwipeLayoutResourceId(position)
        val swipeLayout = view.findViewById(resId) as SwipeLayout ?: throw IllegalStateException("can not find SwipeLayout in target view")

        if (swipeLayout.getTag(resId) == null) {
            val onLayoutListener = OnLayoutListener(position)
            val swipeMemory = SwipeMemory(position)
            swipeLayout.addSwipeListener(swipeMemory)
            swipeLayout.addOnLayoutListener(onLayoutListener)
            swipeLayout.setTag(resId, ValueBox(position, swipeMemory, onLayoutListener))
            shownLayouts.add(swipeLayout)
        } else {
            val valueBox = swipeLayout.getTag(resId) as ValueBox
            valueBox.swipeMemory.setPosition(position)
            valueBox.onLayoutListener.setPosition(position)
            valueBox.position = position
        }
    }

    override fun openItem(position: Int) {
        if (this.mode === Attributes.Mode.Multiple) {
            if (!mOpenPositions.contains(position))
                mOpenPositions.add(position)
        } else {
            mOpenPosition = position
        }
        adapter?.notifyDataSetChanged()
    }

    override fun closeItem(position: Int) {
        if (this.mode === Attributes.Mode.Multiple) {
            mOpenPositions.remove(position)
        } else {
            if (mOpenPosition == position)
                mOpenPosition = INVALID_POSITION
        }
        adapter?.notifyDataSetChanged()
    }

    override fun closeAllExcept(layout: SwipeLayout) {
        shownLayouts.filter { it !== layout }.forEach { it.close() }
    }

    override fun closeAllItems() {
        if (this.mode === Attributes.Mode.Multiple) {
            mOpenPositions.clear()
        } else {
            mOpenPosition = INVALID_POSITION
        }
        shownLayouts.forEach { s -> s.close() }
    }

    override fun removeShownLayouts(layout: SwipeLayout) {
        shownLayouts.remove(layout)
    }

    override val openItems: List<Int>
        get() {
            if (this.mode === Attributes.Mode.Multiple) {
                return ArrayList(mOpenPositions)
            } else {
                return listOf(mOpenPosition)
            }
        }

    override val openLayouts: List<SwipeLayout>
        get() = ArrayList(shownLayouts)

    override fun isOpen(position: Int): Boolean {
        if (this.mode === Attributes.Mode.Multiple) {
            return mOpenPositions.contains(position)
        } else {
            return mOpenPosition == position
        }
    }

    internal inner class ValueBox(var position: Int, var swipeMemory: SwipeMemory, var onLayoutListener: OnLayoutListener)

    internal inner class OnLayoutListener(private var position: Int) : LayoutListener {

        fun setPosition(position: Int) {
            this.position = position
        }

        override fun onLayout(v: SwipeLayout) {
            if (isOpen(position)) {
                v.open(false, false)
            } else {
                v.close(false, false)
            }
        }
    }

    internal inner class SwipeMemory(private var position: Int) : SimpleSwipeListener() {

        override fun onClose(layout: SwipeLayout) {
            if (mode === Attributes.Mode.Multiple) {
                mOpenPositions.remove(position)
            } else {
                mOpenPosition = INVALID_POSITION
            }
        }

        override fun onStartOpen(layout: SwipeLayout) {
            if (mode === Attributes.Mode.Single) {
                closeAllExcept(layout)
            }
        }

        override fun onOpen(layout: SwipeLayout) {
            if (mode === Attributes.Mode.Multiple)
                mOpenPositions.add(position)
            else {
                closeAllExcept(layout)
                mOpenPosition = position
            }
        }

        fun setPosition(position: Int) {
            this.position = position
        }
    }
}
