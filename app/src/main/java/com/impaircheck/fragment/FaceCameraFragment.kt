package com.impaircheck.fragment

import FaceNetModel
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.impaircheck.R
import com.impaircheck.databinding.FragmentFaceCameraBinding
import com.ml.quaterion.facenetdetection.BitmapUtils
import com.ml.quaterion.facenetdetection.Constants
import com.ml.quaterion.facenetdetection.FaceAnalyserRepo
import com.ml.quaterion.facenetdetection.FileReader
import com.ml.quaterion.facenetdetection.FrameAnalyser
import com.ml.quaterion.facenetdetection.Logger
import com.ml.quaterion.facenetdetection.model.Models
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

class FaceCameraFragment: Fragment(), FaceAnalyserRepo {


    private var unknownTrials: Int = 0

    // Serialized data will be stored ( in app's private storage ) with this filename.
    private val SERIALIZED_DATA_FILENAME = "image_data"

    // Shared Pref key to check if the data was stored.


    private lateinit var cameraBinding: FragmentFaceCameraBinding
    private lateinit var previewView: PreviewView
    private lateinit var frameAnalyser: FrameAnalyser
    private lateinit var faceNetModel: FaceNetModel
    private lateinit var fileReader: FileReader
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var isTakingPhoto = false
    // <----------------------- User controls --------------------------->

    // Use the device's GPU to perform faster computations.
    // Refer https://www.tensorflow.org/lite/performance/gpu
    private val useGpu = true
    private lateinit var imageCapture: ImageCapture

    // Use XNNPack to accelerate inference.
    // Refer https://blog.tensorflow.org/2020/07/accelerating-tensorflow-lite-xnnpack-integration.html
    private val useXNNPack = true
    var imageUri: Uri? = null
    private lateinit var outputDirectory: File

    // You may the change the models here.
    // Use the model configs in Models.kt
    // Default is Models.FACENET ; Quantized models are faster
    private val modelInfo = Models.FACENET

    // Camera Facing
    private val cameraFacing = CameraSelector.LENS_FACING_FRONT

    // <---------------------------------------------------------------->


    companion object {

        lateinit var logTextView: TextView

        fun setMessage(message: String) {
            logTextView.text = message
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        cameraBinding = FragmentFaceCameraBinding.inflate(inflater, container, false)
        previewView = cameraBinding.previewView
        logTextView = cameraBinding.logTextview
        logTextView.movementMethod = ScrollingMovementMethod()
        return cameraBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        // Setup other initializations
        faceNetModel = FaceNetModel(requireContext(), modelInfo, useGpu, useXNNPack)
        frameAnalyser =
            FrameAnalyser(requireContext(), cameraBinding.bboxOverlay, faceNetModel, this)
        fileReader = FileReader(faceNetModel)

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestCameraPermission()
        } else {
            startCameraPreview()
        }
        val images = ArrayList<Pair<String, Bitmap>>()
        images.add(Pair("User", getFixedBitmap(Constants.imageUri!!)))
        fileReader.run(images, fileReaderCallback)

    }

    private fun startCameraPreview() {
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

    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder().build()
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(cameraFacing)
            .build()
        imageCapture = ImageCapture.Builder()
            .setTargetResolution(Size(previewView.width, previewView.height))
            .build()
        cameraBinding.captureButton.setOnClickListener {
            takePhoto()
        }
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
        preview.setSurfaceProvider(previewView.surfaceProvider)
        val imageFrameAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(previewView.width, previewView.height))
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

    private fun requestCameraPermission() {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCameraPreview()
            } else {
                showPermissionDeniedDialog()
            }
        }

    private fun showPermissionDeniedDialog() {
        val alertDialog = AlertDialog.Builder(requireContext()).apply {
            setTitle("Camera Permission")
            setMessage("The app couldn't function without the camera permission.")
            setCancelable(false)
            setPositiveButton("ALLOW") { dialog, which ->
                dialog.dismiss()
                requestCameraPermission()
            }
            setNegativeButton("CLOSE") { dialog, which ->
                dialog.dismiss()
            }
            create()
        }
        alertDialog.show()
    }

    private fun takePhoto() {
        if (::imageCapture.isInitialized)
            if (!isTakingPhoto) {   //prepare screen
                isTakingPhoto = true


                val imageCapture = imageCapture
                val photoFile = File(
                    outputDirectory,
                    SimpleDateFormat(
                        EnrollmentFragment.FILE_NAME_FORMAT,
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
                            Log.d(
                                "CameraFragment",
                                "Image saved successfully, Image Uri: $imageUri"
                            )


                        }

                        override fun onError(exception: ImageCaptureException) {
                            isTakingPhoto = false


                        }

                    })


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
                                images.add(Pair(name, getFixedBitmap(imageDocFile.uri)))
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
                fileReader.run(images, fileReaderCallback)
                Logger.log("Detecting faces in ${images.size} images ...")
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

    private fun getFixedBitmap(imageFileUri: Uri): Bitmap {
        var imageBitmap =
            BitmapUtils.getBitmapFromUri(requireContext().contentResolver, imageFileUri)
        val exifInterface =
            ExifInterface(requireContext().contentResolver.openInputStream(imageFileUri)!!)
        imageBitmap =
            when (exifInterface.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> BitmapUtils.rotateBitmap(imageBitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> BitmapUtils.rotateBitmap(imageBitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> BitmapUtils.rotateBitmap(imageBitmap, 270f)
                else -> imageBitmap
            }
        return imageBitmap
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

    override fun theDetectedUser(name: String) {


        if (unknownTrials == 5) {
            showWarningDialog()
        }

        if (name != "User") {
            unknownTrials += 1
            cameraBinding.textView.text = "Please stop cheating and show your face .. you have ${5 - unknownTrials} trials left"
        }else{
            unknownTrials = 0
        }


    }


    // make a function that Show a warning dialog if the detected user is not the user and please stop cheating
    private fun showWarningDialog() {
        val alertDialog = AlertDialog.Builder(requireContext()).apply {
            setTitle("Warning")
            setMessage("The detected user is not you. Please stop cheating.")
            setCancelable(false)
            setPositiveButton("OK") { dialog, which ->
                dialog.dismiss()
            }
            create()
        }
        alertDialog.show()
    }

}