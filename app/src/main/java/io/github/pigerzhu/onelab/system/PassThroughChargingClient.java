package io.github.pigerzhu.onelab.system;

import android.content.Context;

/** Accesses Samsung GameTools' stable Settings contract for USB PD pass-through. */
public final class PassThroughChargingClient {
    private static final String KEY_PASS_THROUGH = "pass_through";

    private final SettingsStore settings;

    public PassThroughChargingClient(Context context) {
        settings = new SettingsStore(context.getApplicationContext());
    }

    public boolean isEnabled() {
        return "1".equals(settings.getSystem(KEY_PASS_THROUGH, "0"));
    }

    public boolean setEnabled(boolean enabled) {
        String requested = enabled ? "1" : "0";
        return settings.putSystemQuietly(KEY_PASS_THROUGH, requested)
                && enabled == isEnabled();
    }
}
