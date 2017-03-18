package com.cfu.listview

import android.content.Context
import android.graphics.Rect
import android.support.v4.view.GravityCompat
import android.support.v4.view.ViewCompat
import android.support.v4.widget.ViewDragHelper
import android.util.AttributeSet
import android.view.*
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.FrameLayout
import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedHashMap


class SwipeLayout constructor(context: Context, attrs: AttributeSet): FrameLayout(context, attrs, 0) {

    enum class DragEdge { Left, Top, Right, Bottom }

    enum class ShowMode { LayDown, PullOut }

    enum class Status { Middle, Open, Close }

    companion object {

        private val DRAG_LEFT = 1
        private val DRAG_RIGHT = 2
        private val DRAG_TOP = 4
        private val DRAG_BOTTOM = 8

        private val DefaultDragEdge = DragEdge.Right
    }

    var isSwipeEnabled = true
    private val swipesEnabled = booleanArrayOf(true, true, true, true)

    private var isBeingDragged:Boolean = false
    
    private val gestureDetector = GestureDetector(getContext(), SwipeDetector())

    private val touchSlop: Int
    private var currentDragEdge: DragEdge = DefaultDragEdge;
    private val dragHelper:ViewDragHelper

    private var eventCounter = 0
    
    private fun setCurrentDragEdge(dragEdge: DragEdge) {
        currentDragEdge = dragEdge
        updateBottomViews()
    }

    private var dragDistance = 0
    private val dragEdges = LinkedHashMap<DragEdge, View?>()

    private var _showMode: ShowMode? = null
    public var showMode: ShowMode?
        get() = _showMode
        set(value) {
            _showMode = value
            requestLayout()
        }


    private var hitSurfaceRect:Rect? = null

    private var _isClickToClose = false
    var isClickToClose: Boolean
        get() = _isClickToClose
        set(value) {
            _isClickToClose = value
        }

    private val edgeSwipesOffset = FloatArray(4)

    private val swipeListeners = ArrayList<SwipeListener>()
    private val revealListeners = HashMap<View, ArrayList<RevealListener>>()
    private val showEntirely = HashMap<View, Boolean>()

    private val onLayoutListeners:MutableList<LayoutListener> = ArrayList<LayoutListener>()

    private var doubleClickListener: DoubleClickListener? = null

    private val swipeDeniers = ArrayList<SwipeDenier>()

    private var sX = -1f
    private var sY = -1f



    var isLeftSwipeEnabled:Boolean
        get() {
            val bottomView = dragEdges[DragEdge.Left]
            return bottomView != null && bottomView.parent === this
                    && bottomView !== surfaceView && swipesEnabled[DragEdge.Left.ordinal]
        }
        set(leftSwipeEnabled) {
            this.swipesEnabled[DragEdge.Left.ordinal] = leftSwipeEnabled
        }

    var isRightSwipeEnabled:Boolean
        get() {
            val bottomView = dragEdges[DragEdge.Right]
            return bottomView != null && bottomView.parent === this
                    && bottomView !== surfaceView && swipesEnabled[DragEdge.Right.ordinal]
        }
        set(rightSwipeEnabled) {
            this.swipesEnabled[DragEdge.Right.ordinal] = rightSwipeEnabled
        }

    var isTopSwipeEnabled:Boolean
        get() {
            val view = dragEdges[DragEdge.Top]
            return view != null && view.parent === this && view !== surfaceView && swipesEnabled[DragEdge.Top.ordinal]
        }
        set(topSwipeEnabled) {
            this.swipesEnabled[DragEdge.Top.ordinal] = topSwipeEnabled
        }

    var isBottomSwipeEnabled:Boolean
        get() {
            val view = dragEdges[DragEdge.Bottom]
            return view != null && view.parent === this && view !== surfaceView && swipesEnabled[DragEdge.Bottom.ordinal]
        }
        set(bottomSwipeEnabled) {
            this.swipesEnabled[DragEdge.Bottom.ordinal] = bottomSwipeEnabled
        }

    fun addSwipeDenier(denier: SwipeDenier) {
        swipeDeniers.add(denier)
    }

    fun removeSwipeDenier(denier: SwipeDenier) {
        swipeDeniers.remove(denier)
    }

    fun removeAllSwipeDeniers() {
        swipeDeniers.clear()
    }

    fun setOnDoubleClickListener(listener: DoubleClickListener) {
        doubleClickListener = listener
    }


    fun addOnLayoutListener(listener: LayoutListener) {
        onLayoutListeners.add(listener)
    }

    fun removeOnLayoutListener(listener: LayoutListener) {
        onLayoutListeners.remove(listener)
    }

    internal var clickListener:View.OnClickListener? = null

    override fun setOnClickListener(l:View.OnClickListener?) {
        super.setOnClickListener(l)
        clickListener = l
    }

    internal var longClickListener:View.OnLongClickListener? = null

    override fun setOnLongClickListener(l:View.OnLongClickListener?) {
        super.setOnLongClickListener(l)
        longClickListener = l
    }


    private val viewBoundCache = HashMap<View, Rect>()//save all children's bound, restore in onLayout

    var willOpenPercentAfterClose = 0.25f
    var willOpenPercentAfterOpen = 0.75f

    val surfaceView: View?
        get() {
            return if (childCount == 0) null else getChildAt(childCount - 1)
        }

    val bottomViews: List<View>
        get() {
            return DragEdge.values().filter { dragEdges[it] != null }.mapTo(ArrayList<View>()) { dragEdges[it]!! }
        }

    val currentBottomView: View?
        get() {

            val bottoms = bottomViews
            return if (currentDragEdge.ordinal < bottoms.size) bottoms[currentDragEdge.ordinal] else null
        }

