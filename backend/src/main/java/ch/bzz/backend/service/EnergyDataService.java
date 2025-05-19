package ch.bzz.backend.service;

import ch.bzz.backend.model.EnergyData;
import ch.bzz.backend.model.Measurement;
import ch.bzz.backend.repository.EnergyDataRepository;
import ch.bzz.backend.repository.MeasurementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing energy data in the database
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EnergyDataService {

    private final EnergyDataRepository energyDataRepository;
    private final MeasurementRepository measurementRepository;

    /**
     * Save energy data to the database
     * @param energyDataList List of energy data to save
     * @return The saved energy data list
     */
    @Transactional
    public List<EnergyData> saveEnergyData(List<EnergyData> energyDataList) {
        return energyDataRepository.saveAll(energyDataList);
    }

    /**
     * Get all energy data from the database
     * @return List of all energy data
     */
    public List<EnergyData> getAllEnergyData() {
        return energyDataRepository.findAll();
    }

    /**
     * Get energy data by meter ID from the database
     * @param meterId The meter ID to search for
     * @return List of energy data for the specified meter
     */
    public List<EnergyData> getEnergyDataByMeterId(String meterId) {
        return energyDataRepository.findByMeterId(meterId);
    }

    /**
     * Get energy data by meter ID and timestamp range from the database
     * @param meterId The meter ID to search for
     * @param startTime The start of the timestamp range
     * @param endTime The end of the timestamp range
     * @return List of energy data for the specified meter and timestamp range
     */
    public List<EnergyData> getEnergyDataByMeterIdAndTimestampRange(
            String meterId, LocalDateTime startTime, LocalDateTime endTime) {
        return energyDataRepository.findByMeterIdAndTimestampBetween(meterId, startTime, endTime);
    }

    /**
     * Get measurements by meter ID and type from the database
     * @param meterId The meter ID to search for
     * @param type The type to search for (PRODUCTION or CONSUMPTION)
     * @return List of measurements for the specified meter ID and type
     */
    public List<Measurement> getMeasurementsByMeterIdAndType(String meterId, EnergyData.DataType type) {
        return measurementRepository.findByEnergyData_MeterIdAndType(meterId, type);
    }

    /**
     * Get measurements by meter ID, type, and timestamp range from the database
     * @param meterId The meter ID to search for
     * @param type The type to search for (PRODUCTION or CONSUMPTION)
     * @param startTime The start of the timestamp range
     * @param endTime The end of the timestamp range
     * @return List of measurements for the specified meter ID, type, and timestamp range
     */
    public List<Measurement> getMeasurementsByMeterIdAndTypeAndTimestampRange(
            String meterId, EnergyData.DataType type, LocalDateTime startTime, LocalDateTime endTime) {
        return measurementRepository.findByEnergyData_MeterIdAndTypeAndTimestampBetween(
                meterId, type, startTime, endTime);
    }

    /**
     * Get all measurements grouped by type for a specific meter ID
     * @param meterId The meter ID to search for
     * @return Map of data types to lists of measurements
     */
    public Map<EnergyData.DataType, List<Measurement>> getAllMeasurementsByMeterIdGroupedByType(String meterId) {
        List<EnergyData> energyDataList = energyDataRepository.findByMeterId(meterId);

        Map<EnergyData.DataType, List<Measurement>> result = new HashMap<>();

        // Get all measurements for the meter ID
        List<Measurement> allMeasurements = energyDataList.stream()
                .flatMap(energyData -> energyData.getMeasurements().stream())
                .collect(Collectors.toList());

        // Group measurements by type
        Map<EnergyData.DataType, List<Measurement>> groupedByType = allMeasurements.stream()
                .collect(Collectors.groupingBy(Measurement::getType));

        // Add production and consumption types to the result
        result.put(EnergyData.DataType.PRODUCTION, 
                groupedByType.getOrDefault(EnergyData.DataType.PRODUCTION, List.of()));
        result.put(EnergyData.DataType.CONSUMPTION, 
                groupedByType.getOrDefault(EnergyData.DataType.CONSUMPTION, List.of()));

        // Calculate net values (production - consumption) if both types exist
        if (groupedByType.containsKey(EnergyData.DataType.PRODUCTION) && 
            groupedByType.containsKey(EnergyData.DataType.CONSUMPTION)) {

            // Create a map of timestamp to production measurement
            Map<LocalDateTime, Measurement> productionByTimestamp = groupedByType.get(EnergyData.DataType.PRODUCTION)
                    .stream()
                    .collect(Collectors.toMap(Measurement::getTimestamp, m -> m, (m1, m2) -> m1));

            // Create a map of timestamp to consumption measurement
            Map<LocalDateTime, Measurement> consumptionByTimestamp = groupedByType.get(EnergyData.DataType.CONSUMPTION)
                    .stream()
                    .collect(Collectors.toMap(Measurement::getTimestamp, m -> m, (m1, m2) -> m1));

            // Calculate net values for timestamps that exist in both maps
            List<Measurement> netMeasurements = productionByTimestamp.entrySet().stream()
                    .filter(entry -> consumptionByTimestamp.containsKey(entry.getKey()))
                    .map(entry -> {
                        LocalDateTime timestamp = entry.getKey();
                        Measurement production = entry.getValue();
                        Measurement consumption = consumptionByTimestamp.get(timestamp);

                        // Create a new measurement for the net value
                        return Measurement.builder()
                                .type(null) // No specific type for net values
                                .identifier("NET")
                                .myvalue(production.getMyvalue() - consumption.getMyvalue())
                                .timestamp(timestamp)
                                .unit(production.getUnit())
                                .build();
                    })
                    .collect(Collectors.toList());

            // Add net measurements to the result
            result.put(null, netMeasurements); // Using null as the key for net values
        }

        return result;
    }
}
