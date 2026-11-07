package com.drwho174.infusioncalc

import android.app.Application
import com.drwho174.infusioncalc.app.AppContainer

class InfusionCalcApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
