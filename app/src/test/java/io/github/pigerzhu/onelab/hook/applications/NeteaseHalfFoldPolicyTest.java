package io.github.pigerzhu.onelab.hook.applications;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NeteaseHalfFoldPolicyTest {
    @Test
    public void halfOpenedEntersOnlyFromNormalPlayer() {
        assertEquals(NeteaseHalfFoldPolicy.Action.ENTER_HALF_PLAYER,
                NeteaseHalfFoldPolicy.actionForDeviceState(
                        NeteaseHalfFoldPolicy.STATE_HALF_OPENED, false));
        assertEquals(NeteaseHalfFoldPolicy.Action.NONE,
                NeteaseHalfFoldPolicy.actionForDeviceState(
                        NeteaseHalfFoldPolicy.STATE_HALF_OPENED, true));
    }

    @Test
    public void openedAndClosedExitOnlyFromHalfPlayer() {
        assertEquals(NeteaseHalfFoldPolicy.Action.EXIT_HALF_PLAYER,
                NeteaseHalfFoldPolicy.actionForDeviceState(
                        NeteaseHalfFoldPolicy.STATE_OPENED, true));
        assertEquals(NeteaseHalfFoldPolicy.Action.EXIT_HALF_PLAYER,
                NeteaseHalfFoldPolicy.actionForDeviceState(
                        NeteaseHalfFoldPolicy.STATE_CLOSED, true));
        assertEquals(NeteaseHalfFoldPolicy.Action.NONE,
                NeteaseHalfFoldPolicy.actionForDeviceState(
                        NeteaseHalfFoldPolicy.STATE_OPENED, false));
    }

    @Test
    public void tentAndUnknownStatesNeverSwitchPlayers() {
        assertEquals(NeteaseHalfFoldPolicy.Action.NONE,
                NeteaseHalfFoldPolicy.actionForDeviceState(
                        NeteaseHalfFoldPolicy.STATE_TENT, false));
        assertEquals(NeteaseHalfFoldPolicy.Action.NONE,
                NeteaseHalfFoldPolicy.actionForDeviceState(99, true));
    }

    @Test
    public void hingeFallbackTreatsOnlyDiscreteHalfAndFlatValuesAsTransitions() {
        assertEquals(NeteaseHalfFoldPolicy.Action.ENTER_HALF_PLAYER,
                NeteaseHalfFoldPolicy.actionForHingeAngle(90f, false));
        assertEquals(NeteaseHalfFoldPolicy.Action.EXIT_HALF_PLAYER,
                NeteaseHalfFoldPolicy.actionForHingeAngle(180f, true));
        assertEquals(NeteaseHalfFoldPolicy.Action.EXIT_HALF_PLAYER,
                NeteaseHalfFoldPolicy.actionForHingeAngle(0f, true));
        assertEquals(NeteaseHalfFoldPolicy.Action.NONE,
                NeteaseHalfFoldPolicy.actionForHingeAngle(135f, false));
    }

    @Test
    public void cachedDeviceStateWinsAndHingeIsUsedOnlyWhenStateIsUnavailable() {
        assertEquals(NeteaseHalfFoldPolicy.Action.NONE,
                NeteaseHalfFoldPolicy.actionForKnownPosture(
                        NeteaseHalfFoldPolicy.STATE_TENT, 90f, false));
        assertEquals(NeteaseHalfFoldPolicy.Action.ENTER_HALF_PLAYER,
                NeteaseHalfFoldPolicy.actionForKnownPosture(-1, 90f, false));
    }

    @Test
    public void stableDeviceStateNamesMapToPolicyWithoutTrustingOemIdentifiers() {
        assertEquals(NeteaseHalfFoldPolicy.STATE_HALF_OPENED,
                NeteaseHalfFoldPolicy.stateForName("HALF_OPENED"));
        assertEquals(NeteaseHalfFoldPolicy.STATE_OPENED,
                NeteaseHalfFoldPolicy.stateForName("OPENED"));
        assertEquals(NeteaseHalfFoldPolicy.STATE_CLOSED,
                NeteaseHalfFoldPolicy.stateForName("CLOSED"));
        assertEquals(NeteaseHalfFoldPolicy.STATE_TENT,
                NeteaseHalfFoldPolicy.stateForName("TENT"));
        assertEquals(-1, NeteaseHalfFoldPolicy.stateForName("REAR_DISPLAY"));
    }

    @Test
    public void oneUi85DeviceStateNamesMapToTheSamePostures() {
        assertEquals(NeteaseHalfFoldPolicy.STATE_CLOSED,
                NeteaseHalfFoldPolicy.stateForName("CLOSE"));
        assertEquals(NeteaseHalfFoldPolicy.STATE_HALF_OPENED,
                NeteaseHalfFoldPolicy.stateForName("HALF_FOLDED"));
        assertEquals(NeteaseHalfFoldPolicy.STATE_OPENED,
                NeteaseHalfFoldPolicy.stateForName("OPEN"));
    }

    @Test
    public void suppressesOnlyTheHalfPlayerNewHalfObserverWhileEnabled() {
        assertEquals(false, NeteaseHalfFoldPolicy.observerNewHalfArgument(
                "com.netease.cloudmusic.halffold.HalfFoldPlayerActivity", true, true));
        assertEquals(true, NeteaseHalfFoldPolicy.observerNewHalfArgument(
                "com.netease.cloudmusic.activity.PlayerActivity", true, true));
        assertEquals(true, NeteaseHalfFoldPolicy.observerNewHalfArgument(
                "com.netease.cloudmusic.halffold.HalfFoldPlayerActivity", true, false));
        assertEquals(false, NeteaseHalfFoldPolicy.observerNewHalfArgument(
                "com.netease.cloudmusic.halffold.HalfFoldPlayerActivity", false, true));
    }

    @Test
    public void fallsBackWhenApplicationContextIsNotReadyDuringAttach() {
        assertEquals("application",
                NeteaseHalfFoldPolicy.preferAvailable("application", "base"));
        assertEquals("base", NeteaseHalfFoldPolicy.preferAvailable(null, "base"));
    }
}
