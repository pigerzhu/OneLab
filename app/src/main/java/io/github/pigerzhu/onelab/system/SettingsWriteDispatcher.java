package io.github.pigerzhu.onelab.system;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

final class SettingsWriteDispatcher {
    private final Executor worker;
    private final Executor callbackExecutor;

    SettingsWriteDispatcher(Executor worker, Executor callbackExecutor) {
        this.worker = worker;
        this.callbackExecutor = callbackExecutor;
    }

    void dispatch(Callable<Boolean> operation, Consumer<Boolean> completion) {
        worker.execute(() -> {
            boolean saved;
            try {
                saved = operation.call();
            } catch (Exception ignored) {
                saved = false;
            }
            boolean result = saved;
            callbackExecutor.execute(() -> completion.accept(result));
        });
    }
}
