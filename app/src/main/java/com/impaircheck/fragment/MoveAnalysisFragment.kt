package com.impaircheck.fragment

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.impaircheck.MainViewModel
import com.impaircheck.PoseLandmarkerHelper
import com.impaircheck.R
import com.impaircheck.databinding.FragmentMoveAnalysisBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import java.lang.Math.toDegrees
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

class MoveAnalysisFragment : Fragment(), PoseLandmarkerHelper.LandmarkerListener {

    companion object {
        private const val TAG = "Pose Landmarker"
    }

    private var isStarted: Boolean = false
    private lateinit var fragmentCameraBinding: FragmentMoveAnalysisBinding


    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    private val viewModel: MainViewModel by activityViewModels()
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService


    //first pose is stand on left leg
    //second pose is stand on right leg
    private var currentPose: Int = 1
    private var timer: CountDownTimer? = null
    private var lastExecutionTime: Long = 0


    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.


        // Start the PoseLandmarkerHelper again when users come back
        // to the foreground.
        backgroundExecutor.execute {
            if (this::poseLandmarkerHelper.isInitialized) {
                if (poseLandmarkerHelper.isClose()) {
                    poseLandmarkerHelper.setupPoseLandmarker()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (this::poseLandmarkerHelper.isInitialized) {
            viewModel.setMinPoseDetectionConfidence(poseLandmarkerHelper.minPoseDetectionConfidence)
            viewModel.setMinPoseTrackingConfidence(poseLandmarkerHelper.minPoseTrackingConfidence)
            viewModel.setMinPosePresenceConfidence(poseLandmarkerHelper.minPosePresenceConfidence)
            viewModel.setPoseDelegate(poseLandmarkerHelper.currentDelegate)

            // Close the PoseLandMarkerHelper and release resources
            backgroundExecutor.execute { poseLandmarkerHelper.clearPoseLandmarker() }
        }
    }

    override fun onDestroyView() {
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
        fragmentCameraBinding =
            FragmentMoveAnalysisBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize our background executor
        backgroundExecutor = Executors.newSingleThreadExecutor()


        handleUI()

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {
            // Set up the camera and its use cases
            setUpCamera()
        }

        // Create the PoseLandmarkerHelper that will handle the inference
        backgroundExecutor.execute {
            poseLandmarkerHelper = PoseLandmarkerHelper(
                context = requireContext(),
                runningMode = RunningMode.LIVE_STREAM,
                minPoseDetectionConfidence = viewModel.currentMinPoseDetectionConfidence,
                minPoseTrackingConfidence = viewModel.currentMinPoseTrackingConfidence,
                minPosePresenceConfidence = viewModel.currentMinPosePresenceConfidence,
                currentDelegate = viewModel.currentPoseDelegate,
                poseLandmarkerHelperListener = this
            )
        }

    }

    private fun handleUI() {

        fragmentCameraBinding.startFirstPoseButton.setOnClickListener {
            cancelCountdown()
            isStarted = false
            fragmentCameraBinding.firstPoseDescriptionLayout.visibility = View.GONE
            fragmentCameraBinding.secondPoseDescriptionLayout.visibility = View.GONE
            fragmentCameraBinding.finishDescriptionLayout.visibility = View.GONE
            currentPose = 1
        }

        fragmentCameraBinding.startSecondPoseButton.setOnClickListener {
            cancelCountdown()
            isStarted = false
            fragmentCameraBinding.firstPoseDescriptionLayout.visibility = View.GONE
            fragmentCameraBinding.secondPoseDescriptionLayout.visibility = View.GONE
            fragmentCameraBinding.finishDescriptionLayout.visibility = View.GONE
            currentPose = 2
        }

        fragmentCameraBinding.nextButton.setOnClickListener {
            //navigate to next fragment
        }

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
                        detectPose(image)
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

    private fun detectPose(imageProxy: ImageProxy) {
        if (this::poseLandmarkerHelper.isInitialized) {
            poseLandmarkerHelper.detectLiveStream(
                imageProxy = imageProxy,
                isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation =
            fragmentCameraBinding.viewFinder.display.rotation
    }

    // Update UI after pose have been detected. Extracts original
    // image height/width to scale and place the landmarks properly through
    // OverlayView
    override fun onResults(
        resultBundle: PoseLandmarkerHelper.ResultBundle
    ) {
        GlobalScope.launch(Dispatchers.Main) {
            if (fragmentCameraBinding != null) {

                // Pass necessary information to OverlayView for drawing on the canvas
                if (resultBundle.poseResults.isNotEmpty()) {
                    fragmentCameraBinding.overlay.setResults(
                        resultBundle.poseResults.first(),
                        resultBundle.inputImageHeight,
                        resultBundle.inputImageWidth,
                        RunningMode.LIVE_STREAM
                    )
                }

                //handle the move analysis of raising left foot
                /*
                23 - left hip
                25 - left knee
                27 - left ankle
                29 - left heel
                31 - left foot index

                24 - Right hip
                26 - Right knee
                28- Right ankle
                30- Right heel
                32- Right foot index
                 */

                if (resultBundle.poseResults.isNotEmpty()) {

//                    println(
//                        "pose results:${
//                            resultBundle.poseResults.first().landmarks().first().size
//                        } ${resultBundle.poseResults.first().landmarks().first()} "
//                    )


                    if (resultBundle.poseResults.first().landmarks().isNotEmpty()) {


                        if (resultBundle.poseResults.first().landmarks().first().size == 33) {

                            val currentTime = System.currentTimeMillis()

                            if (currentTime - lastExecutionTime >= 1000) {
                                // Code to be executed

                                val (isStanding, leg) = isStandingOnOneLeg(
                                    resultBundle.poseResults.first().landmarks().first()
                                )
                                println("isStanding: $isStanding, leg: $leg")
                                if (currentPose == leg) {
                                    isStarted = true
                                    startCountdown()
                                } else {
                                    cancelCountdown()
                                    fragmentCameraBinding.PoseHintTextView.text = if (currentPose == 1)
                                        getString(R.string.make_sure_first_pose_visible) else getString(
                                        R.string.make_sure_second_pose_visible
                                    )

                                }


                                lastExecutionTime = currentTime
                            }



                        } else {
                            cancelCountdown()
                            fragmentCameraBinding.PoseHintTextView.text = if (currentPose == 1)
                                getString(R.string.make_sure_first_pose_visible) else getString(R.string.make_sure_second_pose_visible)

                        }


                    } else {
                        cancelCountdown()
                        fragmentCameraBinding.PoseHintTextView.text = if (currentPose == 1)
                            getString(R.string.make_sure_first_pose_visible) else getString(R.string.make_sure_second_pose_visible)


                    }

                } else {
                    cancelCountdown()
                    fragmentCameraBinding.PoseHintTextView.text = if (currentPose == 1)
                        getString(R.string.make_sure_first_pose_visible) else getString(R.string.make_sure_second_pose_visible)


                }

                // Force a redraw
                fragmentCameraBinding.overlay.invalidate()
            }
        }
    }

    data class Point3D(val x: Float, val y: Float, val z: Float)


    private fun startCountdown() {

        timer = object : CountDownTimer(15000, 1000) {

            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = (millisUntilFinished / 1000) % 60
                fragmentCameraBinding.PoseHintTextView.text = "$secondsRemaining seconds remaining"
            }

            override fun onFinish() {
                if (currentPose == 1) {
                    fragmentCameraBinding.PoseHintTextView.text = "first pose Finished"
                    fragmentCameraBinding.firstPoseDescriptionLayout.visibility = View.GONE
                    fragmentCameraBinding.secondPoseDescriptionLayout.visibility = View.VISIBLE
                } else {
                    fragmentCameraBinding.PoseHintTextView.text = "second pose Finished"
                    fragmentCameraBinding.firstPoseDescriptionLayout.visibility = View.GONE
                    fragmentCameraBinding.secondPoseDescriptionLayout.visibility = View.GONE

                    if (isStarted)
                        fragmentCameraBinding.finishDescriptionLayout.visibility = View.VISIBLE


                }
            }

        }
        timer?.start()


    }


    private fun cancelCountdown() {
//        fragmentCameraBinding.PoseTimeTextView.visibility = View.GONE
        timer?.cancel() // Cancel the countdown
    }


    //version 1
//    private fun isStandingOnOneLeg(
//        poseLandmarks: List<NormalizedLandmark>,
//    ): Pair<Boolean, String?> {
//
//
//
//        val leftHip = Point3D(poseLandmarks[23].x(), poseLandmarks[23].y(), poseLandmarks[23].z())
//        val leftKnee = Point3D(poseLandmarks[25].x(), poseLandmarks[25].y(), poseLandmarks[25].z())
//        val leftAnkle = Point3D(poseLandmarks[27].x(), poseLandmarks[27].y(), poseLandmarks[27].z())
//        val leftHeel = Point3D(poseLandmarks[29].x(), poseLandmarks[29].y(), poseLandmarks[29].z())
//        val leftFoot = Point3D(poseLandmarks[31].x(), poseLandmarks[31].y(), poseLandmarks[31].z())
//
//        val rightHip = Point3D(poseLandmarks[24].x(), poseLandmarks[24].y(), poseLandmarks[24].z())
//        val rightKnee = Point3D(poseLandmarks[26].x(), poseLandmarks[26].y(), poseLandmarks[26].z())
//        val rightAnkle = Point3D(poseLandmarks[28].x(), poseLandmarks[28].y(), poseLandmarks[28].z())
//        val rightHeel = Point3D(poseLandmarks[30].x(), poseLandmarks[30].y(), poseLandmarks[30].z())
//        val rightFoot = Point3D(poseLandmarks[32].x(), poseLandmarks[32].y(), poseLandmarks[32].z())
//
//
//
//
//        fun calculateDistance(p1: Point3D, p2: Point3D): Float {
//            return Math.sqrt(Math.pow((p1.x - p2.x).toDouble(), 2.0) + Math.pow((p1.y - p2.y).toDouble(), 2.0) + Math.pow(
//                (p1.z - p2.z).toDouble(), 2.0))
//                .toFloat()
//        }
//
//        val leftKneeAnkleDistance = calculateDistance(leftKnee, leftAnkle)
//        val rightKneeAnkleDistance = calculateDistance(rightKnee, rightAnkle)
//
//        val leftFootHeelDistance = calculateDistance(leftFoot, leftHeel)
//        val rightFootHeelDistance = calculateDistance(rightFoot, rightHeel)
//
//        val leftAnkleFootDistance = calculateDistance(leftAnkle, leftFoot)
//        val rightAnkleFootDistance = calculateDistance(rightAnkle, rightFoot)
//
//        val leftStanding = leftKneeAnkleDistance <= 0.2 || leftFootHeelDistance <= 0.2 || leftAnkleFootDistance <= 0.2
//        val rightStanding = rightKneeAnkleDistance <= 0.2 || rightFootHeelDistance <= 0.2 || rightAnkleFootDistance <= 0.2
//
//        return when {
//            leftStanding && !rightStanding -> Pair(true, "Left Leg")
//            rightStanding && !leftStanding -> Pair(true, "Right Leg")
//            else -> Pair(false, null)
//        }
//    }


    //version 2
//    private fun isStandingOnOneLeg(
//        poseLandmarks: List<NormalizedLandmark>,
//    ): Pair<Boolean, String?> {
//
//
//
//        val leftHip = Point3D(poseLandmarks[23].x(), poseLandmarks[23].y(), poseLandmarks[23].z())
//        val leftKnee = Point3D(poseLandmarks[25].x(), poseLandmarks[25].y(), poseLandmarks[25].z())
//        val leftAnkle = Point3D(poseLandmarks[27].x(), poseLandmarks[27].y(), poseLandmarks[27].z())
//        val leftHeel = Point3D(poseLandmarks[29].x(), poseLandmarks[29].y(), poseLandmarks[29].z())
//        val leftFoot = Point3D(poseLandmarks[31].x(), poseLandmarks[31].y(), poseLandmarks[31].z())
//
//        val rightHip = Point3D(poseLandmarks[24].x(), poseLandmarks[24].y(), poseLandmarks[24].z())
//        val rightKnee = Point3D(poseLandmarks[26].x(), poseLandmarks[26].y(), poseLandmarks[26].z())
//        val rightAnkle = Point3D(poseLandmarks[28].x(), poseLandmarks[28].y(), poseLandmarks[28].z())
//        val rightHeel = Point3D(poseLandmarks[30].x(), poseLandmarks[30].y(), poseLandmarks[30].z())
//        val rightFoot = Point3D(poseLandmarks[32].x(), poseLandmarks[32].y(), poseLandmarks[32].z())
//
//        fun isLegStraight(hip: Point3D, knee: Point3D, ankle: Point3D): Boolean {
//            val kneeHipDistance = Math.abs(knee.y - hip.y)
//            val ankleKneeDistance = Math.abs(ankle.y - knee.y)
//            return kneeHipDistance > 0.9 && ankleKneeDistance > 0.9
//        }
//
//        fun isFootLower(foot: Point3D, otherFoot: Point3D): Boolean {
//            return foot.y < otherFoot.y - 0.5
//        }
//
//        val leftLegStraight = isLegStraight(leftHip, leftKnee, leftAnkle)
//        val rightLegStraight = isLegStraight(rightHip, rightKnee, rightAnkle)
//
//        val leftFootLower = isFootLower(leftFoot, rightFoot)
//        val rightFootLower = isFootLower(rightFoot, leftFoot)
//
//        return when {
//            leftLegStraight && leftFootLower && !rightLegStraight -> Pair(true, "Left Leg")
//            rightLegStraight && rightFootLower && !leftLegStraight -> Pair(true, "Right Leg")
//            else -> Pair(false, null)
//        }
//    }


    //version 3
    private fun isStandingOnOneLeg(
        poseLandmarks: List<NormalizedLandmark>,
    ): Pair<Boolean, Int> {

        //1 - stand on left leg
        //2 - stand on right leg

        val leftHip = Point3D(poseLandmarks[23].x(), poseLandmarks[23].y(), poseLandmarks[23].z())
        val leftKnee = Point3D(poseLandmarks[25].x(), poseLandmarks[25].y(), poseLandmarks[25].z())
        val leftAnkle = Point3D(poseLandmarks[27].x(), poseLandmarks[27].y(), poseLandmarks[27].z())
        val leftHeel = Point3D(poseLandmarks[29].x(), poseLandmarks[29].y(), poseLandmarks[29].z())
        val leftFoot = Point3D(poseLandmarks[31].x(), poseLandmarks[31].y(), poseLandmarks[31].z())

        val rightHip = Point3D(poseLandmarks[24].x(), poseLandmarks[24].y(), poseLandmarks[24].z())
        val rightKnee = Point3D(poseLandmarks[26].x(), poseLandmarks[26].y(), poseLandmarks[26].z())
        val rightAnkle =
            Point3D(poseLandmarks[28].x(), poseLandmarks[28].y(), poseLandmarks[28].z())
        val rightHeel = Point3D(poseLandmarks[30].x(), poseLandmarks[30].y(), poseLandmarks[30].z())
        val rightFoot = Point3D(poseLandmarks[32].x(), poseLandmarks[32].y(), poseLandmarks[32].z())


        fun calculateDistance(p1: Point3D, p2: Point3D): Double {
            return sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2) + (p1.z - p2.z).pow(2)).toDouble()
        }

        fun calculateAngle(point1: Point3D, middlePoint: Point3D, point2: Point3D): Double {
            // Vector A from middlePoint to point1
            val vectorA = Point3D(
                point1.x - middlePoint.x,
                point1.y - middlePoint.y,
                point1.z - middlePoint.z
            )

            // Vector B from middlePoint to point2
            val vectorB = Point3D(
                point2.x - middlePoint.x,
                point2.y - middlePoint.y,
                point2.z - middlePoint.z
            )

            // Dot product of vectors A and B
            val dotProduct = vectorA.x * vectorB.x + vectorA.y * vectorB.y + vectorA.z * vectorB.z

            // Magnitudes of vectors A and B
            val magnitudeA =
                sqrt(vectorA.x * vectorA.x + vectorA.y * vectorA.y + vectorA.z * vectorA.z)
            val magnitudeB =
                sqrt(vectorB.x * vectorB.x + vectorB.y * vectorB.y + vectorB.z * vectorB.z)

            // Calculate the cosine of the angle
            val cosTheta = dotProduct / (magnitudeA * magnitudeB)

            // Return the angle in degrees
            return toDegrees(acos(cosTheta).toDouble())
        }

        fun isLegStraight(hip: Point3D, knee: Point3D, ankle: Point3D, label: String): Boolean {
            val angle = calculateAngle(hip, knee, ankle)
            println("POSE $label angle: $angle")
            return angle > 85 // threshold for straight leg
        }

        fun areFootAligned(foot: Point3D, otherFoot: Point3D): Boolean {
            val diff = abs(foot.y - otherFoot.y)
            val maxY = max(foot.y, otherFoot.y)
            val diffRatio = if (maxY != 0f) diff / maxY else 0f
            println(" FOOT Diff: $diff, Ratio: $diffRatio")
            //if smaller than 0.001, then the foot are mot aligned
            return diffRatio <= 0.001
        }

        fun areKneesAligned(leftKnee: Point3D, rightKnee: Point3D): Boolean {
            val aligningDiff = abs(leftKnee.x - rightKnee.x)
            val maxX = max(leftKnee.x, rightKnee.x)
            val diffRatio = if (maxX != 0f) aligningDiff / maxX else 0f
            println("POSE KneesAlign Diff: $aligningDiff, Ratio: $diffRatio")
            return diffRatio < 0.1
        }

        val leftLegStraight = isLegStraight(leftHip, leftKnee, leftAnkle, "left")
        val rightLegStraight = isLegStraight(rightHip, rightKnee, rightAnkle, "Right")

//        val leftFootLower = isFootLower(leftFoot, rightFoot,"left")
//        val rightFootLower = isFootLower(rightFoot, leftFoot,"Right")

        val footAligned = areFootAligned(rightFoot, leftFoot)

        val kneesAligned = areKneesAligned(leftKnee, rightKnee)

        return when {
            leftLegStraight && !footAligned && !rightLegStraight && !kneesAligned -> Pair(
                true,
                1
            )

            rightLegStraight && !footAligned && !leftLegStraight && !kneesAligned -> Pair(
                true,
                2
            )

            rightLegStraight && footAligned && leftLegStraight && kneesAligned -> Pair(
                true,
                0
            )

            else -> Pair(false, -1)
        }
    }


    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            if (errorCode == PoseLandmarkerHelper.GPU_ERROR) {
                // If GPU error is detected, switch to CPU
                viewModel.setPoseDelegate(PoseLandmarkerHelper.DELEGATE_CPU)
            }
        }
    }
}
