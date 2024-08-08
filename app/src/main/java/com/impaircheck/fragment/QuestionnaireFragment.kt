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
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.asTextOrNull
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.impaircheck.BuildConfig
import com.impaircheck.R
import com.impaircheck.compose.ChatScreen
import com.impaircheck.constants.currentTestObject
import com.impaircheck.constants.fireBaseDatabase
import com.impaircheck.databinding.FragmentFaceCameraBinding
import com.impaircheck.databinding.FragmentQuestionnaireBinding
import com.ml.quaterion.facenetdetection.BitmapUtils
import com.ml.quaterion.facenetdetection.Constants
import com.ml.quaterion.facenetdetection.FaceAnalyserRepo
import com.ml.quaterion.facenetdetection.FileReader
import com.ml.quaterion.facenetdetection.FrameAnalyser
import com.ml.quaterion.facenetdetection.Logger
import com.ml.quaterion.facenetdetection.model.Models
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors


class QuestionnaireFragment : Fragment(), FaceAnalyserRepo {


    private val messages = mutableStateListOf<String>()

    private val chatHistory = mutableStateListOf<Content>()

    private lateinit var model: GenerativeModel


    private var unknownTrials: Int = 0
    private var noFaceTrials: Int = 0

    private lateinit var cameraBinding: FragmentQuestionnaireBinding
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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model = GenerativeModel(
            "gemini-1.5-flash",
            BuildConfig.googelAPIKeyForAI,

            generationConfig = generationConfig {
                temperature = 0.05f
                topK = 64
                topP = 0.95f
                maxOutputTokens = 8192
                responseMimeType = "text/plain"
            },
            systemInstruction = content {
                text(
                    "\n### Interaction Guidelines\n1. **Introduction and Consent**\n   - Greet the user and introduce yourself as an assessment tool.\n   - Explain the purpose of the assessment clearly.\n   - Ask for the user's consent to proceed with the questions.\n   - Ask for the user one question at a time \n\n2. **Questionnaire**\n   - **Basic Information**\n     - Ask for basic information such as age and gender.\n   - **Physical Symptoms**\n     - Inquire about physical symptoms such as dizziness, blurred vision, slurred speech, or lack of coordination.\n   - **Mental State**\n     - Ask questions related to their mental state, such as feelings of confusion, difficulty concentrating, or memory issues.\n   - **Behavioral Indicators**\n     - Assess their recent behavior by asking if they have been more talkative, less inhibited, or engaging in risky activities.\n   - **Substance Use**\n     - Directly ask if they have consumed alcohol or drugs recently. If yes, inquire about the type, amount, and time of consumption.\n   - **Self-Assessment**\n     - Ask the user to rate their own perceived level of impairment on a scale (e.g., 1 to 10).\n\n3. **Response Analysis**\n   - Implement logic to analyze the user's responses for key indicators of impairment.\n\n\n5. **Confidentiality and Privacy**\n   - Ensure the user that their responses are confidential and will not be shared with third parties.\n   - Explain how their data will be used and stored securely.\n\n\n### Sample Questions\n1. **Basic Information**\n   - \"Please provide your age.\"\n   - \"What is your gender?\"\n   - \"What is the date today?”” Check if the answer is right from calendar\n   - \"What is your birthdate?” Check if the answer is right from the previous answers of his age \n\n2. **Physical Symptoms**\n   - \"Are you experiencing dizziness or blurred vision?\"\n   - \"Is your speech slurred or difficult to understand?\"\n\n3. **Mental State**\n   - \"Are you feeling confused or having trouble concentrating?\"\n   - \"Do you have difficulty remembering recent events?\"\n\n4. **Behavioral Indicators**\n   - \"Have you noticed a change in your level of inhibition or risk-taking behavior?\"\n   - \"Have you been more talkative or outgoing than usual?\"\n\n5. **Substance Use**\n   - \"Have you consumed alcohol or drugs in the past few hours? If yes, please specify.\"\n   - \"How much did you consume and at what time?\"\n\n6. **Self-Assessment**\n   - \"On a scale from 1 to 10, how impaired do you feel right now?\"\n\n### Implementation Notes\n- Use clear, simple language to ensure the questions are easily understood.\n- Allow the user to skip questions if they feel uncomfortable answering.\n- Provide feedback and reassurance throughout the assessment to keep the user engaged.\n- make it max 7 questions\n- Ask the user one question at a time \n"
                )
            },

            )

