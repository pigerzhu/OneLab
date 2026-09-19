package io.github.pigerzhu.onelab.hook.applications;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class SamsungLauncherRecentsTargets {
    static final String POLICY_CLASS =
            "com.honeyspace.ui.common.util.RecentLayoutPolicy";
    static final String REPOSITORY_CLASS =
            "com.honeyspace.ui.common.interfaces.TaskChangerRepository";
    static final String MUTABLE_STATE_FLOW_CLASS =
            "kotlinx.coroutines.flow.MutableStateFlow";
    static final String HONEY_SPACE_INFO_CLASS =
            "com.honeyspace.common.data.HoneySpaceInfo";
    static final String DESKTOP_LAYOUT_MANAGER_CLASS =
            "com.honeyspace.common.recents.DesktopTaskChangerLayoutManager";
    static final String UPDATE_METHOD = "updateLayoutType";
    static final String IS_DEX_SPACE_METHOD = "isDexSpace";
    static final String GET_FORCE_LAYOUT_METHOD = "getForceLayout";

    final Class<?> policyClass;
    final Method updateMethod;
    final Field repositoryField;
    final Field mutableStateField;
    final Field honeySpaceInfoField;
    final Field desktopLayoutManagerField;
    final Method repositoryLayoutMethod;
    final Method isDexSpaceMethod;
    final Method getForceLayoutMethod;

    private SamsungLauncherRecentsTargets(
            Class<?> policyClass,
            Method updateMethod,
            Field repositoryField,
            Field mutableStateField,
            Field honeySpaceInfoField,
            Field desktopLayoutManagerField,
            Method repositoryLayoutMethod,
            Method isDexSpaceMethod,
            Method getForceLayoutMethod) {
        this.policyClass = policyClass;
        this.updateMethod = updateMethod;
        this.repositoryField = repositoryField;
        this.mutableStateField = mutableStateField;
        this.honeySpaceInfoField = honeySpaceInfoField;
        this.desktopLayoutManagerField = desktopLayoutManagerField;
        this.repositoryLayoutMethod = repositoryLayoutMethod;
        this.isDexSpaceMethod = isDexSpaceMethod;
        this.getForceLayoutMethod = getForceLayoutMethod;
    }

    static SamsungLauncherRecentsTargets resolve(ClassLoader loader) throws Exception {
        Class<?> policyClass = Class.forName(POLICY_CLASS, false, loader);
        Class<?> repositoryClass = Class.forName(REPOSITORY_CLASS, false, loader);
        Class<?> mutableStateFlowClass = Class.forName(
                MUTABLE_STATE_FLOW_CLASS, false, loader);
        Class<?> honeySpaceInfoClass = Class.forName(
                HONEY_SPACE_INFO_CLASS, false, loader);
        Class<?> desktopLayoutManagerClass = Class.forName(
                DESKTOP_LAYOUT_MANAGER_CLASS, false, loader);

        Method updateMethod = policyClass.getDeclaredMethod(UPDATE_METHOD);
        Field repositoryField = findUniqueAssignableField(policyClass, repositoryClass);
        Field mutableStateField = findUniqueAssignableField(policyClass, mutableStateFlowClass);
        Field honeySpaceInfoField = findUniqueAssignableField(policyClass, honeySpaceInfoClass);
        Field desktopLayoutManagerField = findUniqueAssignableField(
                policyClass, desktopLayoutManagerClass);
        Method repositoryLayoutMethod = repositoryClass.getMethod("getTaskChangerLayout");
        Method isDexSpaceMethod = honeySpaceInfoClass.getMethod(IS_DEX_SPACE_METHOD);
        Method getForceLayoutMethod = desktopLayoutManagerClass.getMethod(
                GET_FORCE_LAYOUT_METHOD);
        updateMethod.setAccessible(true);
        repositoryField.setAccessible(true);
        mutableStateField.setAccessible(true);
        honeySpaceInfoField.setAccessible(true);
        desktopLayoutManagerField.setAccessible(true);
        repositoryLayoutMethod.setAccessible(true);
        isDexSpaceMethod.setAccessible(true);
        getForceLayoutMethod.setAccessible(true);
        return new SamsungLauncherRecentsTargets(
                policyClass,
                updateMethod,
                repositoryField,
                mutableStateField,
                honeySpaceInfoField,
                desktopLayoutManagerField,
                repositoryLayoutMethod,
                isDexSpaceMethod,
                getForceLayoutMethod);
    }

    static Field findUniqueAssignableField(Class<?> owner, Class<?> expectedType) {
        Field match = null;
        int count = 0;
        for (Field field : owner.getDeclaredFields()) {
            if (!expectedType.isAssignableFrom(field.getType())) continue;
            match = field;
            count++;
        }
        if (count != 1) {
            throw new IllegalStateException(owner.getName() + " has " + count
                    + " fields assignable to " + expectedType.getName());
        }
        match.setAccessible(true);
        return match;
    }
}
