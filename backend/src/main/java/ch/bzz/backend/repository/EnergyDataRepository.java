package ch.bzz.backend.repository;

import ch.bzz.backend.model.EnergyData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for EnergyData entities
 */
@Repository
public interface EnergyDataRepository extends JpaRepository<EnergyData, Long> {
    
    /**
     * Find energy data by meter ID
     * @param meterId The meter ID to search for
     * @return List of energy data for the specified meter
     */
    List<EnergyData> findByMeterId(String meterId);
    
    /**
     * Find energy data by meter ID and timestamp
     * @param meterId The meter ID to search for
     * @param timestamp The timestamp to search for
     * @return Energy data for the specified meter and timestamp
     */
    EnergyData findByMeterIdAndTimestamp(String meterId, LocalDateTime timestamp);
    
    /**
     * Find energy data by meter ID and timestamp range
     * @param meterId The meter ID to search for
     * @param startTime The start of the timestamp range
     * @param endTime The end of the timestamp range
     * @return List of energy data for the specified meter and timestamp range
     */
    List<EnergyData> findByMeterIdAndTimestampBetween(String meterId, LocalDateTime startTime, LocalDateTime endTime);
}