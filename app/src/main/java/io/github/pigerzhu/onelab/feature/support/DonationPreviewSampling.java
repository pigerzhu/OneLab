package io.github.pigerzhu.onelab.feature.support;

/** Chooses a power-of-two decode sample that bounds an RGB_565 preview allocation. */
final class DonationPreviewSampling {
    private static final int RGB_565_BYTES_PER_PIXEL = 2;

    private DonationPreviewSampling() {}

    static int inSampleSizeForRgb565(int width, int height, int maximumBytes) {
        if (width <= 0 || height <= 0 || maximumBytes <= 0) return 1;
        int sampleSize = 1;
        while (decodedBytes(width, height, sampleSize) > maximumBytes
                && sampleSize <= (1 << 29)) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    private static long decodedBytes(int width, int height, int sampleSize) {
        long sampledWidth = (width + (long) sampleSize - 1) / sampleSize;
        long sampledHeight = (height + (long) sampleSize - 1) / sampleSize;
        return sampledWidth * sampledHeight * RGB_565_BYTES_PER_PIXEL;
    }
}
