# SoundReminder — Architecture & Implementation Guide

> **Version:** 2.0.0  
> **Author:** KIRAZINA  
> **License:** MIT (educational/personal use)  
> **Repository:** <https://github.com/KIRAZINA/sound-reminder>

---

## 1. Executive Summary

SoundReminder is a cross-platform, offline-capable desktop alarm and reminder application built with **Java 21** and **JavaFX 21**. It runs entirely on the client with no network dependencies, no server, and no database — all data is persisted as local JSON files.

| Property | Detail |
|---|---|
| **Purpose** | Create and manage scheduled reminders and countdown timers with audible alarms and popup notifications |
| **Java** | JDK 21 (source & target) |
| **UI** | JavaFX 21.0.3 (Controls, Media, Swing) — pure code-only UI (no FXML) |
| **Persistence** | Jackson via JSON files (`~/.soundreminder/`) |
| **Build** | Gradle 9.4.1 (Kotlin DSL) with `java` and `org.openjfx.javafxplugin` plugins |
| **Tests** | JUnit 5.10.2; 66+ tests |
| **Architecture** | Single-process desktop monolith following an **MVC-like** pattern with event-driven scheduling |

---

## 2. Project Structure

### 2.1 Top-Level Layout

```
SoundReminder/
├── build.gradle.kts              # Gradle build: Java 21, JavaFX, fatJar, application plugin
├── settings.gradle.kts           # Project name, foojay resolver convention
├── gradle.properties             # Disables Gradle config cache
├── gradle/
│   ├── libs.versions.toml        # Version catalog (Guava 33.2.0-jre, JUnit 5.10.2)
│   └── wrapper/                  # Gradle wrapper JAR and properties
├── gradlew / gradlew.bat         # Unix / Windows Gradle launchers
├── README.md                     # Full documentation
├── SOUNDREMINDER_ARCHITECTURE.md  # This document
├── build_installer.py            # jpackage-based Windows .exe builder
├── generate_alarm_sound.py       # Generates default alarm.wav programmatically
├── generate_tray_icon.py         # Generates 16×16 PNG tray icon programmatically
└── src/
    ├── main/
    │   ├── java/
    │   │   ├── module-info.java                       # Java module descriptor
    │   │   └── com/soundreminder/
    │   │       ├── Main.java                          # Entry point, logging init
    │   │       ├── model/
    │   │       │   ├── Reminder.java                  # Core domain entity
    │   │       │   ├── ReminderType.java              # SCHEDULED / COUNTDOWN enum
    │   │       │   ├── AppSettings.java               # Sound configuration model
    │   │       │   ├── Priority.java                  # LOW / NORMAL / HIGH / URGENT enum
    │   │       │   ├── Tag.java                       # Tag name + color hex
    │   │       │   ├── RecurrenceRule.java            # iCal-style recurrence logic
    │   │       │   └── ParsedInput.java               # NLP parser output model
    │   │       ├── storage/
    │   │       │   └── StorageService.java            # JSON file persistence layer
    │   │       ├── sound/
    │   │       │   └── SoundManager.java              # Audio playback (Clip-based)
    │   │       ├── scheduler/
    │   │       │   └── ReminderScheduler.java         # Priority-queue scheduler
    │   │       ├── ui/
    │   │       │   ├── MainWindow.java                # Main stage, tab container, lifecycle
    │   │       │   ├── ScheduledReminderTab.java      # Tab: date/time reminders
    │   │       │   ├── CountdownTimerTab.java         # Tab: countdown timers
    │   │       │   ├── NotificationWindow.java        # Popup notification (undecorated, top-right)
    │   │       │   ├── SettingsDialog.java            # Modal sound settings dialog
    │   │       │   └── SystemTrayManager.java         # AWT system tray integration
    │   │       └── util/
    │   │           ├── AlarmSoundGenerator.java       # Programmatic WAV generator
    │   │           └── NaturalLanguageParser.java      # Regex-based NLP parser
    │   └── resources/
    │       ├── css/
    │       │   └── styles.css                         # Dark theme
    │       ├── icons/
    │       │   └── tray-icon.png                      # 16×16 blue circle
    │       └── sounds/
    │           └── alarm.wav                          # Default 880/660 Hz alarm
    └── test/
        └── java/com/soundreminder/
            ├── IntegrationTest.java                   # End-to-end workflow tests
            ├── model/
            │   ├── ReminderTest.java
            │   ├── ReminderTypeTest.java
            │   ├── AppSettingsTest.java
            │   ├── PriorityTest.java
            │   ├── TagTest.java
            │   └── RecurrenceRuleTest.java
            ├── storage/
            │   └── StorageServiceTest.java
            ├── sound/
            │   └── SoundManagerTest.java
            ├── scheduler/
            │   └── SchedulerTest.java
            └── util/
                └── AlarmSoundGeneratorTest.java
```

