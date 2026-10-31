"""Build the legacy Xposed APK with local JDK/Android SDK tools (no Gradle)."""

from __future__ import annotations

import hashlib
import base64
import os
import platform
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
BUILD = ROOT / "build"
SDK = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or
           Path.home() / "AppData/Local/Android/Sdk")
JDK = Path(os.environ.get("JAVA_HOME", r"C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot"))
TOOLS = SDK / "build-tools/36.1.0"
ANDROID_JAR = SDK / "platforms/android-36/android.jar"
XPOSED_URL = "https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar"
XPOSED_SHA256 = "f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25"
# DexKit (libs/dexkit-classes.jar) phụ thuộc FlatBuffers; jar này không nằm trong libs/ nên tải về.
# Nếu có sẵn libs/flatbuffers-java*.jar thì dùng luôn, khỏi tải.
FLATBUFFERS_VERSION = "25.2.10"
FLATBUFFERS_URL = ("https://repo1.maven.org/maven2/com/google/flatbuffers/flatbuffers-java/"
                   f"{FLATBUFFERS_VERSION}/flatbuffers-java-{FLATBUFFERS_VERSION}.jar")
ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")


# ── Cross-platform: Windows dùng .exe/.bat, Linux/Mac bỏ đuôi ────────────────
IS_WIN = platform.system() == "Windows"

def _fix(name: str) -> str:
    return name if IS_WIN else name.replace(".exe", "").replace(".bat", "")

def jbin(name: str) -> Path:
    return JDK / "bin" / _fix(name)

def abt(name: str) -> Path:
    return TOOLS / _fix(name)


def run(*args: object) -> None:
    subprocess.run([str(x) for x in args], check=True, cwd=ROOT)


def main() -> None:
    for path in (jbin("javac.exe"), abt("aapt.exe"), abt("d8.bat"), ANDROID_JAR):
        if not path.is_file():
            raise SystemExit(f"Missing build dependency: {path}")
    BUILD.mkdir(exist_ok=True)
    api = BUILD / "api-82.jar"
    if not api.exists():
        urllib.request.urlretrieve(XPOSED_URL, api)
    if hashlib.sha256(api.read_bytes()).hexdigest() != XPOSED_SHA256:
        raise SystemExit("Xposed API jar checksum mismatch")

    # Thư viện Java đi kèm: DexKit, Kotlin stdlib, FlatBuffers
    libs = sorted((ROOT / "libs").glob("*.jar"))
    if not any("flatbuffers" in j.name.lower() for j in libs):
        fb = BUILD / f"flatbuffers-java-{FLATBUFFERS_VERSION}.jar"
        if not fb.exists():
            urllib.request.urlretrieve(FLATBUFFERS_URL, fb)
        libs.append(fb)
    for need in ("dexkit-classes.jar", "kotlin-stdlib.jar"):
        if not (ROOT / "libs" / need).is_file():
            raise SystemExit(f"Missing libs/{need} (kiểm tra .gitignore có đang chặn *.jar không)")

    if (BUILD / "classes").exists():
        shutil.rmtree(BUILD / "classes")  # tránh lẫn class cũ
    classes = BUILD / "classes"
    classes.mkdir(exist_ok=True)
    sources = sorted((ROOT / "src").rglob("*.java"))
    if not sources:
        raise SystemExit("No Java sources")
    run(jbin("javac.exe"), "-source", "8", "-target", "8", "-encoding", "UTF-8",
        "-cp", os.pathsep.join([str(ANDROID_JAR), str(api), *map(str, libs)]), "-d", classes, *sources)
    class_jar = BUILD / "classes.jar"
    run(jbin("jar.exe"), "cf", class_jar, "-C", classes, ".")
    dex_dir = BUILD / "dex"
    if dex_dir.exists():
        shutil.rmtree(dex_dir)
    dex_dir.mkdir()
    # d8 nhận cả module lẫn các jar thư viện -> dex hết vào APK (có thể ra nhiều classes*.dex)
    run(abt("d8.bat"), "--min-api", "23", "--lib", ANDROID_JAR, "--classpath", api,
        "--output", dex_dir, class_jar, *libs)

    unsigned = BUILD / "unsigned.apk"
    run(abt("aapt.exe"), "package", "-f", "-M", ROOT / "AndroidManifest.xml",
        "-S", ROOT / "res",
        "-I", ANDROID_JAR, "-F", unsigned)
    with zipfile.ZipFile(unsigned, "a") as apk:
        dex_files = sorted(dex_dir.glob("classes*.dex"))
        if not dex_files:
            raise SystemExit("D8 không tạo ra file dex nào")
        for dex in dex_files:
            apk.write(dex, dex.name)
        # libdexkit.so: lưu không nén để zipalign -p căn trang 4KB
        for abi in ABIS:
            so = ROOT / "libs" / abi / "libdexkit.so"
            if so.is_file():
                apk.write(so, f"lib/{abi}/libdexkit.so", compress_type=zipfile.ZIP_STORED)
        for asset in (ROOT / "assets").rglob("*"):
            if asset.is_file():
                apk.write(asset, "assets/" + asset.relative_to(ROOT / "assets").as_posix())
    aligned = BUILD / "aligned.apk"
    run(abt("zipalign.exe"), "-f", "-p", "4", unsigned, aligned)
    release_mode = os.environ.get("TIKGOON_RELEASE") == "1"
    release_key = os.environ.get("TIKGOON_KEYSTORE_BASE64")
    if release_mode and not release_key:
        raise SystemExit("Release build requires TIKGOON_KEYSTORE_BASE64")
    if release_mode:
        keystore = BUILD / "release.keystore"
        keystore.write_bytes(base64.b64decode(release_key, validate=True))
        for name in ("TIKGOON_KEYSTORE_PASSWORD", "TIKGOON_KEY_ALIAS", "TIKGOON_KEY_PASSWORD"):
            if not os.environ.get(name):
                raise SystemExit(f"Missing release signing environment variable: {name}")
        alias = os.environ["TIKGOON_KEY_ALIAS"]
        password_args = ("--ks-pass", "env:TIKGOON_KEYSTORE_PASSWORD",
                         "--key-pass", "env:TIKGOON_KEY_PASSWORD")
    else:
        keystore = BUILD / "debug.keystore"
        alias = "androiddebugkey"
        password_args = ("--ks-pass", "pass:android", "--key-pass", "pass:android")
    if not keystore.exists():
        run(jbin("keytool.exe"), "-genkeypair", "-noprompt", "-keystore", keystore,
            "-storepass", "android", "-keypass", "android", "-alias", "androiddebugkey",
            "-dname", "CN=Android Debug,O=Android,C=US", "-keyalg", "RSA", "-keysize", "2048",
            "-validity", "10000")
    manifest = ET.parse(ROOT / "AndroidManifest.xml").getroot()
    version = manifest.attrib["{http://schemas.android.com/apk/res/android}versionName"]
    output = BUILD / (f"TikGoon-v{version}.apk" if release_mode else "TikGoon-debug.apk")
    shutil.copy2(aligned, output)
    run(abt("apksigner.bat"), "sign", "--ks", keystore, "--ks-key-alias",
        alias, *password_args, output)
    run(abt("apksigner.bat"), "verify", "--verbose", output)
    print(f"Built {output}")
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    print(f"SHA-256 {digest}")
    if release_mode:
        (BUILD / f"{output.name}.sha256").write_text(f"{digest}  {output.name}\n", encoding="utf-8")


if __name__ == "__main__":
    main()
