package com.impaircheck.fragment

import FaceNetModel
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import com.impaircheck.R
import android.util.Size
import android.view.*
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.fragment.findNavController
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.impaircheck.Utils.getFixedBitmap
import com.impaircheck.databinding.FragmentEnrollmentBinding
import com.ml.quaterion.facenetdetection.Constants
import com.ml.quaterion.facenetdetection.FaceAnalyserRepo
import com.ml.quaterion.facenetdetection.FileReader
import com.ml.quaterion.facenetdetection.FrameAnalyser
import com.ml.quaterion.facenetdetection.Logger
import com.ml.quaterion.facenetdetection.model.Models
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext


class EnrollmentFragment : Fragment(), FaceAnalyserRepo {


    private var unknownTrials: Int = 0


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
    private val cameraFacing = CameraSelector.LENS_FACING_FRONT


    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector
    private lateinit var camera: androidx.camera.core.Camera
    private var zoomFactor: Float = 0.0f
    private var takenNumberOfSubsamples = 0
    private var totalSubsamples = 1


    private lateinit var frameAnalyser: FrameAnalyser
    private lateinit var faceNetModel: FaceNetModel
    private lateinit var fileReader: FileReader
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    // <----------------------- User controls --------------------------->

    // Use the device's GPU to perform faster computations.
    // Refer https://www.tensorflow.org/lite/performance/gpu
    private val useGpu = true

    // Use XNNPack to accelerate inference.
    // Refer https://blog.tensorflow.org/2020/07/accelerating-tensorflow-lite-xnnpack-integration.html
    private val useXNNPack = true

    // You may the change the models here.
    // Use the model configs in Models.kt
    // Default is Models.FACENET ; Quantized models are faster
    private val modelInfo = Models.FACENET

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


        // Setup other initializations


        faceNetModel = FaceNetModel(requireContext(), modelInfo, useGpu, useXNNPack)
        frameAnalyser =
            FrameAnalyser(requireContext(), binding.bboxOverlay, faceNetModel, this)
        fileReader = FileReader(faceNetModel)

        mCameraLaunchTime = System.currentTimeMillis()

        proceedWithCamera()

        val images = ArrayList<Pair<String, Bitmap>>()
        if (Constants.imageUri != null) {
            images.add(Pair("User", getFixedBitmap(requireContext(), Constants.imageUri!!)))
        }
        if (images.isNotEmpty()) {
            fileReader.run(images, fileReaderCallback)
        }



        hideAndShowViews()

