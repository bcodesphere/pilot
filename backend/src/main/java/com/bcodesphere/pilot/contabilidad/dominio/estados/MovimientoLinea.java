package com.bcodesphere.pilot.contabilidad.dominio.estados;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.Cuenta;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Una línea de asiento tal como la entrega el puerto de lectura del Libro Mayor (ADR-038 §5), ya ordenada por
 * fecha, año/número de asiento y número de línea. Es el insumo crudo con el que {@link LibroMayor#generar} calcula
 * el saldo acumulado línea a línea.
 *
 * @param fecha fecha contable de la línea (copia de {@code asiento.fecha}, CLAUDE.md 9.3)
 * @param asientoId asiento al que pertenece la línea
 * @param anio año del correlativo del asiento
 * @param numero número del correlativo del asiento
 * @param concepto concepto de la cabecera del asiento
 * @param descripcion descripción propia de la línea, o nula
 * @param cuenta cuenta de detalle afectada por la línea (en una cuenta padre consultada, cada línea trae su propia
 *     cuenta de detalle, ADR-038 §5)
 * @param debe monto del lado Debe (cero si la línea es del Haber)
 * @param haber monto del lado Haber (cero si la línea es del Debe)
 */
public record MovimientoLinea(
        LocalDate fecha,
        UUID asientoId,
        int anio,
        long numero,
        String concepto,
        String descripcion,
        Cuenta cuenta,
        Dinero debe,
        Dinero haber) {}
