package dev.tiktokrootmod.dexkit;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.result.ClassData;

/** Small DexKit adapter for resolving obfuscated TikTok classes at runtime. */
public final class DexKitRuntime implements AutoCloseable {
    private final DexKitBridge bridge;

    static {
        System.loadLibrary("dexkit");
    }

    private DexKitRuntime(String apkPath) {
        bridge = DexKitBridge.create(apkPath);
    }

    public static DexKitRuntime open(String apkPath) {
        if (apkPath == null || apkPath.isEmpty()) {
            throw new IllegalArgumentException("TikTok APK path is empty");
        }
        return new DexKitRuntime(apkPath);
    }

    public int getDexCount() {
        return bridge.getDexNum();
    }

    /** Resolve metadata for a known class descriptor/name; returns null if absent. */
    public ClassData getClassData(String className) {
        if (className == null || className.isEmpty()) return null;
        try {
            return bridge.getClassData(className);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override public void close() {
        bridge.close();
    }
        }
