package catcatch;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Protocol {
    private Protocol() {
    }

    static String encode(String type, Map<String, String> fields) {
        StringBuilder builder = new StringBuilder(type);
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            builder.append('|')
                .append(entry.getKey())
                .append('=')
                .append(encodeValue(entry.getValue()));
        }
        return builder.toString();
    }

    static String encode(String type, String... keyValues) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            fields.put(keyValues[i], keyValues[i + 1]);
        }
        return encode(type, fields);
    }

    static Message parse(String line) {
        String[] parts = line.split("\\|");
        String type = parts[0];
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 1; i < parts.length; i++) {
            int index = parts[i].indexOf('=');
            if (index <= 0) {
                continue;
            }
            String key = parts[i].substring(0, index);
            String value = parts[i].substring(index + 1);
            fields.put(key, decodeValue(value));
        }
        return new Message(type, fields);
    }

    static String encodeList(List<String[]> rows) {
        List<String> encodedRows = new ArrayList<>();
        for (String[] row : rows) {
            List<String> encoded = new ArrayList<>();
            for (String value : row) {
                encoded.add(encodeValue(value));
            }
            encodedRows.add(String.join("~", encoded));
        }
        return String.join(",", encodedRows);
    }

    static List<String[]> decodeList(String raw) {
        List<String[]> rows = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return rows;
        }
        String[] rowParts = raw.split(",");
        for (String row : rowParts) {
            if (row.isBlank()) {
                continue;
            }
            String[] cells = row.split("~", -1);
            String[] decoded = new String[cells.length];
            for (int i = 0; i < cells.length; i++) {
                decoded[i] = decodeValue(cells[i]);
            }
            rows.add(decoded);
        }
        return rows;
    }

    private static String encodeValue(String value) {
        if (value == null) {
            return "";
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeValue(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    record Message(String type, Map<String, String> fields) {
        String get(String key) {
            return fields.getOrDefault(key, "");
        }
    }
}
