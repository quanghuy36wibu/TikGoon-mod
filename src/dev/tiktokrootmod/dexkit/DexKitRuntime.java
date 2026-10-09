package dev.tiktokrootmod.dexkit;

import java.io.File;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.result.ClassData;

/** Process-scoped DexKit bridge for resolving classes in the installed TikTok APK. */
public final class DexKitRuntime implements AutoCloseable {
    private final DexKitBridge bridge;

    static {
        System.loadLibrary("dexkit");
    }

    private DexKitRuntime(String apkPath) {
        DexKitBridge created = DexKitBridge.create(apkPath);
        if (created == null || !created.isValid()) {
            if (created != null) created.close();
            throw new IllegalStateException("DexKitBridge could not open the target APK");
        }
        bridge = created;
    }

    public static DexKitRuntime open(String apkPath) {
        if (apkPath == null || apkPath.trim().isEmpty()) {
            throw new IllegalArgumentException("TikTok APK path is empty");
        }
        File apk = new File(apkPath);
        if (!apk.isFile() || !apk.canRead()) {
            throw new IllegalArgumentException("TikTok APK is not readable: " + apkPath);
        }
        return new DexKitRuntime(apk.getAbsolutePath());
    }

    public boolean isValid() {
        return bridge.isValid();
    }

    public int getDexCount() {
        return bridge.getDexNum();
    }

    /** Resolve a class by its current binary name or descriptor. */
    public ClassData getClassData(String className) {
        if (className == null || className.trim().isEmpty()) return null;
        return bridge.getClassData(className);
    }

    /** Full DexKit query API for hook groups that need dynamic discovery. */
    public DexKitBridge getBridge() {
        return bridge;
    }

    @Override
    public void close() {
        bridge.close();
    }
}
