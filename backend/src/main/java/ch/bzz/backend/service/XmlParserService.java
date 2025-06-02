package ch.bzz.backend.service;

import ch.bzz.backend.model.EnergyData;
import ch.bzz.backend.model.EnergySensorData;
import ch.bzz.backend.model.StromzaehlerDaten;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service interface for parsing XML files
 */
public interface XmlParserService {

    /**
     * Parse an XML file and convert it to a list of EnergyData objects
     * @param inputStream The input stream of the XML file
     * @return List of parsed energy data
     */
    List<EnergyData> parseXml(InputStream inputStream);

    /**
     * Parse an XML file and convert it to a map of StromzaehlerDaten objects
     * using the meter ID as the key
     * @param inputStream The input stream of the XML file
     * @return Map of meter IDs to StromzaehlerDaten objects
     */
    Map<String, StromzaehlerDaten> parseXmlToStromzaehlerDaten(InputStream inputStream);

    /**
     * Process multiple XML files (ESL and SDAT) and map them together
     * This method makes data handling quicker by processing all files at once
     * and calculating relative values
     * 
     * @param inputStreams List of input streams of XML files
     * @return Map of meter IDs to StromzaehlerDaten objects with combined data
     */
    Map<String, StromzaehlerDaten> processMultipleFiles(List<InputStream> inputStreams);

    /**
     * Process multiple XML files (ESL and SDAT) and convert them to standardized EnergySensorData format
     * This method combines data from different file formats and ensures time series consistency
     *
     * @param files List of multipart files to process
     * @return List of EnergySensorData objects containing the processed and combined data in the required format
     * @throws java.io.IOException If there is an error reading the files
     */
    List<EnergySensorData> processFilesToSensorData(List<MultipartFile> files) throws java.io.IOException;
}
