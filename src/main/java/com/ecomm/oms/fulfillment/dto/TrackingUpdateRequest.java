package com.ecomm.oms.fulfillment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrackingUpdateRequest(
        @NotBlank @Size(max = 80) String carrier,
        @NotBlank @Size(max = 120) String trackingNumber) {
}
