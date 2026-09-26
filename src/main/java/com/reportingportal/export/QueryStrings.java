package com.reportingportal.export;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a stored query string such as "from_month=2017-01&sort=revenue&dir=desc"
 * back into the same shape as HttpServletRequest.getParameterMap(), so a
 * schedule's parameters go through exactly the same ParamValidator as a live request.
 */
final class QueryStrings {

    private QueryStrings() {
    }

    static Map<String, String[]> parse(String query) {
        Map<String, List<String>> collected = new LinkedHashMap<>();
        if (query != null && !query.isEmpty()) {
            for (String pair : query.split("&")) {
                int equals = pair.indexOf('=');
                String name = decode(equals < 0 ? pair : pair.substring(0, equals));
                String value = equals < 0 ? "" : decode(pair.substring(equals + 1));
                collected.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
            }
        }
        Map<String, String[]> parameters = new LinkedHashMap<>();
        collected.forEach((name, values) -> parameters.put(name, values.toArray(new String[0])));
        return parameters;
    }

    private static String decode(String text) {
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }
}