### 2.2 Purpose of Each Module

| Module / Package | Responsibility |
|---|---|
| `model/` | Pure domain data — `Reminder`, `ReminderType`, `AppSettings`, `Priority`, `Tag`, `RecurrenceRule`, `ParsedInput`. No logic beyond self-consistency. |
| `storage/` | File I/O — JSON serialization/deserialization via Jackson. Thread-safe with read-write locks. |
| `sound/` | Audio lifecycle — load, play, stop, volume control using Java Sound API (`Clip`). |
| `scheduler/` | Time-based reminder firing — `PriorityBlockingQueue` + `ScheduledExecutorService`. |
| `ui/` | All JavaFX views and controllers — tabs, popups, dialogs, system tray. |
| `util/` | WAV generator + regex-based NLP parser (`AlarmSoundGenerator`, `NaturalLanguageParser`). |
| `resources/css/` | Dark-theme stylesheet applied to all scenes. |
| `resources/sounds/` | Bundled default alarm WAV. |
| `resources/icons/` | System tray icon PNG. |

---

## 3. Architecture & Design

### 3.1 Overall Pattern

The application follows a **single-process desktop monolith** architecture with **MVC-inspired separation**:

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Main.java (Entry Point)                      │
│  Initializes: StorageService, loads AppSettings, creates MainWindow │
└──────────┬──────────────────────────────────────────────────────────┘
           │
           ▼
┌──────────────────┐     ┌──────────────────────┐     ┌─────────────────┐
│    Model Layer   │     │  View / Controller   │     │   Services      │
│  ┌────────────┐  │     │     (JavaFX UI)       │     │  ┌───────────┐ │
│  │ Reminder   │  │     │  ┌────────────────┐   │     │  │ Storage   │ │
│  │ ReminderType│  │────▶│  MainWindow      │───│────▶│  │ Service   │ │
│  │ AppSettings│  │     │  │  (Facade)       │  │     │  └───────────┘ │
│  │ Priority   │  │     │  │                │  │     │  ┌───────────┐ │
│  │ Tag        │  │     │  ├─ ScheduledTab  │  │     │  │ Sound     │ │
│  │ RecRule    │  │     │  ├─ CountdownTab  │  │     │  │ Manager   │ │
│  │ ParsedInput│  │     │  ├─ Notification  │  │     │  └───────────┘ │
│  └────────────┘  │     │  ├─ SettingsDlg   │  │     │  ┌───────────┐ │
│                   │     │  └─ TrayManager   │  │     │  │ Scheduler │ │
│                   │     │                   │  │     │  └───────────┘ │
│                   │     └──────────────────┘  │     └─────────────────┘
└──────────────────┘                            └─────────────────────────┘
```

### 3.2 Module Graph (Java Module System)

```
module com.soundreminder {
    requires javafx.controls;
    requires javafx.media;
    requires javafx.swing;        // AWT system tray interop
    requires java.desktop;        // AWT / javax.sound
    requires java.logging;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;

    opens com.soundreminder.model to com.fasterxml.jackson.databind;
    opens com.soundreminder      to javafx.graphics;

