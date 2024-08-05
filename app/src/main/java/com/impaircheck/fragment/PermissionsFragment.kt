package com.impaircheck.fragment

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import com.impaircheck.R
import com.impaircheck.constants.IS_NEW_USER

private val PERMISSIONS_REQUIRED = if (android.os.Build.VERSION.SDK_INT > 32) {
    arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_MEDIA_LOCATION
    )
} else {
    arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
}

class PermissionsFragment : Fragment() {


    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted = permissions.entries.all { it.value }
            if (granted) {
                Toast.makeText(
                    context,
                    "All permissions granted",
                    Toast.LENGTH_LONG
                ).show()
                navigateToStart()
            } else {
                Toast.makeText(
                    context,
                    "One or more permissions were denied",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        IS_NEW_USER = false
        if (hasPermissions(requireContext())) {
            navigateToStart()
        } else {
            requestPermissionLauncher.launch(PERMISSIONS_REQUIRED)
        }
    }

    private fun navigateToStart() {
        lifecycleScope.launchWhenStarted {
            Navigation.findNavController(
                requireActivity(),
                R.id.fragment_container
            ).navigate(
                R.id.action_permissions_to_camera
            )
        }
    }


    /** Convenience method used to check if all permissions required by this app are granted */
    companion object {

        fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
            ContextCompat.checkSelfPermission(
                context,
                it
            ) == PackageManager.PERMISSION_GRANTED
        }
    }


}
