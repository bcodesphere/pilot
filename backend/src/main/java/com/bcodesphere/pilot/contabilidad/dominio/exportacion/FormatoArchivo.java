package com.bcodesphere.pilot.contabilidad.dominio.exportacion;

/**
 * Formato de archivo de una exportación de reportes contables (CLAUDE.md 10.5, ADR-038): PDF, XLSX o CSV. Es el
 * equivalente de dominio del esquema {@code FormatoExportacion} del contrato; la capa {@code api} convierte entre
 * ambos por nombre, igual que con los demás enums del módulo (CLAUDE.md 8.3).
 */
public enum FormatoArchivo {
    /** Documento PDF con Thymeleaf + OpenHTMLtoPDF. */
    PDF,
    /** Hoja de cálculo XLSX escrita en streaming con Apache POI SXSSF. */
    XLSX,
    /** Texto separado por comas (RFC 4180), sin dependencias. */
    CSV
}
