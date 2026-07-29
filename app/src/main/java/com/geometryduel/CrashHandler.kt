package com.geometryduel

import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.util.Date

/**
 * 全局崩溃处理器：捕获未处理异常，
 * 将堆栈写入外部文件目录 crash/last_crash.txt，并弹出 CrashActivity 展示。
 */
class CrashHandler private constructor(
    private val appContext: Context,
) : Thread.UncaughtExceptionHandler {

    companion object {
        fun install(context: Context) {
            Thread.setDefaultUncaughtExceptionHandler(
                CrashHandler(context.applicationContext)
            )
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val trace = Log.getStackTraceString(throwable)
        val body = "Geometry Duel Crash Report\nThread: ${thread.name}" +
                "\nTime: ${Date()}\n\n$trace"

        var savedPath: String? = null
        try {
            var dir = appContext.getExternalFilesDir("crash")
            if (dir == null) dir = appContext.filesDir
            dir.mkdirs()
            val f = File(dir, "last_crash.txt")
            val w = FileWriter(f, false)
            w.write(body)
            w.close()
            savedPath = f.absolutePath
        } catch (ignored: Exception) {
        }

        try {
            val i = Intent(appContext, CrashActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            i.putExtra(CrashActivity.EXTRA_TRACE, body)
            i.putExtra(CrashActivity.EXTRA_PATH, savedPath)
            appContext.startActivity(i)
        } catch (ignored: Exception) {
        }

        android.os.Process.killProcess(android.os.Process.myPid())
        System.exit(1)
    }
}
