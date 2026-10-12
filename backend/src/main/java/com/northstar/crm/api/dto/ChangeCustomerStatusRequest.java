package com.northstar.crm.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ChangeCustomerStatusRequest(
    @NotNull @Pattern(regexp = "PROSPECT|ACTIVE|CLOSED") @JsonDeserialize(using = InteractionEditFieldDeserializer.class) String status,
    @NotNull @PositiveOrZero @JsonDeserialize(using = InteractionVersionDeserializer.class) Long version) {}
