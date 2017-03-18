package com.cfu.listview

interface SwipeListener {

    fun onStartOpen(layout:SwipeLayout)

    fun onOpen(layout:SwipeLayout)

    fun onStartClose(layout:SwipeLayout)

    fun onClose(layout:SwipeLayout)

    fun onUpdate(layout:SwipeLayout, leftOffset:Int, topOffset:Int)

    fun onHandRelease(layout:SwipeLayout, xvel:Float, yvel:Float)
}