package com.bcodesphere.pilot.contabilidad.dominio.catalogo;

import java.util.UUID;

/**
 * Cuenta del catálogo de la empresa (CLAUDE.md 9.3 y 10.2). La clase y el nivel se derivan del código.
 *
 * @param id identificador (UUID v7)
 * @param codigo código validado
 * @param nombre nombre de la cuenta
 * @param padreId cuenta padre, o nulo en una clase
 * @param naturaleza naturaleza deudora o acreedora
 * @param aceptaMovimientos {@code true} solo en cuentas de detalle, es decir, sin hijas
 * @param activa {@code false} = no acepta movimientos nuevos
 * @param version versión para concurrencia optimista (If-Match)
 */
public record Cuenta(
        UUID id,
        CodigoCuenta codigo,
        String nombre,
        UUID padreId,
        NaturalezaCuenta naturaleza,
        boolean aceptaMovimientos,
        boolean activa,
        long version) {

    /**
     * Indica si la cuenta puede usarse en asientos, en la configuración y en las reglas (CON-006).
     *
     * @return {@code true} si está activa y es de detalle
     */
    public boolean esImputable() {
        return activa && aceptaMovimientos;
    }

    /**
     * Resumen para mostrar la cuenta dentro de otro recurso.
     *
     * @return id, código y nombre
     */
    public ResumenCuenta resumen() {
        return new ResumenCuenta(id, codigo.valor(), nombre);
    }
}
