package xuanmo.arcartxsuite.config.diagnostic;

import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.api.config.MigrationOperation;

/**
 * 执行单条 {@link MigrationOperation} 到目标 {@link YamlConfiguration}。
 * <p>
 * 操作通过修改传入的 configuration 完成。返回 true 表示对象有变化。
 */
public final class MigrationOperationExecutor {

    private MigrationOperationExecutor() {
    }

    /**
     * @return 若 configuration 有任何字段变化则 true
     * @throws MigrationFailureException 操作内部错误（如 from 路径是 section 但 to 已存在为非 section）
     */
    public static boolean execute(YamlConfiguration configuration, MigrationOperation operation)
        throws MigrationFailureException {
        try {
            if (operation instanceof MigrationOperation.Rename rename) {
                return renameSibling(configuration, rename.from(), rename.to());
            } else if (operation instanceof MigrationOperation.Remove remove) {
                return removePath(configuration, remove.path());
            } else if (operation instanceof MigrationOperation.Move move) {
                return movePath(configuration, move.from(), move.to());
            } else if (operation instanceof MigrationOperation.SetIfMissing s) {
                return setIfMissing(configuration, s.path(), s.value());
            } else if (operation instanceof MigrationOperation.ValueMap vm) {
                return applyValueMap(configuration, vm.path(), vm.mapping());
            }
            return false;
        } catch (RuntimeException exception) {
            throw new MigrationFailureException(operation, exception.getMessage(), exception);
        }
    }

    private static boolean renameSibling(YamlConfiguration configuration, String from, String to) {
        if (!configuration.isSet(from)) {
            return false;
        }
        Object value = configuration.get(from);
        configuration.set(to, value);
        configuration.set(from, null);
        return true;
    }

    private static boolean removePath(YamlConfiguration configuration, String path) {
        if (!configuration.isSet(path)) {
            return false;
        }
        configuration.set(path, null);
        return true;
    }

    private static boolean movePath(YamlConfiguration configuration, String from, String to) {
        if (!configuration.isSet(from)) {
            return false;
        }
        Object value = configuration.get(from);
        configuration.set(from, null);
        configuration.set(to, value);
        return true;
    }

    private static boolean setIfMissing(YamlConfiguration configuration, String path, Object value) {
        if (configuration.isSet(path)) {
            return false;
        }
        configuration.set(path, value);
        return true;
    }

    private static boolean applyValueMap(YamlConfiguration configuration, String path, Map<String, String> mapping) {
        Object current = configuration.get(path);
        if (current instanceof String s && mapping.containsKey(s)) {
            String mapped = mapping.get(s);
            if (!mapped.equals(s)) {
                configuration.set(path, mapped);
                return true;
            }
        } else if (current instanceof ConfigurationSection) {
            // value-map 仅作用于标量字段，section 跳过
            return false;
        }
        return false;
    }

    /** 执行 {@link MigrationOperation} 时抛出的内部异常。 */
    public static final class MigrationFailureException extends Exception {
        private final MigrationOperation operation;

        public MigrationFailureException(MigrationOperation operation, String message, Throwable cause) {
            super(message, cause);
            this.operation = operation;
        }

        public MigrationOperation operation() {
            return operation;
        }
    }
}
