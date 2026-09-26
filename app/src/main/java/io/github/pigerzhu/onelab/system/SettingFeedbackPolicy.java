package io.github.pigerzhu.onelab.system;

import io.github.pigerzhu.onelab.R;

public final class SettingFeedbackPolicy {
    public enum SuccessNotice {
        NONE,
        REOPEN_APP,
        REBOOT_DEVICE
    }

    private SettingFeedbackPolicy() {
    }

    public static int messageFor(boolean saved, SuccessNotice successNotice,
                                 int failureMessage) {
        if (!saved) {
            return failureMessage;
        }
        if (successNotice == SuccessNotice.REOPEN_APP) {
            return R.string.toast_saved_reopen_app;
        }
        if (successNotice == SuccessNotice.REBOOT_DEVICE) {
            return R.string.toast_saved_reboot_required;
        }
        return 0;
    }
}
