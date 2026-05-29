package su.kidoz.axiomj.property;

import java.lang.annotation.Annotation;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Shrinker {
    private Shrinker() {}

    public static List<Object> candidates(Class<?> type, Annotation[] annotations, Object value) {
        if (value == null) return List.of();
        if (type == int.class || type == Integer.class) {
            int v = (Integer) value;
            var out = new ArrayList<Object>();
            addDistinct(out, 0);
            addDistinct(out, v / 2);
            addDistinct(out, v > 0 ? 1 : -1);
            return out;
        }
        if (type == long.class || type == Long.class) {
            long v = (Long) value;
            var out = new ArrayList<Object>();
            addDistinct(out, 0L);
            addDistinct(out, v / 2L);
            addDistinct(out, v > 0 ? 1L : -1L);
            return out;
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.TRUE.equals(value) ? List.of(false) : List.of();
        }
        if (type == String.class) {
            var s = (String) value;
            var out = new ArrayList<Object>();
            addDistinct(out, "");
            if (s.length() > 1) addDistinct(out, s.substring(0, s.length() / 2));
            if (!s.isEmpty()) addDistinct(out, s.substring(0, s.length() - 1));
            return out;
        }
        if (type.isEnum()) {
            var constants = type.getEnumConstants();
            return constants.length > 0 && !constants[0].equals(value) ? List.of(constants[0]) : List.of();
        }
        if (type.isRecord()) {
            return recordCandidates(type, value);
        }
        return List.of();
    }

    private static List<Object> recordCandidates(Class<?> type, Object value) {
        try {
            RecordComponent[] components = type.getRecordComponents();
            var current = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                current[i] = components[i].getAccessor().invoke(value);
            }
            var out = new ArrayList<Object>();
            for (int i = 0; i < components.length; i++) {
                for (var candidate : candidates(components[i].getType(), components[i].getAnnotations(), current[i])) {
                    var args = current.clone();
                    args[i] = candidate;
                    out.add(newRecord(type, components, args));
                }
            }
            return out;
        } catch (ReflectiveOperationException e) {
            return List.of();
        }
    }

    private static Object newRecord(Class<?> type, RecordComponent[] components, Object[] args)
            throws ReflectiveOperationException {
        var parameterTypes =
                Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
        var constructor = type.getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }

    private static void addDistinct(List<Object> out, Object value) {
        if (!out.contains(value)) {
            out.add(value);
        }
    }
}
