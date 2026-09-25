package com.bcodesphere.pilot.plataforma.dominio;

import java.util.Optional;

/**
 * Rol de una persona dentro de una empresa (CLAUDE.md 14.2). El rol técnico {@code integracion} pertenece a las API keys
 * y no es una membresía de persona (restricción {@code ck_empresa_usuario_rol} de V5).
 * La jerarquía {@code admin_empresa > contador > auditor} la aplica Spring Security con su {@code RoleHierarchy}.
 */
public enum Rol {
    /** Todo dentro de su empresa: datos, usuarios, API keys, apps y todo lo de contador. */
    ADMIN_EMPRESA("admin_empresa"),
    /** Catálogo, configuración, reglas, asientos, reversiones y reportes. */
    CONTADOR("contador"),
    /** Solo lectura de la contabilidad, la bitácora de n8n y la auditoría. */
    AUDITOR("auditor");

    private final String codigo;

    Rol(String codigo) {
        this.codigo = codigo;
    }

    /**
     * Valor guardado en {@code empresa_usuario.rol}.
     *
     * @return código en minúsculas y guion bajo
     */
    public String codigo() {
        return codigo;
    }

    /**
     * Nombre de la autoridad de Spring Security ({@code ROLE_ADMIN_EMPRESA}); permite usar {@code hasRole('CONTADOR')}.
     *
     * @return autoridad con el prefijo {@code ROLE_}
     */
    public String autoridad() {
        return "ROLE_" + name();
    }

    /**
     * Convierte el valor guardado en la base de datos.
     *
     * @param codigo valor de {@code empresa_usuario.rol}
     * @return el rol, o vacío si el código no corresponde a ninguno
     */
    public static Optional<Rol> deCodigo(String codigo) {
        for (Rol rol : values()) {
            if (rol.codigo.equals(codigo)) {
                return Optional.of(rol);
            }
        }
        return Optional.empty();
    }
}
