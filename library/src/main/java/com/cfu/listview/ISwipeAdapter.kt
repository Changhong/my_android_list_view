package com.cfu.listview

interface ISwipeAdapter {

    fun getSwipeLayoutResourceId(position: Int) : Int;

    fun notifyDataSetChanged();
}