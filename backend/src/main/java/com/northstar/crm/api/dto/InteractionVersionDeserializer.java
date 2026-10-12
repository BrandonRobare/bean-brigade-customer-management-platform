package com.northstar.crm.api.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

public class InteractionVersionDeserializer extends JsonDeserializer<Long> {

  @Override
  public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
      return (Long) context.handleUnexpectedToken(Long.class, parser);
    }
    return parser.getLongValue();
  }
}
