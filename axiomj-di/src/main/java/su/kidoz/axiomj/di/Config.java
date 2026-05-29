package su.kidoz.axiomj.di;

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
}
