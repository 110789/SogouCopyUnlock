package com.ayou.sogoucopyunlock;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookEntry implements IXposedHookLoadPackage {

    private static final String TARGET_PACKAGE = "com.sohu.inputmethod.sogou";
    private static final int LIMIT = 5000;
    private static final String TAG = "[SogouCopyUnlock]";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        XposedHelpers.findAndHookMethod(String.class, "substring",
                int.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        int begin = (int) param.args[0];
                        int end = (int) param.args[1];
                        if (begin != 0 || end != LIMIT) {
                            return;
                        }
                        String original = (String) param.thisObject;
                        if (original.length() <= LIMIT) {
                            return;
                        }
                        boolean fromClipboardGuard = isCalledFromClipboardGuard();
                        if (!fromClipboardGuard) {
                            return;
                        }
                        XposedBridge.log(TAG + " bypassed copy-length truncation, original length=" + original.length());
                        param.setResult(original);
                    }
                });
    }

    private static boolean isCalledFromClipboardGuard() {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        for (StackTraceElement e : stack) {
            if (e.getClassName().startsWith("com.sohu.inputmethod.clipboard")) {
                return true;
            }
        }
        return false;
    }
}
