package com.fg.vaultlog;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.PluginManager;

/** Optional CMI native-event adapter. It uses reflection so CMI remains an optional dependency. */
public final class CmiBalanceBridge {
    private static final String EVENT_CLASS_NAME = "com.Zrips.CMI.events.CMIUserBalanceChangeEvent";

    private final FGVaultLogPlugin plugin;
    private final TransactionStore store;
    private final TransactionDeduplicator deduplicator;
    private final BalanceSnapshotMonitor snapshotMonitor;

    private Listener listener;
    private boolean registered;

    public CmiBalanceBridge(FGVaultLogPlugin plugin, TransactionStore store,
                            TransactionDeduplicator deduplicator,
                            BalanceSnapshotMonitor snapshotMonitor) {
        this.plugin = plugin;
        this.store = store;
        this.deduplicator = deduplicator;
        this.snapshotMonitor = snapshotMonitor;
    }

    public void start() {
        if (!plugin.currentConfig().cmiEnabled()) {
            return;
        }

        PluginManager pluginManager = plugin.getServer().getPluginManager();
        Plugin cmi = pluginManager.getPlugin("CMI");
        if (cmi == null || !cmi.isEnabled()) {
            plugin.getLogger().info("CMI not found; native CMI balance event logging is disabled.");
            return;
        }

        try {
            Class<?> rawEventClass = Class.forName(EVENT_CLASS_NAME, true, cmi.getClass().getClassLoader());
            if (!Event.class.isAssignableFrom(rawEventClass)) {
                throw new IllegalStateException(EVENT_CLASS_NAME + " is not a Bukkit Event");
            }
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>) rawEventClass;
            listener = new Listener() {
            };
            EventExecutor executor = (ignored, event) -> handle(event);
            pluginManager.registerEvent(eventClass, listener, EventPriority.MONITOR, executor, plugin, false);
            registered = true;
            plugin.getLogger().info("CMI native balance event bridge registered.");
        } catch (ClassNotFoundException | LinkageError | RuntimeException ex) {
            registered = false;
            listener = null;
            plugin.getLogger().log(Level.WARNING,
                    "Unable to register CMIUserBalanceChangeEvent; CMI native balance changes will not be logged.", ex);
        }
    }

    public void reload() {
        stop();
        start();
    }

    public void stop() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        registered = false;
    }

    public boolean isRegistered() {
        return registered;
    }

    private void handle(Event event) {
        try {
            Object user = invoke(event, "getUser");
            if (user == null) {
                return;
            }

            double from = number(invoke(event, "getFrom"));
            double to = number(invoke(event, "getTo"));
            double delta = to - from;
            if (!Double.isFinite(delta) || Math.abs(delta) < 1.0E-6) {
                return;
            }

            UUID uuid = asUuid(invokeIfPresent(user, "getUniqueId"));
            if (uuid == null) {
                uuid = asUuid(invokeIfPresent(user, "getUuid"));
            }
            String name = asString(invokeIfPresent(user, "getName"));
            String accountId = uuid == null ? name : uuid.toString();
            if (accountId == null || accountId.isBlank()) {
                return;
            }

            if (deduplicator.consume(accountId, name, delta)) {
                snapshotMonitor.observe(accountId, name, to);
                return;
            }

            String actionType = defaultValue(asString(invokeIfPresent(event, "getActionType")), "unknown");
            Object source = invokeIfPresent(event, "getSource");
            String sourceName = source == null
                    ? "unknown"
                    : defaultValue(asString(invokeIfPresent(source, "getName")), "unknown");
            String world = worldName(invokeIfPresent(user, "getWorld"));
            String eventName = "CMIUserBalanceChangeEvent[actionType=" + actionType + "]";
            String message = "from=" + from + "; to=" + to
                    + "; actionType=" + actionType + "; sourceUser=" + sourceName;

            store.record(new VaultTransaction(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    TransactionOperation.BALANCE_CHANGE,
                    eventName,
                    AccountType.PLAYER,
                    accountId,
                    name,
                    world,
                    delta,
                    delta,
                    from,
                    to,
                    true,
                    "CMI_EVENT",
                    message,
                    "CMI",
                    "CMI",
                    EVENT_CLASS_NAME
            ));
            snapshotMonitor.observe(accountId, name, to);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to record a CMI balance event.", ex);
        }
    }

    private static Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("CMI event method unavailable: " + methodName, ex);
        }
    }

    private static Object invokeIfPresent(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            return null;
        }
    }

    private static double number(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("CMI balance event returned a non-number balance");
        }
        return number.doubleValue();
    }

    private static UUID asUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String string) {
            try {
                return UUID.fromString(string);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String worldName(Object value) {
        if (value instanceof World world) {
            return world.getName();
        }
        return value == null ? null : String.valueOf(value);
    }
}
