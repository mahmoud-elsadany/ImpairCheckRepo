package com.impaircheck.application

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.database
import com.impaircheck.Utils.getAppVersion
import com.impaircheck.Utils.getAppVersionCode
import com.impaircheck.constants.APP_VERSION
import com.impaircheck.constants.APP_VERSION_CODE
import com.impaircheck.constants.fireBaseDatabase


class ApplicationMain : Application(){


    override fun onCreate() {
        super.onCreate()

        fireBaseDatabase = Firebase.database.reference

        APP_VERSION = getAppVersion(this)
        APP_VERSION_CODE = getAppVersionCode(this)

    }
}