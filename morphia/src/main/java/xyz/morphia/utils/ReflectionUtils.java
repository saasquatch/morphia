package xyz.morphia.utils;


import xyz.morphia.logging.Logger;
import xyz.morphia.logging.MorphiaLoggerFactory;
import xyz.morphia.mapping.MappingException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import static java.lang.Class.forName;


/**
 * Various reflection utility methods, used mainly in the Mapper.
 *
 * @author Olafur Gauti Gudmundsson
 */
public final class ReflectionUtils {
    private static final Logger LOG = MorphiaLoggerFactory.get(ReflectionUtils.class);


    private ReflectionUtils() {
    }

    //    public static boolean implementsAnyInterface(final Class type, final Class... interfaceClasses)
    //    {
    //        for (Class iF : interfaceClasses)
    //        {
    //            if (implementsInterface(type, iF))
    //            {
    //                return true;
    //            }
    //        }
    //        return false;
    //    }

    /**
     * Returns the classes in a package
     *
     * @param packageName    the package to scan
     * @param mapSubPackages whether to map the sub-packages while scanning
     * @return the list of classes
     */
    public static Set<Class> getClasses(final String packageName, final boolean mapSubPackages) {
        final ClassLoader loader = Thread.currentThread()
                                         .getContextClassLoader();
        return getClasses(loader, packageName, mapSubPackages);
    }

    /**
     * Returns the classes in a package
     *
     * @param loader         the ClassLoader to use
     * @param packageName    the package to scan
     * @param mapSubPackages whether to map the sub-packages while scanning
     * @return the list of classes
     */
    private static Set<Class> getClasses(final ClassLoader loader, final String packageName, final boolean mapSubPackages) {
        final Set<Class> classes = new HashSet<>();
        final String path = packageName.replace('.', '/');
        try {
            final Enumeration<URL> resources = loader.getResources(path);
            if (resources != null) {
                while (resources.hasMoreElements()) {
                    String filePath = resources.nextElement()
                                               .getFile();
                    // WINDOWS HACK
                    if (filePath.indexOf("%20") > 0) {
                        filePath = filePath.replaceAll("%20", " ");
                    }
                    // # in the jar name
                    if (filePath.indexOf("%23") > 0) {
                        filePath = filePath.replaceAll("%23", "#");
                    }

                    if ((filePath.indexOf("!") > 0) && (filePath.indexOf(".jar") > 0)) {
                        String jarPath = filePath.substring(0, filePath.indexOf("!"))
                                                 .substring(filePath.indexOf(":") + 1);
                        // WINDOWS HACK
                        if (jarPath.contains(":")) {
                            jarPath = jarPath.substring(1);
                        }
                        classes.addAll(getFromJARFile(loader, jarPath, path, mapSubPackages));
                    } else {
                        classes.addAll(getFromDirectory(loader, new File(filePath), packageName, mapSubPackages));
                    }
                }
            }
        } catch (ReflectiveOperationException | IOException e) {
            throw new MappingException(e.getMessage(), e);
        }
        return classes;
    }

    /**
     * Returns the classes in a package found in a jar
     *
     * @param loader         the ClassLoader to use
     * @param jar            the jar to scan
     * @param packageName    the package to scan
     * @param mapSubPackages whether to map the sub-packages while scanning
     * @return the list of classes
     * @throws IOException            thrown if an error is encountered scanning packages
     */
    private static Set<Class<?>> getFromJARFile(final ClassLoader loader, final String jar, final String packageName, final boolean
                                                                                                                          mapSubPackages)
        throws IOException, ClassNotFoundException {
        final Set<Class<?>> classes = new HashSet<>();
        try (JarInputStream jarFile = new JarInputStream(new FileInputStream(jar))) {
            JarEntry jarEntry;
            do {
                jarEntry = jarFile.getNextJarEntry();
                if (jarEntry != null) {
                    String className = jarEntry.getName();
                    if (className.endsWith(".class")) {
                        String classPackageName = getPackageName(className);
                        if (classPackageName.equals(packageName) || (mapSubPackages && isSubPackage(classPackageName, packageName))) {
                            className = stripFilenameExtension(className);
                            classes.add(forName(className.replace('/', '.'), true, loader));
                        }
                    }
                }
            } while (jarEntry != null);
        }
        return classes;
    }

    /**
     * Returns the classes in a package found in a directory
     *
     * @param loader         the ClassLoader to use
     * @param directory      the directory to scan
     * @param packageName    the package to scan
     * @param mapSubPackages whether to map the sub-packages while scanning
     * @return the list of classes
     * @throws ClassNotFoundException thrown if a class can not be found
     */
    private static Set<Class<?>> getFromDirectory(final ClassLoader loader, final File directory, final String packageName,
                                                  final boolean mapSubPackages) throws ClassNotFoundException {
        final Set<Class<?>> classes = new HashSet<>();
        if (directory.exists()) {
            for (final String file : getFileNames(directory, packageName, mapSubPackages)) {
                if (file.endsWith(".class")) {
                    final String name = stripFilenameExtension(file);
                    final Class<?> clazz = forName(name, true, loader);
                    classes.add(clazz);
                }
            }
        }
        return classes;
    }

    private static Set<String> getFileNames(final File directory, final String packageName, final boolean mapSubPackages) {
        Set<String> fileNames = new HashSet<>();
        final File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    fileNames.add(packageName + '.' + file.getName());
                } else if (mapSubPackages) {
                    fileNames.addAll(getFileNames(file, packageName + '.' + file.getName(), true));
                }
            }
        }
        return fileNames;
    }

    private static String getPackageName(final String filename) {
        return filename.contains("/") ? filename.substring(0, filename.lastIndexOf('/')) : filename;
    }

    private static String stripFilenameExtension(final String filename) {
        if (filename.indexOf('.') != -1) {
            return filename.substring(0, filename.lastIndexOf('.'));
        } else {
            return filename;
        }
    }

    private static boolean isSubPackage(final String fullPackageName, final String parentPackageName) {
        return fullPackageName.startsWith(parentPackageName);
    }


    /**
     * Get the underlying class for a type, or null if the type is a variable type.
     *
     * @param type the type
     * @return the underlying class
     */
    public static Class<?> getClass(final Type type) {
        if (type instanceof Class) {
            return (Class) type;
        } else if (type instanceof ParameterizedType) {
            return getClass(((ParameterizedType) type).getRawType());
        } else if (type instanceof GenericArrayType) {
            final Type componentType = ((GenericArrayType) type).getGenericComponentType();
            final Class<?> componentClass = getClass(componentType);
            if (componentClass != null) {
                return Array.newInstance(componentClass, 0).getClass();
            } else {
                LOG.debug("************ ReflectionUtils.getClass 1st else");
                LOG.debug("************ type = " + type);
                return null;
            }
        } else {
            LOG.debug("************ ReflectionUtils.getClass final else");
            LOG.debug("************ type = " + type);
            return null;
        }
    }

    /**
     * @param type the class to check
     * @return true is the class is not a concrete type
     */
    public static boolean isConcrete(final Class type) {
        Class componentType = type;
        if (type.isArray()) {
            componentType = type.getComponentType();
        }
        return !componentType.isInterface() && !Modifier.isAbstract(componentType.getModifiers());
    }
}
