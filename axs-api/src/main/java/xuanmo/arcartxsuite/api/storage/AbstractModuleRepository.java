package xuanmo.arcartxsuite.api.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 模块存储层统一基类。
 * <p>
 * 封装 HikariCP 连接池初始化（MySQL / SQLite 双方言）、生命周期管理、
 * 以及玩家数据删除模板。子类只需关注建表逻辑和业务 SQL。
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * public final class JdbcMyRepository extends AbstractModuleRepository {
 *     public JdbcMyRepository(File dataFolder, StorageConfig cfg, Logger logger) {
 *         super("AXS-MyModule", dataFolder, cfg.toDescriptor(), logger);
 *     }
 *     @Override protected void onInitialize(Connection conn) throws SQLException { ... }
 *     @Override protected List<String> playerDataTables() { return List.of("axs_my_table"); }
 *     @Override protected String playerUuidColumn() { return "player_uuid"; }
 * }
 * }</pre>
 */
public abstract class AbstractModuleRepository {

    protected final String poolName;
    protected final File dataFolder;
    protected final StorageDescriptor descriptor;
    protected final Logger logger;

    private volatile HikariDataSource dataSource;

    protected AbstractModuleRepository(String poolName, File dataFolder, StorageDescriptor descriptor, Logger logger) {
        this.poolName = poolName;
        this.dataFolder = dataFolder;
        this.descriptor = descriptor;
        this.logger = logger;
    }

    public final StorageDescriptor getDescriptor() {
        return descriptor;
    }

    // ─── 生命周期 ─────────────────────────────────────────────

    /**
     * 初始化连接池并建表。幂等——重复调用安全。
     */
    public final void initialize() throws SQLException {
        if (dataSource != null) {
            return;
        }
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new SQLException("无法创建数据目录: " + dataFolder.getAbsolutePath());
        }

        HikariConfig hc = new HikariConfig();
        hc.setPoolName(poolName);
        hc.setMinimumIdle(1);
        hc.setAutoCommit(true);

        if (descriptor.isMysql()) {
            hc.setMaximumPoolSize(descriptor.poolSize());
            String jdbcUrl = "jdbc:mysql://" + descriptor.host() + ":" + descriptor.port()
                + "/" + descriptor.database()
                + "?useSSL=true&characterEncoding=UTF-8&serverTimezone=UTC";
            hc.setJdbcUrl(jdbcUrl);
            hc.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hc.setUsername(descriptor.username());
            hc.setPassword(descriptor.password());
        } else {
            File sqliteFile = new File(dataFolder, descriptor.sqliteFileName());
            if (!sqliteFile.getParentFile().exists()) {
                sqliteFile.getParentFile().mkdirs();
            }
            hc.setJdbcUrl("jdbc:sqlite:" + sqliteFile.getAbsolutePath());
            hc.setDriverClassName("org.sqlite.JDBC");
            hc.setMaximumPoolSize(1);
            hc.setConnectionTestQuery("SELECT 1");
        }

        dataSource = new HikariDataSource(hc);

        if (!descriptor.isMysql()) {
            configureSqlite();
        }

        try (Connection conn = dataSource.getConnection()) {
            onInitialize(conn);
        }

