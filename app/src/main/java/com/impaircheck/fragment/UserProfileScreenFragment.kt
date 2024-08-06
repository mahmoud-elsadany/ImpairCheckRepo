package com.impaircheck.fragment

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.impaircheck.R
import com.impaircheck.Utils.convertToJsonArray
import com.impaircheck.Utils.getCurrentDate
import com.impaircheck.Utils.getLatestTest
import com.impaircheck.constants.IS_NEW_USER
import com.impaircheck.constants.currentTestObject
import com.impaircheck.constants.currentUserId
import com.impaircheck.constants.fireBaseDatabase
import com.impaircheck.databinding.FragmentUserProfileBinding
import com.impaircheck.models.userDataItem
import com.impaircheck.models.userTests
import com.impaircheck.models.userTestsItem

class UserProfileScreenFragment : Fragment() {

    private lateinit var binding: FragmentUserProfileBinding
    private var filteredUserTests: List<userTestsItem> = emptyList()


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (!::binding.isInitialized) {
            binding = FragmentUserProfileBinding.inflate(inflater, container, false)
        }



        handleFetchUserTest()
        initViews()

        return binding.root
    }

    private fun initViews() {


        IS_NEW_USER = false
        binding.lastTestDate.text = getString(R.string.last_test_date) + " "
        binding.lastTestScore.text = getString(R.string.last_test_state) + " "

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
            addNewTest()
        }
        dialog.show()
    }


    private fun handleFetchUserTest() {
        // Fetch users from firebase database
        fireBaseDatabase.child("tests").get().addOnSuccessListener { it ->
            Log.d("firebase", "Got value ${it.value}")


            if (it.value.toString().contains("null")) {
                return@addOnSuccessListener
            }
            val jsonString = convertToJsonArray(it.value as Map<String, Map<String, Any>>)

            val gson = Gson()
            val listType = object : TypeToken<userTests>() {}.type
            val userTestsList: userTests = gson.fromJson(jsonString, listType)


            filteredUserTests =
                userTestsList.filter { userTest -> userTest.userId == currentUserId }

            if (filteredUserTests.isNotEmpty()) {
                val lastTest = getLatestTest(filteredUserTests)
                if (lastTest != null) {
                    binding.lastTestDate.text =
                        getString(R.string.last_test_date) + " " + lastTest.date
                    binding.lastTestScore.text =
                        getString(R.string.last_test_state) + " " + lastTest.state
                }
            }

        }.addOnFailureListener {
            Log.e("firebase", "Error getting data", it)
        }
    }

    private fun addNewTest() {
        val testId = (100..999999).random()
        val userNewTestObj = userTestsItem(
            id = testId,
            date = getCurrentDate(),
            first_pose = false,
            left_eye_percentage = -1.0,
            questionnaire_chat = "", right_eye_percentage = -1.0,
            second_pose = false,
            userId = currentUserId,
            state = "not completed"
        )



        fireBaseDatabase.child("tests").child(testId.toString()).setValue(userNewTestObj)

        currentTestObject = userNewTestObj

        findNavController().navigate(R.id.faceCameraFragment)


    }


}