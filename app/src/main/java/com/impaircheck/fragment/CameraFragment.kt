
/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.impaircheck.fragment

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Camera
import androidx.camera.core.AspectRatio
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.impaircheck.FaceLandmarkerHelper
import com.impaircheck.MainViewModel
import com.impaircheck.databinding.FragmentCameraBinding
import java.io.File
import java.util.Locale
import java.util.Optional
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.toList
import kotlin.math.max
import kotlin.math.roundToInt

class CameraFragment : Fragment(), FaceLandmarkerHelper.LandmarkerListener {

    companion object {
        private const val TAG = "Face Landmarker"
    }

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private lateinit var faceLandmarkerHelper: FaceLandmarkerHelper
    private val viewModel: MainViewModel by activityViewModels()
    private val faceBlendshapesResultAdapter by lazy {
        FaceBlendshapesResultAdapter()
    }

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT

    //sadany get color of eye
    private lateinit var CapturedBitmap: Bitmap
    private var imageHeight: Int = -1
    private var imageWidth: Int = -1
    private var scaleFactor: Float = -1f
    private var faceLandmarkerResults: FaceLandmarkerResult? = null


    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.


        // Start the FaceLandmarkerHelper again when users come back
        // to the foreground.
        backgroundExecutor.execute {
            if (faceLandmarkerHelper.isClose()) {
                faceLandmarkerHelper.setupFaceLandmarker()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if(this::faceLandmarkerHelper.isInitialized) {
            viewModel.setMaxFaces(faceLandmarkerHelper.maxNumFaces)
            viewModel.setMinFaceDetectionConfidence(faceLandmarkerHelper.minFaceDetectionConfidence)
            viewModel.setMinFaceTrackingConfidence(faceLandmarkerHelper.minFaceTrackingConfidence)
            viewModel.setMinFacePresenceConfidence(faceLandmarkerHelper.minFacePresenceConfidence)
            viewModel.setFaceDelegate(faceLandmarkerHelper.currentDelegate)

            // Close the FaceLandmarkerHelper and release resources
            backgroundExecutor.execute { faceLandmarkerHelper.clearFaceLandmarker() }
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        // Shut down our background executor
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding =
            FragmentCameraBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        with(fragmentCameraBinding.recyclerviewResults) {
//            layoutManager = LinearLayoutManager(requireContext())
//            adapter = faceBlendshapesResultAdapter
//        }

        // Initialize our background executor
        backgroundExecutor = Executors.newSingleThreadExecutor()

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {
            // Set up the camera and its use cases
            setUpCamera()
        }

        // Create the FaceLandmarkerHelper that will handle the inference
        backgroundExecutor.execute {
            faceLandmarkerHelper =
                FaceLandmarkerHelper(
                    context = requireContext(),
                    runningMode = RunningMode.LIVE_STREAM,
                    minFaceDetectionConfidence = viewModel.currentMinFaceDetectionConfidence,
                    minFaceTrackingConfidence = viewModel.currentMinFaceTrackingConfidence,
                    minFacePresenceConfidence = viewModel.currentMinFacePresenceConfidence,
                    maxNumFaces = viewModel.currentMaxFaces,
                    currentDelegate = viewModel.currentFaceDelegate,
                    faceLandmarkerHelperListener = this
                )
        }

        // Attach listeners to UI control widgets
        initBottomSheetControls()
    }

    private fun initBottomSheetControls() {
        // init bottom sheet settings
//        fragmentCameraBinding.bottomSheetLayout.maxFacesValue.text =
//            viewModel.currentMaxFaces.toString()
//        fragmentCameraBinding.bottomSheetLayout.detectionThresholdValue.text =
//            String.format(
//                Locale.US, "%.2f", viewModel.currentMinFaceDetectionConfidence
//            )
//        fragmentCameraBinding.bottomSheetLayout.trackingThresholdValue.text =
//            String.format(
//                Locale.US, "%.2f", viewModel.currentMinFaceTrackingConfidence
//            )
//        fragmentCameraBinding.bottomSheetLayout.presenceThresholdValue.text =
//            String.format(
//                Locale.US, "%.2f", viewModel.currentMinFacePresenceConfidence
//            )
//
//        // When clicked, lower face detection score threshold floor
//        fragmentCameraBinding.bottomSheetLayout.detectionThresholdMinus.setOnClickListener {
//            if (faceLandmarkerHelper.minFaceDetectionConfidence >= 0.2) {
//                faceLandmarkerHelper.minFaceDetectionConfidence -= 0.1f
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, raise face detection score threshold floor
//        fragmentCameraBinding.bottomSheetLayout.detectionThresholdPlus.setOnClickListener {
//            if (faceLandmarkerHelper.minFaceDetectionConfidence <= 0.8) {
//                faceLandmarkerHelper.minFaceDetectionConfidence += 0.1f
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, lower face tracking score threshold floor
//        fragmentCameraBinding.bottomSheetLayout.trackingThresholdMinus.setOnClickListener {
//            if (faceLandmarkerHelper.minFaceTrackingConfidence >= 0.2) {
//                faceLandmarkerHelper.minFaceTrackingConfidence -= 0.1f
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, raise face tracking score threshold floor
//        fragmentCameraBinding.bottomSheetLayout.trackingThresholdPlus.setOnClickListener {
//            if (faceLandmarkerHelper.minFaceTrackingConfidence <= 0.8) {
//                faceLandmarkerHelper.minFaceTrackingConfidence += 0.1f
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, lower face presence score threshold floor
//        fragmentCameraBinding.bottomSheetLayout.presenceThresholdMinus.setOnClickListener {
//            if (faceLandmarkerHelper.minFacePresenceConfidence >= 0.2) {
//                faceLandmarkerHelper.minFacePresenceConfidence -= 0.1f
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, raise face presence score threshold floor
//        fragmentCameraBinding.bottomSheetLayout.presenceThresholdPlus.setOnClickListener {
//            if (faceLandmarkerHelper.minFacePresenceConfidence <= 0.8) {
//                faceLandmarkerHelper.minFacePresenceConfidence += 0.1f
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, reduce the number of faces that can be detected at a
//        // time
//        fragmentCameraBinding.bottomSheetLayout.maxFacesMinus.setOnClickListener {
//            if (faceLandmarkerHelper.maxNumFaces > 1) {
//                faceLandmarkerHelper.maxNumFaces--
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, increase the number of faces that can be detected
//        // at a time
//        fragmentCameraBinding.bottomSheetLayout.maxFacesPlus.setOnClickListener {
//            if (faceLandmarkerHelper.maxNumFaces < 2) {
//                faceLandmarkerHelper.maxNumFaces++
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, change the underlying hardware used for inference.
//        // Current options are CPU and GPU
//        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
//            viewModel.currentDelegate, false
//        )
//        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
//            object : AdapterView.OnItemSelectedListener {
//                override fun onItemSelected(
//                    p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long
//                ) {
//                    try {
//                        faceLandmarkerHelper.currentDelegate = p2
//                        updateControlsUi()
//                    } catch(e: UninitializedPropertyAccessException) {
//                        Log.e(TAG, "FaceLandmarkerHelper has not been initialized yet.")
//                    }
//                }
//
//                override fun onNothingSelected(p0: AdapterView<*>?) {
//                    /* no op */
//                }
//            }
    }

    // Update the values displayed in the bottom sheet. Reset Facelandmarker
    // helper.
    private fun updateControlsUi() {

        handleCaptureEyeButton()

//        fragmentCameraBinding.bottomSheetLayout.maxFacesValue.text =
//            faceLandmarkerHelper.maxNumFaces.toString()
//        fragmentCameraBinding.bottomSheetLayout.detectionThresholdValue.text =
//            String.format(
//                Locale.US,
//                "%.2f",
//                faceLandmarkerHelper.minFaceDetectionConfidence
//            )
//        fragmentCameraBinding.bottomSheetLayout.trackingThresholdValue.text =
//            String.format(
//                Locale.US,
//                "%.2f",
//                faceLandmarkerHelper.minFaceTrackingConfidence
//            )
//        fragmentCameraBinding.bottomSheetLayout.presenceThresholdValue.text =
//            String.format(
//                Locale.US,
//                "%.2f",
//                faceLandmarkerHelper.minFacePresenceConfidence
//            )

        // Needs to be cleared instead of reinitialized because the GPU
        // delegate needs to be initialized on the thread using it when applicable
        backgroundExecutor.execute {
            faceLandmarkerHelper.clearFaceLandmarker()
            faceLandmarkerHelper.setupFaceLandmarker()
        }
        fragmentCameraBinding.overlay.clear()
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(requireContext())
        )
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(cameraFacing).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(backgroundExecutor) { image ->
                        detectFace(image)
                    }
                }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun detectFace(imageProxy: ImageProxy) {
        faceLandmarkerHelper.detectLiveStream(
            imageProxy = imageProxy,
            isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation =
            fragmentCameraBinding.viewFinder.display.rotation
    }

    // Update UI after face have been detected. Extracts original
    // image height/width to scale and place the landmarks properly through
    // OverlayView
    override fun onResults(
        resultBundle: FaceLandmarkerHelper.ResultBundle
    ) {
        activity?.runOnUiThread {
            if (_fragmentCameraBinding != null) {
//                if (fragmentCameraBinding.recyclerviewResults.scrollState != SCROLL_STATE_DRAGGING) {
//                    faceBlendshapesResultAdapter.updateResults(resultBundle.result)
//                    faceBlendshapesResultAdapter.notifyDataSetChanged()
//                }


//                fragmentCameraBinding.bottomSheetLayout.inferenceTimeVal.text =
//                    String.format("%d ms", resultBundle.inferenceTime)

                // Pass necessary information to OverlayView for drawing on the canvas
                fragmentCameraBinding.overlay.setResults(
                    resultBundle.result,
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth,
                    RunningMode.LIVE_STREAM
                )
                // Force a redraw
                fragmentCameraBinding.overlay.invalidate()


                //sadany get color of eye
                faceLandmarkerResults =  resultBundle.result

                imageHeight = resultBundle.inputImageHeight
                imageWidth = resultBundle.inputImageWidth

                scaleFactor = max(fragmentCameraBinding.overlay.width * 1f / imageWidth, fragmentCameraBinding.overlay.height * 1f / imageHeight)
            }
        }
    }

    override fun onEmpty() {
        fragmentCameraBinding.overlay.clear()
        activity?.runOnUiThread {
            faceBlendshapesResultAdapter.updateResults(null)
            faceBlendshapesResultAdapter.notifyDataSetChanged()
        }
    }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            faceBlendshapesResultAdapter.updateResults(null)
            faceBlendshapesResultAdapter.notifyDataSetChanged()

            if (errorCode == FaceLandmarkerHelper.GPU_ERROR) {
//                fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
//                    FaceLandmarkerHelper.DELEGATE_CPU, false
//                )
            }
        }
    }


    //sadany get color of eye
    fun handleCaptureEyeButton() {



        fragmentCameraBinding.captureEyeButton.setOnClickListener {
            if (faceLandmarkerResults != null && imageWidth != -1 && imageHeight != -1) {
                CapturedBitmap = getBitmapFromPreviewView(fragmentCameraBinding.viewFinder)
                showRedPercentageInEyes(
                    CapturedBitmap,
                    imageWidth,
                    imageHeight,
                    scaleFactor
                )
            }else
                Toast.makeText(requireContext(), "Face not detected", Toast.LENGTH_SHORT).show()

        }
    }

    fun getBitmapFromPreviewView(previewView: PreviewView): Bitmap {
        val bitmap = Bitmap.createBitmap(
            previewView.width, previewView.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        previewView.draw(canvas)
        return bitmap
    }

    fun showRedPercentageInEyes(
        bitmap: Bitmap,
        imageWidth: Int,
        imageHeight: Int,
        scaleFactor: Float
    ) {

        var rightEyeStartX = 0
        var rightEyeStartY = 0
        var rightEyeEndX = 0
        var rightEyeEndY = 0


        //Right Eye
        FaceLandmarker.FACE_LANDMARKS_RIGHT_EYE.forEach {


            rightEyeStartX = (faceLandmarkerResults!!.faceLandmarks().get(0).get(it!!.start())
                .x() * imageWidth * scaleFactor).roundToInt()
            rightEyeStartY = (faceLandmarkerResults!!.faceLandmarks().get(0).get(it.start())
                .y() * imageHeight * scaleFactor).roundToInt()

            rightEyeEndX = (faceLandmarkerResults!!.faceLandmarks().get(0).get(it.end())
                .x() * imageWidth * scaleFactor).roundToInt()
            rightEyeEndY = (faceLandmarkerResults!!.faceLandmarks().get(0).get(it.end())
                .y() * imageHeight * scaleFactor).roundToInt()


        }

        var leftEyeStartX = 0
        var leftEyeStartY = 0
        var leftEyeEndX = 0
        var leftEyeEndY = 0


        //Left Eye
        FaceLandmarker.FACE_LANDMARKS_LEFT_EYE.forEach {


            leftEyeStartX = (faceLandmarkerResults!!.faceLandmarks().get(0).get(it!!.start())
                .x() * imageWidth * scaleFactor).roundToInt()
            leftEyeStartY = (faceLandmarkerResults!!.faceLandmarks().get(0).get(it.start())
                .y() * imageHeight * scaleFactor).roundToInt()

            leftEyeEndX = (faceLandmarkerResults!!.faceLandmarks().get(0).get(it.end())
                .x() * imageWidth * scaleFactor).roundToInt()
            leftEyeEndY = (faceLandmarkerResults!!.faceLandmarks().get(0).get(it.end())
                .y() * imageHeight * scaleFactor).roundToInt()


        }


        val averageRightEyeColor = getAverageColor(bitmap, rightEyeStartX, rightEyeStartY, rightEyeEndX, rightEyeEndY)
        val rightEyeHexColor = String.format("#%06X", 0xFFFFFF and averageRightEyeColor)
        println("ColorPercentageRightEye in Hex: $rightEyeHexColor")


        val averageLeftEyeColor = getAverageColor(bitmap, leftEyeStartX, leftEyeStartY, leftEyeEndX, leftEyeEndY)
        val leftEyeHexColor = String.format("#%06X", 0xFFFFFF and averageLeftEyeColor)
        println("ColorPercentageLeftEye in Hex: $leftEyeHexColor")

        Toast.makeText(
            requireContext(),
            "ColorPercentageLeftEye in Hex: $leftEyeHexColor, ColorPercentageRightEye in Hex: $rightEyeHexColor",
            Toast.LENGTH_LONG
        ).show()


    }

    fun getAverageColor(
        bitmap: Bitmap,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int
    ): Int {
        var redSum = 0
        var greenSum = 0
        var blueSum = 0
        var pixelCount = 0

        for (x in startX until endX) {
            for (y in startY until endY) {
                val pixel = bitmap.getPixel(x, y)
                redSum += Color.red(pixel)
                greenSum += Color.green(pixel)
                blueSum += Color.blue(pixel)
                pixelCount++
            }
        }

        val averageRed = redSum / pixelCount
        val averageGreen = greenSum / pixelCount
        val averageBlue = blueSum / pixelCount

        return Color.rgb(averageRed, averageGreen, averageBlue)
    }
}

