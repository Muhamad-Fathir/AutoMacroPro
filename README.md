# AutoMacro Pro
Workflow Automation & Auto-Clicker for Windows. Built with **Java Swing**, `java.awt.Robot` for input execution, and **JNativeHook** for global hotkeys (works even when the application is minimized or unfocused).

## Modules
1. **Autoclicker** - intervals down to 1ms, Infinite/Fixed limit, Left/Right/Middle buttons, Single/Double/**Hold**/Drag actions, and Current Cursor or Fixed (Pick Location) positioning.
2. **Macro Sequencer** - sequential Mouse/Keyboard/Delay actions, 1x or Infinite looping, and project export/import as `.amacro` (JSON) files.

Both modules include: Start/Stop/Toggle (pause-resume), Save/Reset Settings, and Custom Hotkeys per control (independent between modules).

## Safety & Performance
* **Failsafe**: move the cursor to **any screen corner** (on any monitor) → the process stops immediately, including while waiting for an interval, a Delay step, or a **Hold Duration**. See `engine/FailsafeMonitor.java` & `engine/PreciseTimer.java`.
* **No `Thread.sleep` on the Event Dispatch Thread (EDT)**: all Robot executions run on a background thread; the EDT only renders the UI. See `engine/AutoClickerEngine.java` / `engine/MacroEngine.java`.
* **No 100% CPU busy-loop**: `PreciseTimer` uses a sleep+spin hybrid with `Thread.onSpinWait()` instead of an empty tight loop.
* **Mouse buttons/keyboard keys will not get "stuck"**: every Hold/Drag/keyboard combination action is wrapped in a `try/finally` block so that `mouseRelease`/`keyRelease` ALWAYS executes even if the failsafe or Stop button interrupts the process mid-execution. See `engine/RobotExecutor.java`.
* **Lightweight error logging**: all execution failures are logged to `%APPDATA%\AutoMacroPro\automacropro.log` (Windows) via `util/AppLogger.java`.

## Project Structure (Maven, ready to open in NetBeans)

```text
AutoMacroPro/
├── pom.xml                  <- open this folder in NetBeans (File > Open Project)
├── build-exe.bat            <- automated .exe build script (see below)
├── launch4j-config.xml      <- alternative .exe build without jpackage
└── src/main/java/com/automacropro/
    ├── Main.java             <- entry point
    ├── model/                <- data (enum + POJO), all have toMap()/fromMap()
    ├── util/                 <- AppLogger, KeyCodeUtil (AWT VK_ vs JNativeHook VC_)
    ├── json/                 <- SimpleJson (JSON parser/writer with no external dependencies)
    ├── persistence/          <- SettingsManager (app settings), MacroProjectIO (.amacro)
    ├── engine/                <- LOGIC: RobotExecutor, PreciseTimer, FailsafeMonitor,
    │                            AutoClickerEngine, MacroEngine (Execution Loop)
    ├── hotkey/               <- KEY LISTENER: GlobalHotkeyManager, PositionPicker
    └── ui/                   <- UI: MainFrame, AutoClickerPanel, MacroSequencerPanel,
                                  ActionEditorDialog, MouseActionConfigPanel, etc.
```

The only external dependency is **JNativeHook** (`com.github.kwhat:jnativehook:2.2.2`), which is automatically downloaded by Maven when the project is opened/built - no need to download the jar manually.

## Running from NetBeans
1. **File > Open Project**, select the `AutoMacroPro` folder (which contains `pom.xml`). NetBeans automatically detects it as a Maven project and downloads JNativeHook during the first build.
2. Right-click the project > **Run**, or press **F6**. The `Main` class is already set as the main class via `pom.xml` (`exec-maven-plugin`).
3. Requirement: **JDK 17 or newer** (check via **Tools > Java Platforms** in NetBeans if you need to add a JDK).

## Build to .exe (installer ready)
There are 2 options. **jpackage is recommended** because it can bundle the JRE so the resulting `.exe` **does not require Java to be installed** on the target computer (important for gaming PCs that may not have Java).

### Option A - jpackage (built-in JDK 14+, result: .exe installer)

```bat
build-exe.bat
```

This script automatically runs `mvn clean package` (creating `target/AutoMacroPro.jar`) and then calls `jpackage --type exe ...`. The resulting installer is located in the `dist/` folder.

**Requirement**: [WiX Toolset 3.x](https://wixtoolset.org/) must be installed & added to the PATH (required by jpackage for `--type exe`/`--type msi` on Windows). Without WiX, change `--type exe` to `--type app-image` in `build-exe.bat` - the result will be a ready-to-use application folder (containing `AutoMacro Pro.exe` inside) with no installation process, and it still does not require Java to be installed.

Manual build (without the script), from the project folder after running `mvn clean package`:

```bat
jpackage --type exe --input target --dest dist ^
  --name "AutoMacro Pro" --app-version 1.0.0 ^
  --main-jar AutoMacroPro.jar --main-class com.automacropro.Main ^
  --win-menu --win-shortcut --icon icon.ico

```

(`--icon icon.ico` is optional - prepare your own `.ico` file if you want a custom icon.)

### Option B - Launch4j (more lightweight, but requires Java already installed on the target)
1. Run `mvn clean package` (result: `target/AutoMacroPro.jar`).
2. Open [Launch4j](https://launch4j.sourceforge.net/), **Load** the `launch4j-config.xml` file, then click **Build wrapper**. Result: `dist/AutoMacroPro.exe`.
3. Since this method does not bundle the JRE, ensure the target PC has JRE 17+, OR fill in `<bundledJrePath>` in the XML pointing to a portable JRE folder that you include alongside the `.exe`.

## IMPORTANT: Running as Administrator
Games running with Administrator/Elevated privileges **will not receive** input from `java.awt.Robot` or JNativeHook hooks running with standard privileges (this is a Windows security restriction - UIPI, preventing non-elevated processes from controlling elevated processes). **Right-click `AutoMacro Pro.exe` > Run as Administrator** if the target game is also running as an Administrator. See the full notes in the "Notes & Best Practices" section in the previous conversation.

## Project File Format (.amacro)
Plain JSON, which can be opened/edited using a standard text editor if needed. The `formatVersion` field increments whenever there is a data structure change (currently `2`, since ClickMode `HOLD` was added) - older files can still be imported, and fields that do not exist in older files will automatically use safe default values (see `fromMap()` in each `model/` class).
