# SoundReminder — Forensic Architecture & Implementation Analysis

> Source of truth: the repository as of the analyzed commit. All statements below are grounded in repository evidence; unknowns are explicitly marked.

## 1. Executive Summary & System Boundaries

### 1.1 Project Purpose

SoundReminder is a single-process desktop alarm/reminder application built with Java 21 and JavaFX 21. It lets a user create scheduled reminders (specific date/time) and countdown timers (duration from now), attach optional images, play an alarm sound, show a top-right notification, and replay the alarm until acknowledged. Data persists as JSON under `~/.soundreminder/`.

**Evidence:** `src/main/java/com/soundreminder/Main.java` (`main`, `start`); `src/main/java/com/soundreminder/ui/MainWindow.java`; `README.md`.

### 1.2 Target Actors

| Actor | Interaction | Implementation |
|---|---|---|
| End user | Creates/removes reminders, settings | JavaFX UI in `ui/*Tab.java`, `SettingsDialog.java` |
| JavaFX runtime | Window lifecycle, rendering | `Main extends Application`, `Platform.setImplicitExit(false)` |
| File system | JSON persistence, images, logs | `storage/StorageService.java`, `Main.initLogging()` |
| Audio subsystem | Alarm playback | `sound/SoundManager.java` (Java Sound API) |
| OS system tray | Tray icon/menu | `ui/SystemTrayManager.java` (AWT `SystemTray`) |
| Scheduler thread pool | Timed firing | `scheduler/ReminderScheduler.java`, `MainWindow` executor |

### 1.3 System Boundaries

**Inside repository:** Java application module `com.soundreminder`, Gradle build, Python installer script, CSS/icon/sound resources, unit/integration tests.

**Outside repository:** JDK 21 runtime, JavaFX SDK modules, OS audio/tray APIs, user home directory storage (`~/.soundreminder/`), optional custom sound files, attached image files copied into storage.

### 1.4 Architectural Paradigm

Desktop monolith (single JVM process) with a hand-wired layered structure: `model` → `storage`/`sound`/`scheduler` → `ui`. It is not a web app, service, or distributed system. Evidence: `application.mainClass = "com.soundreminder.Main"` in `build.gradle.kts`; no HTTP/server dependencies.

---

## 2. Repository Structure & File Organization

### 2.1 Root Directory

| Path | Type | Purpose | Evidence |
|---|---|---|---|
| `build.gradle.kts` | file | Gradle build, deps, JavaFX plugin, fatJar | shown |
| `settings.gradle.kts` | file | root project name | shown |
| `gradle.properties` | file | Gradle properties | present |
| `gradlew`, `gradlew.bat` | files | Gradle wrapper | present |
| `module-info.java` (under src) | file | JPMS module declaration | shown |
| `src/main/java` | dir | application source | listed |
| `src/main/resources` | dir | CSS, icons, sounds | listed |
| `src/test/java` | dir | JUnit tests | listed |
| `build_installer.py` | file | jpackage portable builder | shown |
| `generate_alarm_sound.py` | file | generates alarm WAV | present |
| `generate_tray_icon.py` | file | generates tray PNG | present |
| `README.md` | file | documentation | shown |
| `.gitignore`, `.gitattributes` | files | git config | present |

### 2.2 Source Tree

| Package | Purpose | Depends On | Used By |
|---|---|---|---|
| `com.soundreminder` | entry point, logging | model, storage, ui | JVM |
| `com.soundreminder.model` | domain objects | Jackson | all |
| `com.soundreminder.storage` | JSON persistence | model, Jackson, NIO | ui, Main |
| `com.soundreminder.sound` | audio playback | model, Java Sound | ui |
| `com.soundreminder.scheduler` | timed firing | model, executors | ui |
| `com.soundreminder.ui` | JavaFX UI | model, storage, sound, scheduler | Main |
| `com.soundreminder.util` | NLP parser, sound generator | model | sound (generator only) |

### 2.3 Dependency Management

Gradle 8.x wrapper, Java 21 toolchain. Key dependencies from `build.gradle.kts`:

