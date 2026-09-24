package ht.dinasa.fact.facturation.domain;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class NumeroFacture {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyMMddHHmmss");

    private NumeroFacture() {
    }

    /** Ex. : transporteur 42, le 24/09/2026 à 14:05:09 → "42-260924140509-U" */
    static String auGallon(long codeTransporteur, LocalDateTime date) {
        return codeTransporteur + "-" + FORMAT.format(date) + "-U";
    }
}