    val openStatus: Status
        get() {
            val surfaceView = surfaceView ?: return Status.Close
            val surfaceLeft = surfaceView.left
            val surfaceTop = surfaceView.top
            if (surfaceLeft == paddingLeft && surfaceTop == paddingTop) return Status.Close

            if (surfaceLeft == (paddingLeft - dragDistance) || surfaceLeft == (paddingLeft + dragDistance) || surfaceTop == (paddingTop - dragDistance) || surfaceTop == (paddingTop + dragDistance))
                return Status.Open

            return Status.Middle
        }

    private val currentOffset:Float
        get() {
            if (currentDragEdge == null) return 0f
            return edgeSwipesOffset[currentDragEdge.ordinal]
        }

    val dragEdge: DragEdge
        get() {
            return currentDragEdge
        }


    val dragEdgeMap: Map<DragEdge, View?>
        get() {
            return dragEdges
        }

    private val dragHelperCallback = object : ViewDragHelper.Callback() {
        internal var isCloseBeforeDrag = true

        override fun clampViewPositionHorizontal(child: View?, left: Int, dx: Int): Int {
            if (child === surfaceView) {
                when (currentDragEdge) {
                    SwipeLayout.DragEdge.Top, SwipeLayout.DragEdge.Bottom -> return paddingLeft

                    SwipeLayout.DragEdge.Left -> {
                        if (left < paddingLeft) return paddingLeft
                        if (left > paddingLeft + dragDistance) return paddingLeft + dragDistance
                    }

                    SwipeLayout.DragEdge.Right -> {
                        if (left > paddingLeft) return paddingLeft
                        if (left < paddingLeft - dragDistance) return paddingLeft - dragDistance
                    }
                }
            } else if (currentBottomView === child) {
                when (currentDragEdge) {
                    SwipeLayout.DragEdge.Top, SwipeLayout.DragEdge.Bottom -> return paddingLeft

                    SwipeLayout.DragEdge.Left -> if (showMode == ShowMode.PullOut) {
                        if (left > paddingLeft) return paddingLeft
                    }

                    SwipeLayout.DragEdge.Right -> if (showMode == ShowMode.PullOut) {
                        if (left < measuredWidth - dragDistance) {
                            return measuredWidth - dragDistance
                        }
                    }
                }
            }
            return left
        }

        override fun clampViewPositionVertical(child: View?, top: Int, dy: Int): Int {
            if (child === surfaceView) {
                when (currentDragEdge) {
                    SwipeLayout.DragEdge.Left, SwipeLayout.DragEdge.Right -> return paddingTop
                    SwipeLayout.DragEdge.Top -> {
                        if (top < paddingTop) return paddingTop
                        if (top > paddingTop + dragDistance)
                            return paddingTop + dragDistance
                    }
                    SwipeLayout.DragEdge.Bottom -> {
                        if (top < paddingTop - dragDistance) {
                            return paddingTop - dragDistance
                        }
                        if (top > paddingTop) {
                            return paddingTop
                        }
                    }
                }
            } else {
                val surfaceView = surfaceView
                val surfaceViewTop = surfaceView?.top ?: 0
                when (currentDragEdge) {
                    SwipeLayout.DragEdge.Left, SwipeLayout.DragEdge.Right -> return paddingTop

                    SwipeLayout.DragEdge.Top -> if (showMode == ShowMode.PullOut) {
                        if (top > paddingTop) return paddingTop
                    } else {
                        if (surfaceViewTop + dy < paddingTop)
                            return paddingTop
                        if (surfaceViewTop + dy > paddingTop + dragDistance)
                            return paddingTop + dragDistance
                    }

                    SwipeLayout.DragEdge.Bottom -> if (showMode == ShowMode.PullOut) {
                        if (top < measuredHeight - dragDistance)
                            return measuredHeight - dragDistance
                    } else {
                        if (surfaceViewTop + dy >= paddingTop)
                            return paddingTop
                        if (surfaceViewTop + dy <= paddingTop - dragDistance)
                            return paddingTop - dragDistance
                    }
                }
            }
            return top
        }


        override fun tryCaptureView(child: View?, pointerId: Int): Boolean {
            val result = child === surfaceView || bottomViews.contains(child)
            if (result) {
                isCloseBeforeDrag = openStatus == Status.Close
            }
            return result
        }

        override fun getViewHorizontalDragRange(child: View?): Int {
            return dragDistance
        }

        override fun getViewVerticalDragRange(child: View?): Int {
            return dragDistance
        }

        override fun onViewReleased(releasedChild: View?, xvel: Float, yvel: Float) {
            super.onViewReleased(releasedChild, xvel, yvel)
            processHandRelease(xvel, yvel, isCloseBeforeDrag)
            for (l in swipeListeners) {
                l.onHandRelease(this@SwipeLayout, xvel, yvel)
            }

            invalidate()
        }

        override fun onViewPositionChanged(changedView:View?, left:Int, top:Int, dx:Int, dy:Int) {
            val surfaceView = surfaceView ?: return
            val currentBottomView = currentBottomView
            val evLeft = surfaceView.left
            val evRight = surfaceView.right
            val evTop = surfaceView.top
            val evBottom = surfaceView.bottom
            if (changedView === surfaceView) {

                if (showMode == ShowMode.PullOut && currentBottomView != null)
                {
                    if (currentDragEdge == DragEdge.Left || currentDragEdge == DragEdge.Right) {
                        currentBottomView.offsetLeftAndRight(dx)
                    } else {
                        currentBottomView.offsetTopAndBottom(dy)
                    }
                }
            } else if (bottomViews.contains(changedView)) {

                if (showMode == ShowMode.PullOut)
                {
                    surfaceView.offsetLeftAndRight(dx)
                    surfaceView.offsetTopAndBottom(dy)
                } else
                {
                    val rect = computeBottomLayDown(currentDragEdge)
                    currentBottomView?.layout(rect.left, rect.top, rect.right, rect.bottom)

                    var newLeft = surfaceView.left + dx
                    var newTop = surfaceView.top + dy

                    if (currentDragEdge == DragEdge.Left && newLeft < paddingLeft)
                        newLeft = paddingLeft
                    else if (currentDragEdge == DragEdge.Right && newLeft > paddingLeft)
                        newLeft = paddingLeft
                    else if (currentDragEdge == DragEdge.Top && newTop < paddingTop)
                        newTop = paddingTop
                    else if (currentDragEdge == DragEdge.Bottom && newTop > paddingTop)
                        newTop = paddingTop

                    surfaceView.layout(newLeft, newTop, newLeft + measuredWidth, newTop + measuredHeight)
                }
            }

            dispatchRevealEvent(evLeft, evTop, evRight, evBottom)

            dispatchSwipeEvent(evLeft, evTop, dx, dy)

            invalidate()

            captureChildrenBound()
        }

    }


