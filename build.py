"""Build the legacy Xposed APK with local JDK/Android SDK tools (no Gradle)."""

from __future__ import annotations

import hashlib
import base64
import os
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
FLATBUFFERS_URL = "https://repo.maven.apache.org/maven2/com/google/flatbuffers/flatbuffers-java/23.5.26/flatbuffers-java-23.5.26.jar"


def run(*args: object) -> None:
    subprocess.run([str(x) for x in args], check=True, cwd=ROOT)


def main() -> None:
    for path in (JDK / "bin/javac.exe", TOOLS / "aapt.exe", TOOLS / "d8.bat", ANDROID_JAR):
        if not path.is_file():
            raise SystemExit(f"Missing build dependency: {path}")
    BUILD.mkdir(exist_ok=True)
    api = BUILD / "api-82.jar"
    if not api.exists():
        urllib.request.urlretrieve(XPOSED_URL, api)
    if hashlib.sha256(api.read_bytes()).hexdigest() != XPOSED_SHA256:
        raise SystemExit("Xposed API jar checksum mismatch")

    # DexKit's Android artifact is supplied as its classes.jar plus JNI libs.
    dexkit_jar = ROOT / "libs/dexkit-classes.jar"
    kotlin_jar = ROOT / "libs/kotlin-stdlib.jar"
    flatbuffers_jar = ROOT / "libs/flatbuffers-java-23.5.26.jar"
    if not flatbuffers_jar.exists():
        urllib.request.urlretrieve(FLATBUFFERS_URL, flatbuffers_jar)
    for dependency in (dexkit_jar, kotlin_jar, flatbuffers_jar):
        if not dependency.is_file():
            raise SystemExit(f"Missing DexKit dependency: {dependency}")

    classes = BUILD / "classes"
    classes.mkdir(exist_ok=True)
    sources = sorted((ROOT / "src").rglob("*.java"))
    if not sources:
        raise SystemExit("No Java sources")
    dependency_jars = (str(ANDROID_JAR), str(api), str(dexkit_jar), str(kotlin_jar), str(flatbuffers_jar))
    run(JDK / "bin/javac.exe", "-source", "8", "-target", "8", "-encoding", "UTF-8",
        "-cp", os.pathsep.join(dependency_jars), "-d", classes, *sources)
    class_jar = BUILD / "classes.jar"
    run(JDK / "bin/jar.exe", "cf", class_jar, "-C", classes, ".")
    dex_dir = BUILD / "dex"
    dex_dir.mkdir(exist_ok=True)
    run(TOOLS / "d8.bat", "--min-api", "23", "--lib", ANDROID_JAR, "--classpath", api,
        "--output", dex_dir, class_jar, dexkit_jar, kotlin_jar, flatbuffers_jar)

    unsigned = BUILD / "unsigned.apk"
    run(TOOLS / "aapt.exe", "package", "-f", "-M", ROOT / "AndroidManifest.xml",
        "-S", ROOT / "res",
        "-I", ANDROID_JAR, "-F", unsigned)
    with zipfile.ZipFile(unsigned, "a") as apk:
        apk.write(dex_dir / "classes.dex", "classes.dex")
        for asset in (ROOT / "assets").rglob("*"):
            if asset.is_file():
                apk.write(asset, "assets/" + asset.relative_to(ROOT / "assets").as_posix())
        for abi_dir in (ROOT / "libs").iterdir():
            if abi_dir.is_dir():
                for native in abi_dir.glob("*.so"):
                    apk.write(native, "lib/" + abi_dir.name + "/" + native.name)
    aligned = BUILD / "aligned.apk"
    run(TOOLS / "zipalign.exe", "-f", "4", unsigned, aligned)
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
        run(JDK / "bin/keytool.exe", "-genkeypair", "-noprompt", "-keystore", keystore,
            "-storepass", "android", "-keypass", "android", "-alias", "androiddebugkey",
            "-dname", "CN=Android Debug,O=Android,C=US", "-keyalg", "RSA", "-keysize", "2048",
            "-validity", "10000")
    manifest = ET.parse(ROOT / "AndroidManifest.xml").getroot()
    version = manifest.attrib["{http://schemas.android.com/apk/res/android}versionName"]
    output = BUILD / (f"TikGoon-v{version}.apk" if release_mode else "TikGoon-debug.apk")
    shutil.copy2(aligned, output)
    run(TOOLS / "apksigner.bat", "sign", "--ks", keystore, "--ks-key-alias",
        alias, *password_args, output)
    run(TOOLS / "apksigner.bat", "verify", "--verbose", output)
    print(f"Built {output}")
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    print(f"SHA-256 {digest}")
    if release_mode:
        (BUILD / f"{output.name}.sha256").write_text(f"{digest}  {output.name}\n", encoding="utf-8")


if __name__ == "__main__":
    main()
