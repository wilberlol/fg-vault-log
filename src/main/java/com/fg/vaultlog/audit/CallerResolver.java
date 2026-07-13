package com.fg.vaultlog.audit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/** Best-effort source plugin attribution for a Vault API call. */
public final class CallerResolver {
    private final Plugin owner;
    private final Map<String, Source> cache = new ConcurrentHashMap<>();

    public CallerResolver(Plugin owner) {
        this.owner = owner;
    }

    public Source resolve() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        PluginManager pluginManager;
        try {
            pluginManager = Bukkit.getPluginManager();
        } catch (RuntimeException ex) {
            return Source.UNKNOWN;
        }
        if (pluginManager == null) {
            return Source.UNKNOWN;
        }
        Plugin[] plugins = pluginManager.getPlugins();
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            if (isInternal(className)) {
                continue;
            }
            Source cached = cache.get(className);
            if (cached != null) {
                return cached.withCallSite(element);
            }
            for (Plugin plugin : plugins) {
                if (plugin == owner) {
                    continue;
                }
                try {
                    Class.forName(className, false, plugin.getClass().getClassLoader());
                    Source source = new Source(plugin.getName(), className);
                    cache.put(className, source);
                    return source.withCallSite(element);
                } catch (ClassNotFoundException | LinkageError ignored) {
                    // The stack can contain CraftBukkit and library classes that are not owned by a plugin.
                }
            }
        }
        return Source.UNKNOWN;
    }

    private static boolean isInternal(String className) {
        return className.equals(Thread.class.getName())
                || className.startsWith("com.fg.vaultlog.")
                || className.startsWith("org.bukkit.")
                || className.startsWith("net.milkbowl.vault.")
                || className.startsWith("io.papermc.")
                || className.startsWith("com.destroystokyo.paper.");
    }

    public record Source(String plugin, String className, String callSite) {
        public static final Source UNKNOWN = new Source("unknown", "unknown", "unknown");

        private Source(String plugin, String className) {
            this(plugin, className, className);
        }

        public Source withCallSite(StackTraceElement element) {
            return new Source(plugin, className, element.getClassName() + "#" + element.getMethodName());
        }
    }
}
