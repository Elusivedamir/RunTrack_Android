package com.runtrack.prototype

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

class CrashReportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashFile = File(filesDir, CrashCatcherApplication.CRASH_FILE_NAME)
        val report = crashFile.takeIf { it.exists() }?.readText()
            ?: "Crash report file not found."

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(7, 19, 27))
        }

        val title = TextView(this).apply {
            text = "RunTrack упал при запуске"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

        val hint = TextView(this).apply {
            text = "Сделай скрин этого отчёта или нажми «Копировать». Затем можно очистить отчёт и попробовать снова."
            textSize = 14f
            setTextColor(Color.rgb(170, 180, 188))
            setPadding(0, 16, 0, 16)
        }

        val reportView = TextView(this).apply {
            text = report
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }

        val copy = Button(this).apply {
            text = "Копировать отчёт"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("RunTrack crash", report))
                text = "Скопировано"
            }
        }

        val retry = Button(this).apply {
            text = "Очистить отчёт и попробовать снова"
            setOnClickListener {
                crashFile.delete()
                startActivity(Intent(this@CrashReportActivity, MainActivity::class.java))
                finish()
            }
        }

        root.addView(title)
        root.addView(hint)
        root.addView(copy, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(retry, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val scroll = ScrollView(this).apply {
            addView(reportView)
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        setContentView(root)
    }
}
