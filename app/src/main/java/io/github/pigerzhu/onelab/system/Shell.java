package io.github.pigerzhu.onelab.system;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class Shell {
    private Shell() {
    }

    public static boolean runSu(String command) {
        try {
            Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            return process.waitFor() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String runSuForOutput(String command) {
        return runSuForOutput(command, false);
    }

    public static boolean runSuInMasterMount(String command) {
        try {
            Process process = new ProcessBuilder("su", "-mm", "-c", command)
                    .redirectErrorStream(true).start();
            return process.waitFor() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String runSuInMasterMountForOutput(String command) {
        return runSuForOutput(command, true);
    }

    private static String runSuForOutput(String command, boolean masterMount) {
        StringBuilder output = new StringBuilder();
        try {
            Process process = new ProcessBuilder(masterMount
                    ? new String[]{"su", "-mm", "-c", command}
                    : new String[]{"su", "-c", command})
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() > 0) {
                        output.append('\n');
                    }
                    output.append(line);
                }
            }
            if (process.waitFor() != 0) {
                return null;
            }
            String value = output.toString().trim();
            return value.isEmpty() || "null".equals(value) ? null : value;
        } catch (Exception ignored) {
            return null;
        }
    }
}