- `com.fasterxml.jackson.core:jackson-databind:2.17.0`
- `com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0`
- JavaFX 21.0.3 modules: base, graphics, controls, media, swing (classifier by OS)
- `org.junit.jupiter:junit-jupiter:5.10.2` (test)

Tests run with `--add-opens`/`--add-exports` for reflection. Fat JAR task `fatJar` bundles runtime classpath.

---

## 3. High-Level Architecture

### 3.1 Architecture Pattern

Manual constructor-based composition (no DI framework). UI components receive collaborators via constructors. Observed layers:

- `model`: passive data/serialization
- `storage`: persistence
- `sound`: audio
- `scheduler`: timing
- `ui`: presentation + orchestration (MainWindow owns lifecycle)
- `util`: helpers

### 3.2 Component Map

| Component | Location | Responsibility | Depends On | Used By |
|---|---|---|---|---|
| Main | `com.soundreminder.Main` | logging, startup | StorageService, MainWindow | JVM |
| StorageService | `storage/StorageService.java` | JSON persistence | model, Jackson | Main, ui |
| SoundManager | `sound/SoundManager.java` | alarm playback | AppSettings, Java Sound | ui |
| ReminderScheduler | `scheduler/ReminderScheduler.java` | timed firing | model, executors | MainWindow |
| MainWindow | `ui/MainWindow.java` | orchestration, replay | all | Main |
| ScheduledReminderTab | `ui/ScheduledReminderTab.java` | scheduled reminder CRUD | storage, sound, scheduler | MainWindow |
| CountdownTimerTab | `ui/CountdownTimerTab.java` | countdown CRUD | storage, sound, scheduler | MainWindow |
| NotificationWindow | `ui/NotificationWindow.java` | alarm popup | sound, storage | MainWindow |
| SystemTrayManager | `ui/SystemTrayManager.java` | tray icon | model, AWT | MainWindow |
| NaturalLanguageParser | `util/NaturalLanguageParser.java` | NLP parsing | model | (not wired in main UI) |

### 3.3 Dependency Direction

```mermaid
graph TD
    Main --> StorageService
    Main --> MainWindow
    MainWindow --> StorageService
    MainWindow --> SoundManager
    MainWindow --> ReminderScheduler
    MainWindow --> ScheduledReminderTab
    MainWindow --> CountdownTimerTab
    MainWindow --> SystemTrayManager
    ScheduledReminderTab --> StorageService
    ScheduledReminderTab --> SoundManager
    CountdownTimerTab --> StorageService
    SoundManager --> AppSettings
    ReminderScheduler --> Reminder
```

### 3.4 Architecture Implementation Evidence

**Manual composition in `MainWindow` constructor:**

```java
this.soundManager = new SoundManager(settings);
this.allReminders = new CopyOnWriteArrayList<>();
this.executorService = Executors.newScheduledThreadPool(4, r -> { ... });
this.scheduler = new ReminderScheduler(executorService, this::onReminderFired);
```

File: `src/main/java/com/soundreminder/ui/MainWindow.java` (constructor).

---

## 4. Domain Model & Business Logic

### 4.1 Domain Concepts

- `Reminder` (entity, Serializable, Comparable): id, type, triggerTime, message, imagePath, active, done, recurrence, priority, tags, snooze fields.
- `ReminderType`: SCHEDULED, COUNTDOWN.
- `AppSettings`: customSoundPath, volume (clamped 0–1).
- `Priority`: LOW/NORMAL/HIGH/URGENT with rank; JSON via `@JsonValue`/`@JsonCreator`.
- `RecurrenceRule`: frequency, interval, daysOfWeek, dayOfMonth, description; computes next occurrence.
- `Tag`: name + colorHex (validated regex).
- `ParsedInput`: NLP parser output.

### 4.2 Entities

| Entity | File | Identity | Key Fields |
|---|---|---|---|
| Reminder | `model/Reminder.java` | UUID `id` | type, triggerTime, message, imagePath, active, done, recurrenceRule, priority, tags, snoozeCount |
| AppSettings | `model/AppSettings.java` | none (singleton-ish file) | customSoundPath, volume |
| RecurrenceRule | `model/RecurrenceRule.java` | none | frequency, interval, daysOfWeek, dayOfMonth |

### 4.3 Business Rules

**Volume clamping:**

