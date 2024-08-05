package com.impaircheck

import com.google.firebase.database.DatabaseReference

object constants {

    lateinit var fireBaseDatabase: DatabaseReference
    var APP_VERSION = ""
    var APP_VERSION_CODE = -1

    var IS_NEW_USER = false

}