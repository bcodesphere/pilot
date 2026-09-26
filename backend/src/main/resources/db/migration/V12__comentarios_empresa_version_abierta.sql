-- V12 — Actualiza los comentarios de empresa.nit y empresa.nrc a la versión abierta (ADR-029, ADR-031, ADR-032).
-- No se edita V5 (migración ya aplicada): solo cambian los comentarios, no el esquema ni las restricciones.

COMMENT ON COLUMN empresa.nit IS '14 dígitos. En la versión abierta (empresa PERSONAL) no se captura ni se edita: es nulo (ADR-031, ADR-032). Es de la empresa jurídica de la edición Enterprise, donde es obligatorio (ADR-029).';
COMMENT ON COLUMN empresa.nrc IS 'Registro de IVA. En la versión abierta no se captura ni se edita (ADR-031, ADR-032); es de la empresa jurídica de la edición Enterprise (ADR-029). Sin CHECK de formato: [VERIFICAR] formato con el MH/contador.';