```java
public void setVolume(double volume) {
    this.volume = Math.max(0.0, Math.min(1.0, volume));
}
```
File: `src/main/java/com/soundreminder/model/AppSettings.java`.

**Color hex validation:**

```java
this.colorHex = (colorHex != null && colorHex.matches("^#[0-9A-Fa-f]{6}$"))
        ? colorHex : "#0078D4";
```
File: `src/main/java/com/soundreminder/model/Tag.java`.

**Reminder state transitions:**

```java
public void markAsDone() {
    this.done = true;
    this.active = false;
}
public void snoozeUntil(LocalDateTime newTime) {
    this.triggerTime = newTime;
    this.snoozeCount++;
    this.lastSnoozedAt = LocalDateTime.now();
    this.active = true;
    this.done = false;
}
```
File: `src/main/java/com/soundreminder/model/Reminder.java`.

**UI validation (scheduled):** message non-empty, date/time non-null, trigger in future. File: `ScheduledReminderTab.handleAddReminder()`.

**UI validation (countdown):** totalSeconds > 0 and message non-empty. File: `CountdownTimerTab.handleStartTimer()`.

### 4.4 State Transitions

```mermaid
stateDiagram-v2
    [*] --> Active: created
    Active --> Done: markAsDone
    Active --> Active: snoozeUntil (new trigger)
    Active --> Done: Stop Recurring
    Done --> [*]
```

Evidence: `Reminder.markAsDone`, `Reminder.snoozeUntil`, `NotificationWindow.handleDone/handleStopRecurring`.

### 4.5 Complex Algorithms

**Recurrence next occurrence (`RecurrenceRule.computeNextOccurrence`):** handles daily/weekly/monthly/yearly; weekly with named days uses `findNextDayOfWeek` scanning +1..+7 days; monthly clamps dayOfMonth to month length. File: `src/main/java/com/soundreminder/model/RecurrenceRule.java`.

**NLP parser (`NaturalLanguageParser.parse`):** regex-based extraction of priority, countdown shorthand, recurrence, absolute/relative/tomorrow/next-day times, then remaining text becomes message. File: `src/main/java/com/soundreminder/util/NaturalLanguageParser.java`.

**Alarm sound generation (`AlarmSoundGenerator.generateAlarmWav`):** two-tone 880/660 Hz sine wave with exponential envelope, written as 44.1kHz 16-bit mono WAV. File: `src/main/java/com/soundreminder/util/AlarmSoundGenerator.java`.

---

## 5. Persistence & Storage

### 5.1 Storage Technology

Jackson `ObjectMapper` with `JavaTimeModule`; file-based JSON; `ReentrantReadWriteLock`; atomic write via temp file + `Files.move(REPLACE_EXISTING)`.

### 5.2 Schema (JSON files)

| File | Content | Evidence |
|---|---|---|
| `~/.soundreminder/reminders.json` | `List<Reminder>` | `StorageService.remindersFile` |
| `~/.soundreminder/settings.json` | `AppSettings` | `StorageService.settingsFile` |
| `~/.soundreminder/images/` | copied image files | `imagesDir` |
| `~/.soundreminder/logs/` | dated log files | `Main.initLogging()` |

### 5.3 Data Access

Representative load/save fragments:

```java
public List<Reminder> loadReminders() {
    lock.readLock().lock();
    try {
        if (!Files.exists(remindersFile)) { return new ArrayList<>(); }
        String json = Files.readString(remindersFile);
        List<Reminder> reminders = objectMapper.readValue(json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Reminder.class));
        return reminders;
    } catch (IOException e) {
        LOGGER.log(Level.SEVERE, "Failed to load reminders", e);
        return new ArrayList<>();
    } finally { lock.readLock().unlock(); }
}
```

```java
public void saveReminders(List<Reminder> reminders) {
    lock.writeLock().lock();
    try {
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(reminders);
        Path tempFile = remindersFile.resolveSibling("reminders.json.tmp");
        Files.writeString(tempFile, json);
        Files.move(tempFile, remindersFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) { ... }
    finally { lock.writeLock().unlock(); }
}
```
File: `src/main/java/com/soundreminder/storage/StorageService.java`.

