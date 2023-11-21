package com.quantum.barcodescanner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class BarcodeBoxView(
    context: Context
) : View(context) {

    private val paint = Paint()
    private var mRect = emptyList<RectF>().toMutableList()
    private var positionSelected = 0

    var isDraw = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cornerRadius = 10f

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f

        var index = 0
        mRect.forEach {
            if(positionSelected == index) {
                paint.color = resources.getColor(R.color.yellow_black, null)
            } else {
                paint.color = resources.getColor(R.color.white, null)
            }
            canvas.drawRoundRect(it, cornerRadius, cornerRadius, paint)
            index++
        }

        if (mRect.isNotEmpty()) isDraw = true
    }

    fun setRect(rectList: List<RectF>) {
        mRect.clear()
        if(rectList.isNotEmpty()) mRect.addAll(rectList)
        invalidate()
        requestLayout()
    }

    fun selectPosition(position: Int){
        positionSelected = position
        invalidate()
        requestLayout()
    }

    fun deleteRects(){
        isDraw = false
        mRect.clear()
        invalidate()
        requestLayout()
    }

    fun getRectCount(): Int {
        return mRect.size
    }
}