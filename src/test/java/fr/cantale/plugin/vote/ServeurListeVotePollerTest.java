package fr.cantale.plugin.vote;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServeurListeVotePollerTest {

    @Test
    void notVotedReturnsNull() {
        JsonObject root = JsonParser.parseString("{\"data\":{\"voted\":false,\"nextvote\":null}}").getAsJsonObject();
        assertNull(ServeurListeVotePoller.parseVotePayload(root, "1.1.1.1", "Steve"));
    }

    @Test
    void votedTrueUsesUsernameAndIp() {
        JsonObject root = JsonParser.parseString(
                "{\"data\":{\"voted\":true,\"nextvote\":\"2026-08-13T14:00:00Z\",\"username\":\"Notch\"}}")
                .getAsJsonObject();
        ServeurListeVotePoller.ApiVote vote = ServeurListeVotePoller.parseVotePayload(root, "8.8.8.8", "Steve");
        assertNotNull(vote);
        assertEquals("Notch", vote.username());
        assertEquals("8.8.8.8", vote.address());
        assertTrue(vote.externalId() != null && !vote.externalId().isBlank());
    }

    @Test
    void votedTrueFallsBackToOnlineName() {
        JsonObject root = JsonParser.parseString("{\"data\":{\"voted\":true,\"nextvote\":null}}").getAsJsonObject();
        ServeurListeVotePoller.ApiVote vote = ServeurListeVotePoller.parseVotePayload(root, "1.2.3.4", "Alex");
        assertNotNull(vote);
        assertEquals("Alex", vote.username());
        assertEquals("1.2.3.4", vote.address());
    }

    @Test
    void lastVoteUnixSeconds() {
        JsonObject root = JsonParser.parseString(
                "{\"data\":{\"voted\":true,\"pseudo\":\"Bob\",\"lastvote\":1689241974}}")
                .getAsJsonObject();
        ServeurListeVotePoller.ApiVote vote = ServeurListeVotePoller.parseVotePayload(root, "9.9.9.9", "Steve");
        assertNotNull(vote);
        assertEquals("Bob", vote.username());
        assertEquals(1689241974L, vote.votedAtSeconds());
    }

    @Test
    void parseVotedFlagBooleanAndCanVoteInverse() {
        JsonObject voted = JsonParser.parseString("{\"voted\":true}").getAsJsonObject();
        assertTrue(ServeurListeVotePoller.parseVotedFlag(voted, 0));

        JsonObject notVoted = JsonParser.parseString("{\"voted\":false}").getAsJsonObject();
        assertFalse(ServeurListeVotePoller.parseVotedFlag(notVoted, 0));

        JsonObject cannotVote = JsonParser.parseString("{\"can_vote\":false}").getAsJsonObject();
        assertTrue(ServeurListeVotePoller.parseVotedFlag(cannotVote, 0));
    }
}
