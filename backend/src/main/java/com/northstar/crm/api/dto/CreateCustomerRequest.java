package com.northstar.crm.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(
    @NotBlank @Size(max = 200) @JsonDeserialize(using = InteractionEditFieldDeserializer.class) String fullName) {}
