package xuanmo.arcartxsuite.bridge;

import java.util.Locale;
import org.bukkit.Server;

public record ServerPlatform(String displayName, boolean hybridServer) {

    public static ServerPlatform detect(Server server) {
        String name = safe(server.getName());
        String version = safe(server.getVersion());
        String className = safe(server.getClass().getName());
        String combined = (name + " " + version + " " + className).toLowerCase(Locale.ROOT);

        if (combined.contains("mohist")) {
            return new ServerPlatform("Mohist", true);
        }
        if (combined.contains("arclight")) {
            return new ServerPlatform("Arclight", true);
        }
        if (combined.contains("catserver")) {
            return new ServerPlatform("CatServer", true);
        }
        if (combined.contains("magma")) {
            return new ServerPlatform("Magma", true);
        }
        if (combined.contains("banner")) {
            return new ServerPlatform("Banner", true);
        }
        if (combined.contains("purpur")) {
            return new ServerPlatform("Purpur", false);
        }
        if (combined.contains("pufferfish")) {
            return new ServerPlatform("Pufferfish", false);
        }
        if (combined.contains("paper")) {
            return new ServerPlatform("Paper", false);
        }
        if (combined.contains("spigot")) {
            return new ServerPlatform("Spigot", false);
        }
        if (combined.contains("craftbukkit") || combined.contains("bukkit")) {
            return new ServerPlatform("Bukkit", false);
        }
        if (!name.isBlank()) {
            return new ServerPlatform(name, false);
        }
        return new ServerPlatform(server.getClass().getSimpleName(), false);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
