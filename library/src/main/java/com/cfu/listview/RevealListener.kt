package com.cfu.listview

import android.view.View

interface RevealListener {
    fun onReveal(child: View, edge: SwipeLayout.DragEdge, fraction:Float, distance:Int)
}