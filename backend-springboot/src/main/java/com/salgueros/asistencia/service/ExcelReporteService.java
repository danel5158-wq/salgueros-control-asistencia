package com.salgueros.asistencia.service;

import com.salgueros.asistencia.model.EstadoLlegada;
import com.salgueros.asistencia.model.RegistroAsistencia;
import com.salgueros.asistencia.repository.RegistroAsistenciaRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExcelReporteService {

    private final RegistroAsistenciaRepository registroRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public byte[] generarReporteAsistenciaExcel(LocalDate inicio, LocalDate fin) throws IOException {
        List<RegistroAsistencia> registros = registroRepository.findRegistrosEntreFechas(inicio, fin);

        // Ordenar primero por Área (PLANTA, VENTAS, TRANSPORTADORES, ADMINISTRADORES), luego por Empleado y Fecha
        registros.sort((r1, r2) -> {
            String a1 = r1.getProgramacion().getEmpleado().getArea() != null ? r1.getProgramacion().getEmpleado().getArea() : "PLANTA";
            String a2 = r2.getProgramacion().getEmpleado().getArea() != null ? r2.getProgramacion().getEmpleado().getArea() : "PLANTA";
            int cArea = a1.compareToIgnoreCase(a2);
            if (cArea != 0) return cArea;

            String nom1 = r1.getProgramacion().getEmpleado().getNombreApellido();
            String nom2 = r2.getProgramacion().getEmpleado().getNombreApellido();
            int cNom = nom1.compareToIgnoreCase(nom2);
            if (cNom != 0) return cNom;

            return r1.getProgramacion().getFecha().compareTo(r2.getProgramacion().getFecha());
        });

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Reporte Asistencia Salgueros");

            // Estilos
            CellStyle headerStyle = crearEstiloCabecera(workbook);
            CellStyle retardoStyle = crearEstiloRetardo(workbook);
            CellStyle aTiempoStyle = crearEstiloATiempo(workbook);
            CellStyle normalStyle = workbook.createCellStyle();

            // Cabeceras de columnas
            String[] headers = {
                "ID Registro", "Área", "Cédula", "Empleado", "Fecha", 
                "Hora Prog.", "Hora Entrada Real", "Salida Almuerzo", 
                "Regreso Almuerzo", "Salida Real", "Tiempo Almuerzo",
                "Total Horas Trabajadas", "Minutos Retardo", "Estado Llegada", "Estado Día"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Filas de datos
            int rowIdx = 1;
            for (RegistroAsistencia r : registros) {
                Row row = sheet.createRow(rowIdx++);

                row.createCell(0).setCellValue(r.getIdRegistro());
                row.createCell(1).setCellValue(r.getProgramacion().getEmpleado().getArea() != null ? r.getProgramacion().getEmpleado().getArea() : "PLANTA");
                row.createCell(2).setCellValue(r.getProgramacion().getEmpleado().getCedula());
                row.createCell(3).setCellValue(r.getProgramacion().getEmpleado().getNombreApellido());
                row.createCell(4).setCellValue(r.getProgramacion().getFecha().format(DATE_FMT));
                row.createCell(5).setCellValue(r.getProgramacion().getHoraEntradaProgramada().format(TIME_FMT));

                row.createCell(6).setCellValue(r.getHoraEntradaReal() != null ? r.getHoraEntradaReal().format(TIME_FMT) : "--");
                row.createCell(7).setCellValue(r.getHoraSalidaAlmuerzo() != null ? r.getHoraSalidaAlmuerzo().format(TIME_FMT) : "--");
                row.createCell(8).setCellValue(r.getHoraRegresoAlmuerzo() != null ? r.getHoraRegresoAlmuerzo().format(TIME_FMT) : "--");
                row.createCell(9).setCellValue(r.getHoraSalidaReal() != null ? r.getHoraSalidaReal().format(TIME_FMT) : "--");

                // Cálculo Tiempo de Almuerzo Real
                String tiempoAlmuerzoStr = "--";
                long minAlmuerzo = 0;
                if (r.getHoraSalidaAlmuerzo() != null && r.getHoraRegresoAlmuerzo() != null) {
                    minAlmuerzo = Duration.between(r.getHoraSalidaAlmuerzo(), r.getHoraRegresoAlmuerzo()).toMinutes();
                    tiempoAlmuerzoStr = minAlmuerzo + " min";
                }
                row.createCell(10).setCellValue(tiempoAlmuerzoStr);

                // Cálculo de Total de Horas Trabajadas (Restando Almuerzo)
                String totalHorasTrabajadasStr;
                if (r.getHoraEntradaReal() != null && r.getHoraSalidaReal() != null) {
                    long minutosBrutos = Duration.between(r.getHoraEntradaReal(), r.getHoraSalidaReal()).toMinutes();
                    long minutosEfectivos = Math.max(0, minutosBrutos - minAlmuerzo);
                    long horas = minutosEfectivos / 60;
                    long minutosRestantes = minutosEfectivos % 60;
                    double horasDecimal = Math.round((minutosEfectivos / 60.0) * 100.0) / 100.0;
                    totalHorasTrabajadasStr = horas + "h " + minutosRestantes + "m (" + horasDecimal + " hrs)";
                } else if (r.getHoraEntradaReal() != null) {
                    totalHorasTrabajadasStr = "En curso (Sin salida)";
                } else {
                    totalHorasTrabajadasStr = "--";
                }
                row.createCell(11).setCellValue(totalHorasTrabajadasStr);

                // Minutos Retardo
                Cell cellRetardo = row.createCell(12);
                cellRetardo.setCellValue(r.getMinutosRetardo());
                if (r.getMinutosRetardo() > 0) {
                    cellRetardo.setCellStyle(retardoStyle);
                }

                // Estado Llegada
                Cell cellEstado = row.createCell(13);
                cellEstado.setCellValue(r.getEstadoLlegada().name());
                cellEstado.setCellStyle(r.getEstadoLlegada() == EstadoLlegada.RETARDO ? retardoStyle : aTiempoStyle);

                // Estado Día
                row.createCell(14).setCellValue(r.getEstadoDia().name());
            }

            // ancho de columnas
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private CellStyle crearEstiloCabecera(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        XSSFFont font = (XSSFFont) workbook.createFont();
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        return style;
    }

    private CellStyle crearEstiloRetardo(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        XSSFFont font = (XSSFFont) workbook.createFont();
        font.setColor(IndexedColors.RED.getIndex());
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle crearEstiloATiempo(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        XSSFFont font = (XSSFFont) workbook.createFont();
        font.setColor(IndexedColors.GREEN.getIndex());
        font.setBold(true);
        style.setFont(font);
        return style;
    }
}
