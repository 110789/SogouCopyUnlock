package com.ayou.sogoucopyunlock;

import java.util.HashMap;
import java.util.List;

import android.text.Spanned;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookEntry implements IXposedHookLoadPackage {

    private static final String TARGET_PACKAGE = "com.sohu.inputmethod.sogouoem";
    private static final int LIMIT = 5000;
    private static final int PHRASE_LIMIT = 300;
    private static final String TOAST_TEXT = "哎呀，复制的内容超过字数限制啦~";
    private static final String TAG = "[SogouCopyUnlock]";

    private static boolean sDebug;
    private static boolean sFeatureCopyLimit;
    private static boolean sFeatureToolbar;
    private static boolean sFeaturePhraseLength;
    private static boolean sFeatureClipboardMove;
    private static boolean sFeatureClipboardHistory;

    private static volatile HashMap<?, ?> sToolbarMap;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.Instrumentation",
                    lpparam.classLoader,
                    "callApplicationOnCreate",
                    android.app.Application.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            android.app.Application app = (android.app.Application) param.args[0];
                            loadPrefs(app.getApplicationContext());
                            installHooks(lpparam);
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + " failed to hook Application startup: " + t);
        }
    }

    private static void installHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        if (sFeatureCopyLimit) {
            hookCopyLimit();
            hookCopyLimitToast(lpparam);
        }
        if (sFeatureToolbar) {
            hookToolbarLimit(lpparam);
        }
        if (sFeaturePhraseLength) {
            hookPhraseLengthFilter(lpparam);
            hookSystemLengthFilter(lpparam);
        }
        if (sFeatureClipboardMove) {
            hookClipboardMoveToPhrase(lpparam);
        }
        if (sFeatureClipboardHistory) {
            hookClipboardHistoryEviction(lpparam);
        }
        if (sFeatureClipboardHistory) {
            hookClipboardCountLabel(lpparam);
        }
    }

    private static void loadPrefs(android.content.Context context) {
        sDebug = false;
        sFeatureCopyLimit = true;
        sFeatureToolbar = true;
        sFeaturePhraseLength = true;
        sFeatureClipboardMove = true;
        sFeatureClipboardHistory = true;
        try {
            android.net.Uri uri = android.net.Uri.parse("content://" + ConfigProvider.AUTHORITY);
            android.database.Cursor cursor = context.getContentResolver()
                    .query(uri, null, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        sDebug = cursor.getInt(cursor.getColumnIndexOrThrow(Settings.KEY_DEBUG)) == 1;
                        sFeatureCopyLimit = cursor.getInt(cursor.getColumnIndexOrThrow(Settings.KEY_COPY_LIMIT)) == 1;
                        sFeatureToolbar = cursor.getInt(cursor.getColumnIndexOrThrow(Settings.KEY_TOOLBAR)) == 1;
                        sFeaturePhraseLength = cursor.getInt(cursor.getColumnIndexOrThrow(Settings.KEY_PHRASE_LENGTH)) == 1;
                        sFeatureClipboardMove = cursor.getInt(cursor.getColumnIndexOrThrow(Settings.KEY_CLIPBOARD_MOVE)) == 1;
                        sFeatureClipboardHistory = cursor.getInt(cursor.getColumnIndexOrThrow(Settings.KEY_CLIPBOARD_HISTORY)) == 1;
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + " ConfigProvider query failed, using defaults: " + t);
        }
        XposedBridge.log(TAG + " loaded prefs: debug=" + sDebug
                + " copyLimit=" + sFeatureCopyLimit
                + " toolbar=" + sFeatureToolbar
                + " phraseLength=" + sFeaturePhraseLength
                + " clipboardMove=" + sFeatureClipboardMove
                + " clipboardHistory=" + sFeatureClipboardHistory);
    }

    private static void hookCopyLimit() {
        XposedHelpers.findAndHookMethod(String.class, "substring",
                int.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        int begin = (int) param.args[0];
                        int end = (int) param.args[1];
                        String original = (String) param.thisObject;
                        if (begin == 0 && end == LIMIT
                                && original.length() > LIMIT
                                && isCalledFromClipboardGuard()) {
                            log("bypassed copy-length truncation, original length=" + original.length());
                            param.setResult(original);
                            return;
                        }
                        if (begin == 0 && end == PHRASE_LIMIT
                                && original.length() > PHRASE_LIMIT
                                && sFeaturePhraseLength) {
                            log("bypassed shortcut-phrase move-in truncation, original length=" + original.length());
                            param.setResult(original);
                        }
                    }
                });
    }

    private static void hookCopyLimitToast(XC_LoadPackage.LoadPackageParam lpparam) {
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
    }

    private static void hookToolbarLimit(XC_LoadPackage.LoadPackageParam lpparam) {
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
    }

    private static void hookSystemLengthFilter(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.text.InputFilter$LengthFilter",
                    lpparam.classLoader,
                    "filter",
                    CharSequence.class, int.class, int.class,
                    Spanned.class, int.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (isCalledFromShortcutPhrases()) {
                                log("bypassed system LengthFilter for shortcut phrases");
                                param.setResult(null);
                            }
                        }
                    });
        } catch (Throwable t) {
            log("LengthFilter hook failed: " + t);
        }
    }

    private static void hookPhraseLengthFilter(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> clazz = XposedHelpers.findClass(
                    "com.sogou.inputmethod.oem.oppo.dialog.ShortcutPhrasesDialogTransActivity",
                    lpparam.classLoader);
            XposedBridge.hookAllMethods(clazz, "g0", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length == 7 && param.args[4] instanceof Spanned) {
                        log("bypassed shortcut-phrase 300-char filter");
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable t) {
            log("shortcut phrase filter hook failed: " + t);
        }
    }

    private static void hookClipboardMoveToPhrase(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.sohu.inputmethod.clipboard.ClipboardKeyboard",
                    lpparam.classLoader,
                    "v", int.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            Object thiz = param.thisObject;
                            int index = (int) param.args[0];
                            Object list = XposedHelpers.getObjectField(thiz, "q");
                            Object item = ((List<?>) list).get(index);
                            String text = (String) XposedHelpers.getObjectField(item, "d");
                            XposedHelpers.callMethod(thiz, "I", (Object) text);
                            XposedHelpers.callMethod(thiz, "Z");
                            log("bypassed clipboard move-to-phrase length rejection, length=" + text.length());
                            return null;
                        }
                    });
        } catch (Throwable t) {
            log("clipboard move-to-phrase hook failed: " + t);
        }
    }

    private static void hookClipboardCountLabel(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(String.class, "format",
                    String.class, Object[].class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String fmt = (String) param.args[0];
                            if (!"\uFF08%d/%d\uFF09".equals(fmt)) {
                                return;
                            }
                            Object[] args = (Object[]) param.args[1];
                            if (args == null || args.length != 2
                                    || !(args[1] instanceof Integer)
                                    || (Integer) args[1] != 300) {
                                return;
                            }
                            if (!isCalledFromClipboardCandidateView()) {
                                return;
                            }
                            log("replaced clipboard count label with unlimited text");
                            param.setResult("\uFF08" + args[0] + "/\u65E0\u9650\u5236\uFF09");
                        }
                    });
        } catch (Throwable t) {
            log("clipboard count label hook failed: " + t);
        }
    }

    private static boolean isCalledFromClipboardCandidateView() {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        for (StackTraceElement e : stack) {
            if (e.getClassName().equals("com.sohu.inputmethod.clipboard.ClipboardCandidateView")) {
                return true;
            }
        }
        return false;
    }

    private static void hookClipboardHistoryEviction(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "org.greenrobot.greendao.AbstractDao",
                    lpparam.classLoader,
                    "delete", Object.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            StackTraceElement[] stack = new Throwable().getStackTrace();
                            for (StackTraceElement e : stack) {
                                if (e.getClassName().equals("com.sohu.inputmethod.clipboard.z")
                                        && e.getMethodName().equals("B")) {
                                    log("blocked clipboard history eviction (300-item cap)");
                                    param.setResult(null);
                                    return;
                                }
                            }
                        }
                    });
        } catch (Throwable t) {
            log("clipboard history eviction hook failed: " + t);
        }
    }

    private static boolean isCalledFromShortcutPhrases() {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        for (StackTraceElement e : stack) {
            if (e.getClassName().contains("oem.oppo.dialog")
                    || e.getClassName().contains("ShortcutPhrase")) {
                return true;
            }
        }
        return false;
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
        if (sDebug) {
            XposedBridge.log(TAG + " " + msg);
        }
    }
}
