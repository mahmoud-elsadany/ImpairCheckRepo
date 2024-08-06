package com.impaircheck

import com.google.firebase.database.DatabaseReference
import com.impaircheck.models.userTestsItem

object constants {

    lateinit var fireBaseDatabase: DatabaseReference
    var APP_VERSION = ""
    var APP_VERSION_CODE = -1

    var IS_NEW_USER = false

    var currentUserId = -1

    var currentTestObject: userTestsItem? = null

}