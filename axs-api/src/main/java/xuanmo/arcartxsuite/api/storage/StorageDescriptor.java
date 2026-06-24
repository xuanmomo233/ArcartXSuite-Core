package xuanmo.arcartxsuite.api.storage;

/**
 * 数据库连接描述符，由各模块的配置类构造后传给 {@link AbstractModuleRepository}。
 * <p>
 * 统一 MySQL / SQLite 双方言所需的全部参数。
 */
public record StorageDescriptor(
    boolean isMysql,
    String host,
    int port,
    String database,
    String username,
    String password,
    int poolSize,
    String sqliteFileName,
    String tablePrefix
) {

    /**
     * 快速构造 SQLite 描述符。
     */
    public static StorageDescriptor sqlite(String fileName) {
        return new StorageDescriptor(false, "", 0, "", "", "", 1, fileName, "");
    }

    /**
     * 快速构造 MySQL 描述符。
     */
    public static StorageDescriptor mysql(String host, int port, String database,
                                          String username, String password,
                                          int poolSize, String tablePrefix) {
        return new StorageDescriptor(true, host, port, database, username, password,
            poolSize, "", tablePrefix);
    }
}
