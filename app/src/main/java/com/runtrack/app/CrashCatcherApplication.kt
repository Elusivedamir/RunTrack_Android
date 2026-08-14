package com.runtrack.app

import android.app.Application
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import com.runtrack.app.tracking.RunTrackRuntime

class CrashCatcherApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        RunTrackRuntime.initialize(this)

        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))

                val report = buildString {
                    appendLine("RunTrack diagnostic crash report")
                    appendLine("Time: ${Instant.now()}")
                    appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    append(sw.toString())
                }

                File(filesDir, CRASH_FILE_NAME).writeText(report)
            } catch (_: Throwable) {
            }

            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    companion object {
        const val CRASH_FILE_NAME = "last_crash.txt"
    }
}
