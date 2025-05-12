package ch.bzz.backend.controller;

import ch.bzz.backend.model.EnergyData;
import ch.bzz.backend.model.StromzaehlerDaten;
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
import java.util.List;
import java.util.Map;

/**
 * Controller for handling XML file uploads
 */
@RestController
@RequiredArgsConstructor
public class UploadController {

    private final XmlParserService xmlParserService;
    private final LocalStorageService localStorageService;

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

            // Save the parsed data to local storage
            energyDataList = localStorageService.saveEnergyData(energyDataList);

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
        List<EnergyData> energyDataList = localStorageService.getAllEnergyData();
        return ResponseEntity.ok(energyDataList);
    }

    /**
     * Endpoint for retrieving energy data by meter ID
     * @param meterId The meter ID to search for
     * @return List of energy data for the specified meter
     */
    @GetMapping("/energy-data/meter")
    public ResponseEntity<List<EnergyData>> getEnergyDataByMeterId(@RequestParam("meterId") String meterId) {
        List<EnergyData> energyDataList = localStorageService.getEnergyDataByMeterId(meterId);
        return ResponseEntity.ok(energyDataList);
    }
}
