package xuanmo.arcartxsuite.api.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库跨源一键迁移结果。
 */
public final class MigrationResult {
    private final boolean success;
    private final int tablesMigrated;
    private final long rowsCopied;
    private final Map<String, Long> tableRows = new LinkedHashMap<>();
    private final List<String> errors = new ArrayList<>();

    public MigrationResult(boolean success, int tablesMigrated, long rowsCopied) {
        this.success = success;
        this.tablesMigrated = tablesMigrated;
        this.rowsCopied = rowsCopied;
    }

    public boolean isSuccess() {
        return success;
    }

    public int tablesMigrated() {
        return tablesMigrated;
    }

    public long rowsCopied() {
        return rowsCopied;
    }

    public Map<String, Long> tableRows() {
        return tableRows;
    }

    public List<String> errors() {
        return errors;
    }

    public void addTableRow(String table, long rows) {
        tableRows.put(table, rows);
    }

    public void addError(String error) {
        errors.add(error);
    }
}
