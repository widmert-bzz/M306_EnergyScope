package ch.bzz.backend.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Common model class for representing energy data
 * regardless of the source XML format
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "energy_data")
public class EnergyData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meter_id")
    private String meterId;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @OneToMany(mappedBy = "energyData", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Measurement> measurements = new ArrayList<>();

    /**
     * Type of energy data
     */
    public enum DataType {
        PRODUCTION,
        CONSUMPTION
    }

    /**
     * Helper method to add a measurement
     * Sets the energyData reference in the measurement
     */
    public void addMeasurement(Measurement measurement) {
        measurements.add(measurement);
        measurement.setEnergyData(this);
    }
}
