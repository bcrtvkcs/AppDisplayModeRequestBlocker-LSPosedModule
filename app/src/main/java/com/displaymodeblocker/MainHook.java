package com.displaymodeblocker;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import java.lang.reflect.Method;

/**
 * Ignores the display mode id that apps request through
 * {@code WindowManager.LayoutParams#preferredDisplayModeId}.
 *
 * <p>When an app asks for a specific mode, {@code DisplayModeDirector} turns
 * that request into a {@code PRIORITY_APP_REQUEST_SIZE} vote. That vote beats the
 * user's preferred resolution, so an app can silently switch the panel to a
 * lower-resolution mode (for example 1440x3216 to 1080x2412). Passing mode id 0
 * to {@code AppRequestObserver#setAppRequest} means "no mode requested", so the
 * size vote is never created.
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "DisplayModeRequestBlocker";

    // The system framework is loaded under this package name.
    private static final String SYSTEM_SERVER_PACKAGE = "android";

    private static final String TARGET_METHOD = "setAppRequest";

    // The observer moved between Android releases, so try every known location.
    private static final String[] OBSERVER_CLASS_CANDIDATES = {
            "com.android.server.display.mode.DisplayModeDirector$AppRequestObserver",
            "com.android.server.display.mode.AppRequestObserver",
            "com.android.server.display.DisplayModeDirector$AppRequestObserver",
    };

    private static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
    }

    // setAppRequest(int displayId, int modeId, ...) - the remaining parameters
    // differ between releases, but displayId and modeId always come first.
    private static final XC_MethodHook ZERO_MODE_ID = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            int modeId = (Integer) param.args[1];
            if (modeId != 0) {
                log("Ignoring display mode request: displayId=" + param.args[0]
                        + " modeId=" + modeId);
                param.args[1] = 0;
            }
        }
    };

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        if (!SYSTEM_SERVER_PACKAGE.equals(lpparam.packageName)) {
            return;
        }
        try {
            hookAppRequestObserver(lpparam.classLoader);
        } catch (Throwable t) {
            log("Failed to install hook: " + t);
        }
    }

    private static void hookAppRequestObserver(ClassLoader classLoader) {
        Class<?> observer = null;
        for (String name : OBSERVER_CLASS_CANDIDATES) {
            observer = XposedHelpers.findClassIfExists(name, classLoader);
            if (observer != null) {
                log("Found " + name);
                break;
            }
        }
        if (observer == null) {
            log("AppRequestObserver class not found, nothing hooked");
            return;
        }

        int hooked = 0;
        for (Method method : observer.getDeclaredMethods()) {
            if (!TARGET_METHOD.equals(method.getName())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length < 2 || params[0] != int.class || params[1] != int.class) {
                continue;
            }
            XposedBridge.hookMethod(method, ZERO_MODE_ID);
            hooked++;
            log("Hooked " + method);
        }
        if (hooked == 0) {
            log("No matching " + TARGET_METHOD + " overload found, nothing hooked");
        }
    }
}
