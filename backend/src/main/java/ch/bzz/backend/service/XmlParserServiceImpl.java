package ch.bzz.backend.service;

import ch.bzz.backend.model.EnergyData;
import ch.bzz.backend.model.Measurement;
import ch.bzz.backend.model.Messwert;
import ch.bzz.backend.model.StromzaehlerDaten;
import ch.bzz.backend.model.EnergySensorData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

/**
 * Implementation of the XmlParserService interface
 */
@Service
@Slf4j
public class XmlParserServiceImpl implements XmlParserService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    /**
     * Parse an XML file into a list of EnergyData objects
     * 
     * This method detects the XML format (ESL or SDAT) based on the root element:
     * - ESLBillingData: ESL format (EdmRegisterWertExport_*.xml)
     * - ValidatedMeteredData: SDAT format (20*.xml)
     * 
     * @param inputStream The input stream containing the XML data
     * @return List of EnergyData objects parsed from the XML
     * @throws RuntimeException If there is an error parsing the XML
     */
    @Override
    public List<EnergyData> parseXml(InputStream inputStream) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(inputStream);
            document.getDocumentElement().normalize();

            String rootElement = document.getDocumentElement().getNodeName();
            log.info("Detected XML root element: {}", rootElement);

            if (rootElement.equals("ESLBillingData")) {
                log.info("Parsing ESL format (EdmRegisterWertExport)");
                return parseESLBillingData(document);
            } else if (rootElement.contains("ValidatedMeteredData")) {
                log.info("Parsing SDAT format (ValidatedMeteredData)");
                return parseValidatedMeteredData(document);
            } else {
                log.error("Unknown XML format: {}", rootElement);
                throw new IllegalArgumentException("Unknown XML format: " + rootElement + 
                    ". Expected 'ESLBillingData' (ESL format) or 'ValidatedMeteredData' (SDAT format).");
            }
        } catch (ParserConfigurationException | SAXException | IOException e) {
            log.error("Error parsing XML file", e);
            throw new RuntimeException("Error parsing XML file: " + e.getMessage(), e);
        }
    }

    /**
     * Parse an ESL format XML document (ESLBillingData) into a list of EnergyData objects
     * 
     * ESL format structure:
     * - Root element: ESLBillingData
     * - Contains Meter elements with factoryNo attribute
     * - Each Meter contains TimePeriod elements with end attribute
     * - Each TimePeriod contains ValueRow elements with obis, value, and status attributes
     * 
     * Important OBIS codes:
     * - 1-1:1.8.1: Bezug Hochtarif (Consumption High Tariff)
     * - 1-1:1.8.2: Bezug Niedertarif (Consumption Low Tariff)
     * - 1-1:2.8.1: Einspeisung Hochtarif (Production High Tariff)
     * - 1-1:2.8.2: Einspeisung Niedertarif (Production Low Tariff)
     * 
     * @param document The XML document to parse
     * @return List of EnergyData objects parsed from the document
     */
    private List<EnergyData> parseESLBillingData(Document document) {
        List<EnergyData> result = new ArrayList<>();
        log.debug("Starting to parse ESL format document");

        NodeList meterNodes = document.getElementsByTagName("Meter");
        log.debug("Found {} meter nodes", meterNodes.getLength());

        for (int i = 0; i < meterNodes.getLength(); i++) {
            Node meterNode = meterNodes.item(i);
            if (meterNode.getNodeType() == Node.ELEMENT_NODE) {
                Element meterElement = (Element) meterNode;
                String meterId = meterElement.getAttribute("factoryNo");
                log.debug("Processing meter with ID: {}", meterId);

                NodeList timePeriodNodes = meterElement.getElementsByTagName("TimePeriod");
                log.debug("Found {} time period nodes for meter {}", timePeriodNodes.getLength(), meterId);

                for (int j = 0; j < timePeriodNodes.getLength(); j++) {
                    Node timePeriodNode = timePeriodNodes.item(j);
                    if (timePeriodNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element timePeriodElement = (Element) timePeriodNode;
                        String endTimeStr = timePeriodElement.getAttribute("end");

                        try {
                            LocalDateTime endTime = LocalDateTime.parse(endTimeStr, DATE_TIME_FORMATTER);
                            log.debug("Processing time period ending at: {}", endTime);

                            List<Measurement> measurements = new ArrayList<>();
                            NodeList valueRowNodes = timePeriodElement.getElementsByTagName("ValueRow");
                            log.debug("Found {} value rows for time period", valueRowNodes.getLength());

                            for (int k = 0; k < valueRowNodes.getLength(); k++) {
                                Node valueRowNode = valueRowNodes.item(k);
                                if (valueRowNode.getNodeType() == Node.ELEMENT_NODE) {
                                    Element valueRowElement = (Element) valueRowNode;
                                    String obis = valueRowElement.getAttribute("obis");
                                    String valueStr = valueRowElement.getAttribute("value");

                                    try {
                                        double value = Double.parseDouble(valueStr);

                                        LocalDateTime timestamp = endTime;
                                        if (valueRowElement.hasAttribute("valueTimeStamp")) {
                                            String valueTimeStr = valueRowElement.getAttribute("valueTimeStamp");
                                            timestamp = LocalDateTime.parse(valueTimeStr, DATE_TIME_FORMATTER);
                                        }

                                        // Determine if it's production or consumption based on OBIS code
                                        EnergyData.DataType dataType = determineDataTypeFromObis(obis);
                                        log.debug("OBIS code: {}, Data type: {}, Value: {}", obis, dataType, value);

                                        Measurement measurement = Measurement.builder()
                                                .type(dataType)
                                                .identifier(obis)
                                                .myvalue(value)
                                                .timestamp(timestamp)
                                                .unit("KWH") // Assuming KWH for ESLBillingData
                                                .build();

                                        measurements.add(measurement);
                                    } catch (NumberFormatException e) {
                                        log.warn("Invalid value format for OBIS {}: {}", obis, valueStr, e);
                                    } catch (Exception e) {
                                        log.warn("Error processing value row with OBIS {}", obis, e);
                                    }
                                }
                            }

                            if (!measurements.isEmpty()) {
                                EnergyData energyData = EnergyData.builder()
                                        .meterId(meterId)
                                        .timestamp(endTime)
                                        .build();

                                // Add measurements to energy data
                                for (Measurement measurement : measurements) {
                                    energyData.addMeasurement(measurement);
                                }

                                result.add(energyData);
                                log.debug("Added EnergyData with {} measurements for meter {}", 
                                        measurements.size(), meterId);
                            } else {
                                log.warn("No valid measurements found for meter {} in time period ending at {}", 
                                        meterId, endTime);
                            }
                        } catch (Exception e) {
                            log.warn("Error processing time period with end time {}", endTimeStr, e);
                        }
                    }
                }
            }
        }

        log.info("Parsed {} EnergyData objects from ESL format document", result.size());
        return result;
    }

    /**
     * Parse a SDAT format XML document (ValidatedMeteredData) into a list of EnergyData objects
     * 
     * SDAT format structure:
     * - Root element: rsm:ValidatedMeteredData_12
     * - Contains rsm:DocumentID element with meter ID (e.g., eslevu156407_BR2294_ID735)
     * - Contains rsm:MeteringData elements
     * - Each MeteringData contains:
     *   - rsm:Interval with StartDateTime and EndDateTime
     *   - rsm:Resolution with Resolution value and Unit
     *   - rsm:Observation elements with Position (Sequence) and Volume
     * 
     * @param document The XML document to parse
     * @return List of EnergyData objects parsed from the document
     */
    private List<EnergyData> parseValidatedMeteredData(Document document) {
        List<EnergyData> result = new ArrayList<>();
        log.debug("Starting to parse SDAT format document");

        // Extract DocumentID from the header to get meter ID
        String documentId = "";
        NodeList documentIdNodes = document.getElementsByTagName("rsm:DocumentID");
        if (documentIdNodes.getLength() > 0) {
            String fullDocumentId = documentIdNodes.item(0).getTextContent();
            log.debug("Found DocumentID: {}", fullDocumentId);
            // Extract the ID part (e.g., ID735 from eslevu180263_BR2294_ID735)
            if (fullDocumentId.contains("_ID")) {
                documentId = fullDocumentId.substring(fullDocumentId.lastIndexOf("_ID") + 1);
                log.debug("Extracted meter ID from DocumentID: {}", documentId);
            }
        }

        NodeList meteringDataNodes = document.getElementsByTagName("rsm:MeteringData");
        log.debug("Found {} metering data nodes", meteringDataNodes.getLength());

        for (int i = 0; i < meteringDataNodes.getLength(); i++) {
            Node meteringDataNode = meteringDataNodes.item(i);
            if (meteringDataNode.getNodeType() == Node.ELEMENT_NODE) {
                Element meteringDataElement = (Element) meteringDataNode;

                try {
                    // Check if it's production or consumption data
                    boolean isProduction = meteringDataElement.getElementsByTagName("rsm:ProductionMeteringPoint").getLength() > 0;
                    boolean isConsumption = meteringDataElement.getElementsByTagName("rsm:ConsumptionMeteringPoint").getLength() > 0;

                    EnergyData.DataType dataType = isProduction ? EnergyData.DataType.PRODUCTION : EnergyData.DataType.CONSUMPTION;
                    log.debug("Data type determined: {}", dataType);

                    // Get meter ID - use DocumentID if available, otherwise use VSENationalID
                    String meterId = documentId;
                    if (meterId.isEmpty()) {
                        if (isProduction) {
                            Element productionElement = (Element) meteringDataElement.getElementsByTagName("rsm:ProductionMeteringPoint").item(0);
                            meterId = getTextContent(productionElement, "rsm:VSENationalID");
                        } else if (isConsumption) {
                            Element consumptionElement = (Element) meteringDataElement.getElementsByTagName("rsm:ConsumptionMeteringPoint").item(0);
                            meterId = getTextContent(consumptionElement, "rsm:VSENationalID");
                        }
                    }
                    log.debug("Using meter ID: {}", meterId);

                    // Get interval
                    Element intervalElement = (Element) meteringDataElement.getElementsByTagName("rsm:Interval").item(0);
                    if (intervalElement == null) {
                        log.warn("No interval element found for metering data node {}", i);
                        continue;
                    }

                    String startTimeStr = getTextContent(intervalElement, "rsm:StartDateTime");
                    String endTimeStr = getTextContent(intervalElement, "rsm:EndDateTime");

                    try {
                        LocalDateTime startTime = LocalDateTime.parse(startTimeStr, DATE_TIME_FORMATTER);
                        LocalDateTime endTime = LocalDateTime.parse(endTimeStr, DATE_TIME_FORMATTER);
                        log.debug("Interval: {} to {}", startTime, endTime);

                        // Get unit
                        Element productElement = (Element) meteringDataElement.getElementsByTagName("rsm:Product").item(0);
                        String unit = getTextContent(productElement, "rsm:MeasureUnit");
                        log.debug("Measurement unit: {}", unit);

                        // Get resolution
                        Element resolutionElement = (Element) meteringDataElement.getElementsByTagName("rsm:Resolution").item(0);
                        if (resolutionElement == null) {
                            log.warn("No resolution element found for metering data node {}", i);
                            continue;
                        }

                        String resolutionStr = getTextContent(resolutionElement, "rsm:Resolution");
                        int resolution;
                        try {
                            resolution = Integer.parseInt(resolutionStr);
                            log.debug("Resolution: {} minutes", resolution);
                        } catch (NumberFormatException e) {
                            log.warn("Invalid resolution format: {}", resolutionStr, e);
                            continue;
                        }

                        // Get observations
                        List<Measurement> measurements = new ArrayList<>();
                        NodeList observationNodes = meteringDataElement.getElementsByTagName("rsm:Observation");
                        log.debug("Found {} observation nodes", observationNodes.getLength());

                        for (int j = 0; j < observationNodes.getLength(); j++) {
                            Node observationNode = observationNodes.item(j);
                            if (observationNode.getNodeType() == Node.ELEMENT_NODE) {
                                Element observationElement = (Element) observationNode;

                                try {
                                    Element positionElement = (Element) observationElement.getElementsByTagName("rsm:Position").item(0);
                                    if (positionElement == null) {
                                        log.warn("No position element found for observation node {}", j);
                                        continue;
                                    }

                                    String sequenceStr = getTextContent(positionElement, "rsm:Sequence");
                                    int sequence;
                                    try {
                                        sequence = Integer.parseInt(sequenceStr);
                                    } catch (NumberFormatException e) {
                                        log.warn("Invalid sequence format: {}", sequenceStr, e);
                                        continue;
                                    }

                                    String volumeStr = getTextContent(observationElement, "rsm:Volume");
                                    double volume;
                                    try {
                                        volume = Double.parseDouble(volumeStr);
                                    } catch (NumberFormatException e) {
                                        log.warn("Invalid volume format: {}", volumeStr, e);
                                        continue;
                                    }

                                    // Calculate timestamp based on sequence, start time, and resolution
                                    // Assuming resolution is in minutes
                                    LocalDateTime timestamp = startTime.plusMinutes((sequence - 1) * resolution);
                                    log.debug("Observation: Sequence {}, Volume {}, Timestamp {}", sequence, volume, timestamp);

                                    Measurement measurement = Measurement.builder()
                                            .type(dataType)
                                            .identifier(String.valueOf(sequence))
                                            .myvalue(volume)
                                            .timestamp(timestamp)
                                            .unit(unit)
                                            .build();

                                    measurements.add(measurement);
                                } catch (Exception e) {
                                    log.warn("Error processing observation node {}", j, e);
                                }
                            }
                        }

                        if (!measurements.isEmpty()) {
                            EnergyData energyData = EnergyData.builder()
                                    .meterId(meterId)
                                    .timestamp(endTime)
                                    .build();

                            // Add measurements to energy data
                            for (Measurement measurement : measurements) {
                                energyData.addMeasurement(measurement);
                            }

                            result.add(energyData);
                            log.debug("Added EnergyData with {} measurements for meter {}", 
                                    measurements.size(), meterId);
                        } else {
                            log.warn("No valid measurements found for meter {} in interval {} to {}", 
                                    meterId, startTime, endTime);
                        }
                    } catch (Exception e) {
                        log.warn("Error processing interval with start time {} and end time {}", 
                                startTimeStr, endTimeStr, e);
                    }
                } catch (Exception e) {
                    log.warn("Error processing metering data node {}", i, e);
                }
            }
        }

        log.info("Parsed {} EnergyData objects from SDAT format document", result.size());
        return result;
    }

    private String getTextContent(Element element, String tagName) {
        NodeList nodeList = element.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return "";
    }

    /**
     * Determine the data type (PRODUCTION or CONSUMPTION) based on the OBIS code
     * 
     * OBIS code structure: A-B:C.D.E
     * 
     * Important OBIS codes:
     * - 1-1:1.8.1: Bezug Hochtarif (Consumption High Tariff)
     * - 1-1:1.8.2: Bezug Niedertarif (Consumption Low Tariff)
     * - 1-1:2.8.1: Einspeisung Hochtarif (Production High Tariff)
     * - 1-1:2.8.2: Einspeisung Niedertarif (Production Low Tariff)
     * 
     * General rules:
     * - OBIS codes starting with "1-1:1" are typically for consumption (Bezug)
     * - OBIS codes starting with "1-1:2" are typically for production (Einspeisung)
     * 
     * @param obis The OBIS code to analyze
     * @return The determined data type (PRODUCTION or CONSUMPTION)
     */
    private EnergyData.DataType determineDataTypeFromObis(String obis) {
        log.debug("Determining data type for OBIS code: {}", obis);

        // Check for specific OBIS codes first
        if (obis.equals("1-1:1.8.1") || obis.equals("1-1:1.8.2")) {
            log.debug("OBIS code {} identified as Bezug (Consumption)", obis);
            return EnergyData.DataType.CONSUMPTION;
        } else if (obis.equals("1-1:2.8.1") || obis.equals("1-1:2.8.2")) {
            log.debug("OBIS code {} identified as Einspeisung (Production)", obis);
            return EnergyData.DataType.PRODUCTION;
        }

        // If not a specific code, use the general rule
        if (obis.startsWith("1-1:1")) {
            log.debug("OBIS code {} starts with 1-1:1, classified as Consumption", obis);
            return EnergyData.DataType.CONSUMPTION;
        } else if (obis.startsWith("1-1:2")) {
            log.debug("OBIS code {} starts with 1-1:2, classified as Production", obis);
            return EnergyData.DataType.PRODUCTION;
        } else {
            log.warn("Unknown OBIS code pattern: {}, defaulting to Consumption", obis);
            return EnergyData.DataType.CONSUMPTION;
        }
    }

    /**
     * Parse an XML file into a map of StromzaehlerDaten objects
     * 
     * This method detects the XML format (ESL or SDAT) based on the root element:
     * - ESLBillingData: ESL format (EdmRegisterWertExport_*.xml)
     * - ValidatedMeteredData: SDAT format (20*.xml)
     * 
     * The resulting StromzaehlerDaten objects use TreeMap to store Messwert objects
     * with timestamp as key, as suggested by Roger Bünzli. This ensures no duplicate
     * timestamps can exist.
     * 
     * @param inputStream The input stream containing the XML data
     * @return Map of meter IDs to StromzaehlerDaten objects
     * @throws RuntimeException If there is an error parsing the XML
     */
    @Override
    public Map<String, StromzaehlerDaten> parseXmlToStromzaehlerDaten(InputStream inputStream) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(inputStream);
            document.getDocumentElement().normalize();

            String rootElement = document.getDocumentElement().getNodeName();
            log.info("Detected XML root element for StromzaehlerDaten parsing: {}", rootElement);

            if (rootElement.equals("ESLBillingData")) {
                log.info("Parsing ESL format (EdmRegisterWertExport) to StromzaehlerDaten");
                return parseESLBillingDataToStromzaehlerDaten(document);
            } else if (rootElement.contains("ValidatedMeteredData")) {
                log.info("Parsing SDAT format (ValidatedMeteredData) to StromzaehlerDaten");
                return parseValidatedMeteredDataToStromzaehlerDaten(document);
            } else {
                log.error("Unknown XML format for StromzaehlerDaten parsing: {}", rootElement);
                throw new IllegalArgumentException("Unknown XML format: " + rootElement + 
                    ". Expected 'ESLBillingData' (ESL format) or 'ValidatedMeteredData' (SDAT format).");
            }
        } catch (ParserConfigurationException | SAXException | IOException e) {
            log.error("Error parsing XML file to StromzaehlerDaten", e);
            throw new RuntimeException("Error parsing XML file to StromzaehlerDaten: " + e.getMessage(), e);
        }
    }

    private Map<String, StromzaehlerDaten> parseESLBillingDataToStromzaehlerDaten(Document document) {
        Map<String, StromzaehlerDaten> result = new HashMap<>();

        // Maps to store Hochtarif and Niedertarif values for each meter ID and timestamp
        Map<String, Map<LocalDateTime, Double>> bezugHochtarifMap = new HashMap<>();
        Map<String, Map<LocalDateTime, Double>> bezugNiedertarifMap = new HashMap<>();
        Map<String, Map<LocalDateTime, Double>> einspeisungHochtarifMap = new HashMap<>();
        Map<String, Map<LocalDateTime, Double>> einspeisungNiedertarifMap = new HashMap<>();

        NodeList meterNodes = document.getElementsByTagName("Meter");
        for (int i = 0; i < meterNodes.getLength(); i++) {
            Node meterNode = meterNodes.item(i);
            if (meterNode.getNodeType() == Node.ELEMENT_NODE) {
                Element meterElement = (Element) meterNode;
                String meterId = meterElement.getAttribute("factoryNo");

                NodeList timePeriodNodes = meterElement.getElementsByTagName("TimePeriod");
                for (int j = 0; j < timePeriodNodes.getLength(); j++) {
                    Node timePeriodNode = timePeriodNodes.item(j);
                    if (timePeriodNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element timePeriodElement = (Element) timePeriodNode;
                        String endTimeStr = timePeriodElement.getAttribute("end");
                        LocalDateTime endTime = LocalDateTime.parse(endTimeStr, DATE_TIME_FORMATTER);

                        NodeList valueRowNodes = timePeriodElement.getElementsByTagName("ValueRow");
                        for (int k = 0; k < valueRowNodes.getLength(); k++) {
                            Node valueRowNode = valueRowNodes.item(k);
                            if (valueRowNode.getNodeType() == Node.ELEMENT_NODE) {
                                Element valueRowElement = (Element) valueRowNode;
                                String obis = valueRowElement.getAttribute("obis");
                                String valueStr = valueRowElement.getAttribute("value");
                                double value = Double.parseDouble(valueStr);

                                LocalDateTime timestamp = endTime;
                                if (valueRowElement.hasAttribute("valueTimeStamp")) {
                                    String valueTimeStr = valueRowElement.getAttribute("valueTimeStamp");
                                    timestamp = LocalDateTime.parse(valueTimeStr, DATE_TIME_FORMATTER);
                                }

                                // Store values in the appropriate map based on OBIS code
                                if (obis.equals("1-1:1.8.1")) { // Bezug Hochtarif
                                    bezugHochtarifMap.computeIfAbsent(meterId, k1 -> new HashMap<>())
                                            .put(timestamp, value);
                                } else if (obis.equals("1-1:1.8.2")) { // Bezug Niedertarif
                                    bezugNiedertarifMap.computeIfAbsent(meterId, k1 -> new HashMap<>())
                                            .put(timestamp, value);
                                } else if (obis.equals("1-1:2.8.1")) { // Einspeisung Hochtarif
                                    einspeisungHochtarifMap.computeIfAbsent(meterId, k1 -> new HashMap<>())
                                            .put(timestamp, value);
                                } else if (obis.equals("1-1:2.8.2")) { // Einspeisung Niedertarif
                                    einspeisungNiedertarifMap.computeIfAbsent(meterId, k1 -> new HashMap<>())
                                            .put(timestamp, value);
                                } else {
                                    // For other OBIS codes, create and add Messwert directly
                                    EnergyData.DataType dataType = determineDataTypeFromObis(obis);

                                    // Create or get StromzaehlerDaten for this meter
                                    StromzaehlerDaten stromzaehlerDaten = result.computeIfAbsent(meterId, StromzaehlerDaten::new);

                                    Messwert messwert = Messwert.builder()
                                            .timestamp(timestamp)
                                            .absoluteValue(value)
                                            .relativeValue(0.0) // Calculate relative value if needed
                                            .unit("KWH") // Assuming KWH for ESLBillingData
                                            .type(dataType)
                                            .build();

                                    stromzaehlerDaten.addMesswert(messwert);
                                }
                            }
                        }
                    }
                }
            }
        }

        // Process Bezug (Consumption) values - map to ID742
        processAndAddCombinedValues(result, bezugHochtarifMap, bezugNiedertarifMap, "ID742", EnergyData.DataType.CONSUMPTION);

        // Process Einspeisung (Production) values - map to ID735
        processAndAddCombinedValues(result, einspeisungHochtarifMap, einspeisungNiedertarifMap, "ID735", EnergyData.DataType.PRODUCTION);

        return result;
    }

    /**
     * Process and add combined values for Hochtarif and Niedertarif
     * 
     * @param result The result map to add the combined values to
     * @param hochtarifMap Map of Hochtarif values by meter ID and timestamp
     * @param niedertarifMap Map of Niedertarif values by meter ID and timestamp
     * @param targetMeterId The target meter ID to use for the combined values
     * @param dataType The data type (PRODUCTION or CONSUMPTION)
     */
    private void processAndAddCombinedValues(
            Map<String, StromzaehlerDaten> result,
            Map<String, Map<LocalDateTime, Double>> hochtarifMap,
            Map<String, Map<LocalDateTime, Double>> niedertarifMap,
            String targetMeterId,
            EnergyData.DataType dataType) {

        // Create or get StromzaehlerDaten for the target meter ID
        StromzaehlerDaten targetStromzaehlerDaten = result.computeIfAbsent(targetMeterId, StromzaehlerDaten::new);

        // Process all meter IDs in the Hochtarif map
        for (String sourceMeterId : hochtarifMap.keySet()) {
            Map<LocalDateTime, Double> hochtarifValues = hochtarifMap.get(sourceMeterId);
            Map<LocalDateTime, Double> niedertarifValues = niedertarifMap.getOrDefault(sourceMeterId, new HashMap<>());

            // Process all timestamps in the Hochtarif map
            for (Map.Entry<LocalDateTime, Double> entry : hochtarifValues.entrySet()) {
                LocalDateTime timestamp = entry.getKey();
                double hochtarifValue = entry.getValue();

                // Get the corresponding Niedertarif value if available
                double niedertarifValue = niedertarifValues.getOrDefault(timestamp, 0.0);

                // Calculate the combined value
                double combinedValue = hochtarifValue + niedertarifValue;

                // Create Messwert with the combined value
                Messwert messwert = Messwert.builder()
                        .timestamp(timestamp)
                        .absoluteValue(combinedValue)
                        .relativeValue(0.0) // Calculate relative value if needed
                        .unit("KWH") // Assuming KWH for ESLBillingData
                        .type(dataType)
                        .build();

                // Add the Messwert to the target StromzaehlerDaten
                targetStromzaehlerDaten.addMesswert(messwert);
            }

            // Process timestamps that are only in the Niedertarif map
            for (Map.Entry<LocalDateTime, Double> entry : niedertarifValues.entrySet()) {
                LocalDateTime timestamp = entry.getKey();

                // Skip if already processed with Hochtarif
                if (hochtarifValues.containsKey(timestamp)) {
                    continue;
                }

                double niedertarifValue = entry.getValue();

                // Create Messwert with just the Niedertarif value
                Messwert messwert = Messwert.builder()
                        .timestamp(timestamp)
                        .absoluteValue(niedertarifValue)
                        .relativeValue(0.0) // Calculate relative value if needed
                        .unit("KWH") // Assuming KWH for ESLBillingData
                        .type(dataType)
                        .build();

                // Add the Messwert to the target StromzaehlerDaten
                targetStromzaehlerDaten.addMesswert(messwert);
            }
        }
    }

    private Map<String, StromzaehlerDaten> parseValidatedMeteredDataToStromzaehlerDaten(Document document) {
        Map<String, StromzaehlerDaten> result = new HashMap<>();

        // Extract DocumentID from the header
        String documentId = "";
        NodeList documentIdNodes = document.getElementsByTagName("rsm:DocumentID");
        if (documentIdNodes.getLength() > 0) {
            String fullDocumentId = documentIdNodes.item(0).getTextContent();
            // Extract the ID part (e.g., ID735 from eslevu180263_BR2294_ID735)
            if (fullDocumentId.contains("_ID")) {
                documentId = fullDocumentId.substring(fullDocumentId.lastIndexOf("_ID") + 1);
            }
        }

        NodeList meteringDataNodes = document.getElementsByTagName("rsm:MeteringData");
        for (int i = 0; i < meteringDataNodes.getLength(); i++) {
            Node meteringDataNode = meteringDataNodes.item(i);
            if (meteringDataNode.getNodeType() == Node.ELEMENT_NODE) {
                Element meteringDataElement = (Element) meteringDataNode;

                // Check if it's production or consumption data
                boolean isProduction = meteringDataElement.getElementsByTagName("rsm:ProductionMeteringPoint").getLength() > 0;
                boolean isConsumption = meteringDataElement.getElementsByTagName("rsm:ConsumptionMeteringPoint").getLength() > 0;

                EnergyData.DataType dataType = isProduction ? EnergyData.DataType.PRODUCTION : EnergyData.DataType.CONSUMPTION;

                // Get meter ID - use DocumentID if available, otherwise use VSENationalID
                String meterId = documentId;
                if (meterId.isEmpty()) {
                    if (isProduction) {
                        Element productionElement = (Element) meteringDataElement.getElementsByTagName("rsm:ProductionMeteringPoint").item(0);
                        meterId = getTextContent(productionElement, "rsm:VSENationalID");
                    } else if (isConsumption) {
                        Element consumptionElement = (Element) meteringDataElement.getElementsByTagName("rsm:ConsumptionMeteringPoint").item(0);
                        meterId = getTextContent(consumptionElement, "rsm:VSENationalID");
                    }
                }

                // Create or get StromzaehlerDaten for this meter
                StromzaehlerDaten stromzaehlerDaten = result.computeIfAbsent(meterId, StromzaehlerDaten::new);

                // Get interval
                Element intervalElement = (Element) meteringDataElement.getElementsByTagName("rsm:Interval").item(0);
                String startTimeStr = getTextContent(intervalElement, "rsm:StartDateTime");
                LocalDateTime startTime = LocalDateTime.parse(startTimeStr, DATE_TIME_FORMATTER);

                // Get unit
                Element productElement = (Element) meteringDataElement.getElementsByTagName("rsm:Product").item(0);
                String unit = getTextContent(productElement, "rsm:MeasureUnit");

                // Get resolution
                Element resolutionElement = (Element) meteringDataElement.getElementsByTagName("rsm:Resolution").item(0);
                String resolutionStr = getTextContent(resolutionElement, "rsm:Resolution");
                int resolution = Integer.parseInt(resolutionStr);

                // Get observations
                NodeList observationNodes = meteringDataElement.getElementsByTagName("rsm:Observation");
                for (int j = 0; j < observationNodes.getLength(); j++) {
                    Node observationNode = observationNodes.item(j);
                    if (observationNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element observationElement = (Element) observationNode;

                        Element positionElement = (Element) observationElement.getElementsByTagName("rsm:Position").item(0);
                        String sequenceStr = getTextContent(positionElement, "rsm:Sequence");
                        int sequence = Integer.parseInt(sequenceStr);

                        String volumeStr = getTextContent(observationElement, "rsm:Volume");
                        double volume = Double.parseDouble(volumeStr);

                        // Calculate timestamp based on sequence, start time, and resolution
                        // Assuming resolution is in minutes
                        LocalDateTime timestamp = startTime.plusMinutes((sequence - 1) * resolution);

                        // Create Messwert and add to StromzaehlerDaten
                        Messwert messwert = Messwert.builder()
                                .timestamp(timestamp)
                                .absoluteValue(volume)
                                .relativeValue(0.0) // Calculate relative value if needed
                                .unit(unit)
                                .type(dataType)
                                .build();

                        stromzaehlerDaten.addMesswert(messwert);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Process multiple XML files and combine the results into a single map of StromzaehlerDaten objects
     * 
     * This method:
     * 1. Parses each XML file into StromzaehlerDaten objects
     * 2. Merges the results by meter ID
     * 3. Calculates relative values for each meter
     * 
     * The resulting StromzaehlerDaten objects use TreeMap to store Messwert objects
     * with timestamp as key, as suggested by Roger Bünzli. This ensures no duplicate
     * timestamps can exist.
     * 
     * @param inputStreams List of input streams containing XML data
     * @return Map of meter IDs to combined StromzaehlerDaten objects
     */
    @Override
    public Map<String, StromzaehlerDaten> processMultipleFiles(List<InputStream> inputStreams) {
        log.info("Processing {} XML files", inputStreams.size());

        // Combined result map
        Map<String, StromzaehlerDaten> combinedResult = new HashMap<>();

        // Process all input streams
        int fileCount = 0;
        for (InputStream inputStream : inputStreams) {
            fileCount++;
            try {
                log.debug("Processing file {}/{}", fileCount, inputStreams.size());
                Map<String, StromzaehlerDaten> fileResult = parseXmlToStromzaehlerDaten(inputStream);
                log.debug("File {}/{} parsed successfully with {} meter(s)", fileCount, inputStreams.size(), fileResult.size());

                // Merge results
                for (Map.Entry<String, StromzaehlerDaten> entry : fileResult.entrySet()) {
                    String meterId = entry.getKey();
                    StromzaehlerDaten fileData = entry.getValue();

                    // Get or create StromzaehlerDaten for this meter
                    StromzaehlerDaten combinedData = combinedResult.computeIfAbsent(meterId, StromzaehlerDaten::new);
                    int initialMeasurementCount = combinedData.getAllMesswerte().size();

                    // Add all measurements from this file
                    for (Map.Entry<LocalDateTime, Messwert> messwertEntry : fileData.getAllMesswerte().entrySet()) {
                        combinedData.addMesswert(messwertEntry.getValue());
                    }

                    int newMeasurementCount = combinedData.getAllMesswerte().size();
                    int addedMeasurements = newMeasurementCount - initialMeasurementCount;
                    log.debug("Added {} measurements for meter {} from file {}/{}", 
                            addedMeasurements, meterId, fileCount, inputStreams.size());
                }
            } catch (Exception e) {
                log.error("Error processing file {}/{}", fileCount, inputStreams.size(), e);
                // Continue with next file instead of failing the entire process
            }
        }

        log.info("Finished processing {} files, found {} unique meters", inputStreams.size(), combinedResult.size());

        // Calculate relative values for each meter
        for (Map.Entry<String, StromzaehlerDaten> entry : combinedResult.entrySet()) {
            String meterId = entry.getKey();
            StromzaehlerDaten stromzaehlerDaten = entry.getValue();

            try {
                log.debug("Calculating relative values for meter {}", meterId);
                calculateRelativeValues(stromzaehlerDaten);
                log.debug("Relative values calculated successfully for meter {}", meterId);
            } catch (Exception e) {
                log.error("Error calculating relative values for meter {}", meterId, e);
                // Continue with next meter instead of failing the entire process
            }
        }

        return combinedResult;
    }

    /**
     * Calculate relative values for all measurements in a StromzaehlerDaten object
     * 
     * The relative value is the difference between the current absolute value and the previous absolute value.
     * This is calculated separately for each data type (PRODUCTION and CONSUMPTION) to ensure
     * that only measurements of the same type are compared.
     * 
     * The calculation is done in chronological order (sorted by timestamp) using the TreeMap structure.
     * 
     * For the first measurement of each type, the relative value remains 0.0 since there is no previous
     * measurement to compare with.
     * 
     * @param stromzaehlerDaten The StromzaehlerDaten object to process
     */
    private void calculateRelativeValues(StromzaehlerDaten stromzaehlerDaten) {
        TreeMap<LocalDateTime, Messwert> messwerte = stromzaehlerDaten.getAllMesswerte();
        log.debug("Calculating relative values for {} measurements", messwerte.size());

        // We need at least two measurements to calculate relative values
        if (messwerte.size() < 2) {
            log.debug("Not enough measurements to calculate relative values (need at least 2, found {})", 
                    messwerte.size());
            return;
        }

        // Separate by type (production/consumption)
        Map<EnergyData.DataType, TreeMap<LocalDateTime, Messwert>> typeMap = new HashMap<>();

        // Initialize maps for each type
        typeMap.put(EnergyData.DataType.PRODUCTION, new TreeMap<>());
        typeMap.put(EnergyData.DataType.CONSUMPTION, new TreeMap<>());

        // Populate type maps
        for (Map.Entry<LocalDateTime, Messwert> entry : messwerte.entrySet()) {
            Messwert messwert = entry.getValue();
            typeMap.get(messwert.getType()).put(entry.getKey(), messwert);
        }

        log.debug("Separated measurements by type: {} production, {} consumption",
                typeMap.get(EnergyData.DataType.PRODUCTION).size(),
                typeMap.get(EnergyData.DataType.CONSUMPTION).size());

        // Calculate relative values for each type
        for (EnergyData.DataType type : typeMap.keySet()) {
            TreeMap<LocalDateTime, Messwert> typedMesswerte = typeMap.get(type);

            if (typedMesswerte.size() < 2) {
                log.debug("Not enough {} measurements to calculate relative values (need at least 2, found {})",
                        type, typedMesswerte.size());
                continue;
            }

            log.debug("Calculating relative values for {} {} measurements", typedMesswerte.size(), type);

            // Get the first entry to use as previous value
            Map.Entry<LocalDateTime, Messwert> previousEntry = typedMesswerte.firstEntry();
            log.debug("First {} measurement at {}: absolute value = {}", 
                    type, previousEntry.getKey(), previousEntry.getValue().getAbsoluteValue());

            int updatedCount = 0;

            // Iterate through the rest of the entries
            for (Map.Entry<LocalDateTime, Messwert> entry : typedMesswerte.entrySet()) {
                // Skip the first entry
                if (entry.getKey().equals(previousEntry.getKey())) {
                    continue;
                }

                Messwert currentMesswert = entry.getValue();
                Messwert previousMesswert = previousEntry.getValue();

                try {
                    // Calculate relative value
                    double relativeValue = currentMesswert.getAbsoluteValue() - previousMesswert.getAbsoluteValue();

                    // Check for potentially erroneous values (e.g., negative consumption or very large jumps)
                    if (relativeValue < 0 && type == EnergyData.DataType.CONSUMPTION) {
                        log.warn("Negative relative value for consumption: {} at {}", 
                                relativeValue, entry.getKey());
                    } else if (relativeValue > 0 && type == EnergyData.DataType.PRODUCTION) {
                        log.warn("Positive relative value for production: {} at {}", 
                                relativeValue, entry.getKey());
                    }

                    if (Math.abs(relativeValue) > 1000) {
                        log.warn("Large relative value change: {} at {}", relativeValue, entry.getKey());
                    }

                    // Update the messwert
                    currentMesswert.setRelativeValue(relativeValue);
                    updatedCount++;

                    log.debug("Calculated relative value for {} at {}: {} (current: {}, previous: {})",
                            type, entry.getKey(), relativeValue, 
                            currentMesswert.getAbsoluteValue(), previousMesswert.getAbsoluteValue());
                } catch (Exception e) {
                    log.error("Error calculating relative value for {} at {}", 
                            type, entry.getKey(), e);
                }

                // Update previous entry for next iteration
                previousEntry = entry;
            }

            log.debug("Updated relative values for {} out of {} {} measurements", 
                    updatedCount, typedMesswerte.size(), type);
        }

        log.debug("Finished calculating relative values");
    }

    /**
     * Process multiple XML files (ESL and SDAT) and convert them to standardized EnergySensorData format
     * This method combines data from different file formats and ensures time series consistency
     *
     * @param files List of multipart files to process
     * @return List of EnergySensorData objects containing the processed and combined data in the required format
     * @throws java.io.IOException If there is an error reading the files
     */
    @Override
    public List<EnergySensorData> processFilesToSensorData(List<MultipartFile> files) throws IOException {
        log.info("Processing {} XML files for standardized sensor data format", files.size());

        // Maps to store the absolute meter values for each sensor ID
        Map<String, TreeMap<LocalDateTime, Double>> sensorValues = new HashMap<>();
        // Initialize maps for ID742 (consumption), ID735 (production) and 38157930 (device meter)
        sensorValues.put("ID742", new TreeMap<>());
        sensorValues.put("ID735", new TreeMap<>());
        sensorValues.put("38157930", new TreeMap<>()); // Add support for the device meter

        // Process ESL files first to get base values
        log.info("Step 1: Processing ESL files for base values");
        for (MultipartFile file : files) {
            try (InputStream inputStream = file.getInputStream()) {
                Document document = parseXmlDocument(inputStream);
                String rootElement = document.getDocumentElement().getNodeName();

                if (rootElement.equals("ESLBillingData")) {
                    log.info("Processing ESL file: {}", file.getOriginalFilename());
                    processEslFileForSensorData(document, sensorValues);
                }
            } catch (Exception e) {
                // Continue with next file if this one fails
                log.error("Error processing ESL file {}: {}", file.getOriginalFilename(), e.getMessage());
            }
        }
        log.info("Completed processing ESL files. Found {} data points for ID742, {} for ID735, and {} for 38157930",
                sensorValues.get("ID742").size(), sensorValues.get("ID735").size(), sensorValues.get("38157930").size());

        // Process SDAT files to get interval data
        log.info("Step 2: Processing SDAT files for interval data");
        for (MultipartFile file : files) {
            try (InputStream inputStream = file.getInputStream()) {
                Document document = parseXmlDocument(inputStream);
                String rootElement = document.getDocumentElement().getNodeName();

                if (rootElement.contains("ValidatedMeteredData")) {
                    log.info("Processing SDAT file: {}", file.getOriginalFilename());
                    processSdatFileForSensorData(document, sensorValues);
                }
            } catch (Exception e) {
                // Continue with next file if this one fails
                log.error("Error processing SDAT file {}: {}", file.getOriginalFilename(), e.getMessage());
            }
        }
        log.info("After SDAT processing: {} data points for ID742, {} for ID735, and {} for 38157930",
                sensorValues.get("ID742").size(), sensorValues.get("ID735").size(), sensorValues.get("38157930").size());

        // Convert the results to the required EnergySensorData format
        List<EnergySensorData> result = new ArrayList<>();
        for (String sensorId : sensorValues.keySet()) {
            TreeMap<LocalDateTime, Double> values = sensorValues.get(sensorId);
            if (!values.isEmpty()) {
                EnergySensorData sensorData = createEnergySensorData(sensorId, values);
                result.add(sensorData);
                log.info("Created EnergySensorData for {} with {} data points", sensorId, sensorData.getData().size());
            } else {
                log.warn("No data points found for sensor ID {}", sensorId);
            }
        }

        return result;
    }

    /**
     * Helper method to parse an XML document from an input stream
     *
     * @param inputStream The input stream to parse
     * @return The parsed XML document
     * @throws ParserConfigurationException If there is an error with the parser configuration
     * @throws SAXException If there is an error parsing the XML
     * @throws IOException If there is an error reading the input stream
     */
    private Document parseXmlDocument(InputStream inputStream) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(inputStream);
        document.getDocumentElement().normalize();
        return document;
    }

    /**
     * Process an ESL format XML document for sensor data
     *
     * @param document The XML document to parse
     * @param sensorValues Map to store the parsed values by sensor ID
     */
    private void processEslFileForSensorData(Document document, Map<String, TreeMap<LocalDateTime, Double>> sensorValues) {
        log.debug("Processing ESL format for sensor data");

        // Maps to store Hochtarif and Niedertarif values temporarily
        Map<LocalDateTime, Double> bezugHochtarif = new HashMap<>();
        Map<LocalDateTime, Double> bezugNiedertarif = new HashMap<>();
        Map<LocalDateTime, Double> einspeisungHochtarif = new HashMap<>();
        Map<LocalDateTime, Double> einspeisungNiedertarif = new HashMap<>();

        NodeList meterNodes = document.getElementsByTagName("Meter");
        for (int i = 0; i < meterNodes.getLength(); i++) {
            Element meterElement = (Element) meterNodes.item(i);
            String meterId = meterElement.getAttribute("factoryNo");
            log.debug("Processing meter with ID: {}", meterId);

            // Get values TreeMap for the device meter if the ID matches
            TreeMap<LocalDateTime, Double> deviceMeterValues = null;
            if (meterId.equals("38157930")) {
                deviceMeterValues = sensorValues.get(meterId);
                log.debug("Found device meter with ID: {}", meterId);
            }

            NodeList timePeriodNodes = meterElement.getElementsByTagName("TimePeriod");
            for (int j = 0; j < timePeriodNodes.getLength(); j++) {
                Element timePeriodElement = (Element) timePeriodNodes.item(j);
                String endTimeStr = timePeriodElement.getAttribute("end");

                try {
                    LocalDateTime timestamp = LocalDateTime.parse(endTimeStr, DATE_TIME_FORMATTER);

                    NodeList valueRowNodes = timePeriodElement.getElementsByTagName("ValueRow");
                    for (int k = 0; k < valueRowNodes.getLength(); k++) {
                        Element valueRowElement = (Element) valueRowNodes.item(k);
                        String obis = valueRowElement.getAttribute("obis");
                        String valueStr = valueRowElement.getAttribute("value");

                        try {
                            double value = Double.parseDouble(valueStr);

                            // Use valueTimeStamp if available, otherwise use endTime
                            if (valueRowElement.hasAttribute("valueTimeStamp")) {
                                String valueTimeStr = valueRowElement.getAttribute("valueTimeStamp");
                                timestamp = LocalDateTime.parse(valueTimeStr, DATE_TIME_FORMATTER);
                            }

                            // Store values in the appropriate map based on OBIS code
                            if (obis.equals("1-1:1.8.1")) { // Bezug Hochtarif
                                bezugHochtarif.put(timestamp, value);
                                log.debug("Found Bezug Hochtarif at {}: {}", timestamp, value);
                            } else if (obis.equals("1-1:1.8.2")) { // Bezug Niedertarif
                                bezugNiedertarif.put(timestamp, value);
                                log.debug("Found Bezug Niedertarif at {}: {}", timestamp, value);
                            } else if (obis.equals("1-1:2.8.1")) { // Einspeisung Hochtarif
                                einspeisungHochtarif.put(timestamp, value);
                                log.debug("Found Einspeisung Hochtarif at {}: {}", timestamp, value);
                            } else if (obis.equals("1-1:2.8.2")) { // Einspeisung Niedertarif
                                einspeisungNiedertarif.put(timestamp, value);
                                log.debug("Found Einspeisung Niedertarif at {}: {}", timestamp, value);
                            }

                            // If this is the device meter, store values directly
                            if (deviceMeterValues != null) {
                                // For simplicity, store all values for the device meter
                                // Alternatively, you could filter by specific OBIS codes
                                deviceMeterValues.put(timestamp, value);
                                log.debug("Added device meter value for {} at {}: {} (OBIS: {})",
                                    meterId, timestamp, value, obis);
                            }
                        } catch (NumberFormatException e) {
                            log.warn("Invalid value format for OBIS {}: {}", obis, valueStr, e);
                        } catch (Exception e) {
                            log.warn("Error processing value row with OBIS {}", obis, e);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error processing time period with end time {}", endTimeStr, e);
                }
            }
        }

        // Calculate combined values and add them to the sensorValues map
        TreeMap<LocalDateTime, Double> id742Values = sensorValues.get("ID742"); // Bezug (consumption)
        TreeMap<LocalDateTime, Double> id735Values = sensorValues.get("ID735"); // Einspeisung (production)

        // Process bezug (consumption) values for ID742
        for (Map.Entry<LocalDateTime, Double> entry : bezugHochtarif.entrySet()) {
            LocalDateTime timestamp = entry.getKey();
            double hochtarifValue = entry.getValue();
            double niedertarifValue = bezugNiedertarif.getOrDefault(timestamp, 0.0);
            double combinedValue = hochtarifValue + niedertarifValue;

            id742Values.put(timestamp, combinedValue);
            log.debug("Added combined Bezug value for ID742 at {}: {}", timestamp, combinedValue);
        }

        // Process einspeisung (production) values for ID735
        for (Map.Entry<LocalDateTime, Double> entry : einspeisungHochtarif.entrySet()) {
            LocalDateTime timestamp = entry.getKey();
            double hochtarifValue = entry.getValue();
            double niedertarifValue = einspeisungNiedertarif.getOrDefault(timestamp, 0.0);
            double combinedValue = hochtarifValue + niedertarifValue;

            id735Values.put(timestamp, combinedValue);
            log.debug("Added combined Einspeisung value for ID735 at {}: {}", timestamp, combinedValue);
        }

        // Add niedertarif-only entries if they exist
        for (Map.Entry<LocalDateTime, Double> entry : bezugNiedertarif.entrySet()) {
            LocalDateTime timestamp = entry.getKey();
            if (!bezugHochtarif.containsKey(timestamp)) {
                id742Values.put(timestamp, entry.getValue());
                log.debug("Added Niedertarif-only Bezug value for ID742 at {}: {}", timestamp, entry.getValue());
            }
        }

        for (Map.Entry<LocalDateTime, Double> entry : einspeisungNiedertarif.entrySet()) {
            LocalDateTime timestamp = entry.getKey();
            if (!einspeisungHochtarif.containsKey(timestamp)) {
                id735Values.put(timestamp, entry.getValue());
                log.debug("Added Niedertarif-only Einspeisung value for ID735 at {}: {}", timestamp, entry.getValue());
            }
        }
    }

    /**
     * Process a SDAT format XML document for sensor data
     *
     * @param document The XML document to parse
     * @param sensorValues Map to store the parsed values by sensor ID
     */
    private void processSdatFileForSensorData(Document document, Map<String, TreeMap<LocalDateTime, Double>> sensorValues) {
        log.debug("Processing SDAT format for sensor data");

        // Extract DocumentID to determine the sensorId
        String sensorId = "";
        NodeList documentIdNodes = document.getElementsByTagName("rsm:DocumentID");
        if (documentIdNodes.getLength() > 0) {
            String fullDocumentId = documentIdNodes.item(0).getTextContent();
            if (fullDocumentId.contains("_ID")) {
                sensorId = fullDocumentId.substring(fullDocumentId.lastIndexOf("_") + 1);
                log.debug("Extracted sensor ID from DocumentID: {}", sensorId);
            }
        }

        // Skip if sensorId is not one of our target IDs
        if (!sensorId.equals("ID742") && !sensorId.equals("ID735")) {
            log.warn("SensorId {} is not one of the expected IDs (ID742 or ID735), trying to determine from data type", sensorId);
            // Attempt to determine from data type if not found in document ID
            sensorId = "";
        }

        NodeList meteringDataNodes = document.getElementsByTagName("rsm:MeteringData");
        for (int i = 0; i < meteringDataNodes.getLength(); i++) {
            Element meteringDataElement = (Element) meteringDataNodes.item(i);

            try {
                // Check if it's production or consumption data to determine sensorId if not already set
                boolean isProduction = meteringDataElement.getElementsByTagName("rsm:ProductionMeteringPoint").getLength() > 0;
                boolean isConsumption = meteringDataElement.getElementsByTagName("rsm:ConsumptionMeteringPoint").getLength() > 0;

                // If sensorId is not already set, determine it from the data type
                if (sensorId.isEmpty()) {
                    if (isProduction) {
                        sensorId = "ID735"; // Production data maps to ID735
                    } else if (isConsumption) {
                        sensorId = "ID742"; // Consumption data maps to ID742
                    } else {
                        log.warn("Could not determine sensor ID for metering data at index {}, skipping", i);
                        continue;
                    }
                }

                TreeMap<LocalDateTime, Double> sensorDataPoints = sensorValues.get(sensorId);
                if (sensorDataPoints == null) {
                    log.warn("No data map found for sensor ID {}, skipping", sensorId);
                    continue;
                }

                // Get interval information
                Element intervalElement = (Element) meteringDataElement.getElementsByTagName("rsm:Interval").item(0);
                if (intervalElement == null) {
                    log.warn("No interval element found for metering data node {}, skipping", i);
                    continue;
                }

                String startTimeStr = getTextContent(intervalElement, "rsm:StartDateTime");
                LocalDateTime startTime;
                try {
                    startTime = LocalDateTime.parse(startTimeStr, DATE_TIME_FORMATTER);
                } catch (Exception e) {
                    log.warn("Invalid start time format: {}, skipping", startTimeStr, e);
                    continue;
                }

                // Get resolution
                Element resolutionElement = (Element) meteringDataElement.getElementsByTagName("rsm:Resolution").item(0);
                if (resolutionElement == null) {
                    log.warn("No resolution element found for metering data node {}, skipping", i);
                    continue;
                }

                int resolution;
                try {
                    resolution = Integer.parseInt(getTextContent(resolutionElement, "rsm:Resolution"));
                } catch (NumberFormatException e) {
                    log.warn("Invalid resolution format, skipping metering data", e);
                    continue;
                }

                // Find the base value for this day
                LocalDateTime dayStart = startTime.withHour(0).withMinute(0).withSecond(0).withNano(0);

                // Look for base value (the latest absolute value before this day's start)
                Double baseValue = null;
                Map.Entry<LocalDateTime, Double> baseEntry = sensorDataPoints.floorEntry(dayStart);
                if (baseEntry != null) {
                    baseValue = baseEntry.getValue();
                    log.debug("Found base value for {} at {}: {}", sensorId, baseEntry.getKey(), baseValue);
                } else {
                    // Look for the earliest value in this day as a fallback
                    baseEntry = sensorDataPoints.ceilingEntry(dayStart);
                    if (baseEntry != null && baseEntry.getKey().toLocalDate().equals(dayStart.toLocalDate())) {
                        baseValue = baseEntry.getValue();
                        log.debug("Using same day earliest value for {} as base: {}", sensorId, baseValue);
                    }
                }

                // If no base value is found, use 0 as fallback
                if (baseValue == null) {
                    baseValue = 0.0;
                    log.warn("No base value found for {}, using 0 as fallback", sensorId);
                }

                // Process observations
                NodeList observationNodes = meteringDataElement.getElementsByTagName("rsm:Observation");

                // Keep track of cumulative value
                double cumulativeValue = baseValue;

                // Sort observations by sequence
                Map<Integer, Element> sortedObservations = new TreeMap<>();
                for (int j = 0; j < observationNodes.getLength(); j++) {
                    Element observationElement = (Element) observationNodes.item(j);
                    Element positionElement = (Element) observationElement.getElementsByTagName("rsm:Position").item(0);
                    if (positionElement == null) continue;

                    try {
                        int sequence = Integer.parseInt(getTextContent(positionElement, "rsm:Sequence"));
                        sortedObservations.put(sequence, observationElement);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid sequence format in observation", e);
                    }
                }

                // Process observations in sequence order
                for (Map.Entry<Integer, Element> entry : sortedObservations.entrySet()) {
                    int sequence = entry.getKey();
                    Element observationElement = entry.getValue();

                    try {
                        String volumeStr = getTextContent(observationElement, "rsm:Volume");
                        double volume = Double.parseDouble(volumeStr);

                        // Calculate timestamp for this observation
                        LocalDateTime timestamp = startTime.plusMinutes((sequence - 1) * resolution);

                        // Add volume to cumulative value
                        cumulativeValue += volume;

                        // Check if we already have an absolute value at this timestamp from an ESL file
                        if (!sensorDataPoints.containsKey(timestamp)) {
                            // Only add if not already present (ESL values take precedence)
                            sensorDataPoints.put(timestamp, cumulativeValue);
                            log.debug("Added accumulated value for {} at {}: {}", sensorId, timestamp, cumulativeValue);
                        } else {
                            // If ESL value exists, use it to realign our cumulative calculation
                            double eslValue = sensorDataPoints.get(timestamp);
                            cumulativeValue = eslValue;
                            log.debug("Found existing ESL value for {} at {}: {}, realigning cumulative value",
                                    sensorId, timestamp, eslValue);
                        }
                    } catch (NumberFormatException e) {
                        log.warn("Invalid volume format in observation", e);
                    }
                }
            } catch (Exception e) {
                log.error("Error processing metering data node {}", i, e);
            }
        }
    }

    /**
     * Create an EnergySensorData object from a map of timestamp to value
     *
     * @param sensorId The sensor ID
     * @param values The map of timestamp to value
     * @return The EnergySensorData object
     */
    private EnergySensorData createEnergySensorData(String sensorId, TreeMap<LocalDateTime, Double> values) {
        EnergySensorData sensorData = new EnergySensorData();
        sensorData.setSensorId(sensorId);

        List<EnergySensorData.DataPoint> dataPoints = new ArrayList<>();

        for (Map.Entry<LocalDateTime, Double> entry : values.entrySet()) {
            LocalDateTime timestamp = entry.getKey();
            Double value = entry.getValue();

            // Convert LocalDateTime to UTC seconds since epoch
            long epochSeconds = timestamp.toInstant(ZoneOffset.UTC).getEpochSecond();

            EnergySensorData.DataPoint dataPoint = new EnergySensorData.DataPoint();
            dataPoint.setTs(String.valueOf(epochSeconds));
            dataPoint.setValue(value);

            dataPoints.add(dataPoint);
        }

        sensorData.setData(dataPoints);
        return sensorData;
    }
}
