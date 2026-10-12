package com.northstar.crm.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateInteractionRequest(
    @NotNull @PositiveOrZero @JsonDeserialize(using = InteractionVersionDeserializer.class) Long version,
    @JsonDeserialize(using = InteractionEditFieldDeserializer.class)
        @Pattern(
            regexp = "CALL|EMAIL|NOTE|MEETING",
            message = "interactionType must be CALL, EMAIL, NOTE, or MEETING")
        String interactionType,
    @JsonDeserialize(using = InteractionEditFieldDeserializer.class) @Pattern(regexp = "(?s).*\\S.*") @Size(max = 1024) String summary) {

  @AssertTrue(message = "At least one of interactionType or summary must be supplied")
  public boolean isEditPresent() {
    return interactionType != null || summary != null;
  }
}
