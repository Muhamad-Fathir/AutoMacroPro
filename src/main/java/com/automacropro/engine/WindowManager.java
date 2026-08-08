package com.automacropro.engine;

import com.automacropro.util.AppLogger;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Borderless-window utility for the Window Manager tab.
 *
 * <h3>Why JNA</h3>
 * Java can only restyle windows it owns. Making <em>another</em> process's
 * window borderless means editing that window's Win32 style bits, which has no
 * Java API at all - hence {@code User32.SetWindowLong(GWL_STYLE, ...)}.
 * jna-platform already ships the User32 bindings, so nothing is hand-written.
 *
 * <h3>Threading</h3>
 * Every method here is blocking and MUST be called off the EDT.
 * {@code SetWindowPos} synchronously sends WM_WINDOWPOSCHANGING to the target
 * window, so a hung target hangs the caller - which would freeze our UI if this
 * ran on the EDT. {@code WindowManagerPanel} calls all of it from SwingWorkers.
 *
 * <h3>Restore</h3>
 * The original style bits and bounds are captured <em>before</em> the first
 * modification and kept in {@link #originals}, because Win32 offers no "undo"
 * - once the caption bit is cleared, the previous value is unrecoverable. If
 * this map is lost (app restart), the user restores the window manually via the
 * target app itself.
 */
public final class WindowManager {

    /** Style bits that together make up the frame: caption bar, resize grip, buttons. */
    private static final int WS_BORDER = 0x00800000;
    private static final int WS_DLGFRAME = 0x00400000;
    private static final int WS_THICKFRAME = 0x00040000;
    private static final int WS_MINIMIZEBOX = 0x00020000;
    private static final int WS_MAXIMIZEBOX = 0x00010000;
    private static final int WS_SYSMENU = 0x00080000;
    private static final int WS_CAPTION = WS_BORDER | WS_DLGFRAME;
    private static final int FRAME_BITS =
            WS_CAPTION | WS_THICKFRAME | WS_MINIMIZEBOX | WS_MAXIMIZEBOX | WS_SYSMENU;

    private static final int WS_EX_DLGMODALFRAME = 0x00000001;
    private static final int WS_EX_WINDOWEDGE = 0x00000100;
    private static final int WS_EX_CLIENTEDGE = 0x00000200;
    private static final int WS_EX_STATICEDGE = 0x00020000;
    private static final int WS_EX_TOOLWINDOW = 0x00000080;
    private static final int EX_FRAME_BITS =
            WS_EX_DLGMODALFRAME | WS_EX_WINDOWEDGE | WS_EX_CLIENTEDGE | WS_EX_STATICEDGE;

    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_NOOWNERZORDER = 0x0200;
    private static final int SWP_FRAMECHANGED = 0x0020;
    private static final int SWP_SHOWWINDOW = 0x0040;

    private static final int GW_OWNER = 4;
    private static final int DWMWA_CLOAKED = 14;
    private static final int ERROR_ACCESS_DENIED = 5;

    /**
     * Minimal hand-rolled dwmapi binding - jna-platform has no Dwmapi class,
     * and this is the single function we need from it. Loaded lazily inside
     * {@link #isCloaked} so a machine without dwmapi.dll degrades to a noisier
     * window list instead of failing to load this class at all.
     */
    private interface Dwmapi extends Library {
        Dwmapi INSTANCE = Native.load("dwmapi", Dwmapi.class);

        WinNT.HRESULT DwmGetWindowAttribute(WinDef.HWND hwnd, int attribute,
                                           IntByReference value, int valueSize);
    }

    /** Pre-modification state, so {@link #restore} has something to put back. */
    private static final Map<WinDef.HWND, Saved> originals = new LinkedHashMap<>();

    private WindowManager() {
    }

    /** One row in the window list. */
    public static final class WindowInfo {
        public final WinDef.HWND hwnd;
        public final String title;
        public final int pid;

        WindowInfo(WinDef.HWND hwnd, String title, int pid) {
            this.hwnd = hwnd;
            this.title = title;
            this.pid = pid;
        }

        public boolean isBorderless() {
            return originals.containsKey(hwnd);
        }

        @Override
        public String toString() {
            return title + "  (PID " + pid + ")" + (isBorderless() ? "  [borderless]" : "");
        }
    }

    private static final class Saved {
        final int style;
        final int exStyle;
        final WinDef.RECT bounds;

        Saved(int style, int exStyle, WinDef.RECT bounds) {
            this.style = style;
            this.exStyle = exStyle;
            this.bounds = bounds;
        }
    }

    /** Thrown for the expected, user-actionable failures (elevation, dead window). */
    public static class WindowManagerException extends Exception {
        public WindowManagerException(String message) {
            super(message);
        }
    }

    /**
     * Top-level, user-visible windows. Filters out the noise that makes a raw
     * EnumWindows list unusable: invisible windows, owned dialogs, tool windows,
     * untitled windows, our own frame, and DWM-cloaked UWP shells (the dozens of
     * ghost "Settings"/"Calculator" entries that exist but are not on screen).
     */
    public static List<WindowInfo> listWindows() {
        List<WindowInfo> found = new ArrayList<>();
        User32.INSTANCE.EnumWindows((hwnd, data) -> {
            if (!User32.INSTANCE.IsWindowVisible(hwnd)) {
                return true;
            }
            if (User32.INSTANCE.GetWindow(hwnd, new WinDef.DWORD(GW_OWNER)) != null) {
                return true; // owned dialog/popup, not a real app window
            }
            if ((User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE) & WS_EX_TOOLWINDOW) != 0) {
                return true;
            }
            char[] buffer = new char[512];
            int length = User32.INSTANCE.GetWindowText(hwnd, buffer, buffer.length);
            if (length == 0) {
                return true;
            }
            if (isCloaked(hwnd)) {
                return true;
            }
            IntByReference pid = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid);
            if (pid.getValue() == currentProcessId()) {
                return true; // never offer to mangle our own window
            }
            found.add(new WindowInfo(hwnd, new String(buffer, 0, length), pid.getValue()));
            return true;
        }, null);
        found.sort((a, b) -> a.title.compareToIgnoreCase(b.title));
        return found;
    }

    /**
     * Strips the frame and resizes the window to fill the monitor it currently
     * sits on (not the primary monitor - that would fling multi-monitor users'
     * windows across the desktop).
     */
    public static void makeBorderless(WindowInfo target) throws WindowManagerException {
        WinDef.HWND hwnd = target.hwnd;
        requireAlive(hwnd);

        int style = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_STYLE);
        int exStyle = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE);

        WinDef.RECT bounds = new WinDef.RECT();
        User32.INSTANCE.GetWindowRect(hwnd, bounds);
        originals.putIfAbsent(hwnd, new Saved(style, exStyle, bounds));

        // A maximized window keeps its maximized flag and would snap back to a
        // frame-sized client area the moment it is restored, undoing our resize.
        User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_RESTORE);

        apply(hwnd, style & ~FRAME_BITS, exStyle & ~EX_FRAME_BITS, monitorBounds(hwnd));
    }

    /** Puts back the exact style bits and bounds captured before the first change. */
    public static void restore(WindowInfo target) throws WindowManagerException {
        Saved saved = originals.get(target.hwnd);
        if (saved == null) {
            throw new WindowManagerException(
                    "This window was not made borderless by AutoMacro Pro in this session, "
                            + "so there is no saved state to restore.");
        }
        requireAlive(target.hwnd);
        apply(target.hwnd, saved.style, saved.exStyle, saved.bounds);
        originals.remove(target.hwnd);
    }

    private static void apply(WinDef.HWND hwnd, int style, int exStyle, WinDef.RECT rect)
            throws WindowManagerException {
        Native.setLastError(0);
        // GWL_STYLE/GWL_EXSTYLE hold 32-bit values, so the non-Ptr variant is
        // correct on 64-bit Windows too (only handles/pointers need SetWindowLongPtr).
        User32.INSTANCE.SetWindowLong(hwnd, WinUser.GWL_STYLE, style);
        int error = Native.getLastError();
        if (error == ERROR_ACCESS_DENIED) {
            throw new WindowManagerException(
                    "Access denied. That window belongs to a process running as Administrator - "
                            + "restart AutoMacro Pro as Administrator to modify it.");
        }
        User32.INSTANCE.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, exStyle);

        // SWP_FRAMECHANGED is what forces Windows to recompute the non-client
        // area; without it the old frame stays painted until the next resize.
        boolean ok = User32.INSTANCE.SetWindowPos(hwnd, null,
                rect.left, rect.top,
                rect.right - rect.left, rect.bottom - rect.top,
                SWP_NOZORDER | SWP_NOOWNERZORDER | SWP_FRAMECHANGED | SWP_SHOWWINDOW);
        if (!ok) {
            throw new WindowManagerException("Windows rejected the resize (SetWindowPos failed).");
        }
    }

    private static WinDef.RECT monitorBounds(WinDef.HWND hwnd) {
        WinUser.HMONITOR monitor = User32.INSTANCE.MonitorFromWindow(hwnd, WinUser.MONITOR_DEFAULTTONEAREST);
        WinUser.MONITORINFO info = new WinUser.MONITORINFO();
        User32.INSTANCE.GetMonitorInfo(monitor, info);
        return info.rcMonitor; // full monitor, taskbar included - that is the point
    }

    private static void requireAlive(WinDef.HWND hwnd) throws WindowManagerException {
        if (!User32.INSTANCE.IsWindow(hwnd)) {
            throw new WindowManagerException("That window no longer exists. Click Refresh.");
        }
    }

    /**
     * DWM cloaking is how modern Windows hides UWP windows that technically
     * still exist. Wrapped defensively: an unavailable dwmapi is a reason to
     * show a slightly noisier list, never to fail the whole refresh.
     */
    private static boolean isCloaked(WinDef.HWND hwnd) {
        try {
            IntByReference cloaked = new IntByReference();
            Dwmapi.INSTANCE.DwmGetWindowAttribute(hwnd, DWMWA_CLOAKED, cloaked, 4);
            return cloaked.getValue() != 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int currentProcessId() {
        return (int) ProcessHandle.current().pid();
    }

    /** True when the Win32 layer is usable at all (i.e. we are on Windows with JNA loaded). */
    public static boolean isSupported() {
        try {
            return System.getProperty("os.name", "").toLowerCase().contains("win")
                    && User32.INSTANCE != null;
        } catch (Throwable ex) {
            AppLogger.error("JNA/User32 unavailable - Window Manager tab disabled", ex);
            return false;
        }
    }
}
