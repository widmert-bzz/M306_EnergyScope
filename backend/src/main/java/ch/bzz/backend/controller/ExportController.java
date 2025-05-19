package ch.bzz.backend.controller;

import ch.bzz.backend.model.EnergyData;
import ch.bzz.backend.model.Measurement;
import ch.bzz.backend.service.EnergyDataService;
import ch.bzz.backend.service.LocalStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for exporting data in different formats
 */
@RestController
@RequestMapping("/export")
@RequiredArgsConstructor
public class ExportController {

    private final LocalStorageService localStorageService;
    private final EnergyDataService energyDataService;

    /**
     * Export energy data as JSON
     * @param meterId Optional meter ID to filter data
     * @return JSON representation of energy data
     */
    @GetMapping("/json")
    public ResponseEntity<List<Map<String, Object>>> exportJson(@RequestParam(value = "meterId", required = false) String meterId) {
        List<EnergyData> energyDataList;

        if (meterId != null && !meterId.isEmpty()) {
            energyDataList = energyDataService.getEnergyDataByMeterId(meterId);
        } else {
            energyDataList = energyDataService.getAllEnergyData();
        }

        List<Map<String, Object>> jsonData = convertToJsonFormat(energyDataList);
        return ResponseEntity.ok(jsonData);
    }

    /**
     * Export energy data as CSV
     * @param meterId Optional meter ID to filter data
     * @return CSV representation of energy data
     */
    @GetMapping("/csv")
    public ResponseEntity<String> exportCsv(@RequestParam(value = "meterId", required = false) String meterId) {
        List<EnergyData> energyDataList;

        if (meterId != null && !meterId.isEmpty()) {
            energyDataList = energyDataService.getEnergyDataByMeterId(meterId);
        } else {
            energyDataList = energyDataService.getAllEnergyData();
        }

        String csvData = convertToCsvFormat(energyDataList);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setContentDispositionFormData("attachment", 
            meterId != null ? meterId + ".csv" : "energy_data.csv");

        return new ResponseEntity<>(csvData, headers, HttpStatus.OK);
    }

    /**
     * Export all measurements grouped by type for a specific meter ID as JSON
     * This endpoint returns production, consumption, and net values in one call
     * @param meterId The meter ID to search for
     * @return JSON representation of measurements grouped by type
     */
    @GetMapping("/json/measurements")
    public ResponseEntity<Map<String, List<Map<String, Object>>>> exportMeasurementsByType(
            @RequestParam("meterId") String meterId) {
        Map<EnergyData.DataType, List<Measurement>> measurementsByType = 
                energyDataService.getAllMeasurementsByMeterIdGroupedByType(meterId);

        Map<String, List<Map<String, Object>>> result = new HashMap<>();

        // Convert production measurements
        List<Measurement> productionMeasurements = measurementsByType.get(EnergyData.DataType.PRODUCTION);
        if (productionMeasurements != null && !productionMeasurements.isEmpty()) {
            result.put("production", convertMeasurementsToJsonFormat(productionMeasurements));
        }

        // Convert consumption measurements
        List<Measurement> consumptionMeasurements = measurementsByType.get(EnergyData.DataType.CONSUMPTION);
        if (consumptionMeasurements != null && !consumptionMeasurements.isEmpty()) {
            result.put("consumption", convertMeasurementsToJsonFormat(consumptionMeasurements));
        }

        // Convert net measurements
        List<Measurement> netMeasurements = measurementsByType.get(null);
        if (netMeasurements != null && !netMeasurements.isEmpty()) {
            result.put("net", convertMeasurementsToJsonFormat(netMeasurements));
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Convert energy data to JSON format as specified in the requirements
     * @param energyDataList List of energy data
     * @return List of maps representing JSON data
     */
    private List<Map<String, Object>> convertToJsonFormat(List<EnergyData> energyDataList) {
        Map<String, List<Map<String, Object>>> groupedData = new HashMap<>();

        for (EnergyData energyData : energyDataList) {
            String sensorId = energyData.getMeterId();

            if (!groupedData.containsKey(sensorId)) {
                groupedData.put(sensorId, new ArrayList<>());
            }

            for (Measurement measurement : energyData.getMeasurements()) {
                Map<String, Object> dataPoint = new HashMap<>();
                dataPoint.put("ts", measurement.getTimestamp().toString());
                dataPoint.put("value", measurement.getMyvalue());

                groupedData.get(sensorId).add(dataPoint);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : groupedData.entrySet()) {
            Map<String, Object> sensorData = new HashMap<>();
            sensorData.put("sensorId", entry.getKey());
            sensorData.put("data", entry.getValue());
            result.add(sensorData);
        }

        return result;
    }

    /**
     * Convert energy data to CSV format as specified in the requirements
     * @param energyDataList List of energy data
     * @return CSV string
     */
    private String convertToCsvFormat(List<EnergyData> energyDataList) {
        StringBuilder csv = new StringBuilder("timestamp,value\n");

        // Group by meter ID
        Map<String, List<EnergyData>> groupedByMeterId = energyDataList.stream()
                .collect(Collectors.groupingBy(EnergyData::getMeterId));

        for (Map.Entry<String, List<EnergyData>> entry : groupedByMeterId.entrySet()) {
            List<EnergyData> meterData = entry.getValue();

            // Flatten measurements for this meter
            for (EnergyData data : meterData) {
                for (Measurement measurement : data.getMeasurements()) {
                    csv.append(measurement.getTimestamp()).append(",")
                       .append(measurement.getMyvalue()).append("\n");
                }
            }
        }

        return csv.toString();
    }

    /**
     * Convert measurements to JSON format
     * @param measurements List of measurements
     * @return List of maps representing JSON data
     */
    private List<Map<String, Object>> convertMeasurementsToJsonFormat(List<Measurement> measurements) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (Measurement measurement : measurements) {
            Map<String, Object> dataPoint = new HashMap<>();
            dataPoint.put("ts", measurement.getTimestamp().toString());
            dataPoint.put("value", measurement.getMyvalue());

            result.add(dataPoint);
        }

        return result;
    }
}
