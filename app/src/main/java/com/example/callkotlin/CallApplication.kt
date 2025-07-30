package com.example.callkotlin

import android.app.Application
import com.example.callkotlin.core.LiveKitManager

class CallApplication : Application() {
    
    lateinit var liveKitManager: LiveKitManager
    
    override fun onCreate() {
        super.onCreate()
        liveKitManager = LiveKitManager(this)
    }
} 