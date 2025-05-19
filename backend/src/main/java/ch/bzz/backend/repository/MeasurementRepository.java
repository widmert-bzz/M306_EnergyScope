package ch.bzz.backend.repository;

import ch.bzz.backend.model.Measurement;
import ch.bzz.backend.model.EnergyData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Measurement entities
 */
@Repository
public interface MeasurementRepository extends JpaRepository<Measurement, Long> {
    
    /**
     * Find measurements by energy data
     * @param energyData The energy data to search for
     * @return List of measurements for the specified energy data
     */
    List<Measurement> findByEnergyData(EnergyData energyData);
    
    /**
     * Find measurements by type
     * @param type The type to search for (PRODUCTION or CONSUMPTION)
     * @return List of measurements for the specified type
     */
    List<Measurement> findByType(EnergyData.DataType type);
    
    /**
     * Find measurements by energy data and type
     * @param energyData The energy data to search for
     * @param type The type to search for (PRODUCTION or CONSUMPTION)
     * @return List of measurements for the specified energy data and type
     */
    List<Measurement> findByEnergyDataAndType(EnergyData energyData, EnergyData.DataType type);
    
    /**
     * Find measurements by energy data's meter ID and type
     * @param meterId The meter ID to search for
     * @param type The type to search for (PRODUCTION or CONSUMPTION)
     * @return List of measurements for the specified meter ID and type
     */
    List<Measurement> findByEnergyData_MeterIdAndType(String meterId, EnergyData.DataType type);
    
    /**
     * Find measurements by energy data's meter ID, type, and timestamp range
     * @param meterId The meter ID to search for
     * @param type The type to search for (PRODUCTION or CONSUMPTION)
     * @param startTime The start of the timestamp range
     * @param endTime The end of the timestamp range
     * @return List of measurements for the specified meter ID, type, and timestamp range
     */
    List<Measurement> findByEnergyData_MeterIdAndTypeAndTimestampBetween(
            String meterId, EnergyData.DataType type, LocalDateTime startTime, LocalDateTime endTime);
}