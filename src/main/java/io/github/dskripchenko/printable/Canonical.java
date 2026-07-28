package io.github.dskripchenko.printable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * PHP-compatible payload canonicalization — byte-identical to the Printable
 * server ({@code App\Auth\HashSigner::canonicalize}):
 *
 * <ul>
 *   <li>recursive key sort for maps (lists keep their order);</li>
 *   <li>PHP {@code JSON_NUMERIC_CHECK} semantics: numeric strings become
 *       numbers ("42" → 42, "100.0" → 100, "1e3" → 1000, "007" → 7);</li>
 *   <li>empty maps encode as {@code []} (PHP array semantics);</li>
 *   <li>maps with sequential "0".."n" keys encode as arrays;</li>
 *   <li>unicode and slashes are not escaped.</li>
 * </ul>
 */
public final class Canonical {

    private static final Pattern NUMERIC = Pattern.compile("^[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?$");

    private Canonical() {
    }

    public static String canonicalize(Map<String, ?> payload) {
        StringBuilder out = new StringBuilder();
        encode(payload, out);
        return out.toString();
    }

    private static void encode(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
            return;
        }
        if (value instanceof Boolean bool) {
            out.append(bool ? "true" : "false");
            return;
        }
        if (value instanceof Number number) {
            encodeNumber(number, out);
            return;
        }
        if (value instanceof String string) {
            if (isNumericString(string)) {
                encodeNumericString(string.trim(), out);
            } else {
                encodeString(string, out);
            }
            return;
        }
        if (value instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                encode(list.get(i), out);
            }
            out.append(']');
            return;
        }
        if (value instanceof Map<?, ?> map) {
            encodeMap(map, out);
            return;
        }
        encodeString(String.valueOf(value), out);
    }

    private static void encodeMap(Map<?, ?> map, StringBuilder out) {
        if (map.isEmpty()) {
            out.append("[]"); // PHP: пустой объект после json_decode(assoc) — []
            return;
        }
        List<String> keys = new ArrayList<>();
        for (Object key : map.keySet()) {
            keys.add(String.valueOf(key));
        }

        // Ключи "0".."n" PHP считает списком.
        boolean sequential = true;
        for (int i = 0; i < keys.size(); i++) {
            if (!keys.get(i).equals(String.valueOf(i))) {
                sequential = false;
                break;
            }
        }
        if (sequential) {
            out.append('[');
            int i = 0;
            for (Object item : map.values()) {
                if (i++ > 0) {
                    out.append(',');
                }
                encode(item, out);
            }
            out.append(']');
            return;
        }

        // PHP ksort: числовые ключи сравниваются как числа, прочие — как строки.
        keys.sort(Comparator
                .comparingInt((String k) -> isNumericString(k) ? 0 : 1)
                .thenComparing(k -> isNumericString(k) ? "" : k)
                .thenComparingDouble(k -> isNumericString(k) ? Double.parseDouble(k) : 0));

        out.append('{');
        boolean first = true;
        for (String key : keys) {
            if (!first) {
                out.append(',');
            }
            first = false;
            encodeString(key, out);
            out.append(':');
            encode(valueForKey(map, key), out);
        }
        out.append('}');
    }

    private static Object valueForKey(Map<?, ?> map, String key) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (String.valueOf(entry.getKey()).equals(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static void encodeNumericString(String value, StringBuilder out) {
        double number = Double.parseDouble(value);
        if (number == Math.rint(number) && !Double.isInfinite(number)) {
            out.append((long) number);
        } else {
            encodeNumber(number, out);
        }
    }

    private static void encodeNumber(Number number, StringBuilder out) {
        if (number instanceof Double || number instanceof Float) {
            double d = number.doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                // json.dumps(2.0) в Python — "2.0"; PHP float 2.0 — "2.0".
                out.append((long) d).append(".0");
            } else {
                out.append(d);
            }
        } else {
            out.append(number);
        }
    }

    private static void encodeString(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        out.append('"');
    }

    static boolean isNumericString(String value) {
        String trimmed = value.trim();
        return !trimmed.isEmpty() && NUMERIC.matcher(trimmed).matches();
    }
}
