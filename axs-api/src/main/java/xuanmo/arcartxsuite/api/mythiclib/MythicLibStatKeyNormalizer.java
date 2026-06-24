package xuanmo.arcartxsuite.api.mythiclib;

import java.util.Locale;

public final class MythicLibStatKeyNormalizer {

    private MythicLibStatKeyNormalizer() {
    }

    public static String normalize(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String normalized = rawValue
            .trim()
            .replaceAll("[^A-Za-z0-9]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_", "")
            .replaceAll("_$", "");
        return normalized.isBlank() ? "" : normalized.toUpperCase(Locale.ROOT);
    }
}
