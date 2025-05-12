package ch.bzz.backend.controller;

import ch.bzz.backend.model.EnergyData;
import ch.bzz.backend.model.Measurement;
import ch.bzz.backend.model.StromzaehlerDaten;
import ch.bzz.backend.service.EnergyDataService;
import ch.bzz.backend.service.LocalStorageService;
import ch.bzz.backend.service.XmlParserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Controller for handling XML file uploads and retrieving energy data
 */
@RestController
@RequiredArgsConstructor
public class UploadController {

    private final XmlParserService xmlParserService;
    private final LocalStorageService localStorageService;
    private final EnergyDataService energyDataService;

    /**
     * Endpoint for uploading and parsing XML files
     * @param file The XML file to parse
     * @return List of parsed energy data
     */
    @PostMapping("/upload")
    public ResponseEntity<List<EnergyData>> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            // Parse the XML file
            List<EnergyData> energyDataList = xmlParserService.parseXml(file.getInputStream());

            // Save the parsed data to both local storage and database
            localStorageService.saveEnergyData(energyDataList);
            energyDataList = energyDataService.saveEnergyData(energyDataList);

            return ResponseEntity.ok(energyDataList);
        } catch (IOException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Endpoint for uploading and parsing XML files to StromzaehlerDaten
     * @param file The XML file to parse
     * @return Map of meter IDs to StromzaehlerDaten objects
     */
    @PostMapping("/upload/stromzaehler")
    public ResponseEntity<Map<String, StromzaehlerDaten>> uploadFileToStromzaehlerDaten(@RequestParam("file") MultipartFile file) {
        try {
            // Parse the XML file to StromzaehlerDaten
            Map<String, StromzaehlerDaten> stromzaehlerDatenMap = xmlParserService.parseXmlToStromzaehlerDaten(file.getInputStream());

            // Save the parsed data to local storage
            stromzaehlerDatenMap = localStorageService.saveStromzaehlerDaten(stromzaehlerDatenMap);

            return ResponseEntity.ok(stromzaehlerDatenMap);
        } catch (IOException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Endpoint for retrieving all energy data
     * @return List of all energy data
     */
    @GetMapping("/energy-data")
    public ResponseEntity<List<EnergyData>> getAllEnergyData() {
        List<EnergyData> energyDataList = energyDataService.getAllEnergyData();
        return ResponseEntity.ok(energyDataList);
    }

    /**
     * Endpoint for retrieving energy data by meter ID
     * @param meterId The meter ID to search for
     * @return List of energy data for the specified meter
     */
    @GetMapping("/energy-data/meter")
    public ResponseEntity<List<EnergyData>> getEnergyDataByMeterId(@RequestParam("meterId") String meterId) {
        List<EnergyData> energyDataList = energyDataService.getEnergyDataByMeterId(meterId);
        return ResponseEntity.ok(energyDataList);
    }

    /**
     * Endpoint for retrieving all measurements grouped by type for a specific meter ID
     * This endpoint returns production, consumption, and net values in one call
     * @param meterId The meter ID to search for
     * @return Map of data types to lists of measurements
     */
    @GetMapping("/energy-data/meter/measurements")
    public ResponseEntity<Map<EnergyData.DataType, List<Measurement>>> getMeasurementsByMeterIdGroupedByType(
            @RequestParam("meterId") String meterId) {
        Map<EnergyData.DataType, List<Measurement>> measurements = 
                energyDataService.getAllMeasurementsByMeterIdGroupedByType(meterId);
        return ResponseEntity.ok(measurements);
    }

    /**
     * Endpoint for retrieving measurements by meter ID and type
     * @param meterId The meter ID to search for
     * @param type The type to search for (PRODUCTION or CONSUMPTION)
     * @return List of measurements for the specified meter ID and type
     */
    @GetMapping("/energy-data/meter/measurements/type")
    public ResponseEntity<List<Measurement>> getMeasurementsByMeterIdAndType(
            @RequestParam("meterId") String meterId,
            @RequestParam("type") EnergyData.DataType type) {
        List<Measurement> measurements = energyDataService.getMeasurementsByMeterIdAndType(meterId, type);
        return ResponseEntity.ok(measurements);
    }

    /**
     * Endpoint for retrieving measurements by meter ID, type, and timestamp range
     * @param meterId The meter ID to search for
     * @param type The type to search for (PRODUCTION or CONSUMPTION)
     * @param startTime The start of the timestamp range (ISO format)
     * @param endTime The end of the timestamp range (ISO format)
     * @return List of measurements for the specified meter ID, type, and timestamp range
     */
    @GetMapping("/energy-data/meter/measurements/range")
    public ResponseEntity<List<Measurement>> getMeasurementsByMeterIdAndTypeAndTimestampRange(
            @RequestParam("meterId") String meterId,
            @RequestParam("type") EnergyData.DataType type,
            @RequestParam("startTime") String startTime,
            @RequestParam("endTime") String endTime) {
        LocalDateTime start = LocalDateTime.parse(startTime, DateTimeFormatter.ISO_DATE_TIME);
        LocalDateTime end = LocalDateTime.parse(endTime, DateTimeFormatter.ISO_DATE_TIME);
        List<Measurement> measurements = energyDataService.getMeasurementsByMeterIdAndTypeAndTimestampRange(
                meterId, type, start, end);
        return ResponseEntity.ok(measurements);
    }
}
