package io.github.pigerzhu.onelab.hook.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class GosVrrPolicyTest {
    @Test
    public void clampsEveryGosTargetOnlyWhenEnabled() {
        assertTrue(GosVrrPolicy.applies(true, GosVrrPolicy.PUBG_PACKAGE, 30));
        assertTrue(GosVrrPolicy.applies(true, "com.example.othergame", 60));
        assertFalse(GosVrrPolicy.applies(true, "com.example.othergame", 120));
        assertFalse(GosVrrPolicy.applies(false, GosVrrPolicy.PUBG_PACKAGE, 30));
        assertEquals(120, GosVrrPolicy.normalize(true, GosVrrPolicy.PUBG_PACKAGE, 30));
        assertEquals(120, GosVrrPolicy.normalize(true, "com.example.othergame", 60));
        assertEquals(120, GosVrrPolicy.normalize(true, GosVrrPolicy.PUBG_PACKAGE, 120));
        assertEquals(30, GosVrrPolicy.normalize(false, GosVrrPolicy.PUBG_PACKAGE, 30));
        assertEquals(30, GosVrrPolicy.normalize(true, "", 30));
    }
}
