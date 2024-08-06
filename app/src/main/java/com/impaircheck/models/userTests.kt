package com.impaircheck.models

class userTests : ArrayList<userTestsItem>()


data class userTestsItem(
    val id: Int,
    val date: String,
    var first_pose: Boolean,
    var left_eye_percentage: Double,
    var questionnaire_chat: String,
    var right_eye_percentage: Double,
    var second_pose: Boolean,
    val userId: Int,
    var state: String
)