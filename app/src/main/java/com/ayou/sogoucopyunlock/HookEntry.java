package com.ayou.sogoucopyunlock;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

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

        XposedHelpers.findAndHookMethod(Toast.class, "show", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Toast toast = (Toast) param.thisObject;
                View view = toast.getView();
                if (view == null) {
                    return;
                }
                String text = findText(view);
                if (TOAST_TEXT.equals(text)) {
                    log("suppressed copy-limit toast");
                    param.setResult(null);
                }
            }
        });
    }

    private static String findText(View view) {
        if (view instanceof TextView) {
            CharSequence t = ((TextView) view).getText();
            return t == null ? null : t.toString();
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                String found = findText(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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
