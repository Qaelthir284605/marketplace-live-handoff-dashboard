package example.marketplace;

import java.lang.reflect.Array;
import java.lang.reflect.RecordComponent;
import java.util.Iterator;
import java.util.Map;

final class Json {
    private Json() {}

    static String write(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return quote(text);
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{");
            Iterator<? extends Map.Entry<?, ?>> entries = map.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry<?, ?> entry = entries.next();
                out.append(quote(entry.getKey().toString())).append(':').append(write(entry.getValue()));
                if (entries.hasNext()) out.append(',');
            }
            return out.append('}').toString();
        }
        if (value instanceof Iterable<?> values) {
            StringBuilder out = new StringBuilder("[");
            Iterator<?> iterator = values.iterator();
            while (iterator.hasNext()) {
                out.append(write(iterator.next()));
                if (iterator.hasNext()) out.append(',');
            }
            return out.append(']').toString();
        }
        if (value.getClass().isArray()) {
            StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < Array.getLength(value); i++) {
                if (i > 0) out.append(',');
                out.append(write(Array.get(value, i)));
            }
            return out.append(']').toString();
        }
        if (value.getClass().isRecord()) {
            java.util.LinkedHashMap<String, Object> fields = new java.util.LinkedHashMap<>();
            try {
                for (RecordComponent component : value.getClass().getRecordComponents()) {
                    fields.put(component.getName(), component.getAccessor().invoke(value));
                }
            } catch (ReflectiveOperationException error) {
                throw new IllegalArgumentException("Cannot encode record", error);
            }
            return write(fields);
        }
        throw new IllegalArgumentException("Cannot encode " + value.getClass().getName());
    }

    static String stringField(String json, String field) {
        String marker = quote(field) + ":";
        int start = json.indexOf(marker);
        if (start < 0) return null;
        start += marker.length();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length() || json.charAt(start) != '"') return null;
        StringBuilder value = new StringBuilder();
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') return value.toString();
            if (c == '\\' && i + 1 < json.length()) c = json.charAt(++i);
            value.append(c);
        }
        return null;
    }

    static boolean booleanField(String json, String field) {
        String marker = quote(field) + ":";
        int start = json.indexOf(marker);
        if (start < 0) return false;
        start += marker.length();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        return json.startsWith("true", start);
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }
}