Image copy resolves relative path:

```java
public String copyImageToStorage(Path sourcePath) throws IOException {
    String newName = timestamp + "_" + fileName;
    Path destPath = imagesDir.resolve(newName);
    Files.copy(sourcePath, destPath, ...REPLACE_EXISTING);
    String relativePath = baseDir.relativize(destPath).toString().replace("\\", "/");
    return relativePath;
}
```

### 5.4 Transactions

No database transactions. Concurrency is guarded by `ReentrantReadWriteLock` per `StorageService` instance. Evidence: `lock` field.

### 5.5 Migrations

No migration framework. `@JsonIgnoreProperties(ignoreUnknown = true)` on `Reminder` tolerates unknown fields. Evidence: `model/Reminder.java`.

### 5.6 Caching

No caching layer. Sound data is cached in `SoundManager.soundData` byte array after first load. Evidence: `sound/SoundManager.java`.

---

## 6. Application Layer & Backend

### 6.1 API Architecture

No network API (REST/GraphQL/gRPC/WebSocket) is present. The application is a desktop GUI; interactions are in-process JavaFX events and callbacks. Evidence: no server/controller annotations; dependencies contain no web framework.

### 6.2 Controllers / Handlers

UI event handlers act as entry points:

- `ScheduledReminderTab.handleAddReminder()` → validates → creates `Reminder` → `allReminders.add` → `storageService.saveReminders` → `scheduleCallback.accept`.
- `CountdownTimerTab.handleStartTimer()` similar.
- `NotificationWindow.handleDone/handleSnooze/handleStopRecurring`.

### 6.3 Middleware / Filters / Interceptors

None present (no web pipeline).

### 6.4 Authentication / Authorization

Not applicable; single-user local app. No credentials, roles, or access checks. Evidence: no auth code.

### 6.5 Asynchronous Processing

- `ReminderScheduler` uses `ScheduledExecutorService` and `PriorityBlockingQueue<Reminder>`.
- `MainWindow.executorService`: `Executors.newScheduledThreadPool(4, daemon threads)`.
- `CountdownTimerTab.timerUpdateExecutor`: single-thread scheduled executor updating list every second.
- `SoundManager.testSound()` spawns a `SoundTestThread`.

Representative scheduling:

```java
scheduledFuture = executor.schedule(() -> {
    Reminder fired = reminderQueue.poll();
    if (fired != null && fired.isActive()) {
        onReminderFired.accept(fired);
        rescheduleIfRecurring(fired);
    }
    recalculateScheduling();
}, delayMillis, TimeUnit.MILLISECONDS);
```
File: `src/main/java/com/soundreminder/scheduler/ReminderScheduler.java`, `recalculateScheduling()`.

### 6.6 Error Handling

- `Main.start` catches `IOException` and generic `Exception`, shows JavaFX `Alert`, exits.
- `StorageService` catches `IOException`, logs, returns defaults/empty.
- UI handlers catch exceptions and show `Alert`.
- `SoundManager` catches audio exceptions and logs.

Logging: `java.util.logging` to console (WARNING+) and dated file (ALL). File: `Main.initLogging()`.

---

## 7. Frontend / Presentation Layer

### 7.1 Component Architecture

JavaFX, pure Java (no FXML). `MainWindow` builds `BorderPane` with toolbar + `TabPane`. Two tabs: `ScheduledReminderTab`, `CountdownTimerTab`. CSS from `/css/styles.css`.

### 7.2 State Management

- Shared `CopyOnWriteArrayList<Reminder> allReminders` passed to tabs.
- `SoundManager` holds settings reference.
- No global store framework.

### 7.3 Routing

Not applicable; tab-based navigation via `TabPane`.

### 7.4 Data Fetching

No network. Data loaded from `StorageService` at startup (`loadSavedReminders`).

### 7.5 Styling

CSS resource `/css/styles.css` applied to scenes. Evidence: `loadStylesheet` in MainWindow, SettingsDialog, NotificationWindow.

### 7.6 Performance

Only observable optimization: daemon thread pools, `CopyOnWriteArrayList`, atomic file writes. Countdown list refreshes every second.

---

## 8. Cross-Cutting Concerns

### 8.1 Design Patterns

