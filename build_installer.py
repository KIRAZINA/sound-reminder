"""
build_installer.py — Build a portable Windows distribution for SoundReminder.
Creates a self-contained folder with bundled JRE — no Java installation needed.
Prerequisites: JDK 21 installed, Gradle wrapper in project root.
Usage: python build_installer.py
Output: build/SoundReminder/  (portable folder — copy anywhere and run SoundReminder.bat)
"""

import os
import sys
import subprocess
import shutil
import glob

# Project paths
PROJECT_DIR = os.path.dirname(os.path.abspath(__file__))
GRADLEW = os.path.join(PROJECT_DIR, "gradlew.bat")
BUILD_DIR = os.path.join(PROJECT_DIR, "build")
LIBS_DIR = os.path.join(BUILD_DIR, "libs")
INSTALLER_DIR = os.path.join(BUILD_DIR, "installer")
APP_NAME = "SoundReminder"
APP_VERSION = "1.0.0"
MAIN_CLASS = "com.soundreminder.Main"
VENDOR = "KIRAZINA"

def run_cmd(cmd, cwd=None):
    """Run a command and print output in real-time."""
    print(f"> Running: {' '.join(cmd)}")
    result = subprocess.run(cmd, cwd=cwd or PROJECT_DIR, capture_output=False)
    if result.returncode != 0:
        print(f"ERROR: Command failed with exit code {result.returncode}")
        sys.exit(1)
    return result

def main():
    print("=" * 60)
    print(f"  {APP_NAME} Windows Portable Builder")
    print("=" * 60)

    # Find Java home
    java_home = os.environ.get("JAVA_HOME")

    if not java_home or not os.path.exists(os.path.join(java_home, "bin", "jpackage.exe")):
        common_paths = [
            os.environ.get("JDK_HOME", ""),
            os.path.join(os.environ.get("ProgramFiles", "C:\\Program Files"), "Java", "jdk-21.0.10"),
            os.path.join(os.environ.get("ProgramFiles", "C:\\Program Files"), "Java", "jdk-21"),
            os.path.join(os.environ.get("ProgramFiles(x86)", "C:\\Program Files (x86)"), "Java", "jdk-21.0.10"),
        ]
        for path in common_paths:
            if path and os.path.exists(os.path.join(path, "bin", "jpackage.exe")):
                java_home = path
                break

    if not java_home or not os.path.exists(os.path.join(java_home, "bin", "jpackage.exe")):
        java_bin = shutil.which("java")
        if java_bin:
            jdk_base = os.path.join(os.environ.get("ProgramFiles", "C:\\Program Files"), "Java")
            if os.path.exists(jdk_base):
                for entry in sorted(os.listdir(jdk_base)):
                    if entry.startswith("jdk"):
                        candidate = os.path.join(jdk_base, entry)
                        if os.path.exists(os.path.join(candidate, "bin", "jpackage.exe")):
                            java_home = candidate
                            break

    if not java_home or not os.path.exists(os.path.join(java_home, "bin", "jpackage.exe")):
        print("ERROR: jpackage.exe not found. Please set JAVA_HOME to your JDK 21 installation.")
        print("Expected path: C:\\Program Files\\Java\\jdk-21.0.10")
        print(f"Current JAVA_HOME: {java_home or '(not set)'}")
        sys.exit(1)

    print(f"JAVA_HOME: {java_home}")

    # Step 1: Build fat JAR with Gradle
    print("\n[1/4] Building application JAR...")
    run_cmd([GRADLEW, "clean", "fatJar", "--no-daemon"])

    fat_jars = glob.glob(os.path.join(LIBS_DIR, f"{APP_NAME}-*-all.jar"))
    if not fat_jars:
        print("ERROR: Fat JAR not found in build/libs/")
        sys.exit(1)
    fat_jar = fat_jars[0]
    print(f"  Found JAR: {os.path.basename(fat_jar)} ({os.path.getsize(fat_jar) / 1024 / 1024:.1f} MB)")

    # Step 2: Create output directory
    print("\n[2/4] Preparing output directory...")
    output_dir = os.path.join(INSTALLER_DIR, APP_NAME)
    if os.path.exists(INSTALLER_DIR):
        shutil.rmtree(INSTALLER_DIR)
    # Note: jpackage creates the SoundReminder folder itself

    # Step 3: Build portable app image with jpackage
    print("\n[3/4] Building portable app image with jpackage...")
    jpackage_exe = os.path.join(java_home, "bin", "jpackage.exe")

    jpackage_cmd = [
        jpackage_exe,
        "--type", "app-image",
        "--input", LIBS_DIR,
        "--main-jar", os.path.basename(fat_jar),
        "--main-class", MAIN_CLASS,
        "--name", APP_NAME,
        "--app-version", APP_VERSION,
        "--vendor", VENDOR,
        "--description", "A cross-platform alarm and reminder application",
        "--dest", INSTALLER_DIR,
        "--java-options", "-Xms128m",
        "--java-options", "-Xmx512m",
    ]

    # Add icon if .ico exists
    ico_path = os.path.join(PROJECT_DIR, "src", "main", "resources", "icons", "app-icon.ico")
    if os.path.exists(ico_path):
        jpackage_cmd.extend(["--icon", ico_path])

    run_cmd(jpackage_cmd)

    # Step 4: Add helper files
    print("\n[4/4] Adding helper files...")

    # Create a simple launch batch file in the root of the portable folder
    launcher_path = os.path.join(output_dir, f"Run-{APP_NAME}.bat")
    with open(launcher_path, "w") as f:
        f.write("@echo off\n")
        f.write(f"echo Starting {APP_NAME}...\n")
        f.write(f'start "" "{APP_NAME}.exe"\n')
        f.write("exit\n")

    # Create a README in the portable folder
    readme_path = os.path.join(output_dir, "README.txt")
    with open(readme_path, "w") as f:
        f.write(f"{APP_NAME} v{APP_VERSION}\n")
        f.write("=" * 40 + "\n\n")
        f.write(f"To run {APP_NAME}, double-click:\n")
        f.write(f"  {APP_NAME}.exe\n\n")
        f.write("No Java installation required — this folder contains everything.\n")
        f.write("You can copy this entire folder anywhere.\n\n")
        f.write(f"Data is stored in: %USERPROFILE%\\.soundreminder\\\n\n")
        f.write(f"Source code: https://github.com/KIRAZINA/sound-reminder\n")

    # Verify output
    exe_path = os.path.join(output_dir, f"{APP_NAME}.exe")
    if os.path.exists(exe_path):
        folder_size = 0
        for dirpath, dirnames, filenames in os.walk(output_dir):
            for fname in filenames:
                fp = os.path.join(dirpath, fname)
                folder_size += os.path.getsize(fp)

        size_mb = folder_size / 1024 / 1024
        print(f"\n{'=' * 60}")
        print(f"  SUCCESS! Portable app created:")
        print(f"  Path: {output_dir}")
        print(f"  Size: {size_mb:.1f} MB")
        print(f"  Launcher: {os.path.basename(exe_path)}")
        print(f"{'=' * 60}")
        print(f"\nUsers can:")
        print(f"  1. Extract/copy the '{APP_NAME}' folder anywhere")
        print(f"  2. Double-click '{APP_NAME}.exe' to run")
        print(f"  3. No Java installation needed!")
        print(f"\nTo distribute: zip the '{APP_NAME}' folder and upload to GitHub Releases.")
    else:
        print(f"\nWARNING: {APP_NAME}.exe not found in {output_dir}")
        print(f"Check {INSTALLER_DIR} for any output files.")
        sys.exit(1)

if __name__ == "__main__":
    main()