    init {
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        dragHelper = ViewDragHelper.create(this, dragHelperCallback)

        val a = context.obtainStyledAttributes(attrs, R.styleable.SwipeLayout)
        val dragEdgeChoices = a.getInt(R.styleable.SwipeLayout_drag_edge, DRAG_RIGHT)
        edgeSwipesOffset[DragEdge.Left.ordinal] = a.getDimension(R.styleable.SwipeLayout_leftEdgeSwipeOffset, 0f)
        edgeSwipesOffset[DragEdge.Right.ordinal] = a.getDimension(R.styleable.SwipeLayout_rightEdgeSwipeOffset, 0f)
        edgeSwipesOffset[DragEdge.Top.ordinal] = a.getDimension(R.styleable.SwipeLayout_topEdgeSwipeOffset, 0f)
        edgeSwipesOffset[DragEdge.Bottom.ordinal] = a.getDimension(R.styleable.SwipeLayout_bottomEdgeSwipeOffset, 0f)

        isClickToClose = a.getBoolean(R.styleable.SwipeLayout_clickToClose, isClickToClose)


        if ((dragEdgeChoices and DRAG_LEFT) == DRAG_LEFT)
        {
            dragEdges.put(DragEdge.Left, null)
        }
        if ((dragEdgeChoices and DRAG_TOP) == DRAG_TOP)
        {
            dragEdges.put(DragEdge.Top, null)
        }
        if ((dragEdgeChoices and DRAG_RIGHT) == DRAG_RIGHT)
        {
            dragEdges.put(DragEdge.Right, null)
        }
        if ((dragEdgeChoices and DRAG_BOTTOM) == DRAG_BOTTOM)
        {
            dragEdges.put(DragEdge.Bottom, null)
        }
        val ordinal = a.getInt(R.styleable.SwipeLayout_show_mode, ShowMode.PullOut.ordinal)
        showMode = ShowMode.values()[ordinal]
        a.recycle()
    }

    fun addSwipeListener(listener: SwipeListener) {
        swipeListeners.add(listener)
    }

    fun removeSwipeListener(listener: SwipeListener) {
        swipeListeners.remove(listener);
    }

    fun removeAllSwipeListener() {
        swipeListeners.clear();
    }

    fun addRevealListener(childId: Int, listener: RevealListener) {
        val child = findViewById(childId) ?: throw IllegalArgumentException("Child does not belong to SwipeListener.")
        if (!showEntirely.containsKey(child)) {
            showEntirely.put(child, false)
        }
        if (revealListeners[child] == null) {
            revealListeners.put(child, ArrayList<RevealListener>())
        }
        revealListeners[child]!!.add(listener)
    }

    fun addRevealListener(childIds: IntArray, listener: RevealListener) {
        for (i in childIds) {
            addRevealListener(i, listener)
        }
    }

    fun removeRevealListener(childId: Int, listener: RevealListener) {
        val child = findViewById(childId) ?: return

        showEntirely.remove(child)
        if (revealListeners.containsKey(child)) {
            revealListeners[child]!!.remove(listener)
        }
    }

    fun removeAllRevealListeners(childId: Int) {
        val child = findViewById(childId)
        if (child != null) {
            revealListeners.remove(child)
            showEntirely.remove(child)
        }
    }



    fun clearDragEdge() {
        dragEdges.clear()
    }

    fun setDrag(dragEdge: DragEdge, childId:Int) {
        clearDragEdge()
        addDrag(dragEdge, childId)
    }

    fun setDrag(dragEdge: DragEdge, child:View) {
        clearDragEdge()
        addDrag(dragEdge, child)
    }

    fun addDrag(dragEdge: DragEdge, childId:Int) {
        addDrag(dragEdge, findViewById(childId), null)
    }

    @JvmOverloads  fun addDrag(dragEdge: DragEdge, child:View?, params:ViewGroup.LayoutParams? = null) {
        var params = params
        if (child == null) return

        if (params == null)
        {
            params = generateDefaultLayoutParams()
        }
        if (!checkLayoutParams(params))
        {
            params = generateLayoutParams(params)
        }
        var gravity = -1
        when (dragEdge) {
            SwipeLayout.DragEdge.Left -> gravity = Gravity.LEFT
            SwipeLayout.DragEdge.Right -> gravity = Gravity.RIGHT
            SwipeLayout.DragEdge.Top -> gravity = Gravity.TOP
            SwipeLayout.DragEdge.Bottom -> gravity = Gravity.BOTTOM
        }
        if (params is FrameLayout.LayoutParams)
        {
            params.gravity = gravity
        }
        addView(child, 0, params)
    }


