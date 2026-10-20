package io.github.pigerzhu.onelab.diagnostics;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import io.github.pigerzhu.onelab.system.DiagnosticCapture;

/** Exports the stopped recording without loading its log segments into memory. */
final class DiagnosticCaptureArchive {
    private DiagnosticCaptureArchive() {}

    static void put(ZipOutputStream zip, Context context) throws IOException {
        File directory = DiagnosticCapture.directory(context);
        for (String prefix : new String[]{"logcat", "state"}) {
            int count = prefix.equals("logcat") ? DiagnosticCapture.LOG_SEGMENTS : DiagnosticCapture.STATE_SEGMENTS;
            for (int i = count - 1; i >= 0; i--) {
                File file = new File(directory, prefix + "." + i + ".log");
                if (file.isFile()) putFile(zip, file);
            }
        }
        for (String name : new String[]{"process-exits.txt", "crash-history.txt", "last-anr.txt", "fold-state.txt"}) {
            File file = new File(directory, name);
            if (file.isFile()) putFile(zip, file);
        }
        zip.putNextEntry(new ZipEntry("recents-capture/README.txt"));
        zip.write(("Live logcat segments: 0 is newest; higher numbers precede it.\n"
                + "State samples include process PIDs, uptime, app configuration and recents hook status.\n"
                + "Compare PID changes and uptime to distinguish a process restart from a device reboot.\n"
                + "A capture_error/logcat_exited marker or missing heartbeats means recording was interrupted.\n"
                + "Logcat is bounded to 8 MiB and state history to 1 MiB; oldest segments rotate.\n"
                + "Historical exits/DropBox entries may predate this recording; compare their timestamps.\n")
                .getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void putFile(ZipOutputStream zip, File file) throws IOException {
        zip.putNextEntry(new ZipEntry("recents-capture/" + file.getName()));
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) zip.write(buffer, 0, count);
        }
        zip.closeEntry();
    }
}
