package hyphanet.support;

import com.machinezoo.noexception.Exceptions;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A utility class that provides caching class loading and object instantiation capabilities.
 *
 * @author Brandon Wiley
 * @author oskar
 */
public final class Loader {

    private static final ConcurrentMap<String, Class<?>> classes = new ConcurrentHashMap<>();

    private Loader() {
        // Prevent instantiation
    }

    /**
     * Loads and caches a class by its name.
     *
     * @param name The fully qualified name of the class to load
     *
     * @return The loaded Class object
     *
     * @throws ClassNotFoundException if the class cannot be found
     * @throws NullPointerException   if name is null
     */
    @SuppressWarnings("RedundantThrows")
    public static Class<?> load(String name) throws ClassNotFoundException {
        return classes.computeIfAbsent(name, Exceptions.sneak().function(Class::forName));
    }

    /**
     * Creates a new instance of the specified class using its default constructor.
     *
     * @param classname The fully qualified name of the class to instantiate
     *
     * @return A new instance of the specified class
     *
     * @throws InvocationTargetException if the underlying constructor throws an exception
     * @throws NoSuchMethodException     if a matching constructor is not found
     * @throws InstantiationException    if the class cannot be instantiated
     * @throws IllegalAccessException    if the constructor cannot be accessed
     * @throws ClassNotFoundException    if the class cannot be found
     * @throws NullPointerException      if classname is null
     */
    public static Object getInstance(String classname)
        throws InvocationTargetException, NoSuchMethodException, InstantiationException,
               IllegalAccessException, ClassNotFoundException {
        return getInstance(classname, new Class<?>[]{}, new Object[]{});
    }

    /**
     * Creates a new instance of the specified class using a constructor matching the provided
     * argument types.
     *
     * @param classname The fully qualified name of the class to instantiate
     * @param argTypes  The classes of the constructor arguments
     * @param args      The constructor arguments
     *
     * @return A new instance of the specified class
     *
     * @throws InvocationTargetException if the underlying constructor throws an exception
     * @throws NoSuchMethodException     if a matching constructor is not found
     * @throws InstantiationException    if the class cannot be instantiated
     * @throws IllegalAccessException    if the constructor cannot be accessed
     * @throws ClassNotFoundException    if the class cannot be found
     * @throws NullPointerException      if any parameter is null
     */
    public static Object getInstance(String classname, Class<?>[] argTypes, Object[] args)
        throws InvocationTargetException, NoSuchMethodException, InstantiationException,
               IllegalAccessException, ClassNotFoundException {
        Objects.requireNonNull(classname, "Class name cannot be null");
        Objects.requireNonNull(argTypes, "Argument types array cannot be null");
        Objects.requireNonNull(args, "Arguments array cannot be null");
        return getInstance(load(classname), argTypes, args);
    }

    /**
     * Creates a new instance of the specified class using a constructor matching the provided
     * argument types.
     *
     * @param clazz    The class to instantiate
     * @param argTypes The classes of the constructor arguments
     * @param args     The constructor arguments
     *
     * @return A new instance of the specified class
     *
     * @throws InvocationTargetException if the underlying constructor throws an exception
     * @throws NoSuchMethodException     if a matching constructor is not found
     * @throws InstantiationException    if the class cannot be instantiated
     * @throws IllegalAccessException    if the constructor cannot be accessed
     * @throws NullPointerException      if any parameter is null
     */
    public static Object getInstance(Class<?> clazz, Class<?>[] argTypes, Object[] args)
        throws InvocationTargetException, NoSuchMethodException, InstantiationException,
               IllegalAccessException {
        Objects.requireNonNull(clazz, "Class cannot be null");
        Objects.requireNonNull(argTypes, "Argument types array cannot be null");
        Objects.requireNonNull(args, "Arguments array cannot be null");

        if (argTypes.length != args.length) {
            throw new IllegalArgumentException(
                "Argument types and arguments arrays must have the same length");
        }

        Constructor<?> constructor = clazz.getConstructor(argTypes);
        return constructor.newInstance(args);
    }
}
