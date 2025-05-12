package ch.bzz.backend.service;

import ch.bzz.backend.model.EnergyData;
import ch.bzz.backend.model.Measurement;
import ch.bzz.backend.model.Messwert;
import ch.bzz.backend.model.StromzaehlerDaten;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

    @Override
    public List<EnergyData> parseXml(InputStream inputStream) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(inputStream);
            document.getDocumentElement().normalize();

            String rootElement = document.getDocumentElement().getNodeName();

            if (rootElement.equals("ESLBillingData")) {
                return parseESLBillingData(document);
            } else if (rootElement.contains("ValidatedMeteredData")) {
                return parseValidatedMeteredData(document);
            } else {
                throw new IllegalArgumentException("Unknown XML format: " + rootElement);
            }
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new RuntimeException("Error parsing XML file", e);
        }
    }

    private List<EnergyData> parseESLBillingData(Document document) {
        List<EnergyData> result = new ArrayList<>();

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

                        List<Measurement> measurements = new ArrayList<>();
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

                                // Determine if it's production or consumption based on OBIS code
                                EnergyData.DataType dataType = determineDataTypeFromObis(obis);

                                Measurement measurement = Measurement.builder()
                                        .type(dataType)
                                        .identifier(obis)
                                        .myvalue(value)
                                        .timestamp(timestamp)
                                        .unit("KWH") // Assuming KWH for ESLBillingData
                                        .build();

                                measurements.add(measurement);
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
                        }
                    }
                }
            }
        }

        return result;
    }

    private List<EnergyData> parseValidatedMeteredData(Document document) {
        List<EnergyData> result = new ArrayList<>();

        NodeList meteringDataNodes = document.getElementsByTagName("rsm:MeteringData");
        for (int i = 0; i < meteringDataNodes.getLength(); i++) {
            Node meteringDataNode = meteringDataNodes.item(i);
            if (meteringDataNode.getNodeType() == Node.ELEMENT_NODE) {
                Element meteringDataElement = (Element) meteringDataNode;

                // Check if it's production or consumption data
                boolean isProduction = meteringDataElement.getElementsByTagName("rsm:ProductionMeteringPoint").getLength() > 0;
                boolean isConsumption = meteringDataElement.getElementsByTagName("rsm:ConsumptionMeteringPoint").getLength() > 0;

                EnergyData.DataType dataType = isProduction ? EnergyData.DataType.PRODUCTION : EnergyData.DataType.CONSUMPTION;

                // Get meter ID
                String meterId = "";
                if (isProduction) {
                    Element productionElement = (Element) meteringDataElement.getElementsByTagName("rsm:ProductionMeteringPoint").item(0);
                    meterId = getTextContent(productionElement, "rsm:VSENationalID");
                } else if (isConsumption) {
                    Element consumptionElement = (Element) meteringDataElement.getElementsByTagName("rsm:ConsumptionMeteringPoint").item(0);
                    meterId = getTextContent(consumptionElement, "rsm:VSENationalID");
                }

                // Get interval
                Element intervalElement = (Element) meteringDataElement.getElementsByTagName("rsm:Interval").item(0);
                String startTimeStr = getTextContent(intervalElement, "rsm:StartDateTime");
                String endTimeStr = getTextContent(intervalElement, "rsm:EndDateTime");
                LocalDateTime startTime = LocalDateTime.parse(startTimeStr, DATE_TIME_FORMATTER);
                LocalDateTime endTime = LocalDateTime.parse(endTimeStr, DATE_TIME_FORMATTER);

                // Get unit
                Element productElement = (Element) meteringDataElement.getElementsByTagName("rsm:Product").item(0);
                String unit = getTextContent(productElement, "rsm:MeasureUnit");

                // Get observations
                List<Measurement> measurements = new ArrayList<>();
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
                        Element resolutionElement = (Element) meteringDataElement.getElementsByTagName("rsm:Resolution").item(0);
                        String resolutionStr = getTextContent(resolutionElement, "rsm:Resolution");
                        int resolution = Integer.parseInt(resolutionStr);

                        // Assuming resolution is in minutes
                        LocalDateTime timestamp = startTime.plusMinutes((sequence - 1) * resolution);

                        Measurement measurement = Measurement.builder()
                                .type(dataType)
                                .identifier(String.valueOf(sequence))
                                .myvalue(volume)
                                .timestamp(timestamp)
                                .unit(unit)
                                .build();

                        measurements.add(measurement);
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
                }
            }
        }

        return result;
    }

    private String getTextContent(Element element, String tagName) {
        NodeList nodeList = element.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return "";
    }

    private EnergyData.DataType determineDataTypeFromObis(String obis) {
        // OBIS codes starting with "1-1:1" are typically for consumption
        // OBIS codes starting with "1-1:2" are typically for production
        if (obis.startsWith("1-1:1")) {
            return EnergyData.DataType.CONSUMPTION;
        } else if (obis.startsWith("1-1:2")) {
            return EnergyData.DataType.PRODUCTION;
        } else {
            // Default to consumption if we can't determine
            return EnergyData.DataType.CONSUMPTION;
        }
    }

    @Override
    public Map<String, StromzaehlerDaten> parseXmlToStromzaehlerDaten(InputStream inputStream) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(inputStream);
            document.getDocumentElement().normalize();

            String rootElement = document.getDocumentElement().getNodeName();

            if (rootElement.equals("ESLBillingData")) {
                return parseESLBillingDataToStromzaehlerDaten(document);
            } else if (rootElement.contains("ValidatedMeteredData")) {
                return parseValidatedMeteredDataToStromzaehlerDaten(document);
            } else {
                throw new IllegalArgumentException("Unknown XML format: " + rootElement);
            }
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new RuntimeException("Error parsing XML file", e);
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

    @Override
    public Map<String, StromzaehlerDaten> processMultipleFiles(List<InputStream> inputStreams) {
        // Combined result map
        Map<String, StromzaehlerDaten> combinedResult = new HashMap<>();

        // Process all input streams
        for (InputStream inputStream : inputStreams) {
            Map<String, StromzaehlerDaten> fileResult = parseXmlToStromzaehlerDaten(inputStream);

            // Merge results
            for (Map.Entry<String, StromzaehlerDaten> entry : fileResult.entrySet()) {
                String meterId = entry.getKey();
                StromzaehlerDaten fileData = entry.getValue();

                // Get or create StromzaehlerDaten for this meter
                StromzaehlerDaten combinedData = combinedResult.computeIfAbsent(meterId, StromzaehlerDaten::new);

                // Add all measurements from this file
                for (Map.Entry<LocalDateTime, Messwert> messwertEntry : fileData.getAllMesswerte().entrySet()) {
                    combinedData.addMesswert(messwertEntry.getValue());
                }
            }
        }

        // Calculate relative values for each meter
        for (StromzaehlerDaten stromzaehlerDaten : combinedResult.values()) {
            calculateRelativeValues(stromzaehlerDaten);
        }

        return combinedResult;
    }

    /**
     * Calculate relative values for all measurements in a StromzaehlerDaten object
     * The relative value is the difference between the current absolute value and the previous absolute value
     * 
     * @param stromzaehlerDaten The StromzaehlerDaten object to process
     */
    private void calculateRelativeValues(StromzaehlerDaten stromzaehlerDaten) {
        TreeMap<LocalDateTime, Messwert> messwerte = stromzaehlerDaten.getAllMesswerte();

        // We need at least two measurements to calculate relative values
        if (messwerte.size() < 2) {
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

        // Calculate relative values for each type
        for (EnergyData.DataType type : typeMap.keySet()) {
            TreeMap<LocalDateTime, Messwert> typedMesswerte = typeMap.get(type);

            if (typedMesswerte.size() < 2) {
                continue;
            }

            // Get the first entry to use as previous value
            Map.Entry<LocalDateTime, Messwert> previousEntry = typedMesswerte.firstEntry();

            // Iterate through the rest of the entries
            for (Map.Entry<LocalDateTime, Messwert> entry : typedMesswerte.entrySet()) {
                // Skip the first entry
                if (entry.getKey().equals(previousEntry.getKey())) {
                    continue;
                }

                Messwert currentMesswert = entry.getValue();
                Messwert previousMesswert = previousEntry.getValue();

                // Calculate relative value
                double relativeValue = currentMesswert.getAbsoluteValue() - previousMesswert.getAbsoluteValue();

                // Update the messwert
                currentMesswert.setRelativeValue(relativeValue);

                // Update previous entry for next iteration
                previousEntry = entry;
            }
        }
    }
}
