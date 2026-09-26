package com.reportingportal.report;

import java.util.List;

/** The 27 Brazilian federative units (26 states + the Federal District): the only accepted state codes. */
public final class BrazilStates {

    public static final List<String> CODES = List.of(
            "AC", "AL", "AM", "AP", "BA", "CE", "DF", "ES", "GO", "MA", "MG", "MS", "MT", "PA",
            "PB", "PE", "PI", "PR", "RJ", "RN", "RO", "RR", "RS", "SC", "SE", "SP", "TO");

    private BrazilStates() {
    }

    public static boolean isValid(String code) {
        return CODES.contains(code);
    }
}