    override fun addView(child:View?, index:Int, params:ViewGroup.LayoutParams?) {
        if (child == null) return
        var gravity = Gravity.NO_GRAVITY
        if (params != null) {
            try
            {
                gravity = params.javaClass.getField("gravity").get(params) as Int
            }
            catch (e:Exception) {
                e.printStackTrace()
            }

        }

        if (gravity > 0)
        {
            gravity = GravityCompat.getAbsoluteGravity(gravity, ViewCompat.getLayoutDirection(this))

            if ((gravity and Gravity.LEFT) == Gravity.LEFT)
            {
                dragEdges.put(DragEdge.Left, child)
            }
            if ((gravity and Gravity.RIGHT) == Gravity.RIGHT)
            {
                dragEdges.put(DragEdge.Right, child)
            }
            if ((gravity and Gravity.TOP) == Gravity.TOP)
            {
                dragEdges.put(DragEdge.Top, child)
            }
            if ((gravity and Gravity.BOTTOM) == Gravity.BOTTOM)
            {
                dragEdges.put(DragEdge.Bottom, child)
            }
        }
        else
        {
            for (entry in dragEdges.entries)
            {
                if (entry.value == null)
                {
                    dragEdges.put(entry.key, child)
                    break
                }
            }
        }
        if (child.getParent() === this)
        {
            return
        }
        super.addView(child, index, params)
    }

    protected fun processHandRelease(xvel:Float, yvel:Float, isCloseBeforeDragged:Boolean) {
        val minVelocity = dragHelper.getMinVelocity()
        val surfaceView = surfaceView
        val currentDragEdge = currentDragEdge
        if (surfaceView == null)
        {
            return
        }
        val willOpenPercent = (if (isCloseBeforeDragged) willOpenPercentAfterClose else willOpenPercentAfterOpen)
        if (currentDragEdge == DragEdge.Left)
        {
            if (xvel > minVelocity)
                open()
            else if (xvel < -minVelocity)
                close()
            else
            {
                val openPercent = 1f * surfaceView.left / dragDistance
                if (openPercent > willOpenPercent)
                    open()
                else
                    close()
            }
        }
        else if (currentDragEdge == DragEdge.Right)
        {
            if (xvel > minVelocity)
                close()
            else if (xvel < -minVelocity)
                open()
            else
            {
                val openPercent = 1f * (-surfaceView.left) / dragDistance
                if (openPercent > willOpenPercent)
                    open()
                else
                    close()
            }
        }
        else if (currentDragEdge == DragEdge.Top)
        {
            if (yvel > minVelocity)
                open()
            else if (yvel < -minVelocity)
                close()
            else
            {
                val openPercent = 1f * surfaceView.top / dragDistance
                if (openPercent > willOpenPercent)
                    open()
                else
                    close()
            }
        }
        else if (currentDragEdge == DragEdge.Bottom)
        {
            if (yvel > minVelocity)
                close()
            else if (yvel < -minVelocity)
                open()
            else
            {
                val openPercent = 1f * (-surfaceView.top) / dragDistance
                if (openPercent > willOpenPercent)
                    open()
                else
                    close()
            }
        }
    }

    @JvmOverloads fun open(smooth:Boolean = true, notify:Boolean = true) {
        val surface = surfaceView
        val bottom = currentBottomView
        if (surface == null)
        {
            return
        }
        val dx:Int
        val dy:Int
        val rect = computeSurfaceLayoutArea(true)
        if (smooth)
        {
            dragHelper.smoothSlideViewTo(surface, rect.left, rect.top)
        }
        else
        {
            dx = rect.left - surface!!.getLeft()
            dy = rect.top - surface!!.getTop()
            surface!!.layout(rect.left, rect.top, rect.right, rect.bottom)
            if (showMode == ShowMode.PullOut)
            {
                val bRect = computeBottomLayoutAreaViaSurface(ShowMode.PullOut, rect)
                if (bottom != null)
                {
                    bottom!!.layout(bRect.left, bRect.top, bRect.right, bRect.bottom)
                }
            }
            if (notify)
            {
                dispatchRevealEvent(rect.left, rect.top, rect.right, rect.bottom)
                dispatchSwipeEvent(rect.left, rect.top, dx, dy)
            }
            else
            {
                safeBottomView()
            }
        }
        invalidate()
    }

    @JvmOverloads fun toggle(smooth:Boolean = true) {
        if (openStatus == Status.Open)
            close(smooth)
        else if (openStatus == Status.Close) open(smooth)
    }


    
    fun open(edge: DragEdge) {
        setCurrentDragEdge(edge)
        open(true, true)
    }

    fun open(smooth:Boolean, edge: DragEdge) {
        setCurrentDragEdge(edge)
        open(smooth, true)
    }

    fun open(smooth:Boolean, notify:Boolean, edge: DragEdge) {
        setCurrentDragEdge(edge)
        open(smooth, notify)
    }
    
    @JvmOverloads  fun close(smooth:Boolean = true, notify:Boolean = true) {
        val surface = surfaceView
        if (surface == null)
        {
            return
        }
        val dx:Int
        val dy:Int
        if (smooth)
            dragHelper.smoothSlideViewTo(surfaceView, getPaddingLeft(), getPaddingTop())
        else
        {
            val rect = computeSurfaceLayoutArea(false)
            dx = rect.left - surface.left
            dy = rect.top - surface.top
            surface.layout(rect.left, rect.top, rect.right, rect.bottom)
            if (notify)
            {
                dispatchRevealEvent(rect.left, rect.top, rect.right, rect.bottom)
                dispatchSwipeEvent(rect.left, rect.top, dx, dy)
            }
            else
            {
                safeBottomView()
            }
        }
        invalidate()
    }

