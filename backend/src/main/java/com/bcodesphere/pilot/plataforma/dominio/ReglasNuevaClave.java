package com.bcodesphere.pilot.plataforma.dominio;

import com.bcodesphere.pilot.compartido.ErrorCampo;
import com.bcodesphere.pilot.compartido.ExcepcionValidacion;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Reglas de forma de una API key nueva que el contrato no puede expresar (CLAUDE.md 8.4, plan F1-06): nombre sin
 * espacios en los extremos y no vacío, alcances sin repetidos y vencimiento futuro. Todas fallan con 422
 * {@code PLT-002} y la lista {@code errores} con el campo culpable; se reúnen todas antes de rechazar.
 */
public final class ReglasNuevaClave {

    /** Longitud máxima del nombre (columna {@code VARCHAR(100)} y contrato). */
    static final int MAXIMO_NOMBRE = 100;

    private ReglasNuevaClave() {}

    /**
     * Datos ya validados de una clave nueva.
     *
     * @param nombre nombre sin espacios en los extremos
     * @param alcances alcances únicos
     * @param expiraEn vencimiento futuro, o nulo si no vence
     */
    public record Validada(String nombre, Set<AlcanceClave> alcances, Instant expiraEn) {

        /** Copia defensiva de los alcances: el registro es inmutable y no expone el conjunto recibido. */
        public Validada {
            alcances = Set.copyOf(alcances);
        }
    }

    /**
     * Valida y normaliza los datos de una clave nueva.
     *
     * @param nombre nombre recibido
     * @param codigosAlcance alcances recibidos, tal cual (pueden traer repetidos)
     * @param expiraEn vencimiento recibido; nulo si la clave no vence
     * @param ahora instante de la validación
     * @return los datos normalizados
     * @throws ExcepcionValidacion 422 {@code PLT-002} con un error por cada campo inválido
     */
    public static Validada validar(String nombre, List<String> codigosAlcance, Instant expiraEn, Instant ahora) {
        List<ErrorCampo> errores = new ArrayList<>();

        // 1. Nombre: sin espacios en los extremos; vacío o demasiado largo se rechaza
        String limpio = nombre == null ? "" : nombre.strip();
        if (limpio.isEmpty()) {
            errores.add(new ErrorCampo("nombre", "El nombre no puede estar vacío"));
        } else if (limpio.length() > MAXIMO_NOMBRE) {
            errores.add(new ErrorCampo("nombre", "El nombre no puede superar " + MAXIMO_NOMBRE + " caracteres"));
        }

        // 2. Alcances: al menos uno, todos conocidos y ninguno repetido (el contrato no usa uniqueItems a propósito
        //    para que el rechazo sea este 422 con su campo y no un 400 genérico)
        Set<AlcanceClave> alcances = EnumSet.noneOf(AlcanceClave.class);
        if (codigosAlcance == null || codigosAlcance.isEmpty()) {
            errores.add(new ErrorCampo("alcances", "Debe indicar al menos un alcance"));
        } else {
            for (String codigo : codigosAlcance) {
                AlcanceClave alcance = AlcanceClave.deCodigo(codigo).orElse(null);
                if (alcance == null) {
                    errores.add(new ErrorCampo("alcances", "Alcance desconocido"));
                } else if (!alcances.add(alcance)) {
                    errores.add(new ErrorCampo("alcances", "Los alcances no pueden repetirse"));
                    break;
                }
            }
        }

        // 3. Vencimiento: una clave que nace vencida no sirve; nulo significa que no vence
        if (expiraEn != null && !expiraEn.isAfter(ahora)) {
            errores.add(new ErrorCampo("expiraEn", "La fecha de expiración debe ser futura"));
        }

        // 4. Un solo 422 con todos los errores
        if (!errores.isEmpty()) {
            throw new ExcepcionValidacion("PLT-002", "La solicitud contiene datos inválidos", errores);
        }
        return new Validada(limpio, alcances, expiraEn);
    }
}
