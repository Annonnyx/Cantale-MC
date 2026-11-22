package fr.cantale.plugin.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Contrat JSON des canaux {@code cantale:chat:*} et {@code cantale:admin:actions}.
 * Pas d'objet Bukkit : parsable dans les tests et sur le thread Pub/Sub.
 */
public final class RedisBridgeMessages {

    public record Chat(
            long id,
            String playerUuid,
            String playerName,
            String message,
            Integer factionId,
            String serverId
    ) {
    }

    public record Admin(
            long id,
            String type,
            String targetUuid,
            String targetName,
            String payload,
            String actorDiscordId,
            String serverId
    ) {
    }

    private RedisBridgeMessages() {
    }

    public static Chat parseChat(String json) {
        JsonObject object = object(json);
        if (object == null) {
            return null;
        }
        String uuid = text(object, "playerUuid");
        String message = text(object, "message");
        if (uuid == null || uuid.isBlank() || message == null) {
            return null;
        }
        return new Chat(longValue(object, "id"), uuid, text(object, "playerName"), message,
                positiveInt(object, "factionId"), text(object, "serverId"));
    }

    public static Admin parseAdmin(String json) {
        JsonObject object = object(json);
        if (object == null) {
            return null;
        }
        String type = text(object, "type");
        if (type == null || type.isBlank()) {
            return null;
        }
        return new Admin(
                longValue(object, "id"),
                type,
                text(object, "targetUuid"),
                text(object, "targetName"),
                text(object, "payload"),
                text(object, "actorDiscordId"),
                text(object, "serverId"));
    }

    public static String serverIdOf(String json) {
        JsonObject object = object(json);
        return object == null ? null : text(object, "serverId");
    }

    public static String outboundChat(String source, String playerUuid, String playerName, String message,
                                      Integer factionId, String serverId) {
        JsonObject object = new JsonObject();
        object.addProperty("source", source == null || source.isBlank() ? "mc" : source);
        if (serverId != null && !serverId.isBlank()) {
            object.addProperty("serverId", serverId);
        }
        if (playerUuid == null || playerUuid.isBlank()) {
            object.add("playerUuid", JsonNull.INSTANCE);
        } else {
            object.addProperty("playerUuid", playerUuid);
        }
        object.addProperty("playerName", playerName == null ? "" : playerName);
        object.addProperty("message", message == null ? "" : message);
        if (factionId != null && factionId > 0) {
            object.addProperty("factionId", factionId);
        } else {
            object.add("factionId", JsonNull.INSTANCE);
        }
        return object.toString();
    }

    private static JsonObject object(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                return null;
            }
            return element.getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String text(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            return object.get(key).getAsString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static long longValue(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return 0L;
        }
        try {
            return object.get(key).getAsLong();
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    private static Integer positiveInt(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            int value = object.get(key).getAsInt();
            return value > 0 ? value : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