    private fun computeBottomLayDown(dragEdge: DragEdge): Rect {
        var bl = paddingLeft
        var bt = paddingTop
        val br:Int
        val bb:Int
        if (dragEdge == DragEdge.Right) {
            bl = measuredWidth - dragDistance
        } else if (dragEdge == DragEdge.Bottom) {
            bt = measuredHeight - dragDistance
        }
        
        if (dragEdge == DragEdge.Left || dragEdge == DragEdge.Right) {
            br = bl + dragDistance
            bb = bt + measuredHeight
        } else {
            br = bl + measuredWidth
            bb = bt + dragDistance
        }
        return Rect(bl, bt, br, bb)
    }

    protected fun dispatchSwipeEvent(surfaceLeft:Int, surfaceTop:Int, dx:Int, dy:Int) {
        val edge = dragEdge
        var open = true
        if (edge == DragEdge.Left)
        {
            if (dx < 0) open = false
        }
        else if (edge == DragEdge.Right)
        {
            if (dx > 0) open = false
        }
        else if (edge == DragEdge.Top)
        {
            if (dy < 0) open = false
        }
        else if (edge == DragEdge.Bottom)
        {
            if (dy > 0) open = false
        }

        dispatchSwipeEvent(surfaceLeft, surfaceTop, open)
    }

    protected fun dispatchSwipeEvent(surfaceLeft:Int, surfaceTop:Int, open:Boolean) {
        safeBottomView()
        val status = openStatus

        if (!swipeListeners.isEmpty())
        {
            eventCounter++
            for (l in swipeListeners)
            {
                if (eventCounter == 1)
                {
                    if (open)
                    {
                        l.onStartOpen(this)
                    }
                    else
                    {
                        l.onStartClose(this)
                    }
                }
                l.onUpdate(this@SwipeLayout, surfaceLeft - paddingLeft, surfaceTop - paddingTop)
            }

            if (status == Status.Close)
            {
                for (l in swipeListeners)
                {
                    l.onClose(this@SwipeLayout)
                }
                eventCounter = 0
            }

            if (status == Status.Open)
            {
                val currentBottomView = currentBottomView
                if (currentBottomView != null)
                {
                    currentBottomView.isEnabled = true
                }
                for (l in swipeListeners)
                {
                    l.onOpen(this@SwipeLayout)
                }
                eventCounter = 0
            }
        }
    }

    protected fun dispatchRevealEvent(surfaceLeft:Int, surfaceTop:Int, surfaceRight:Int, surfaceBottom:Int) {
        if (revealListeners.isEmpty()) return
        for (entry in revealListeners.entries)
        {
            val child = entry.key
            val rect = getRelativePosition(child)
            if (isViewShowing(child, rect, currentDragEdge, surfaceLeft, surfaceTop,
                    surfaceRight, surfaceBottom))
            {
                showEntirely.put(child, false)
                var distance = 0
                var fraction = 0f
                if (showMode == ShowMode.LayDown)
                {
                    when (currentDragEdge) {
                        SwipeLayout.DragEdge.Left -> {
                            distance = rect.left - surfaceLeft
                            fraction = distance / child.getWidth().toFloat()
                        }
                        SwipeLayout.DragEdge.Right -> {
                            distance = rect.right - surfaceRight
                            fraction = distance / child.getWidth().toFloat()
                        }
                        SwipeLayout.DragEdge.Top -> {
                            distance = rect.top - surfaceTop
                            fraction = distance / child.getHeight().toFloat()
                        }
                        SwipeLayout.DragEdge.Bottom -> {
                            distance = rect.bottom - surfaceBottom
                            fraction = distance / child.getHeight().toFloat()
                        }
                    }
                }
                else if (showMode == ShowMode.PullOut)
                {
                    when (currentDragEdge) {
                        SwipeLayout.DragEdge.Left -> {
                            distance = rect.right - getPaddingLeft()
                            fraction = distance / child.getWidth().toFloat()
                        }
                        SwipeLayout.DragEdge.Right -> {
                            distance = rect.left - getWidth()
                            fraction = distance / child.getWidth().toFloat()
                        }
                        SwipeLayout.DragEdge.Top -> {
                            distance = rect.bottom - getPaddingTop()
                            fraction = distance / child.getHeight().toFloat()
                        }
                        SwipeLayout.DragEdge.Bottom -> {
                            distance = rect.top - getHeight()
                            fraction = distance / child.getHeight().toFloat()
                        }
                    }
                }

                for (l in entry.value)
                {
                    l.onReveal(child, currentDragEdge, Math.abs(fraction), distance)
                    if (Math.abs(fraction) == 1f)
                    {
                        showEntirely.put(child, true)
                    }
                }
            }

            if (isViewTotallyFirstShowed(child, rect, currentDragEdge, surfaceLeft, surfaceTop, surfaceRight, surfaceBottom)) {
                showEntirely.put(child, true)
                for (l in entry.value)
                {
                    if (currentDragEdge == DragEdge.Left || currentDragEdge == DragEdge.Right)
                        l.onReveal(child, currentDragEdge, 1f, child.width)
                    else
                        l.onReveal(child, currentDragEdge, 1f, child.height)
                }
            }

        }
    }

