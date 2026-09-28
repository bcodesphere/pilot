package com.bcodesphere.pilot.contabilidad.aplicacion;

/**
 * Resultado de una exportación de reporte contable, listo para responder a la API (ADR-038, F4-04): el contenido ya
 * generado, el nombre de archivo sugerido (en minúsculas y sin espacios, CLAUDE.md 12.5) y el tipo de contenido MIME
 * exacto del formato.
 *
 * @param contenido bytes del archivo (PDF, XLSX o CSV)
 * @param nombreArchivo nombre sugerido para {@code Content-Disposition}, por ejemplo {@code balanza_2026-01-01_2026-12-31.csv}
 * @param tipoContenido tipo MIME exacto del formato exportado
 */
public record ArchivoExportado(byte[] contenido, String nombreArchivo, String tipoContenido) {

    /** Copia defensiva del arreglo de bytes (los records no copian arreglos por sí solos). */
    public ArchivoExportado {
        contenido = contenido.clone();
    }

    /**
     * Bytes del archivo.
     *
     * @return copia defensiva del contenido
     */
    @Override
    public byte[] contenido() {
        return contenido.clone();
    }
}
