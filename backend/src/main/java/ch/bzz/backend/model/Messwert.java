package ch.bzz.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a measurement value with timestamp, absolute and relative values
 * as suggested by Roger Bünzli.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Messwert {
    /**
     * The timestamp of the measurement
     */
    private LocalDateTime timestamp;
    
    /**
     * The absolute measurement value
     */
    private double absoluteValue;
    
    /**
     * The relative measurement value (difference from previous measurement)
     */
    private double relativeValue;
    
    /**
     * The unit of measurement (e.g., KWH)
     */
    private String unit;
    
    /**
     * The type of energy data (production or consumption)
     */
    private EnergyData.DataType type;
}