| Pattern | Evidence | Location | Role |
|---|---|---|---|
| Manual DI (constructor injection) | constructors take collaborators | all components | wiring |
| Callback/observer | `Consumer<Reminder>`, `BiConsumer` | scheduler, MainWindow | firing events |
| Strategy (sound fallback) | custom → bundled → generated | `SoundManager.loadSoundData` | audio source selection |
| Thread-safe collection | `CopyOnWriteArrayList` | `MainWindow` | shared reminder list |

### 8.2 Dependency Injection

No framework. `MainWindow` constructs `SoundManager`, executor, scheduler; tabs receive callbacks. Evidence: constructors shown.

### 8.3 Configuration

- `AppSettings` persisted JSON.
- Volume clamped in setter.
- Custom sound path string; if `"Default (bundled)"` UI maps to null.
- No environment variables except `user.home`, `os.name`, `app.data.dir` (Android branch).
- Feature flags: none.

### 8.4 Security

- No authentication/authorization.
- Input validation for dates/times/messages.
- File copy uses provided path; no path traversal normalization beyond `resolveImagePath` `.normalize()`.
- No SQL (no DB).
- No CSRF/CORS (no web).

### 8.5 Observability

`java.util.logging`: console handler WARNING+, file handler ALL with custom formatter including timestamp/level/logger/message. Evidence: `Main.initLogging()`.

### 8.6 Testing

JUnit 5. Test files:

| Test | Scope |
|---|---|
| `IntegrationTest` | end-to-end workflow without UI |
| `SchedulerTest` | scheduling, overdue, cancel, clear |
| `SoundManagerTest` | playback, fallback, volume, custom sound |
| `StorageServiceTest` | persistence, images, settings |
| `AppSettingsTest`, `ReminderTest`, `ReminderTypeTest` | model |
| `AlarmSoundGeneratorTest` | sound generation |

Representative test fragment (overdue fires immediately):

```java
LocalDateTime pastTime = LocalDateTime.now().minusMinutes(5);
Reminder reminder = new Reminder(ReminderType.SCHEDULED, pastTime, "Overdue reminder", null);
testScheduler.schedule(reminder);
boolean fired = latch.await(3, TimeUnit.SECONDS);
assertTrue(fired);
```
File: `src/test/java/com/soundreminder/scheduler/SchedulerTest.java`.

---

## 9. Critical Execution Flows

### 9.1 Flow: Add Scheduled Reminder and Fire

**Overview:** User creates a scheduled reminder; it is persisted, scheduled, fires at trigger time, shows notification and alarm, replay cycle runs until acknowledged.

**Steps:**

1. `ScheduledReminderTab.handleAddReminder()` validates input.
2. Creates `Reminder(ReminderType.SCHEDULED, triggerTime, message, imagePath)`.
3. `allReminders.add(reminder)`; `storageService.saveReminders(allReminders)`.
4. `scheduleCallback.accept(reminder)` → `MainWindow.scheduler.schedule(reminder)`.
5. `ReminderScheduler.schedule` adds to `PriorityBlockingQueue`, calls `recalculateScheduling`.
6. Executor schedules task for delay; on fire, polls queue, calls `onReminderFired` (MainWindow).
7. `MainWindow.onReminderFired` (on JavaFX thread) starts sound, shows `NotificationWindow`, schedules replay.
8. On acknowledge: `onReminderDone` marks done, cancels scheduler, saves, refreshes UI.

**Evidence fragment (MainWindow onReminderFired):**

```java
private void onReminderFired(Reminder reminder) {
    Platform.runLater(() -> {
        if (activeNotification != null && activeNotification.isShowing()) {
            activeNotification.hide();
        }
        this.currentlyFiringReminder = reminder;
        this.replayCount = 0;
        soundManager.startAlarm();
        activeNotification = new NotificationWindow(soundManager, storageService,
                this::onReminderDone, this::onSnoozeReminder);
        activeNotification.show(reminder);
        scheduleReplay(reminder);
    });
}
```

**Sequence diagram:**

