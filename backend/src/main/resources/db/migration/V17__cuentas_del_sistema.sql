-- V17 — Cuentas del sistema: bloquea la edición del catálogo base copiado de la plantilla (ADR-042).

-- Cuentas del catálogo base: de solo lectura para el usuario (código, nombre, naturaleza y estado, CON-021).
-- pilot_app NO recibe UPDATE (sistema): la columna solo la fija la precarga (INSERT, B1/B3) y esta migración.
ALTER TABLE cuenta_contable ADD COLUMN sistema BOOLEAN NOT NULL DEFAULT false;

-- Marca como sistema, en las empresas ya instaladas, toda cuenta cuyo código coincide con uno del catálogo base
-- actual. RLS forzado se aplica incluso al dueño (ADR-026); se levanta solo durante esta sentencia, dentro de la
-- transacción de Flyway, y se restablece a continuación.
ALTER TABLE cuenta_contable NO FORCE ROW LEVEL SECURITY;
UPDATE cuenta_contable c SET sistema = true FROM plantilla_cuenta p WHERE p.codigo = c.codigo;
ALTER TABLE cuenta_contable FORCE ROW LEVEL SECURITY;

COMMENT ON COLUMN cuenta_contable.sistema IS 'true si la cuenta viene del catálogo base: código, nombre, naturaleza y estado no se editan (CON-021, ADR-042). false en las subcuentas que agrega el usuario.';
