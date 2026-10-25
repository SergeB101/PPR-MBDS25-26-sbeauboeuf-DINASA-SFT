package ht.dinasa.fact.facturation.web;

import ht.dinasa.fact.facturation.application.ExportDocuments;
import ht.dinasa.fact.facturation.application.ExportDocuments.Fichier;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/exports")
class ExportsController {

    private final ExportDocuments exports;

    ExportsController(ExportDocuments exports) {
        this.exports = exports;
    }

    @Operation(summary = "Facture en PDF")
    @GetMapping("/factures/{id}/pdf")
    ResponseEntity<byte[]> facturePdf(@PathVariable long id) {
        return telecharger(exports.facturePdf(id));
    }

    @Operation(summary = "Facture en Excel (détail des lignes)")
    @GetMapping("/factures/{id}/excel")
    ResponseEntity<byte[]> factureExcel(@PathVariable long id) {
        return telecharger(exports.factureExcel(id));
    }

    @Operation(summary = "Liste de factures en Excel (5 000 au plus)")
    @GetMapping("/factures/excel")
    ResponseEntity<byte[]> listeExcel(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
                                      @RequestParam(required = false) Long idTransporteur,
                                      @RequestParam(required = false) Boolean postee) {
        return telecharger(exports.listeExcel(du, au, idTransporteur, postee));
    }

    @Operation(summary = "Relevé des factures d'un transporteur en PDF")
    @GetMapping("/releve/pdf")
    ResponseEntity<byte[]> relevePdf(@RequestParam long idTransporteur,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
        return telecharger(exports.relevePdf(idTransporteur, du, au));
    }

    @Operation(summary = "Relevé des factures d'un transporteur en Excel")
    @GetMapping("/releve/excel")
    ResponseEntity<byte[]> releveExcel(@RequestParam long idTransporteur,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
        return telecharger(exports.releveExcel(idTransporteur, du, au));
    }

    private static ResponseEntity<byte[]> telecharger(Fichier f) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.typeMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.nom(), StandardCharsets.UTF_8).build().toString())
                .body(f.contenu());
    }
}