        logger.info(poolName + " 数据库已初始化 (" + (descriptor.isMysql() ? "MySQL" : "SQLite") + ")");
    }

    /**
     * 关闭连接池。
     */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            dataSource = null;
        }
    }

    public final boolean isAvailable() {
        return dataSource != null && !dataSource.isClosed();
    }

    // ─── 玩家数据删除 ────────────────────────────────────────

    /**
     * 删除指定玩家在该模块的全部数据。
     *
     * @param playerUuid 玩家 UUID
     * @return 总受影响行数
     */
    public final int deletePlayerData(UUID playerUuid) throws SQLException {
        List<String> tables = playerDataTables();
        if (tables == null || tables.isEmpty()) {
            return 0;
        }
        String column = playerUuidColumn();
        int total = 0;
        try (Connection conn = getConnection()) {
            for (String table : tables) {
                String sql = "DELETE FROM " + table + " WHERE " + column + " = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, playerUuid.toString());
                    total += ps.executeUpdate();
                }
            }
        }
        return total;
    }

    /**
     * 清空该模块所有玩家数据表。
     *
     * @return 总受影响行数
     */
    public final int deleteAllPlayerData() throws SQLException {
        List<String> tables = playerDataTables();
        if (tables == null || tables.isEmpty()) {
            return 0;
        }
        int total = 0;
        try (Connection conn = getConnection()) {
            for (String table : tables) {
                try (java.sql.Statement stmt = conn.createStatement()) {
                    total += stmt.executeUpdate("DELETE FROM " + table);
                }
            }
        }
        return total;
    }

    // ─── 子类需实现 ─────────────────────────────────────────

    /**
     * 建表 / 索引逻辑。在首次 {@link #initialize()} 时通过传入的连接调用一次。
     */
    protected abstract void onInitialize(Connection connection) throws SQLException;

    /**
     * 返回该模块存储玩家数据的所有表名。
     * 用于 {@link #deletePlayerData(UUID)} 统一清除。
     * 若模块无玩家数据可返回空列表。
     */
    protected abstract List<String> playerDataTables();

    /**
     * 玩家 UUID 在表中的列名，默认 {@code "player_uuid"}。
     */
    protected String playerUuidColumn() {
        return "player_uuid";
    }

    // ─── 工具方法 ─────────────────────────────────────────────

    /**
     * 获取连接。子类业务方法使用。
     */
    protected final Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException(poolName + " 数据源不可用");
        }
        return dataSource.getConnection();
    }

    /**
     * 判断当前是否为 MySQL 方言。
     */
    protected final boolean isMysql() {
        return descriptor.isMysql();
    }

    /**
     * MySQL 使用 AUTO_INCREMENT，SQLite 使用 AUTOINCREMENT。
     */
    protected final String autoIncrement() {
        return descriptor.isMysql() ? "AUTO_INCREMENT" : "AUTOINCREMENT";
    }

    /**
     * 静默执行 SQL（忽略异常，适用于 CREATE INDEX IF NOT EXISTS 等）。
     */
    protected final void tryExecute(String sql) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    /**
     * 在现有活跃 Connection 句柄上静默执行 SQL。
     */
    protected final void tryExecute(Connection conn, String sql) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    /**
     * 执行单条查询并返回第一行第一列的 int 值，不存在则返回 defaultValue。
     */
    protected final int queryInt(String sql, int defaultValue, Object... params) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : defaultValue;
            }
        }
    }

    private void configureSqlite() {
        tryExecute("PRAGMA journal_mode=WAL");
        tryExecute("PRAGMA synchronous=NORMAL");
        tryExecute("PRAGMA busy_timeout=5000");
    }

    /**
     * 该 Repository 负责的所有数据表名列表，包括业务表和玩家表。
     * 用于跨数据源一键迁移，默认返回 {@link #playerDataTables()}。
     */
    protected List<String> allTables() {
        return playerDataTables();
    }

    /**
     * 跨源一键将当前数据源中的数据，完全克隆/迁移到目标数据源对应的连接池上。
     * <p>
     * 本方法支持 SQLite ↔ MySQL 双向互迁，自动创建目标表结构并支持事务分批透传。
     *
     * @param targetDescriptor 目标数据库描述符
     * @param overwriteTarget  是否覆盖目标表原有数据
     * @return 迁移报告
     */
    public final MigrationResult migrateData(StorageDescriptor targetDescriptor, boolean overwriteTarget) {
        if (!isAvailable()) {
            return new MigrationResult(false, 0, 0);
        }
        List<String> tables = allTables();
        if (tables == null || tables.isEmpty()) {
            return new MigrationResult(true, 0, 0);
        }

        HikariDataSource targetDS = null;
        try {
            // 1. 初始化临时目标数据源
            HikariConfig hc = new HikariConfig();
            hc.setPoolName(poolName + "-TargetTemp");
            hc.setMinimumIdle(1);
            hc.setAutoCommit(false); // 批量提交关闭 autoCommit 提高事务速度

            if (targetDescriptor.isMysql()) {
                hc.setMaximumPoolSize(2);
                String jdbcUrl = "jdbc:mysql://" + targetDescriptor.host() + ":" + targetDescriptor.port()
                    + "/" + targetDescriptor.database()
                    + "?useSSL=true&characterEncoding=UTF-8&serverTimezone=UTC";
                hc.setJdbcUrl(jdbcUrl);
                hc.setDriverClassName("com.mysql.cj.jdbc.Driver");
                hc.setUsername(targetDescriptor.username());
                hc.setPassword(targetDescriptor.password());
            } else {
                File sqliteFile = new File(dataFolder, targetDescriptor.sqliteFileName());
                if (!sqliteFile.getParentFile().exists()) {
                    sqliteFile.getParentFile().mkdirs();
                }
                hc.setJdbcUrl("jdbc:sqlite:" + sqliteFile.getAbsolutePath());
                hc.setDriverClassName("org.sqlite.JDBC");
                hc.setMaximumPoolSize(1);
                hc.setConnectionTestQuery("SELECT 1");
            }
            targetDS = new HikariDataSource(hc);

            // 2. 触发建表与初始化逻辑
            try (Connection conn = targetDS.getConnection()) {
                onInitialize(conn);
                conn.commit();
            }

            int migratedCount = 0;
            long totalRows = 0;
            MigrationResult result = new MigrationResult(true, 0, 0);

            // 3. 逐表数据同步
            for (String table : tables) {
                try {
                    long tableRows = migrateSingleTable(table, overwriteTarget, targetDS);
                    result.addTableRow(table, tableRows);
                    totalRows += tableRows;
                    migratedCount++;
                } catch (Exception e) {
                    result.addError("迁移表 " + table + " 失败: " + e.getMessage());
                }
            }

            final int finalMigratedCount = migratedCount;
            final long finalTotalRows = totalRows;
            MigrationResult report = new MigrationResult(result.errors().isEmpty(), finalMigratedCount, finalTotalRows);
            report.tableRows().putAll(result.tableRows());
            report.errors().addAll(result.errors());
            return report;

        } catch (Exception e) {
            MigrationResult res = new MigrationResult(false, 0, 0);
            res.addError("初始化目标迁移连接池失败: " + e.getMessage());
            return res;
        } finally {
            if (targetDS != null && !targetDS.isClosed()) {
                targetDS.close();
            }
        }
    }

    private long migrateSingleTable(String table, boolean overwrite, HikariDataSource targetDS) throws SQLException {
        long count = 0;
        try (Connection srcConn = getConnection();
             Connection destConn = targetDS.getConnection();
             PreparedStatement srcPs = srcConn.prepareStatement("SELECT * FROM " + table);
             ResultSet rs = srcPs.executeQuery()) {

            destConn.setAutoCommit(false);

            // 如果覆盖，先清空目标表
            if (overwrite) {
                try (java.sql.Statement stmt = destConn.createStatement()) {
                    stmt.executeUpdate("DELETE FROM " + table);
                }
            }

            java.sql.ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            // 构造 INSERT SQL
            StringBuilder insertSql = new StringBuilder("INSERT INTO " + table + " (");
            StringBuilder placeholders = new StringBuilder();
            for (int i = 1; i <= columnCount; i++) {
                if (i > 1) {
                    insertSql.append(", ");
                    placeholders.append(", ");
                }
                insertSql.append(meta.getColumnName(i));
                placeholders.append("?");
            }
            insertSql.append(") VALUES (").append(placeholders).append(")");

            try (PreparedStatement destPs = destConn.prepareStatement(insertSql.toString())) {
                while (rs.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        destPs.setObject(i, rs.getObject(i));
                    }
                    destPs.addBatch();
                    count++;

                    if (count % 500 == 0) {
                        destPs.executeBatch();
                        destConn.commit();
                    }
                }
                destPs.executeBatch();
                destConn.commit();
            } catch (SQLException e) {
                destConn.rollback();
                throw e;
            }
        }
        return count;
    }
}
