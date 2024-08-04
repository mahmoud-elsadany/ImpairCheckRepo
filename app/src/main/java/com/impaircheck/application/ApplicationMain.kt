package com.impaircheck.application

import android.app.Application
import com.impaircheck.Utils.getAppVersion
import com.impaircheck.Utils.getAppVersionCode
import com.impaircheck.constants.APP_VERSION
import com.impaircheck.constants.APP_VERSION_CODE


class ApplicationMain : Application(){


    override fun onCreate() {
        super.onCreate()

        APP_VERSION = getAppVersion(this)
        APP_VERSION_CODE = getAppVersionCode(this)

    }
}