        binding.btnTakePhoto.setOnClickListener {

            takePhoto()
        }
        handleBtnCancel()


    }

    private fun handleBtnCancel() {
        binding.btnCancel.setOnClickListener {
            Constants.imageUri = null
            findNavController().navigateUp()
        }
    }

    private val fileReaderCallback = object : FileReader.ProcessCallback {
        override fun onProcessCompleted(
            data: ArrayList<Pair<String, FloatArray>>,
            numImagesWithNoFaces: Int
        ) {
            frameAnalyser.faceList = data
            Logger.log("Images parsed. Found $numImagesWithNoFaces images with no faces.")
        }
    }


    private fun hideAndShowViews() {


        binding.btnTakePhoto.visibility = View.VISIBLE

        binding.btnCancel.visibility = View.VISIBLE


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
                binding.tvZoomFactor.text = zoomFactor.toString() + "x"
                cameraControl.setZoomRatio(zoomFactor)

                return true
            }
        })


    private fun proceedWithCamera() {
        preCameraSetup()


    }

    private fun preCameraSetup() {
        cameraExecutor = Executors.newSingleThreadExecutor()

        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(requireContext()))
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
            val orientationEventListener =
                object : OrientationEventListener(this.requireContext()) {
                    override fun onOrientationChanged(orientation: Int) {
                        // Monitors orientation values to determine the target rotation value
                        val rotation: Int = when (orientation) {
                            in 45..134 -> Surface.ROTATION_270
                            in 135..224 -> Surface.ROTATION_180
                            in 225..314 -> Surface.ROTATION_90
                            else -> Surface.ROTATION_0
                        }

                        imageCapture.targetRotation = rotation
                    }
                }
            orientationEventListener.enable()

            preview.setSurfaceProvider(previewView.surfaceProvider)
            val imageFrameAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(previewView.width, previewView.height))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            imageFrameAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), frameAnalyser)



            try {
                processCameraProvider.unbindAll()
                camera = processCameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageFrameAnalysis,
                    imageCapture
                )
                camera.cameraControl.setZoomRatio(zoomFactor)

            } catch (e: Exception) {
                //navigate back
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder().build()
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(cameraFacing)
            .build()
        imageCapture = ImageCapture.Builder()
            .setTargetResolution(Size(binding.previewView.width, binding.previewView.height))
            .build()

        val orientationEventListener = object : OrientationEventListener(this.requireContext()) {
            override fun onOrientationChanged(orientation: Int) {
                // Monitors orientation values to determine the target rotation value
                val rotation: Int = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }

                imageCapture.targetRotation = rotation
            }
        }
        orientationEventListener.enable()
        preview.setSurfaceProvider(binding.previewView.surfaceProvider)
        val imageFrameAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(binding.previewView.width, binding.previewView.height))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        imageFrameAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), frameAnalyser)
        cameraProvider.bindToLifecycle(
            this as LifecycleOwner,
            cameraSelector,
            preview,
            imageFrameAnalysis,
            imageCapture
        )
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

    override fun theDetectedUser(name: String) {
        println("The detected user in camera is $name")

        if (unknownTrials >= 2) {
            showWarningDialog()
        } else {
            if (name != "User") {
                unknownTrials += 1
            } else {
                unknownTrials = 0
            }
        }
    }

    override fun numberOfFacesDetected(number: Int) {

        if (number > 0) {
            // Face detected
            binding.waringtvName.visibility = View.GONE
            binding.btnTakePhoto.isEnabled = true
        } else {
            // No face detected
            binding.waringtvName.visibility = View.VISIBLE
            binding.waringtvName.text = "No face detected"
            binding.btnTakePhoto.isEnabled = false

        }
    }

    private fun showWarningDialog() {
        binding.warningLayout.visibility = View.VISIBLE
        binding.warningOverlayView.visibility = View.VISIBLE
        binding.warningTitle.text = "Warning"
        binding.warningMessage.text = "Please show your face to the camera and stop cheating"
        binding.warningButton.setOnClickListener {
            binding.warningLayout.visibility = View.GONE
            binding.warningOverlayView.visibility = View.GONE
            unknownTrials = 0
        }
    }

    private fun launchChooseDirectoryIntent() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        directoryAccessLauncher.launch(intent)
    }

    private val directoryAccessLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val dirUri = it.data?.data ?: return@registerForActivityResult
            val childrenUri =
                DocumentsContract.buildChildDocumentsUriUsingTree(
                    dirUri,
                    DocumentsContract.getTreeDocumentId(dirUri)
                )
            val tree = DocumentFile.fromTreeUri(requireContext(), childrenUri)
            val images = ArrayList<Pair<String, Bitmap>>()
            var errorFound = false
            if (tree!!.listFiles().isNotEmpty()) {
                for (doc in tree.listFiles()) {
                    if (doc.isDirectory && !errorFound) {
                        val name = doc.name!!
                        for (imageDocFile in doc.listFiles()) {
                            try {
                                images.add(
                                    Pair(
                                        name,
                                        getFixedBitmap(requireContext(), imageDocFile.uri)
                                    )
                                )
                            } catch (e: Exception) {
                                errorFound = true
                                break
                            }
                        }
                    } else {
                        errorFound = true
                    }
                }
            } else {
                errorFound = true
                Logger.log("The selected folder doesn't contain any directories. Make sure that the file structure is as described in the README of the project and then restart the app.")
            }
            if (!errorFound) {
                try {
                    fileReader.run(images, fileReaderCallback)
                } catch (e: Exception) {
                    Logger.log("Error while parsing image file")
                    findNavController().popBackStack(R.id.userProfileScreenFragment, false)
                }
            } else {
                val alertDialog = AlertDialog.Builder(requireContext()).apply {
                    setTitle("Error while parsing directory")
                    setMessage("There were some errors while parsing the directory. Please see the log below. Make sure that the file structure is as described in the README of the project and then tap RESELECT")
                    setCancelable(false)
                    setPositiveButton("RESELECT") { dialog, which ->
                        dialog.dismiss()
                        launchChooseDirectoryIntent()
                    }
                    setNegativeButton("CANCEL") { dialog, which ->
                        dialog.dismiss()
                    }
                    create()
                }
                alertDialog.show()
            }
        }


}