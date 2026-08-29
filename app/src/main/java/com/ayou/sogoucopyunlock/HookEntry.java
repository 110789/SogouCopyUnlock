package com.ayou.sogoucopyunlock;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookEntry implements IXposedHookLoadPackage {

    private static final String TARGET_PACKAGE = "com.sohu.inputmethod.sogouoem";
    private static final int LIMIT = 5000;
    private static final String TOAST_TEXT = "哎呀，复制的内容超过字数限制啦~";
    private static final boolean DEBUG = true;
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
                        if (!isCalledFromClipboardGuard()) {
                            return;
                        }
                        log("bypassed copy-length truncation, original length=" + original.length());
                        param.setResult(original);
                    }
                });

        try {
            XposedHelpers.findAndHookMethod(
                    "com.sogou.base.popuplayer.toast.SToast",
                    lpparam.classLoader,
                    "show",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object toastParam;
                            try {
                                toastParam = XposedHelpers.getObjectField(param.thisObject, "mToastParameter");
                            } catch (Throwable t) {
                                log("getObjectField mToastParameter failed: " + t);
                                return;
                            }
                            if (toastParam == null) {
                                return;
                            }
                            Object textObj;
                            try {
                                textObj = XposedHelpers.getObjectField(toastParam, "b");
                            } catch (Throwable t) {
                                log("getObjectField b failed: " + t);
                                return;
                            }
                            if (textObj instanceof CharSequence) {
                                log("SToast.show() text=" + textObj.toString());
                            }
                            if (textObj instanceof CharSequence && TOAST_TEXT.contentEquals((CharSequence) textObj)) {
                                log("suppressed copy-limit SToast, no view created");
                                param.setResult(null);
                            }
                        }
                    });
            log("SToast hook installed");
        } catch (Throwable t) {
            log("SToast hook failed: " + t);
        }
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

    private static void log(String msg) {
        if (DEBUG) {
            XposedBridge.log(TAG + " " + msg);
        }
    }
}
