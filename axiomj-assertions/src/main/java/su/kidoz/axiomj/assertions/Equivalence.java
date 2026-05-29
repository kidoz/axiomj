package su.kidoz.axiomj.assertions;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Deep structural ("object graph") comparison powering {@code Subject.isEquivalentTo}. Compares two values
 * field-by-field, recursing into POJOs/records, collections, arrays and maps, ignoring reference identity. Returns the
 * first difference as a path-prefixed message, or {@code null} when the graphs are equivalent.
 */
final class Equivalence {
    private static final int MAX_DEPTH = 12;

    private Equivalence() {}

    static String diff(Object actual, Object expected, Set<String> ignoredFields) {
        return compare("", actual, expected, ignoredFields, new HashSet<>(), MAX_DEPTH);
    }

    private static String compare(
            String path, Object a, Object b, Set<String> ignored, Set<RefPair> visited, int depth) {
        if (a == b) {
            return null;
        }
        if (a == null || b == null) {
            return mismatch(path, a, b);
        }
        if (depth <= 0) {
            return Objects.equals(a, b) ? null : mismatch(path, a, b);
        }
        if (a instanceof Map<?, ?> ma && b instanceof Map<?, ?> mb) {
            return compareMap(path, ma, mb, ignored, visited, depth);
        }
        if (a.getClass().isArray() && b.getClass().isArray()) {
            return compareList(path, arrayToList(a), arrayToList(b), ignored, visited, depth);
        }
        if (a instanceof Iterable<?> ia && b instanceof Iterable<?> ib) {
            return compareList(path, toList(ia), toList(ib), ignored, visited, depth);
        }
        if (isLeaf(a) || isLeaf(b)) {
            return Objects.equals(a, b) ? null : mismatch(path, a, b);
        }
        if (!visited.add(new RefPair(a, b))) {
            return null; // cycle or already-compared pair
        }
        if (a.getClass() == b.getClass()) {
            return compareFields(path, a, b, ignored, visited, depth);
        }
        return Objects.equals(a, b)
                ? null
                : at(path)
                        + "expected <%s> (%s) but was <%s> (%s)"
                                .formatted(
                                        b,
                                        b.getClass().getSimpleName(),
                                        a,
                                        a.getClass().getSimpleName());
    }

    private static String compareFields(
            String path, Object a, Object b, Set<String> ignored, Set<RefPair> visited, int depth) {
        for (Field field : fields(a.getClass())) {
            if (ignored.contains(field.getName())) {
                continue;
            }
            Object av;
            Object bv;
            try {
                av = field.get(a);
                bv = field.get(b);
            } catch (IllegalAccessException e) {
                continue;
            }
            var childPath = path.isEmpty() ? field.getName() : path + "." + field.getName();
            var result = compare(childPath, av, bv, ignored, visited, depth - 1);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static String compareMap(
            String path, Map<?, ?> a, Map<?, ?> b, Set<String> ignored, Set<RefPair> visited, int depth) {
        if (a.size() != b.size()) {
            return at(path) + "expected map size <%d> but was <%d>".formatted(b.size(), a.size());
        }
        for (var entry : b.entrySet()) {
            if (!a.containsKey(entry.getKey())) {
                return at(path) + "missing entry for key <%s>".formatted(entry.getKey());
            }
            var result = compare(
                    path + "[" + entry.getKey() + "]",
                    a.get(entry.getKey()),
                    entry.getValue(),
                    ignored,
                    visited,
                    depth - 1);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static String compareList(
            String path, List<?> a, List<?> b, Set<String> ignored, Set<RefPair> visited, int depth) {
        if (a.size() != b.size()) {
            return at(path) + "expected size <%d> but was <%d>".formatted(b.size(), a.size());
        }
        for (int i = 0; i < a.size(); i++) {
            var result = compare(path + "[" + i + "]", a.get(i), b.get(i), ignored, visited, depth - 1);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static boolean isLeaf(Object value) {
        return value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>
                || value.getClass().getName().startsWith("java.")
                || value.getClass().getName().startsWith("javax.");
    }

    private static List<Field> fields(Class<?> type) {
        var list = new ArrayList<Field>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (var field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                field.setAccessible(true);
                list.add(field);
            }
        }
        return list;
    }

    private static List<Object> toList(Iterable<?> iterable) {
        var list = new ArrayList<Object>();
        for (Object element : iterable) {
            list.add(element);
        }
        return list;
    }

    private static List<Object> arrayToList(Object array) {
        int length = Array.getLength(array);
        var list = new ArrayList<Object>(length);
        for (int i = 0; i < length; i++) {
            list.add(Array.get(array, i));
        }
        return list;
    }

    private static String mismatch(String path, Object a, Object b) {
        return at(path) + "expected <%s> but was <%s>".formatted(b, a);
    }

    private static String at(String path) {
        return path.isEmpty() ? "" : path + ": ";
    }

    private record RefPair(Object a, Object b) {
        @Override
        public boolean equals(Object other) {
            return other instanceof RefPair pair && pair.a == a && pair.b == b;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(a) * 31 + System.identityHashCode(b);
        }
    }
}
