package com.northstar.crm.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateCustomerRequest(
    @NotBlank @Size(max = 200) @JsonDeserialize(using = InteractionEditFieldDeserializer.class) String fullName,
    @NotNull @PositiveOrZero @JsonDeserialize(using = InteractionVersionDeserializer.class) Long version) {}
