package io.github.pigerzhu.onelab.system;

import static org.junit.Assert.assertEquals;

import io.github.pigerzhu.onelab.R;

import org.junit.Test;

public final class SettingFeedbackPolicyTest {

    @Test
    public void ordinarySuccessfulSaveIsSilent() {
        assertEquals(0, SettingFeedbackPolicy.messageFor(
                true,
                SettingFeedbackPolicy.SuccessNotice.NONE,
                R.string.toast_save_failed_permission));
    }

    @Test
    public void requiredReopenAndRebootNoticesRemainVisible() {
        assertEquals(R.string.toast_saved_reopen_app, SettingFeedbackPolicy.messageFor(
                true,
                SettingFeedbackPolicy.SuccessNotice.REOPEN_APP,
                R.string.toast_save_failed_permission));
        assertEquals(R.string.toast_saved_reboot_required, SettingFeedbackPolicy.messageFor(
                true,
                SettingFeedbackPolicy.SuccessNotice.REBOOT_DEVICE,
                R.string.toast_save_failed_permission));
    }

    @Test
    public void failedSaveUsesTheProvidedError() {
        assertEquals(R.string.toast_write_failed_root_permission,
                SettingFeedbackPolicy.messageFor(
                        false,
                        SettingFeedbackPolicy.SuccessNotice.NONE,
                        R.string.toast_write_failed_root_permission));
    }
}