    exports com.soundreminder.model;
    exports com.soundreminder.storage;
    exports com.soundreminder.sound;
    exports com.soundreminder.scheduler;
    exports com.soundreminder.util;
}
```

### 3.3 Layer Communication

| Caller | Callee | Mechanism |
|---|---|---|
| `MainWindow` | `StorageService` | Direct method call (injected reference) |
| `MainWindow` | `SoundManager` | Direct method call (injected reference) |
| `MainWindow` | `ReminderScheduler` | Direct method call (injected reference) |
| `ReminderScheduler` | `MainWindow` | `Consumer<Reminder>` callback (`onReminderFired`) |
| UI tabs | `MainWindow` | Method references for add/delete/done callbacks |
| `NotificationWindow` | `MainWindow` | `Consumer<Reminder>` done callback, `BiConsumer<Reminder, Long>` snooze callback |
| `MainWindow` ↔ `SystemTrayManager` | `MainWindow` passes `ReminderProvider` functional interface |

**Key principle**: All cross-component communication happens through constructor-injected references or functional-interface callbacks. No event bus, no service locator, no DI framework.

---

## 4. Core Business Logic & Domain Model

### 4.1 Domain Entities

#### `Reminder` (`com.soundreminder.model.Reminder`)

The singular domain entity. Represents both scheduled reminders and countdown timers.

```java
public class Reminder implements Comparable<Reminder> {
    private String id;              // UUID
    private ReminderType type;      // SCHEDULED | COUNTDOWN
    private LocalDateTime triggerTime;
    private String message;
    private String imagePath;       // nullable
    private boolean active;
    private boolean done;
    private LocalDateTime createdAt;
    private RecurrenceRule recurrenceRule;  // nullable, iCal-style
    private LocalDateTime lastFiredAt;      // nullable
    private int snoozeCount;
    private LocalDateTime lastSnoozedAt;    // nullable
    private Set<String> tags;              // tag names
    private Priority priority;             // default NORMAL
}
```

- **ID** is a UUID generated at construction.
- **`compareTo`** orders by `triggerTime`, breaking ties by `id` (required by `PriorityBlockingQueue` to avoid violating the `Comparable` contract).
- **`equals/hashCode`** are based solely on `id`.
- **`markAsDone()`** sets `done=true` and `active=false`.
- **`snoozeUntil(LocalDateTime)`** updates `triggerTime`, increments `snoozeCount`, sets `lastSnoozedAt`.
- **`isRecurring()`** returns `true` if `recurrenceRule != null`.
- **`computeNextOccurrence()`** delegates to `RecurrenceRule.computeNextOccurrence()`.
- **`snoozeCount`** tracks total number of times the reminder has been snoozed.
- `@JsonIgnoreProperties(ignoreUnknown = true)` on class for backward-compat deserialization.
- `@JsonCreator` 6-param constructor (type, triggerTime, message, imagePath, recurrenceRule, priority, tags); 4-param legacy constructor delegates to it.

#### `ReminderType` (`com.soundreminder.model.ReminderType`)

```java
public enum ReminderType { SCHEDULED, COUNTDOWN }
```

- `SCHEDULED`: user picks a specific date and time.
- `COUNTDOWN`: user enters hours/minutes/seconds; `triggerTime` is computed as `now + duration` at creation time.

#### `AppSettings` (`com.soundreminder.model.AppSettings`)

```java
public class AppSettings {
    private String customSoundPath;  // null = use bundled sound
    private double volume;           // [0.0, 1.0], default 0.8
}
```

Volume setter clamps to `[0.0, 1.0]`.

#### `Priority` (`com.soundreminder.model.Priority`)

```java
public enum Priority { LOW(-1), NORMAL(0), HIGH(1), URGENT(2) }
```

- Rank values allow numeric comparison (`getRank()`).
- Jackson serializes as lowercase strings (`@JsonValue`/`@JsonCreator`).
- Default priority for all new reminders is `NORMAL`.

#### `Tag` (`com.soundreminder.model.Tag`)

```java
public class Tag {
    private String name;       // display name
    private String colorHex;   // #RRGGBB format
}
```

- `colorHex` is validated by regex `^#[0-9A-Fa-f]{6}$`; invalid values fall back to `#0078D4`.
- `equals/hashCode` based solely on `name`.

#### `RecurrenceRule` (`com.soundreminder.model.RecurrenceRule`)

```java
public class RecurrenceRule {
    public enum Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }
    private Frequency frequency;
    private int interval;              // every N units (default 1)
    private Set<DayOfWeek> daysOfWeek;  // for WEEKLY with specific days
    private Integer dayOfMonth;        // for MONTHLY with specific day
    private String description;
}
```

- `computeNextOccurrence(LocalDateTime from)` computes the next valid date/time based on frequency.
- For WEEKLY with `daysOfWeek`, finds the next matching day of the week (within 7-day window).
- For MONTHLY with `dayOfMonth`, clamps to last valid day if the month is shorter.

