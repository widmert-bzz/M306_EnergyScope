package ch.bzz.backend.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Model class representing the ValidatedMeteredData XML format
 */
@Data
public class ValidatedMeteredData {
    private HeaderInformation headerInformation;
    private List<MeteringData> meteringDataList;

    @Data
    public static class HeaderInformation {
        private String headerVersion;
        private Sender sender;
        private Receiver receiver;
        private InstanceDocument instanceDocument;
        private BusinessScopeProcess businessScopeProcess;
    }

    @Data
    public static class Sender {
        private ID id;
        private String role;
    }

    @Data
    public static class Receiver {
        private ID id;
        private String role;
    }

    @Data
    public static class ID {
        private String eicid;
    }

    @Data
    public static class InstanceDocument {
        private String dictionaryAgencyID;
        private String versionID;
        private String documentID;
        private DocumentType documentType;
        private LocalDateTime creation;
        private String status;
    }

    @Data
    public static class DocumentType {
        private String ebIXCode;
    }

    @Data
    public static class BusinessScopeProcess {
        private BusinessReasonType businessReasonType;
        private String businessDomainType;
        private String businessSectorType;
        private ReportPeriod reportPeriod;
        private BusinessService businessService;
    }

    @Data
    public static class BusinessReasonType {
        private String ebIXCode;
    }

    @Data
    public static class ReportPeriod {
        private LocalDateTime startDateTime;
        private LocalDateTime endDateTime;
    }

    @Data
    public static class BusinessService {
        private ServiceTransaction serviceTransaction;
    }

    @Data
    public static class ServiceTransaction {
        private boolean isIntelligibleCheckRequired;
    }

    @Data
    public static class MeteringData {
        private String documentID;
        private Interval interval;
        private Resolution resolution;
        private String productionMeteringPoint; // For production data
        private String consumptionMeteringPoint; // For consumption data
        private Product product;
        private List<Observation> observations;
    }

    @Data
    public static class Interval {
        private LocalDateTime startDateTime;
        private LocalDateTime endDateTime;
    }

    @Data
    public static class Resolution {
        private int resolution;
        private String unit;
    }

    @Data
    public static class Product {
        private String id;
        private String measureUnit;
    }

    @Data
    public static class Observation {
        private Position position;
        private double volume;
    }

    @Data
    public static class Position {
        private int sequence;
    }
}