    protected fun getRelativePosition(child:View):Rect {
        var t = child
        val r = Rect(t.left, t.top, 0, 0)
        while (t.parent != null && t !== rootView)
        {
            t = t.parent as View
            if (t === this) break
            r.left += t.left
            r.top += t.top
        }
        r.right = r.left + child.measuredWidth
        r.bottom = r.top + child.measuredHeight
        return r
    }

    protected fun isViewShowing(child:View, relativePosition:Rect, availableEdge: DragEdge, surfaceLeft:Int,
                                surfaceTop:Int, surfaceRight:Int, surfaceBottom:Int):Boolean {
        val childLeft = relativePosition.left
        val childRight = relativePosition.right
        val childTop = relativePosition.top
        val childBottom = relativePosition.bottom
        if (showMode == ShowMode.LayDown)
        {
            when (availableEdge) {
                SwipeLayout.DragEdge.Right -> if (surfaceRight > childLeft && surfaceRight <= childRight)
                {
                    return true
                }
                SwipeLayout.DragEdge.Left -> if (surfaceLeft < childRight && surfaceLeft >= childLeft)
                {
                    return true
                }
                SwipeLayout.DragEdge.Top -> if (surfaceTop >= childTop && surfaceTop < childBottom)
                {
                    return true
                }
                SwipeLayout.DragEdge.Bottom -> if (surfaceBottom > childTop && surfaceBottom <= childBottom)
                {
                    return true
                }
            }
        }
        else if (showMode == ShowMode.PullOut)
        {
            when (availableEdge) {
                SwipeLayout.DragEdge.Right -> if (childLeft <= getWidth() && childRight > getWidth()) return true
                SwipeLayout.DragEdge.Left -> if (childRight >= getPaddingLeft() && childLeft < getPaddingLeft()) return true
                SwipeLayout.DragEdge.Top -> if (childTop < getPaddingTop() && childBottom >= getPaddingTop()) return true
                SwipeLayout.DragEdge.Bottom -> if (childTop < getHeight() && childTop >= getPaddingTop()) return true
            }
        }
        return false
    }


    protected fun isViewTotallyFirstShowed(child:View, relativePosition:Rect, edge: DragEdge, surfaceLeft:Int,
                                           surfaceTop:Int, surfaceRight:Int, surfaceBottom:Int):Boolean {
        if (showEntirely[child] == true) return false
        val childLeft = relativePosition.left
        val childRight = relativePosition.right
        val childTop = relativePosition.top
        val childBottom = relativePosition.bottom
        var r = false
        if (showMode == ShowMode.LayDown)
        {
            if ((edge == DragEdge.Right && surfaceRight <= childLeft)
                    || (edge == DragEdge.Left && surfaceLeft >= childRight)
                    || (edge == DragEdge.Top && surfaceTop >= childBottom)
                    || (edge == DragEdge.Bottom && surfaceBottom <= childTop))
                r = true
        }
        else if (showMode == ShowMode.PullOut)
        {
            if ((edge == DragEdge.Right && childRight <= getWidth())
                    || (edge == DragEdge.Left && childLeft >= getPaddingLeft())
                    || (edge == DragEdge.Top && childTop >= getPaddingTop())
                    || (edge == DragEdge.Bottom && childBottom <= getHeight()))
                r = true
        }
        return r
    }

    private fun computeSurfaceLayoutArea(open:Boolean):Rect {
        var l = paddingLeft
        var t = paddingTop
        if (open)
        {
            if (currentDragEdge == DragEdge.Left)
                l = paddingLeft + dragDistance
            else if (currentDragEdge == DragEdge.Right)
                l = paddingLeft - dragDistance
            else if (currentDragEdge == DragEdge.Top)
                t = paddingTop + dragDistance
            else
                t = paddingTop - dragDistance
        }
        return Rect(l, t, l + measuredWidth, t + measuredHeight)
    }

    private fun safeBottomView() {
        val status = openStatus
        val bottoms = bottomViews

        if (status == Status.Close)
        {
            for (bottom in bottoms)
            {
                if (bottom.visibility != View.INVISIBLE)
                {
                    bottom.visibility = View.INVISIBLE
                }
            }
        }
        else
        {
            val currentBottomView = currentBottomView
            if (currentBottomView != null && currentBottomView.visibility != View.VISIBLE)
            {
                currentBottomView.visibility = View.VISIBLE
            }
        }
    }

    private fun computeBottomLayoutAreaViaSurface(mode: ShowMode, surfaceArea:Rect):Rect {
        val rect = surfaceArea
        val bottomView = currentBottomView

        var bl = rect.left
        var bt = rect.top
        var br = rect.right
        var bb = rect.bottom
        if (mode == ShowMode.PullOut)
        {
            if (currentDragEdge == DragEdge.Left)
                bl = rect.left - dragDistance
            else if (currentDragEdge == DragEdge.Right)
                bl = rect.right
            else if (currentDragEdge == DragEdge.Top)
                bt = rect.top - dragDistance
            else
                bt = rect.bottom

            if (currentDragEdge == DragEdge.Left || currentDragEdge == DragEdge.Right)
            {
                bb = rect.bottom
                br = bl + (if (bottomView == null) 0 else bottomView!!.getMeasuredWidth())
            }
            else
            {
                bb = bt + (if (bottomView == null) 0 else bottomView!!.getMeasuredHeight())
                br = rect.right
            }
        }
        else if (mode == ShowMode.LayDown)
        {
            if (currentDragEdge == DragEdge.Left)
                br = bl + dragDistance
            else if (currentDragEdge == DragEdge.Right)
                bl = br - dragDistance
            else if (currentDragEdge == DragEdge.Top)
                bb = bt + dragDistance
            else
                bt = bb - dragDistance

        }
        return Rect(bl, bt, br, bb)

    }

