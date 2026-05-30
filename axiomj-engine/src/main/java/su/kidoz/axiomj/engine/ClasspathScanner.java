package su.kidoz.axiomj.engine;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import su.kidoz.axiomj.api.Fact;
import su.kidoz.axiomj.api.Property;

public final class ClasspathScanner {

    private ClasspathScanner() {}

    public static List<String> scan(RunConfig config) {
        var discoveredClasses = new ArrayList<String>();
        var classpath = System.getProperty("java.class.path");
        var paths = classpath.split(File.pathSeparator);

        for (var path : paths) {
            var file = new File(path);
            if (!file.exists()) continue;

            if (file.isDirectory()) {
                scanDirectory(file, file, config, discoveredClasses);
            } else if (file.getName().endsWith(".jar")) {
                scanJar(file, config, discoveredClasses);
            }
        }

        return discoveredClasses;
    }

    private static void scanDirectory(File root, File dir, RunConfig config, List<String> discoveredClasses) {
        var files = dir.listFiles();
        if (files == null) return;

        for (var file : files) {
            if (file.isDirectory()) {
                scanDirectory(root, file, config, discoveredClasses);
            } else if (file.getName().endsWith(".class")) {
                var className = getClassName(root, file);
                if (shouldInclude(className, config)) {
                    inspectClass(className, discoveredClasses);
                }
            }
        }
    }

    private static void scanJar(File jar, RunConfig config, List<String> discoveredClasses) {
        try (var jarFile = new JarFile(jar)) {
            var entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    var className = entry.getName()
                            .replace('/', '.')
                            .substring(0, entry.getName().length() - 6);
                    if (shouldInclude(className, config)) {
                        inspectClass(className, discoveredClasses);
                    }
                }
            }
        } catch (IOException _) {
            // Ignore unreadable jars
        }
    }

    private static String getClassName(File root, File classFile) {
        var rootPath = root.getAbsolutePath();
        var classPath = classFile.getAbsolutePath();
        var relativePath = classPath.substring(rootPath.length() + 1);
        return relativePath.replace(File.separatorChar, '.').substring(0, relativePath.length() - 6);
    }

    private static boolean shouldInclude(String className, RunConfig config) {
        // Module info should be skipped
        if (className.equals("module-info") || className.endsWith(".module-info")) {
            return false;
        }

        boolean included = config.includePackages().isEmpty();
        for (var pkg : config.includePackages()) {
            if (className.startsWith(pkg + ".") || className.equals(pkg)) {
                included = true;
                break;
            }
        }

        if (!included) return false;

        for (var pkg : config.excludePackages()) {
            if (className.startsWith(pkg + ".") || className.equals(pkg)) {
                return false;
            }
        }

        return true;
    }

    private static void inspectClass(String className, List<String> discoveredClasses) {
        try {
            var clazz = Class.forName(className, false, ClasspathScanner.class.getClassLoader());
            // Ignore abstract classes or interfaces for test discovery
            if (clazz.isInterface() || java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                return;
            }

            for (var method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Fact.class) || method.isAnnotationPresent(Property.class)) {
                    discoveredClasses.add(className);
                    return;
                }
            }
        } catch (Throwable _) {
            // Ignore classes that cannot be loaded (e.g. missing dependencies)
        }
    }
}
