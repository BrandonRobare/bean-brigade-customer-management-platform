package com.northstar.crm.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CustomerDTO (
        @NotBlank
        @Pattern(regexp = "^CUS-[0-9]{4}",
        message = "customerId must be of the form CUS-XXXX")
        String customerId,
        @NotBlank
        String fullName,
        @NotNull
        @Pattern(
                regexp = "ACTIVE|PROSPECT|CLOSED",
                message = "status must be one of ACTIVE, PROSPECT, or CLOSED"
        )
        String status,
        @NotNull
        String createdAt
){}
