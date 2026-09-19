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
    static final String UPDATE_METHOD = "updateLayoutType";

    final Method updateMethod;
    final Field repositoryField;
    final Field mutableStateField;
    final Method repositoryLayoutMethod;

    private SamsungLauncherRecentsTargets(
            Method updateMethod,
            Field repositoryField,
            Field mutableStateField,
            Method repositoryLayoutMethod) {
        this.updateMethod = updateMethod;
        this.repositoryField = repositoryField;
        this.mutableStateField = mutableStateField;
        this.repositoryLayoutMethod = repositoryLayoutMethod;
    }

    static SamsungLauncherRecentsTargets resolve(ClassLoader loader) throws Exception {
        Class<?> policyClass = Class.forName(POLICY_CLASS, false, loader);
        Class<?> repositoryClass = Class.forName(REPOSITORY_CLASS, false, loader);
        Class<?> mutableStateFlowClass = Class.forName(
                MUTABLE_STATE_FLOW_CLASS, false, loader);

        Method updateMethod = policyClass.getDeclaredMethod(UPDATE_METHOD);
        Field repositoryField = findUniqueAssignableField(policyClass, repositoryClass);
        Field mutableStateField = findUniqueAssignableField(policyClass, mutableStateFlowClass);
        Method repositoryLayoutMethod = repositoryClass.getMethod("getTaskChangerLayout");
        updateMethod.setAccessible(true);
        repositoryField.setAccessible(true);
        mutableStateField.setAccessible(true);
        repositoryLayoutMethod.setAccessible(true);
        return new SamsungLauncherRecentsTargets(
                updateMethod, repositoryField, mutableStateField, repositoryLayoutMethod);
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
