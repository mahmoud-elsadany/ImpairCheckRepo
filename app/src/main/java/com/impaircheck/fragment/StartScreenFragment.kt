package com.impaircheck.fragment

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.impaircheck.R
import com.impaircheck.Utils.convertToJsonArray
import com.impaircheck.Utils.hideKeyboard
import com.impaircheck.constants
import com.impaircheck.constants.IS_NEW_USER
import com.impaircheck.constants.currentUserId
import com.impaircheck.constants.fireBaseDatabase
import com.impaircheck.databinding.FragmentStartScreenBinding
import com.impaircheck.fragment.RegistrationFragment.Companion.RESULT_KEY_URI
import com.impaircheck.models.userData
import com.impaircheck.models.userDataItem
import com.ml.quaterion.facenetdetection.Constants
import java.io.File
import java.io.IOException

class StartScreenFragment : Fragment() {

    private lateinit var binding: FragmentStartScreenBinding


    private var filteredUsers: List<userDataItem> = emptyList()
    private var newUserData: userDataItem? = null


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (!::binding.isInitialized) {
            binding = FragmentStartScreenBinding.inflate(inflater, container, false)
        }

        handleFetchUsers()
        initViews()

        return binding.root
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun initViews() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateButtonStates()
            }

            override fun afterTextChanged(s: Editable?) {}
        }

        binding.editTextName.addTextChangedListener(textWatcher)
        binding.editTextID.addTextChangedListener(textWatcher)

        binding.buttonCaptureImage.setOnClickListener {

            if (checkIfUserExists(binding.editTextID.text.toString().toInt())) {
                // Handle the case where the user already exists
                handleCheckUserExists()
            } else {
                // Handle the case where the user does not exist
                if (!IS_NEW_USER) {
                    Toast.makeText(
                        requireContext(),
                        "User with ID ${binding.editTextID.text} does not exist. Please generate a new ID.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else
                    navigateToCamera(it)
            }


        }

        binding.buttonGenerateID.setOnClickListener {
            val id = (100..999999).random()
            binding.editTextID.setText(id.toString())
            binding.editTextID.isEnabled = false
            IS_NEW_USER = true
            newUserData = userDataItem(
                id,
                "",
                binding.editTextName.text.toString()
            )

            Toast.makeText(
                requireContext(),
                "memorize your ID to be able to login later with it: $id",
                Toast.LENGTH_LONG
            ).show()
        }

        binding.buttonSubmit.setOnClickListener {
            if (checkIfUserExists(binding.editTextID.text.toString().toInt())) {
//                Toast.makeText(
//                    requireContext(),
//                    "User with ID ${binding.editTextID.text} already exists",
//                    Toast.LENGTH_SHORT
//                ).show()

                currentUserId = binding.editTextID.text.toString().toInt()
                findNavController().navigate(R.id.userProfileScreenFragment)
            } else
                handleSubmitNewUser()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun navigateToCamera(view: View) {
        view.hideKeyboard()
        Log.d("RegistrationFragment", "Enroll face button clicked")
        findNavController().navigate(R.id.enrollmentFragment)
        setFragmentResultListener(RESULT_KEY_URI) { _, bundle ->
            Constants.imageUri =
                bundle.getParcelable(
                    RESULT_KEY_URI, Uri::class.java
                )

            println("is this new user: $IS_NEW_USER")

            if (IS_NEW_USER)
                uploadImageToFirebase(Constants.imageUri!!)
            else
                updateButtonStates()

        }
    }


    private fun handleSubmitNewUser() {
        val name = binding.editTextName.text.toString()
        val id = binding.editTextID.text.toString()

        if (name.isEmpty() || id.isEmpty() || Constants.imageUri == null) {
            Toast.makeText(
                requireContext(),
                "Please fill all fields and capture an image",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            fireBaseDatabase.child("users").child(id).setValue(newUserData)

            currentUserId = id.toInt()

            findNavController().navigate(R.id.userProfileScreenFragment)
        }
    }


    private fun updateButtonStates() {
        val isNameFilled = binding.editTextName.text.isNotEmpty()
        val isIDFilled = binding.editTextID.text.isNotEmpty()
        val isImageCaptured = Constants.imageUri != null

        binding.buttonCaptureImage.isEnabled = isNameFilled && isIDFilled
        binding.buttonSubmit.isEnabled = isNameFilled && isIDFilled && isImageCaptured
    }


    private fun handleFetchUsers() {
        // Fetch users from firebase database
        fireBaseDatabase.child("users").get().addOnSuccessListener { it ->
            Log.d("firebase", "Got value ${it.value}")
            Log.d(
                "firebase",
                "converted value ${convertToJsonArray(it.value as Map<String, Map<String, Any>>)}"
            )


            val jsonString = convertToJsonArray(it.value as Map<String, Map<String, Any>>)

            val gson = Gson()
            val listType = object : TypeToken<userData>() {}.type
            val userList: userData = gson.fromJson(jsonString, listType)


            filteredUsers = userList.filter { user -> user != null }


        }.addOnFailureListener {
            Log.e("firebase", "Error getting data", it)
        }
    }

    private fun checkIfUserExists(id: Int): Boolean {
        return filteredUsers.any { user -> user.id == id }
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun handleCheckUserExists() {
        val userId = binding.editTextID.text.toString().toIntOrNull()

        val currentUser: userDataItem? = filteredUsers.find { user -> user.id == userId }
        if (currentUser != null) {
            // Handle the found user
            currentUser.imageLink.let { downloadImageFromFirebase(it) }

        } else {
            // Handle the case where no user is found
            Log.d("firebase", "No user found with ID: $userId")
        }
    }

    private fun uploadImageToFirebase(imageUri: Uri) {
        showLoading()
        val storageReference: StorageReference =
            FirebaseStorage.getInstance().reference.child("usersImages/${binding.editTextID.text}_${System.currentTimeMillis()}.jpg")

        storageReference.putFile(imageUri)
            .addOnSuccessListener { taskSnapshot ->
                storageReference.downloadUrl.addOnSuccessListener { uri ->
                    Log.d("firebase", "Image uploaded successfully. URL: $uri")
                    newUserData = userDataItem(
                        binding.editTextID.text.toString().toInt(),
                        uri.toString(),
                        binding.editTextName.text.toString()
                    )
                    hideLoading()

                    updateButtonStates()

                }
            }
            .addOnFailureListener { exception ->
                Log.e("firebase", "Failed to upload image", exception)
            }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun downloadImageFromFirebase(imageLink: String) {

        showLoading()
        Thread {
            try {
                val future = Glide.with(this)
                    .asFile()
                    .load(imageLink)
                    .submit()

                val imageFile = future.get()
                val savedFile = saveFileToExternalStorage(requireContext(), imageFile)

                requireActivity().runOnUiThread {
                    if (savedFile != null) {
                        val fileUri: Uri = Uri.fromFile(savedFile)
                        Log.d("MainActivity", "Image saved. URI: $fileUri")
                        // Do something with the URI, e.g., pass it to another activity or fragment
                        hideLoading()
                        Constants.imageUri = fileUri

                        navigateToCamera(requireView())
                    } else {
                        Log.e("MainActivity", "Failed to save image")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()


    }

    private fun saveFileToExternalStorage(context: Context, file: File): File? {
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val savedFile = File(storageDir, file.name)

        return try {
            file.copyTo(savedFile, overwrite = true)
            savedFile
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun showLoading() {
        binding.loadingLayout.visibility = View.VISIBLE
        binding.loadingOverlayView.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        binding.loadingLayout.visibility = View.GONE
        binding.loadingOverlayView.visibility = View.GONE
    }


}