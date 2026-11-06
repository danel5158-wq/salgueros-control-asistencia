package com.salgueros.asistencia.controller;

import com.salgueros.asistencia.service.ExcelReporteService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/reportes/asistencia")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReporteExcelController {

    private final ExcelReporteService excelReporteService;

    @GetMapping("/excel")
    public ResponseEntity<byte[]> descargarReporteExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {

        if (fechaInicio == null) fechaInicio = LocalDate.now().minusDays(7);
        if (fechaFin == null) fechaFin = LocalDate.now();

        try {
            byte[] excelBytes = excelReporteService.generarReporteAsistenciaExcel(fechaInicio, fechaFin);
            String filename = "Reporte_Asistencia_Salgueros_" + fechaInicio + "_al_" + fechaFin + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excelBytes);

        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