```mermaid
sequenceDiagram
    participant U as User
    participant T as ScheduledReminderTab
    participant S as StorageService
    participant Sch as ReminderScheduler
    participant MW as MainWindow
    participant SM as SoundManager
    participant NW as NotificationWindow
    U->>T: Add Reminder
    T->>S: saveReminders
    T->>Sch: schedule(reminder)
    Sch->>Sch: recalculateScheduling
    Sch-->>MW: onReminderFired (later)
    MW->>SM: startAlarm
    MW->>NW: show(reminder)
    U->>NW: Mark as Done
    NW->>MW: onReminderDone
    MW->>SM: stopAlarm
    MW->>S: saveReminders
```

**Edge cases:** past time rejected in UI; overdue reminders on startup are staggered 1 second apart (`loadSavedReminders`); inactive reminders ignored by scheduler.

### 9.2 Flow: Countdown Timer

1. `CountdownTimerTab.handleStartTimer` computes `triggerTime = now + duration`, creates `ReminderType.COUNTDOWN`.
2. Persist + schedule.
3. Timer list refreshes every second showing remaining time.
4. Fires like scheduled reminder.

### 9.3 Flow: Replay Cycle

`MainWindow.scheduleReplay` schedules: after 60s stop sound if notification not showing (see quirk); after 5 minutes (300s) restart alarm + notification; repeats. Evidence: `REPLAY_DELAY_SECONDS = 60`, `REPLAY_INTERVAL_SECONDS = 300`.

---

## 10. Infrastructure, DevOps & Deployment

### 10.1 Docker

Not present (no Dockerfile).

### 10.2 Docker Compose

Not present.

### 10.3 CI/CD

No CI workflow files found in repository (no `.github` directory in listing). Evidence unavailable.

### 10.4 Kubernetes

Not present.

### 10.5 Infrastructure as Code / Installer

`build_installer.py` uses Gradle `fatJar` then `jpackage` to create a portable app image under `build/installer/SoundReminder/`. It searches for `jpackage.exe` via `JAVA_HOME`/common paths. Evidence: script shown.

---

## 11. Code-Level Implementation Catalog

### Scheduling Mechanism
- **Purpose:** precise timed firing of earliest reminder.
- **Location:** `src/main/java/com/soundreminder/scheduler/ReminderScheduler.java`
- **Symbols:** `schedule`, `cancel`, `reSchedule`, `clear`, `checkOverdueReminders`, `recalculateScheduling`, `rescheduleIfRecurring`
- **Dependencies:** `ScheduledExecutorService`, `PriorityBlockingQueue`, `Reminder`
- **Consumers:** `MainWindow`
- **Source:** see section 6.5 fragment.

### Persistence Mechanism
- **Purpose:** atomic JSON save/load.
- **Location:** `src/main/java/com/soundreminder/storage/StorageService.java`
- **Symbols:** `loadReminders`, `saveReminders`, `loadSettings`, `saveSettings`, `copyImageToStorage`, `resolveImagePath`
- **Source:** section 5.3.

### Sound Playback
- **Purpose:** looped alarm with volume.
- **Location:** `src/main/java/com/soundreminder/sound/SoundManager.java`
- **Symbols:** `startAlarm`, `stopAlarm`, `isPlaying`, `loadSoundData`, `setClipVolume`, `testSound`
- **Source fragment:**

```java
public void startAlarm() {
    stopAlarm();
    try {
        if (soundData == null) { soundData = loadSoundData(); }
        AudioInputStream ais = AudioSystem.getAudioInputStream(
                new java.io.ByteArrayInputStream(soundData));
        Clip clip = AudioSystem.getClip();
        clip.open(ais);
        setClipVolume(clip, settings.getVolume());
        clip.loop(Clip.LOOP_CONTINUOUSLY);
        activeClip = clip;
    } catch (LineUnavailableException | IOException | UnsupportedAudioFileException e) {
        LOGGER.log(Level.SEVERE, "Failed to start alarm", e);
    }
}
```

### Replay Logic
- **Purpose:** auto-stop after 60s, restart after 5 min until done.
- **Location:** `MainWindow.scheduleReplay`
- **Source:** see 9.3.

