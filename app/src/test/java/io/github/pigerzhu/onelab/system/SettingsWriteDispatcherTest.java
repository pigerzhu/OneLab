package io.github.pigerzhu.onelab.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class SettingsWriteDispatcherTest {
    @Test
    public void runsWorkOffCallerAndDeliversOneCompletion() throws Exception {
        Executor callback = Runnable::run;
        SettingsWriteDispatcher dispatcher = new SettingsWriteDispatcher(
                Executors.newSingleThreadExecutor(), callback);
        long caller = Thread.currentThread().getId();
        AtomicReference<Long> worker = new AtomicReference<>();
        AtomicInteger completions = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);

        dispatcher.dispatch(() -> {
            worker.set(Thread.currentThread().getId());
            return true;
        }, saved -> {
            assertTrue(saved);
            completions.incrementAndGet();
            done.countDown();
        });

        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertNotEquals(caller, worker.get().longValue());
        assertEquals(1, completions.get());
    }

    @Test
    public void preservesSubmissionOrder() throws Exception {
        SettingsWriteDispatcher dispatcher = new SettingsWriteDispatcher(
                Executors.newSingleThreadExecutor(), Runnable::run);
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(3);

        for (int value = 1; value <= 3; value++) {
            int submitted = value;
            dispatcher.dispatch(() -> {
                order.add(submitted);
                return true;
            }, saved -> done.countDown());
        }

        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertEquals(java.util.Arrays.asList(1, 2, 3), order);
    }
}
