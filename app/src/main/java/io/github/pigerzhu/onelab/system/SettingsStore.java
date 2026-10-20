package io.github.pigerzhu.onelab.system;

import io.github.pigerzhu.onelab.R;

import android.Manifest;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class SettingsStore {

    private static final class DispatcherHolder {
        private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
        private static final SettingsWriteDispatcher INSTANCE = new SettingsWriteDispatcher(
                Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "onelab-settings-writer");
                    thread.setDaemon(true);
                    return thread;
                }),
                runnable -> MAIN_HANDLER.post(runnable));
    }

    private final Context context;

    public SettingsStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public String getGlobal(String key, String defValue) {
        String value = Settings.Global.getString(context.getContentResolver(), key);
        return value == null ? defValue : value;
    }

    public int getGlobalInt(String key, int defValue) {
        try {
            return Integer.parseInt(getGlobal(key, String.valueOf(defValue)));
        } catch (NumberFormatException ignored) {
            return defValue;
        }
    }

    public boolean setGlobal(String key, String value) {
        boolean saved = putGlobalDirect(key, value) || putWithRoot("global", key, value);
        showSaveFeedback(saved);
        return saved;
    }

    public void setGlobalAsync(String key, String value, Consumer<Boolean> completion) {
        DispatcherHolder.INSTANCE.dispatch(() -> putGlobalQuietly(key, value), saved -> {
            showSaveFeedback(saved);
            completion.accept(saved);
        });
    }

    public void setGlobalAsync(String key, String value) {
        setGlobalAsync(key, value, saved -> { });
    }

    public boolean putGlobalQuietly(String key, String value) {
        return putGlobalDirect(key, value) || putWithRoot("global", key, value);
    }

    public void putGlobalQuietlyAsync(String key, String value, Consumer<Boolean> completion) {
        DispatcherHolder.INSTANCE.dispatch(() -> putGlobalQuietly(key, value), completion);
    }

    public void putGlobalQuietlyAsync(String key, String value) {
        putGlobalQuietlyAsync(key, value, saved -> { });
    }

    public boolean putGlobalsQuietly(Map<String, String> values) {
        boolean direct = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!putGlobalDirect(entry.getKey(), entry.getValue())) {
                direct = false;
                break;
            }
        }
        if (direct) {
            return true;
        }
        if (!Shell.runSu(globalWriteCommand(context.getPackageName(), values))) {
            return false;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!valueEquals(entry.getValue(), Settings.Global.getString(
                    context.getContentResolver(), entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    public void putGlobalsQuietlyAsync(
            Map<String, String> values, Consumer<Boolean> completion) {
        DispatcherHolder.INSTANCE.dispatch(() -> putGlobalsQuietly(values), completion);
    }

    public void putGlobalsQuietlyAsync(Map<String, String> values) {
        putGlobalsQuietlyAsync(values, saved -> { });
    }

    public String getSystem(String key, String defValue) {
        String value;
        try {
            value = Settings.System.getString(context.getContentResolver(), key);
        } catch (SecurityException e) {
            value = runSuForOutput("settings get system " + key);
        }
        return value == null ? defValue : value;
    }

    public boolean putSystemQuietly(String key, String value) {
        return putSystemDirect(key, value) || putWithRoot("system", key, value);
    }

    public void putSystemQuietlyAsync(String key, String value, Consumer<Boolean> completion) {
        DispatcherHolder.INSTANCE.dispatch(() -> putSystemQuietly(key, value), completion);
    }

    public void putSystemQuietlyAsync(String key, String value) {
        putSystemQuietlyAsync(key, value, saved -> { });
    }

    public String getSecure(String key, String defValue) {
        String value = Settings.Secure.getString(context.getContentResolver(), key);
        return value == null ? defValue : value;
    }

    public int getSecureInt(String key, int defValue) {
        try {
            return Integer.parseInt(getSecure(key, String.valueOf(defValue)));
        } catch (NumberFormatException ignored) {
            return defValue;
        }
    }

    public boolean setSecure(String key, String value) {
        return putSecureDirect(key, value) || putWithRoot("secure", key, value);
    }

    public void setSecureAsync(String key, String value, Consumer<Boolean> completion) {
        DispatcherHolder.INSTANCE.dispatch(() -> setSecure(key, value), completion);
    }

    public void setSecureAsync(String key, String value) {
        setSecureAsync(key, value, saved -> { });
    }

    public boolean setSecureWithToast(String key, String value) {
        boolean saved = putSecureDirect(key, value) || putWithRoot("secure", key, value);
        showSaveFeedback(saved);
        return saved;
    }

    public void setSecureWithToastAsync(
            String key, String value, Consumer<Boolean> completion) {
        DispatcherHolder.INSTANCE.dispatch(() -> setSecure(key, value), saved -> {
            showSaveFeedback(saved);
            completion.accept(saved);
        });
    }

    public void setSecureWithToastAsync(String key, String value) {
        setSecureWithToastAsync(key, value, saved -> { });
    }

    private void showSaveFeedback(boolean saved) {
        int message = SettingFeedbackPolicy.messageFor(
                saved,
                SettingFeedbackPolicy.SuccessNotice.NONE,
                R.string.toast_save_failed_permission);
        if (message != 0) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        }
    }

    public boolean runSu(String command) {
        return Shell.runSu(command);
    }

    public String runSuForOutput(String command) {
        return Shell.runSuForOutput(command);
    }

    private boolean putGlobalDirect(String key, String value) {
        try {
            return Settings.Global.putString(context.getContentResolver(), key, value)
                    && valueEquals(value, Settings.Global.getString(
                    context.getContentResolver(), key));
        } catch (SecurityException ignored) {
            return false;
        }
    }

    private boolean putSecureDirect(String key, String value) {
        try {
            return Settings.Secure.putString(context.getContentResolver(), key, value)
                    && valueEquals(value, Settings.Secure.getString(
                    context.getContentResolver(), key));
        } catch (SecurityException ignored) {
            return false;
        }
    }

    private boolean putSystemDirect(String key, String value) {
        try {
            return Settings.System.putString(context.getContentResolver(), key, value)
                    && valueEquals(value, Settings.System.getString(
                    context.getContentResolver(), key));
        } catch (SecurityException ignored) {
            return false;
        }
    }

    private boolean putWithRoot(String namespace, String key, String value) {
        String command = value == null
                ? "settings delete " + namespace + " " + shellQuote(key)
                : "settings put " + namespace + " " + shellQuote(key)
                + " " + shellQuote(value);
        if (!Shell.runSu(command)) {
            return false;
        }
        String actual = value == null
                ? runSuForOutput("settings get " + namespace + " " + shellQuote(key))
                : namespaceValue(namespace, key);
        return valueEquals(value, actual);
    }

    private String namespaceValue(String namespace, String key) {
        if ("global".equals(namespace)) {
            return Settings.Global.getString(context.getContentResolver(), key);
        }
        if ("secure".equals(namespace)) {
            return Settings.Secure.getString(context.getContentResolver(), key);
        }
        if ("system".equals(namespace)) {
            return getSystem(key, null);
        }
        return runSuForOutput("settings get " + namespace + " " + shellQuote(key));
    }

    private static boolean valueEquals(String expected, String actual) {
        return expected == null ? actual == null : expected.equals(actual);
    }

    static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    static String globalWriteCommand(String packageName, Map<String, String> values) {
        StringBuilder command = new StringBuilder("(pm grant ")
                .append(shellQuote(packageName))
                .append(' ')
                .append(shellQuote(Manifest.permission.WRITE_SECURE_SETTINGS))
                .append(" >/dev/null 2>&1 || true)");
        for (Map.Entry<String, String> entry : values.entrySet()) {
            command.append(" && settings put global ")
                    .append(shellQuote(entry.getKey()))
                    .append(' ')
                    .append(shellQuote(entry.getValue()));
        }
        return command.toString();
    }
}
