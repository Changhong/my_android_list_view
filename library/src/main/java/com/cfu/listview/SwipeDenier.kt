package com.cfu.listview

import android.view.MotionEvent

interface SwipeDenier {
     fun shouldDenySwipe(event: MotionEvent): Boolean;
}