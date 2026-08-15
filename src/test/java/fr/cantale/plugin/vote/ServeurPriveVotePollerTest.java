package fr.cantale.plugin.vote;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServeurPriveVotePollerTest {

    @Test
    void parseTimestampUnixSeconds() {
        assertEquals(1689241974L, ServeurPriveVotePoller.parseTimestamp(JsonParser.parseString("1689241974")));
    }

    @Test
    void parseTimestampUnixMillis() {
        assertEquals(1689241974L, ServeurPriveVotePoller.parseTimestamp(JsonParser.parseString("1689241974000")));
    }

    @Test
    void parseTimestampIso() {
        assertEquals(1689241974L, ServeurPriveVotePoller.parseTimestamp(
                JsonParser.parseString("\"2023-07-13T09:52:54Z\"")));
    }

    @Test
    void missingIpEndpointIs404Only() {
        assertTrue(ServeurPriveVotePoller.isMissingIpEndpoint(404));
        assertFalse(ServeurPriveVotePoller.isMissingIpEndpoint(200));
        assertFalse(ServeurPriveVotePoller.isMissingIpEndpoint(500));
    }

    @Test
    void endpointLabelsNeverIncludeTokenOrUrl() {
        assertEquals("GET /v1/servers/{token}/votes",
                ServeurPriveVotePoller.endpointLabel(ServeurPriveVotePoller.VoteEndpoint.LIST));
        assertEquals("GET /v1/servers/{token}/votes/{ip}",
                ServeurPriveVotePoller.endpointLabel(ServeurPriveVotePoller.VoteEndpoint.BY_IP));
    }
}