    private fun captureChildrenBound() {
        val currentBottomView = currentBottomView
        if (openStatus == Status.Close)
        {
            viewBoundCache.remove(currentBottomView)
            return
        }

        val views = arrayOf<View>(surfaceView!!, currentBottomView!!)
        for (child in views)
        {
            var rect:Rect? = viewBoundCache.get(child)
            if (rect == null)
            {
                rect = Rect()
                viewBoundCache.put(child, rect)
            }
            rect.left = child.left
            rect.top = child.top
            rect.right = child.right
            rect.bottom = child.bottom
        }
    }

    private fun updateBottomViews() {
        val currentBottomView = currentBottomView
        if (currentBottomView != null)
        {
            if (currentDragEdge == DragEdge.Left || currentDragEdge == DragEdge.Right)
            {
                dragDistance = currentBottomView.measuredWidth - dp2px(currentOffset)
            }
            else
            {
                dragDistance = currentBottomView.measuredHeight - dp2px(currentOffset)
            }
        }

        if (showMode == ShowMode.PullOut)
        {
            layoutPullOut()
        }
        else if (showMode == ShowMode.LayDown)
        {
            layoutLayDown()
        }

        safeBottomView()
    }

    private fun dp2px(dp:Float):Int {
        return (dp * context.resources.displayMetrics.density + 0.5f).toInt()
    }

    internal fun layoutPullOut() {
        val surfaceView = surfaceView
        var surfaceRect:Rect? = viewBoundCache.get(surfaceView)
        if (surfaceRect == null) surfaceRect = computeSurfaceLayoutArea(false)
        if (surfaceView != null)
        {
            surfaceView.layout(surfaceRect.left, surfaceRect.top, surfaceRect.right, surfaceRect.bottom)
            bringChildToFront(surfaceView)
        }
        val currentBottomView = currentBottomView
        var bottomViewRect:Rect? = viewBoundCache.get(currentBottomView)
        if (bottomViewRect == null)
            bottomViewRect = computeBottomLayoutAreaViaSurface(ShowMode.PullOut, surfaceRect)
        if (currentBottomView != null)
        {
            currentBottomView.layout(bottomViewRect.left, bottomViewRect.top, bottomViewRect.right, bottomViewRect.bottom)
        }
    }

    internal fun layoutLayDown() {
        val surfaceView = surfaceView
        var surfaceRect:Rect? = viewBoundCache.get(surfaceView)
        if (surfaceRect == null) surfaceRect = computeSurfaceLayoutArea(false)
        if (surfaceView != null)
        {
            surfaceView.layout(surfaceRect.left, surfaceRect.top, surfaceRect.right, surfaceRect.bottom)
            bringChildToFront(surfaceView)
        }
        val currentBottomView = currentBottomView
        var bottomViewRect:Rect? = viewBoundCache.get(currentBottomView)
        if (bottomViewRect == null)
            bottomViewRect = computeBottomLayoutAreaViaSurface(ShowMode.LayDown, surfaceRect)
        if (currentBottomView != null)
        {
            currentBottomView.layout(bottomViewRect.left, bottomViewRect.top, bottomViewRect.right, bottomViewRect.bottom)
        }
    }

    override fun computeScroll() {
        super.computeScroll()
        if (dragHelper.continueSettling(true))
        {
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    private fun isTouchOnSurface(ev:MotionEvent):Boolean {
        val view = surfaceView ?: return false
        if (hitSurfaceRect == null)
        {
            hitSurfaceRect = Rect()
        }
        view.getHitRect(hitSurfaceRect)
        return hitSurfaceRect!!.contains(ev.x.toInt(), ev.y.toInt())
    }

    internal inner class SwipeDetector: GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent):Boolean {
            if (isClickToClose && isTouchOnSurface(e)) {
                close()
            }
            return super.onSingleTapUp(e)
        }

        override fun onDoubleTap(e:MotionEvent):Boolean {
            if (doubleClickListener != null)
            {
                val target:View
                val bottom = currentBottomView
                val surface = surfaceView
                if (bottom != null && e.x > bottom.left && e.x < bottom.right
                        && e.y > bottom.top && e.y < bottom.bottom)
                {
                    target = bottom
                }
                else
                {
                    target = surface!!
                }
                doubleClickListener!!.onDoubleClick(this@SwipeLayout, target === surface)
            }
            return true
        }
    }

    override fun onTouchEvent(event:MotionEvent):Boolean {
        if (!isSwipeEnabled) return super.onTouchEvent(event)

        val action = event.actionMasked
        gestureDetector.onTouchEvent(event)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                dragHelper.processTouchEvent(event)
                sX = event.rawX
                sY = event.rawY

                //TODO: check this, the java implementation does not have break
            }

            MotionEvent.ACTION_MOVE -> {
                checkCanDrag(event)
                if (isBeingDragged) {
                    parent.requestDisallowInterceptTouchEvent(true)
                    dragHelper.processTouchEvent(event)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isBeingDragged = false
                dragHelper.processTouchEvent(event)
            }

            else -> dragHelper.processTouchEvent(event)
        }

