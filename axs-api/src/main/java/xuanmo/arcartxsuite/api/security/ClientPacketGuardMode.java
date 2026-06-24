package xuanmo.arcartxsuite.api.security;

import java.util.Locale;

public enum ClientPacketGuardMode {
    SILENT("silent"),
    NOTIFY("notify"),
    PUNISH("punish");

    private final String configValue;

    ClientPacketGuardMode(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static ClientPacketGuardMode parse(String rawValue, ClientPacketGuardMode fallback) {
        if (rawValue == null || rawValue.isBlank()) {
            return fallback;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        for (ClientPacketGuardMode mode : values()) {
            if (mode.configValue.equals(normalized)) {
                return mode;
            }
        }
        return fallback;
    }
}
