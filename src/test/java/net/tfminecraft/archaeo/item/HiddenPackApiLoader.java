package net.tfminecraft.archaeo.item;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines a fresh copy of Archaeo's {@code item} classes whose view of the classpath hides the given
 * packages, as on a server where a pack plugin is enabled but its API classes are missing or moved.
 * {@link #replacing} swaps a pack class for different bytecode, as in an incompatible build that
 * reuses a class name for another type. Everything else comes from the normal test class loader.
 */
final class HiddenPackApiLoader extends ClassLoader {
    private static final String OWN = "net.tfminecraft.archaeo.item.";
    private final List<String> hidden;
    private final Map<String, byte[]> replaced = new HashMap<>();

    HiddenPackApiLoader(String... hiddenPrefixes) {
        super(HiddenPackApiLoader.class.getClassLoader());
        this.hidden = List.of(hiddenPrefixes);
    }

    /**
     * @param name binary class name the item copies will see
     * @param bytecode class file defined under that name instead of the real one
     * @return this loader
     */
    HiddenPackApiLoader replacing(String name, byte[] bytecode) {
        replaced.put(name, bytecode.clone());
        return this;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            byte[] substitute = replaced.get(name);
            if (substitute == null) {
                for (String prefix : hidden) {
                    if (name.startsWith(prefix)) throw new ClassNotFoundException(name);
                }
                if (!name.startsWith(OWN) || name.startsWith(OWN + "HiddenPackApiLoader")) return super.loadClass(name, resolve);
            }
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null && substitute != null) {
                loaded = defineClass(name, substitute, 0, substitute.length);
            } else if (loaded == null) {
                try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                    if (in == null) throw new ClassNotFoundException(name);
                    byte[] bytes = in.readAllBytes();
                    // Same code source as the real item classes, so the copies are Archaeo's own code in
                    // everything except which pack API classes they can see.
                    loaded = defineClass(name, bytes, 0, bytes.length, ItemMatcher.class.getProtectionDomain());
                } catch (IOException exception) {
                    throw new ClassNotFoundException(name, exception);
                }
            }
            if (resolve) resolveClass(loaded);
            return loaded;
        }
    }
}
