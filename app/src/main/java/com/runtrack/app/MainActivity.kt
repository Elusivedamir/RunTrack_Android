package com.runtrack.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashFile = File(filesDir, CrashCatcherApplication.CRASH_FILE_NAME)
        if (crashFile.exists() && crashFile.length() > 0L) {
            startActivity(Intent(this, CrashReportActivity::class.java))
            finish()
            return
        }

        setContent {
            RunTrackPrototypeApp()
        }
    }
}
