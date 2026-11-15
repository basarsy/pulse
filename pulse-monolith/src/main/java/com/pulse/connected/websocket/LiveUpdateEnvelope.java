package com.pulse.connected.websocket;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A live update on its way to the other instances: which instance produced it (so that instance
 * can skip it), the STOMP destination, and the payload exactly as browsers receive it.
 */
record LiveUpdateEnvelope(String origin, String destination, JsonNode payload) {
}
