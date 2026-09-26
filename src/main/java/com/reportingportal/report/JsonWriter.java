package com.reportingportal.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Map;

/**
 * Turns plain Java values into JSON text: Map (object), Collection (array),
 * String, BigDecimal and other numbers, Boolean, LocalDate/YearMonth (ISO
 * strings) and null. The fixed stack has no JSON library, and this is all the
 * portal needs.
 *
 * Escaping: besides what JSON requires (quote, backslash, control characters),
 * the characters < > & are written as \\u003c \\u003e \\u0026. The output is then
 * harmless even if it were ever placed inside an HTML page, e.g. a category
 * named "</script>" could not end a script block.
 */
public final class JsonWriter {

    private JsonWriter() {
    }

    public static String toJson(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            writeString(text, out);
        } else if (value instanceof BigDecimal number) {
            out.append(number.toPlainString());     // never 1E+3 notation
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof LocalDate || value instanceof YearMonth) {
            writeString(value.toString(), out);      // 2017-06-01, 2017-06
        } else if (value instanceof Map<?, ?> map) {
            writeObject(map, out);
        } else if (value instanceof Collection<?> list) {
            writeArray(list, out);
        } else {
            throw new IllegalArgumentException("Cannot write " + value.getClass().getName() + " as JSON");
        }
    }

    private static void writeObject(Map<?, ?> map, StringBuilder out) {
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString(String.valueOf(entry.getKey()), out);
            out.append(':');
            write(entry.getValue(), out);
        }
        out.append('}');
    }

    private static void writeArray(Collection<?> list, StringBuilder out) {
        out.append('[');
        boolean first = true;
        for (Object item : list) {
            if (!first) {
                out.append(',');
            }
            first = false;
            write(item, out);
        }
        out.append(']');
    }

    private static void writeString(String text, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '<', '>', '&', ' ', ' ' -> out.append(String.format("\\u%04x", (int) c));
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));   // other control characters
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