        return super.onTouchEvent(event) || isBeingDragged || action == MotionEvent.ACTION_DOWN
    }

    private fun checkCanDrag(ev:MotionEvent) {
        if (isBeingDragged) return
        if (openStatus == Status.Middle)
        {
            isBeingDragged = true
            return
        }
        val status = openStatus
        val distanceX = ev.getRawX() - sX
        val distanceY = ev.getRawY() - sY
        var angle = Math.abs(distanceY / distanceX)
        angle = Math.toDegrees(Math.atan(angle.toDouble())).toFloat()
        if (openStatus == Status.Close)
        {
            val dragEdge: DragEdge
            if (angle < 45)
            {
                if (distanceX > 0 && isLeftSwipeEnabled)
                {
                    dragEdge = DragEdge.Left
                }
                else if (distanceX < 0 && isRightSwipeEnabled)
                {
                    dragEdge = DragEdge.Right
                }
                else
                    return

            }
            else
            {
                if (distanceY > 0 && isTopSwipeEnabled)
                {
                    dragEdge = DragEdge.Top
                }
                else if (distanceY < 0 && isBottomSwipeEnabled)
                {
                    dragEdge = DragEdge.Bottom
                }
                else
                    return
            }
            setCurrentDragEdge(dragEdge)
        }

        var doNothing = false
        if (currentDragEdge == DragEdge.Right)
        {
            var suitable = (status == Status.Open && distanceX > touchSlop) || (status == Status.Close && distanceX < -touchSlop)
            suitable = suitable || (status == Status.Middle)

            if (angle > 30 || !suitable)
            {
                doNothing = true
            }
        }

        if (currentDragEdge == DragEdge.Left)
        {
            var suitable = (status == Status.Open && distanceX < - touchSlop) || (status == Status.Close && distanceX > touchSlop)
            suitable = suitable || status == Status.Middle

            if (angle > 30 || !suitable)
            {
                doNothing = true
            }
        }

        if (currentDragEdge == DragEdge.Top)
        {
            var suitable = (status == Status.Open && distanceY < - touchSlop) || (status == Status.Close && distanceY > touchSlop)
            suitable = suitable || status == Status.Middle

            if (angle < 60 || !suitable)
            {
                doNothing = true
            }
        }

        if (currentDragEdge == DragEdge.Bottom)
        {
            var suitable = (status == Status.Open && distanceY > touchSlop) || (status == Status.Close && distanceY < -touchSlop)
            suitable = suitable || status == Status.Middle

            if (angle < 60 || !suitable)
            {
                doNothing = true
            }
        }
        isBeingDragged = !doNothing
    }

    override fun onInterceptTouchEvent(ev:MotionEvent):Boolean {
        if (!isSwipeEnabled)
        {
            return false
        }
        if (isClickToClose && openStatus == Status.Open && isTouchOnSurface(ev))
        {
            return true
        }
        for (denier in swipeDeniers)
        {
            if (denier.shouldDenySwipe(ev))
            {
                return false
            }
        }

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                dragHelper.processTouchEvent(ev)
                isBeingDragged = false
                sX = ev.rawX
                sY = ev.rawY
                if (openStatus == Status.Middle) {
                    isBeingDragged = true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val beforeCheck = isBeingDragged
                checkCanDrag(ev)
                if (isBeingDragged)
                {
                    val parent = getParent()
                    if (parent != null)
                    {
                        parent!!.requestDisallowInterceptTouchEvent(true)
                    }
                }
                if (!beforeCheck && isBeingDragged)
                {
                    //let children has one chance to catch the touch, and request the swipe not intercept
                    //useful when swipeLayout wrap a swipeLayout or other gestural layout
                    return false
                }
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                isBeingDragged = false
                dragHelper.processTouchEvent(ev)
            }
            else -> dragHelper.processTouchEvent(ev)
        }
        return isBeingDragged
    }

    override fun onLayout(changed:Boolean, l:Int, t:Int, r:Int, b:Int) {
        updateBottomViews()

        for (i in onLayoutListeners.indices)
        {
            onLayoutListeners.get(i).onLayout(this)
        }

    }

    private fun performAdapterViewItemLongClick():Boolean {
        if (openStatus != Status.Close) return false
        val t = getParent()
        if (t is AdapterView<*>)
        {
            val view = t as AdapterView<*>
            val p = view.getPositionForView(this@SwipeLayout)
            if (p == AdapterView.INVALID_POSITION) return false
            val vId = view.getItemIdAtPosition(p)
            var handled = false
            try
            {
                val m = AbsListView::class.java.getDeclaredMethod("performLongPress", View::class.java, Int::class.javaPrimitiveType, Long::class.javaPrimitiveType)
                m.setAccessible(true)
                handled = m.invoke(view, this@SwipeLayout, p, vId) as Boolean

            }
            catch (e:Exception) {
                e.printStackTrace()

                if (view.getOnItemLongClickListener() != null)
                {
                    handled = view.getOnItemLongClickListener().onItemLongClick(view, this@SwipeLayout, p, vId)
                }
                if (handled)
                {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }

            return handled
        }
        return false
    }

    private fun insideAdapterView():Boolean {
        return adapterView != null
    }

    private val adapterView:AdapterView<*>?
        get() {
            val t = parent
            if (t is AdapterView<*>)
            {
                return t
            }
            return null
        }

    private fun performAdapterViewItemClick() {
        if (openStatus != Status.Close) return
        val t = getParent()
        if (t is AdapterView<*>)
        {
            val view = t
            val p = view.getPositionForView(this@SwipeLayout)
            if (p != AdapterView.INVALID_POSITION)
            {
                view.performItemClick(view.getChildAt(p - view.firstVisiblePosition), p, view.adapter.getItemId(p))
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (insideAdapterView()) {
            if (clickListener == null) {
                setOnClickListener { performAdapterViewItemClick() }
            }
            if (longClickListener == null) {
                setOnLongClickListener {
                    performAdapterViewItemLongClick()
                }
            }
        }
    }


    override fun onViewRemoved(child:View) {
        for (entry in HashMap<DragEdge, View>(dragEdges).entries)
        {
            if (entry.value === child)
            {
                dragEdges.remove(entry.key)
            }
        }
    }


}


