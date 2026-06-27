package com.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Locale;

public final class DeclarationPayloads {

    private static final String[][] SOURCE_PERMIT_TYPE_MAPPINGS = new String[][] {
            { "IM", "IM Permit" },
            { "IT", "IT Permit" },
            { "IR", "IR Permit" },
            { "IN", "IN Permit" },
            { "IE", "IE Permit" },
            { "II", "II Permit" },
            { "ME", "ME Permit" },
            { "IG", "IG Permit" },
            { "ID", "ID Permit" },
            { "DP", "DP Permit" },
            { "TT", "TT Permit" },
            { "TW", "TW Permit" }
    };

    private DeclarationPayloads() {
    }

    public static JsonNode unwrap(JsonNode payload) {
        if (payload == null || payload.isMissingNode() || payload.isNull()) {
            return MissingNode.getInstance();
        }

        JsonNode inboundMessage = payload.path("inboundMessage");
        if (inboundMessage.isObject()) {
            return inboundMessage;
        }

        return payload;
    }

    public static boolean isWrapped(JsonNode payload) {
        return payload != null && payload.path("inboundMessage").isObject();
    }

    public static ObjectNode unwrapObject(ObjectNode payload) {
        if (payload == null) {
            return null;
        }
        JsonNode unwrapped = unwrap(payload);
        return unwrapped instanceof ObjectNode objectNode ? objectNode : payload;
    }

    public static String detectDeclarationFamily(JsonNode payload) {
        JsonNode unwrapped = unwrap(payload);
        String[] candidates = new String[] {
                text(payload, "type"),
                text(unwrapped, "type"),
                text(unwrapped.path("header"), "commonAccessReference"),
                text(unwrapped.path("header"), "applicationType")
        };

        for (String candidate : candidates) {
            String normalized = normalize(candidate);
            if (normalized.isBlank()) {
                continue;
            }
            if (normalized.contains("TNP") || normalized.contains("TRANSHIPMENT")) {
                return "TNP";
            }
            if (normalized.contains("INP") || normalized.contains("INNONPAYMENT")) {
                return "INP";
            }
            if (normalized.contains("IPT") || normalized.contains("INPAYMENT")) {
                return "IPT";
            }
            if (normalized.contains("COO") || normalized.contains("CERTIFICATEOFORIGIN")) {
                return "COO";
            }
            if (normalized.contains("OUT")) {
                return "OUT";
            }
        }
        return null;
    }

    public static boolean matchesDeclarationFamily(JsonNode payload, String expectedFamily) {
        if (expectedFamily == null || expectedFamily.isBlank()) {
            return false;
        }
        String detected = detectDeclarationFamily(payload);
        return expectedFamily.trim().equalsIgnoreCase(detected);
    }

    public static JsonNode annotatePermitType(JsonNode payload, String sourceLabel) {
        if (payload == null || payload.isMissingNode() || payload.isNull()) {
            return payload;
        }

        if (payload.isArray()) {
            for (JsonNode entry : payload) {
                annotatePermitType(entry, null);
            }
            return payload;
        }

        if (!(payload instanceof ObjectNode objectNode)) {
            return payload;
        }

        ObjectNode target = unwrapObject(objectNode);
        if (target == null || text(target, "permitType") != null) {
            return payload;
        }

        String resolvedPermitType = resolvePermitType(payload, sourceLabel);
        if (resolvedPermitType != null) {
            target.put("permitType", resolvedPermitType);
        }
        return payload;
    }

    public static String resolvePermitType(JsonNode payload, String sourceLabel) {
        JsonNode unwrapped = unwrap(payload);
        return firstNonBlank(
                text(payload, "permitType"),
                text(unwrapped, "permitType"),
                inferPermitTypeFromSourceLabel(sourceLabel),
                inferPermitTypeFromPayload(unwrapped));
    }

    private static String text(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z]", "");
    }

    private static String inferPermitTypeFromSourceLabel(String sourceLabel) {
        if (sourceLabel == null || sourceLabel.isBlank()) {
            return null;
        }

        String normalizedSource = sourceLabel.replace('\\', '/');
        int slashIndex = normalizedSource.lastIndexOf('/');
        String filename = slashIndex >= 0 ? normalizedSource.substring(slashIndex + 1) : normalizedSource;
        int extensionIndex = filename.lastIndexOf('.');
        String stem = extensionIndex > 0 ? filename.substring(0, extensionIndex) : filename;
        String[] tokens = stem.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]+", " ").trim().split("\\s+");

        for (String[] mapping : SOURCE_PERMIT_TYPE_MAPPINGS) {
            for (String token : tokens) {
                if (mapping[0].equals(token)) {
                    return mapping[1];
                }
            }
        }
        return null;
    }

    private static String inferPermitTypeFromPayload(JsonNode payload) {
        if (payload == null || payload.isMissingNode() || payload.isNull()) {
            return null;
        }

        String family = detectDeclarationFamily(payload);
        String declarationType = text(payload.path("header"), "declarationType");
        if (family == null || declarationType == null) {
            return null;
        }

        return switch (family.toUpperCase(Locale.ROOT)) {
            case "INP" -> switch (declarationType) {
                case "21" -> "IE Permit";
                case "22" -> "IN Permit";
                case "24" -> "IR Permit";
                default -> null;
            };
            case "IPT" -> switch (declarationType) {
                case "12" -> "ID Permit";
                default -> null;
            };
            case "TNP" -> switch (declarationType) {
                case "70" -> "TT Permit";
                case "72" -> "TW Permit";
                default -> null;
            };
            default -> null;
        };
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