#### `ParsedInput` (`com.soundreminder.model.ParsedInput`)

```java
public class ParsedInput {
    private String message;
    private LocalDateTime triggerDateTime;
    private long countdownSeconds;      // -1 if not countdown
    private boolean isCountdown;
    private RecurrenceRule recurrenceRule;
    private Priority priority;
    private String error;
}
```

- Output model for `NaturalLanguageParser`.
- `hasError()` returns `true` when parsing failed.

### 4.2 Key Business Rules

1. **Only active reminders fire.** The scheduler only considers reminders where `active == true`.
2. **Overdue reminders are staggered on startup.** Collected and fired 1 second apart via executor to prevent UI thread flooding.
3. **Replay cycle until acknowledged.** Alarm sounds for 60s, stops for 240s, repeats indefinitely.
4. **Countdown triggerTime is computed eagerly.** Set to `now + duration` at creation time; behaves identically to scheduled after that.
5. **Image paths are stored relative to the storage root.** Copied into `~/.soundreminder/images/` with timestamp prefix; `StorageService.resolveImagePath()` converts back to absolute at load time.
6. **Recurring reminders automatically reschedule.** `ReminderScheduler.rescheduleIfRecurring()` computes next occurrence and re-adds to queue after firing. `triggerTime` updated on same object reference.
7. **Snooze updates the trigger time in-place.** `reminder.snoozeUntil(now + seconds)` modifies the reference; `scheduler.reSchedule()` replaces queue entry.
8. **NLP input can replace manual field entry.** `NaturalLanguageParser` supports relative time, absolute time, date+time, countdown shorthand, recurrence keywords, and priority tags — all offline via regex.

### 4.3 User Journeys

**Create a Scheduled Reminder:**
1. User opens "Scheduled Reminder" tab.
2. Picks date/time, optionally types message, attaches image, sets recurrence/priority/tags, or uses NLP text field.
3. Clicks "Add Reminder".
4. Validated → created → added to `CopyOnWriteArrayList` → persisted → scheduled.

**Create a Countdown Timer:**
1. User opens "Countdown Timer" tab.
2. Sets hours/minutes/seconds via Spinners or types NLP shorthand ("20m", "1h30m").
3. Optionally types message and attaches image.
4. Clicks "Start Timer".
5. Same flow as above; `triggerTime` = now + duration.

**Reminder Fires:**
1. `ReminderScheduler` executor thread fires the task.
2. `MainWindow.onReminderFired()` called.
3. Sound loops via `SoundManager.startAlarm()`.
4. `NotificationWindow` appears top-right with message, image, snooze buttons (5m/15m/30m/1h), "Acknowledge" (recurring) or "Mark as Done" (non-recurring), and "Stop Recurring" (recurring only).
5. Replay cycle: 60s sound → 240s silence → repeat.
6. "Mark as Done" → sound stops → marked done → persisted → UI refreshes.
7. "Acknowledge" (recurring) → sound stops → reminder stays active for next occurrence.
8. "Stop Recurring" → sound stops → marked done + removed from active list.
9. Snooze button → alarm stops → `snoozeUntil(now + seconds)` → scheduler re-plans.

---

## 5. Persistence & Data Model

### 5.1 Storage Strategy

All data is local files under `~/.soundreminder/`:

```
~/.soundreminder/
├── reminders.json      # JSON array of Reminder objects
├── settings.json       # JSON object of AppSettings
├── images/             # Copied attachment images (timestamp-prefixed)
└── logs/               # Dated log files (soundreminder_YYYY-MM-DD.log)
```

### 5.2 JSON Schemas

