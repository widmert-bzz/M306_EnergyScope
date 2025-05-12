package ch.bzz.backend.service;

import ch.bzz.backend.model.EnergyData;
import ch.bzz.backend.model.StromzaehlerDaten;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

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
}
