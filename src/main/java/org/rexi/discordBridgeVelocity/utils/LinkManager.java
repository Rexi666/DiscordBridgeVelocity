package org.rexi.discordBridgeVelocity.utils;

import org.rexi.discordBridgeVelocity.DiscordBridgeVelocity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LinkManager {
    private static final Map<String, String> codeToDiscordId = new ConcurrentHashMap<>(); // código → discordId
    private static final Map<String, String> discordToCode = new ConcurrentHashMap<>(); //discordId → código
    private static final Map<String, Long> codeExpiry = new ConcurrentHashMap<>();        // código → timestamp de expiración

    private final DiscordBridgeVelocity plugin;
    private final DBManager dbManager;
    private static final long EXPIRATION_TIME = TimeUnit.MINUTES.toMillis(5);

    public LinkManager(DiscordBridgeVelocity plugin, DBManager dbManager) {
        this.plugin = plugin;
        this.dbManager = dbManager;

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            codeExpiry.entrySet().removeIf(entry -> {
                if (entry.getValue() < now) {
                    String code = entry.getKey();
                    String discordId = codeToDiscordId.remove(code);
                    if (discordId != null) discordToCode.remove(discordId);
                    return true;
                }
                return false;
            });
        }, 1, 1, TimeUnit.MINUTES);
    }

    public static String generateCode(String discordId) {
        if (discordToCode.containsKey(discordId)) {
            return discordToCode.get(discordId);
        }

        String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        codeToDiscordId.put(code, discordId);
        discordToCode.put(discordId, code);
        codeExpiry.put(code, System.currentTimeMillis() + EXPIRATION_TIME);

        return code;
    }

    public static String getDiscordId(String code) {
        Long expiry = codeExpiry.get(code);
        if (expiry == null || expiry < System.currentTimeMillis()) {
            removeCode(code);
            return null;
        }
        return codeToDiscordId.get(code);
    }

    public static void removeCode(String code) {
        String discordId = codeToDiscordId.remove(code);
        if (discordId != null) discordToCode.remove(discordId);
        codeExpiry.remove(code);
    }
}

