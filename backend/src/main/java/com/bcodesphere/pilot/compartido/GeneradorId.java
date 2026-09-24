package com.bcodesphere.pilot.compartido;

import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;

/**
 * Genera UUID versión 7 (RFC 9562) para las claves primarias (ADR-010).
 * Implementación propia de ~10 líneas: evita sumar una dependencia para un formato fijo y estable.
 */
public final class GeneradorId {

    /** Fuente de aleatoriedad criptográfica, compartida (es segura entre hilos). */
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private GeneradorId() {}

    /** Genera un UUID v7 con la hora actual: los 48 bits altos son milisegundos Unix, así los índices quedan ordenados. */
    public static UUID nuevo() {
        return generar(System.currentTimeMillis(), ALEATORIO);
    }

    /**
     * Arma un UUID v7 a partir de un instante y una fuente aleatoria (visible al paquete para probarlo).
     *
     * @param milisUnix milisegundos desde 1970 (48 bits)
     * @param aleatorio origen de los bits aleatorios
     */
    static UUID generar(long milisUnix, Random aleatorio) {
        // 1. 128 bits: 74 aleatorios, luego se fijan versión y variante
        long alto = (milisUnix << 16) | (aleatorio.nextLong() & 0x0FFFL);
        long bajo = aleatorio.nextLong();
        // 2. Versión 7 en los 4 bits altos del tercer campo
        alto = (alto & 0xFFFFFFFFFFFF0FFFL) | 0x7000L;
        // 3. Variante RFC 4122 (bits 10) en los 2 bits altos del último campo
        bajo = (bajo & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(alto, bajo);
    }
}