### NLP Parser
- **Purpose:** parse free-text reminder input.
- **Location:** `src/main/java/com/soundreminder/util/NaturalLanguageParser.java`
- **Symbols:** `parse`, `extractPriority`, `tryParseCountdownShorthand`, `extractCountdown`, `extractRecurrence`, `extractAbsoluteDateTime`, etc.
- **Status:** implemented but not referenced by UI tabs in current code.

### Recurrence Calculation
- **Purpose:** compute next trigger for recurring reminders.
- **Location:** `RecurrenceRule.computeNextOccurrence`
- **Source:** section 4.5.

### Image Storage
- **Purpose:** copy + relative path resolution.
- **Location:** `StorageService.copyImageToStorage`, `resolveImagePath`.

### System Tray
- **Purpose:** tray icon, menu, balloons.
- **Location:** `SystemTrayManager`.

### Logging
- **Purpose:** console + file logging.
- **Location:** `Main.initLogging`.

---

## 12. Important Classes & Functions

| Symbol | File | Responsibility | Importance |
|---|---|---|---|
| `Main.main/start/stop/initLogging` | `Main.java` | entry, logging, startup | High |
| `StorageService` (all) | `StorageService.java` | persistence | High |
| `SoundManager` (all) | `SoundManager.java` | audio | High |
| `ReminderScheduler` (all) | `ReminderScheduler.java` | timing | High |
| `MainWindow` (constructor, onReminderFired, scheduleReplay, onReminderDone, onSnoozeReminder, cleanup) | `MainWindow.java` | orchestration | High |
| `ScheduledReminderTab.handleAddReminder` | `ScheduledReminderTab.java` | create scheduled | High |
| `CountdownTimerTab.handleStartTimer` | `CountdownTimerTab.java` | create countdown | High |
| `NotificationWindow.show/handleDone/handleSnooze` | `NotificationWindow.java` | alarm popup | High |
| `NaturalLanguageParser.parse` | `NaturalLanguageParser.java` | NLP | Medium (unused in UI) |
| `RecurrenceRule.computeNextOccurrence` | `RecurrenceRule.java` | recurrence | Medium |
| `AlarmSoundGenerator.generateAlarmWav` | `AlarmSoundGenerator.java` | sound gen | Medium |
| `SystemTrayManager` | `SystemTrayManager.java` | tray | Medium |

---

## 13. Configuration & Runtime Behavior

**Startup sequence:**

1. `Main.main` calls `initLogging()` (creates `~/.soundreminder/logs/`, adds handlers).
2. `launch(args)` → JavaFX calls `Main.start(primaryStage)`.
3. `Platform.setImplicitExit(false)`.
4. `new StorageService()` creates `~/.soundreminder/` + `images/`, configures Jackson.
5. `storageService.loadSettings()` (defaults if missing).
6. `new MainWindow(primaryStage, storageService, settings)`:
   - constructs SoundManager, CopyOnWriteArrayList, executor, scheduler
   - `buildMainWindow()` builds UI, sets close handler
   - `loadSavedReminders()` loads JSON, schedules future, staggers overdue
   - configures stage, `stage.show()`
   - creates SystemTrayManager
7. App runs; daemon worker threads handle scheduling.

**Shutdown:** close handler minimizes to tray if supported; otherwise `cleanup()` saves reminders, stops sound, shuts down executors, removes tray.

Evidence: `Main.start`, `MainWindow` constructor, `cleanup`.

---

## 14. Empirical Observations & Codebase Quirks

### 14.1 Idiosyncrasies
- Pure Java JavaFX (no FXML); all UI built programmatically.
- Android branch in `StorageService` (`os.name` contains "android") but GluonFX plugin commented out in build.
- `Reminder` has `serialVersionUID = 2L` and implements `Serializable` but storage uses JSON, not Java serialization.

