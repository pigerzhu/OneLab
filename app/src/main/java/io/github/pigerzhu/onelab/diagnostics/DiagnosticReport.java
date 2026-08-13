package io.github.pigerzhu.onelab.diagnostics;

import io.github.pigerzhu.onelab.R;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.provider.MediaStore;
import android.util.DisplayMetrics;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.json.JSONObject;

import io.github.pigerzhu.onelab.BuildConfig;
import io.github.pigerzhu.onelab.contract.SettingsKeys;
import io.github.pigerzhu.onelab.contract.SplitViewRatioOverrides;
import io.github.pigerzhu.onelab.system.SdhmsClient;
import io.github.pigerzhu.onelab.system.Shell;

/** Builds a privacy-filtered support bundle on explicit user request. */
public final class DiagnosticReport {
    private static final String PREFS = "onelab_diagnostics";
    private static final String KEY_SESSION_STARTED_AT = "session_started_at";
    private static final String KEY_SESSION_STOPPED_AT = "session_stopped_at";
    private static final String KEY_LATEST_REPORT = "latest_report";
    private static final String REPORT_PREFIX = "OneLab-diagnostic-";
    private static final String REPORT_RELATIVE_PATH =
            Environment.DIRECTORY_DOWNLOADS + "/OneLab/";
    private static final int MAX_LOG_LINES = 4_000;
    private static final int MAX_REPORT_LOG_LINES = 1_200;
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)(token|cookie|authorization|android_id|serial|imei|imsi|bssid|ssid)"
                    + "\\s*[:=]\\s*[^\\s,;]+");
    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)(?:\\+?86[- ]?)?1[3-9]\\d{9}(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");

    private DiagnosticReport() {
    }

    public static long startSession(Context context) {
        long now = System.currentTimeMillis();
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_SESSION_STARTED_AT, now)
                .remove(KEY_SESSION_STOPPED_AT)
                .apply();
        return now;
    }

    public static long stopSession(Context context) {
        if (sessionStartedAt(context) == 0L) return 0L;
        long now = System.currentTimeMillis();
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_SESSION_STOPPED_AT, now)
                .apply();
        return now;
    }

    public static long sessionStartedAt(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_SESSION_STARTED_AT, 0L);
    }

    public static long sessionStoppedAt(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_SESSION_STOPPED_AT, 0L);
    }

    public static boolean isRecording(Context context) {
        return sessionStartedAt(context) > 0L && sessionStoppedAt(context) == 0L;
    }

    public static boolean hasCompletedSession(Context context) {
        long startedAt = sessionStartedAt(context);
        return startedAt > 0L && sessionStoppedAt(context) >= startedAt;
    }

    public static String latestReportName(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LATEST_REPORT, null);
    }

    public static PublishedReport generate(Context context) throws IOException {
        if (!hasCompletedSession(context)) {
            throw new IOException("no completed recording session");
        }
        RuntimeCompatibilityReport.Result compatibility =
                RuntimeCompatibilityReport.collect(context);
        File directory = reportDirectory(context);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("cannot create the diagnostics directory");
        }
        String timestamp = new SimpleDateFormat(
                "yyyyMMdd-HHmmss", Locale.US).format(new Date());
        String fileName = REPORT_PREFIX + timestamp + ".zip";
        File output = new File(directory, fileName);
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(
                new FileOutputStream(output)))) {
            put(zip, "summary.txt", buildSummary(context));
            put(zip, "device.txt", buildDevice(context));
            put(zip, "features.txt", buildFeatures(context));
            put(zip, "gpu-frequency.txt", buildGpuFrequency(context));
            put(zip, "packages.txt", buildPackages(context));
            put(zip, "split-view.txt", redact(buildSplitView(context)));
            put(zip, "runtime-state.txt", redact(buildRuntimeState(context)));
            put(zip, "compatibility.txt", redact(compatibility.compatibility));
            put(zip, "hook-runtime.txt", redact(compatibility.hookLog));
            put(zip, "logcat.txt", buildFilteredLogcat(context));
            put(zip, "privacy.txt",
                    "This report only contains OneLab feature states, versions of the "
                            + "related apps, device compatibility information, the filtered "
                            + "reproduction log and the OneLab persistent hook log.\n"
                            + "It does not collect accounts, network names, location, the "
                            + "full app list, screenshots or app data.\n");
        }
        Uri uri;
        try {
            uri = publishToDownloads(context, output, fileName);
        } finally {
            output.delete();
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_SESSION_STARTED_AT)
                .remove(KEY_SESSION_STOPPED_AT)
                .putString(KEY_LATEST_REPORT, fileName)
                .apply();
        pruneTemporaryReports(directory);
        return new PublishedReport(uri, fileName,
                context.getString(R.string.diagnostics_download_path, fileName));
    }

    public static void clear(Context context) {
        pruneTemporaryReports(reportDirectory(context));
        deletePublishedReports(context);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_SESSION_STARTED_AT)
                .remove(KEY_SESSION_STOPPED_AT)
                .remove(KEY_LATEST_REPORT)
                .apply();
    }

    private static String buildSummary(Context context) {
        long startedAt = sessionStartedAt(context);
        long stoppedAt = sessionStoppedAt(context);
        return "report_format=4\n"
                + "generated_at=" + isoTime(System.currentTimeMillis()) + "\n"
                + "recording_started_at="
                + (startedAt == 0 ? "not_started" : isoTime(startedAt)) + "\n"
                + "recording_stopped_at="
                + (stoppedAt == 0 ? "not_stopped" : isoTime(stoppedAt)) + "\n"
                + "app_version=" + BuildConfig.VERSION_NAME + "\n"
                + "app_version_code=" + BuildConfig.VERSION_CODE + "\n"
                + "git_commit=" + BuildConfig.GIT_COMMIT + "\n"
                + "instructions=describe the reproduction steps, the expected result and the "
                + "actual result in the GitHub issue.\n";
    }

    private static String buildDevice(Context context) {
        Configuration config = context.getResources().getConfiguration();
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        String fingerprint = Build.FINGERPRINT == null ? "" : Build.FINGERPRINT;
        int slash = fingerprint.indexOf('/');
        String safeBuild = slash >= 0 ? fingerprint.substring(0, slash) : fingerprint;
        return "manufacturer=" + Build.MANUFACTURER + "\n"
                + "model=" + Build.MODEL + "\n"
                + "device=" + Build.DEVICE + "\n"
                + "android_sdk=" + Build.VERSION.SDK_INT + "\n"
                + "android_release=" + Build.VERSION.RELEASE + "\n"
                + "security_patch=" + Build.VERSION.SECURITY_PATCH + "\n"
                + "build_display=" + safeValue(Build.DISPLAY) + "\n"
                + "build_family=" + safeBuild + "\n"
                + "screen_width_dp=" + config.screenWidthDp + "\n"
                + "screen_height_dp=" + config.screenHeightDp + "\n"
                + "smallest_width_dp=" + config.smallestScreenWidthDp + "\n"
                + "density_dpi=" + metrics.densityDpi + "\n"
                + "orientation=" + config.orientation + "\n"
                + "ui_mode=" + config.uiMode + "\n";
    }

    private static String buildFeatures(Context context) {
        StringBuilder output = new StringBuilder();
        for (DiagnosticCatalog.Feature feature : DiagnosticCatalog.FEATURES) {
            String raw = Settings.Global.getString(
                    context.getContentResolver(), feature.settingKey);
            output.append("feature=").append(feature.id)
                    .append(" | label=").append(feature.label)
                    .append(" | enabled=").append("1".equals(raw))
                    .append(" | raw=").append(raw == null ? "unset" : safeValue(raw));
            if (feature.packageName != null) {
                output.append(" | package=").append(feature.packageName)
                        .append(" | installed=")
                        .append(packageVersion(context, feature.packageName) != null);
            }
            output.append('\n');
        }
        for (DiagnosticCatalog.Value value : DiagnosticCatalog.VALUES) {
            String raw = Settings.Global.getString(
                    context.getContentResolver(), value.settingKey);
            output.append("value=").append(value.id)
                    .append(" | raw=").append(summarizeValue(raw))
                    .append('\n');
        }
        appendExtraFeatureState(context, output);
        return output.toString();
    }

    private static String buildGpuFrequency(Context context) {
        int bootCount = Settings.Global.getInt(
                context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        String snapshot = Settings.Global.getString(
                context.getContentResolver(), SettingsKeys.KEY_GPU_SUPPORTED_FREQUENCIES);
        return DiagnosticGpuSnapshot.describe(snapshot, bootCount);
    }

    private static void appendExtraFeatureState(Context context, StringBuilder output) {
        String enhancedProcessing = Settings.Global.getString(
                context.getContentResolver(), "enhanced_processing");
        String gameHeat = Settings.Secure.getString(
                context.getContentResolver(), "allow_more_heat_value");
        output.append("value=performance.enhanced_processing | raw=")
                .append(summarizeValue(enhancedProcessing)).append('\n');
        output.append("value=games.global_heat_budget | raw=")
                .append(summarizeValue(gameHeat)).append('\n');
        output.append("value=thermal.service_delta | raw=")
                .append(sdhmsValue(SdhmsClient.getThermalDelta())).append('\n');
        output.append("value=thermal.service_supported_delta | raw=")
                .append(sdhmsValue(SdhmsClient.getSupportedThermalDelta())).append('\n');
        output.append("value=thermal.service_control_flag | raw=")
                .append(sdhmsValue(SdhmsClient.getThermalControlFlag())).append('\n');

        String multiStar = Settings.Secure.getString(
                context.getContentResolver(), "multistar_setting_json_repository");
        output.append("feature=window.persist_freeform_bounds | enabled=")
                .append(readJsonBoolean(multiStar, "persistFreeformBounds"))
                .append('\n');

        SharedPreferences cover = context.getSharedPreferences(
                "onelab_cover", Context.MODE_PRIVATE);
        output.append("feature=fold.full_cover_display | enabled=")
                .append(cover.getBoolean("outer_system_enabled", false))
                .append('\n');
        output.append("value=fold.cover_content_mode | raw=")
                .append(cover.getInt("mode", 0)).append('\n');

        SharedPreferences coverEdge = context.getSharedPreferences(
                "onelab_cover_edge", Context.MODE_PRIVATE);
        output.append("feature=experiments.cover_edge_rejection | enabled=")
                .append(coverEdge.getBoolean("active", false))
                .append(" | width_percent=")
                .append(coverEdge.getFloat("width_percent", 2f))
                .append('\n');
    }

    private static boolean readJsonBoolean(String raw, String key) {
        if (raw == null || raw.trim().isEmpty()) return false;
        try {
            JSONObject settings = new JSONObject(raw).optJSONObject("settings");
            return settings != null && settings.optBoolean(key, false);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String sdhmsValue(int value) {
        return value == SdhmsClient.VALUE_UNAVAILABLE
                ? "unavailable"
                : String.valueOf(value);
    }

    private static String buildPackages(Context context) {
        Set<String> packages = new LinkedHashSet<>();
        for (DiagnosticCatalog.Feature feature : DiagnosticCatalog.FEATURES) {
            if (feature.packageName != null) packages.add(feature.packageName);
        }
        packages.add("com.google.android.captiveportallogin");
        packages.add("com.sec.android.gallery3d");
        packages.add("com.samsung.android.game.gos");
        packages.add("com.sec.android.sdhms");
        StringBuilder output = new StringBuilder();
        for (String packageName : packages) {
            String version = packageVersion(context, packageName);
            output.append(packageName).append('=')
                    .append(version == null ? "not_installed" : version).append('\n');
        }
        return output.toString();
    }

    private static String buildSplitView(Context context) {
        String rawSnapshot = Settings.Global.getString(
                context.getContentResolver(), SettingsKeys.KEY_SPLIT_VIEW_ALLOWED_PACKAGES);
        Set<String> allowed = splitPackages(rawSnapshot);
        Map<String, Float> ratios = SplitViewRatioOverrides.parse(Settings.Global.getString(
                context.getContentResolver(), SettingsKeys.KEY_SPLIT_VIEW_RATIO_OVERRIDES));
        StringBuilder output = new StringBuilder();
        output.append("snapshot_status=")
                .append(rawSnapshot == null ? "unset" : "available").append('\n');
        output.append("snapshot_package_count=").append(allowed.size()).append('\n');
        output.append("ratio_override_count=").append(ratios.size()).append('\n');
        for (String packageName : allowed) {
            output.append("snapshot_package=").append(packageName)
                    .append(" | installed=").append(isInstalled(context, packageName))
                    .append(" | enabled=").append(isEnabled(context, packageName))
                    .append(" | launchable=").append(isLaunchable(context, packageName))
                    .append(" | ratio=").append(formatRatio(ratios.get(packageName)))
                    .append('\n');
        }
        for (Map.Entry<String, Float> entry : ratios.entrySet()) {
            if (allowed.contains(entry.getKey())) continue;
            output.append("ratio_only_package=").append(entry.getKey())
                    .append(" | installed=").append(isInstalled(context, entry.getKey()))
                    .append(" | enabled=").append(isEnabled(context, entry.getKey()))
                    .append(" | launchable=").append(isLaunchable(context, entry.getKey()))
                    .append(" | ratio=").append(formatRatio(entry.getValue()))
                    .append(" | in_snapshot=false\n");
        }
        output.append("diagnostic_hint=list eligibility and ratio engine support are two "
                + "independent conditions; snapshot_package does not guarantee that the "
                + "ratio takes effect.\n");
        return output.toString();
    }

    private static String buildRuntimeState(Context context) {
        String passThrough = Settings.System.getString(
                context.getContentResolver(), "pass_through");
        StringBuilder command = new StringBuilder();
        command.append("printf 'oneui_version_raw='; getprop ro.build.version.oneui; ")
                .append("printf 'build_incremental='; getprop ro.build.version.incremental; ")
                .append("printf 'device_state='; cmd device_state print-state; ")
                .append("printf 'accelerometer_rotation='; settings get system accelerometer_rotation; ")
                .append("printf 'user_rotation='; settings get system user_rotation; ")
                .append("printf 'battery='; dumpsys battery | grep -E ")
                .append("'AC powered|USB powered|Wireless powered|status:|level:|temperature:'; ")
                .append("printf 'usb='; dumpsys usb | grep -E ")
                .append("'mCurrentFunctions|mConnected|mUsbDataUnlocked|speed|powerRole|dataRole'; ")
                .append("printf 'network_slot0='; cmd phone get-allowed-network-types-for-users -s 0; ")
                .append("printf 'network_slot1='; cmd phone get-allowed-network-types-for-users -s 1");
        String shellState = Shell.runSuForOutput(command.toString());
        StringBuilder output = new StringBuilder();
        output.append("pass_through_setting=")
                .append(passThrough == null ? "unset" : safeValue(passThrough)).append('\n');
        output.append("sdhms_service_status=")
                .append(safeValue(Shell.runSuForOutput("service check sdhms"))).append('\n');
        output.append("runtime_snapshot=\n")
                .append(shellState == null ? "unavailable\n" : shellState).append('\n');
        return output.toString();
    }

    private static Set<String> splitPackages(String raw) {
        Set<String> packages = new LinkedHashSet<>();
        if (raw == null) return packages;
        for (String item : raw.split(",")) {
            String packageName = item.trim();
            if (!packageName.isEmpty()) packages.add(packageName);
        }
        return packages;
    }

    private static boolean isInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private static boolean isEnabled(Context context, String packageName) {
        try {
            return context.getPackageManager().getApplicationInfo(packageName, 0).enabled;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private static boolean isLaunchable(Context context, String packageName) {
        return context.getPackageManager().getLaunchIntentForPackage(packageName) != null;
    }

    private static String formatRatio(Float ratio) {
        return ratio == null ? "unset" : String.format(Locale.US, "%.4f", ratio);
    }

    private static String buildFilteredLogcat(Context context) {
        String raw = Shell.runSuForOutput(
                "logcat -d -v epoch -t " + MAX_LOG_LINES);
        if (raw == null) {
            return "status=unavailable\n"
                    + "reason=cannot read logcat through root; the remaining diagnostic "
                    + "files are still usable.\n";
        }
        Set<String> packageNames = new LinkedHashSet<>();
        packageNames.add("OneLab");
        packageNames.add("io.github.pigerzhu.onelab");
        packageNames.add("LSPosed");
        packageNames.add("AndroidRuntime");
        for (DiagnosticCatalog.Feature feature : DiagnosticCatalog.FEATURES) {
            if (feature.packageName != null) packageNames.add(feature.packageName);
        }

        long startedAt = sessionStartedAt(context);
        long stoppedAt = sessionStoppedAt(context);
        String[] lines = raw.split("\\R");
        StringBuilder output = new StringBuilder();
        int included = 0;
        for (String line : lines) {
            if (startedAt > 0 && logEpochMillis(line) < startedAt) continue;
            if (stoppedAt > 0 && logEpochMillis(line) > stoppedAt) continue;
            if (!containsAny(line, packageNames)) continue;
            output.append(redact(line)).append('\n');
            included++;
            if (included >= MAX_REPORT_LOG_LINES) break;
        }
        if (output.length() == 0) {
            output.append("status=empty\n")
                    .append("reason=no log entries matching OneLab or the target apps were "
                            + "captured during the recording window.\n");
        }
        return output.toString();
    }

    private static long logEpochMillis(String line) {
        int space = line.indexOf(' ');
        if (space <= 0) return Long.MAX_VALUE;
        try {
            return (long) (Double.parseDouble(line.substring(0, space)) * 1000d);
        } catch (NumberFormatException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static boolean containsAny(String line, Set<String> values) {
        for (String value : values) {
            if (line.contains(value)) return true;
        }
        return false;
    }

    private static String redact(String value) {
        String redacted = SENSITIVE_ASSIGNMENT.matcher(value)
                .replaceAll("$1=<redacted>");
        redacted = PHONE.matcher(redacted).replaceAll("<phone>");
        return EMAIL.matcher(redacted).replaceAll("<email>");
    }

    private static String summarizeValue(String raw) {
        if (raw == null) return "unset";
        if (raw.length() <= 120) return safeValue(raw);
        int entries = raw.split("[,;]").length;
        return "<configured entries=" + entries + " length=" + raw.length() + ">";
    }

    private static String safeValue(String raw) {
        if (raw == null) return "unset";
        return redact(raw.replace('\n', ' ').replace('\r', ' '));
    }

    private static String packageVersion(Context context, String packageName) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            return info.versionName + " (" + info.getLongVersionCode() + ")";
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }
    }

    private static String isoTime(long millis) {
        SimpleDateFormat format = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date(millis));
    }

    private static File reportDirectory(Context context) {
        return new File(context.getCacheDir(), "diagnostics");
    }

    private static void put(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static Uri publishToDownloads(
            Context context, File source, String fileName) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
        values.put(MediaStore.Downloads.RELATIVE_PATH, REPORT_RELATIVE_PATH);
        values.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("cannot create the file in Downloads");
        try (InputStream input = new java.io.FileInputStream(source);
             OutputStream output = resolver.openOutputStream(uri, "w")) {
            if (output == null) throw new IOException("cannot write the file in Downloads");
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
        } catch (IOException error) {
            resolver.delete(uri, null, null);
            throw error;
        }
        values.clear();
        values.put(MediaStore.Downloads.IS_PENDING, 0);
        resolver.update(uri, values, null, null);
        return uri;
    }

    private static void deletePublishedReports(Context context) {
        ContentResolver resolver = context.getContentResolver();
        String selection = MediaStore.Downloads.RELATIVE_PATH + "=? AND "
                + MediaStore.Downloads.DISPLAY_NAME + " LIKE ?";
        String[] args = {REPORT_RELATIVE_PATH, REPORT_PREFIX + "%.zip"};
        try (Cursor cursor = resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Downloads._ID},
                selection,
                args,
                null)) {
            if (cursor == null) return;
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID);
            while (cursor.moveToNext()) {
                Uri uri = Uri.withAppendedPath(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        String.valueOf(cursor.getLong(idColumn)));
                resolver.delete(uri, null, null);
            }
        } catch (Exception ignored) {
            // A stale report can still be removed normally from the Downloads app.
        }
    }

    private static void pruneTemporaryReports(File directory) {
        File[] reports = directory.listFiles();
        if (reports == null) return;
        for (File report : reports) {
            if (report.isFile()) report.delete();
        }
    }

    public static final class PublishedReport {
        public final Uri uri;
        public final String fileName;
        public final String displayPath;

        PublishedReport(Uri uri, String fileName, String displayPath) {
            this.uri = uri;
            this.fileName = fileName;
            this.displayPath = displayPath;
        }
    }
}
