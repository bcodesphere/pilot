package com.bcodesphere.pilot.plataforma.dominio;

import com.bcodesphere.pilot.compartido.ErrorCampo;
import com.bcodesphere.pilot.compartido.ExcepcionValidacion;
import java.util.List;

/**
 * Regla del nombre del espacio de trabajo de una persona natural (ADR-032): se guarda sin espacios en los extremos y
 * no puede quedar vacío. La longitud máxima (250) la valida el contrato y la columna.
 */
public final class NombreEspacio {

    private NombreEspacio() {}

    /**
     * Normaliza el nombre recibido.
     *
     * @param nombre texto enviado por el cliente
     * @return el nombre sin espacios al inicio ni al final
     * @throws ExcepcionValidacion 422 {@code PLT-002} si, sin espacios, queda vacío
     */
    public static String normalizar(String nombre) {
        // 1. strip() quita también los espacios Unicode; un nombre solo de espacios no identifica el espacio de trabajo
        String limpio = nombre == null ? "" : nombre.strip();
        if (limpio.isEmpty()) {
            throw new ExcepcionValidacion(
                    "PLT-002",
                    "La solicitud contiene datos inválidos",
                    List.of(new ErrorCampo("nombre", "El nombre no puede estar vacío")));
        }
        return limpio;
    }
}
