package io.github.pigerzhu.onelab.feature.support;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class DonationPreviewSamplingTest {
    @Test
    public void fullResolutionDonationImageStaysUnderOneMegabyte() {
        assertEquals(4, DonationPreviewSampling.inSampleSizeForRgb565(
                2041, 2154, 1_000_000));
    }

    @Test
    public void smallImageIsNotUpscaledDuringDecode() {
        assertEquals(1, DonationPreviewSampling.inSampleSizeForRgb565(
                640, 480, 1_000_000));
    }

    @Test
    public void samplingUsesPowerOfTwoSteps() {
        assertEquals(8, DonationPreviewSampling.inSampleSizeForRgb565(
                4000, 3000, 1_000_000));
    }
}
