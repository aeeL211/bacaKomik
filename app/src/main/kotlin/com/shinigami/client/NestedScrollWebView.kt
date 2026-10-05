package com.shinigami.client

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat

class NestedScrollWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.webViewStyle,
) : WebView(context, attrs, defStyleAttr),
    NestedScrollingChild3 {

    init {
        // Tanpa ini, AndroidViewHolder Compose memasang LayoutParams default WRAP_CONTENT.
        // Chromium WebView membaca layoutParams.height == WRAP_CONTENT sebagai "tinggi mengikuti
        // konten", sehingga 100vh / fixed inset-0 jadi pendek: dialog dan teks tampil di atas.
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    private val childHelper = NestedScrollingChildHelper(this).apply {
        isNestedScrollingEnabled = true
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var initialDownY = 0f
    private var lastMotionY = 0
    private var isBeingDragged = false
    private val scrollOffset = IntArray(2)
    private val scrollConsumed = IntArray(2)
    private var nestedOffsetY = 0

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val obtained = MotionEvent.obtain(event)
        val action = event.actionMasked

        if (action == MotionEvent.ACTION_DOWN) {
            nestedOffsetY = 0
            initialDownY = event.y
            isBeingDragged = false
        }

        val eventY = event.y.toInt()
        event.offsetLocation(0f, nestedOffsetY.toFloat())

        val result: Boolean = when (action) {
            MotionEvent.ACTION_DOWN -> {
                lastMotionY = eventY
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
                super.onTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE -> {
                val totalDy = event.y - initialDownY

                if (!isBeingDragged) {
                    if (totalDy > touchSlop && !canScrollVertically(-1)) {
                        isBeingDragged = true
                        lastMotionY = eventY
                    }
                }

                var deltaY = lastMotionY - eventY

                if (isBeingDragged) {
                    if (dispatchNestedPreScroll(0, deltaY, scrollConsumed, scrollOffset, ViewCompat.TYPE_TOUCH)) {
                        deltaY -= scrollConsumed[1]
                        event.offsetLocation(0f, -scrollOffset[1].toFloat())
                        nestedOffsetY += scrollOffset[1]
                    }
                }

                val oldScrollY = scrollY
                val returnValue = super.onTouchEvent(event)
                val dyConsumed = scrollY - oldScrollY
                var dyUnconsumed = deltaY - dyConsumed

                if (dyUnconsumed < 0 && canScrollVertically(-1)) {
                    dyUnconsumed = 0
                }

                if (isBeingDragged && dispatchNestedScroll(0, dyConsumed, 0, dyUnconsumed, scrollOffset, ViewCompat.TYPE_TOUCH)) {
                    event.offsetLocation(0f, -scrollOffset[1].toFloat())
                    nestedOffsetY += scrollOffset[1]
                    lastMotionY -= scrollOffset[1]
                } else {
                    lastMotionY = eventY - scrollOffset[1]
                }

                returnValue
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasPulling = isBeingDragged
                isBeingDragged = false
                val returnValue = super.onTouchEvent(event)
                if (wasPulling) {
                    // Compose's pull-to-refresh only "releases" (runs onRefresh or hides the
                    // spinner) when it receives a fling. WebView sends none for a slow pull,
                    // so the spinner stayed forever. Send a zero-velocity fling ourselves.
                    dispatchNestedPreFling(0f, 0f)
                }
                stopNestedScroll(ViewCompat.TYPE_TOUCH)
                returnValue
            }
            else -> super.onTouchEvent(event)
        }

        obtained.recycle()
        return result
    }

    // NestedScrollingChild3
    override fun startNestedScroll(axes: Int, type: Int): Boolean = childHelper.startNestedScroll(axes, type)

    override fun stopNestedScroll(type: Int) {
        childHelper.stopNestedScroll(type)
    }

    override fun hasNestedScrollingParent(type: Int): Boolean = childHelper.hasNestedScrollingParent(type)

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int,
        consumed: IntArray,
    ) {
        childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type, consumed)
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int,
    ): Boolean = childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type)

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
        type: Int,
    ): Boolean = childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)

    // NestedScrollingChild2 & NestedScrollingChild
    override fun setNestedScrollingEnabled(enabled: Boolean) {
        childHelper.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean = childHelper.isNestedScrollingEnabled

    override fun startNestedScroll(axes: Int): Boolean = childHelper.startNestedScroll(axes)

    override fun stopNestedScroll() {
        childHelper.stopNestedScroll()
    }

    override fun hasNestedScrollingParent(): Boolean = childHelper.hasNestedScrollingParent()

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
    ): Boolean = childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow)

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
    ): Boolean = childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow)

    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean = childHelper.dispatchNestedFling(velocityX, velocityY, consumed)

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean = childHelper.dispatchNestedPreFling(velocityX, velocityY)
}
