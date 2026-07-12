# AutoMacro Pro

Workflow Automation & Auto-Clicker untuk Windows. Dibangun dengan **Java Swing**,
`java.awt.Robot` untuk eksekusi input, dan **JNativeHook** untuk global hotkey
(jalan walau aplikasi minimized/tidak fokus).

## Modul

1. **Autoclicker** - interval hingga 1ms, limit Infinite/Fixed, Left/Right/Middle,
   Single/Double/**Hold**/Drag, posisi Current Cursor atau Fixed (Pick Location).
2. **Macro Sequencer** - urutan aksi Mouse/Keyboard/Delay, loop 1x atau Infinite,
   export/import project sebagai file `.amacro` (JSON).

Kedua modul punya: Start/Stop/Toggle (pause-resume), Save/Reset Settings, dan
Custom Hotkey per kontrol (independen antar modul).

## Keamanan & Performa

- **Failsafe**: gerakkan kursor ke **pojok layar manapun** (di monitor manapun) →
  proses berhenti seketika, termasuk saat sedang menunggu interval, Delay step,
  atau **Hold Duration**. Lihat `engine/FailsafeMonitor.java` & `engine/PreciseTimer.java`.
- **Tidak ada `Thread.sleep` di Event Dispatch Thread (EDT)**: semua eksekusi
  Robot jalan di background thread; EDT cuma menampilkan UI. Lihat
  `engine/AutoClickerEngine.java` / `engine/MacroEngine.java`.
- **Tidak ada busy-loop 100% CPU**: `PreciseTimer` memakai sleep+spin hybrid
  dengan `Thread.onSpinWait()`, bukan tight loop kosong.
- **Tombol/tombol-keyboard tidak akan "nyangkut"**: setiap aksi Hold/Drag/kombinasi
  keyboard dibungkus `try/finally` sehingga `mouseRelease`/`keyRelease` SELALU
  jalan walau failsafe/Stop memutus proses di tengah jalan. Lihat
  `engine/RobotExecutor.java`.
- **Error logging ringan**: semua kegagalan eksekusi tercatat ke
  `%APPDATA%\AutoMacroPro\automacropro.log` (Windows) via `util/AppLogger.java`.

## Struktur Project (Maven, siap dibuka di NetBeans)

```
AutoMacroPro/
├── pom.xml                  <- buka folder ini di NetBeans (File > Open Project)
├── build-exe.bat            <- script otomatis build .exe (lihat di bawah)
├── launch4j-config.xml      <- alternatif build .exe tanpa jpackage
└── src/main/java/com/automacropro/
    ├── Main.java             <- entry point
    ├── model/                <- data (enum + POJO), semua punya toMap()/fromMap()
    ├── util/                 <- AppLogger, KeyCodeUtil (AWT VK_ vs JNativeHook VC_)
    ├── json/                 <- SimpleJson (parser/writer JSON tanpa dependency luar)
    ├── persistence/          <- SettingsManager (app settings), MacroProjectIO (.amacro)
    ├── engine/                <- LOGIC: RobotExecutor, PreciseTimer, FailsafeMonitor,
    │                            AutoClickerEngine, MacroEngine (Execution Loop)
    ├── hotkey/               <- KEY LISTENER: GlobalHotkeyManager, PositionPicker
    └── ui/                   <- UI: MainFrame, AutoClickerPanel, MacroSequencerPanel,
                                  ActionEditorDialog, MouseActionConfigPanel, dst.
```

Satu-satunya dependency eksternal: **JNativeHook** (`com.github.kwhat:jnativehook:2.2.2`),
otomatis ter-download oleh Maven saat project dibuka/dibuild - tidak perlu
download jar manual.

## Menjalankan dari NetBeans

1. **File > Open Project**, pilih folder `AutoMacroPro` (yang ada `pom.xml`-nya).
   NetBeans otomatis mendeteksinya sebagai Maven project dan men-download
   JNativeHook saat pertama kali di-build.
2. Klik kanan project > **Run**, atau tekan **F6**. Class `Main` sudah
   diset sebagai main class lewat `pom.xml` (`exec-maven-plugin`).
3. Requirement: **JDK 17 atau lebih baru** (cek lewat **Tools > Java Platforms**
   di NetBeans bila perlu menambahkan JDK).

## Build ke .exe (siap install)

Ada 2 opsi. **jpackage direkomendasikan** karena bisa membundel JRE sehingga
`.exe` hasilnya **tidak butuh Java terinstall** di komputer target (penting
untuk PC gaming yang belum tentu ada Java).

### Opsi A - jpackage (built-in JDK 14+, hasil: installer .exe)

```bat
build-exe.bat
```

Script ini otomatis: `mvn clean package` (bikin `target/AutoMacroPro.jar`)
lalu memanggil `jpackage --type exe ...`. Hasil installer ada di folder `dist/`.

**Requirement**: [WiX Toolset 3.x](https://wixtoolset.org/) terpasang & ada di
PATH (dibutuhkan jpackage untuk `--type exe`/`--type msi` di Windows). Tanpa
WiX, ganti `--type exe` jadi `--type app-image` pada `build-exe.bat` - hasilnya
folder aplikasi siap-pakai (ada `AutoMacro Pro.exe` di dalamnya) tanpa proses
instalasi, tetap tidak butuh Java terinstall.

Manual (tanpa script), dari folder project setelah `mvn clean package`:

```bat
jpackage --type exe --input target --dest dist ^
  --name "AutoMacro Pro" --app-version 1.0.0 ^
  --main-jar AutoMacroPro.jar --main-class com.automacropro.Main ^
  --win-menu --win-shortcut --icon icon.ico
```

(`--icon icon.ico` opsional - siapkan file `.ico` sendiri kalau mau ikon custom.)

### Opsi B - Launch4j (lebih ringan, tapi butuh Java sudah terinstall di target)

1. `mvn clean package` (hasil: `target/AutoMacroPro.jar`).
2. Buka [Launch4j](https://launch4j.sourceforge.net/), **Load** file
   `launch4j-config.xml`, lalu klik **Build wrapper**. Hasil: `dist/AutoMacroPro.exe`.
3. Karena cara ini tidak membundel JRE, pastikan target PC punya JRE 17+,
   ATAU isi `<bundledJrePath>` di XML menuju folder JRE portable yang kamu
   sertakan bersama `.exe`-nya.

## ⚠️ PENTING: Menjalankan sebagai Administrator

Game yang berjalan dengan hak akses Administrator/Elevated **tidak akan
menerima** input dari `java.awt.Robot` atau hook JNativeHook yang berjalan
dengan hak akses biasa (ini pembatasan keamanan Windows - UIPI, mencegah
proses non-elevated mengontrol proses elevated). **Klik kanan `AutoMacro Pro.exe`
> Run as Administrator** kalau game target juga jalan sebagai Administrator.
Lihat catatan lebih lengkap di bagian "Notes & Best Practices" pada percakapan
sebelumnya.

## Format File Project (`.amacro`)

Plain JSON, bisa dibuka/diedit pakai text editor biasa kalau perlu. Field
`formatVersion` naik setiap ada perubahan struktur data (sekarang `2`, sejak
ClickMode `HOLD` ditambahkan) - file lama tetap bisa di-import, field yang
belum ada di file lama otomatis memakai nilai default yang aman (lihat
`fromMap()` di tiap class `model/`).
