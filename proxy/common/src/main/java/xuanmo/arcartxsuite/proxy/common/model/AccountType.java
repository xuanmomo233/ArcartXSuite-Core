package xuanmo.arcartxsuite.proxy.common.model;

/**
 * 认证账号类型。
 */
public enum AccountType {
    MICROSOFT("microsoft", "微软正版"),
    LITTLESKIN("littleskin", "LittleSkin"),
    OFFLINE("offline", "离线"),
    UNKNOWN("unknown", "未知");

    private final String id;
    private final String displayName;

    AccountType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public boolean premium() {
        return this == MICROSOFT || this == LITTLESKIN;
    }

    public static AccountType fromId(String id) {
        for (AccountType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