**`reminders.json`** — Array of reminder objects:

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "type": "SCHEDULED",
    "triggerTime": "2026-06-23T14:30:00",
    "message": "Team standup",
    "imagePath": null,
    "active": true,
    "done": false,
    "createdAt": "2026-06-22T10:00:00",
    "recurrenceRule": null,
    "lastFiredAt": null,
    "snoozeCount": 0,
    "lastSnoozedAt": null,
    "tags": [],
    "priority": "normal"
  }
]
```

**`settings.json`** — Single settings object:

```json
{
  "customSoundPath": "C:/Users/Me/sounds/alert.wav",
  "volume": 0.8
}
```

### 5.3 Jackson Configuration

- `ObjectMapper` configured with `JavaTimeModule` for `LocalDateTime`.
- `@JsonCreator` + `@JsonProperty` on constructors for immutable-like deserialization.
- `@JsonIgnoreProperties(ignoreUnknown = true)` on `Reminder` for backward-compat.
- `module-info.java` opens `com.soundreminder.model` to `com.fasterxml.jackson.databind`.

### 5.4 `StorageService` (`com.soundreminder.storage.StorageService`)

| Method | Description |
|---|---|
| `loadReminders()` | Reads `reminders.json`, deserializes to `List<Reminder>`. Returns empty list if file missing. |
| `saveReminders(List<Reminder>)` | Atomic write via `.tmp` + rename. |
| `loadSettings()` | Reads `settings.json`. Returns default `AppSettings` if file missing. |
| `saveSettings(AppSettings)` | Atomic write. |
| `copyImageToStorage(Path)` | Copies source to `images/` with `{timestamp}_{filename}`. Returns relative path. |
| `resolveImagePath(String)` | Converts relative path to absolute. Passes through absolute paths unchanged. |
| `removeReminder(String)` | Loads all, removes by ID, saves back. |

**Thread safety**: `ReentrantReadWriteLock` — read lock for loads, write lock for saves/removes.
**Atomic writes**: Write to `.tmp`, then `Files.move()` (atomic on same filesystem).

---

## 6. Core Service Implementation

### 6.1 `ReminderScheduler` (`com.soundreminder.scheduler.ReminderScheduler`)

The heart of time-based behavior. Only the earliest reminder (queue head) is ever scheduled on the executor. A `ReentrantLock` serializes concurrent `recalculateScheduling()` calls.

```
schedule(reminder):
  queue.put(reminder)
  recalculateScheduling()

recalculateScheduling():
  lock.lock()
  cancel any existing ScheduledFuture
  peek at queue head
  compute delay = triggerTime - now
  if delay > 0:
    schedule new task with executor.schedule(task, delay)
  else:
    fire immediately
  lock.unlock()
```

**When a task fires**:
1. Poll queue → `onReminderFired.accept(reminder)`
2. `rescheduleIfRecurring(fired)` — if recurring, compute next occurrence, re-add to queue
3. `recalculateScheduling()` — schedule next

**Methods**:
- `schedule(Reminder)` — add to queue and recalculate.
- `cancel(String)` — remove by ID.
- `reSchedule(Reminder)` — replace existing entry with updated version (for snooze).
- `clear()` — empty queue and cancel pending future.
- `checkOverdueReminders()` — drain and fire all past-due reminders.
- `getPendingCount()` — queue size.

### 6.2 `SoundManager` (`com.soundreminder.sound.SoundManager`)

Audio playback using `javax.sound.sampled.Clip`.

**Sound loading fallback chain**:
1. Custom sound file path from `AppSettings` (if file exists).
2. Bundled resource `/sounds/alarm.wav`.
3. Programmatic WAV via `AlarmSoundGenerator.generateAlarmWav()`.

**Volume control**: Linear interpolation across MASTER_GAIN decibel range:
```java
float gain = minDb + (float) volume * range;
```

**Methods**: `startAlarm()`, `stopAlarm()`, `isPlaying()`, `updateSettings(AppSettings)`, `testSound()`.

### 6.3 `AlarmSoundGenerator` (`com.soundreminder.util.AlarmSoundGenerator`)

Generates 1-second WAV with two-tone alternating pattern (880 Hz / 660 Hz, 0.125s segments). Has standalone `main()` for CLI generation.

### 6.4 `NaturalLanguageParser` (`com.soundreminder.util.NaturalLanguageParser`)

Regex-based NLP parser with no external dependencies. Supports:

| Pattern | Example | Behavior |
|---|---|---|
| Countdown shorthand | `"20m meeting"`, `"1h30m"` | Sets countdown seconds + message |
| Countdown full | `"in 20 minutes"`, `"in 2 hours"` | Sets countdown seconds |
| Relative time | `"in 30m"`, `"in 2 hours"` | Sets triggerDateTime = now + duration |
| Absolute time | `"at 3:30pm"`, `"at 15:00"` | Sets time today (or tomorrow if past) |
| Date+time | `"on 2026-06-25 at 14:00"` | Sets exact date/time |
| Tomorrow | `"tomorrow at 9am"` | Sets tomorrow's date + time |
| Next weekday | `"next monday at 10:00"` | Finds next occurrence of that day |
| Recurrence | `"daily"`, `"every weekday"`, `"every monday"`, `"every 2 weeks"`, `"monthly"`, `"yearly"` | Sets RecurrenceRule |
| Priority tag | `"!urgent"`, `"!high"`, `"!low"` | Sets priority |

All remaining unrecognized text becomes the reminder message.

---

## 7. UI Implementation (JavaFX Frontend)

### 7.1 Component Tree

```
Stage (MainWindow)
├── Scene
│   ├── BorderPane
│   │   ├── TOP: ToolBar (Settings button, status label)
│   │   └── CENTER: TabPane
│   │       ├── Tab "Scheduled Reminder" → ScheduledReminderTab
│   │       │   ├── Input form: NLP text field, DatePicker, ComboBox(HH:mm),
│   │       │   │              TextArea, recurrence dropdown, priority dropdown,
│   │       │   │              tags chip input, image attach, Add button
│   │       │   └── ListView<Reminder> (custom ListCell with tag pills, priority border)
│   │       └── Tab "Countdown Timer" → CountdownTimerTab
│   │           ├── Input form: NLP shorthand field, Spinner(HH/MM/SS),
│   │           │              TextArea, recurrence dropdown, priority dropdown,
│   │           │              tags chip input, image attach, Start button
│   │           └── ListView<TimerDisplay> (custom ListCell)
│   └── (stylesheet: styles.css)
├── NotificationWindow (separate undecorated Stage)
│   ├── VBox
│   │   ├── Label (message)
│   │   ├── ImageView (optional)
│   │   ├── HBox snooze buttons (5m / 15m / 30m / 1h)
│   │   ├── Button "Acknowledge" / "Mark as Done" (text changes for recurring)
│   │   └── Button "Stop Recurring" (visible only for recurring)
├── SettingsDialog (modal Stage)
│   ├── VBox
│   │   ├── Sound file path + Browse
│   │   ├── Test Sound
│   │   └── Volume Slider (0-100%)
└── SystemTrayManager (AWT)
    ├── TrayIcon (16×16 PNG)
    └── PopupMenu (Open, Show Pending, Exit)
