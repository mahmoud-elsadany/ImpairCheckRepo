package com.impaircheck

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object Utils {

    fun getAppVersion(context: Context): String {
        val packageInfo: PackageInfo
        try {
            packageInfo =
                context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return "V "
        }
        return packageInfo.versionName.toString()
    }

    fun getAppVersionCode(context: Context): Int {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val longVersionCode = PackageInfoCompat.getLongVersionCode(pInfo)
            return longVersionCode.toInt()
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return -1
    }

    fun showOneOptionDialog(
        context: Context,
        onConfirmButtonClicked: () -> Unit,
        title: String,
        message: String,
        positiveButtonText: String,
        isCancelable: Boolean = true
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setCancelable(isCancelable)
            .setMessage(message)
            .setPositiveButton(positiveButtonText) { dialog, which ->
                // Respond to positive button press
                dialog.dismiss()
                onConfirmButtonClicked()

            }.show()


    }

}