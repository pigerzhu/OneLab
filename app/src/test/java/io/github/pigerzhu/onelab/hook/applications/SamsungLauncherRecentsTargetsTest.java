package io.github.pigerzhu.onelab.hook.applications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public final class SamsungLauncherRecentsTargetsTest {
    @Test
    public void declaresStableLauncherContracts() {
        assertEquals("com.honeyspace.ui.common.util.RecentLayoutPolicy",
                SamsungLauncherRecentsTargets.POLICY_CLASS);
        assertEquals("com.honeyspace.ui.common.interfaces.TaskChangerRepository",
                SamsungLauncherRecentsTargets.REPOSITORY_CLASS);
        assertEquals("kotlinx.coroutines.flow.MutableStateFlow",
                SamsungLauncherRecentsTargets.MUTABLE_STATE_FLOW_CLASS);
        assertEquals("updateLayoutType", SamsungLauncherRecentsTargets.UPDATE_METHOD);
    }

    @Test
    public void findsOneAssignableFieldAndIgnoresDecoys() {
        Field field = SamsungLauncherRecentsTargets.findUniqueAssignableField(
                OneRepositoryOwner.class, Repository.class);
        assertEquals("repository", field.getName());
    }

    @Test
    public void rejectsAmbiguousAssignableFields() {
        try {
            SamsungLauncherRecentsTargets.findUniqueAssignableField(
                    TwoRepositoryOwner.class, Repository.class);
            fail("Expected ambiguous fields to be rejected");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("2"));
        }
    }

    @Test
    public void launcherIsDispatchedAndRecommendedInScope() throws Exception {
        String entry = read("src/main/java/io/github/pigerzhu/onelab/hook/Entry.java");
        String constants = read(
                "src/main/java/io/github/pigerzhu/onelab/hook/core/HookConstants.java");
        String scope = read("src/main/res/values/arrays.xml");
        assertTrue(entry.contains("SamsungLauncherRecentsHook.install(lpparam)"));
        assertTrue(constants.contains(
                "SAMSUNG_LAUNCHER_PACKAGE = \"com.sec.android.app.launcher\""));
        assertTrue(scope.contains("<item>com.sec.android.app.launcher</item>"));
    }

    @Test
    public void hookFallsBackToAttachContextWhenApplicationContextIsUnavailable()
            throws Exception {
        String hook = read(
                "src/main/java/io/github/pigerzhu/onelab/hook/applications/"
                        + "SamsungLauncherRecentsHook.java");
        assertTrue(hook.contains("getApplicationContext()"));
        assertTrue(hook.contains("context == null"));
        assertTrue(hook.contains("context = attachContext"));
    }

    @Test
    public void installsStateFlowWriteInterceptionBeforeSamsungLayoutAnimation()
            throws Exception {
        String hook = read(
                "src/main/java/io/github/pigerzhu/onelab/hook/applications/"
                        + "SamsungLauncherRecentsHook.java");
        assertTrue(hook.contains("hookStateFlowWrite"));
        assertTrue(hook.contains("beforeHookedMethod"));
        assertTrue(hook.contains("param.args[0] = selected"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }

    private interface Repository {
    }

    @SuppressWarnings("unused")
    private static final class OneRepositoryOwner {
        private String decoy;
        private Repository repository;
    }

    @SuppressWarnings("unused")
    private static final class TwoRepositoryOwner {
        private Repository first;
        private Repository second;
    }
}