        sendMessage("hi")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        cameraBinding = FragmentQuestionnaireBinding.inflate(inflater, container, false)
        previewView = cameraBinding.previewView

        cameraBinding.composeView.apply {
            setContent {
                ChatScreen(
                    messages = messages.asReversed(),
                    onSendMessage = { message ->
                        sendMessage(message)
                    }
                )
            }
        }


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


    private fun sendMessage(message: String) {
        messages.add("User: $message")
        chatHistory.add(content("user") { text(message) })


        lifecycleScope.launch {
            val chat = model.startChat(chatHistory)
            val response = chat.sendMessage(message)

            response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.asTextOrNull()?.let {
                messages.add("Bot: $it")
                chatHistory.add(content("model") { text(it) })

                if (it.contains("Thank you for") || it.contains("Take care")) {
                    showCompletedDialog()
                }

            }
        }
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
                            Constants.imageUri = Uri.fromFile(photoFile)

                            isTakingPhoto = false
                            Log.d(
                                "CameraFragment",
                                "Image saved successfully, Image Uri: ${Constants.imageUri}"
                            )

                            if (unknownTrials == 0) {
                                findNavController().navigate(R.id.gallery_fragment)
                            }


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
                try {
                    fileReader.run(images, fileReaderCallback)
                } catch (e: Exception) {
                    Logger.log("Error while parsing image file")
                    findNavController().popBackStack(R.id.userProfileScreenFragment, false)
                }
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

        println("The detected user in camera is $name")

        if (unknownTrials >= 5) {
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
        println("The number of faces detected is $number")
        if (number == 0) {
            // No face detected

            if (noFaceTrials >= 5) {
                showNotShowingFaceDialog()
            } else {
                noFaceTrials += 1

            }

        } else {
            noFaceTrials = 0
        }
    }


    // make a function that Show a warning dialog if the detected user is not the user and please stop cheating
    private fun showWarningDialog() {

        cameraBinding.warningLayout.visibility = View.VISIBLE
        cameraBinding.warningOverlayView.visibility = View.VISIBLE
        cameraBinding.warningTitle.text = "Warning"
        cameraBinding.warningMessage.text = "The detected user is not you. Please stop cheating."
        cameraBinding.warningButton.setOnClickListener {
            cameraBinding.warningLayout.visibility = View.GONE
            cameraBinding.warningOverlayView.visibility = View.GONE
            unknownTrials = 0
        }

    }

    //make a funcrtion that show a dialog that the user has completed the questionnaire
    private fun showCompletedDialog() {
        val alertDialog = AlertDialog.Builder(requireContext()).apply {
            setTitle("Completed")
            setMessage("Thank you for completing the questionnaire.")
            setCancelable(false)
            setPositiveButton("OK") { dialog, which ->
                dialog.dismiss()

                updateCurrentTest()
            }
            create()
        }
        alertDialog.show()
    }

    // make a function that show a dialog that warn of not showing a face who is doing the exam
    private fun showNotShowingFaceDialog() {

        cameraBinding.warningLayout.visibility = View.VISIBLE
        cameraBinding.warningOverlayView.visibility = View.VISIBLE
        cameraBinding.warningTitle.text = "Warning"
        cameraBinding.warningMessage.text = "Please show your face to the camera."
        cameraBinding.warningButton.setOnClickListener {
            cameraBinding.warningLayout.visibility = View.GONE
            cameraBinding.warningOverlayView.visibility = View.GONE
            findNavController().popBackStack(R.id.questionnaire_fragment, false)
        }


    }


    private fun updateCurrentTest() {
        if (currentTestObject != null) {
            val testId = currentTestObject!!.id
            val userUpdatedTestObj = currentTestObject

            userUpdatedTestObj!!.questionnaire_chat = convertMessagesToString(messages)
            userUpdatedTestObj.state = "Pending Results"


            fireBaseDatabase.child("tests").child(testId.toString()).setValue(userUpdatedTestObj)

            currentTestObject = userUpdatedTestObj

            //pop the back stack to the user profile fragment
            findNavController().popBackStack(R.id.userProfileScreenFragment, false)
        } else {
            Toast.makeText(
                requireContext(),
                getString(R.string.error_in_getting_test),
                Toast.LENGTH_SHORT
            ).show()

            findNavController().popBackStack(R.id.userProfileScreenFragment, false)


        }


    }

    private fun convertMessagesToString(messages: MutableList<String>, separator: String = ", "): String {
        return messages.joinToString(separator)
    }


}