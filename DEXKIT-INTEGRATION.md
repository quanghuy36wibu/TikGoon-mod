# DexKit integration status

This branch adds the DexKit Android runtime (DexKit 2.3.0 API from the supplied NexAlloy source archive), JNI libraries for arm64-v8a, armeabi-v7a, x86 and x86_64, and a small Java adapter at `src/dev/tiktokrootmod/dexkit/DexKitRuntime.java`.

## Important limitation about `patches-0.8.0.mpp`

The supplied `.mpp` is a compiled Morphe patch package. It contains Morphe extension dex files and references `app.morphe.extension.tiktok.*` classes. It is not a standalone Xposed module or a DexKit database, and this legacy TikGoon project does not contain the Morphe patch executor/runtime. Renaming or copying the `.mpp` into TikGoon would not activate those patches. This change therefore integrates the DexKit runtime as groundwork, but does **not** claim to port the `.mpp` patches or automatically replace TikGoon's existing hook targets.

## Build notes

`build.py` expects the Android SDK/JDK paths documented in the project README. It downloads the pinned FlatBuffers Java dependency if it is not already in `libs/`. DexKit is loaded from the target TikTok APK path, not the module APK.
