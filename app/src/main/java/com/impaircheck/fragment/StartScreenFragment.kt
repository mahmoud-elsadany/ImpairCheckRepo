package com.impaircheck.fragment

import android.content.Context
import android.graphics.Bitmap
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
import com.impaircheck.R
import com.impaircheck.databinding.FragmentEnrollmentBinding
import com.impaircheck.databinding.FragmentStartScreenBinding
import com.impaircheck.fragment.RegistrationFragment.Companion.RESULT_KEY_URI
import com.ml.quaterion.facenetdetection.Constants

class StartScreenFragment  : Fragment() {

    private lateinit var binding: FragmentStartScreenBinding


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (!::binding.isInitialized) {
            binding = FragmentStartScreenBinding.inflate(inflater, container, false)
        }

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
            it.hideKeyboard()
            Log.d("RegistrationFragment", "Enroll face button clicked")
            findNavController().navigate(R.id.enrollmentFragment)
            setFragmentResultListener(RESULT_KEY_URI) { _, bundle ->
                Constants.imageUri =
                    bundle.getParcelable(
                        RESULT_KEY_URI, Uri::class.java
                    )
                if(Constants.imageUri != null){
                    Log.d("RegistrationFragment", "URI: ${Constants.imageUri}")
                    Toast.makeText(requireContext(), "URI: ${Constants.imageUri}", Toast.LENGTH_SHORT).show()
                }

                updateButtonStates()


                // Handle the list of URIs
            }
        }

        binding.buttonSubmit.setOnClickListener {
            handleSubmit()
        }
    }

    private fun handleSubmit() {
        val name = binding.editTextName.text.toString()
        val id = binding.editTextID.text.toString()

        if (name.isEmpty() || id.isEmpty() ||  Constants.imageUri == null) {
            Toast.makeText(requireContext(), "Please fill all fields and capture an image", Toast.LENGTH_SHORT).show()
        } else {

        }
    }

    fun View.hideKeyboard() {
        val inputManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.hideSoftInputFromWindow(windowToken, 0)
    }

    private fun updateButtonStates() {
        val isNameFilled = binding.editTextName.text.isNotEmpty()
        val isIDFilled = binding.editTextID.text.isNotEmpty()
        val isImageCaptured =  Constants.imageUri != null

        binding.buttonCaptureImage.isEnabled = isNameFilled && isIDFilled
        binding.buttonSubmit.isEnabled = isNameFilled && isIDFilled && isImageCaptured
    }
}