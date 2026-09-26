package com.bcodesphere.pilot.plataforma.infraestructura;

import com.bcodesphere.pilot.plataforma.aplicacion.HashSecreto;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Adaptador de {@link HashSecreto} con Argon2id (CLAUDE.md 5.1 y 14.1) mediante {@link Argon2PasswordEncoder} de
 * Spring Security con los parámetros {@code defaultsForSpringSecurity_v5_8()} (Argon2id, 16 bytes de sal, 32 de hash,
 * 1 hilo, 16 MiB de memoria, 2 iteraciones). Requiere BouncyCastle en el classpath. El resultado empieza con
 * {@code $argon2id$} y lleva su propia sal y parámetros, así que subir el costo más adelante no invalida hashes viejos.
 */
@Component
class HashSecretoArgon2 implements HashSecreto {

    /** El codificador es seguro entre hilos; se crea una vez. */
    private final Argon2PasswordEncoder codificador = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    @Override
    public String hashear(String secreto) {
        return codificador.encode(secreto);
    }

    @Override
    public boolean coincide(String secreto, String hash) {
        // matches() devuelve false ante un hash mal formado; nunca se propaga el detalle del error
        return codificador.matches(secreto, hash);
    }
}