```

### 7.2 Data Flow Between UI Components

```
ScheduledReminderTab          CountdownTimerTab
       │                            │
       │  callback refs             │  callback refs
       ▼                            ▼
┌─────────────────────────────────────────┐
│            MainWindow                   │
│  ┌───────────────────────────────────┐  │
│  │  allReminders (CopyOnWriteArrayList)│ │
│  └───────────────────────────────────┘  │
│  │  │  │                               │
│  ▼  ▼  ▼                               │
│  Storage  Scheduler  SoundManager       │
└─────────────────────────────────────────┘
       │
       ▼
NotificationWindow  ──callbacks──►  MainWindow
  (done, snooze)                       │
SystemTrayManager   ──callbacks──►  MainWindow
```

### 7.3 Threading Model

| Thread | Purpose | Created By |
|---|---|---|
| **JavaFX Application Thread** | All UI rendering, event handlers, `Platform.runLater()` | JavaFX runtime |
| **SoundReminder-Worker** (pool, 4 daemon threads) | Reminder scheduling, replay timers, background I/O | `Executors.newScheduledThreadPool(4)` in `MainWindow` |
| **TimerUpdateThread** (1 daemon, 1s period) | Live countdown timer display | `CountdownTimerTab.startTimerUpdates()` |
| **SoundTestThread** (1 daemon) | 2-second sound test in settings | `SoundManager.testSound()` |

### 7.4 Window Lifecycle

- **Startup**: `Platform.setImplicitExit(false)`. Load reminders → schedule future ones → stagger overdue fires 1s apart.
- **Close request**: Minimize to tray if supported.
- **Tray "Exit"**: `cleanup()` — save state, stop sound, shutdown executors, remove tray icon, exit.

### 7.5 CSS Theme

Dark theme with: background `#1E1E1E`, surface `#2D2D2D`, accent `#0078D4`, text `#E0E0E0`, danger `#C42B1C`, timer `#FFB900`.

---

## 8. Key Technical Patterns & Decisions

### 8.1 Design Patterns Inventory

