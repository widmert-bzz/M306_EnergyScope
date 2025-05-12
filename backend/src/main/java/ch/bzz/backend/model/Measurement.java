package ch.bzz.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Represents a measurement value with timestamp, value, and unit
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "measurement")
public class Measurement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private EnergyData.DataType type;

    @Column(name = "identifier")
    private String identifier; // OBIS code or sequence number

    @Column(name = "myvalue")
    private double myvalue;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @Column(name = "unit")
    private String unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "energy_data_id")
    @JsonIgnore // Avoid circular reference in JSON serialization
    private EnergyData energyData;
}
