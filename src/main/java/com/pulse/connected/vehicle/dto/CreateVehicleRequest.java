package com.pulse.connected.vehicle.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateVehicleRequest {

    @NotBlank(message = "VIN is required")
    @Size(min = 17, max = 17, message = "VIN must be exactly 17 characters")
    private String vin;

    @NotBlank(message = "Model is required")
    private String model;

    @NotNull(message = "Model year is required")
    @Min(value = 1900, message = "Invalid model year")
    @Max(value = 2100, message = "Invalid model year")
    private Integer modelYear;

    public CreateVehicleRequest() {}

    public CreateVehicleRequest(String vin, String model, Integer modelYear) {
        this.vin = vin;
        this.model = model;
        this.modelYear = modelYear;
    }

    public static CreateVehicleRequestBuilder builder() {
        return new CreateVehicleRequestBuilder();
    }

    public String getVin() { return vin; }
    public void setVin(String vin) { this.vin = vin; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Integer getModelYear() { return modelYear; }
    public void setModelYear(Integer modelYear) { this.modelYear = modelYear; }

    public static class CreateVehicleRequestBuilder {
        private String vin;
        private String model;
        private Integer modelYear;

        CreateVehicleRequestBuilder() {}

        public CreateVehicleRequestBuilder vin(String vin) { this.vin = vin; return this; }
        public CreateVehicleRequestBuilder model(String model) { this.model = model; return this; }
        public CreateVehicleRequestBuilder modelYear(Integer modelYear) { this.modelYear = modelYear; return this; }

        public CreateVehicleRequest build() {
            return new CreateVehicleRequest(vin, model, modelYear);
        }
    }
}
