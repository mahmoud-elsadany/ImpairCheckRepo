package com.impaircheck

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.impaircheck.constants.APP_VERSION_CODE
import com.impaircheck.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var activityMainBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.fragment_container) as NavHostFragment
//        val navController = navHostFragment.navController
//        activityMainBinding.navigation.setupWithNavController(navController)

        handleRemoteConfig()

    }

    private fun handleRemoteConfig() {
        //handle remote config
        val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 500
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.activate()
        remoteConfig.fetch()
        remoteConfig.fetchAndActivate().addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val latest_version_code =
                    remoteConfig.getLong("latest_version_code")
                val android_update_url = remoteConfig.getString("android_update_url")

                println(
                    "remote config : latest_version_code = $latest_version_code"
                )

                if (latest_version_code > APP_VERSION_CODE)
                    Utils.showOneOptionDialog(
                        context = this,
                        onConfirmButtonClicked = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(android_update_url))
                            finish()
                            startActivity(intent)
                        },
                        title = getString(R.string.update_dialog_title),
                        message = getString(R.string.make_sure_to_update),
                        positiveButtonText = getString(R.string.update),
                        isCancelable = false
                    )


            } else {
                Toast.makeText(
                    this,
                    "Fetch failed",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }


    }


    override fun onBackPressed() {
        finish()
    }
}
