# DexKit integration

TikGoon includes the DexKit 2.3.0 Android runtime, Kotlin standard library, and JNI libraries for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`. `build.py` compiles the DexKit Java API into the module DEX files, packages all DEX files emitted by D8, and adds each available native library under `lib/<abi>/`.

## Runtime behavior

When the target TikTok process attaches, `Entry` initializes a single process-scoped `DexKitRuntime` using the installed TikTok APK path (`ApplicationInfo.sourceDir`). A successful initialization logs `TikGoon: DexKit ready; dex count=<n>`. Failure logs the exception and leaves existing static hook groups running, so a DexKit native-loading issue does not intentionally disable the current features.

Hook groups can retrieve the runtime through `Entry.getDexKitRuntime()`, check `isValid()`, call `getDexCount()`, use `getClassData()` for direct class lookup, or use `getBridge()` for DexKit's full query API. The runtime is not closed during normal process lifetime because hook groups may share it.

## Build behavior

The GitHub Actions build is triggered by changes under `src/` or `libs/`, as well as by changes to `build.py` or this document. The build downloads the pinned FlatBuffers Java dependency when it is not already present. All DEX outputs from D8 are packaged, not just `classes.dex`, so additional DEX files are not silently omitted.

## Scope and limitations

DexKit is initialized and made available to the module, but existing TikTok hooks still use their current targets; they have not all been rewritten to discover obfuscated targets dynamically. Each hook migration needs a concrete query and runtime test against the target TikTok version.

The supplied `patches-0.8.0.mpp` is a compiled Morphe patch package, not a standalone Xposed module or DexKit database. Copying it into TikGoon does not activate those patches; porting individual Morphe patches requires separate implementation and testing.
