package su.kidoz.axiomj.di;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;

/** Read-only view over configuration properties loaded by {@code @UseConfig}; injectable into tests. */
public final class Config {
    private final Properties properties;

    public Config(Properties properties) {
        this.properties = properties;
    }

    public Optional<String> find(String key) {
        return Optional.ofNullable(properties.getProperty(key));
    }

    public String get(String key) {
        var value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("Missing config key: " + key);
        }
        return value;
    }

    public String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    public long getLong(String key) {
        return Long.parseLong(get(key));
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key));
    }

    public double getDouble(String key) {
        return Double.parseDouble(get(key));
    }

    public <T> T getRecord(String keyPrefix, Class<T> recordType) {
        if (!recordType.isRecord()) {
            throw new IllegalArgumentException(recordType.getName() + " is not a record type");
        }
        try {
            var components = recordType.getRecordComponents();
            var types = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
            var args = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                var propKey = keyPrefix + "." + components[i].getName();
                var type = types[i];
                if (type == String.class) args[i] = get(propKey);
                else if (type == int.class || type == Integer.class) args[i] = getInt(propKey);
                else if (type == long.class || type == Long.class) args[i] = getLong(propKey);
                else if (type == boolean.class || type == Boolean.class) args[i] = getBoolean(propKey);
                else if (type == double.class || type == Double.class) args[i] = getDouble(propKey);
                else throw new IllegalArgumentException("Unsupported record component type: " + type);
            }
            var constructor = recordType.getDeclaredConstructor(types);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to coerce config into " + recordType.getName(), e);
        }
    }
}
