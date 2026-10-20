package io.github.pigerzhu.onelab.hook.system;

public interface GpuDvfsVoteBackend {
    boolean acquireMinimum(int mhz);

    boolean acquireMaximum(int mhz);

    void releaseAll();
}
