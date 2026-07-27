package com.geometryduel.android;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.util.Date;

/**
 * 全局崩溃处理器：捕获未处理异常（含 GL 渲染线程），
 * 将堆栈写入外部文件目录 crash/last_crash.txt，并弹出 CrashActivity 展示。
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private final Context appContext;

    public static void install(Context context) {
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(context.getApplicationContext()));
    }

    private CrashHandler(Context appContext) {
        this.appContext = appContext;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        String trace = Log.getStackTraceString(throwable);
        String body = "Geometry Duel 崩溃报告\n线程: " + thread.getName()
                + "\n时间: " + new Date() + "\n\n" + trace;

        String savedPath = null;
        try {
            File dir = appContext.getExternalFilesDir("crash");
            if (dir == null) dir = appContext.getFilesDir();
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File f = new File(dir, "last_crash.txt");
            FileWriter w = new FileWriter(f, false);
            w.write(body);
            w.close();
            savedPath = f.getAbsolutePath();
        } catch (Exception ignored) {
        }

        try {
            Intent i = new Intent(appContext, CrashActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            i.putExtra(CrashActivity.EXTRA_TRACE, body);
            i.putExtra(CrashActivity.EXTRA_PATH, savedPath);
            appContext.startActivity(i);
        } catch (Exception ignored) {
        }

        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(1);
    }
}
