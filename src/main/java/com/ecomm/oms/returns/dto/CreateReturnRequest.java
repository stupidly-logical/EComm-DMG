package com.ecomm.oms.returns.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateReturnRequest(
        @Size(max = 500) String reason,
        @NotEmpty @Valid List<Line> items) {

    public record Line(
            @NotNull Long orderItemId,
            @NotNull @Positive Integer quantity,
            @Size(max = 255) String reason) {
    }
}
