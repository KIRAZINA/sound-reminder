# SoundReminder

A cross-platform, offline-capable alarm and reminder application built with Java 21 and JavaFX 21.

Schedule reminders for specific times or set countdown timers — each with custom messages, optional image attachments, and a loud alarm sound.

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Java](https://img.shields.io/badge/java-21-orange.svg)
![JavaFX](https://img.shields.io/badge/javafx-21-green.svg)

## Download

### Windows (Recommended)

Download the latest `.exe` installer from [Releases](https://github.com/KIRAZINA/sound-reminder/releases). No Java installation required — the installer includes everything.

### Build from Source

See the [Building](#building) section below.

## Features

- **Scheduled Reminders** — Pick a date and time, write a message, optionally attach an image
- **Countdown Timers** — Set hours, minutes, seconds with a custom message and image
- **Persistent Storage** — All reminders and settings survive app restarts
- **Loud Alarm Sound** — Clear, attention-grabbing sound with volume control
- **Top-Left Notification** — Pop-up with message, attached image, and "Mark as Done" checkbox
- **Automatic Replay** — If not acknowledged: sound stops after 60s, restarts after 5 minutes, repeats
- **System Tray** — Minimize to tray; quick access to pending reminders
- **Dark Theme UI** — Modern, clean, flat design
- **Fully Offline** — No internet connection required

## Installation (Windows)

1. Download `SoundReminder-Setup.exe` from [Releases](https://github.com/KIRAZINA/sound-reminder/releases)
2. Double-click the installer
3. Follow the installation wizard
4. Launch SoundReminder from the Start Menu or Desktop shortcut

**No Java needed** — the installer bundles the complete runtime.

## Building from Source

### Requirements

| Component | Version |
|-----------|---------|
| Java JDK  | 21+     |
| Gradle    | 8.5+ (wrapper included) |

### Build

```powershell
# Build the project
.\gradlew.bat build

# Run from source
.\gradlew.bat run

# Build executable JAR
.\gradlew.bat fatJar

# Build Windows .exe installer (requires JDK 21)
python build_installer.py
```

### Linux

```bash
chmod +x gradlew
./gradlew build
./gradlew run
```

## Usage

### Scheduled Reminder
1. Select a **Date** and **Time**
2. Type your **Message**
3. Optionally click **Attach Image** (PNG/JPG)
4. Click **Add Reminder**

### Countdown Timer
1. Set **Hours**, **Minutes**, **Seconds**
2. Type your **Message**
3. Click **Start Timer**

### When a Reminder Fires
- The alarm sound starts immediately
- A notification appears in the **top-right corner**
- Check **"Mark as Done"** to stop the sound and remove the reminder
- If not acknowledged: sound stops after 60s, restarts after 5 minutes

### Settings
Click **Settings** in the toolbar to change:
- Custom alarm sound file (WAV/MP3/AIFF)
- Volume level (0–100%)

### System Tray
- Right-click the tray icon: **Open**, **Show Pending Reminders**, **Exit**
- Double-click to restore the main window

## Project Structure

```
SoundReminder/
├── build.gradle.kts              # Gradle build configuration
├── build_installer.py            # Windows .exe installer builder
├── generate_alarm_sound.py       # Generates the default alarm WAV
├── generate_tray_icon.py         # Generates the tray icon PNG
├── README.md                     # This file
├── src/
│   └── main/
│       ├── java/
│       │   ├── module-info.java
│       │   └── com/soundreminder/
│       │       ├── Main.java
│       │       ├── model/
│       │       ├── storage/
│       │       ├── sound/
│       │       ├── scheduler/
│       │       ├── ui/
│       │       └── util/
│       └── resources/
│           ├── css/styles.css
│           ├── sounds/alarm.wav
│           └── icons/tray-icon.png
└── src/test/                     # Unit and integration tests (65 tests)
```

## Technical Details

- **Scheduling**: `ScheduledExecutorService` + `PriorityBlockingQueue`
- **Persistence**: Jackson JSON with `JavaTimeModule`
- **Sound**: Java Sound API with `MASTER_GAIN` volume control
- **UI**: JavaFX 21, pure Java code
- **Threading**: `CopyOnWriteArrayList` for thread safety
- **Logging**: `java.util.logging` to console and dated log files

## Data Storage

All user data is stored in `~/.soundreminder/`:
- `reminders.json` — All reminders
- `settings.json` — Application settings
- `images/` — Attached image files
- `logs/` — Application logs

## Building the Windows Installer

The `build_installer.py` script uses **jpackage** (included in JDK 21) to create a standalone `.exe` installer:

```powershell
# Prerequisites: JDK 21, Python 3.x
python build_installer.py
```

The installer is output to `build/installer/SoundReminder-Setup.exe`. Users can install without needing Java.

## License

This project is provided as-is for educational and personal use.

## Contributing

Contributions are welcome. Please follow the existing code style and add comments to all new code in English.
