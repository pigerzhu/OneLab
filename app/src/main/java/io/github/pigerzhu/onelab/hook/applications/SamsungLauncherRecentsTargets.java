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
        Method updateMethod = policyClass.getDeclaredMethod(UPDATE_METHOD);
        Field repositoryField = findUniqueAssignableField(policyClass, repositoryClass);
        Field mutableStateField = findUniqueAssignableField(policyClass, mutableStateFlowClass);
        FieldMethod honeySpace = findUniqueFieldWithMethod(
                policyClass, IS_DEX_SPACE_METHOD, boolean.class);
        FieldMethod desktop = findUniqueFieldWithMethod(
                policyClass, GET_FORCE_LAYOUT_METHOD, null);
        Method repositoryLayoutMethod = repositoryClass.getMethod("getTaskChangerLayout");
        updateMethod.setAccessible(true);
        repositoryField.setAccessible(true);
        mutableStateField.setAccessible(true);
        repositoryLayoutMethod.setAccessible(true);
        return new SamsungLauncherRecentsTargets(
                policyClass,
                updateMethod,
                repositoryField,
                mutableStateField,
                honeySpace.field,
                desktop.field,
                repositoryLayoutMethod,
                honeySpace.method,
                desktop.method);
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

    static FieldMethod findUniqueFieldWithMethod(
            Class<?> owner, String methodName, Class<?> expectedReturnType) {
        FieldMethod match = null;
        int count = 0;
        for (Field field : owner.getDeclaredFields()) {
            try {
                Method method = field.getType().getMethod(methodName);
                if (method.getParameterCount() != 0
                        || (expectedReturnType != null
                        && method.getReturnType() != expectedReturnType)) continue;
                field.setAccessible(true);
                method.setAccessible(true);
                match = new FieldMethod(field, method);
                count++;
            } catch (NoSuchMethodException ignored) {
                // This dependency does not expose the stable business method.
            }
        }
        if (count != 1) {
            throw new IllegalStateException(owner.getName() + " has " + count
                    + " fields exposing " + methodName + "()");
        }
        return match;
    }

    static final class FieldMethod {
        final Field field;
        final Method method;

        FieldMethod(Field field, Method method) {
            this.field = field;
            this.method = method;
        }
    }
}
