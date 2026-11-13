package com.pulse.connected.messaging;

/**
 * Shared Kafka topic name constants used across all services and the simulator.
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    public static final String VEHICLE_COMMANDS = "vehicle.commands";
    public static final String VEHICLE_COMMAND_RESULTS = "vehicle.command-results";
    public static final String VEHICLE_HEARTBEAT = "vehicle.heartbeat";
    public static final String OTA_INSTRUCTIONS = "ota.vehicle-instructions";
    public static final String OTA_UPDATE_STATUS = "ota.vehicle-update-status";
    public static final String VEHICLE_TELEMETRY = "vehicle.telemetry";

    /** Topics consumed by the application; each has a dead-letter topic. */
    public static final java.util.List<String> CONSUMED = java.util.List.of(
            VEHICLE_COMMAND_RESULTS, VEHICLE_HEARTBEAT, OTA_UPDATE_STATUS, VEHICLE_TELEMETRY);

    /** Dead-letter topic for messages from {@code topic} that could not be processed. */
    public static String deadLetter(String topic) {
        return topic + ".DLT";
    }
}
