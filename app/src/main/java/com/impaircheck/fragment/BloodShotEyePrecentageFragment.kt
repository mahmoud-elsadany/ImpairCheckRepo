/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.impaircheck.fragment

import android.app.AlertDialog
import android.content.DialogInterface
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.impaircheck.FaceLandmarkerHelper
import com.impaircheck.MainViewModel
import com.impaircheck.R
import com.impaircheck.Utils.getCurrentDate
import com.impaircheck.constants.currentTestObject
import com.impaircheck.constants.currentUserId
import com.impaircheck.constants.fireBaseDatabase
import com.impaircheck.databinding.FragmentGalleryBinding
import com.impaircheck.models.userTestsItem
import com.ml.quaterion.facenetdetection.Constants
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class BloodShotEyePrecentageFragment : Fragment(), FaceLandmarkerHelper.LandmarkerListener {

    enum class MediaType {
        IMAGE,
        VIDEO,
        UNKNOWN
    }

    private var LeftEyeRedPercentage: Double = 0.0
    private var RightEyeRedPercentage: Double = 0.0

    //sadany get color of eye
    private lateinit var CapturedBitmap: Bitmap
    private var imageHeight: Int = -1
    private var imageWidth: Int = -1
    private var scaleFactor: Float = -1f
    private var faceLandmarkerResults: FaceLandmarkerResult? = null


    private var _fragmentGalleryBinding: FragmentGalleryBinding? = null
    private val fragmentGalleryBinding
        get() = _fragmentGalleryBinding!!
    private lateinit var faceLandmarkerHelper: FaceLandmarkerHelper
    private val viewModel: MainViewModel by activityViewModels()
    private val faceBlendshapesResultAdapter by lazy {
        FaceBlendshapesResultAdapter()
    }

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ScheduledExecutorService

    private val getContent =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            // Handle the returned Uri
            uri?.let { mediaUri ->
                when (val mediaType = loadMediaType(mediaUri)) {
                    MediaType.IMAGE -> runDetectionOnImage(mediaUri)
                    MediaType.VIDEO -> runDetectionOnVideo(mediaUri)
                    MediaType.UNKNOWN -> {
                        updateDisplayView(mediaType)
                        Toast.makeText(
                            requireContext(),
                            "Unsupported data type.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentGalleryBinding =
            FragmentGalleryBinding.inflate(inflater, container, false)

        return fragmentGalleryBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        runDetectionOnImage(Constants.imageUri!!)


//        fragmentGalleryBinding.fabGetContent.setOnClickListener {
//            getContent.launch(arrayOf("image/*", "video/*"))
//        }
//        with(fragmentGalleryBinding.recyclerviewResults) {
//            layoutManager = LinearLayoutManager(requireContext())
//            adapter = faceBlendshapesResultAdapter
//        }

        initBottomSheetControls()
    }

    override fun onPause() {
        fragmentGalleryBinding.overlay.clear()
        if (fragmentGalleryBinding.videoView.isPlaying) {
            fragmentGalleryBinding.videoView.stopPlayback()
        }
        fragmentGalleryBinding.videoView.visibility = View.GONE
        fragmentGalleryBinding.imageResult.visibility = View.GONE
        fragmentGalleryBinding.tvPlaceholder.visibility = View.VISIBLE

        activity?.runOnUiThread {
            faceBlendshapesResultAdapter.updateResults(null)
            faceBlendshapesResultAdapter.notifyDataSetChanged()
        }
        super.onPause()
    }

    private fun initBottomSheetControls() {
        // init bottom sheet settings

        handleCaptureEyeButton()

        handleNextButton()

//        fragmentGalleryBinding.bottomSheetLayout.maxFacesValue.text =
//            viewModel.currentMaxFaces.toString()
//        fragmentGalleryBinding.bottomSheetLayout.detectionThresholdValue.text =
//            String.format(
//                Locale.US, "%.2f", viewModel.currentMinFaceDetectionConfidence
//            )
//        fragmentGalleryBinding.bottomSheetLayout.trackingThresholdValue.text =
//            String.format(
//                Locale.US, "%.2f", viewModel.currentMinFaceTrackingConfidence
//            )
//        fragmentGalleryBinding.bottomSheetLayout.presenceThresholdValue.text =
//            String.format(
//                Locale.US, "%.2f", viewModel.currentMinFacePresenceConfidence
//            )
//
//        // When clicked, lower detection score threshold floor
//        fragmentGalleryBinding.bottomSheetLayout.detectionThresholdMinus.setOnClickListener {
//            if (viewModel.currentMinFaceDetectionConfidence >= 0.2) {
//                viewModel.setMinFaceDetectionConfidence(viewModel.currentMinFaceDetectionConfidence - 0.1f)
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, raise detection score threshold floor
//        fragmentGalleryBinding.bottomSheetLayout.detectionThresholdPlus.setOnClickListener {
//            if (viewModel.currentMinFaceDetectionConfidence <= 0.8) {
//                viewModel.setMinFaceDetectionConfidence(viewModel.currentMinFaceDetectionConfidence + 0.1f)
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, lower face tracking score threshold floor
//        fragmentGalleryBinding.bottomSheetLayout.trackingThresholdMinus.setOnClickListener {
//            if (viewModel.currentMinFaceTrackingConfidence >= 0.2) {
//                viewModel.setMinFaceTrackingConfidence(
//                    viewModel.currentMinFaceTrackingConfidence - 0.1f
//                )
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, raise face tracking score threshold floor
//        fragmentGalleryBinding.bottomSheetLayout.trackingThresholdPlus.setOnClickListener {
//            if (viewModel.currentMinFaceTrackingConfidence <= 0.8) {
//                viewModel.setMinFaceTrackingConfidence(
//                    viewModel.currentMinFaceTrackingConfidence + 0.1f
//                )
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, lower face presence score threshold floor
//        fragmentGalleryBinding.bottomSheetLayout.presenceThresholdMinus.setOnClickListener {
//            if (viewModel.currentMinFacePresenceConfidence >= 0.2) {
//                viewModel.setMinFacePresenceConfidence(
//                    viewModel.currentMinFacePresenceConfidence - 0.1f
//                )
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, raise face presence score threshold floor
//        fragmentGalleryBinding.bottomSheetLayout.presenceThresholdPlus.setOnClickListener {
//            if (viewModel.currentMinFacePresenceConfidence <= 0.8) {
//                viewModel.setMinFacePresenceConfidence(
//                    viewModel.currentMinFacePresenceConfidence + 0.1f
//                )
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, reduce the number of objects that can be detected at a time
//        fragmentGalleryBinding.bottomSheetLayout.maxFacesMinus.setOnClickListener {
//            if (viewModel.currentMaxFaces > 1) {
//                viewModel.setMaxFaces(viewModel.currentMaxFaces - 1)
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, increase the number of objects that can be detected at a time
//        fragmentGalleryBinding.bottomSheetLayout.maxFacesPlus.setOnClickListener {
//            if (viewModel.currentMaxFaces < 2) {
//                viewModel.setMaxFaces(viewModel.currentMaxFaces + 1)
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, change the underlying hardware used for inference. Current options are CPU
//        // GPU, and NNAPI
//        fragmentGalleryBinding.bottomSheetLayout.spinnerDelegate.setSelection(
//            viewModel.currentDelegate,
//            false
//        )
//        fragmentGalleryBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
//            object : AdapterView.OnItemSelectedListener {
//                override fun onItemSelected(
//                    p0: AdapterView<*>?,
//                    p1: View?,
//                    p2: Int,
//                    p3: Long
//                ) {
//
//                    viewModel.setDelegate(p2)
//                    updateControlsUi()
//                }
//
//                override fun onNothingSelected(p0: AdapterView<*>?) {
//                    /* no op */
//                }
//            }
    }

    private fun handleNextButton() {
        fragmentGalleryBinding.nextButton.setOnClickListener {
            showDialog()
        }
    }

    private fun showDialog() {
        val dialog = AlertDialog.Builder(requireContext())
        dialog.setTitle(getString(R.string.dialog_title))
        dialog.setMessage(getString(R.string.dialog_go_to_move_analysis_message))
        dialog.setPositiveButton("OK") { dialogInterface: DialogInterface, i: Int ->
            dialogInterface.dismiss()
            updateCurrentTest()
        }
        dialog.show()
    }


    // Load and display the image.
    private fun runDetectionOnImage(uri: Uri) {
        setUiEnabled(false)
        backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
        updateDisplayView(MediaType.IMAGE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(
                requireActivity().contentResolver,
                uri
            )
            ImageDecoder.decodeBitmap(source)
        } else {
            MediaStore.Images.Media.getBitmap(
                requireActivity().contentResolver,
                uri
            )
        }
            .copy(Bitmap.Config.ARGB_8888, true)
            ?.let { bitmap ->

//                var bitmap = currBitmap


                fragmentGalleryBinding.tvLeftRedPercentage.text = ""
                fragmentGalleryBinding.tvRightRedPercentage.text = ""

                fragmentGalleryBinding.imageResult.setImageBitmap(bitmap)

                // check if bitmap resolution is too high
//                val MAX_IMAGE_SIZE = 1920 * 1080
//                if (bitmap.width * bitmap.height > MAX_IMAGE_SIZE) {
//                    bitmap = compressBitmap(bitmap, 80)
//                }

                // Run face landmarker on the input image
                backgroundExecutor.execute {

                    faceLandmarkerHelper =
                        FaceLandmarkerHelper(
                            context = requireContext(),
                            runningMode = RunningMode.IMAGE,
                            minFaceDetectionConfidence = viewModel.currentMinFaceDetectionConfidence,
                            minFaceTrackingConfidence = viewModel.currentMinFaceTrackingConfidence,
                            minFacePresenceConfidence = viewModel.currentMinFacePresenceConfidence,
                            maxNumFaces = viewModel.currentMaxFaces,
                            currentDelegate = viewModel.currentFaceDelegate
                        )
                    //sadany get color of eye
                    CapturedBitmap = bitmap

                    faceLandmarkerHelper.detectImage(bitmap)?.let { result ->
                        activity?.runOnUiThread {
//                            if (fragmentGalleryBinding.recyclerviewResults.scrollState != ViewPager2.SCROLL_STATE_DRAGGING) {
//                                faceBlendshapesResultAdapter.updateResults(result.result)
//                                faceBlendshapesResultAdapter.notifyDataSetChanged()
//                            }
                            fragmentGalleryBinding.overlay.setResults(
                                result.result,
                                bitmap.height,
                                bitmap.width,
                                RunningMode.IMAGE
                            )


                            //sadany get color of eye
                            faceLandmarkerResults = result.result

                            imageHeight = result.inputImageHeight
                            imageWidth = result.inputImageWidth

                            scaleFactor = 1f


                            setUiEnabled(true)
//                            fragmentGalleryBinding.bottomSheetLayout.inferenceTimeVal.text =
//                                String.format("%d ms", result.inferenceTime)
                        }
                    } ?: run { Log.e(TAG, "Error running face landmarker.") }

                    faceLandmarkerHelper.clearFaceLandmarker()
                }
            }
    }


    //sadany get color of eye

    fun compressBitmap(original: Bitmap, quality: Int): Bitmap {
        val outputStream = ByteArrayOutputStream()
        // Compress the bitmap with the specified quality (0-100)
        original.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val byteArray = outputStream.toByteArray()
        return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    }

    fun handleCaptureEyeButton() {
        fragmentGalleryBinding.captureEyeButton.setOnClickListener {
            if (faceLandmarkerResults != null && imageWidth != -1 && imageHeight != -1 && scaleFactor != -1F) {

                showLoading()
                RightEyeRedPercentage = 0.0
                LeftEyeRedPercentage = 0.0

                showRedPercentageInRightEye(
                    CapturedBitmap,
                    imageWidth,
                    imageHeight,
                    scaleFactor
                )
                showRedPercentageInLeftEye(
                    CapturedBitmap,
                    imageWidth,
                    imageHeight,
                    scaleFactor
                )

                fragmentGalleryBinding.nextButton.isEnabled = true

            } else {
                findNavController().popBackStack(R.id.userProfileScreenFragment, true)

                Toast.makeText(
                    requireContext(),
                    "Face not detected pls re-try again",
                    Toast.LENGTH_SHORT
                ).show()

            }

        }
    }

    private fun getLeftEyePupilPoints(faceLandmarkerResults: FaceLandmarkerResult): List<Line> {
        //left eye
        val leftEyeRightX =
            faceLandmarkerResults.faceLandmarks()[0].get(469).x() * imageWidth * scaleFactor
        val leftEyeRightY =
            faceLandmarkerResults.faceLandmarks()[0].get(469).y() * imageHeight * scaleFactor


        val leftEyeLeftX =
            faceLandmarkerResults.faceLandmarks()[0].get(471).x() * imageWidth * scaleFactor
        val leftEyeLeftY =
            faceLandmarkerResults.faceLandmarks()[0].get(471).y() * imageHeight * scaleFactor


        val leftEyeTopX =
            faceLandmarkerResults.faceLandmarks()[0].get(470).x() * imageWidth * scaleFactor
        val leftEyeTopY =
            faceLandmarkerResults.faceLandmarks()[0].get(470).y() * imageHeight * scaleFactor


        val leftEyeBottomX =
            faceLandmarkerResults.faceLandmarks()[0].get(472).x() * imageWidth * scaleFactor
        val leftEyeBottomY =
            faceLandmarkerResults.faceLandmarks()[0].get(472).y() * imageHeight * scaleFactor


        val listOfPointsLeftEyePupil = mutableListOf<Line>()

//        println("1st line: " + leftEyeRightX.roundToInt() + " " + leftEyeRightY.roundToInt() + " " + leftEyeBottomX.roundToInt() + " " + leftEyeBottomY.roundToInt())

        listOfPointsLeftEyePupil.add(
            Line(
                leftEyeRightX.roundToInt(),
                leftEyeRightY.roundToInt(),
                leftEyeBottomX.roundToInt(),
                leftEyeBottomY.roundToInt()
            )
        )

//        println("2nd line: " + leftEyeBottomX.roundToInt() + " " + leftEyeBottomY.roundToInt() + " " + leftEyeLeftX.roundToInt() + " " + leftEyeLeftY.roundToInt())


        listOfPointsLeftEyePupil.add(
            Line(
                leftEyeBottomX.roundToInt(),
                leftEyeBottomY.roundToInt(),
                leftEyeLeftX.roundToInt(),
                leftEyeLeftY.roundToInt()
            )
        )

//        println("3rd line: " + leftEyeLeftX.roundToInt() + " " + leftEyeLeftY.roundToInt() + " " + leftEyeTopX.roundToInt() + " " + leftEyeTopY.roundToInt())

        listOfPointsLeftEyePupil.add(
            Line(
                leftEyeLeftX.roundToInt(),
                leftEyeLeftY.roundToInt(),
                leftEyeTopX.roundToInt(),
                leftEyeTopY.roundToInt()
            )
        )

//        println("4th line: " + leftEyeTopX.roundToInt() + " " + leftEyeTopY.roundToInt() + " " + leftEyeRightX.roundToInt() + " " + leftEyeRightY.roundToInt())

        listOfPointsLeftEyePupil.add(
            Line(
                leftEyeTopX.roundToInt(),
                leftEyeTopY.roundToInt(),
                leftEyeRightX.roundToInt(),
                leftEyeRightY.roundToInt()
            )
        )



        return listOfPointsLeftEyePupil

    }

    private fun getRightEyePupilPoints(faceLandmarkerResults: FaceLandmarkerResult): List<Line> {
        //right eye
        val rightEyeRightX =
            faceLandmarkerResults.faceLandmarks()[0].get(474).x() * imageWidth * scaleFactor
        val rightEyeRightY =
            faceLandmarkerResults.faceLandmarks()[0].get(474).y() * imageHeight * scaleFactor

        val rightEyeLeftX =
            faceLandmarkerResults.faceLandmarks()[0].get(476).x() * imageWidth * scaleFactor
        val rightEyeLeftY =
            faceLandmarkerResults.faceLandmarks()[0].get(476).y() * imageHeight * scaleFactor

        val rightEyeTopX =
            faceLandmarkerResults.faceLandmarks()[0].get(475).x() * imageWidth * scaleFactor
        val rightEyeTopY =
            faceLandmarkerResults.faceLandmarks()[0].get(475).y() * imageHeight * scaleFactor

        val rightEyeBottomX =
            faceLandmarkerResults.faceLandmarks()[0].get(477).x() * imageWidth * scaleFactor
        val rightEyeBottomY =
            faceLandmarkerResults.faceLandmarks()[0].get(477).y() * imageHeight * scaleFactor


        val listOfPointsRightEyePupil = mutableListOf<Line>()

//        println("rightEye 1st line: " + rightEyeRightX.roundToInt() + " " + rightEyeRightY.roundToInt() + " " + rightEyeTopX.roundToInt() + " " + rightEyeTopY.roundToInt())

        listOfPointsRightEyePupil.add(
            Line(
                rightEyeRightX.roundToInt(),
                rightEyeRightY.roundToInt(),
                rightEyeTopX.roundToInt(),
                rightEyeTopY.roundToInt()
            )
        )

//        println("rightEye 2nd line: " + rightEyeTopX.roundToInt() + " " + rightEyeTopY.roundToInt() + " " + rightEyeLeftX.roundToInt() + " " + rightEyeLeftY.roundToInt())


        listOfPointsRightEyePupil.add(
            Line(
                rightEyeTopX.roundToInt(),
                rightEyeTopY.roundToInt(),
                rightEyeLeftX.roundToInt(),
                rightEyeLeftY.roundToInt()
            )
        )

//        println("rightEye 3rd line: " + rightEyeLeftX.roundToInt() + " " + rightEyeLeftY.roundToInt() + " " + rightEyeBottomX.roundToInt() + " " + rightEyeBottomY.roundToInt())

        listOfPointsRightEyePupil.add(
            Line(
                rightEyeLeftX.roundToInt(),
                rightEyeLeftY.roundToInt(),
                rightEyeBottomX.roundToInt(),
                rightEyeBottomY.roundToInt()
            )
        )

//        println("rightEye 4th line: " + rightEyeBottomX.roundToInt() + " " + rightEyeBottomY.roundToInt() + " " + rightEyeRightX.roundToInt() + " " + rightEyeRightY.roundToInt())

        listOfPointsRightEyePupil.add(
            Line(
                rightEyeBottomX.roundToInt(),
                rightEyeBottomY.roundToInt(),
                rightEyeRightX.roundToInt(),
                rightEyeRightY.roundToInt()
            )
        )



        return listOfPointsRightEyePupil


    }

    fun showRedPercentageInRightEye(
        bitmap: Bitmap,
        imageWidth: Int,
        imageHeight: Int,
        scaleFactor: Float
    ) {


        val listOfPointsRightEye = mutableListOf<Line>()

        //Right Eye
        FaceLandmarker.FACE_LANDMARKS_RIGHT_EYE.forEach {

            val startX = faceLandmarkerResults!!.faceLandmarks().get(0).get(it!!.start())
                .x() * imageWidth * scaleFactor
            val startY = faceLandmarkerResults!!.faceLandmarks().get(0).get(it.start())
                .y() * imageWidth * scaleFactor
            val endX = faceLandmarkerResults!!.faceLandmarks().get(0).get(it.end())
                .x() * imageHeight * scaleFactor
            val endY = faceLandmarkerResults!!.faceLandmarks().get(0).get(it.end())
                .y() * imageHeight * scaleFactor

            listOfPointsRightEye.add(
                Line(
                    startX.roundToInt(),
                    startY.roundToInt(),
                    endX.roundToInt(),
                    endY.roundToInt()
                )
            )


        }


        val filteredList = removeCommonPoints(
            listOfPupilPoints = getPointsInsideShape(
                getRightEyePupilPoints(faceLandmarkerResults!!)
            ), listOfWholeEyePoints = getPointsInsideShape(listOfPointsRightEye)
        )

        getEyeAverageColor(
            "RightEye",
            bitmap,
            listOfPoints = filteredList
        )


    }

    fun showRedPercentageInLeftEye(
        bitmap: Bitmap,
        imageWidth: Int,
        imageHeight: Int,
        scaleFactor: Float
    ) {

        val listOfPointsLeftEye = mutableListOf<Line>()
        //Left Eye
        FaceLandmarker.FACE_LANDMARKS_LEFT_EYE.forEach {
            val startX = faceLandmarkerResults!!.faceLandmarks().get(0).get(it!!.start())
                .x() * imageWidth * scaleFactor
            val startY = faceLandmarkerResults!!.faceLandmarks().get(0).get(it.start())
                .y() * imageWidth * scaleFactor
            val endX = faceLandmarkerResults!!.faceLandmarks().get(0).get(it.end())
                .x() * imageHeight * scaleFactor
            val endY = faceLandmarkerResults!!.faceLandmarks().get(0).get(it.end())
                .y() * imageHeight * scaleFactor

            listOfPointsLeftEye.add(
                Line(
                    startX.roundToInt(),
                    startY.roundToInt(),
                    endX.roundToInt(),
                    endY.roundToInt()
                )
            )


        }

        val filteredList = removeCommonPoints(
            listOfPupilPoints = getPointsInsideShape(
                getLeftEyePupilPoints(faceLandmarkerResults!!)
            ), listOfWholeEyePoints = getPointsInsideShape(listOfPointsLeftEye)
        )




        getEyeAverageColor(
            "LeftEye",
            bitmap,
            listOfPoints = filteredList
        )

    }


    fun getPointsInsideShape(lines: List<Line>): List<Point> {
        // Find the bounding box of the shape
        val minX = lines.minOf { minOf(it.startX, it.endX) }
        val maxX = lines.maxOf { maxOf(it.startX, it.endX) }
        val minY = lines.minOf { minOf(it.startY, it.endY) }
        val maxY = lines.maxOf { maxOf(it.startY, it.endY) }

        val pointsInside = mutableListOf<Point>()

        // Function to check if a point is inside the shape using the ray-casting algorithm
        fun isPointInsideShape(x: Int, y: Int): Boolean {
            var intersections = 0
            for (line in lines) {
                val startX = line.startX
                val startY = line.startY
                val endX = line.endX
                val endY = line.endY

                if ((startY > y) != (endY > y) && x < (endX - startX) * (y - startY) / (endY - startY) + startX) {
                    intersections++
                }
            }
            return intersections % 2 != 0
        }

        // Check each point in the bounding box
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                if (isPointInsideShape(x, y)) {
                    pointsInside.add(Point(x, y))
                }
            }
        }

        return pointsInside
    }

    fun removeCommonPoints(
        listOfPupilPoints: List<Point>,
        listOfWholeEyePoints: List<Point>
    ): List<Point> {
        val commonPoints = listOfPupilPoints.intersect(listOfWholeEyePoints).toSet()
        return listOfPupilPoints.filterNot { it in commonPoints } + listOfWholeEyePoints.filterNot { it in commonPoints }
    }


    fun getEyeAverageColor(
        name: String,
        bitmap: Bitmap,
        listOfPoints: List<Point>
    ) {


        var redSum = 0
        var greenSum = 0
        var blueSum = 0
        val pixelCount = listOfPoints.size



        for (i in 0 until pixelCount) {
            if (listOfPoints[i].x < bitmap.width && listOfPoints[i].y < bitmap.height) {
                val pixel = bitmap.getPixel(listOfPoints[i].x, listOfPoints[i].y)
                redSum += Color.red(pixel)
                greenSum += Color.green(pixel)
                blueSum += Color.blue(pixel)
            }
        }


        val averageRed = redSum / pixelCount
        val averageGreen = greenSum / pixelCount
        val averageBlue = blueSum / pixelCount


        val avgColor = Color.rgb(averageRed, averageGreen, averageBlue)

//        val hexColor = String.format("#%06X", 0xFFFFFF and avgColor)
//        println("ColorPercentage$name in Hex: $hexColor")


        if (name == "LeftEye") {
            LeftEyeRedPercentage = calculateRedPercentage(redSum, greenSum, blueSum)
            fragmentGalleryBinding.tvLeftRedPercentage.text =
                "LeftEye Red Percentage: $LeftEyeRedPercentage"
        } else {
            RightEyeRedPercentage = calculateRedPercentage(redSum, greenSum, blueSum)
            fragmentGalleryBinding.tvRightRedPercentage.text =
                "RightEye Red Percentage: $RightEyeRedPercentage"
        }

        if (RightEyeRedPercentage != 0.0 && LeftEyeRedPercentage != 0.0)
            hideLoading()

    }

    fun calculateRedPercentage(redSum: Int, greenSum: Int, blueSum: Int): Double {
        // Calculate the total sum of the red, green, and blue values
        val totalSum = redSum + greenSum + blueSum

        // Check if the total sum is zero to avoid division by zero
        if (totalSum == 0) {
            return 0.0
        }

        // Calculate the percentage of the red color
        val redPercentage = (redSum.toDouble() / totalSum) * 100


        return redPercentage
    }


    private fun runDetectionOnVideo(uri: Uri) {
        setUiEnabled(false)
        updateDisplayView(MediaType.VIDEO)

        with(fragmentGalleryBinding.videoView) {
            setVideoURI(uri)
            // mute the audio
            setOnPreparedListener { it.setVolume(0f, 0f) }
            requestFocus()
        }

        backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
        backgroundExecutor.execute {

            faceLandmarkerHelper = FaceLandmarkerHelper(
                context = requireContext(),
                runningMode = RunningMode.VIDEO,
                minFaceDetectionConfidence = viewModel.currentMinFaceDetectionConfidence,
                minFaceTrackingConfidence = viewModel.currentMinFaceTrackingConfidence,
                minFacePresenceConfidence = viewModel.currentMinFacePresenceConfidence,
                maxNumFaces = viewModel.currentMaxFaces,
                currentDelegate = viewModel.currentFaceDelegate
            )

            activity?.runOnUiThread {
                fragmentGalleryBinding.videoView.visibility = View.GONE
                fragmentGalleryBinding.progress.visibility = View.VISIBLE
            }

            faceLandmarkerHelper.detectVideoFile(uri, VIDEO_INTERVAL_MS)
                ?.let { resultBundle ->
                    activity?.runOnUiThread { displayVideoResult(resultBundle) }
                }
                ?: run { Log.e(TAG, "Error running face landmarker.") }

            faceLandmarkerHelper.clearFaceLandmarker()
        }
    }

    // Setup and display the video.
    private fun displayVideoResult(result: FaceLandmarkerHelper.VideoResultBundle) {

        fragmentGalleryBinding.videoView.visibility = View.VISIBLE
        fragmentGalleryBinding.progress.visibility = View.GONE

        fragmentGalleryBinding.videoView.start()
        val videoStartTimeMs = SystemClock.uptimeMillis()

        backgroundExecutor.scheduleAtFixedRate(
            {
                activity?.runOnUiThread {
                    val videoElapsedTimeMs =
                        SystemClock.uptimeMillis() - videoStartTimeMs
                    val resultIndex =
                        videoElapsedTimeMs.div(VIDEO_INTERVAL_MS).toInt()

                    if (resultIndex >= result.results.size || fragmentGalleryBinding.videoView.visibility == View.GONE) {
                        // The video playback has finished so we stop drawing bounding boxes
                        backgroundExecutor.shutdown()
                    } else {
                        fragmentGalleryBinding.overlay.setResults(
                            result.results[resultIndex],
                            result.inputImageHeight,
                            result.inputImageWidth,
                            RunningMode.VIDEO
                        )

//                        if (fragmentGalleryBinding.recyclerviewResults.scrollState != ViewPager2.SCROLL_STATE_DRAGGING) {
//                            faceBlendshapesResultAdapter.updateResults(result.results[resultIndex])
//                            faceBlendshapesResultAdapter.notifyDataSetChanged()
//                        }

                        setUiEnabled(true)

//                        fragmentGalleryBinding.bottomSheetLayout.inferenceTimeVal.text =
//                            String.format("%d ms", result.inferenceTime)
                    }
                }
            },
            0,
            VIDEO_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }

    private fun updateDisplayView(mediaType: MediaType) {
        fragmentGalleryBinding.imageResult.visibility =
            if (mediaType == MediaType.IMAGE) View.VISIBLE else View.GONE
        fragmentGalleryBinding.videoView.visibility =
            if (mediaType == MediaType.VIDEO) View.VISIBLE else View.GONE
        fragmentGalleryBinding.tvPlaceholder.visibility =
            if (mediaType == MediaType.UNKNOWN) View.VISIBLE else View.GONE
    }

    // Check the type of media that user selected.
    private fun loadMediaType(uri: Uri): MediaType {
        val mimeType = context?.contentResolver?.getType(uri)
        mimeType?.let {
            if (mimeType.startsWith("image")) return MediaType.IMAGE
            if (mimeType.startsWith("video")) return MediaType.VIDEO
        }

        return MediaType.UNKNOWN
    }

    private fun setUiEnabled(enabled: Boolean) {
//        fragmentGalleryBinding.fabGetContent.isEnabled = enabled
//        fragmentGalleryBinding.bottomSheetLayout.detectionThresholdMinus.isEnabled =
//            enabled
//        fragmentGalleryBinding.bottomSheetLayout.detectionThresholdPlus.isEnabled =
//            enabled
//        fragmentGalleryBinding.bottomSheetLayout.trackingThresholdMinus.isEnabled =
//            enabled
//        fragmentGalleryBinding.bottomSheetLayout.trackingThresholdPlus.isEnabled =
//            enabled
//        fragmentGalleryBinding.bottomSheetLayout.presenceThresholdMinus.isEnabled =
//            enabled
//        fragmentGalleryBinding.bottomSheetLayout.presenceThresholdPlus.isEnabled =
//            enabled
//        fragmentGalleryBinding.bottomSheetLayout.maxFacesPlus.isEnabled =
//            enabled
//        fragmentGalleryBinding.bottomSheetLayout.maxFacesMinus.isEnabled =
//            enabled
//        fragmentGalleryBinding.bottomSheetLayout.spinnerDelegate.isEnabled =
//            enabled
    }

    private fun classifyingError() {
        activity?.runOnUiThread {
            fragmentGalleryBinding.progress.visibility = View.GONE
            setUiEnabled(true)
            updateDisplayView(MediaType.UNKNOWN)
        }
    }

    override fun onError(error: String, errorCode: Int) {
        classifyingError()
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            if (errorCode == FaceLandmarkerHelper.GPU_ERROR) {
//                fragmentGalleryBinding.bottomSheetLayout.spinnerDelegate.setSelection(
//                    FaceLandmarkerHelper.DELEGATE_CPU,
//                    false
//                )
            }
        }
    }

    override fun onResults(resultBundle: FaceLandmarkerHelper.ResultBundle) {
        // no-op
    }

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(
                requireActivity(), R.id.fragment_container
            ).navigate(R.id.action_gallery_to_permissions)
        }

        // Start the FaceLandmarkerHelper again when users come back
        // to the foreground.

    }

    companion object {
        private const val TAG = "GalleryFragment"

        // Value used to get frames at specific intervals for inference (e.g. every 300ms)
        private const val VIDEO_INTERVAL_MS = 300L
    }


    private fun updateCurrentTest() {
        if (currentTestObject != null) {
            val testId = currentTestObject!!.id
            val userUpdatedTestObj = currentTestObject

            userUpdatedTestObj!!.right_eye_percentage = RightEyeRedPercentage
            userUpdatedTestObj.left_eye_percentage = LeftEyeRedPercentage

            fireBaseDatabase.child("tests").child(testId.toString()).setValue(userUpdatedTestObj)

            currentTestObject = userUpdatedTestObj
            findNavController().navigate(R.id.moveAnalysisFragment)

        } else {
            Toast.makeText(
                requireContext(),
                getString(R.string.error_in_getting_test),
                Toast.LENGTH_SHORT
            ).show()

            findNavController().popBackStack(R.id.userProfileScreenFragment, true)


        }


    }

    private fun showLoading() {
        fragmentGalleryBinding.loadingLayout.visibility = View.VISIBLE
        fragmentGalleryBinding.loadingOverlayView.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        fragmentGalleryBinding.loadingLayout.visibility = View.GONE
        fragmentGalleryBinding.loadingOverlayView.visibility = View.GONE
    }

}