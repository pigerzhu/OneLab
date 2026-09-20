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
    static final String USE_TABLET_UI_METHOD = "useTabletUI";
    static final String GET_DISPLAY_ID_METHOD = "getDisplayId";

    final Class<?> policyClass;
    final Method updateMethod;
    final Field repositoryField;
    final Field mutableStateField;
    final Field honeySpaceInfoField;
    final Field desktopLayoutManagerField;
    final Method repositoryLayoutMethod;
    final Method isDexSpaceMethod;
    final Method getForceLayoutMethod;
    final Field legacyForcePolicyField;
    final Method legacyForcePolicyMethod;
    final Field displayInfoField;
    final Field nestedDisplayInfoOwnerField;
    final Method displayIdMethod;

    private SamsungLauncherRecentsTargets(
            Class<?> policyClass,
            Method updateMethod,
            Field repositoryField,
            Field mutableStateField,
            Field honeySpaceInfoField,
            Field desktopLayoutManagerField,
            Method repositoryLayoutMethod,
            Method isDexSpaceMethod,
            Method getForceLayoutMethod,
            Field legacyForcePolicyField,
            Method legacyForcePolicyMethod,
            Field displayInfoField,
            Field nestedDisplayInfoOwnerField,
            Method displayIdMethod) {
        this.policyClass = policyClass;
        this.updateMethod = updateMethod;
        this.repositoryField = repositoryField;
        this.mutableStateField = mutableStateField;
        this.honeySpaceInfoField = honeySpaceInfoField;
        this.desktopLayoutManagerField = desktopLayoutManagerField;
        this.repositoryLayoutMethod = repositoryLayoutMethod;
        this.isDexSpaceMethod = isDexSpaceMethod;
        this.getForceLayoutMethod = getForceLayoutMethod;
        this.legacyForcePolicyField = legacyForcePolicyField;
        this.legacyForcePolicyMethod = legacyForcePolicyMethod;
        this.displayInfoField = displayInfoField;
        this.nestedDisplayInfoOwnerField = nestedDisplayInfoOwnerField;
        this.displayIdMethod = displayIdMethod;
    }

    static SamsungLauncherRecentsTargets resolve(ClassLoader loader) throws Exception {
        Class<?> policyClass = Class.forName(POLICY_CLASS, false, loader);
        Class<?> repositoryClass = Class.forName(REPOSITORY_CLASS, false, loader);
        Class<?> mutableStateFlowClass = Class.forName(
                MUTABLE_STATE_FLOW_CLASS, false, loader);
        Method updateMethod = policyClass.getDeclaredMethod(UPDATE_METHOD);
        Field repositoryField = findUniqueAssignableField(policyClass, repositoryClass);
        Field mutableStateField = findUniqueAssignableField(policyClass, mutableStateFlowClass);
        FieldMethod honeySpace = findOptionalUniqueFieldWithMethod(
                policyClass, IS_DEX_SPACE_METHOD, boolean.class);
        FieldMethod desktop = findOptionalUniqueFieldWithMethod(
                policyClass, GET_FORCE_LAYOUT_METHOD, null);
        FieldMethod legacyForce = findOptionalUniqueFieldWithMethod(
                policyClass, USE_TABLET_UI_METHOD, boolean.class);
        FieldMethod directDisplay = findOptionalUniqueFieldWithMethod(
                policyClass, GET_DISPLAY_ID_METHOD, int.class);
        FieldMethod nestedDisplay = directDisplay == null && legacyForce != null
                ? findNestedUniqueFieldWithMethod(
                        policyClass, USE_TABLET_UI_METHOD, GET_DISPLAY_ID_METHOD, int.class)
                : null;
        if ((honeySpace == null) != (desktop == null)) {
            throw new IllegalStateException(
                    policyClass.getName() + " has an incomplete desktop policy contract");
        }
        if (honeySpace == null && legacyForce == null) {
            throw new IllegalStateException(
                    policyClass.getName() + " has no supported desktop policy contract");
        }
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
                honeySpace != null ? honeySpace.field : null,
                desktop != null ? desktop.field : null,
                repositoryLayoutMethod,
                honeySpace != null ? honeySpace.method : null,
                desktop != null ? desktop.method : null,
                legacyForce != null ? legacyForce.field : null,
                legacyForce != null ? legacyForce.method : null,
                directDisplay != null ? directDisplay.field
                        : nestedDisplay != null ? nestedDisplay.nestedField : null,
                nestedDisplay != null ? nestedDisplay.field : null,
                directDisplay != null ? directDisplay.method
                        : nestedDisplay != null ? nestedDisplay.method : null);
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
        FieldMethod match = findOptionalUniqueFieldWithMethod(
                owner, methodName, expectedReturnType);
        if (match == null) {
            throw new IllegalStateException(owner.getName()
                    + " has 0 fields exposing " + methodName + "()");
        }
        return match;
    }

    static FieldMethod findOptionalUniqueFieldWithMethod(
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
        if (count > 1) {
            throw new IllegalStateException(owner.getName() + " has " + count
                    + " fields exposing " + methodName + "()");
        }
        return match;
    }

    static FieldMethod findNestedUniqueFieldWithMethod(
            Class<?> owner, String outerMethodName, String nestedMethodName,
            Class<?> expectedReturnType) {
        FieldMethod match = null;
        int count = 0;
        for (Field outerField : owner.getDeclaredFields()) {
            try {
                Method outerMethod = outerField.getType().getMethod(outerMethodName);
                if (outerMethod.getParameterCount() != 0) continue;
                for (Field nestedField : outerField.getType().getDeclaredFields()) {
                    try {
                        Method nestedMethod = nestedField.getType().getMethod(nestedMethodName);
                        if (nestedMethod.getParameterCount() != 0
                                || (expectedReturnType != null
                                && nestedMethod.getReturnType() != expectedReturnType)) continue;
                        outerField.setAccessible(true);
                        nestedField.setAccessible(true);
                        nestedMethod.setAccessible(true);
                        match = new FieldMethod(outerField, nestedMethod, nestedField);
                        count++;
                    } catch (NoSuchMethodException ignored) {
                        // This nested dependency does not expose the stable method.
                    }
                }
            } catch (NoSuchMethodException ignored) {
                // This dependency does not expose the outer business method.
            }
        }
        if (count > 1) {
            throw new IllegalStateException(owner.getName() + " has " + count
                    + " nested fields exposing " + nestedMethodName + "()");
        }
        return match;
    }

    static final class FieldMethod {
        final Field field;
        final Method method;
        final Field nestedField;

        FieldMethod(Field field, Method method) {
            this(field, method, null);
        }

        FieldMethod(Field field, Method method, Field nestedField) {
            this.field = field;
            this.method = method;
            this.nestedField = nestedField;
        }
    }
}
