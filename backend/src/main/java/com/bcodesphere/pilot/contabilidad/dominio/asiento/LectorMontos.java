package com.bcodesphere.pilot.contabilidad.dominio.asiento;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.ExcepcionContabilidad;
import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Lee los montos de entrada de las líneas (esquema {@code MontoEntrada}, ADR-036). Es más estricto que la forma del
 * contrato: solo acepta un decimal no negativo escrito con hasta 2 decimales, igual que el esquema Zod del frontend
 * (CLAUDE.md 10.1). No usa {@link Dinero#de(String)} porque este lanza {@link IllegalArgumentException}, y aquí el
 * error de negocio es {@code CON-003}.
 */
final class LectorMontos {

    /** Hasta 17 enteros y 2 decimales escritos, sin signo, sin notación científica. */
    private static final Pattern PATRON = Pattern.compile("^\\d{1,17}(\\.\\d{1,2})?$");

    private LectorMontos() {}

    /**
     * Convierte la cadena de un monto de entrada.
     *
     * @param texto monto como cadena
     * @param numeroLinea posición de la línea capturada, desde 1 (para el mensaje)
     * @param lado {@code Debe} o {@code Haber} (para el mensaje)
     * @return el monto con 2 decimales
     * @throws ExcepcionContabilidad {@code CON-003} si es negativo, no es decimal o tiene más de 2 decimales escritos
     */
    static Dinero leer(String texto, int numeroLinea, String lado) {
        // 1. "-5", "10.505", "1e3" o vacío no cumplen el patrón: CON-003 (un "10.500" también, porque se escribieron 3)
        if (texto == null || !PATRON.matcher(texto).matches()) {
            throw ExcepcionContabilidad.montoInvalido(numeroLinea, lado);
        }
        // 2. El patrón garantiza que la conversión es exacta y sin pérdida de centavos
        return Dinero.de(new BigDecimal(texto).toPlainString());
    }
}
