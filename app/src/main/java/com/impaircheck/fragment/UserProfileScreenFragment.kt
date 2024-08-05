package com.impaircheck.fragment

import android.app.AlertDialog
import android.content.DialogInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import com.impaircheck.R
import com.impaircheck.constants.IS_NEW_USER
import com.impaircheck.databinding.FragmentStartScreenBinding
import com.impaircheck.databinding.FragmentUserProfileBinding
import com.impaircheck.fragment.RegistrationFragment.Companion.RESULT_KEY_URI
import com.ml.quaterion.facenetdetection.Constants

class UserProfileScreenFragment : Fragment() {

    private lateinit var binding: FragmentUserProfileBinding


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (!::binding.isInitialized) {
            binding = FragmentUserProfileBinding.inflate(inflater, container, false)
        }

        initViews()

        return binding.root
    }

    private fun initViews() {


        IS_NEW_USER = false
        binding.lastTestDate.text = getString(R.string.last_test_date) + " "
        binding.lastTestScore.text = getString(R.string.last_test_score) + " "

        binding.buttonReTest.setOnClickListener {
            showDialog()
        }

    }

    private fun showDialog() {
        val dialog = AlertDialog.Builder(requireContext())
        dialog.setTitle(getString(R.string.dialog_title))
        dialog.setMessage(getString(R.string.dialog_message))
        dialog.setPositiveButton("OK") { dialogInterface: DialogInterface, i: Int ->
            dialogInterface.dismiss()
            findNavController().navigate(R.id.faceCameraFragment)
        }
        dialog.show()
    }

}