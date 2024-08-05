package com.impaircheck.fragment

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.impaircheck.R
import com.impaircheck.Utils.hideKeyboard
import com.impaircheck.constants
import com.impaircheck.constants.fireBaseDatabase
import com.impaircheck.databinding.FragmentStartScreenBinding
import com.impaircheck.fragment.RegistrationFragment.Companion.RESULT_KEY_URI
import com.impaircheck.models.userData
import com.impaircheck.models.userDataItem
import com.impaircheck.models.userDataUtils.convertToJsonArray
import com.ml.quaterion.facenetdetection.Constants

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
                navigateToCamera(it)
            }


        }

        binding.buttonGenerateID.setOnClickListener {
            val id = (100..999999).random()
            binding.editTextID.setText(id.toString())
            binding.editTextID.isEnabled = false
            newUserData = userDataItem(
                id,
                "",
                binding.editTextName.text.toString()
            )
        }

        binding.buttonSubmit.setOnClickListener {
            if (checkIfUserExists(binding.editTextID.text.toString().toInt())) {
//                Toast.makeText(
//                    requireContext(),
//                    "User with ID ${binding.editTextID.text} already exists",
//                    Toast.LENGTH_SHORT
//                ).show()
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

//            findNavController().navigate(R.id.userProfileScreenFragment)
        }
    }


    private fun updateButtonStates() {
        val isNameFilled = binding.editTextName.text.isNotEmpty()
        val isIDFilled = binding.editTextID.text.isNotEmpty()
        val isImageCaptured = Constants.imageUri != null

        if (isImageCaptured) {
            uploadImageToFirebase(Constants.imageUri!!)
        }

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
        return filteredUsers.any { user -> user.Id == id }
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun handleCheckUserExists() {
        val userId = binding.editTextID.text.toString().toIntOrNull()

        val currentUser: userDataItem? = filteredUsers.find { user -> user.Id == userId }
        if (currentUser != null) {
            // Handle the found user
            currentUser.imageLink.let { downloadImageFromFirebase(it) }

        } else {
            // Handle the case where no user is found
            Log.d("firebase", "No user found with ID: $userId")
        }
    }

    private fun uploadImageToFirebase(imageUri: Uri) {
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
                }
            }
            .addOnFailureListener { exception ->
                Log.e("firebase", "Failed to upload image", exception)
            }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun downloadImageFromFirebase(imageLink: String) {
        val storageReference =
            FirebaseStorage.getInstance().reference.child(imageLink)

        storageReference.downloadUrl.addOnSuccessListener { uri ->
            // Load the image using Picasso
            Log.d("firebase", "Image downloaded successfully. URL: $uri")
            Constants.imageUri = uri
//            navigateToCamera(binding.root)

        }.addOnFailureListener { exception ->
            Log.e("firebase", "Failed to download image", exception)
        }
    }
}