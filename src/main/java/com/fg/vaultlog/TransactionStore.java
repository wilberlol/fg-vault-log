package com.fg.vaultlog;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;

/** Single-threaded SQLite access so economy calls never wait on database I/O. */
public final class TransactionStore implements AutoCloseable {
    private static final AtomicInteger THREAD_ID = new AtomicInteger();

    private final Logger logger;
    private final File databaseFile;
    private ExecutorService executor;
    private Connection connection;

    public TransactionStore(JavaPlugin plugin, File databaseFile) {
        this(plugin.getLogger(), databaseFile);
    }

    public TransactionStore(Logger logger, File databaseFile) {
        this.logger = logger;
        this.databaseFile = databaseFile;
    }

    public void open() throws SQLException, InterruptedException, ExecutionException, TimeoutException {
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new SQLException("無法建立資料夾: " + parent);
        }

        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "FGVaultLog-SQLite-" + THREAD_ID.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });

        Future<?> initialized = executor.submit(() -> {
            try {
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
                initializeSchema(connection);
            } catch (ReflectiveOperationException | SQLException ex) {
                throw new StoreInitializationException(ex);
            }
        });
        try {
            initialized.get(15, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException ex) {
            close();
            throw ex;
        }
    }

    public File databaseFile() {
        return databaseFile;
    }

    public void record(VaultTransaction transaction) {
        ExecutorService current = executor;
        if (current == null || current.isShutdown()) {
            return;
        }
        try {
            current.execute(() -> insertSafely(transaction));
        } catch (RejectedExecutionException ignored) {
            // A server shutdown can race with the last provider callback; the provider must still return normally.
        }
    }

    public CompletableFuture<List<VaultTransaction>> queryLatest(String accountQuery, int limit, int offset) {
        ExecutorService current = executor;
        if (current == null || current.isShutdown()) {
            return CompletableFuture.failedFuture(new IllegalStateException("資料庫尚未開啟或已關閉"));
        }

        CompletableFuture<List<VaultTransaction>> future = new CompletableFuture<>();
        try {
            current.execute(() -> querySafely(accountQuery, limit, offset, future));
        } catch (RejectedExecutionException ex) {
            future.completeExceptionally(ex);
        }
        return future;
    }

    private void insertSafely(VaultTransaction transaction) {
        String sql = "INSERT INTO transactions ("
                + "id, occurred_at, operation, event_name, account_type, account_id, account_name, world, "
                + "requested_amount, amount, balance_before, balance_after, success, response_type, "
                + "message, provider, source_plugin, source_class"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, transaction.id());
            statement.setString(index++, transaction.occurredAt().toString());
            statement.setString(index++, transaction.operation().name());
            statement.setString(index++, transaction.eventName());
            statement.setString(index++, transaction.accountType().name());
            statement.setString(index++, transaction.accountId());
            setNullableString(statement, index++, transaction.accountName());
            setNullableString(statement, index++, transaction.world());
            statement.setDouble(index++, transaction.requestedAmount());
            statement.setDouble(index++, transaction.amount());
            setNullableDouble(statement, index++, transaction.balanceBefore());
            setNullableDouble(statement, index++, transaction.balanceAfter());
            statement.setBoolean(index++, transaction.success());
            statement.setString(index++, transaction.responseType());
            setNullableString(statement, index++, transaction.message());
            statement.setString(index++, transaction.provider());
            statement.setString(index++, transaction.sourcePlugin());
            statement.setString(index, transaction.sourceClass());
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "寫入 Vault 金流紀錄失敗", ex);
        }
    }

    private void querySafely(String accountQuery, int limit, int offset,
                             CompletableFuture<List<VaultTransaction>> future) {
        String normalized = accountQuery == null ? "" : accountQuery.trim();
        boolean all = normalized.isEmpty() || normalized.equals("*");
        String sql = "SELECT id, occurred_at, operation, event_name, account_type, account_id, account_name, world, "
                + "requested_amount, amount, balance_before, balance_after, success, response_type, message, "
                + "provider, source_plugin, source_class FROM transactions "
                + (all ? "" : "WHERE account_id LIKE ? COLLATE NOCASE OR account_name LIKE ? COLLATE NOCASE ")
                + "ORDER BY occurred_at DESC, rowid DESC LIMIT ? OFFSET ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (!all) {
                String pattern = "%" + normalized + "%";
                statement.setString(index++, pattern);
                statement.setString(index++, pattern);
            }
            statement.setInt(index++, Math.max(1, Math.min(50, limit)));
            statement.setInt(index, Math.max(0, offset));

            List<VaultTransaction> result = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(readRow(rows));
                }
            }
            future.complete(result);
        } catch (SQLException | RuntimeException ex) {
            future.completeExceptionally(ex);
        }
    }

    private VaultTransaction readRow(ResultSet rows) throws SQLException {
        return new VaultTransaction(
                rows.getString("id"),
                Instant.parse(rows.getString("occurred_at")),
                TransactionOperation.valueOf(rows.getString("operation")),
                rows.getString("event_name"),
                AccountType.valueOf(rows.getString("account_type")),
                rows.getString("account_id"),
                rows.getString("account_name"),
                rows.getString("world"),
                rows.getDouble("requested_amount"),
                rows.getDouble("amount"),
                nullableDouble(rows, "balance_before"),
                nullableDouble(rows, "balance_after"),
                rows.getBoolean("success"),
                rows.getString("response_type"),
                rows.getString("message"),
                rows.getString("provider"),
                rows.getString("source_plugin"),
                rows.getString("source_class")
        );
    }

    private static Double nullableDouble(ResultSet rows, String column) throws SQLException {
        double value = rows.getDouble(column);
        return rows.wasNull() ? null : value;
    }

    private static void initializeSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS transactions ("
                    + "id TEXT PRIMARY KEY,"
                    + "occurred_at TEXT NOT NULL,"
                    + "operation TEXT NOT NULL,"
                    + "event_name TEXT NOT NULL DEFAULT 'unknown',"
                    + "account_type TEXT NOT NULL,"
                    + "account_id TEXT NOT NULL,"
                    + "account_name TEXT,"
                    + "world TEXT,"
                    + "requested_amount REAL NOT NULL,"
                    + "amount REAL NOT NULL,"
                    + "balance_before REAL,"
                    + "balance_after REAL,"
                    + "success INTEGER NOT NULL,"
                    + "response_type TEXT NOT NULL,"
                    + "message TEXT,"
                    + "provider TEXT NOT NULL,"
                    + "source_plugin TEXT NOT NULL,"
                    + "source_class TEXT NOT NULL"
                    + ")");
            ensureEventColumn(statement);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_transactions_account "
                    + "ON transactions(account_id, occurred_at DESC)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_transactions_time "
                    + "ON transactions(occurred_at DESC)");
        }
    }

    private static void ensureEventColumn(Statement statement) throws SQLException {
        boolean exists = false;
        try (ResultSet columns = statement.executeQuery("PRAGMA table_info(transactions)")) {
            while (columns.next()) {
                if ("event_name".equalsIgnoreCase(columns.getString("name"))) {
                    exists = true;
                    break;
                }
            }
        }
        if (!exists) {
            statement.executeUpdate("ALTER TABLE transactions ADD COLUMN event_name "
                    + "TEXT NOT NULL DEFAULT 'unknown'");
        }
    }

    private static void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static void setNullableDouble(PreparedStatement statement, int index, Double value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.REAL);
        } else {
            statement.setDouble(index, value);
        }
    }

    @Override
    public void close() {
        ExecutorService current = executor;
        if (current == null) {
            return;
        }
        try {
            Future<?> closeTask = current.submit(() -> {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException ex) {
                        logger.log(Level.WARNING, "關閉 Vault 金流資料庫失敗", ex);
                    }
                }
            });
            current.shutdown();
            closeTask.get(10, TimeUnit.SECONDS);
            current.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            current.shutdownNow();
        } catch (ExecutionException | TimeoutException ex) {
            current.shutdownNow();
            logger.log(Level.WARNING, "等待 Vault 金流資料庫關閉逾時", ex);
        } finally {
            executor = null;
            connection = null;
        }
    }

    private static final class StoreInitializationException extends RuntimeException {
        private StoreInitializationException(Throwable cause) {
            super(cause);
        }
    }
}
