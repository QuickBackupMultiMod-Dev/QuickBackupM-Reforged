package io.github.skydynamic.quickbackupmulti.database;

/**
 * Resolves H2 MVStore DataType class names written by both current and 3.3.1 relocated jars.
 */
public final class H2ClassNameCompat {
    private static final String REPACK_PREFIX = "io.github.skydynamic.quickbackupmulti.repack.org.h2";
    private static final String H2_PREFIX = "org.h2";

    private H2ClassNameCompat() {
    }

    public static Class<?> forName(String name) throws ClassNotFoundException {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException original) {
            if (name != null && name.startsWith(REPACK_PREFIX)) {
                String remapped = H2_PREFIX + name.substring(REPACK_PREFIX.length());
                try {
                    return Class.forName(remapped);
                } catch (ClassNotFoundException remappedFailure) {
                    remappedFailure.addSuppressed(original);
                    throw remappedFailure;
                }
            }
            throw original;
        }
    }
}
