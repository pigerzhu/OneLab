package io.github.pigerzhu.onelab.hook.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class GosVrrPolicyTest {
    @Test
    public void clampsOnlyPubgRequestsBelow120() {
        assertTrue(GosVrrPolicy.applies(GosVrrPolicy.PUBG_PACKAGE, 30));
        assertTrue(GosVrrPolicy.applies(GosVrrPolicy.PUBG_PACKAGE, 60));
        assertFalse(GosVrrPolicy.applies(GosVrrPolicy.PUBG_PACKAGE, 120));
        assertFalse(GosVrrPolicy.applies("com.example.othergame", 30));
        assertEquals(120, GosVrrPolicy.normalize(GosVrrPolicy.PUBG_PACKAGE, 30));
        assertEquals(120, GosVrrPolicy.normalize(GosVrrPolicy.PUBG_PACKAGE, 120));
        assertEquals(30, GosVrrPolicy.normalize("com.example.othergame", 30));
    }
}
