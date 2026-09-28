package com.bcodesphere.pilot.contabilidad.dominio.estados;

import com.bcodesphere.pilot.contabilidad.dominio.catalogo.CodigoCuenta;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.Cuenta;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.NaturalezaCuenta;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Catálogo sintético para las pruebas de {@code dominio.estados}: un extracto realista del catálogo base
 * (docs/contabilidad/catalogo-base.md) con clases 1, 4 (incluido el grupo 44), 5, y las cuentas intermedias de cada
 * rama, para ejercitar la jerarquía por nivel sin depender de la base de datos.
 */
final class CatalogoDePrueba {

    private CatalogoDePrueba() {}

    /** Arma el catálogo y expone cada cuenta creada por su código. */
    record Catalogo(List<Cuenta> todas, java.util.Map<String, Cuenta> porCodigo) {
        Cuenta detalle(String codigo) {
            return porCodigo.get(codigo);
        }
    }

    /**
     * Construye el catálogo de prueba.
     *
     * @return las cuentas y un índice por código
     */
    static Catalogo construir() {
        List<Cuenta> todas = new ArrayList<>();
        java.util.Map<String, Cuenta> porCodigo = new java.util.HashMap<>();

        agregarClase1(todas, porCodigo);
        agregarClases2y3(todas, porCodigo);
        agregarClase4(todas, porCodigo);
        agregarClase5(todas, porCodigo);

        return new Catalogo(List.copyOf(todas), porCodigo);
    }

    /** Clase 1 (Activo, deudora): Caja (detalle) y una cuenta complementaria acreedora (depreciación acumulada). */
    private static void agregarClase1(List<Cuenta> todas, java.util.Map<String, Cuenta> porCodigo) {
        agregar(todas, porCodigo, "1", "ACTIVO", NaturalezaCuenta.DEUDORA, false);
        agregar(todas, porCodigo, "11", "ACTIVO CORRIENTE", NaturalezaCuenta.DEUDORA, false);
        agregar(todas, porCodigo, "1101", "Efectivo y equivalentes", NaturalezaCuenta.DEUDORA, false);
        agregar(todas, porCodigo, "110101", "Efectivo", NaturalezaCuenta.DEUDORA, false);
        agregar(todas, porCodigo, "11010101", "Caja general", NaturalezaCuenta.DEUDORA, true);
        agregar(todas, porCodigo, "11010102", "Caja chica", NaturalezaCuenta.DEUDORA, true);
        agregar(todas, porCodigo, "12", "ACTIVO NO CORRIENTE", NaturalezaCuenta.DEUDORA, false);
        agregar(todas, porCodigo, "1202", "Depreciación acumulada", NaturalezaCuenta.ACREEDORA, false);
        agregar(
                todas,
                porCodigo,
                "120201",
                "Depreciación acumulada de bienes muebles",
                NaturalezaCuenta.ACREEDORA,
                false);
        agregar(
                todas,
                porCodigo,
                "12020101",
                "Depreciación acumulada — mobiliario y equipo",
                NaturalezaCuenta.ACREEDORA,
                true);
    }

    /** Clase 2 (Pasivo) y clase 3 (Patrimonio), mínimas. */
    private static void agregarClases2y3(List<Cuenta> todas, java.util.Map<String, Cuenta> porCodigo) {
        agregar(todas, porCodigo, "2", "PASIVO", NaturalezaCuenta.ACREEDORA, false);
        agregar(todas, porCodigo, "21", "PASIVO CORRIENTE", NaturalezaCuenta.ACREEDORA, false);
        agregar(todas, porCodigo, "2101", "Cuentas por pagar", NaturalezaCuenta.ACREEDORA, false);
        agregar(todas, porCodigo, "210101", "Cuentas por pagar comerciales", NaturalezaCuenta.ACREEDORA, false);
        agregar(todas, porCodigo, "21010101", "Proveedores", NaturalezaCuenta.ACREEDORA, true);
        agregar(todas, porCodigo, "3", "PATRIMONIO", NaturalezaCuenta.ACREEDORA, false);
        agregar(todas, porCodigo, "31", "CAPITAL", NaturalezaCuenta.ACREEDORA, false);
        agregar(todas, porCodigo, "3101", "Capital social", NaturalezaCuenta.ACREEDORA, false);
        agregar(todas, porCodigo, "310101", "Capital social", NaturalezaCuenta.ACREEDORA, false);
        agregar(todas, porCodigo, "31010101", "Capital social suscrito y pagado", NaturalezaCuenta.ACREEDORA, true);
    }

    /** Clase 4 (Costos y gastos, deudora): un gasto normal y el grupo 44 (impuesto sobre la renta). */
    private static void agregarClase4(List<Cuenta> todas, java.util.Map<String, Cuenta> porCodigo) {
        agregar(todas, porCodigo, "4", "COSTOS Y GASTOS", NaturalezaCuenta.DEUDORA, false);
        agregar(todas, porCodigo, "42", "GASTOS DE OPERACIÓN", NaturalezaCuenta.DEUDORA, false);
        agregar(todas, porCodigo, "4202", "Gastos de administración", NaturalezaCuenta.DEUDORA, false);
        agregar(todas, porCodigo, "420201", "Gastos de administración", NaturalezaCuenta.DEUDORA, false);
        agregar(todas, porCodigo, "42020101", "Sueldos y salarios — administración", NaturalezaCuenta.DEUDORA, true);
        agregar(todas, porCodigo, "44", "IMPUESTO SOBRE LA RENTA", NaturalezaCuenta.DEUDORA, false);
        agregar(todas, porCodigo, "4401", "Impuesto sobre la renta", NaturalezaCuenta.DEUDORA, false);
        agregar(todas, porCodigo, "440101", "Impuesto sobre la renta", NaturalezaCuenta.DEUDORA, false);
        agregar(
                todas,
                porCodigo,
                "44010101",
                "Gasto por impuesto sobre la renta corriente",
                NaturalezaCuenta.DEUDORA,
                true);
    }

    /** Clase 5 (Ingresos, acreedora): ventas gravadas y exentas. */
    private static void agregarClase5(List<Cuenta> todas, java.util.Map<String, Cuenta> porCodigo) {
        agregar(todas, porCodigo, "5", "INGRESOS", NaturalezaCuenta.ACREEDORA, false);
        agregar(todas, porCodigo, "51", "INGRESOS DE OPERACIÓN", NaturalezaCuenta.ACREEDORA, false);
        agregar(todas, porCodigo, "5101", "Ventas", NaturalezaCuenta.ACREEDORA, false);
        agregar(todas, porCodigo, "510101", "Ventas", NaturalezaCuenta.ACREEDORA, false);
        agregar(todas, porCodigo, "51010101", "Ventas gravadas", NaturalezaCuenta.ACREEDORA, true);
        agregar(todas, porCodigo, "51010102", "Ventas exentas", NaturalezaCuenta.ACREEDORA, true);
    }

    private static void agregar(
            List<Cuenta> todas,
            java.util.Map<String, Cuenta> porCodigo,
            String codigo,
            String nombre,
            NaturalezaCuenta naturaleza,
            boolean detalle) {
        Cuenta c = new Cuenta(UUID.randomUUID(), CodigoCuenta.de(codigo), nombre, null, naturaleza, detalle, true, 0);
        todas.add(c);
        porCodigo.put(codigo, c);
    }
}
