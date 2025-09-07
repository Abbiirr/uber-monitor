package com.example.uber_monitor

import android.content.Intent

object MediaProjectionSingleton {
    /** Populated in MainActivity.onActivityResult */
    var projectionData: Intent? = null

    var resultCode: Int = 0
}
