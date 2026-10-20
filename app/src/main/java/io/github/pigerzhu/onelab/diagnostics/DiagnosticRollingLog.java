package io.github.pigerzhu.onelab.diagnostics;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Bounded, immediately flushed capture segments; segment zero is the newest. */
public final class DiagnosticRollingLog implements Closeable {
    private final File directory;
    private final String prefix;
    private final long maxBytes;
    private final int segments;
    private FileOutputStream stream;
    private long bytes;

    public DiagnosticRollingLog(File directory, String prefix, long maxBytes, int segments)
            throws IOException {
        this.directory = directory;
        this.prefix = prefix;
        this.maxBytes = maxBytes;
        this.segments = segments;
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("capture directory");
        open();
    }

    private File file(int index) { return new File(directory, prefix + "." + index + ".log"); }

    private void open() throws IOException {
        bytes = file(0).length();
        stream = new FileOutputStream(file(0), true);
    }

    public synchronized void write(String text) throws IOException {
        byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
        if (bytes > 0 && bytes + encoded.length > maxBytes) {
            stream.close();
            Files.deleteIfExists(file(segments - 1).toPath());
            for (int i = segments - 2; i >= 0; i--) {
                if (file(i).exists()) Files.move(file(i).toPath(), file(i + 1).toPath());
            }
            open();
        }
        stream.write(encoded);
        stream.flush();
        bytes += encoded.length;
    }

    @Override public synchronized void close() throws IOException { stream.close(); }

    public static String read(File directory, String prefix, int segments) throws IOException {
        StringBuilder text = new StringBuilder();
        for (int i = segments - 1; i >= 0; i--) {
            File file = new File(directory, prefix + "." + i + ".log");
            if (file.isFile()) text.append(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        }
        return text.toString();
    }
}
