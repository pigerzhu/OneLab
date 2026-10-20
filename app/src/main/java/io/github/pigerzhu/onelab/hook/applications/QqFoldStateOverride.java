package io.github.pigerzhu.onelab.hook.applications;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

/** Overrides QQ's readable Fold cache without depending on obfuscated field names. */
final class QqFoldStateOverride {
    private static final String PAD_UTIL = "com.tencent.common.config.pad.PadUtil";
    private static final String DEVICE_TYPE = "com.tencent.common.config.pad.DeviceType";

    private final Class<?> padUtilClass;
    private final Field initializedField;
    private final Field deviceTypeField;
    private final Field foldSwitchField;
    private final Object foldDeviceType;
    private final Object openFoldSwitch;

    private boolean forced;
    private boolean savedInitialized;
    private Object savedDeviceType;
    private Object savedFoldSwitch;

    private QqFoldStateOverride(
            Class<?> padUtilClass,
            Field initializedField,
            Field deviceTypeField,
            Field foldSwitchField,
            Object foldDeviceType,
            Object openFoldSwitch
    ) {
        this.padUtilClass = padUtilClass;
        this.initializedField = initializedField;
        this.deviceTypeField = deviceTypeField;
        this.foldSwitchField = foldSwitchField;
        this.foldDeviceType = foldDeviceType;
        this.openFoldSwitch = openFoldSwitch;
    }

    static QqFoldStateOverride create(ClassLoader classLoader) throws Exception {
        Class<?> padUtil = classLoader.loadClass(PAD_UTIL);
        Class<?> deviceType = classLoader.loadClass(DEVICE_TYPE);
        Field initialized = findUniqueStaticField(padUtil, AtomicBoolean.class);
        Field cachedDeviceType = findUniqueStaticField(padUtil, deviceType);
        Field cachedFoldSwitch = findFoldSwitchField(padUtil);
        return new QqFoldStateOverride(
                padUtil,
                initialized,
                cachedDeviceType,
                cachedFoldSwitch,
                enumConstant(deviceType, "FOLD"),
                enumConstant(cachedFoldSwitch.getType(), "OPEN"));
    }

    synchronized void apply(boolean enabled) throws IllegalAccessException {
        synchronized (padUtilClass) {
            AtomicBoolean initialized = (AtomicBoolean) initializedField.get(null);
            if (enabled) {
                if (!forced) {
                    savedInitialized = initialized.get();
                    savedDeviceType = deviceTypeField.get(null);
                    savedFoldSwitch = foldSwitchField.get(null);
                    forced = true;
                }
                deviceTypeField.set(null, foldDeviceType);
                foldSwitchField.set(null, openFoldSwitch);
                initialized.set(true);
            } else if (forced) {
                initialized.set(savedInitialized);
                deviceTypeField.set(null, savedDeviceType);
                foldSwitchField.set(null, savedFoldSwitch);
                forced = false;
            }
        }
    }

    private static Field findUniqueStaticField(Class<?> owner, Class<?> type) {
        Field match = null;
        for (Field field : owner.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == type) {
                if (match != null) {
                    throw new IllegalStateException(
                            "Multiple static " + type.getName() + " fields in " + owner.getName());
                }
                field.setAccessible(true);
                match = field;
            }
        }
        if (match == null) {
            throw new IllegalStateException(
                    "No static " + type.getName() + " field in " + owner.getName());
        }
        return match;
    }

    private static Field findFoldSwitchField(Class<?> padUtil) {
        Field match = null;
        for (Field field : padUtil.getDeclaredFields()) {
            Class<?> type = field.getType();
            if (!Modifier.isStatic(field.getModifiers()) || !type.isEnum()
                    || enumConstantOrNull(type, "OPEN") == null
                    || enumConstantOrNull(type, "CLOSE") == null) {
                continue;
            }
            if (match != null) {
                throw new IllegalStateException("Multiple Fold switch fields in "
                        + padUtil.getName());
            }
            field.setAccessible(true);
            match = field;
        }
        if (match == null) {
            throw new IllegalStateException("No Fold switch field in " + padUtil.getName());
        }
        return match;
    }

    private static Object enumConstant(Class<?> enumClass, String name) {
        Object value = enumConstantOrNull(enumClass, name);
        if (value == null) {
            throw new IllegalStateException(name + " missing from " + enumClass.getName());
        }
        return value;
    }

    private static Object enumConstantOrNull(Class<?> enumClass, String name) {
        Object[] constants = enumClass.getEnumConstants();
        if (constants == null) return null;
        for (Object constant : constants) {
            if (name.equals(((Enum<?>) constant).name())) return constant;
        }
        return null;
    }
}
