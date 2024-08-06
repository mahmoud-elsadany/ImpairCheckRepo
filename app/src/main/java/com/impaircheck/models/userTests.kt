package com.impaircheck.models

class userTests : ArrayList<userTestsItem>()


data class userTestsItem(
    val id: Int,
    val date: String,
    val first_pose: Boolean,
    val left_eye_percentage: Int,
    val questionnaire_chat: String,
    val right_eye_percentage: Int,
    val second_pose: Boolean,
    val userId: Int,
    val state: String
)