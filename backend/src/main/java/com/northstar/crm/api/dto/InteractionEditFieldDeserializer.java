package com.northstar.crm.api.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.io.IOException;

public class InteractionEditFieldDeserializer extends JsonDeserializer<String> {

  @Override
  public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    if (!parser.hasToken(JsonToken.VALUE_STRING)) {
      return (String) context.handleUnexpectedToken(String.class, parser);
    }
    return parser.getText();
  }

  @Override
  public String getNullValue(DeserializationContext context) throws JsonMappingException {
    return context.reportInputMismatch(String.class, "Edit fields must not be null");
  }

  @Override
  public Object getAbsentValue(DeserializationContext context) {
    return null;
  }
}
