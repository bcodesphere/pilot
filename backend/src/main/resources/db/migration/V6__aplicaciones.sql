-- V6 — Catálogo de apps y apps instaladas por empresa (CLAUDE.md §4.4, §9.2, ADR-021, ADR-030).

-- 1. APLICACION: catálogo global. Agregar una fila no requiere cambios en el shell del frontend.
CREATE TABLE aplicacion (
    codigo      VARCHAR(40) PRIMARY KEY,
    nombre      VARCHAR(100) NOT NULL,
    descripcion VARCHAR(300),
    edicion     VARCHAR(11) NOT NULL,
    orden       SMALLINT NOT NULL,
    disponible  BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT ck_aplicacion_edicion CHECK (edicion IN ('COMUNITARIA', 'ENTERPRISE'))
);

COMMENT ON TABLE aplicacion IS 'Catálogo global de apps del ERP (ADR-030). Se carga por migración; global, sin RLS.';
COMMENT ON COLUMN aplicacion.codigo IS 'Identificador estable de la app; también prefijo de sus rutas.';
COMMENT ON COLUMN aplicacion.edicion IS 'COMUNITARIA (instalable) o ENTERPRISE (visible pero bloqueada en 1.0).';
COMMENT ON COLUMN aplicacion.orden IS 'Orden de presentación en el catálogo.';
COMMENT ON COLUMN aplicacion.disponible IS 'Si se muestra en el catálogo.';

-- Solo lectura: el catálogo lo cambian las migraciones, nunca la aplicación.
GRANT SELECT ON aplicacion TO pilot_app;

-- Carga inicial (ADR-030): solo Contabilidad es instalable en 1.0; las demás se muestran bloqueadas.
INSERT INTO aplicacion (codigo, nombre, descripcion, edicion, orden) VALUES
    ('contabilidad', 'Contabilidad', 'Catálogo de cuentas, Libro Diario, mayorización, estados financieros e IVA.', 'COMUNITARIA', 10),
    ('ventas', 'Ventas', 'Cotizaciones, pedidos y documentos de venta.', 'ENTERPRISE', 20),
    ('clientes', 'Clientes', 'Directorio y seguimiento de clientes.', 'ENTERPRISE', 30),
    ('proveedores', 'Proveedores', 'Directorio de proveedores y compras.', 'ENTERPRISE', 40),
    ('inventario', 'Inventario', 'Bodegas, existencias y kardex.', 'ENTERPRISE', 50),
    ('marketing', 'Marketing', 'Campañas y comunicación con clientes.', 'ENTERPRISE', 60);

-- 2. EMPRESA_APLICACION: apps instaladas por empresa. Sin columna "activa": no hay desinstalación en 1.0.
CREATE TABLE empresa_aplicacion (
    empresa_id         UUID NOT NULL REFERENCES empresa(id),
    aplicacion_codigo  VARCHAR(40) NOT NULL REFERENCES aplicacion(codigo),
    instalada_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    instalada_por      VARCHAR(64) NOT NULL,
    PRIMARY KEY (empresa_id, aplicacion_codigo)
);

COMMENT ON TABLE empresa_aplicacion IS 'Apps instaladas por empresa (ADR-030). Una fila = instalada; no hay desinstalación en 1.0.';
COMMENT ON COLUMN empresa_aplicacion.instalada_en IS 'Momento de la instalación (UTC).';
COMMENT ON COLUMN empresa_aplicacion.instalada_por IS 'Usuario que la instaló (app.usuario_id).';

ALTER TABLE empresa_aplicacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE empresa_aplicacion FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_empresa ON empresa_aplicacion TO pilot_app
    USING (empresa_id = current_setting('app.empresa_id')::uuid)
    WITH CHECK (empresa_id = current_setting('app.empresa_id')::uuid);
COMMENT ON POLICY aislamiento_empresa ON empresa_aplicacion IS 'pilot_app solo ve e instala apps de la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';

-- Sin UPDATE ni DELETE: instalar es irreversible en 1.0 (ADR-030).
GRANT SELECT, INSERT ON empresa_aplicacion TO pilot_app;
