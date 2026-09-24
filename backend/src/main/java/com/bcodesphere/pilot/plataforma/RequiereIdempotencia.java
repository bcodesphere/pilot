package com.bcodesphere.pilot.plataforma;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un endpoint (o todos los de un controlador) que exige el header {@code Idempotency-Key}
 * (CLAUDE.md 1.1.4). Sin el header la petición se rechaza con 428 {@code PLT-006} antes de llegar al controlador.
 * La lógica de deduplicación la aplica {@link ServicioIdempotencia} desde el caso de uso.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiereIdempotencia {

    /** Nombre del header que lleva la clave de idempotencia (CLAUDE.md 12.2). */
    String HEADER = "Idempotency-Key";
}