| Pattern | Location | Application |
|---|---|---|
| **MVC** | Entire app | Model (Reminder, AppSettings), View (JavaFX widgets), Controller (MainWindow, tabs) |
| **Facade** | `MainWindow` | Coordinates all services |
| **Observer / Callback** | `scheduler/`, `ui/` | `Consumer<Reminder>` fire callback; `BiConsumer<Reminder, Long>` snooze callback |
| **Priority Queue** | `ReminderScheduler` | `PriorityBlockingQueue<Reminder>` |
| **Read-Write Lock** | `StorageService` | `ReentrantReadWriteLock` |
| **Copy-on-Write** | `MainWindow` | `CopyOnWriteArrayList<Reminder>` |
| **Reentrant Lock** | `ReminderScheduler` | `ReentrantLock` for `recalculateScheduling()` |
| **Fallback Chain** | `SoundManager` | Custom file → bundled → generated |
| **Factory Method** | UI tabs | Custom `ListCell` factories |
| **Strategy** | `ReminderType` | Different tab UI per type |
| **Singleton (per instance)** | Services | Single instances injected via constructor |
| **Interpreter / Parser** | `NaturalLanguageParser` | Regex-based state machine parses free-text into `ParsedInput` |

### 8.2 Configuration Management

No config files outside `~/.soundreminder/`. `AppSettings` persisted to `settings.json`.

### 8.3 Security

No network access, no user accounts, no authentication. Standard OS file permissions.

### 8.4 Error Handling Strategy

| Layer | Approach |
|---|---|
| **Startup** | `Main.showErrorAndExit()` — `Alert(ERROR)` → `Platform.exit()` + `System.exit(1)` |
| **Storage I/O** | Propagates upward; callers handle with try-catch + logging |
| **Sound** | Catches `Exception` in `startAlarm()`, falls back to generated sound |
| **Logging** | `java.util.logging` — console at `WARNING+`, file at `ALL` to `~/.soundreminder/logs/` |
| **UI thread safety** | `Platform.runLater()` for all UI mutations from non-UI threads |

### 8.5 Thread Safety Summary

| Shared Resource | Protection |
|---|---|
| `allReminders` | `CopyOnWriteArrayList` |
| `reminders.json` / `settings.json` | `ReentrantReadWriteLock` |
| Scheduler state | `ReentrantLock` serializes `recalculateScheduling()` |
| `activeClip` in `SoundManager` | Single-threaded access from worker pool |
| UI state | All mutations on JavaFX thread via `Platform.runLater()` |

---

## 9. Critical Data Flows

### 9.1 Full Reminder Lifecycle (Fire → Acknowledge/Snooze/Done)

```
┌─────────┐    ┌──────────────┐    ┌──────────────┐    ┌───────────┐
│  User    │    │  Scheduler   │    │  SoundManager│    │  Storage  │
│  creates │    │  (executor)  │    │              │    │  Service  │
│  reminder│    │              │    │              │    │           │
└────┬─────┘    └──────┬───────┘    └──────┬───────┘    └─────┬─────┘
     │                 │                   │                 │
     │  1. addReminder │                   │                 │
     ├────────────────►│                   │                 │
     │                 │  2. saveReminders  │                 │
     │                 ├────────────────────────────────────►│
     │                 │                   │                 │
     │                 │  3. (time passes)  │                 │
     │                 │  triggerTime       │                 │
     │                 │  reached           │                 │
     │                 │                   │                 │
     │                 │  4. onReminder     │                 │
     │                 │  Fired             │                 │
     │                 ├───────────────────┼─────►(MainWindow)│
     │                 │                   │                 │
     │                 │  5. startAlarm()   │                 │
     │                 ├──────────────────►│                 │
     │                 │                   │  Clip.loop()    │
     │                 │                   │                 │
     │                 │  6. NotificationWindow.show()       │
     │                 │  (top-right popup, snooze buttons)  │
     │                 │                   │                 │
     │                 │  7. Recurring re-scheduled          │
     │                 │  computeNextOccurrence() → re-queue │
     │                 │                   │                 │
     │                 │  8. Replay cycle starts             │
     │                 │  60s on / 240s off                 │
     │                 │                   │                 │
     │ 9a. User Acknow-│                   │                 │
     │     ledges(recur)├───────────────────┼────►(stays     │
     │                 │                   │      active)    │
     │                 │                   │                 │
     │ 9b. User snoozes│                   │                 │
     │     (5/15/30/1h)├───────────────────┼────►(triggerTime│
     │                 │                   │      updated,   │
     │                 │                   │      re-queued) │
     │                 │                   │                 │
     │ 9c. Mark as Done│                   │                 │
     ├─────────────────┼───────────────────┼────►(removed)   │
     │                 │                   │                 │
     │                 │  10. stopAlarm()  │                 │
     │                 ├──────────────────►│                 │
     │                 │                   │                 │
     │                 │  11a. markAsDone()│                 │
     │                 │  (non-recurring   │                 │
     │                 │   or Stop Recur)  │                 │
     │                 │  saveReminders()  │                 │
     │                 ├────────────────────────────────────►│
     │                 │                   │                 │
     │                 │  11b. (Recurring) │                 │
     │                 │  just save state; │                 │
     │                 │  no markAsDone    │                 │
     │                 │                   │                 │
     │                 │  12. hide notif   │                 │
     │                 │  refresh UI       │                 │
```

