package com.geometryduel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.io.File

/** 崩溃信息展示页：等宽字体，文本可长按选择复制。 */
class CrashActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TRACE = "trace"
        const val EXTRA_PATH = "path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var trace = intent.getStringExtra(EXTRA_TRACE)
        val path = intent.getStringExtra(EXTRA_PATH)
        if (trace == null) trace = readSaved()

        val body = buildString {
            append("The app crashed. Please send a screenshot or the text below to the developer:\n")
            if (path != null) append("Log file: ").append(path).append("\n")
            append("\n").append(trace)
        }

        setContent {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                SelectionContainer {
                    Text(
                        body,
                        color = Color.Black,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    private fun readSaved(): String {
        return try {
            var dir = getExternalFilesDir("crash")
            if (dir == null) dir = filesDir
            val f = File(dir, "last_crash.txt")
            if (!f.exists()) return "(no crash log)"
            f.readText()
        } catch (e: Exception) {
            "(failed to read crash log: $e)"
        }
    }
}
