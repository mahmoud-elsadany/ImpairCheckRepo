package com.impaircheck.fragment

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import com.impaircheck.R
import com.impaircheck.databinding.FragmentRegisterationfragmentBinding
import com.ml.quaterion.facenetdetection.Constants

class RegistrationFragment : Fragment() {

    private var _binding: FragmentRegisterationfragmentBinding? = null
    private val binding get() = _binding!!



    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentRegisterationfragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set the click listener in onViewCreated

        binding.btnRecognizeFace.isEnabled = Constants.imageUri != null
        binding.btnRecognizeFace.setOnClickListener{
            findNavController().navigate(R.id.faceCameraFragment)
        }
        binding.btnEnrollFace.setOnClickListener {
            Log.d("RegistrationFragment", "Enroll face button clicked")
            findNavController().navigate(R.id.enrollmentFragment)
            setFragmentResultListener(RESULT_KEY_URI) { _, bundle ->
                Constants.imageUri =
                    bundle.getParcelable(
                        RESULT_KEY_URI, Uri::class.java
                    )
               if(Constants.imageUri != null){
                   binding.btnRecognizeFace.isEnabled=true
                   Log.d("RegistrationFragment", "URI: ${Constants.imageUri}")
                   Toast.makeText(requireContext(), "URI: ${Constants.imageUri}", Toast.LENGTH_SHORT).show()
               }


                // Handle the list of URIs
            }
        }


    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    companion object {
        const val RESULT_KEY_URI = "RESULT_KEY_URIS"
    }
}