### 9.2 Application Startup

```
Main.main()
  ├─ initLogging()
  │     └─ ConsoleHandler (WARNING+) + FileHandler (ALL) → ~/.soundreminder/logs/
  ├─ launch(JavaFX)
  │     └─ Main.start(stage)
  │           ├─ new StorageService()
  │           ├─ storage.loadSettings()
  │           ├─ new MainWindow(stage, storage, settings)
  │           │     ├─ new SoundManager(settings)
  │           │     ├─ new ReminderScheduler(onReminderFired)
  │           │     ├─ buildMainWindow()
  │           │     ├─ configureStage()
  │           │     ├─ loadSavedReminders()
  │           │     │     ├─ storage.loadReminders()
  │           │     │     ├─ future → scheduler.schedule()
  │           │     │     └─ overdue → stagger 1s apart [staggered]
  │           │     ├─ stage.show()
  │           │     └─ systemTrayManager.initializeTray()
  │           └─ (waiting for user interaction)
  └─ cleanup()
        ├─ storage.saveReminders()
        ├─ soundManager.stopAlarm()
        ├─ executorService.shutdown()
        ├─ scheduler.clear()
        ├─ systemTrayManager.removeTrayIcon()
        └─ Platform.exit()
```

### 9.3 Sound Loading Fallback

```
SoundManager.startAlarm()
  └─ loadSoundData()
       ├─ [1] customSoundPath exists?  → AudioSystem.getAudioInputStream(file)
       ├─ [2] bundled alarm.wav exists? → getResourceAsStream → AudioInputStream
       └─ [3] fallback → AlarmSoundGenerator.generateAlarmWav() → ByteArrayInputStream
```

---

## 10. Observations

### 10.1 Notable Implementation Details

1. **No FXML usage.** All UI built programmatically. `javafx.fxml` dependency removed as vestigial.
2. **Mockito was removed.** Declared but never used in any test.
3. **Thread pool sizing.** 4 threads in ScheduledExecutorService (only one scheduled at a time; pool handles replay + background I/O).
4. **`CopyOnWriteArrayList` trade-off.** Full array copy on every write; acceptable for <100 reminders.
5. **Priority queue not drained on shutdown.** Persistence handled by `MainWindow.cleanup()` saving `allReminders`.
6. **Recurring + snooze use same object reference.** `triggerTime` updated in-place; `CopyOnWriteArrayList` and `PriorityBlockingQueue` see updates through reference identity.
7. **NLP parser is pure regex.** No AI, no external libraries. All 20+ patterns are compiled `java.util.regex.Pattern` instances.

### 10.2 Potential Inconsistencies

- **No `final` modifier on model fields.** Reminder and AppSettings are mutable despite `@JsonCreator` constructors.
- **`ScheduledReminderTab` and `CountdownTimerTab` share no base class.** Independently implemented with duplicated patterns.

### 10.3 Test Coverage Notes

- **66+ tests total** covering all major components.
- Integration tests simulate full lifecycle end-to-end without UI.
- Storage tests use real file I/O (not mocked), validating atomic write behavior.
- Scheduler tests use real `ScheduledExecutorService` with short delays.
- Sound tests generate small WAV files in memory.
- No UI tests for JavaFX components.
