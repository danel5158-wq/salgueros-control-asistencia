package com.salgueros.asistencia.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salgueros.asistencia.dto.CrearEmpleadoRequestDTO;
import com.salgueros.asistencia.dto.EnrolamientoRequestDTO;
import com.salgueros.asistencia.dto.MarcacionRequestDTO;
import com.salgueros.asistencia.dto.MarcacionResponseDTO;
import com.salgueros.asistencia.dto.ProgramacionRequestDTO;
import com.salgueros.asistencia.model.*;
import com.salgueros.asistencia.repository.EmpleadoRepository;
import com.salgueros.asistencia.repository.ProgramacionSemanalRepository;
import com.salgueros.asistencia.repository.RegistroAsistenciaRepository;
import com.salgueros.asistencia.repository.VectorFacialRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class AsistenciaService {

    private final EmpleadoRepository empleadoRepository;
    private final VectorFacialRepository vectorRepository;
    private final ProgramacionSemanalRepository programacionRepository;
    private final RegistroAsistenciaRepository registroRepository;
    private final ObjectMapper objectMapper;

    @Value("${salgueros.asistencia.tolerancia-minutos:5}")
    private int toleranciaMinutos;

    @Value("${salgueros.asistencia.umbral-distancia-vector:0.45}")
    private double umbralDistancia;

    @Value("${salgueros.admin.pin:2026}")
    private String adminPin;

    public AsistenciaService(EmpleadoRepository empleadoRepository,
                             VectorFacialRepository vectorRepository,
                             ProgramacionSemanalRepository programacionRepository,
                             RegistroAsistenciaRepository registroRepository,
                             ObjectMapper objectMapper) {
        this.empleadoRepository = empleadoRepository;
        this.vectorRepository = vectorRepository;
        this.programacionRepository = programacionRepository;
        this.registroRepository = registroRepository;
        this.objectMapper = objectMapper;
    }

    public List<Empleado> listarEmpleadosActivos() {
        return empleadoRepository.findByEstado(EstadoEmpleado.ACTIVO);
    }

    public void validarPinAdmin(String pin) {
        if (pin == null || !adminPin.equals(pin.trim())) {
            throw new SecurityException("Acceso Denegado: PIN de Administrador incorrecto.");
        }
    }

    @Transactional
    public Map<String, Object> crearNuevoEmpleado(CrearEmpleadoRequestDTO request) {
        validarPinAdmin(request.getAdminPin());

        if (request.getCedula() == null || request.getCedula().isBlank()) {
            throw new IllegalArgumentException("La cédula es requerida");
        }
        if (request.getNombreApellido() == null || request.getNombreApellido().isBlank()) {
            throw new IllegalArgumentException("El nombre y apellido son requeridos");
        }

        String cedula = request.getCedula().trim();
        if (empleadoRepository.findByCedula(cedula).isPresent()) {
            throw new IllegalArgumentException("Ya existe un empleado con la cédula " + cedula);
        }

        String area = request.getArea() != null && !request.getArea().isBlank()
                ? request.getArea().trim().toUpperCase()
                : "PLANTA";

        Empleado nuevo = Empleado.builder()
                .cedula(cedula)
                .nombreApellido(request.getNombreApellido().trim())
                .area(area)
                .estado(EstadoEmpleado.ACTIVO)
                .build();

        empleadoRepository.save(nuevo);

        // Crear turno automático para hoy para que pueda probar de inmediato
        LocalDate hoy = LocalDate.now();
        if (programacionRepository.findByEmpleadoAndFecha(nuevo, hoy).isEmpty()) {
            ProgramacionSemanal prog = ProgramacionSemanal.builder()
                    .empleado(nuevo)
                    .fecha(hoy)
                    .horaEntradaProgramada(LocalTime.of(8, 0))
                    .minutosAlmuerzoProgramados(60)
                    .horasExtrasAutorizadas(true)
                    .tipoNovedadPrecargada(TipoNovedad.NINGUNA)
                    .build();
            programacionRepository.save(prog);
        }

        return Map.of(
                "exito", true,
                "mensaje", "Empleado creado exitosamente en área " + nuevo.getArea() + " con turno activo para hoy",
                "idEmpleado", nuevo.getIdEmpleado(),
                "cedula", nuevo.getCedula(),
                "nombreApellido", nuevo.getNombreApellido(),
                "area", nuevo.getArea()
        );
    }

    @Transactional
    public Map<String, Object> programarTurnos(ProgramacionRequestDTO request) {
        validarPinAdmin(request.getAdminPin());

        LocalDate fechaInicio = request.getFechaInicio() != null ? request.getFechaInicio() : LocalDate.now();
        LocalDate fechaFin = request.getFechaFin() != null ? request.getFechaFin() : fechaInicio;

        if (fechaFin.isBefore(fechaInicio)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la fecha inicial");
        }

        LocalTime horaEntrada = request.getHoraEntrada() != null ? request.getHoraEntrada() : LocalTime.of(8, 0);
        int minutosAlmuerzo = request.getMinutosAlmuerzo() != null ? request.getMinutosAlmuerzo() : 60;
        boolean horasExtras = Boolean.TRUE.equals(request.getHorasExtras());
        TipoNovedad novedad = request.getTipoNovedad() != null ? request.getTipoNovedad() : TipoNovedad.NINGUNA;

        List<Empleado> empleadosObjetivo;
        String criterioAsignacion;

        if (request.getIdEmpleado() != null) {
            Empleado emp = empleadoRepository.findById(request.getIdEmpleado())
                    .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado con ID: " + request.getIdEmpleado()));
            empleadosObjetivo = List.of(emp);
            criterioAsignacion = "Empleado: " + emp.getNombreApellido();
        } else if (request.getArea() != null && !request.getArea().isBlank() && !"TODOS".equalsIgnoreCase(request.getArea())) {
            // Asignación Masiva por Área (PLANTA, VENTAS, TRANSPORTADORES, ADMINISTRADORES)
            String areaFiltro = request.getArea().trim().toUpperCase();
            empleadosObjetivo = empleadoRepository.findByEstadoAndArea(EstadoEmpleado.ACTIVO, areaFiltro);
            criterioAsignacion = "Área: " + areaFiltro;
        } else {
            // Aplicar a TODOS los empleados activos
            empleadosObjetivo = empleadoRepository.findByEstado(EstadoEmpleado.ACTIVO);
            criterioAsignacion = "TODOS los empleados activos";
        }

        if (empleadosObjetivo.isEmpty()) {
            throw new IllegalArgumentException("No hay empleados activos en el criterio (" + criterioAsignacion + ") para programar turnos");
        }

        int turnosCreadosOActualizados = 0;

        for (Empleado emp : empleadosObjetivo) {
            LocalDate fechaActual = fechaInicio;
            while (!fechaActual.isAfter(fechaFin)) {
                Optional<ProgramacionSemanal> optProg = programacionRepository.findByEmpleadoAndFecha(emp, fechaActual);
                ProgramacionSemanal prog;

                if (optProg.isPresent()) {
                    prog = optProg.get();
                    prog.setHoraEntradaProgramada(horaEntrada);
                    prog.setMinutosAlmuerzoProgramados(minutosAlmuerzo);
                    prog.setHorasExtrasAutorizadas(horasExtras);
                    prog.setTipoNovedadPrecargada(novedad);
                } else {
                    prog = ProgramacionSemanal.builder()
                            .empleado(emp)
                            .fecha(fechaActual)
                            .horaEntradaProgramada(horaEntrada)
                            .minutosAlmuerzoProgramados(minutosAlmuerzo)
                            .horasExtrasAutorizadas(horasExtras)
                            .tipoNovedadPrecargada(novedad)
                            .build();
                }

                programacionRepository.save(prog);
                turnosCreadosOActualizados++;
                fechaActual = fechaActual.plusDays(1);
            }
        }

        return Map.of(
                "exito", true,
                "mensaje", "Turnos programados exitosamente por el Administrador",
                "totalTurnos", turnosCreadosOActualizados,
                "totalEmpleados", empleadosObjetivo.size(),
                "desde", fechaInicio.toString(),
                "hasta", fechaFin.toString(),
                "horaEntrada", horaEntrada.toString()
        );
    }

    public List<Map<String, Object>> listarProgramacionRango(LocalDate inicio, LocalDate fin) {
        if (inicio == null) inicio = LocalDate.now();
        if (fin == null) fin = inicio;

        List<ProgramacionSemanal> lista = programacionRepository.findByFechaBetween(inicio, fin);
        return lista.stream().map(p -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("idProgramacion", p.getIdProgramacion());
            map.put("idEmpleado", p.getEmpleado().getIdEmpleado());
            map.put("nombreEmpleado", p.getEmpleado().getNombreApellido());
            map.put("cedula", p.getEmpleado().getCedula());
            map.put("area", p.getEmpleado().getArea() != null ? p.getEmpleado().getArea() : "PLANTA");
            map.put("fecha", p.getFecha().toString());
            map.put("horaEntrada", p.getHoraEntradaProgramada().toString());
            map.put("minutosAlmuerzo", p.getMinutosAlmuerzoProgramados());
            map.put("horasExtras", p.getHorasExtrasAutorizadas());
            map.put("novedad", p.getTipoNovedadPrecargada().name());
            return map;
        }).toList();
    }

    public List<Map<String, Object>> listarRegistrosAsistencia(LocalDate inicio, LocalDate fin) {
        if (inicio == null) inicio = LocalDate.now().minusDays(7);
        if (fin == null) fin = LocalDate.now();

        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        List<RegistroAsistencia> registros = registroRepository.findRegistrosEntreFechas(inicio, fin);

        return registros.stream().map(r -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("idRegistro", r.getIdRegistro());
            map.put("cedula", r.getProgramacion().getEmpleado().getCedula());
            map.put("nombreEmpleado", r.getProgramacion().getEmpleado().getNombreApellido());
            map.put("area", r.getProgramacion().getEmpleado().getArea() != null ? r.getProgramacion().getEmpleado().getArea() : "PLANTA");
            map.put("fecha", r.getProgramacion().getFecha().format(dateFmt));
            map.put("horaEntradaProgramada", r.getProgramacion().getHoraEntradaProgramada().format(timeFmt));
            map.put("horaEntradaReal", r.getHoraEntradaReal() != null ? r.getHoraEntradaReal().format(timeFmt) : "--");
            map.put("horaSalidaAlmuerzo", r.getHoraSalidaAlmuerzo() != null ? r.getHoraSalidaAlmuerzo().format(timeFmt) : "--");
            map.put("horaRegresoAlmuerzo", r.getHoraRegresoAlmuerzo() != null ? r.getHoraRegresoAlmuerzo().format(timeFmt) : "--");
            map.put("horaSalidaReal", r.getHoraSalidaReal() != null ? r.getHoraSalidaReal().format(timeFmt) : "--");
            map.put("minutosRetardo", r.getMinutosRetardo());
            map.put("estadoLlegada", r.getEstadoLlegada().name());
            map.put("estadoDia", r.getEstadoDia().name());

            // Cálculo horas trabajadas
            String horasTrabajadas = "--";
            long minAlmuerzo = 0;
            if (r.getHoraSalidaAlmuerzo() != null && r.getHoraRegresoAlmuerzo() != null) {
                minAlmuerzo = Duration.between(r.getHoraSalidaAlmuerzo(), r.getHoraRegresoAlmuerzo()).toMinutes();
            }
            if (r.getHoraEntradaReal() != null && r.getHoraSalidaReal() != null) {
                long minutosBrutos = Duration.between(r.getHoraEntradaReal(), r.getHoraSalidaReal()).toMinutes();
                long minutosEfectivos = Math.max(0, minutosBrutos - minAlmuerzo);
                long h = minutosEfectivos / 60;
                long m = minutosEfectivos % 60;
                double hDecimal = Math.round((minutosEfectivos / 60.0) * 100.0) / 100.0;
                horasTrabajadas = h + "h " + m + "m (" + hDecimal + " hrs)";
            } else if (r.getHoraEntradaReal() != null) {
                horasTrabajadas = "En curso (Sin salida)";
            }
            map.put("horasTrabajadas", horasTrabajadas);
            map.put("minutosAlmuerzo", minAlmuerzo);

            return map;
        }).toList();
    }

    @Transactional
    public Map<String, Object> enrolarRostro(EnrolamientoRequestDTO request) {
        if (request.getCedula() == null || request.getCedula().isBlank()) {
            throw new IllegalArgumentException("La cédula es requerida");
        }
        if (request.getVectorEmbedding() == null || request.getVectorEmbedding().size() < 128) {
            throw new IllegalArgumentException("Se requiere un vector de 128 dimensiones extraído por la cámara");
        }

        Empleado empleado = empleadoRepository.findByCedula(request.getCedula().trim())
                .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado con cédula: " + request.getCedula()));

        try {
            String jsonVector = objectMapper.writeValueAsString(request.getVectorEmbedding());
            List<VectorFacial> existentes = vectorRepository.findByEmpleado(empleado);
            VectorFacial vectorFacial;

            if (!existentes.isEmpty()) {
                vectorFacial = existentes.get(0);
                vectorFacial.setVectorEmbedding(jsonVector);
            } else {
                vectorFacial = VectorFacial.builder()
                        .empleado(empleado)
                        .vectorEmbedding(jsonVector)
                        .build();
            }

            vectorRepository.save(vectorFacial);

            return Map.of(
                    "exito", true,
                    "mensaje", "Rostro biométrico enrolado con éxito",
                    "empleado", empleado.getNombreApellido(),
                    "cedula", empleado.getCedula(),
                    "dimensiones", request.getVectorEmbedding().size()
            );
        } catch (Exception e) {
            throw new RuntimeException("Error al guardar vector biométrico: " + e.getMessage());
        }
    }

    @Transactional
    public MarcacionResponseDTO procesarMarcacion(MarcacionRequestDTO request) {
        List<Double> vectorEntrada = request.getVectorEmbedding();
        if (vectorEntrada == null || vectorEntrada.size() < 128) {
            return MarcacionResponseDTO.builder()
                    .exito(false)
                    .mensaje("Vector facial inválido o incompleto (se requieren 128 dimensiones)")
                    .build();
        }

        // 1. Búsqueda y comparación vectorial (Distancia Euclidiana)
        MatchBiometrico match = buscarMejorCoincidencia(vectorEntrada);
        if (match == null || match.distancia > umbralDistancia) {
            return MarcacionResponseDTO.builder()
                    .exito(false)
                    .mensaje("Rostro no reconocido en la base de empleados activos")
                    .distanciaBiometrica(match != null ? match.distancia : 999.0)
                    .build();
        }

        Empleado empleado = match.empleado;
        LocalDate hoy = LocalDate.now();
        LocalDateTime ahora = LocalDateTime.now();

        // 2. Consulta de la Programación Semanal (Fuente de Verdad)
        Optional<ProgramacionSemanal> optProg = programacionRepository.findByEmpleadoAndFecha(empleado, hoy);
        if (optProg.isEmpty()) {
            return MarcacionResponseDTO.builder()
                    .exito(false)
                    .nombreEmpleado(empleado.getNombreApellido())
                    .cedula(empleado.getCedula())
                    .mensaje("Empleado no tiene turno programado para la fecha de hoy")
                    .build();
        }

        ProgramacionSemanal prog = optProg.get();
        if (prog.getTipoNovedadPrecargada() != TipoNovedad.NINGUNA) {
            return MarcacionResponseDTO.builder()
                    .exito(false)
                    .nombreEmpleado(empleado.getNombreApellido())
                    .cedula(empleado.getCedula())
                    .mensaje("Turno no disponible por novedad: " + prog.getTipoNovedadPrecargada())
                    .build();
        }

        // 3. Obtener o crear Registro de Asistencia
        Optional<RegistroAsistencia> optReg = registroRepository.findByProgramacion(prog);
        RegistroAsistencia registro = optReg.orElse(null);

        String tipo = request.getTipoMarcacion() != null ? request.getTipoMarcacion().toUpperCase() : "ENTRADA";
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

        // REGLA FUNDAMENTAL DE SEGURIDAD:
        // Si la jornada ya fue completada y cerrada hoy (salida laboral registrada),
        // queda bloqueada permanentemente para evitar cualquier manipulación de horas.
        if (registro != null && registro.getHoraSalidaReal() != null) {
            return MarcacionResponseDTO.builder()
                    .exito(false)
                    .nombreEmpleado(empleado.getNombreApellido())
                    .cedula(empleado.getCedula())
                    .tipoMarcacion(tipo)
                    .horaMarcacion(registro.getHoraSalidaReal().format(timeFmt))
                    .distanciaBiometrica(Math.round(match.distancia * 1000.0) / 1000.0)
                    .mensaje("⛔ Tu jornada de hoy ya fue finalizada y cerrada a las " + 
                            registro.getHoraSalidaReal().format(timeFmt) + 
                            ". Tu horario está bloqueado y no admite más marcaciones.")
                    .build();
        }

        // VALIDACIONES ESPECÍFICAS DE BLOQUEO POR TIPO DE MARCACIÓN
        switch (tipo) {
            case "ENTRADA":
                // Si la hora de entrada ya existe, se BLOQUEA de forma inmutable. No se permite sobreescribir.
                if (registro != null && registro.getHoraEntradaReal() != null) {
                    return MarcacionResponseDTO.builder()
                            .exito(false)
                            .nombreEmpleado(empleado.getNombreApellido())
                            .cedula(empleado.getCedula())
                            .tipoMarcacion(tipo)
                            .horaMarcacion(registro.getHoraEntradaReal().format(timeFmt))
                            .distanciaBiometrica(Math.round(match.distancia * 1000.0) / 1000.0)
                            .mensaje("⛔ Ya registraste tu ENTRADA hoy a las " + 
                                    registro.getHoraEntradaReal().format(timeFmt) + 
                                    ". Tu hora de entrada está bloqueada y no puede ser modificada.")
                            .build();
                }
                break;

            case "SALIDA_ALMUERZO":
                if (registro == null || registro.getHoraEntradaReal() == null) {
                    return MarcacionResponseDTO.builder()
                            .exito(false)
                            .nombreEmpleado(empleado.getNombreApellido())
                            .cedula(empleado.getCedula())
                            .tipoMarcacion(tipo)
                            .mensaje("⚠️ Debes registrar primero tu ENTRADA laboral antes de salir a almorzar.")
                            .build();
                }
                if (prog.getMinutosAlmuerzoProgramados() == 0) {
                    return MarcacionResponseDTO.builder()
                            .exito(false)
                            .nombreEmpleado(empleado.getNombreApellido())
                            .cedula(empleado.getCedula())
                            .tipoMarcacion(tipo)
                            .mensaje("⚠️ Tu turno es de jornada continua (0 min de almuerzo autorizados).")
                            .build();
                }
                // Si ya marcó salida a almuerzo, se BLOQUEA.
                if (registro.getHoraSalidaAlmuerzo() != null) {
                    return MarcacionResponseDTO.builder()
                            .exito(false)
                            .nombreEmpleado(empleado.getNombreApellido())
                            .cedula(empleado.getCedula())
                            .tipoMarcacion(tipo)
                            .horaMarcacion(registro.getHoraSalidaAlmuerzo().format(timeFmt))
                            .distanciaBiometrica(Math.round(match.distancia * 1000.0) / 1000.0)
                            .mensaje("⛔ Ya registraste tu SALIDA A ALMUERZO hoy a las " + 
                                    registro.getHoraSalidaAlmuerzo().format(timeFmt) + 
                                    ". Esta hora ya está bloqueada.")
                            .build();
                }
                break;

            case "REGRESO_ALMUERZO":
                if (registro == null || registro.getHoraEntradaReal() == null) {
                    return MarcacionResponseDTO.builder()
                            .exito(false)
                            .nombreEmpleado(empleado.getNombreApellido())
                            .cedula(empleado.getCedula())
                            .tipoMarcacion(tipo)
                            .mensaje("⚠️ Debes registrar primero tu ENTRADA laboral.")
                            .build();
                }
                if (registro.getHoraSalidaAlmuerzo() == null) {
                    return MarcacionResponseDTO.builder()
                            .exito(false)
                            .nombreEmpleado(empleado.getNombreApellido())
                            .cedula(empleado.getCedula())
                            .tipoMarcacion(tipo)
                            .mensaje("⚠️ Debes registrar primero la SALIDA A ALMUERZO antes de registrar tu retorno.")
                            .build();
                }
                // Si ya marcó regreso de almuerzo, se BLOQUEA.
                if (registro.getHoraRegresoAlmuerzo() != null) {
                    return MarcacionResponseDTO.builder()
                            .exito(false)
                            .nombreEmpleado(empleado.getNombreApellido())
                            .cedula(empleado.getCedula())
                            .tipoMarcacion(tipo)
                            .horaMarcacion(registro.getHoraRegresoAlmuerzo().format(timeFmt))
                            .distanciaBiometrica(Math.round(match.distancia * 1000.0) / 1000.0)
                            .mensaje("⛔ Ya registraste tu RETORNO DE ALMUERZO hoy a las " + 
                                    registro.getHoraRegresoAlmuerzo().format(timeFmt) + 
                                    ". Esta hora ya está bloqueada.")
                            .build();
                }
                break;

            case "SALIDA":
                if (registro == null || registro.getHoraEntradaReal() == null) {
                    return MarcacionResponseDTO.builder()
                            .exito(false)
                            .nombreEmpleado(empleado.getNombreApellido())
                            .cedula(empleado.getCedula())
                            .tipoMarcacion(tipo)
                            .mensaje("⚠️ Debes registrar primero tu ENTRADA laboral antes de marcar la salida.")
                            .build();
                }
                if (registro.getHoraSalidaAlmuerzo() != null && registro.getHoraRegresoAlmuerzo() == null) {
                    return MarcacionResponseDTO.builder()
                            .exito(false)
                            .nombreEmpleado(empleado.getNombreApellido())
                            .cedula(empleado.getCedula())
                            .tipoMarcacion(tipo)
                            .mensaje("⚠️ Tienes una pausa de almuerzo pendiente. Registra tu RETORNO DE ALMUERZO antes de marcar la salida laboral.")
                            .build();
                }
                // Si ya marcó salida laboral, se BLOQUEA.
                if (registro.getHoraSalidaReal() != null) {
                    return MarcacionResponseDTO.builder()
                            .exito(false)
                            .nombreEmpleado(empleado.getNombreApellido())
                            .cedula(empleado.getCedula())
                            .tipoMarcacion(tipo)
                            .horaMarcacion(registro.getHoraSalidaReal().format(timeFmt))
                            .distanciaBiometrica(Math.round(match.distancia * 1000.0) / 1000.0)
                            .mensaje("⛔ Ya registraste tu SALIDA LABORAL hoy a las " + 
                                    registro.getHoraSalidaReal().format(timeFmt) + 
                                    ". Tu jornada ya fue cerrada y bloqueada.")
                            .build();
                }
                break;

            default:
                return MarcacionResponseDTO.builder()
                        .exito(false)
                        .mensaje("Tipo de marcación no reconocido: " + tipo)
                        .build();
        }

        // 4. Ejecución del Motor de Registro (
        return ejecutarMotorReglas(tipo, prog, registro, empleado, ahora, match.distancia);
    }

    private MarcacionResponseDTO ejecutarMotorReglas(String tipo, ProgramacionSemanal prog, 
                                                    RegistroAsistencia registro, Empleado empleado, 
                                                    LocalDateTime ahora, double distancia) {
        if (registro == null) {
            registro = RegistroAsistencia.builder()
                    .programacion(prog)
                    .horaEntradaReal(ahora)
                    .estadoLlegada(EstadoLlegada.A_TIEMPO)
                    .estadoDia(EstadoDia.EN_CURSO)
                    .build();
        }

        String mensajeResultado;
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

        switch (tipo) {
            case "ENTRADA":
                registro.setHoraEntradaReal(ahora);
                // Motor de Tolerancia de 3 Minutos
                LocalTime programada = prog.getHoraEntradaProgramada();
                LocalTime real = ahora.toLocalTime();
                long minutosDiferencia = Duration.between(programada, real).toMinutes();

                if (minutosDiferencia > toleranciaMinutos) {
                    registro.setEstadoLlegada(EstadoLlegada.RETARDO);
                    registro.setMinutosRetardo((int) minutosDiferencia);
                    mensajeResultado = "⚠️ ENTRADA REGISTRADA CON RETARDO (+" + minutosDiferencia + " min)";
                } else {
                    registro.setEstadoLlegada(EstadoLlegada.A_TIEMPO);
                    registro.setMinutosRetardo(0);
                    mensajeResultado = "✅ ENTRADA REGISTRADA A TIEMPO";
                }
                break;

            case "SALIDA_ALMUERZO":
                registro.setHoraSalidaAlmuerzo(ahora);
                mensajeResultado = "🍽️ SALIDA A ALMUERZO REGISTRADA. ¡Buen provecho!";
                break;

            case "REGRESO_ALMUERZO":
                registro.setHoraRegresoAlmuerzo(ahora);
                mensajeResultado = "☕ RETORNO DE ALMUERZO REGISTRADO. ¡Bienvenido de vuelta!";
                break;

            case "SALIDA":
                registro.setHoraSalidaReal(ahora);
                registro.setEstadoDia(EstadoDia.COMPLETADO);
                mensajeResultado = "🏁 SALIDA LABORAL REGISTRADA. ¡ADIOS, nos vemos manaña ... !";
                break;

            default:
                mensajeResultado = "Marcación procesada: " + tipo;
        }

        registroRepository.save(registro);

        return MarcacionResponseDTO.builder()
                .exito(true)
                .nombreEmpleado(empleado.getNombreApellido())
                .cedula(empleado.getCedula())
                .tipoMarcacion(tipo)
                .estadoLlegada(registro.getEstadoLlegada().name())
                .minutosRetardo(registro.getMinutosRetardo())
                .horaMarcacion(ahora.format(timeFmt))
                .distanciaBiometrica(Math.round(distancia * 1000.0) / 1000.0)
                .mensaje(mensajeResultado)
                .build();
    }

    private MatchBiometrico buscarMejorCoincidencia(List<Double> vectorTarget) {
        List<VectorFacial> activos = vectorRepository.findAllActiveEmbeddings();
        MatchBiometrico mejor = null;
        double menorDistancia = Double.MAX_VALUE;

        for (VectorFacial vf : activos) {
            try {
                List<Double> base = objectMapper.readValue(vf.getVectorEmbedding(), new TypeReference<List<Double>>() {});
                double dist = calcularDistanciaEuclidiana(vectorTarget, base);
                if (dist < menorDistancia) {
                    menorDistancia = dist;
                    mejor = new MatchBiometrico(vf.getEmpleado(), dist);
                }
            } catch (Exception e) {
                log.error("Error al deserializar vector id {}: {}", vf.getIdVector(), e.getMessage());
            }
        }
        return mejor;
    }

    /**
     * Fórmula: d(p, q) = sqrt( sum( (pi - qi)^2 ) )
     */
    private double calcularDistanciaEuclidiana(List<Double> a, List<Double> b) {
        int len = Math.min(a.size(), b.size());
        double sum = 0.0;
        for (int i = 0; i < len; i++) {
            double diff = a.get(i) - b.get(i);
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    private static class MatchBiometrico {
        Empleado empleado;
        double distancia;
        MatchBiometrico(Empleado e, double d) { this.empleado = e; this.distancia = d; }
    }
}
