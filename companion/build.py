"""Package AxOS Companion into a standalone app with PyInstaller.

    pip install pyinstaller -r requirements.txt
    python build.py

Output in dist/: AxOS-Companion-Windows.exe, AxOS-Companion-macOS.zip or
AxOS-Companion-Linux. GitHub Actions runs this on every platform
(.github/workflows/companion.yml) and publishes the results as a release.
"""
import os
import platform
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
NAME = "AxOS Companion"
SYSTEM = platform.system()


def main():
    os.chdir(HERE)
    args = [sys.executable, "-m", "PyInstaller", "--noconfirm", "--clean", "--name", NAME,
            "--add-data", "icon.png:.",
            "--collect-all", "aiortc", "--collect-all", "av", "--collect-all", "aioice", "--collect-all", "pylibsrtp",
            "--collect-submodules", "websockets", "--collect-data", "soundcard",
            "--hidden-import", "pyperclip", "--hidden-import", "certifi", "--collect-data", "certifi"]
    # pynput picks its backend at import time, so name it explicitly (it can't be imported without a display)
    backend = {"Windows": "win32", "Darwin": "darwin"}.get(SYSTEM, "xorg")
    for mod in ("keyboard._" + backend, "mouse._" + backend, "_util." + backend, "_util." + backend + ("_keysyms" if backend == "xorg" else "_vks")):
        args += ["--hidden-import", "pynput." + mod]
    if backend == "xorg":
        args += ["--collect-submodules", "Xlib"]
    if SYSTEM == "Windows":
        args += ["--onefile", "--windowed", "--icon", "icon.ico", "--collect-all", "vgamepad", "--collect-all", "dxcam"]
    elif SYSTEM == "Darwin":
        args += ["--windowed", "--icon", "icon.png", "--osx-bundle-identifier", "com.axos.companion"]
    else:
        args += ["--onefile"]
    subprocess.check_call(args + ["axos-companion.py"])
    dist = os.path.join(HERE, "dist")
    if SYSTEM == "Windows":
        out = os.path.join(dist, "AxOS-Companion-Windows.exe")
        shutil.move(os.path.join(dist, NAME + ".exe"), out)
    elif SYSTEM == "Darwin":
        out = os.path.join(dist, "AxOS-Companion-macOS.zip")
        subprocess.check_call(["ditto", "-c", "-k", "--sequesterRsrc", "--keepParent", os.path.join(dist, NAME + ".app"), out])
    else:
        out = os.path.join(dist, "AxOS-Companion-Linux")
        shutil.move(os.path.join(dist, NAME), out)
    print("Built", out, f"({os.path.getsize(out) / 1e6:.0f} MB)")


if __name__ == "__main__":
    main()
