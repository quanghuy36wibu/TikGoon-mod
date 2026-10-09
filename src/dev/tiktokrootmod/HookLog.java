package dev.tiktokrootmod;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.robv.android.xposed.XposedBridge;

/**
 * Ghi log vừa vào LSPosed vừa vào tệp trong thư mục files của TikTok.
 * Màn hình cài đặt TikGoon đọc tệp này bằng root ("Nâng cao" -> "Xem nhật ký hook").
 */
final class HookLog {
    private static volatile File file;

    private HookLog() {}

    static void init(Context context) {
        try {
            File target = new File(context.getFilesDir(), "tiktokrootmod_log");
            if (target.length() > 100_000) target.delete();
            file = target;
            write("---- phiên mới, pid " + android.os.Process.myPid() + " ----");
        } catch (Throwable ignored) { }
    }

    static void log(String message) {
        XposedBridge.log(message);
        write(message);
    }

    static void log(Throwable error) {
        XposedBridge.log(error);
        write(Log.getStackTraceString(error));
    }

    private static synchronized void write(String message) {
        File target = file;
        if (target == null) return;
        try (FileOutputStream out = new FileOutputStream(target, true)) {
            String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            String text = message.length() > 1500 ? message.substring(0, 1500) + "…" : message;
            out.write((time + " " + text.replace("TikTokRootMod: ", "") + "\n").getBytes("UTF-8"));
        } catch (Throwable ignored) { }
    }
}
