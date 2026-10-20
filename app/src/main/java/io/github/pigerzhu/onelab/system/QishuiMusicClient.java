package io.github.pigerzhu.onelab.system;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class QishuiMusicClient {
    public static final String PACKAGE_NAME = "com.luna.music";
    public static final String PREFERENCES_NAME = "qishui_music";
    public static final String PREFERENCE_ENABLED = "enabled";
    private static final String ENABLE = "{\"support_feature\":{\"recognition_strategy\":\"support_feature\",\"recognize_flag\":4,\"recognition_reason\":\"is_pad_storage_said_yes\",\"recognize_duration\":1}}";
    private static final String DISABLE = "{\"support_feature\":{\"recognition_strategy\":\"support_feature\",\"recognize_flag\":0,\"recognition_reason\":\"not_pad_storage_said_no\",\"recognize_duration\":1}}";
    private static final String PREF_ORIGINAL = "original_record";
    private final Context context;
    public QishuiMusicClient(Context context) { this.context = context.getApplicationContext(); }
    public static String enableRecord() { return ENABLE; }
    public static String disableRecord() { return DISABLE; }
    public static boolean isPadRecord(String json) { return json != null && json.contains("\"recognize_flag\":4"); }
    static String normalizeRecord(String output) {
        if (output == null) return null;
        String value = output.trim();
        return value.startsWith("{") && value.endsWith("}") ? value : null;
    }
    public boolean isEnabled() { return context.getSharedPreferences(PREFERENCES_NAME, 0).getBoolean(PREFERENCE_ENABLED, false); }
    public boolean isInstalled() { return context.getPackageManager().getLaunchIntentForPackage(PACKAGE_NAME) != null; }
    public boolean setEnabled(boolean enabled) {
        if (!isInstalled() || !copyAsset()) return false;
        Shell.runSu("am force-stop " + PACKAGE_NAME);
        String apk = Shell.runSuForOutput("pm path " + PACKAGE_NAME + " | sed -n 's/^package://p' | head -n 1");
        if (apk == null) return false;
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_NAME, 0);
        String json = enabled ? ENABLE : DISABLE;
        String qApk = quote(apk);
        String libs = "/data/local/tmp/onelab-qishui-libs";
        String dex = "/data/local/tmp/onelab-qishui-keva.dex";
        String prefix = "mkdir -p " + libs + " && unzip -p " + qApk + " 'lib/arm64-v8a/libkeva.so' > " + libs + "/libkeva.so && unzip -p " + qApk + " 'lib/arm64-v8a/libc++_shared.so' > " + libs + "/libc++_shared.so && LD_LIBRARY_PATH=" + libs + " CLASSPATH=" + quote(apk + ":" + dex);
        if (enabled && !prefs.contains(PREF_ORIGINAL)) {
            String recordOutput = Shell.runSuInMasterMountForOutput(
                    prefix + " app_process / QishuiKevaTool read-record");
            String original = normalizeRecord(recordOutput);
            if (original == null) return false;
            boolean saved = prefs.edit().putString(PREF_ORIGINAL, original).commit();
            if (!saved) return false;
        }
        boolean wrote = Shell.runSuInMasterMount(
                prefix + " app_process / QishuiKevaTool write-record " + quote(json));
        if (!wrote) return false;
        String confirmed = normalizeRecord(Shell.runSuInMasterMountForOutput(
                prefix + " app_process / QishuiKevaTool read-record"));
        boolean success = enabled ? isPadRecord(confirmed) : !isPadRecord(confirmed);
        if (success) success = prefs.edit().putBoolean(PREFERENCE_ENABLED, enabled).commit();
        return success;
    }
    private boolean copyAsset() {
        try (InputStream in = context.getAssets().open("qishui_keva_tool.dex"); FileOutputStream out = new FileOutputStream(new File(context.getCacheDir(), "qishui.dex"))) {
            byte[] b = new byte[8192]; int n; while ((n = in.read(b)) >= 0) out.write(b, 0, n);
            return Shell.runSu("cp " + quote(new File(context.getCacheDir(), "qishui.dex").getAbsolutePath()) + " /data/local/tmp/onelab-qishui-keva.dex");
        } catch (Exception ignored) { return false; }
    }
    private static String quote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }
}
