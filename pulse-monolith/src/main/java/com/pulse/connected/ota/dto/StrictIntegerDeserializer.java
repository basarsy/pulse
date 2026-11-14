package com.pulse.connected.ota.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

/**
 * Accepts only a JSON integer that fits an {@code int} (or {@code null}). Jackson's default would
 * truncate {@code 25.5} to 25 or coerce {@code "25"}, silently changing what the client sent; here
 * those are rejected and reach the client as a 400.
 */
public class StrictIntegerDeserializer extends StdDeserializer<Integer> {

    public StrictIntegerDeserializer() {
        super(Integer.class);
    }

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT && parser.getNumberType() == JsonParser.NumberType.INT) {
            return parser.getIntValue();
        }
        return (Integer) context.handleUnexpectedToken(Integer.class, parser);
    }
}
