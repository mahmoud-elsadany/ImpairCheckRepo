package com.impaircheck.overlays

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class FaceFrameOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val paint: Paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width
        val height = height

        // Draw the outer oval frame
        val frameLeft = width / 6
        val frameTop = height / 6
        val frameRight = 5 * width / 6
        val frameBottom = 5 * height / 6
        val ovalRect = RectF(frameLeft.toFloat(), frameTop.toFloat(), frameRight.toFloat(), frameBottom.toFloat())
        canvas.drawOval(ovalRect, paint)

        // Draw the eyes markers as ovals
        val eyeY = height / 2 - height / 10
        val eyeWidth = width / 8
        val eyeHeight = height / 20
        val leftEyeX = width / 2 - width / 6
        val rightEyeX = width / 2 + width / 6
        val leftEyeRect = RectF((leftEyeX - eyeWidth / 2).toFloat(),
            (eyeY - eyeHeight / 2).toFloat(), (leftEyeX + eyeWidth / 2).toFloat(), (eyeY + eyeHeight / 2).toFloat()
        )
        val rightEyeRect = RectF((rightEyeX - eyeWidth / 2).toFloat(),
            (eyeY - eyeHeight / 2).toFloat(), (rightEyeX + eyeWidth / 2).toFloat(), (eyeY + eyeHeight / 2).toFloat()
        )
        canvas.drawOval(leftEyeRect, paint)
        canvas.drawOval(rightEyeRect, paint)


    }
}
