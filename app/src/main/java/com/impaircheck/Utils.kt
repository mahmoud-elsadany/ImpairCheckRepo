package com.impaircheck

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.content.pm.PackageInfoCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.impaircheck.models.userTestsItem
import com.ml.quaterion.facenetdetection.BitmapUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    fun View.hideKeyboard() {
        val inputManager =
            context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.hideSoftInputFromWindow(windowToken, 0)
    }

    fun getFixedBitmap(context: Context,imageFileUri: Uri): Bitmap {
        var imageBitmap =
            BitmapUtils.getBitmapFromUri(context.contentResolver, imageFileUri)
        val exifInterface =
            ExifInterface(context.contentResolver.openInputStream(imageFileUri)!!)
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

    fun convertToJsonArray(data: Map<String, Map<String, Any>>): String {
        val gson = Gson()
        val jsonArray = JsonArray()

        data.forEach { (_, value) ->
            val jsonObject = JsonObject()
            value.forEach { (key, fieldValue) ->
                when (fieldValue) {
                    is Number -> jsonObject.addProperty(key, fieldValue)
                    is String -> jsonObject.addProperty(key, fieldValue)
                    is Boolean -> jsonObject.addProperty(key, fieldValue)
                    else -> jsonObject.addProperty(key, fieldValue.toString())
                }
            }
            jsonArray.add(jsonObject)
        }

        return gson.toJson(jsonArray)
    }


    fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
        val currentDate = Date()
        return dateFormat.format(currentDate)
    }

    fun getLatestTest(tests: List<userTestsItem>): userTestsItem? {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        return tests.maxByOrNull {
            dateFormat.parse(it.date)?.time ?: Long.MIN_VALUE
        }
    }
}