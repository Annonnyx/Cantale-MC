package fr.cantale.plugin.web;

import fr.cantale.plugin.cache.RedisChannels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisRelayPolicyTest {

    @Test
    void webChatIsRelayedAndSqlPollingStaysOff() {
        assertEquals(RedisRelayPolicy.Action.RELAY_CHAT, RedisRelayPolicy.actionFor(RedisChannels.CHAT_WEB_TO_MC));
        assertTrue(RedisRelayPolicy.relaysIntoGame(RedisChannels.CHAT_WEB_TO_MC));
        assertFalse(RedisRelayPolicy.relaysIntoGame(RedisChannels.CHAT_MC_TO_WEB));
        assertEquals(RedisRelayPolicy.Action.PUBLISH_ONLY, RedisRelayPolicy.actionFor(RedisChannels.CHAT_MC_TO_WEB));
        assertEquals(RedisRelayPolicy.Action.ADMIN, RedisRelayPolicy.actionFor(RedisChannels.ADMIN_ACTIONS));
        assertEquals(RedisRelayPolicy.Action.IGNORE, RedisRelayPolicy.actionFor("cantale:other"));
        assertFalse(RedisRelayPolicy.sqlPollingEnabled(true));
        assertFalse(RedisRelayPolicy.sqlPollingEnabled(false));
    }

    @Test
    void chatPayloadRoundTripsWithoutPollingFields() {
        String json = """
                {"id":42,"playerUuid":"11111111-1111-1111-1111-111111111111","playerName":"Noa","message":"salut","factionId":7}
                """;
        RedisBridgeMessages.Chat chat = RedisBridgeMessages.parseChat(json);
        assertEquals(42L, chat.id());
        assertEquals("Noa", chat.playerName());
        assertEquals(7, chat.factionId());
        assertNull(RedisBridgeMessages.parseChat("{"));
        assertNull(RedisBridgeMessages.parseAdmin("{\"id\":1}"));

        RedisBridgeMessages.Admin admin = RedisBridgeMessages.parseAdmin("""
                {"id":9,"type":"ban","targetUuid":null,"targetName":"Noa","payload":"raison","actorDiscordId":"1"}
                """);
        assertEquals("ban", admin.type());
        assertEquals("raison", admin.payload());
        assertNull(admin.targetUuid());
    }
}
