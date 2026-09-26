package io.github.pigerzhu.onelab.hook.applications;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Weak identity registry for HoneySpace-scoped recent-layout policy instances. */
final class SamsungRecentsPolicyRegistry {
    private final List<Entry> entries = new ArrayList<>();

    synchronized Entry register(
            Object policy,
            Object writableState,
            Object repository,
            Object honeySpaceInfo,
            Object desktopLayoutManager) {
        prune();
        Entry existing = findByPolicyInternal(policy);
        if (existing != null) return existing;
        Entry entry = new Entry(
                policy, writableState, repository, honeySpaceInfo, desktopLayoutManager);
        entries.add(entry);
        return entry;
    }

    synchronized Entry findByPolicy(Object policy) {
        prune();
        return findByPolicyInternal(policy);
    }

    synchronized Entry findByWritableState(Object writableState) {
        prune();
        for (Entry entry : entries) {
            if (entry.writableState() == writableState) return entry;
        }
        return null;
    }

    synchronized List<Entry> snapshot() {
        prune();
        return new ArrayList<>(entries);
    }

    private Entry findByPolicyInternal(Object policy) {
        for (Entry entry : entries) {
            if (entry.policy() == policy) return entry;
        }
        return null;
    }

    private void prune() {
        Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.policy() == null || entry.writableState() == null) iterator.remove();
        }
    }

    static final class Entry {
        private final WeakReference<Object> policy;
        private final WeakReference<Object> writableState;
        private final WeakReference<Object> repository;
        private final WeakReference<Object> honeySpaceInfo;
        private final WeakReference<Object> desktopLayoutManager;
        private boolean writingOverride;

        Entry(
                Object policy,
                Object writableState,
                Object repository,
                Object honeySpaceInfo,
                Object desktopLayoutManager) {
            this.policy = new WeakReference<>(policy);
            this.writableState = new WeakReference<>(writableState);
            this.repository = new WeakReference<>(repository);
            this.honeySpaceInfo = new WeakReference<>(honeySpaceInfo);
            this.desktopLayoutManager = new WeakReference<>(desktopLayoutManager);
        }

        Object policy() {
            return policy.get();
        }

        Object writableState() {
            return writableState.get();
        }

        Object repository() {
            return repository.get();
        }

        Object honeySpaceInfo() {
            return honeySpaceInfo.get();
        }

        Object desktopLayoutManager() {
            return desktopLayoutManager.get();
        }

        boolean isWritingOverride() {
            return writingOverride;
        }

        void setWritingOverride(boolean value) {
            writingOverride = value;
        }
    }
}
