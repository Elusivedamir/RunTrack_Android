package com.runtrack.app

import android.app.Application
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import com.runtrack.app.tracking.RunTrackRuntime
import okhttp3.OkHttpClient
import org.maplibre.android.module.http.HttpRequestUtil

class CrashCatcherApplication : Application() {

    override fun onCreate() {
        super.onCreate()
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

        runCatching { configureOsmHttpClient() }
        RunTrackRuntime.initialize(this)
    }

    /**
     * OSM public tile policy requires an application-identifying User-Agent.
     * Default HTTP caching is intentionally preserved.
     */
    private fun configureOsmHttpClient() {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                    .newBuilder()
                    .header(
                        "User-Agent",
                        "RunTrack-Android/0.7 (com.runtrack.app; +https://github.com/Elusivedamir/RunTrack_Android)",
                    )
                    .header("X-Requested-With", packageName)
                    .build()
                chain.proceed(request)
            }
            .build()

        HttpRequestUtil.setOkHttpClient(client)
    }

    companion object {
        const val CRASH_FILE_NAME = "last_crash.txt"
    }
}
