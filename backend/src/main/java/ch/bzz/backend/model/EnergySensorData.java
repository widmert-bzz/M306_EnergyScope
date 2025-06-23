package ch.bzz.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Model class representing the standardized format for energy sensor data
 * to be used in the JSON output
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnergySensorData {
    /**
     * The sensor ID (e.g., ID742 for consumption, ID735 for production)
     */
    private String sensorId;

    /**
     * The list of data points for this sensor
     */
    @Builder.Default
    private List<DataPoint> data = new ArrayList<>();

    /**
     * Inner class representing a single data point with timestamp and value
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPoint {
        /**
         * The timestamp in UTC seconds since epoch
         */
        private String ts;

        /**
         * The absolute meter value at this timestamp
         */
        private double value;
    }
}
