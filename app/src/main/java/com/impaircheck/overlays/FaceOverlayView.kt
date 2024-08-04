package com.impaircheck.overlays


import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlin.math.max
import kotlin.math.min


class FaceOverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {

    private var results: FaceLandmarkerResult? = null
    private var linePaint = Paint()
    private var pointPaint = Paint()

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    init {
        initPaints()
    }

    fun clear() {
        results = null
        linePaint.reset()
        pointPaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        linePaint.color = Color.YELLOW
        linePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        linePaint.style = Paint.Style.STROKE

        pointPaint.color = Color.RED
        pointPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        pointPaint.style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        if (results == null || results!!.faceLandmarks().isEmpty()) {
            clear()
            return
        }

        results?.let { faceLandmarkerResult ->

            //left eye
            val leftEyeRightX =
                faceLandmarkerResult.faceLandmarks()[0].get(469).x() * imageWidth * scaleFactor
            val leftEyeRightY =
                faceLandmarkerResult.faceLandmarks()[0].get(469).y() * imageHeight * scaleFactor

            pointPaint.color = Color.WHITE
            canvas.drawPoint(
                leftEyeRightX,
                leftEyeRightY, pointPaint
            )


            val leftEyeLeftX =
                faceLandmarkerResult.faceLandmarks()[0].get(471).x() * imageWidth * scaleFactor
            val leftEyeLeftY =
                faceLandmarkerResult.faceLandmarks()[0].get(471).y() * imageHeight * scaleFactor
            pointPaint.color = Color.WHITE
            canvas.drawPoint(
                leftEyeLeftX,
                leftEyeLeftY, pointPaint
            )

            val leftEyeCenterX =
                faceLandmarkerResult.faceLandmarks()[0].get(468).x() * imageWidth * scaleFactor
            val leftEyeCenterY =
                faceLandmarkerResult.faceLandmarks()[0].get(468).y() * imageHeight * scaleFactor
            pointPaint.color = Color.WHITE
            canvas.drawPoint(
                leftEyeCenterX,
                leftEyeCenterY, pointPaint
            )


            val leftEyeTopX =
                faceLandmarkerResult.faceLandmarks()[0].get(470).x() * imageWidth * scaleFactor
            val leftEyeTopY =
                faceLandmarkerResult.faceLandmarks()[0].get(470).y() * imageHeight * scaleFactor
            pointPaint.color = Color.WHITE
            canvas.drawPoint(
                leftEyeTopX,
                leftEyeTopY, pointPaint
            )

            val leftEyeBottomX =
                faceLandmarkerResult.faceLandmarks()[0].get(472).x() * imageWidth * scaleFactor
            val leftEyeBottomY =
                faceLandmarkerResult.faceLandmarks()[0].get(472).y() * imageHeight * scaleFactor
            pointPaint.color = Color.WHITE
            canvas.drawPoint(
                leftEyeBottomX,
                leftEyeBottomY, pointPaint
            )


            //right eye
            val rightEyeRightX =
                faceLandmarkerResult.faceLandmarks()[0].get(474).x() * imageWidth * scaleFactor
            val rightEyeRightY =
                faceLandmarkerResult.faceLandmarks()[0].get(474).y() * imageHeight * scaleFactor
            pointPaint.color = Color.WHITE
            canvas.drawPoint(
                rightEyeRightX,
                rightEyeRightY, pointPaint
            )

            val rightEyeLeftX =
                faceLandmarkerResult.faceLandmarks()[0].get(476).x() * imageWidth * scaleFactor
            val rightEyeLeftY =
                faceLandmarkerResult.faceLandmarks()[0].get(476).y() * imageHeight * scaleFactor
            pointPaint.color = Color.WHITE
            canvas.drawPoint(
                rightEyeLeftX,
                rightEyeLeftY, pointPaint
            )

            val rightEyeCenterX =
                faceLandmarkerResult.faceLandmarks()[0].get(473).x() * imageWidth * scaleFactor
            val rightEyeCenterY =
                faceLandmarkerResult.faceLandmarks()[0].get(473).y() * imageHeight * scaleFactor
            pointPaint.color = Color.WHITE
            canvas.drawPoint(
                rightEyeCenterX,
                rightEyeCenterY, pointPaint
            )

            val rightEyeTopX =
                faceLandmarkerResult.faceLandmarks()[0].get(475).x() * imageWidth * scaleFactor
            val rightEyeTopY =
                faceLandmarkerResult.faceLandmarks()[0].get(475).y() * imageHeight * scaleFactor
            pointPaint.color = Color.WHITE
            canvas.drawPoint(
                rightEyeTopX,
                rightEyeTopY, pointPaint
            )

            val rightEyeBottomX =
                faceLandmarkerResult.faceLandmarks()[0].get(477).x() * imageWidth * scaleFactor
            val rightEyeBottomY =
                faceLandmarkerResult.faceLandmarks()[0].get(477).y() * imageHeight * scaleFactor
            pointPaint.color = Color.WHITE
            canvas.drawPoint(
                rightEyeBottomX,
                rightEyeBottomY, pointPaint
            )


//            for (landmark in faceLandmarkerResult.faceLandmarks()) {
//                for (normalizedLandmark in landmark) {
//                    canvas.drawPoint(
//                        normalizedLandmark.x() * imageWidth * scaleFactor,
//                        normalizedLandmark.y() * imageHeight * scaleFactor, pointPaint
//                    )
//                }
//            }



            FaceLandmarker.FACE_LANDMARKS_RIGHT_EYE.forEach {

                canvas.drawLine(
                    faceLandmarkerResult.faceLandmarks().get(0).get(it!!.start())
                        .x() * imageWidth * scaleFactor,
                    faceLandmarkerResult.faceLandmarks().get(0).get(it.start())
                        .y() * imageHeight * scaleFactor,
                    faceLandmarkerResult.faceLandmarks().get(0).get(it.end())
                        .x() * imageWidth * scaleFactor,
                    faceLandmarkerResult.faceLandmarks().get(0).get(it.end())
                        .y() * imageHeight * scaleFactor,
                    linePaint
                )


            }




            FaceLandmarker.FACE_LANDMARKS_LEFT_EYE.forEach {

                canvas.drawLine(
                    faceLandmarkerResult.faceLandmarks().get(0).get(it!!.start())
                        .x() * imageWidth * scaleFactor,
                    faceLandmarkerResult.faceLandmarks().get(0).get(it.start())
                        .y() * imageHeight * scaleFactor,
                    faceLandmarkerResult.faceLandmarks().get(0).get(it.end())
                        .x() * imageWidth * scaleFactor,
                    faceLandmarkerResult.faceLandmarks().get(0).get(it.end())
                        .y() * imageHeight * scaleFactor,
                    linePaint
                )
            }


        }
    }

    fun setResults(
        faceLandmarkerResults: FaceLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {


        results = faceLandmarkerResults

        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        scaleFactor = when (runningMode) {
            RunningMode.IMAGE,
            RunningMode.VIDEO -> {
                min(width * 1f / imageWidth, height * 1f / imageHeight)
            }

            RunningMode.LIVE_STREAM -> {
                // PreviewView is in FILL_START mode. So we need to scale up the
                // landmarks to match with the size that the captured images will be
                // displayed.
                max(width * 1f / imageWidth, height * 1f / imageHeight)
            }
        }
        invalidate()
    }

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 8F
        private const val TAG = "Face Landmarker Overlay"
    }
}
