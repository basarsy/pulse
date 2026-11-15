package com.pulse.connected.vehicle.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateVehicleRequest(
        @NotBlank(message = "VIN is required")
        @Size(min = 17, max = 17, message = "VIN must be exactly 17 characters")
        String vin,

        @NotBlank(message = "Model is required")
        String model,

        @NotNull(message = "Model year is required")
        @Min(value = 1900, message = "Invalid model year")
        @Max(value = 2100, message = "Invalid model year")
        Integer modelYear) {
}
