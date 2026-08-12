package io.github.pigerzhu.onelab.hook.applications;

final class NeteaseHalfFoldPolicy {
    private static final String HALF_PLAYER_ACTIVITY =
            "com.netease.cloudmusic.halffold.HalfFoldPlayerActivity";
    static final int STATE_CLOSED = 0;
    static final int STATE_TENT = 1;
    static final int STATE_HALF_OPENED = 2;
    static final int STATE_OPENED = 3;

    enum Action {
        NONE,
        ENTER_HALF_PLAYER,
        EXIT_HALF_PLAYER
    }

    private NeteaseHalfFoldPolicy() {
    }

    static Action actionForDeviceState(int state, boolean halfPlayer) {
        if (state == STATE_HALF_OPENED) {
            return halfPlayer ? Action.NONE : Action.ENTER_HALF_PLAYER;
        }
        if (halfPlayer && (state == STATE_OPENED || state == STATE_CLOSED)) {
            return Action.EXIT_HALF_PLAYER;
        }
        return Action.NONE;
    }

    static Action actionForHingeAngle(float angle, boolean halfPlayer) {
        if (Math.abs(angle - 90f) <= 1f) {
            return halfPlayer ? Action.NONE : Action.ENTER_HALF_PLAYER;
        }
        if (halfPlayer && (angle <= 1f || angle >= 179f)) {
            return Action.EXIT_HALF_PLAYER;
        }
        return Action.NONE;
    }

    static Action actionForKnownPosture(int deviceState, float hingeAngle, boolean halfPlayer) {
        return deviceState >= 0
                ? actionForDeviceState(deviceState, halfPlayer)
                : actionForHingeAngle(hingeAngle, halfPlayer);
    }

    static int stateForName(String name) {
        if ("CLOSED".equals(name) || "CLOSE".equals(name)) return STATE_CLOSED;
        if ("TENT".equals(name)) return STATE_TENT;
        if ("HALF_OPENED".equals(name) || "HALF_FOLDED".equals(name)) {
            return STATE_HALF_OPENED;
        }
        if ("OPENED".equals(name) || "OPEN".equals(name)) return STATE_OPENED;
        return -1;
    }

    static boolean observerNewHalfArgument(
            String ownerClassName,
            boolean originalArgument,
            boolean enabled) {
        if (enabled && HALF_PLAYER_ACTIVITY.equals(ownerClassName)) return false;
        return originalArgument;
    }

    static <T> T preferAvailable(T preferred, T fallback) {
        return preferred != null ? preferred : fallback;
    }
}
