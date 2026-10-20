package io.github.pigerzhu.onelab.system;

import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.Test;

public final class DonationImageSaverTest {
    @Test
    public void copyPreservesOriginalQrImageBytes() throws Exception {
        byte[] original = {1, 4, 9, 16, 25};
        ByteArrayOutputStream saved = new ByteArrayOutputStream();

        DonationImageSaver.copy(new ByteArrayInputStream(original), saved);

        assertArrayEquals(original, saved.toByteArray());
    }
}