### 14.2 Technical Debt
- `NaturalLanguageParser`, `ParsedInput`, `Tag`, `Priority`, `RecurrenceRule` are implemented but the UI tabs do not use them (no NLP input field, no tag/priority/recurrence controls). Recurring reminder feature is model+scheduling capable but not creatable from UI.
- `scheduleReplay` condition `activeNotification != null && !activeNotification.isShowing()` means the 60-second stop branch only runs if notification is hidden; since notification is showing when scheduled, the branch is effectively skipped (observable logic mismatch with README).
- `ReminderScheduler.checkOverdueReminders()` exists but is not called in `MainWindow.loadSavedReminders` (overdue handled separately).
- README claims "65 tests"; repository contains 8 test files (test count not 65 by method count).
- README says notification appears "top-left"/"top-right"? README says top-left? Actually README: "Top-Left Notification"; code positions top-right (`bounds.getMaxX() - width - 10`). Contradiction.
- `SettingsDialog.handleBrowseSound` stores absolute path in label but `handleSave` treats label text as path; if user doesn't browse, label shows `"Default (bundled)"` and maps to null.

### 14.3 Strengths
- Thread-safe shared list (`CopyOnWriteArrayList`).
- Atomic file writes (temp + rename).
- Read/write locks for storage.
- Overdue reminders staggered to avoid UI flooding.
- Sound fallback chain (custom → bundled → generated).
- Unit/integration tests covering scheduler, sound, storage.

### 14.4 Contradictions
- README "Top-Left Notification" vs code top-right position.
- README "Automatic Replay — sound stops after 60s, restarts after 5 minutes" vs `scheduleReplay` logic condition likely not executing stop branch.
- README "65 tests" vs repository test files.

---

## 15. Architecture Decision Evidence

| Decision/Property | Evidence | Confidence |
|---|---|---|
| Desktop monolith | `Main extends Application`, no web deps | High |
| File-based JSON persistence | `StorageService` | High |
| Manual DI | constructors | High |
| JavaFX UI | build javafx plugin, `ui/*` | High |
| Thread pool scheduling | `ReminderScheduler`, executors | High |
| No authentication | no auth code | High |
| Recurring model exists but unused in UI | grep shows no UI usage | Medium |
| NLP parser unused in UI | grep | Medium |

---

## 16. Final System Model

### Component Graph

```mermaid
graph TD
    Main --> StorageService
    Main --> MainWindow
    MainWindow --> SoundManager
    MainWindow --> ReminderScheduler
    MainWindow --> ScheduledReminderTab
    MainWindow --> CountdownTimerTab
    MainWindow --> SystemTrayManager
    MainWindow --> NotificationWindow
```

### Data Flow

```mermaid
flowchart LR
    UI[JavaFX UI] --> Model[Reminder/AppSettings]
    Model --> Storage[StorageService JSON]
    Storage --> Disk[(~/.soundreminder)]
    Scheduler[ReminderScheduler] --> Callback[MainWindow.onReminderFired]
    Callback --> Sound[SoundManager]
    Callback --> Notif[NotificationWindow]
```

### Runtime Flow

```mermaid
flowchart TD
    Start[main] --> Log[initLogging]
    Log --> Launch[JavaFX launch]
    Launch --> StartApp[Main.start]
    StartApp --> StorageInit[StorageService]
    StorageInit --> LoadSettings[loadSettings]
    LoadSettings --> MainWindow[MainWindow]
    MainWindow --> LoadReminders[loadSavedReminders]
    LoadReminders --> Schedule[Scheduler]
    Schedule --> Run[Event Loop]
```

### Technology Matrix

| Area | Technology | Evidence |
|---|---|---|
| Language | Java 21 | build.gradle.kts |
| Framework | JavaFX 21 | javafx plugin/deps |
| Build | Gradle 8 | wrapper |
| Persistence | Jackson JSON | StorageService |
| Audio | Java Sound API | SoundManager |
| Concurrency | ScheduledExecutorService, PriorityBlockingQueue | scheduler |
| Testing | JUnit 5 | build.gradle.kts |
| Containerization | None | no Dockerfile |
| CI/CD | None in repo | no .github |
| Installer | jpackage via Python | build_installer.py |

---

## 17. Missing / Unavailable Information

- Production runtime environment and performance characteristics cannot be determined statically.
- No CI/CD configuration present in repository.
- No database; all persistence local files.
- Android build path is scaffolded but disabled (GluonFX plugin commented out); runtime behavior on Android unverified.
- `checkOverdueReminders()` not invoked; intended usage unknown from code.
- External behavior of OS tray on all platforms not verifiable from repository.
- Test execution results not included in repository (no reports).
- No license file? README says MIT badge but no LICENSE file observed.
