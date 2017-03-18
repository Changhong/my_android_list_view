package com.cfu.listview

interface ISwipeItemManager {

    fun openItem(position: Int)

    fun closeItem(position: Int)

    fun closeAllExcept(layout: SwipeLayout)

    fun closeAllItems()

    val openItems: List<Int>

    val openLayouts: List<SwipeLayout>

    fun removeShownLayouts(layout: SwipeLayout)

    fun isOpen(position: Int): Boolean

    var mode: Attributes.Mode
}
