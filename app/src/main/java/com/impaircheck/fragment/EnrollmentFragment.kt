package com.impaircheck.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.impaircheck.R
import android.util.Size
import android.view.*
import android.view.inputmethod.InputMethodManager
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import com.impaircheck.databinding.FragmentEnrollmentBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext


class EnrollmentFragment : Fragment() {
    private lateinit var binding: FragmentEnrollmentBinding
    private var imageUri: Uri? = null
    private var job: Job = Job()

    val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageCapture: ImageCapture
    private lateinit var outputDirectory: File
    private var mCameraLaunchTime: Long = 0
    private var isTakingPhoto = false


    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector
    private lateinit var camera: androidx.camera.core.Camera
    private var zoomFactor: Float = 0.0f
    private var takenNumberOfSubsamples = 0
    private var totalSubsamples = 1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (!::binding.isInitialized) {
            binding = FragmentEnrollmentBinding.inflate(inflater, container, false)
        }
        binding.root.isFocusableInTouchMode = true
        binding.root.requestFocus()


        return binding.root

    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBluetoothEventKeyListener(view)
        initUI()
    }

    private fun setupBluetoothEventKeyListener(view: View) {
        view.isFocusableInTouchMode = true
        view.requestFocus()

            view.setOnKeyListener { v, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_ENTER -> {

                            takePhoto()

                            true
                        }

                        else -> false
                    }
                } else {
                    false
                }
            }

    }


    private fun initUI() {


        proceedWithCamera()
        mCameraLaunchTime = System.currentTimeMillis()
        hideAndShowViews()

        binding.btnTakePhoto.setOnClickListener {

            takePhoto()
        }
        handleBtnDone()
        handleBtnCancel()



    }

    private fun handleBtnCancel() {
        binding.btnCancel.setOnClickListener {
            findNavController().navigateUp()
        }
    }



    private fun handleBtnDone() {
        binding.btnDone.setOnClickListener {
            sendImagesUrisToCallingFragment()
        }
    }


    private fun hideAndShowViews() {


            binding.btnTakePhoto.visibility = View.VISIBLE

            binding.btnCancel.visibility = View.VISIBLE
            binding.btnDone.visibility = View.VISIBLE


    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setUpZoomAndFocus() {


        scaleGestureDetector = getZoomScaleGestureDetector()
        gestureDetector = getTapToFocusGestureDetector()
        binding.touchableZoomFragment.setOnTouchListener { _, event ->
            // Pass the touch event to both the scale and gesture detectors

            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun getTapToFocusGestureDetector() = GestureDetector(
        requireContext(),
        object : GestureDetector.SimpleOnGestureListener() {


            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {

                val factory = binding.previewView.meteringPointFactory
                val point = factory.createPoint(e.x, e.y)

                val action = FocusMeteringAction.Builder(point).build()

                camera.cameraControl.startFocusAndMetering(action)
                return true
            }
        })

    private fun getZoomScaleGestureDetector() = ScaleGestureDetector(
        requireContext(),
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {

                // Clamp the zoom scale
                val cameraInfo = camera.cameraInfo
                val zoomState = cameraInfo.zoomState.value
                val maxZoomRatio = zoomState?.maxZoomRatio ?: 1.0f
                val minZoomRatio = zoomState?.minZoomRatio ?: 1.0f

                zoomFactor *= detector.scaleFactor

                zoomFactor = zoomFactor.coerceIn(minZoomRatio, maxZoomRatio)

                val cameraControl = camera.cameraControl
                binding.tvZoomFactor.text = zoomFactor.toString()+"x"
                cameraControl.setZoomRatio(zoomFactor)

                return true
            }
        })


    private fun proceedWithCamera() {
        preCameraSetup()
        startCamera()


        setUpZoomAndFocus()

    }

    private fun preCameraSetup() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        outputDirectory = getOutputDirectory()
    }

    private fun getOutputDirectory(): File {
        val mediaDirectory = context?.externalMediaDirs?.firstOrNull()?.let { mFile ->
            File(mFile, resources.getString(R.string.app_name)).apply {
                mkdirs()
            }
        }

        return if (mediaDirectory != null && mediaDirectory.exists()) mediaDirectory
        else requireContext().filesDir

    }

    @SuppressLint("RestrictedApi")
    private fun startCamera() {
        val cameraProvider = ProcessCameraProvider.getInstance(requireContext())
        cameraProvider.addListener({
            val processCameraProvider: ProcessCameraProvider = cameraProvider.get()
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            val previewView = binding.previewView


            val preview = Preview.Builder()

                .build()
                .also { mPreview ->
                    mPreview.setSurfaceProvider(previewView.surfaceProvider)
                }


            // Create the image capture use case with the same aspect ratio as the preview
            imageCapture = ImageCapture.Builder()
                .setTargetResolution(Size(previewView.width, previewView.height))
                .build()
            val orientationEventListener = object : OrientationEventListener(this.requireContext()) {
                override fun onOrientationChanged(orientation : Int) {
                    // Monitors orientation values to determine the target rotation value
                    val rotation : Int = when (orientation) {
                        in 45..134 -> Surface.ROTATION_270
                        in 135..224 -> Surface.ROTATION_180
                        in 225..314 -> Surface.ROTATION_90
                        else -> Surface.ROTATION_0
                    }

                    imageCapture.targetRotation = rotation
                }
            }
            orientationEventListener.enable()

            try {
                processCameraProvider.unbindAll()
                camera = processCameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                camera.cameraControl.setZoomRatio(zoomFactor)

            } catch (e: Exception) {
                //navigate back
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }


    private fun takePhoto() {
        if (::imageCapture.isInitialized)
            if (!isTakingPhoto) {   //prepare screen
                isTakingPhoto = true


                val imageCapture = imageCapture
                val photoFile = File(
                    outputDirectory,
                    SimpleDateFormat(
                        FILE_NAME_FORMAT,
                        Locale.getDefault()
                    ).format(System.currentTimeMillis()) + ".jpeg"
                )

                val outputOption = ImageCapture.OutputFileOptions
                    .Builder(photoFile)
                    .build()

                imageCapture.takePicture(outputOption,
                    ContextCompat.getMainExecutor(requireContext()),
                    object : ImageCapture.OnImageSavedCallback {

                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {



                            //exif issue workaround
                            val imgURI: Uri = Uri.fromFile(photoFile)

                            isTakingPhoto = false
                            imageUri = imgURI
                            takenNumberOfSubsamples++



                            if (takenNumberOfSubsamples == totalSubsamples)
                                sendImagesUrisToCallingFragment()
                        }

                        override fun onError(exception: ImageCaptureException) {
                            isTakingPhoto = false


                        }

                    })


            }

    }

    private fun sendImagesUrisToCallingFragment() {
        val resultBundle = bundleOf(
            RegistrationFragment.RESULT_KEY_URI to imageUri
        )

        setFragmentResult(RegistrationFragment.RESULT_KEY_URI, resultBundle)
        findNavController().navigateUp()
    }

    override fun onResume() {
        if (::camera.isInitialized) {
            camera.cameraControl.setZoomRatio(zoomFactor)

        }

        super.onResume()
    }

    override fun onDestroyView() {
        cameraExecutor.shutdown()
        super.onDestroyView()
    }
    companion object {
        private const val TAG = "CameraXBasic"
        const val FILE_NAME_FORMAT = "yy-MM-dd-HH-mm-ss-SSS"
    }

}