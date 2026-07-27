package com.geometryduel.android;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/** 崩溃信息展示页：白底黑字等宽字体，文本可长按选择复制。 */
public class CrashActivity extends Activity {
    public static final String EXTRA_TRACE = "trace";
    public static final String EXTRA_PATH = "path";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String trace = getIntent().getStringExtra(EXTRA_TRACE);
        String path = getIntent().getStringExtra(EXTRA_PATH);
        if (trace == null) trace = readSaved();

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        TextView tv = new TextView(this);
        tv.setTextIsSelectable(true);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextColor(Color.BLACK);
        tv.setBackgroundColor(Color.WHITE);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad, pad, pad);
        StringBuilder sb = new StringBuilder("The app crashed. Please send a screenshot or the text below to the developer:\n");
        if (path != null) sb.append("Log file: ").append(path).append("\n");
        sb.append("\n").append(trace);
        tv.setText(sb.toString());
        sv.addView(tv, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(sv);
    }

    private String readSaved() {
        try {
            File dir = getExternalFilesDir("crash");
            if (dir == null) dir = getFilesDir();
            File f = new File(dir, "last_crash.txt");
            if (!f.exists()) return "(no crash log)";
            BufferedReader r = new BufferedReader(new FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            r.close();
            return sb.toString();
        } catch (Exception e) {
            return "(failed to read crash log: " + e + ")";
        }
    }
}
