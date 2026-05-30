package su.kidoz.axiomj.engine;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import su.kidoz.axiomj.api.Fact;
import su.kidoz.axiomj.api.Property;

public final class ClasspathScanner {

    private ClasspathScanner() {}

    public static List<String> scan(RunConfig config) {
        List<String> discoveredClasses = new ArrayList<>();
        String classpath = System.getProperty("java.class.path");
        String[] paths = classpath.split(File.pathSeparator);

        for (String path : paths) {
            File file = new File(path);
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
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(root, file, config, discoveredClasses);
            } else if (file.getName().endsWith(".class")) {
                String className = getClassName(root, file);
                if (shouldInclude(className, config)) {
                    inspectClass(className, discoveredClasses);
                }
            }
        }
    }

    private static void scanJar(File jar, RunConfig config, List<String> discoveredClasses) {
        try (JarFile jarFile = new JarFile(jar)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    String className = entry.getName()
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
        String rootPath = root.getAbsolutePath();
        String classPath = classFile.getAbsolutePath();
        String relativePath = classPath.substring(rootPath.length() + 1);
        return relativePath.replace(File.separatorChar, '.').substring(0, relativePath.length() - 6);
    }

    private static boolean shouldInclude(String className, RunConfig config) {
        // Module info should be skipped
        if (className.equals("module-info") || className.endsWith(".module-info")) {
            return false;
        }

        boolean included = config.includePackages().isEmpty();
        for (String pkg : config.includePackages()) {
            if (className.startsWith(pkg + ".") || className.equals(pkg)) {
                included = true;
                break;
            }
        }

        if (!included) return false;

        for (String pkg : config.excludePackages()) {
            if (className.startsWith(pkg + ".") || className.equals(pkg)) {
                return false;
            }
        }

        return true;
    }

    private static void inspectClass(String className, List<String> discoveredClasses) {
        try {
            Class<?> clazz = Class.forName(className, false, ClasspathScanner.class.getClassLoader());
            // Ignore abstract classes or interfaces for test discovery
            if (clazz.isInterface() || java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                return;
            }

            for (Method method : clazz.getDeclaredMethods()) {
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
