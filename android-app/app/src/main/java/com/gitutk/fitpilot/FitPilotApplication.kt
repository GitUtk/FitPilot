package com.gitutk.fitpilot

import android.app.Application

class FitPilotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: FitPilotApplication
            private set
    }
}
