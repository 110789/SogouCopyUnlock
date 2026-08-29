package com.ayou.sogoucopyunlock;

import java.util.HashMap;

import android.text.Spanned;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookEntry implements IXposedHookLoadPackage {

    private static final String TARGET_PACKAGE = "com.sohu.inputmethod.sogouoem";
    private static final int LIMIT = 5000;
    private static final String TOAST_TEXT = "哎呀，复制的内容超过字数限制啦~";
    private static final boolean DEBUG = false;
    private static final String TAG = "[SogouCopyUnlock]";

    private static volatile HashMap<?, ?> sToolbarMap;

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
                                return;
                            }
                            if (toastParam == null) {
                                return;
                            }
                            Object textObj;
                            try {
                                textObj = XposedHelpers.getObjectField(toastParam, "b");
                            } catch (Throwable t) {
                                return;
                            }
                            if (textObj instanceof CharSequence && TOAST_TEXT.contentEquals((CharSequence) textObj)) {
                                log("suppressed copy-limit SToast, no view created");
                                param.setResult(null);
                            }
                        }
                    });
        } catch (Throwable t) {
            log("SToast hook failed: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "g20",
                    lpparam.classLoader,
                    "a",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                Object map = XposedHelpers.getObjectField(param.thisObject, "a");
                                if (map instanceof HashMap) {
                                    sToolbarMap = (HashMap<?, ?>) map;
                                }
                            } catch (Throwable t) {
                                log("capture toolbar map failed: " + t);
                            }
                        }
                    });
        } catch (Throwable t) {
            log("g20.a hook failed: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(HashMap.class, "size", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.thisObject == sToolbarMap) {
                        log("faked toolbar HashMap.size() -> 0");
                        param.setResult(0);
                    }
                }
            });
        } catch (Throwable t) {
            log("HashMap.size hook failed: " + t);
        }

        try {
            Class<?> clazz = XposedHelpers.findClass(
                    "com.sogou.inputmethod.oem.oppo.dialog.ShortcutPhrasesDialogTransActivity",
                    lpparam.classLoader);
            XposedBridge.hookAllMethods(clazz, "g0", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length == 6 && param.args[3] instanceof Spanned) {
                        log("bypassed shortcut-phrase 300-char filter");
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable t) {
            log("shortcut phrase filter hook failed: " + t);
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
