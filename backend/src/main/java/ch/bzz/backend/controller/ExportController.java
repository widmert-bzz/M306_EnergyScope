package ch.bzz.backend.controller;

import ch.bzz.backend.model.EnergyData;
import ch.bzz.backend.model.Measurement;
import ch.bzz.backend.model.StromzaehlerDaten;
import ch.bzz.backend.model.Messwert;
import ch.bzz.backend.service.EnergyDataService;
import ch.bzz.backend.service.LocalStorageService;
import ch.bzz.backend.service.XmlParserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
    private final XmlParserService xmlParserService;

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

    /**
     * Export mapped data from sdat and edm files
     * This endpoint maps data from sdat files to edm files and returns the mapped data
     * when the timestamp is correct
     * 
     * @return JSON representation of mapped data
     */
    @GetMapping("/json/mapped-data")
    public ResponseEntity<Map<String, List<Map<String, Object>>>> exportMappedData() {
        try {
            // Load the sdat and edm files
            String sdatFilePath = "src/main/resources/data/20220331_093121_12X-0000001216-O_E66_12X-LIPPUNEREM-T_ESLEVU373852_-1312619674.xml";
            String edmFilePath = "src/main/resources/data/EdmRegisterWertExport_20190403_eslevu_20190403050446.xml";

            File sdatFile = new File(sdatFilePath);
            File edmFile = new File(edmFilePath);

            if (!sdatFile.exists() || !edmFile.exists()) {
                return ResponseEntity.notFound().build();
            }

            // Create input streams for the files
            List<InputStream> inputStreams = new ArrayList<>();
            inputStreams.add(new FileInputStream(sdatFile));
            inputStreams.add(new FileInputStream(edmFile));

            // Process the files using the XmlParserService
            Map<String, StromzaehlerDaten> mappedData = xmlParserService.processMultipleFiles(inputStreams);

            // Convert the mapped data to a format that the frontend can use
            Map<String, List<Map<String, Object>>> result = new HashMap<>();

            for (Map.Entry<String, StromzaehlerDaten> entry : mappedData.entrySet()) {
                String meterId = entry.getKey();
                StromzaehlerDaten stromzaehlerDaten = entry.getValue();

                List<Map<String, Object>> dataPoints = new ArrayList<>();

                // Group measurements by type
                Map<EnergyData.DataType, List<Messwert>> measurementsByType = new HashMap<>();
                measurementsByType.put(EnergyData.DataType.PRODUCTION, new ArrayList<>());
                measurementsByType.put(EnergyData.DataType.CONSUMPTION, new ArrayList<>());

                // Populate type maps
                for (Map.Entry<LocalDateTime, Messwert> messwertEntry : stromzaehlerDaten.getAllMesswerte().entrySet()) {
                    Messwert messwert = messwertEntry.getValue();
                    EnergyData.DataType type = messwert.getType();

                    if (type != null) {
                        measurementsByType.get(type).add(messwert);
                    }
                }

                // Convert production measurements
                List<Messwert> productionMeasurements = measurementsByType.get(EnergyData.DataType.PRODUCTION);
                if (productionMeasurements != null && !productionMeasurements.isEmpty()) {
                    List<Map<String, Object>> productionDataPoints = convertMesswerteToJsonFormat(productionMeasurements);
                    result.put("production", productionDataPoints);
                }

                // Convert consumption measurements
                List<Messwert> consumptionMeasurements = measurementsByType.get(EnergyData.DataType.CONSUMPTION);
                if (consumptionMeasurements != null && !consumptionMeasurements.isEmpty()) {
                    List<Map<String, Object>> consumptionDataPoints = convertMesswerteToJsonFormat(consumptionMeasurements);
                    result.put("consumption", consumptionDataPoints);
                }

                // Calculate and convert net measurements
                if (productionMeasurements != null && !productionMeasurements.isEmpty() &&
                    consumptionMeasurements != null && !consumptionMeasurements.isEmpty()) {

                    // Create a map of timestamp to production measurement
                    Map<LocalDateTime, Messwert> productionByTimestamp = new HashMap<>();
                    for (Messwert messwert : productionMeasurements) {
                        productionByTimestamp.put(messwert.getTimestamp(), messwert);
                    }

                    // Create a map of timestamp to consumption measurement
                    Map<LocalDateTime, Messwert> consumptionByTimestamp = new HashMap<>();
                    for (Messwert messwert : consumptionMeasurements) {
                        consumptionByTimestamp.put(messwert.getTimestamp(), messwert);
                    }

                    // Calculate net values for timestamps that exist in both maps
                    List<Messwert> netMeasurements = new ArrayList<>();
                    for (Map.Entry<LocalDateTime, Messwert> prodEntry : productionByTimestamp.entrySet()) {
                        LocalDateTime timestamp = prodEntry.getKey();
                        Messwert production = prodEntry.getValue();

                        if (consumptionByTimestamp.containsKey(timestamp)) {
                            Messwert consumption = consumptionByTimestamp.get(timestamp);

                            // Create a new measurement for the net value
                            Messwert netMesswert = Messwert.builder()
                                    .timestamp(timestamp)
                                    .absoluteValue(production.getAbsoluteValue() - consumption.getAbsoluteValue())
                                    .relativeValue(production.getRelativeValue() - consumption.getRelativeValue())
                                    .unit(production.getUnit())
                                    .type(null) // No specific type for net values
                                    .build();

                            netMeasurements.add(netMesswert);
                        }
                    }

                    // Convert net measurements
                    if (!netMeasurements.isEmpty()) {
                        List<Map<String, Object>> netDataPoints = convertMesswerteToJsonFormat(netMeasurements);
                        result.put("net", netDataPoints);
                    }
                }
            }

            return ResponseEntity.ok(result);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Convert Messwert objects to JSON format
     * @param messwerte List of Messwert objects
     * @return List of maps representing JSON data
     */
    private List<Map<String, Object>> convertMesswerteToJsonFormat(List<Messwert> messwerte) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (Messwert messwert : messwerte) {
            Map<String, Object> dataPoint = new HashMap<>();
            dataPoint.put("ts", messwert.getTimestamp().toString());
            dataPoint.put("value", messwert.getAbsoluteValue());

            result.add(dataPoint);
        }

        return result;
    }
}
