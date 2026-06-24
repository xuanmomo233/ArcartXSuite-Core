package xuanmo.arcartxsuite.config.diagnostic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import xuanmo.arcartxsuite.api.config.ConfigDiagnosisReport;
import xuanmo.arcartxsuite.api.config.ModuleConfigSpec;

/**
 * 内存中持有的最近一次诊断结果集。
 * <p>
 * 由 {@link ConfigDiagnosticEngine} 跑完后写入；命令 {@code /arcartxsuite config preview|apply}
 * 通过此 store 找到对应 spec 与 report。
 */
public final class ConfigDiagnosisStore {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public synchronized void put(ModuleConfigSpec spec, ConfigDiagnosisReport report) {
        entries.put(spec.ownerId(), new Entry(spec, report));
    }

    public Optional<Entry> get(String ownerId) {
        return Optional.ofNullable(entries.get(ownerId));
    }

    public List<Entry> all() {
        // 保持插入顺序：用 LinkedHashMap 视图
        Map<String, Entry> copy = new LinkedHashMap<>(entries);
        return new ArrayList<>(copy.values());
    }

    public void clear() {
        entries.clear();
    }

    public record Entry(ModuleConfigSpec spec, ConfigDiagnosisReport report) {
    }
}
