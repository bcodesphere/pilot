-- =====================================================================================================
-- schema.sql — Esquema de la base de datos de Pilot 1.0 (PostgreSQL 17)
-- Incluye tablas, índices, claves, políticas de Row-Level Security, triggers (partida doble), permisos (GRANT)
-- y comentarios (COMMENT ON). No incluye el historial de Flyway ni la creación de roles.
-- Orden de carga sugerido en una base vacía: 1) roles (infra/docker/postgres/init/01-roles.sh),
-- 2) este archivo, 3) data.sql. Fuente de verdad: backend/src/main/resources/db/migration/.
-- =====================================================================================================
--
-- PostgreSQL database dump
--

\restrict HfOEsRHFy8VAJ98oEmFFVCXC9soEPlOz8JnPBpINFjcHOU9JgZ5EqcUn5YTpDkM

-- Dumped from database version 17.11
-- Dumped by pg_dump version 17.11

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: btree_gist; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;


--
-- Name: EXTENSION btree_gist; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION btree_gist IS 'support for indexing common datatypes in GiST';


--
-- Name: api_key_por_prefijo(character varying); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.api_key_por_prefijo(p_prefijo character varying) RETURNS TABLE(id uuid, empresa_id uuid, hash_secreto character varying, alcances text[], expira_en timestamp with time zone, revocada_en timestamp with time zone)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
    -- Solo lo necesario para verificar el secreto y derivar la empresa
    SELECT k.id, k.empresa_id, k.hash_secreto, k.alcances, k.expira_en, k.revocada_en
      FROM api_key k
     WHERE k.prefijo = p_prefijo
$$;


--
-- Name: FUNCTION api_key_por_prefijo(p_prefijo character varying); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.api_key_por_prefijo(p_prefijo character varying) IS 'API key por prefijo: id, empresa, hash, alcances, expiración y revocación (incluye revocadas y vencidas). SECURITY DEFINER de pilot_busqueda (ADR-026).';


--
-- Name: membresia_activa(uuid, uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.membresia_activa(p_usuario_id uuid, p_empresa_id uuid) RETURNS character varying
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
    -- Misma condición que membresias_de_usuario, para que el selector y la validación nunca discrepen
    SELECT m.rol
      FROM empresa_usuario m
      JOIN empresa e ON e.id = m.empresa_id
     WHERE m.usuario_id = p_usuario_id
       AND m.empresa_id = p_empresa_id
       AND m.estado = 'ACTIVA'
       AND e.estado = 'ACTIVA'
$$;


--
-- Name: FUNCTION membresia_activa(p_usuario_id uuid, p_empresa_id uuid); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.membresia_activa(p_usuario_id uuid, p_empresa_id uuid) IS 'Rol del usuario en la empresa si la membresía y la empresa están ACTIVAS; NULL en otro caso. SECURITY DEFINER de pilot_busqueda (ADR-026).';


--
-- Name: membresias_de_usuario(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.membresias_de_usuario(p_usuario_id uuid) RETURNS TABLE(empresa_id uuid, nombre_empresa character varying, tipo_empresa character varying, rol character varying)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
    -- Solo campos necesarios; membresía y empresa deben estar activas; orden estable por nombre
    SELECT e.id, e.nombre, e.tipo, m.rol
      FROM empresa_usuario m
      JOIN empresa e ON e.id = m.empresa_id
     WHERE m.usuario_id = p_usuario_id
       AND m.estado = 'ACTIVA'
       AND e.estado = 'ACTIVA'
     ORDER BY e.nombre, e.id
$$;


--
-- Name: FUNCTION membresias_de_usuario(p_usuario_id uuid); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.membresias_de_usuario(p_usuario_id uuid) IS 'Membresías ACTIVAS de un usuario en empresas ACTIVAS: empresa, nombre, tipo y rol. SECURITY DEFINER de pilot_busqueda (ADR-026).';


--
-- Name: validar_partida_doble(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validar_partida_doble() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_asiento    UUID;
    v_empresa    UUID;
    v_cabecera   asiento%ROWTYPE;
    v_debe       NUMERIC(19,2);
    v_haber      NUMERIC(19,2);
    v_lineas     INT;
    v_otra_fecha INT;
BEGIN
    -- 1. Identifica el asiento afectado según la tabla que disparó el trigger
    IF TG_TABLE_NAME = 'asiento' THEN
        v_asiento := NEW.id;
        v_empresa := NEW.empresa_id;
    ELSIF TG_OP = 'DELETE' THEN
        v_asiento := OLD.asiento_id;
        v_empresa := OLD.empresa_id;
    ELSE
        v_asiento := NEW.asiento_id;
        v_empresa := NEW.empresa_id;
    END IF;

    -- 2. Busca la cabecera por id y empresa de la fila que disparó el trigger (regla 1.1.3); si no existe o no es visible no hay nada que validar
    SELECT * INTO v_cabecera FROM asiento WHERE id = v_asiento AND empresa_id = v_empresa;
    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    -- 3. Suma ambos lados, cuenta las líneas y busca líneas con otra fecha
    SELECT COALESCE(SUM(debe), 0), COALESCE(SUM(haber), 0), COUNT(*), COUNT(*) FILTER (WHERE fecha <> v_cabecera.fecha)
      INTO v_debe, v_haber, v_lineas, v_otra_fecha
      FROM asiento_linea
     WHERE asiento_id = v_asiento AND empresa_id = v_cabecera.empresa_id;

    -- 4. Rechaza menos de dos líneas (CON-001; redundante con el CHECK de un solo lado, que impide que una línea sola cuadre, y se deja como defensa), descuadres (CON-005), totales de cabecera inconsistentes y fechas distintas
    IF v_lineas < 2 OR v_debe <> v_haber
       OR v_cabecera.total_debe <> v_debe OR v_cabecera.total_haber <> v_haber OR v_otra_fecha > 0 THEN
        RAISE EXCEPTION 'Asiento % inválido: diferencia %, líneas %, totales de cabecera % / %, sumas % / %, líneas con otra fecha %',
            v_asiento, v_debe - v_haber, v_lineas, v_cabecera.total_debe, v_cabecera.total_haber, v_debe, v_haber, v_otra_fecha;
    END IF;
    RETURN NULL;
END;
$$;


--
-- Name: FUNCTION validar_partida_doble(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.validar_partida_doble() IS 'Al COMMIT verifica del asiento afectado: al menos 2 líneas, Σ Debe = Σ Haber, totales de cabecera iguales a las sumas y la fecha de cada línea igual a la del asiento (última defensa de la partida doble, ADR-036). Todas sus consultas filtran por asiento y empresa (regla 1.1.3).';


--
-- Name: validar_transicion_asiento(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validar_transicion_asiento() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    -- 1. Solo se permite pasar de CONTABILIZADO a REVERTIDO fijando la reversión (de nula a no nula) y subiendo version en 1
    IF OLD.estado = 'CONTABILIZADO' AND NEW.estado = 'REVERTIDO'
       AND OLD.asiento_reversion_id IS NULL AND NEW.asiento_reversion_id IS NOT NULL
       AND NEW.version = OLD.version + 1 THEN
        -- 2. La reversión enlazada debe ser realmente un contra-asiento de este asiento
        IF NOT EXISTS (SELECT 1 FROM asiento r
                        WHERE r.id = NEW.asiento_reversion_id AND r.empresa_id = OLD.empresa_id
                          AND r.asiento_revertido_id = OLD.id) THEN
            RAISE EXCEPTION 'Transición inválida del asiento %: % no es una reversión suya', OLD.id, NEW.asiento_reversion_id;
        END IF;
        RETURN NEW;
    END IF;

    -- 3. Cualquier otro cambio de estado, reversión o versión se rechaza
    RAISE EXCEPTION 'Transición inválida del asiento %: % → % (solo CONTABILIZADO → REVERTIDO con su reversión y version + 1)',
        OLD.id, OLD.estado, NEW.estado;
END;
$$;


--
-- Name: FUNCTION validar_transicion_asiento(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.validar_transicion_asiento() IS 'Permite únicamente CONTABILIZADO → REVERTIDO con asiento_reversion_id (de nulo a una reversión suya) y version + 1 (ADR-019).';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: activo_fijo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.activo_fijo (
    id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    descripcion character varying(300) NOT NULL,
    categoria character varying(20) NOT NULL,
    fecha_adquisicion date NOT NULL,
    costo numeric(19,2) NOT NULL,
    valor_residual numeric(19,2) DEFAULT 0 NOT NULL,
    vida_util_meses smallint NOT NULL,
    operacion_id uuid NOT NULL,
    estado character varying(12) DEFAULT 'EN_USO'::character varying NOT NULL,
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    creado_por character varying(64) NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT activo_fijo_costo_check CHECK ((costo > (0)::numeric)),
    CONSTRAINT activo_fijo_valor_residual_check CHECK ((valor_residual >= (0)::numeric)),
    CONSTRAINT activo_fijo_vida_util_meses_check CHECK ((vida_util_meses > 0)),
    CONSTRAINT ck_activo_fijo_categoria CHECK (((categoria)::text = ANY ((ARRAY['MOBILIARIO_EQUIPO'::character varying, 'EQUIPO_COMPUTO'::character varying, 'VEHICULO'::character varying, 'EDIFICIO'::character varying, 'TERRENO'::character varying])::text[]))),
    CONSTRAINT ck_activo_fijo_estado CHECK (((estado)::text = ANY ((ARRAY['EN_USO'::character varying, 'REVERTIDO'::character varying])::text[]))),
    CONSTRAINT ck_activo_fijo_residual CHECK ((valor_residual < costo))
);

ALTER TABLE ONLY public.activo_fijo FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE activo_fijo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.activo_fijo IS 'Activo fijo dado de alta por la operación COMPRA_ACTIVO_FIJO (ADR-041). Inmutable salvo el paso a REVERTIDO cuando se revierte esa operación (ADR-019). RLS forzado.';


--
-- Name: COLUMN activo_fijo.categoria; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.activo_fijo.categoria IS 'Una de las cinco categorías de la spec F4.5 §5.5; TERRENO no se deprecia (sin fila en plantilla_vida_util).';


--
-- Name: COLUMN activo_fijo.costo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.activo_fijo.costo IS 'Costo de adquisición; base del cálculo de la cuota mensual (costo − residual) / vida útil en meses.';


--
-- Name: COLUMN activo_fijo.valor_residual; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.activo_fijo.valor_residual IS 'Valor que no se deprecia; 0 mientras plantilla_vida_util no esté confirmada (spec F4.5 §5.5).';


--
-- Name: COLUMN activo_fijo.vida_util_meses; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.activo_fijo.vida_util_meses IS 'Copiada de plantilla_vida_util al momento de dar de alta el activo (no cambia si la plantilla cambia después).';


--
-- Name: COLUMN activo_fijo.operacion_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.activo_fijo.operacion_id IS 'Operación COMPRA_ACTIVO_FIJO que dio de alta el activo (llave compuesta con empresa_id).';


--
-- Name: COLUMN activo_fijo.estado; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.activo_fijo.estado IS 'EN_USO o REVERTIDO; pasa a REVERTIDO cuando se revierte el asiento de su operación de alta.';


--
-- Name: CONSTRAINT ck_activo_fijo_residual ON activo_fijo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT ck_activo_fijo_residual ON public.activo_fijo IS 'El valor residual nunca alcanza el costo: la cuota mensual siempre es mayor que cero.';


--
-- Name: api_key; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.api_key (
    id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    nombre character varying(100) NOT NULL,
    prefijo character varying(16) NOT NULL,
    hash_secreto character varying(200) NOT NULL,
    alcances text[] NOT NULL,
    expira_en timestamp with time zone,
    revocada_en timestamp with time zone,
    ultimo_uso_en timestamp with time zone,
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    creado_por character varying(64) NOT NULL,
    CONSTRAINT ck_api_key_alcances CHECK (((cardinality(alcances) >= 1) AND (alcances <@ ARRAY['integracion:operaciones'::text]))),
    CONSTRAINT ck_api_key_prefijo CHECK (((prefijo)::text ~ '^pk_[a-z0-9]{4,13}$'::text))
);

ALTER TABLE ONLY public.api_key FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE api_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.api_key IS 'Credenciales de integraciones (n8n). Se autentican por prefijo + secreto; la empresa se deriva de la clave.';


--
-- Name: COLUMN api_key.nombre; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.api_key.nombre IS 'Nombre descriptivo, p. ej. "n8n producción".';


--
-- Name: COLUMN api_key.prefijo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.api_key.prefijo IS 'Parte visible para identificarla (pk_xxxx); único global porque se busca sin conocer la empresa (ADR-026).';


--
-- Name: COLUMN api_key.hash_secreto; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.api_key.hash_secreto IS 'Argon2id del secreto; el secreto se muestra una sola vez al crearla.';


--
-- Name: COLUMN api_key.alcances; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.api_key.alcances IS 'Alcances concedidos; en 1.0 solo integracion:operaciones.';


--
-- Name: COLUMN api_key.expira_en; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.api_key.expira_en IS 'Vencimiento opcional (UTC).';


--
-- Name: COLUMN api_key.revocada_en; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.api_key.revocada_en IS 'Momento de la revocación (UTC); una clave revocada devuelve 401.';


--
-- Name: COLUMN api_key.ultimo_uso_en; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.api_key.ultimo_uso_en IS 'Último uso exitoso (UTC).';


--
-- Name: COLUMN api_key.creado_por; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.api_key.creado_por IS 'Usuario (app.usuario_id) que la creó.';


--
-- Name: aplicacion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.aplicacion (
    codigo character varying(40) NOT NULL,
    nombre character varying(100) NOT NULL,
    descripcion character varying(300),
    edicion character varying(11) NOT NULL,
    orden smallint NOT NULL,
    disponible boolean DEFAULT true NOT NULL,
    CONSTRAINT ck_aplicacion_edicion CHECK (((edicion)::text = ANY ((ARRAY['COMUNITARIA'::character varying, 'ENTERPRISE'::character varying])::text[])))
);


--
-- Name: TABLE aplicacion; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.aplicacion IS 'Catálogo global de apps del ERP (ADR-030). Se carga por migración; global, sin RLS.';


--
-- Name: COLUMN aplicacion.codigo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.aplicacion.codigo IS 'Identificador estable de la app; también prefijo de sus rutas.';


--
-- Name: COLUMN aplicacion.edicion; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.aplicacion.edicion IS 'COMUNITARIA (instalable) o ENTERPRISE (visible pero bloqueada en 1.0).';


--
-- Name: COLUMN aplicacion.orden; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.aplicacion.orden IS 'Orden de presentación en el catálogo.';


--
-- Name: COLUMN aplicacion.disponible; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.aplicacion.disponible IS 'Si se muestra en el catálogo.';


--
-- Name: asiento; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.asiento (
    id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    anio smallint NOT NULL,
    numero bigint NOT NULL,
    fecha date NOT NULL,
    concepto character varying(500) NOT NULL,
    estado character varying(13) NOT NULL,
    origen_tipo character varying(12) NOT NULL,
    origen_id uuid,
    modo_precio character varying(7),
    asiento_revertido_id uuid,
    asiento_reversion_id uuid,
    total_debe numeric(19,2) NOT NULL,
    total_haber numeric(19,2) NOT NULL,
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    creado_por character varying(64) NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_asiento_anio CHECK (((anio)::numeric = EXTRACT(year FROM fecha))),
    CONSTRAINT ck_asiento_estado CHECK (((estado)::text = ANY ((ARRAY['CONTABILIZADO'::character varying, 'REVERTIDO'::character varying])::text[]))),
    CONSTRAINT ck_asiento_modo_precio CHECK (((modo_precio IS NULL) OR ((modo_precio)::text = ANY ((ARRAY['CON_IVA'::character varying, 'SIN_IVA'::character varying])::text[])))),
    CONSTRAINT ck_asiento_numero CHECK ((numero >= 1)),
    CONSTRAINT ck_asiento_origen_tipo CHECK (((origen_tipo)::text = ANY ((ARRAY['MANUAL'::character varying, 'N8N'::character varying, 'REVERSION'::character varying, 'OPERACION'::character varying])::text[]))),
    CONSTRAINT ck_asiento_reversion_coherente CHECK ((((origen_tipo)::text = 'REVERSION'::text) = (asiento_revertido_id IS NOT NULL))),
    CONSTRAINT ck_asiento_reversion_no_revertida CHECK ((NOT (((origen_tipo)::text = 'REVERSION'::text) AND ((estado)::text = 'REVERTIDO'::text)))),
    CONSTRAINT ck_asiento_revertido_coherente CHECK ((((estado)::text = 'REVERTIDO'::text) = (asiento_reversion_id IS NOT NULL))),
    CONSTRAINT ck_asiento_totales CHECK (((total_debe = total_haber) AND (total_debe > (0)::numeric)))
);

ALTER TABLE ONLY public.asiento FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE asiento; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.asiento IS 'Cabecera de la partida contable. Inmutable: solo pasa de CONTABILIZADO a REVERTIDO (ADR-019). RLS forzado.';


--
-- Name: COLUMN asiento.anio; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.asiento.anio IS 'Año de la fecha contable; con numero forma el número visible del asiento.';


--
-- Name: COLUMN asiento.numero; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.asiento.numero IS 'Correlativo por empresa y año, asignado desde correlativo_asiento.';


--
-- Name: COLUMN asiento.fecha; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.asiento.fecha IS 'Fecha contable (DATE en hora de El Salvador), no futura (CON-007).';


--
-- Name: COLUMN asiento.estado; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.asiento.estado IS 'CONTABILIZADO o REVERTIDO; el único cambio permitido tras guardar es CONTABILIZADO → REVERTIDO.';


--
-- Name: COLUMN asiento.origen_tipo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.asiento.origen_tipo IS 'MANUAL (Libro Diario), N8N (operación externa) o REVERSION (contra-asiento).';


--
-- Name: COLUMN asiento.origen_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.asiento.origen_id IS 'operacion_externa.id cuando viene de n8n. Sin llave foránea: la tabla llega en F5.';


--
-- Name: COLUMN asiento.modo_precio; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.asiento.modo_precio IS 'Modo con que se separó el IVA (CON_IVA o SIN_IVA); nulo si ninguna línea llevó IVA.';


--
-- Name: COLUMN asiento.asiento_revertido_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.asiento.asiento_revertido_id IS 'En una reversión: el asiento que revierte (misma empresa, llave compuesta).';


--
-- Name: COLUMN asiento.asiento_reversion_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.asiento.asiento_reversion_id IS 'En un asiento revertido: la reversión que lo anula (misma empresa, llave compuesta).';


--
-- Name: COLUMN asiento.total_debe; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.asiento.total_debe IS 'Σ Debe de las líneas guardadas; el trigger diferido verifica que coincida.';


--
-- Name: COLUMN asiento.total_haber; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.asiento.total_haber IS 'Σ Haber de las líneas guardadas; el trigger diferido verifica que coincida.';


--
-- Name: COLUMN asiento.creado_por; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.asiento.creado_por IS 'Valor de app.usuario_id; en asientos de n8n no es un usuario (como V11, ADR-036).';


--
-- Name: COLUMN asiento.version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.asiento.version IS 'Se incrementa en la única actualización permitida (paso a REVERTIDO).';


--
-- Name: CONSTRAINT ck_asiento_anio ON asiento; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT ck_asiento_anio ON public.asiento IS 'El año de numeración es el de la fecha contable (ADR-036, decisión 7).';


--
-- Name: CONSTRAINT ck_asiento_origen_tipo ON asiento; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT ck_asiento_origen_tipo ON public.asiento IS 'MANUAL (Libro Diario), N8N (operación externa), REVERSION (contra-asiento) u OPERACION (operación guiada, ADR-041).';


--
-- Name: CONSTRAINT ck_asiento_reversion_coherente ON asiento; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT ck_asiento_reversion_coherente ON public.asiento IS 'origen_tipo = REVERSION si y solo si asiento_revertido_id no es nulo (ADR-036, decisión 7).';


--
-- Name: CONSTRAINT ck_asiento_reversion_no_revertida ON asiento; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT ck_asiento_reversion_no_revertida ON public.asiento IS 'Una reversión no se revierte (CON-009).';


--
-- Name: CONSTRAINT ck_asiento_revertido_coherente ON asiento; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT ck_asiento_revertido_coherente ON public.asiento IS 'estado = REVERTIDO si y solo si asiento_reversion_id no es nulo.';


--
-- Name: CONSTRAINT ck_asiento_totales ON asiento; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT ck_asiento_totales ON public.asiento IS 'Partida doble en la cabecera: Debe = Haber y mayores que cero (CON-004, CON-005).';


--
-- Name: asiento_linea; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.asiento_linea (
    id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    asiento_id uuid NOT NULL,
    numero_linea smallint NOT NULL,
    fecha date NOT NULL,
    cuenta_id uuid NOT NULL,
    descripcion character varying(300),
    debe numeric(19,2) DEFAULT 0 NOT NULL,
    haber numeric(19,2) DEFAULT 0 NOT NULL,
    origen_linea character varying(14) NOT NULL,
    linea_base_id uuid,
    CONSTRAINT ck_asiento_linea_debe CHECK ((debe >= (0)::numeric)),
    CONSTRAINT ck_asiento_linea_haber CHECK ((haber >= (0)::numeric)),
    CONSTRAINT ck_asiento_linea_origen CHECK (((origen_linea)::text = ANY ((ARRAY['USUARIO'::character varying, 'IVA_CALCULADO'::character varying, 'OPERACION'::character varying])::text[]))),
    CONSTRAINT ck_asiento_linea_un_lado CHECK (((debe = (0)::numeric) <> (haber = (0)::numeric)))
);

ALTER TABLE ONLY public.asiento_linea FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE asiento_linea; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.asiento_linea IS 'Líneas de un asiento (Debe o Haber). Insert-only para pilot_app (ADR-019). RLS forzado.';


--
-- Name: COLUMN asiento_linea.numero_linea; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.asiento_linea.numero_linea IS 'Posición de la línea dentro del asiento, desde 1.';


--
-- Name: COLUMN asiento_linea.fecha; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.asiento_linea.fecha IS 'Copia de asiento.fecha para las consultas del Mayor; el trigger diferido verifica que coincida.';


--
-- Name: COLUMN asiento_linea.cuenta_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.asiento_linea.cuenta_id IS 'Cuenta de detalle de la misma empresa (llave compuesta).';


--
-- Name: COLUMN asiento_linea.origen_linea; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.asiento_linea.origen_linea IS 'USUARIO (capturada), IVA_CALCULADO (expandida por "lleva IVA") u OPERACION (generada desde n8n).';


--
-- Name: COLUMN asiento_linea.linea_base_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.asiento_linea.linea_base_id IS 'En una línea de IVA: la línea que la originó, del mismo asiento (llave compuesta con asiento_id).';


--
-- Name: CONSTRAINT ck_asiento_linea_debe ON asiento_linea; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT ck_asiento_linea_debe ON public.asiento_linea IS 'Montos no negativos (CON-003); los 2 decimales los fija NUMERIC(19,2).';


--
-- Name: CONSTRAINT ck_asiento_linea_haber ON asiento_linea; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT ck_asiento_linea_haber ON public.asiento_linea IS 'Montos no negativos (CON-003); los 2 decimales los fija NUMERIC(19,2).';


--
-- Name: CONSTRAINT ck_asiento_linea_un_lado ON asiento_linea; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT ck_asiento_linea_un_lado ON public.asiento_linea IS 'Solo Debe o solo Haber, nunca ambos ni ninguno (CON-002).';


--
-- Name: auditoria; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auditoria (
    id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    entidad character varying(60) NOT NULL,
    entidad_id character varying(100) NOT NULL,
    accion character varying(30) NOT NULL,
    usuario_id character varying(64) NOT NULL,
    valor_anterior jsonb,
    valor_nuevo jsonb,
    trace_id character varying(64),
    creado_en timestamp with time zone DEFAULT now() NOT NULL
)
PARTITION BY RANGE (creado_en);

ALTER TABLE ONLY public.auditoria FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE auditoria; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.auditoria IS 'Bitácora de mutaciones de datos de negocio: quién, qué, cuándo, valor anterior y nuevo. Solo inserción; partición mensual; retención 10 años.';


--
-- Name: COLUMN auditoria.empresa_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.auditoria.empresa_id IS 'Empresa a la que pertenece el registro; base de la política RLS. Las entidades sin empresa (usuario) se registran en auditoria_global (ADR-025).';


--
-- Name: COLUMN auditoria.entidad; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.auditoria.entidad IS 'Nombre lógico de la entidad modificada (p. ej. asiento, cuenta_contable).';


--
-- Name: COLUMN auditoria.entidad_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.auditoria.entidad_id IS 'Identificador de la entidad; texto para admitir claves no UUID.';


--
-- Name: COLUMN auditoria.accion; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.auditoria.accion IS 'Acción realizada (p. ej. CREAR, ACTUALIZAR, REVERTIR).';


--
-- Name: COLUMN auditoria.usuario_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.auditoria.usuario_id IS 'Usuario (UUID) que ejecutó la acción, o el marcador del sistema (app.usuario_id).';


--
-- Name: COLUMN auditoria.valor_anterior; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.auditoria.valor_anterior IS 'Estado previo de la entidad; nulo en creaciones.';


--
-- Name: COLUMN auditoria.valor_nuevo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.auditoria.valor_nuevo IS 'Estado posterior de la entidad; nulo en eliminaciones.';


--
-- Name: COLUMN auditoria.trace_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.auditoria.trace_id IS 'traceId de la petición, para correlacionar con los logs.';


--
-- Name: COLUMN auditoria.creado_en; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.auditoria.creado_en IS 'Momento del evento (UTC); columna de partición mensual.';


--
-- Name: auditoria_2026_09; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auditoria_2026_09 (
    id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    entidad character varying(60) NOT NULL,
    entidad_id character varying(100) NOT NULL,
    accion character varying(30) NOT NULL,
    usuario_id character varying(64) NOT NULL,
    valor_anterior jsonb,
    valor_nuevo jsonb,
    trace_id character varying(64),
    creado_en timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE auditoria_2026_09; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.auditoria_2026_09 IS 'Partición mensual de auditoria (UTC). Sin permisos para pilot_app: se accede por la tabla padre.';


--
-- Name: auditoria_2026_10; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auditoria_2026_10 (
    id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    entidad character varying(60) NOT NULL,
    entidad_id character varying(100) NOT NULL,
    accion character varying(30) NOT NULL,
    usuario_id character varying(64) NOT NULL,
    valor_anterior jsonb,
    valor_nuevo jsonb,
    trace_id character varying(64),
    creado_en timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE auditoria_2026_10; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.auditoria_2026_10 IS 'Partición mensual de auditoria (UTC). Sin permisos para pilot_app: se accede por la tabla padre.';


--
-- Name: auditoria_2026_11; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auditoria_2026_11 (
    id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    entidad character varying(60) NOT NULL,
    entidad_id character varying(100) NOT NULL,
    accion character varying(30) NOT NULL,
    usuario_id character varying(64) NOT NULL,
    valor_anterior jsonb,
    valor_nuevo jsonb,
    trace_id character varying(64),
    creado_en timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE auditoria_2026_11; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.auditoria_2026_11 IS 'Partición mensual de auditoria (UTC). Sin permisos para pilot_app: se accede por la tabla padre.';


--
-- Name: auditoria_2026_12; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auditoria_2026_12 (
    id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    entidad character varying(60) NOT NULL,
    entidad_id character varying(100) NOT NULL,
    accion character varying(30) NOT NULL,
    usuario_id character varying(64) NOT NULL,
    valor_anterior jsonb,
    valor_nuevo jsonb,
    trace_id character varying(64),
    creado_en timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE auditoria_2026_12; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.auditoria_2026_12 IS 'Partición mensual de auditoria (UTC). Sin permisos para pilot_app: se accede por la tabla padre.';


--
-- Name: auditoria_2027_01; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auditoria_2027_01 (
    id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    entidad character varying(60) NOT NULL,
    entidad_id character varying(100) NOT NULL,
    accion character varying(30) NOT NULL,
    usuario_id character varying(64) NOT NULL,
    valor_anterior jsonb,
    valor_nuevo jsonb,
    trace_id character varying(64),
    creado_en timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE auditoria_2027_01; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.auditoria_2027_01 IS 'Partición mensual de auditoria (UTC). Sin permisos para pilot_app: se accede por la tabla padre.';


--
-- Name: auditoria_2027_02; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auditoria_2027_02 (
    id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    entidad character varying(60) NOT NULL,
    entidad_id character varying(100) NOT NULL,
    accion character varying(30) NOT NULL,
    usuario_id character varying(64) NOT NULL,
    valor_anterior jsonb,
    valor_nuevo jsonb,
    trace_id character varying(64),
    creado_en timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE auditoria_2027_02; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.auditoria_2027_02 IS 'Partición mensual de auditoria (UTC). Sin permisos para pilot_app: se accede por la tabla padre.';


--
-- Name: auditoria_2027_03; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auditoria_2027_03 (
    id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    entidad character varying(60) NOT NULL,
    entidad_id character varying(100) NOT NULL,
    accion character varying(30) NOT NULL,
    usuario_id character varying(64) NOT NULL,
    valor_anterior jsonb,
    valor_nuevo jsonb,
    trace_id character varying(64),
    creado_en timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE auditoria_2027_03; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.auditoria_2027_03 IS 'Partición mensual de auditoria (UTC). Sin permisos para pilot_app: se accede por la tabla padre.';


--
-- Name: auditoria_2027_04; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auditoria_2027_04 (
    id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    entidad character varying(60) NOT NULL,
    entidad_id character varying(100) NOT NULL,
    accion character varying(30) NOT NULL,
    usuario_id character varying(64) NOT NULL,
    valor_anterior jsonb,
    valor_nuevo jsonb,
    trace_id character varying(64),
    creado_en timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE auditoria_2027_04; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.auditoria_2027_04 IS 'Partición mensual de auditoria (UTC). Sin permisos para pilot_app: se accede por la tabla padre.';


--
-- Name: auditoria_2027_05; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auditoria_2027_05 (
    id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    entidad character varying(60) NOT NULL,
    entidad_id character varying(100) NOT NULL,
    accion character varying(30) NOT NULL,
    usuario_id character varying(64) NOT NULL,
    valor_anterior jsonb,
    valor_nuevo jsonb,
    trace_id character varying(64),
    creado_en timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE auditoria_2027_05; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.auditoria_2027_05 IS 'Partición mensual de auditoria (UTC). Sin permisos para pilot_app: se accede por la tabla padre.';


--
-- Name: auditoria_2027_06; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auditoria_2027_06 (
    id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    entidad character varying(60) NOT NULL,
    entidad_id character varying(100) NOT NULL,
    accion character varying(30) NOT NULL,
    usuario_id character varying(64) NOT NULL,
    valor_anterior jsonb,
    valor_nuevo jsonb,
    trace_id character varying(64),
    creado_en timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE auditoria_2027_06; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.auditoria_2027_06 IS 'Partición mensual de auditoria (UTC). Sin permisos para pilot_app: se accede por la tabla padre.';


--
-- Name: auditoria_2027_07; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auditoria_2027_07 (
    id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    entidad character varying(60) NOT NULL,
    entidad_id character varying(100) NOT NULL,
    accion character varying(30) NOT NULL,
    usuario_id character varying(64) NOT NULL,
    valor_anterior jsonb,
    valor_nuevo jsonb,
    trace_id character varying(64),
    creado_en timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE auditoria_2027_07; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.auditoria_2027_07 IS 'Partición mensual de auditoria (UTC). Sin permisos para pilot_app: se accede por la tabla padre.';


--
-- Name: auditoria_2027_08; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auditoria_2027_08 (
    id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    entidad character varying(60) NOT NULL,
    entidad_id character varying(100) NOT NULL,
    accion character varying(30) NOT NULL,
    usuario_id character varying(64) NOT NULL,
    valor_anterior jsonb,
    valor_nuevo jsonb,
    trace_id character varying(64),
    creado_en timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE auditoria_2027_08; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.auditoria_2027_08 IS 'Partición mensual de auditoria (UTC). Sin permisos para pilot_app: se accede por la tabla padre.';


--
-- Name: auditoria_2027_09; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auditoria_2027_09 (
    id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    entidad character varying(60) NOT NULL,
    entidad_id character varying(100) NOT NULL,
    accion character varying(30) NOT NULL,
    usuario_id character varying(64) NOT NULL,
    valor_anterior jsonb,
    valor_nuevo jsonb,
    trace_id character varying(64),
    creado_en timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE auditoria_2027_09; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.auditoria_2027_09 IS 'Partición mensual de auditoria (UTC). Sin permisos para pilot_app: se accede por la tabla padre.';


--
-- Name: auditoria_2027_10; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auditoria_2027_10 (
    id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    entidad character varying(60) NOT NULL,
    entidad_id character varying(100) NOT NULL,
    accion character varying(30) NOT NULL,
    usuario_id character varying(64) NOT NULL,
    valor_anterior jsonb,
    valor_nuevo jsonb,
    trace_id character varying(64),
    creado_en timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE auditoria_2027_10; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.auditoria_2027_10 IS 'Partición mensual de auditoria (UTC). Sin permisos para pilot_app: se accede por la tabla padre.';


--
-- Name: auditoria_2027_11; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auditoria_2027_11 (
    id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    entidad character varying(60) NOT NULL,
    entidad_id character varying(100) NOT NULL,
    accion character varying(30) NOT NULL,
    usuario_id character varying(64) NOT NULL,
    valor_anterior jsonb,
    valor_nuevo jsonb,
    trace_id character varying(64),
    creado_en timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE auditoria_2027_11; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.auditoria_2027_11 IS 'Partición mensual de auditoria (UTC). Sin permisos para pilot_app: se accede por la tabla padre.';


--
-- Name: auditoria_2027_12; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auditoria_2027_12 (
    id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    entidad character varying(60) NOT NULL,
    entidad_id character varying(100) NOT NULL,
    accion character varying(30) NOT NULL,
    usuario_id character varying(64) NOT NULL,
    valor_anterior jsonb,
    valor_nuevo jsonb,
    trace_id character varying(64),
    creado_en timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE auditoria_2027_12; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.auditoria_2027_12 IS 'Partición mensual de auditoria (UTC). Sin permisos para pilot_app: se accede por la tabla padre.';


--
-- Name: auditoria_default; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auditoria_default (
    id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    entidad character varying(60) NOT NULL,
    entidad_id character varying(100) NOT NULL,
    accion character varying(30) NOT NULL,
    usuario_id character varying(64) NOT NULL,
    valor_anterior jsonb,
    valor_nuevo jsonb,
    trace_id character varying(64),
    creado_en timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE auditoria_default; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.auditoria_default IS 'Partición de respaldo para fechas sin partición mensual; debe vigilarse y vaciarse creando la partición que falte.';


--
-- Name: auditoria_global; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auditoria_global (
    id uuid NOT NULL,
    entidad character varying(60) NOT NULL,
    entidad_id character varying(100) NOT NULL,
    accion character varying(30) NOT NULL,
    usuario_id character varying(64) NOT NULL,
    valor_anterior jsonb,
    valor_nuevo jsonb,
    trace_id character varying(64),
    creado_en timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE auditoria_global; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.auditoria_global IS 'Bitácora de mutaciones de entidades sin empresa (p. ej. usuario). Solo inserción; sin RLS (ADR-025).';


--
-- Name: COLUMN auditoria_global.entidad; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.auditoria_global.entidad IS 'Nombre lógico de la entidad modificada (p. ej. usuario).';


--
-- Name: COLUMN auditoria_global.entidad_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.auditoria_global.entidad_id IS 'Identificador de la entidad; texto para admitir claves no UUID.';


--
-- Name: COLUMN auditoria_global.accion; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.auditoria_global.accion IS 'Acción realizada (p. ej. CREAR, ACTUALIZAR).';


--
-- Name: COLUMN auditoria_global.usuario_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.auditoria_global.usuario_id IS 'Usuario (UUID) que ejecutó la acción, o el marcador del sistema.';


--
-- Name: COLUMN auditoria_global.valor_anterior; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.auditoria_global.valor_anterior IS 'Estado previo; nulo en creaciones. Sin datos personales sin enmascarar.';


--
-- Name: COLUMN auditoria_global.valor_nuevo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.auditoria_global.valor_nuevo IS 'Estado posterior; nulo en eliminaciones.';


--
-- Name: COLUMN auditoria_global.trace_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.auditoria_global.trace_id IS 'traceId de la petición, para correlacionar con los logs.';


--
-- Name: COLUMN auditoria_global.creado_en; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.auditoria_global.creado_en IS 'Momento del evento (UTC).';


--
-- Name: configuracion_contable; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.configuracion_contable (
    empresa_id uuid NOT NULL,
    modo_precio_defecto character varying(7) DEFAULT 'CON_IVA'::character varying NOT NULL,
    cuenta_iva_debito_id uuid NOT NULL,
    cuenta_iva_credito_id uuid NOT NULL,
    actualizado_en timestamp with time zone DEFAULT now() NOT NULL,
    actualizado_por character varying(64),
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_configuracion_contable_modo CHECK (((modo_precio_defecto)::text = ANY ((ARRAY['CON_IVA'::character varying, 'SIN_IVA'::character varying])::text[])))
);

ALTER TABLE ONLY public.configuracion_contable FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE configuracion_contable; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.configuracion_contable IS 'Configuración contable de la empresa (una fila): modo de precio y cuentas de IVA. RLS forzado.';


--
-- Name: COLUMN configuracion_contable.modo_precio_defecto; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.configuracion_contable.modo_precio_defecto IS 'CON_IVA o SIN_IVA; valor inicial del formulario manual y modo de todas las operaciones de n8n (ADR-015).';


--
-- Name: COLUMN configuracion_contable.cuenta_iva_debito_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.configuracion_contable.cuenta_iva_debito_id IS 'IVA débito fiscal (pasivo); cuenta de la misma empresa (llave compuesta).';


--
-- Name: COLUMN configuracion_contable.cuenta_iva_credito_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.configuracion_contable.cuenta_iva_credito_id IS 'IVA crédito fiscal (activo); cuenta de la misma empresa (llave compuesta).';


--
-- Name: COLUMN configuracion_contable.actualizado_en; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.configuracion_contable.actualizado_en IS 'Última modificación (UTC).';


--
-- Name: COLUMN configuracion_contable.actualizado_por; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.configuracion_contable.actualizado_por IS 'Usuario (app.usuario_id) que hizo la última modificación.';


--
-- Name: COLUMN configuracion_contable.version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.configuracion_contable.version IS 'Versión para concurrencia optimista (If-Match/ETag).';


--
-- Name: correlativo_asiento; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.correlativo_asiento (
    empresa_id uuid NOT NULL,
    anio smallint NOT NULL,
    ultimo bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_correlativo_asiento_ultimo CHECK ((ultimo >= 0))
);

ALTER TABLE ONLY public.correlativo_asiento FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE correlativo_asiento; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.correlativo_asiento IS 'Último número de asiento por empresa y año. Se incrementa con INSERT … ON CONFLICT DO UPDATE … RETURNING en la transacción del asiento (ADR-036). RLS forzado.';


--
-- Name: COLUMN correlativo_asiento.anio; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.correlativo_asiento.anio IS 'Año de la fecha contable del asiento; la numeración reinicia cada año.';


--
-- Name: COLUMN correlativo_asiento.ultimo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.correlativo_asiento.ultimo IS 'Último número asignado; el bloqueo de la fila serializa la numeración sin huecos entre transacciones que terminan bien.';


--
-- Name: CONSTRAINT ck_correlativo_asiento_ultimo ON correlativo_asiento; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT ck_correlativo_asiento_ultimo ON public.correlativo_asiento IS 'El correlativo nunca es negativo.';


--
-- Name: cuenta_contable; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cuenta_contable (
    id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    codigo character varying(8) NOT NULL,
    nombre character varying(200) NOT NULL,
    clase smallint GENERATED ALWAYS AS ((substr((codigo)::text, 1, 1))::smallint) STORED,
    nivel smallint NOT NULL,
    cuenta_padre_id uuid,
    naturaleza character varying(9) NOT NULL,
    acepta_movimientos boolean NOT NULL,
    activa boolean DEFAULT true NOT NULL,
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    creado_por character varying(64) NOT NULL,
    actualizado_en timestamp with time zone DEFAULT now() NOT NULL,
    actualizado_por character varying(64),
    version bigint DEFAULT 0 NOT NULL,
    sistema boolean DEFAULT false NOT NULL,
    CONSTRAINT ck_cuenta_contable_codigo CHECK (((codigo)::text ~ '^[1-5][0-9]*$'::text)),
    CONSTRAINT ck_cuenta_contable_longitud CHECK ((length((codigo)::text) = ANY (ARRAY[1, 2, 4, 6, 8]))),
    CONSTRAINT ck_cuenta_contable_naturaleza CHECK (((naturaleza)::text = ANY ((ARRAY['DEUDORA'::character varying, 'ACREEDORA'::character varying])::text[]))),
    CONSTRAINT ck_cuenta_contable_nivel CHECK ((nivel =
CASE length((codigo)::text)
    WHEN 1 THEN 1
    WHEN 2 THEN 2
    WHEN 4 THEN 3
    WHEN 6 THEN 4
    WHEN 8 THEN 5
    ELSE NULL::integer
END))
);

ALTER TABLE ONLY public.cuenta_contable FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE cuenta_contable; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.cuenta_contable IS 'Catálogo de cuentas por empresa, copiado de plantilla_cuenta al instalar Contabilidad y editable. RLS forzado.';


--
-- Name: COLUMN cuenta_contable.codigo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.cuenta_contable.codigo IS 'Código de 1, 2, 4, 6 u 8 dígitos; el de la cuenta padre es prefijo del de la hija. Único por empresa.';


--
-- Name: COLUMN cuenta_contable.clase; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.cuenta_contable.clase IS 'Derivada del primer dígito: 1 Activo, 2 Pasivo, 3 Capital, 4 Costos y Gastos, 5 Ingresos.';


--
-- Name: COLUMN cuenta_contable.nivel; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.cuenta_contable.nivel IS '1 clase, 2 grupo, 3 cuenta, 4 subcuenta, 5 detalle; se valida contra la longitud del código.';


--
-- Name: COLUMN cuenta_contable.cuenta_padre_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.cuenta_contable.cuenta_padre_id IS 'Cuenta de nivel superior, de la misma empresa (llave compuesta); nula solo en las clases.';


--
-- Name: COLUMN cuenta_contable.naturaleza; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.cuenta_contable.naturaleza IS 'DEUDORA (aumenta con el Debe) o ACREEDORA (aumenta con el Haber); editable para cuentas complementarias.';


--
-- Name: COLUMN cuenta_contable.acepta_movimientos; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.cuenta_contable.acepta_movimientos IS 'true solo en cuentas de detalle sin hijas; las demás no aceptan asientos (CON-006).';


--
-- Name: COLUMN cuenta_contable.activa; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.cuenta_contable.activa IS 'false = no acepta movimientos nuevos (CON-006); no se puede desactivar con saldo (CON-012).';


--
-- Name: COLUMN cuenta_contable.creado_por; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.cuenta_contable.creado_por IS 'Usuario (app.usuario_id) que la creó.';


--
-- Name: COLUMN cuenta_contable.actualizado_en; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.cuenta_contable.actualizado_en IS 'Última modificación (UTC).';


--
-- Name: COLUMN cuenta_contable.actualizado_por; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.cuenta_contable.actualizado_por IS 'Usuario (app.usuario_id) que hizo la última modificación.';


--
-- Name: COLUMN cuenta_contable.version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.cuenta_contable.version IS 'Versión para concurrencia optimista (If-Match/ETag).';


--
-- Name: COLUMN cuenta_contable.sistema; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.cuenta_contable.sistema IS 'true si la cuenta viene del catálogo base: código, nombre, naturaleza y estado no se editan (CON-021, ADR-042). false en las subcuentas que agrega el usuario.';


--
-- Name: depreciacion_registrada; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.depreciacion_registrada (
    id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    activo_id uuid NOT NULL,
    anio smallint NOT NULL,
    mes smallint NOT NULL,
    monto numeric(19,2) NOT NULL,
    asiento_id uuid NOT NULL,
    estado character varying(12) DEFAULT 'VIGENTE'::character varying NOT NULL,
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    creado_por character varying(64) NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_depreciacion_estado CHECK (((estado)::text = ANY ((ARRAY['VIGENTE'::character varying, 'REVERTIDA'::character varying])::text[]))),
    CONSTRAINT depreciacion_registrada_mes_check CHECK (((mes >= 1) AND (mes <= 12))),
    CONSTRAINT depreciacion_registrada_monto_check CHECK ((monto > (0)::numeric))
);

ALTER TABLE ONLY public.depreciacion_registrada FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE depreciacion_registrada; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.depreciacion_registrada IS 'Cuota de depreciación de un activo en un mes, generada por la operación DEPRECIACION_MENSUAL (ADR-041). Inmutable salvo el paso a REVERTIDA. RLS forzado.';


--
-- Name: COLUMN depreciacion_registrada.anio; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.depreciacion_registrada.anio IS 'Año del mes depreciado (con mes, forma el período: uq_depreciacion_vigente evita repetirlo por activo).';


--
-- Name: COLUMN depreciacion_registrada.monto; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.depreciacion_registrada.monto IS 'Cuota del mes; la línea recta calcula (costo − residual) / vida útil, con la última cuota ajustando el redondeo (spec F4.5 §5.5).';


--
-- Name: COLUMN depreciacion_registrada.asiento_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.depreciacion_registrada.asiento_id IS 'Asiento de la operación DEPRECIACION_MENSUAL que generó esta cuota (llave compuesta con empresa_id).';


--
-- Name: COLUMN depreciacion_registrada.estado; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.depreciacion_registrada.estado IS 'VIGENTE o REVERTIDA; pasa a REVERTIDA cuando se revierte el asiento, y libera el período para registrarlo de nuevo (spec F4.5 §5.5).';


--
-- Name: empresa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.empresa (
    id uuid NOT NULL,
    tipo character varying(8) NOT NULL,
    propietario_id uuid,
    nit character varying(14),
    nrc character varying(10),
    nombre character varying(250) NOT NULL,
    nombre_comercial character varying(250),
    estado character varying(15) DEFAULT 'ACTIVA'::character varying NOT NULL,
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    actualizado_en timestamp with time zone DEFAULT now() NOT NULL,
    actualizado_por character varying(64),
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_empresa_estado CHECK (((estado)::text = ANY ((ARRAY['ACTIVA'::character varying, 'INACTIVA'::character varying])::text[]))),
    CONSTRAINT ck_empresa_juridica_con_nit CHECK ((((tipo)::text = 'PERSONAL'::text) OR (nit IS NOT NULL))),
    CONSTRAINT ck_empresa_nit_formato CHECK (((nit IS NULL) OR ((nit)::text ~ '^[0-9]{14}$'::text))),
    CONSTRAINT ck_empresa_tipo CHECK (((tipo)::text = ANY ((ARRAY['PERSONAL'::character varying, 'JURIDICA'::character varying])::text[])))
);

ALTER TABLE ONLY public.empresa FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE empresa; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.empresa IS 'Contribuyente que usa Pilot y unidad de aislamiento (tenant). RLS forzado: la empresa ES el tenant, por eso filtra por id.';


--
-- Name: COLUMN empresa.tipo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.empresa.tipo IS 'PERSONAL (1.0, creada automáticamente) o JURIDICA (edición Enterprise, ADR-029).';


--
-- Name: COLUMN empresa.propietario_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.empresa.propietario_id IS 'Usuario dueño de la empresa PERSONAL; nulo en las jurídicas.';


--
-- Name: COLUMN empresa.nit; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.empresa.nit IS '14 dígitos. En la versión abierta (empresa PERSONAL) no se captura ni se edita: es nulo (ADR-031, ADR-032). Es de la empresa jurídica de la edición Enterprise, donde es obligatorio (ADR-029).';


--
-- Name: COLUMN empresa.nrc; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.empresa.nrc IS 'Registro de IVA. En la versión abierta no se captura ni se edita (ADR-031, ADR-032); es de la empresa jurídica de la edición Enterprise (ADR-029). Sin CHECK de formato: [VERIFICAR] formato con el MH/contador.';


--
-- Name: COLUMN empresa.nombre; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.empresa.nombre IS 'Razón social o nombre de la empresa.';


--
-- Name: COLUMN empresa.estado; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.empresa.estado IS 'ACTIVA o INACTIVA.';


--
-- Name: COLUMN empresa.actualizado_en; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.empresa.actualizado_en IS 'Última modificación (UTC).';


--
-- Name: COLUMN empresa.actualizado_por; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.empresa.actualizado_por IS 'Usuario (app.usuario_id) que hizo la última modificación.';


--
-- Name: COLUMN empresa.version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.empresa.version IS 'Versión para concurrencia optimista (If-Match/ETag).';


--
-- Name: empresa_aplicacion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.empresa_aplicacion (
    empresa_id uuid NOT NULL,
    aplicacion_codigo character varying(40) NOT NULL,
    instalada_en timestamp with time zone DEFAULT now() NOT NULL,
    instalada_por character varying(64) NOT NULL
);

ALTER TABLE ONLY public.empresa_aplicacion FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE empresa_aplicacion; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.empresa_aplicacion IS 'Apps instaladas por empresa (ADR-030). Una fila = instalada; no hay desinstalación en 1.0.';


--
-- Name: COLUMN empresa_aplicacion.instalada_en; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.empresa_aplicacion.instalada_en IS 'Momento de la instalación (UTC).';


--
-- Name: COLUMN empresa_aplicacion.instalada_por; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.empresa_aplicacion.instalada_por IS 'Usuario que la instaló (app.usuario_id).';


--
-- Name: empresa_usuario; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.empresa_usuario (
    empresa_id uuid NOT NULL,
    usuario_id uuid NOT NULL,
    rol character varying(30) NOT NULL,
    estado character varying(15) DEFAULT 'ACTIVA'::character varying NOT NULL,
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    actualizado_en timestamp with time zone DEFAULT now() NOT NULL,
    actualizado_por character varying(64),
    CONSTRAINT ck_empresa_usuario_estado CHECK (((estado)::text = ANY ((ARRAY['ACTIVA'::character varying, 'INACTIVA'::character varying])::text[]))),
    CONSTRAINT ck_empresa_usuario_rol CHECK (((rol)::text = ANY ((ARRAY['admin_empresa'::character varying, 'contador'::character varying, 'auditor'::character varying])::text[])))
);

ALTER TABLE ONLY public.empresa_usuario FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE empresa_usuario; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.empresa_usuario IS 'Membresía de un usuario en una empresa con un rol (CLAUDE.md §14.2). Un usuario puede tener varias.';


--
-- Name: COLUMN empresa_usuario.rol; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.empresa_usuario.rol IS 'admin_empresa, contador o auditor.';


--
-- Name: COLUMN empresa_usuario.estado; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.empresa_usuario.estado IS 'ACTIVA o INACTIVA (la desactivación no borra la fila).';


--
-- Name: COLUMN empresa_usuario.actualizado_en; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.empresa_usuario.actualizado_en IS 'Última modificación (UTC).';


--
-- Name: COLUMN empresa_usuario.actualizado_por; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.empresa_usuario.actualizado_por IS 'Usuario (app.usuario_id) que hizo la última modificación.';


--
-- Name: idempotencia; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.idempotencia (
    empresa_id uuid NOT NULL,
    clave character varying(100) NOT NULL,
    hash_solicitud character(64) NOT NULL,
    estado_http smallint NOT NULL,
    respuesta jsonb NOT NULL,
    creado_en timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY public.idempotencia FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE idempotencia; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.idempotencia IS 'Respuestas guardadas por Idempotency-Key para que un reintento no duplique su efecto; retención 7 días (CLAUDE.md 12.6).';


--
-- Name: COLUMN idempotencia.empresa_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.idempotencia.empresa_id IS 'Empresa dueña de la clave; la clave solo es única dentro de una empresa.';


--
-- Name: COLUMN idempotencia.clave; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.idempotencia.clave IS 'Valor del header Idempotency-Key.';


--
-- Name: COLUMN idempotencia.hash_solicitud; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.idempotencia.hash_solicitud IS 'SHA-256 (hex) del cuerpo: misma clave con otro cuerpo se rechaza (INT-005).';


--
-- Name: COLUMN idempotencia.estado_http; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.idempotencia.estado_http IS 'Código HTTP de la respuesta original, para devolverla igual.';


--
-- Name: COLUMN idempotencia.respuesta; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.idempotencia.respuesta IS 'Cuerpo de la respuesta original.';


--
-- Name: COLUMN idempotencia.creado_en; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.idempotencia.creado_en IS 'Momento de registro (UTC); base de la purga a 7 días.';


--
-- Name: operacion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.operacion (
    id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    tipo character varying(30) NOT NULL,
    fecha date NOT NULL,
    descripcion character varying(300),
    datos jsonb NOT NULL,
    resumen jsonb NOT NULL,
    total numeric(19,2) NOT NULL,
    asiento_id uuid NOT NULL,
    estado character varying(13) DEFAULT 'CONTABILIZADA'::character varying NOT NULL,
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    creado_por character varying(64) NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_operacion_estado CHECK (((estado)::text = ANY ((ARRAY['CONTABILIZADA'::character varying, 'REVERTIDA'::character varying])::text[]))),
    CONSTRAINT ck_operacion_tipo CHECK (((tipo)::text = ANY ((ARRAY['VENTA'::character varying, 'COMPRA_GASTO'::character varying, 'COBRO_CLIENTE'::character varying, 'PAGO_PROVEEDOR'::character varying, 'APORTE_CAPITAL'::character varying, 'PRESTAMO_RECIBIDO'::character varying, 'PAGO_CUOTA'::character varying, 'TRASLADO_FONDOS'::character varying, 'COMPRA_ACTIVO_FIJO'::character varying, 'DEPRECIACION_MENSUAL'::character varying])::text[]))),
    CONSTRAINT operacion_total_check CHECK ((total > (0)::numeric))
);

ALTER TABLE ONLY public.operacion FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE operacion; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.operacion IS 'Hecho de negocio registrado desde una pantalla guiada (ADR-041); guarda la entrada, el resumen calculado y el asiento resultante. Inmutable salvo el paso a REVERTIDA (ADR-019). RLS forzado.';


--
-- Name: COLUMN operacion.tipo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.operacion.tipo IS 'Uno de los diez tipos guiados del motor ContabilizarOperacion (ADR-041 §5.2).';


--
-- Name: COLUMN operacion.fecha; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.operacion.fecha IS 'Fecha contable de la operación (DATE en hora de El Salvador, como asiento.fecha).';


--
-- Name: COLUMN operacion.datos; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.operacion.datos IS 'Entrada de negocio ya validada (el cuerpo tipado del formulario), tal como la recibió el motor.';


--
-- Name: COLUMN operacion.resumen; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.operacion.resumen IS 'Resumen calculado por el motor: base, IVA, exento, no sujeto y total (ResumenOperacion del contrato).';


--
-- Name: COLUMN operacion.asiento_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.operacion.asiento_id IS 'Asiento generado por el motor en la misma transacción (ADR-018); nunca nulo.';


--
-- Name: COLUMN operacion.estado; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.operacion.estado IS 'CONTABILIZADA o REVERTIDA; pasa a REVERTIDA cuando se revierte su asiento (evento AsientoRevertido, ADR-036).';


--
-- Name: COLUMN operacion.creado_por; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.operacion.creado_por IS 'Valor de app.usuario_id que registró la operación.';


--
-- Name: COLUMN operacion.version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.operacion.version IS 'Versión para concurrencia optimista (paso a REVERTIDA).';


--
-- Name: plantilla_configuracion_contable; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.plantilla_configuracion_contable (
    id boolean DEFAULT true NOT NULL,
    modo_precio_defecto character varying(7) NOT NULL,
    cuenta_iva_debito_codigo character varying(8) NOT NULL,
    cuenta_iva_credito_codigo character varying(8) NOT NULL,
    CONSTRAINT ck_plantilla_configuracion_modo CHECK (((modo_precio_defecto)::text = ANY ((ARRAY['CON_IVA'::character varying, 'SIN_IVA'::character varying])::text[]))),
    CONSTRAINT ck_plantilla_configuracion_unica CHECK (id)
);


--
-- Name: TABLE plantilla_configuracion_contable; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.plantilla_configuracion_contable IS 'Configuración contable por defecto: una sola fila. Borrador pendiente de validación por contador (ADR-034).';


--
-- Name: COLUMN plantilla_configuracion_contable.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.plantilla_configuracion_contable.id IS 'Siempre true: con su CHECK y su PRIMARY KEY impide una segunda fila.';


--
-- Name: COLUMN plantilla_configuracion_contable.modo_precio_defecto; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.plantilla_configuracion_contable.modo_precio_defecto IS 'CON_IVA (el monto incluye IVA) o SIN_IVA (el IVA se suma), CLAUDE.md §11.1.';


--
-- Name: COLUMN plantilla_configuracion_contable.cuenta_iva_debito_codigo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.plantilla_configuracion_contable.cuenta_iva_debito_codigo IS 'IVA débito fiscal (pasivo): 21020101.';


--
-- Name: COLUMN plantilla_configuracion_contable.cuenta_iva_credito_codigo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.plantilla_configuracion_contable.cuenta_iva_credito_codigo IS 'IVA crédito fiscal (activo): 11040101.';


--
-- Name: plantilla_cuenta; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.plantilla_cuenta (
    codigo character varying(8) NOT NULL,
    nombre character varying(200) NOT NULL,
    naturaleza character varying(9) NOT NULL,
    nivel smallint GENERATED ALWAYS AS (
CASE length((codigo)::text)
    WHEN 1 THEN 1
    WHEN 2 THEN 2
    WHEN 4 THEN 3
    WHEN 6 THEN 4
    WHEN 8 THEN 5
    ELSE NULL::integer
END) STORED,
    clase smallint GENERATED ALWAYS AS ((substr((codigo)::text, 1, 1))::smallint) STORED,
    CONSTRAINT ck_plantilla_cuenta_codigo CHECK (((codigo)::text ~ '^[1-5][0-9]*$'::text)),
    CONSTRAINT ck_plantilla_cuenta_longitud CHECK ((length((codigo)::text) = ANY (ARRAY[1, 2, 4, 6, 8]))),
    CONSTRAINT ck_plantilla_cuenta_naturaleza CHECK (((naturaleza)::text = ANY ((ARRAY['DEUDORA'::character varying, 'ACREEDORA'::character varying])::text[])))
);


--
-- Name: TABLE plantilla_cuenta; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.plantilla_cuenta IS 'Catálogo de cuentas base: borrador pendiente de validación por contador (ADR-034). Global, solo lectura para pilot_app.';


--
-- Name: COLUMN plantilla_cuenta.codigo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.plantilla_cuenta.codigo IS 'Código de la cuenta; su primer dígito es la clase (1 a 5) y su longitud el nivel.';


--
-- Name: COLUMN plantilla_cuenta.naturaleza; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.plantilla_cuenta.naturaleza IS 'DEUDORA o ACREEDORA; por defecto según la clase, con excepciones marcadas en el catálogo base (p. ej. depreciación acumulada).';


--
-- Name: COLUMN plantilla_cuenta.nivel; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.plantilla_cuenta.nivel IS 'Derivado de la longitud: 1 clase, 2 grupo, 3 cuenta, 4 subcuenta, 5 detalle (CLAUDE.md §10.2).';


--
-- Name: COLUMN plantilla_cuenta.clase; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.plantilla_cuenta.clase IS 'Derivado del primer dígito: 1 Activo, 2 Pasivo, 3 Patrimonio, 4 Costos y Gastos (grupo 44 aparte), 5 Ingresos.';


--
-- Name: plantilla_regla_contabilizacion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.plantilla_regla_contabilizacion (
    tipo_operacion character varying(40) NOT NULL,
    categoria character varying(13) NOT NULL,
    codigo character varying(40) NOT NULL,
    cuenta_codigo character varying(8),
    activa boolean DEFAULT true NOT NULL,
    prefijo_permitido character varying(8) NOT NULL,
    CONSTRAINT ck_plantilla_regla_categoria CHECK (((categoria)::text = ANY ((ARRAY['INGRESO'::character varying, 'COBRO'::character varying, 'PAGO'::character varying, 'GASTO'::character varying, 'CONTRAPARTIDA'::character varying, 'ACTIVO'::character varying, 'DEPRECIACION'::character varying])::text[]))),
    CONSTRAINT ck_plantilla_regla_cuenta CHECK (((NOT activa) OR (cuenta_codigo IS NOT NULL))),
    CONSTRAINT ck_plantilla_regla_tipo CHECK (((tipo_operacion)::text = ANY ((ARRAY['CIERRE_INGRESOS_DIARIO'::character varying, 'VENTA'::character varying, 'COMPRA_GASTO'::character varying, 'COBRO_CLIENTE'::character varying, 'PAGO_PROVEEDOR'::character varying, 'APORTE_CAPITAL'::character varying, 'PRESTAMO_RECIBIDO'::character varying, 'PAGO_CUOTA'::character varying, 'COMPRA_ACTIVO_FIJO'::character varying, 'DEPRECIACION_MENSUAL'::character varying])::text[])))
);


--
-- Name: TABLE plantilla_regla_contabilizacion; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.plantilla_regla_contabilizacion IS 'Reglas de contabilización por defecto, con el código de la cuenta (no su id). Borrador pendiente de validación por contador (ADR-034).';


--
-- Name: COLUMN plantilla_regla_contabilizacion.categoria; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.plantilla_regla_contabilizacion.categoria IS 'INGRESO (concepto de ingreso) o COBRO (forma de pago).';


--
-- Name: COLUMN plantilla_regla_contabilizacion.cuenta_codigo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.plantilla_regla_contabilizacion.cuenta_codigo IS 'Cuenta de detalle por defecto; nula solo en la regla inactiva COBRO/OTRO, que el contador configura y activa (ADR-035).';


--
-- Name: COLUMN plantilla_regla_contabilizacion.activa; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.plantilla_regla_contabilizacion.activa IS 'false solo para COBRO/OTRO, que no tiene cuenta por defecto.';


--
-- Name: COLUMN plantilla_regla_contabilizacion.prefijo_permitido; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.plantilla_regla_contabilizacion.prefijo_permitido IS 'Prefijo de código que debe tener la cuenta de esta regla (p. ej. "1101"); base de CON-022 (ADR-042).';


--
-- Name: CONSTRAINT ck_plantilla_regla_tipo ON plantilla_regla_contabilizacion; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT ck_plantilla_regla_tipo ON public.plantilla_regla_contabilizacion IS 'CIERRE_INGRESOS_DIARIO (n8n) más los diez tipos guiados de ADR-041; otro tipo exige un ADR y una migración nueva.';


--
-- Name: plantilla_vida_util; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.plantilla_vida_util (
    categoria character varying(20) NOT NULL,
    vida_util_meses smallint NOT NULL,
    valor_residual_porcentaje numeric(5,4) NOT NULL,
    inicia_mes_siguiente boolean NOT NULL,
    confirmada boolean DEFAULT false NOT NULL,
    CONSTRAINT ck_plantilla_vida_util_categoria CHECK (((categoria)::text = ANY ((ARRAY['MOBILIARIO_EQUIPO'::character varying, 'EQUIPO_COMPUTO'::character varying, 'VEHICULO'::character varying, 'EDIFICIO'::character varying])::text[]))),
    CONSTRAINT plantilla_vida_util_valor_residual_porcentaje_check CHECK (((valor_residual_porcentaje >= (0)::numeric) AND (valor_residual_porcentaje < (1)::numeric))),
    CONSTRAINT plantilla_vida_util_vida_util_meses_check CHECK ((vida_util_meses > 0))
);


--
-- Name: TABLE plantilla_vida_util; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.plantilla_vida_util IS 'Vida útil, residual y mes de inicio por categoría de activo depreciable: borrador [VERIFICAR] con el contador (ADR-034, ADR-041). Global, solo lectura para pilot_app.';


--
-- Name: COLUMN plantilla_vida_util.categoria; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.plantilla_vida_util.categoria IS 'Categoría de activo depreciable; TERRENO no aparece aquí porque no se deprecia (spec F4.5 §5.5).';


--
-- Name: COLUMN plantilla_vida_util.valor_residual_porcentaje; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.plantilla_vida_util.valor_residual_porcentaje IS 'Fracción del costo que no se deprecia (0.0000 a 0.9999); provisional 0 para las cuatro categorías (spec F4.5 §5.5).';


--
-- Name: COLUMN plantilla_vida_util.inicia_mes_siguiente; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.plantilla_vida_util.inicia_mes_siguiente IS 'true: la depreciación inicia el mes siguiente al de la adquisición (propuesta provisional, spec F4.5 §5.5).';


--
-- Name: COLUMN plantilla_vida_util.confirmada; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.plantilla_vida_util.confirmada IS 'false mientras el contador no valide estos valores; DEPRECIACION_MENSUAL responde 422 CON-023 mientras tanto (ADR-041).';


--
-- Name: regla_contabilizacion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.regla_contabilizacion (
    id uuid NOT NULL,
    empresa_id uuid NOT NULL,
    tipo_operacion character varying(40) NOT NULL,
    categoria character varying(13) NOT NULL,
    codigo character varying(40) NOT NULL,
    cuenta_id uuid,
    activa boolean DEFAULT true NOT NULL,
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    creado_por character varying(64) NOT NULL,
    actualizado_en timestamp with time zone DEFAULT now() NOT NULL,
    actualizado_por character varying(64),
    version bigint DEFAULT 0 NOT NULL,
    prefijo_permitido character varying(8) NOT NULL,
    CONSTRAINT ck_regla_contabilizacion_categoria CHECK (((categoria)::text = ANY ((ARRAY['INGRESO'::character varying, 'COBRO'::character varying, 'PAGO'::character varying, 'GASTO'::character varying, 'CONTRAPARTIDA'::character varying, 'ACTIVO'::character varying, 'DEPRECIACION'::character varying])::text[]))),
    CONSTRAINT ck_regla_contabilizacion_cuenta CHECK (((NOT activa) OR (cuenta_id IS NOT NULL))),
    CONSTRAINT ck_regla_contabilizacion_tipo CHECK (((tipo_operacion)::text = ANY ((ARRAY['CIERRE_INGRESOS_DIARIO'::character varying, 'VENTA'::character varying, 'COMPRA_GASTO'::character varying, 'COBRO_CLIENTE'::character varying, 'PAGO_PROVEEDOR'::character varying, 'APORTE_CAPITAL'::character varying, 'PRESTAMO_RECIBIDO'::character varying, 'PAGO_CUOTA'::character varying, 'COMPRA_ACTIVO_FIJO'::character varying, 'DEPRECIACION_MENSUAL'::character varying])::text[])))
);

ALTER TABLE ONLY public.regla_contabilizacion FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE regla_contabilizacion; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.regla_contabilizacion IS 'Reglas que dicen qué cuenta usar por tipo de operación, categoría y código en las operaciones de n8n. RLS forzado.';


--
-- Name: COLUMN regla_contabilizacion.tipo_operacion; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.regla_contabilizacion.tipo_operacion IS 'Tipo de operación externa; en 1.0 solo CIERRE_INGRESOS_DIARIO.';


--
-- Name: COLUMN regla_contabilizacion.categoria; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.regla_contabilizacion.categoria IS 'INGRESO (concepto de ingreso) o COBRO (forma de pago).';


--
-- Name: COLUMN regla_contabilizacion.codigo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.regla_contabilizacion.codigo IS 'Concepto (VENTAS_GRAVADAS…) o forma de pago (EFECTIVO…), en mayúsculas con guion bajo.';


--
-- Name: COLUMN regla_contabilizacion.cuenta_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.regla_contabilizacion.cuenta_id IS 'Cuenta de detalle de la misma empresa (llave compuesta); nula solo si la regla está inactiva (ADR-035).';


--
-- Name: COLUMN regla_contabilizacion.activa; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.regla_contabilizacion.activa IS 'Una operación que use una regla inactiva o ausente se rechaza con CON-020.';


--
-- Name: COLUMN regla_contabilizacion.creado_por; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.regla_contabilizacion.creado_por IS 'Usuario (app.usuario_id) que la creó.';


--
-- Name: COLUMN regla_contabilizacion.actualizado_en; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.regla_contabilizacion.actualizado_en IS 'Última modificación (UTC).';


--
-- Name: COLUMN regla_contabilizacion.actualizado_por; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.regla_contabilizacion.actualizado_por IS 'Usuario (app.usuario_id) que hizo la última modificación.';


--
-- Name: COLUMN regla_contabilizacion.version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.regla_contabilizacion.version IS 'Versión para concurrencia optimista (If-Match/ETag).';


--
-- Name: COLUMN regla_contabilizacion.prefijo_permitido; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.regla_contabilizacion.prefijo_permitido IS 'Prefijo de código que debe tener la cuenta de esta regla; asignarle una cuenta fuera del grupo responde 422 CON-022 (ADR-042). Se completa más abajo antes de quedar NOT NULL.';


--
-- Name: saldo_cuenta_mensual; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.saldo_cuenta_mensual (
    empresa_id uuid NOT NULL,
    cuenta_id uuid NOT NULL,
    anio smallint NOT NULL,
    mes smallint NOT NULL,
    total_debe numeric(19,2) DEFAULT 0 NOT NULL,
    total_haber numeric(19,2) DEFAULT 0 NOT NULL,
    actualizado_en timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_saldo_cuenta_mensual_debe CHECK ((total_debe >= (0)::numeric)),
    CONSTRAINT ck_saldo_cuenta_mensual_haber CHECK ((total_haber >= (0)::numeric)),
    CONSTRAINT ck_saldo_cuenta_mensual_mes CHECK (((mes >= 1) AND (mes <= 12)))
);

ALTER TABLE ONLY public.saldo_cuenta_mensual FORCE ROW LEVEL SECURITY;


--
-- Name: TABLE saldo_cuenta_mensual; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.saldo_cuenta_mensual IS 'Movimientos acumulados por cuenta de detalle y mes. Solo se modifica con el upsert de CLAUDE.md §10.3, en la misma transacción del asiento (ADR-018). RLS forzado.';


--
-- Name: COLUMN saldo_cuenta_mensual.cuenta_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.saldo_cuenta_mensual.cuenta_id IS 'Cuenta de detalle de la misma empresa (llave compuesta); las cuentas padre se calculan al consultar.';


--
-- Name: COLUMN saldo_cuenta_mensual.anio; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.saldo_cuenta_mensual.anio IS 'Año de la fecha contable de las líneas acumuladas.';


--
-- Name: COLUMN saldo_cuenta_mensual.mes; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.saldo_cuenta_mensual.mes IS 'Mes (1 a 12) de la fecha contable de las líneas acumuladas.';


--
-- Name: COLUMN saldo_cuenta_mensual.total_debe; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.saldo_cuenta_mensual.total_debe IS 'Σ Debe de las líneas de la cuenta en el mes; invariante: igual a la suma de asiento_linea.';


--
-- Name: COLUMN saldo_cuenta_mensual.total_haber; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.saldo_cuenta_mensual.total_haber IS 'Σ Haber de las líneas de la cuenta en el mes; invariante: igual a la suma de asiento_linea.';


--
-- Name: COLUMN saldo_cuenta_mensual.actualizado_en; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.saldo_cuenta_mensual.actualizado_en IS 'Última acumulación (UTC).';


--
-- Name: CONSTRAINT ck_saldo_cuenta_mensual_mes ON saldo_cuenta_mensual; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT ck_saldo_cuenta_mensual_mes ON public.saldo_cuenta_mensual IS 'Mes válido de 1 a 12.';


--
-- Name: tasa_impuesto; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tasa_impuesto (
    id uuid NOT NULL,
    tipo character varying(20) NOT NULL,
    tasa numeric(7,4) NOT NULL,
    vigente_desde date NOT NULL,
    vigente_hasta date,
    CONSTRAINT ck_tasa_impuesto_tasa CHECK (((tasa >= (0)::numeric) AND (tasa <= (1)::numeric))),
    CONSTRAINT ck_tasa_impuesto_tipo CHECK (((tipo)::text = 'IVA'::text)),
    CONSTRAINT ck_tasa_impuesto_vigencia CHECK (((vigente_hasta IS NULL) OR (vigente_hasta >= vigente_desde)))
);


--
-- Name: TABLE tasa_impuesto; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.tasa_impuesto IS 'Tasas de impuesto con vigencia (desde/hasta). Global, sin RLS; la cambian las migraciones, nunca la aplicación (regla 1.1.13).';


--
-- Name: COLUMN tasa_impuesto.tipo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tasa_impuesto.tipo IS 'Impuesto al que aplica; en 1.0 solo IVA.';


--
-- Name: COLUMN tasa_impuesto.tasa; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tasa_impuesto.tasa IS 'Fracción decimal: 0.1300 = 13 %.';


--
-- Name: COLUMN tasa_impuesto.vigente_desde; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tasa_impuesto.vigente_desde IS 'Primer día de vigencia. La fila inicial de IVA usa la fecha TÉCNICA 2000-01-01 (ADR-034); la fecha exacta es [VERIFICAR] con el contador.';


--
-- Name: COLUMN tasa_impuesto.vigente_hasta; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tasa_impuesto.vigente_hasta IS 'Último día de vigencia (inclusive); nulo = vigente sin fecha de fin.';


--
-- Name: usuario; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.usuario (
    id uuid NOT NULL,
    sub_keycloak character varying(64) NOT NULL,
    correo character varying(254) NOT NULL,
    nombre character varying(200) NOT NULL,
    telefono character varying(12) NOT NULL,
    recomendaciones_aceptadas_en timestamp with time zone,
    recomendaciones_retiradas_en timestamp with time zone,
    estado character varying(15) DEFAULT 'ACTIVO'::character varying NOT NULL,
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    actualizado_en timestamp with time zone,
    CONSTRAINT ck_usuario_correo_minuscula CHECK (((correo)::text = lower((correo)::text))),
    CONSTRAINT ck_usuario_estado CHECK (((estado)::text = ANY ((ARRAY['ACTIVO'::character varying, 'BLOQUEADO'::character varying])::text[]))),
    CONSTRAINT ck_usuario_telefono CHECK (((telefono)::text ~ '^\+503[0-9]{8}$'::text))
);


--
-- Name: TABLE usuario; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.usuario IS 'Persona que inicia sesión (tabla global, sin RLS). La identidad y la contraseña viven en Keycloak; aquí solo el perfil (ADR-028).';


--
-- Name: COLUMN usuario.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.usuario.id IS 'UUID v7 generado por la aplicación (ADR-010).';


--
-- Name: COLUMN usuario.sub_keycloak; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.usuario.sub_keycloak IS 'Claim "sub" del token OIDC; enlaza la sesión con el usuario. Inmutable para la aplicación.';


--
-- Name: COLUMN usuario.correo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.usuario.correo IS 'Correo verificado en Keycloak, siempre en minúsculas. Dato personal: se enmascara en logs.';


--
-- Name: COLUMN usuario.nombre; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.usuario.nombre IS 'Nombre completo del usuario. Dato personal.';


--
-- Name: COLUMN usuario.telefono; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.usuario.telefono IS 'Teléfono de El Salvador con formato +503XXXXXXXX. Dato personal (ADR-028).';


--
-- Name: COLUMN usuario.recomendaciones_aceptadas_en; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.usuario.recomendaciones_aceptadas_en IS '"Acepto recibir recomendaciones por correo": momento de aceptación; nulo = nunca aceptó (ADR-028).';


--
-- Name: COLUMN usuario.recomendaciones_retiradas_en; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.usuario.recomendaciones_retiradas_en IS 'Último retiro del consentimiento; vigente si aceptadas_en > retiradas_en o retiradas_en es nulo.';


--
-- Name: COLUMN usuario.estado; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.usuario.estado IS 'ACTIVO o BLOQUEADO.';


--
-- Name: COLUMN usuario.creado_en; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.usuario.creado_en IS 'Momento del alta (UTC), en el primer inicio de sesión.';


--
-- Name: COLUMN usuario.actualizado_en; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.usuario.actualizado_en IS 'Última modificación del perfil (UTC); nulo si nunca se modificó.';


--
-- Name: auditoria_2026_09; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria ATTACH PARTITION public.auditoria_2026_09 FOR VALUES FROM ('2026-09-01 00:00:00+00') TO ('2026-10-01 00:00:00+00');


--
-- Name: auditoria_2026_10; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria ATTACH PARTITION public.auditoria_2026_10 FOR VALUES FROM ('2026-10-01 00:00:00+00') TO ('2026-11-01 00:00:00+00');


--
-- Name: auditoria_2026_11; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria ATTACH PARTITION public.auditoria_2026_11 FOR VALUES FROM ('2026-11-01 00:00:00+00') TO ('2026-12-01 00:00:00+00');


--
-- Name: auditoria_2026_12; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria ATTACH PARTITION public.auditoria_2026_12 FOR VALUES FROM ('2026-12-01 00:00:00+00') TO ('2027-01-01 00:00:00+00');


--
-- Name: auditoria_2027_01; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria ATTACH PARTITION public.auditoria_2027_01 FOR VALUES FROM ('2027-01-01 00:00:00+00') TO ('2027-02-01 00:00:00+00');


--
-- Name: auditoria_2027_02; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria ATTACH PARTITION public.auditoria_2027_02 FOR VALUES FROM ('2027-02-01 00:00:00+00') TO ('2027-03-01 00:00:00+00');


--
-- Name: auditoria_2027_03; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria ATTACH PARTITION public.auditoria_2027_03 FOR VALUES FROM ('2027-03-01 00:00:00+00') TO ('2027-04-01 00:00:00+00');


--
-- Name: auditoria_2027_04; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria ATTACH PARTITION public.auditoria_2027_04 FOR VALUES FROM ('2027-04-01 00:00:00+00') TO ('2027-05-01 00:00:00+00');


--
-- Name: auditoria_2027_05; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria ATTACH PARTITION public.auditoria_2027_05 FOR VALUES FROM ('2027-05-01 00:00:00+00') TO ('2027-06-01 00:00:00+00');


--
-- Name: auditoria_2027_06; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria ATTACH PARTITION public.auditoria_2027_06 FOR VALUES FROM ('2027-06-01 00:00:00+00') TO ('2027-07-01 00:00:00+00');


--
-- Name: auditoria_2027_07; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria ATTACH PARTITION public.auditoria_2027_07 FOR VALUES FROM ('2027-07-01 00:00:00+00') TO ('2027-08-01 00:00:00+00');


--
-- Name: auditoria_2027_08; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria ATTACH PARTITION public.auditoria_2027_08 FOR VALUES FROM ('2027-08-01 00:00:00+00') TO ('2027-09-01 00:00:00+00');


--
-- Name: auditoria_2027_09; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria ATTACH PARTITION public.auditoria_2027_09 FOR VALUES FROM ('2027-09-01 00:00:00+00') TO ('2027-10-01 00:00:00+00');


--
-- Name: auditoria_2027_10; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria ATTACH PARTITION public.auditoria_2027_10 FOR VALUES FROM ('2027-10-01 00:00:00+00') TO ('2027-11-01 00:00:00+00');


--
-- Name: auditoria_2027_11; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria ATTACH PARTITION public.auditoria_2027_11 FOR VALUES FROM ('2027-11-01 00:00:00+00') TO ('2027-12-01 00:00:00+00');


--
-- Name: auditoria_2027_12; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria ATTACH PARTITION public.auditoria_2027_12 FOR VALUES FROM ('2027-12-01 00:00:00+00') TO ('2028-01-01 00:00:00+00');


--
-- Name: auditoria_default; Type: TABLE ATTACH; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria ATTACH PARTITION public.auditoria_default DEFAULT;


--
-- Name: activo_fijo activo_fijo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activo_fijo
    ADD CONSTRAINT activo_fijo_pkey PRIMARY KEY (id);


--
-- Name: api_key api_key_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api_key
    ADD CONSTRAINT api_key_pkey PRIMARY KEY (id);


--
-- Name: api_key api_key_prefijo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api_key
    ADD CONSTRAINT api_key_prefijo_key UNIQUE (prefijo);


--
-- Name: aplicacion aplicacion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aplicacion
    ADD CONSTRAINT aplicacion_pkey PRIMARY KEY (codigo);


--
-- Name: asiento_linea asiento_linea_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asiento_linea
    ADD CONSTRAINT asiento_linea_pkey PRIMARY KEY (id);


--
-- Name: asiento asiento_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asiento
    ADD CONSTRAINT asiento_pkey PRIMARY KEY (id);


--
-- Name: auditoria auditoria_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria
    ADD CONSTRAINT auditoria_pkey PRIMARY KEY (id, creado_en);


--
-- Name: auditoria_2026_09 auditoria_2026_09_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria_2026_09
    ADD CONSTRAINT auditoria_2026_09_pkey PRIMARY KEY (id, creado_en);


--
-- Name: auditoria_2026_10 auditoria_2026_10_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria_2026_10
    ADD CONSTRAINT auditoria_2026_10_pkey PRIMARY KEY (id, creado_en);


--
-- Name: auditoria_2026_11 auditoria_2026_11_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria_2026_11
    ADD CONSTRAINT auditoria_2026_11_pkey PRIMARY KEY (id, creado_en);


--
-- Name: auditoria_2026_12 auditoria_2026_12_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria_2026_12
    ADD CONSTRAINT auditoria_2026_12_pkey PRIMARY KEY (id, creado_en);


--
-- Name: auditoria_2027_01 auditoria_2027_01_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria_2027_01
    ADD CONSTRAINT auditoria_2027_01_pkey PRIMARY KEY (id, creado_en);


--
-- Name: auditoria_2027_02 auditoria_2027_02_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria_2027_02
    ADD CONSTRAINT auditoria_2027_02_pkey PRIMARY KEY (id, creado_en);


--
-- Name: auditoria_2027_03 auditoria_2027_03_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria_2027_03
    ADD CONSTRAINT auditoria_2027_03_pkey PRIMARY KEY (id, creado_en);


--
-- Name: auditoria_2027_04 auditoria_2027_04_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria_2027_04
    ADD CONSTRAINT auditoria_2027_04_pkey PRIMARY KEY (id, creado_en);


--
-- Name: auditoria_2027_05 auditoria_2027_05_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria_2027_05
    ADD CONSTRAINT auditoria_2027_05_pkey PRIMARY KEY (id, creado_en);


--
-- Name: auditoria_2027_06 auditoria_2027_06_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria_2027_06
    ADD CONSTRAINT auditoria_2027_06_pkey PRIMARY KEY (id, creado_en);


--
-- Name: auditoria_2027_07 auditoria_2027_07_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria_2027_07
    ADD CONSTRAINT auditoria_2027_07_pkey PRIMARY KEY (id, creado_en);


--
-- Name: auditoria_2027_08 auditoria_2027_08_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria_2027_08
    ADD CONSTRAINT auditoria_2027_08_pkey PRIMARY KEY (id, creado_en);


--
-- Name: auditoria_2027_09 auditoria_2027_09_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria_2027_09
    ADD CONSTRAINT auditoria_2027_09_pkey PRIMARY KEY (id, creado_en);


--
-- Name: auditoria_2027_10 auditoria_2027_10_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria_2027_10
    ADD CONSTRAINT auditoria_2027_10_pkey PRIMARY KEY (id, creado_en);


--
-- Name: auditoria_2027_11 auditoria_2027_11_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria_2027_11
    ADD CONSTRAINT auditoria_2027_11_pkey PRIMARY KEY (id, creado_en);


--
-- Name: auditoria_2027_12 auditoria_2027_12_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria_2027_12
    ADD CONSTRAINT auditoria_2027_12_pkey PRIMARY KEY (id, creado_en);


--
-- Name: auditoria_default auditoria_default_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria_default
    ADD CONSTRAINT auditoria_default_pkey PRIMARY KEY (id, creado_en);


--
-- Name: auditoria_global auditoria_global_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auditoria_global
    ADD CONSTRAINT auditoria_global_pkey PRIMARY KEY (id);


--
-- Name: configuracion_contable configuracion_contable_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.configuracion_contable
    ADD CONSTRAINT configuracion_contable_pkey PRIMARY KEY (empresa_id);


--
-- Name: cuenta_contable cuenta_contable_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cuenta_contable
    ADD CONSTRAINT cuenta_contable_pkey PRIMARY KEY (id);


--
-- Name: depreciacion_registrada depreciacion_registrada_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.depreciacion_registrada
    ADD CONSTRAINT depreciacion_registrada_pkey PRIMARY KEY (id);


--
-- Name: empresa_aplicacion empresa_aplicacion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.empresa_aplicacion
    ADD CONSTRAINT empresa_aplicacion_pkey PRIMARY KEY (empresa_id, aplicacion_codigo);


--
-- Name: empresa empresa_nit_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.empresa
    ADD CONSTRAINT empresa_nit_key UNIQUE (nit);


--
-- Name: empresa empresa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.empresa
    ADD CONSTRAINT empresa_pkey PRIMARY KEY (id);


--
-- Name: empresa_usuario empresa_usuario_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.empresa_usuario
    ADD CONSTRAINT empresa_usuario_pkey PRIMARY KEY (empresa_id, usuario_id);


--
-- Name: tasa_impuesto ex_tasa_impuesto_sin_traslape; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tasa_impuesto
    ADD CONSTRAINT ex_tasa_impuesto_sin_traslape EXCLUDE USING gist (tipo WITH =, daterange(vigente_desde, vigente_hasta, '[]'::text) WITH &&);


--
-- Name: CONSTRAINT ex_tasa_impuesto_sin_traslape ON tasa_impuesto; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT ex_tasa_impuesto_sin_traslape ON public.tasa_impuesto IS 'Impide dos vigencias solapadas del mismo tipo de impuesto.';


--
-- Name: idempotencia idempotencia_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.idempotencia
    ADD CONSTRAINT idempotencia_pkey PRIMARY KEY (empresa_id, clave);


--
-- Name: operacion operacion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operacion
    ADD CONSTRAINT operacion_pkey PRIMARY KEY (id);


--
-- Name: correlativo_asiento pk_correlativo_asiento; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.correlativo_asiento
    ADD CONSTRAINT pk_correlativo_asiento PRIMARY KEY (empresa_id, anio);


--
-- Name: saldo_cuenta_mensual pk_saldo_cuenta_mensual; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.saldo_cuenta_mensual
    ADD CONSTRAINT pk_saldo_cuenta_mensual PRIMARY KEY (empresa_id, cuenta_id, anio, mes);


--
-- Name: plantilla_configuracion_contable plantilla_configuracion_contable_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plantilla_configuracion_contable
    ADD CONSTRAINT plantilla_configuracion_contable_pkey PRIMARY KEY (id);


--
-- Name: plantilla_cuenta plantilla_cuenta_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plantilla_cuenta
    ADD CONSTRAINT plantilla_cuenta_pkey PRIMARY KEY (codigo);


--
-- Name: plantilla_regla_contabilizacion plantilla_regla_contabilizacion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plantilla_regla_contabilizacion
    ADD CONSTRAINT plantilla_regla_contabilizacion_pkey PRIMARY KEY (tipo_operacion, categoria, codigo);


--
-- Name: plantilla_vida_util plantilla_vida_util_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plantilla_vida_util
    ADD CONSTRAINT plantilla_vida_util_pkey PRIMARY KEY (categoria);


--
-- Name: regla_contabilizacion regla_contabilizacion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.regla_contabilizacion
    ADD CONSTRAINT regla_contabilizacion_pkey PRIMARY KEY (id);


--
-- Name: tasa_impuesto tasa_impuesto_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tasa_impuesto
    ADD CONSTRAINT tasa_impuesto_pkey PRIMARY KEY (id);


--
-- Name: activo_fijo uq_activo_fijo_empresa_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activo_fijo
    ADD CONSTRAINT uq_activo_fijo_empresa_id UNIQUE (empresa_id, id);


--
-- Name: CONSTRAINT uq_activo_fijo_empresa_id ON activo_fijo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT uq_activo_fijo_empresa_id ON public.activo_fijo IS 'Objetivo de la llave foránea compuesta de depreciacion_registrada.activo_id (ADR-035).';


--
-- Name: asiento uq_asiento_empresa_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asiento
    ADD CONSTRAINT uq_asiento_empresa_id UNIQUE (empresa_id, id);


--
-- Name: CONSTRAINT uq_asiento_empresa_id ON asiento; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT uq_asiento_empresa_id ON public.asiento IS 'Objetivo de las llaves foráneas compuestas (empresa_id, asiento_id): impide referenciar asientos de otra empresa (ADR-036).';


--
-- Name: asiento_linea uq_asiento_linea_asiento_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asiento_linea
    ADD CONSTRAINT uq_asiento_linea_asiento_id UNIQUE (asiento_id, id);


--
-- Name: CONSTRAINT uq_asiento_linea_asiento_id ON asiento_linea; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT uq_asiento_linea_asiento_id ON public.asiento_linea IS 'Objetivo de fk_asiento_linea_base: la línea base pertenece al mismo asiento.';


--
-- Name: asiento_linea uq_asiento_linea_numero; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asiento_linea
    ADD CONSTRAINT uq_asiento_linea_numero UNIQUE (asiento_id, numero_linea);


--
-- Name: asiento uq_asiento_numero; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asiento
    ADD CONSTRAINT uq_asiento_numero UNIQUE (empresa_id, anio, numero);


--
-- Name: CONSTRAINT uq_asiento_numero ON asiento; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT uq_asiento_numero ON public.asiento IS 'Sin números duplicados por empresa y año.';


--
-- Name: cuenta_contable uq_cuenta_contable_codigo; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cuenta_contable
    ADD CONSTRAINT uq_cuenta_contable_codigo UNIQUE (empresa_id, codigo);


--
-- Name: cuenta_contable uq_cuenta_contable_empresa_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cuenta_contable
    ADD CONSTRAINT uq_cuenta_contable_empresa_id UNIQUE (empresa_id, id);


--
-- Name: CONSTRAINT uq_cuenta_contable_empresa_id ON cuenta_contable; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT uq_cuenta_contable_empresa_id ON public.cuenta_contable IS 'Objetivo de las llaves foráneas compuestas (empresa_id, cuenta_id): impide referenciar cuentas de otra empresa (ADR-035).';


--
-- Name: operacion uq_operacion_empresa_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operacion
    ADD CONSTRAINT uq_operacion_empresa_id UNIQUE (empresa_id, id);


--
-- Name: CONSTRAINT uq_operacion_empresa_id ON operacion; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT uq_operacion_empresa_id ON public.operacion IS 'Objetivo de la llave foránea compuesta de activo_fijo.operacion_id (V18): impide referenciar una operación de otra empresa (ADR-035).';


--
-- Name: regla_contabilizacion uq_regla_contabilizacion; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.regla_contabilizacion
    ADD CONSTRAINT uq_regla_contabilizacion UNIQUE (empresa_id, tipo_operacion, categoria, codigo);


--
-- Name: usuario usuario_correo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT usuario_correo_key UNIQUE (correo);


--
-- Name: usuario usuario_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT usuario_pkey PRIMARY KEY (id);


--
-- Name: usuario usuario_sub_keycloak_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT usuario_sub_keycloak_key UNIQUE (sub_keycloak);


--
-- Name: idx_auditoria_entidad; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auditoria_entidad ON ONLY public.auditoria USING btree (empresa_id, entidad, entidad_id, creado_en);


--
-- Name: INDEX idx_auditoria_entidad; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON INDEX public.idx_auditoria_entidad IS 'Historial de una entidad de una empresa en orden cronológico.';


--
-- Name: auditoria_2026_09_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX auditoria_2026_09_empresa_id_entidad_entidad_id_creado_en_idx ON public.auditoria_2026_09 USING btree (empresa_id, entidad, entidad_id, creado_en);


--
-- Name: auditoria_2026_10_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX auditoria_2026_10_empresa_id_entidad_entidad_id_creado_en_idx ON public.auditoria_2026_10 USING btree (empresa_id, entidad, entidad_id, creado_en);


--
-- Name: auditoria_2026_11_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX auditoria_2026_11_empresa_id_entidad_entidad_id_creado_en_idx ON public.auditoria_2026_11 USING btree (empresa_id, entidad, entidad_id, creado_en);


--
-- Name: auditoria_2026_12_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX auditoria_2026_12_empresa_id_entidad_entidad_id_creado_en_idx ON public.auditoria_2026_12 USING btree (empresa_id, entidad, entidad_id, creado_en);


--
-- Name: auditoria_2027_01_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX auditoria_2027_01_empresa_id_entidad_entidad_id_creado_en_idx ON public.auditoria_2027_01 USING btree (empresa_id, entidad, entidad_id, creado_en);


--
-- Name: auditoria_2027_02_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX auditoria_2027_02_empresa_id_entidad_entidad_id_creado_en_idx ON public.auditoria_2027_02 USING btree (empresa_id, entidad, entidad_id, creado_en);


--
-- Name: auditoria_2027_03_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX auditoria_2027_03_empresa_id_entidad_entidad_id_creado_en_idx ON public.auditoria_2027_03 USING btree (empresa_id, entidad, entidad_id, creado_en);


--
-- Name: auditoria_2027_04_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX auditoria_2027_04_empresa_id_entidad_entidad_id_creado_en_idx ON public.auditoria_2027_04 USING btree (empresa_id, entidad, entidad_id, creado_en);


--
-- Name: auditoria_2027_05_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX auditoria_2027_05_empresa_id_entidad_entidad_id_creado_en_idx ON public.auditoria_2027_05 USING btree (empresa_id, entidad, entidad_id, creado_en);


--
-- Name: auditoria_2027_06_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX auditoria_2027_06_empresa_id_entidad_entidad_id_creado_en_idx ON public.auditoria_2027_06 USING btree (empresa_id, entidad, entidad_id, creado_en);


--
-- Name: auditoria_2027_07_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX auditoria_2027_07_empresa_id_entidad_entidad_id_creado_en_idx ON public.auditoria_2027_07 USING btree (empresa_id, entidad, entidad_id, creado_en);


--
-- Name: auditoria_2027_08_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX auditoria_2027_08_empresa_id_entidad_entidad_id_creado_en_idx ON public.auditoria_2027_08 USING btree (empresa_id, entidad, entidad_id, creado_en);


--
-- Name: auditoria_2027_09_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX auditoria_2027_09_empresa_id_entidad_entidad_id_creado_en_idx ON public.auditoria_2027_09 USING btree (empresa_id, entidad, entidad_id, creado_en);


--
-- Name: auditoria_2027_10_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX auditoria_2027_10_empresa_id_entidad_entidad_id_creado_en_idx ON public.auditoria_2027_10 USING btree (empresa_id, entidad, entidad_id, creado_en);


--
-- Name: auditoria_2027_11_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX auditoria_2027_11_empresa_id_entidad_entidad_id_creado_en_idx ON public.auditoria_2027_11 USING btree (empresa_id, entidad, entidad_id, creado_en);


--
-- Name: auditoria_2027_12_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX auditoria_2027_12_empresa_id_entidad_entidad_id_creado_en_idx ON public.auditoria_2027_12 USING btree (empresa_id, entidad, entidad_id, creado_en);


--
-- Name: auditoria_default_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX auditoria_default_empresa_id_entidad_entidad_id_creado_en_idx ON public.auditoria_default USING btree (empresa_id, entidad, entidad_id, creado_en);


--
-- Name: idx_asiento_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_asiento_fecha ON public.asiento USING btree (empresa_id, fecha);


--
-- Name: INDEX idx_asiento_fecha; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON INDEX public.idx_asiento_fecha IS 'Consulta del Libro Diario y de los reportes por rango de fechas (CLAUDE.md §10.5).';


--
-- Name: idx_auditoria_global_entidad; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auditoria_global_entidad ON public.auditoria_global USING btree (entidad, entidad_id, creado_en);


--
-- Name: INDEX idx_auditoria_global_entidad; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON INDEX public.idx_auditoria_global_entidad IS 'Historial de una entidad global en orden cronológico.';


--
-- Name: idx_cuenta_contable_padre; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cuenta_contable_padre ON public.cuenta_contable USING btree (empresa_id, cuenta_padre_id);


--
-- Name: INDEX idx_cuenta_contable_padre; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON INDEX public.idx_cuenta_contable_padre IS 'Búsqueda de las hijas de una cuenta (árbol del catálogo y validación de padre).';


--
-- Name: idx_empresa_usuario_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_empresa_usuario_usuario ON public.empresa_usuario USING btree (usuario_id);


--
-- Name: INDEX idx_empresa_usuario_usuario; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON INDEX public.idx_empresa_usuario_usuario IS 'Búsqueda de las membresías de un usuario (selector de empresa, GET /me).';


--
-- Name: idx_idempotencia_creado_en; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_idempotencia_creado_en ON public.idempotencia USING btree (creado_en);


--
-- Name: INDEX idx_idempotencia_creado_en; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON INDEX public.idx_idempotencia_creado_en IS 'Acelera la purga de claves con más de 7 días de antigüedad.';


--
-- Name: idx_linea_mayor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_linea_mayor ON public.asiento_linea USING btree (empresa_id, cuenta_id, fecha);


--
-- Name: INDEX idx_linea_mayor; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON INDEX public.idx_linea_mayor IS 'Movimientos de una cuenta en un rango de fechas: Libro Mayor, saldo del mes parcial y balanza.';


--
-- Name: idx_operacion_listado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_operacion_listado ON public.operacion USING btree (empresa_id, fecha DESC, id DESC);


--
-- Name: INDEX idx_operacion_listado; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON INDEX public.idx_operacion_listado IS 'Consulta paginada de operaciones por empresa, ordenada por fecha descendente (GET /contabilidad/operaciones).';


--
-- Name: idx_regla_contabilizacion_cuenta; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_regla_contabilizacion_cuenta ON public.regla_contabilizacion USING btree (empresa_id, cuenta_id);


--
-- Name: INDEX idx_regla_contabilizacion_cuenta; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON INDEX public.idx_regla_contabilizacion_cuenta IS 'Detecta las reglas que usan una cuenta (CON-016).';


--
-- Name: uq_asiento_operacion_vigente; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_asiento_operacion_vigente ON public.asiento USING btree (empresa_id, origen_id) WHERE (((origen_tipo)::text = 'N8N'::text) AND ((estado)::text = 'CONTABILIZADO'::text));


--
-- Name: INDEX uq_asiento_operacion_vigente; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON INDEX public.uq_asiento_operacion_vigente IS 'Última defensa contra dos asientos vigentes de la misma operación de n8n; un asiento REVERTIDO deja libre la operación para reenviarla corregida.';


--
-- Name: uq_asiento_revertido_una_vez; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_asiento_revertido_una_vez ON public.asiento USING btree (empresa_id, asiento_revertido_id) WHERE (asiento_revertido_id IS NOT NULL);


--
-- Name: INDEX uq_asiento_revertido_una_vez; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON INDEX public.uq_asiento_revertido_una_vez IS 'Un asiento no puede tener dos reversiones (CON-008), aunque dos peticiones compitan.';


--
-- Name: uq_depreciacion_vigente; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_depreciacion_vigente ON public.depreciacion_registrada USING btree (empresa_id, activo_id, anio, mes) WHERE ((estado)::text = 'VIGENTE'::text);


--
-- Name: INDEX uq_depreciacion_vigente; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON INDEX public.uq_depreciacion_vigente IS 'Última defensa contra dos depreciaciones vigentes del mismo activo y mes, aunque dos peticiones compitan (spec F4.5, foco de revisión 2).';


--
-- Name: uq_empresa_personal; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_empresa_personal ON public.empresa USING btree (propietario_id) WHERE ((tipo)::text = 'PERSONAL'::text);


--
-- Name: INDEX uq_empresa_personal; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON INDEX public.uq_empresa_personal IS 'Garantiza una sola empresa PERSONAL por propietario (ADR-029).';


--
-- Name: auditoria_2026_09_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_auditoria_entidad ATTACH PARTITION public.auditoria_2026_09_empresa_id_entidad_entidad_id_creado_en_idx;


--
-- Name: auditoria_2026_09_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.auditoria_pkey ATTACH PARTITION public.auditoria_2026_09_pkey;


--
-- Name: auditoria_2026_10_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_auditoria_entidad ATTACH PARTITION public.auditoria_2026_10_empresa_id_entidad_entidad_id_creado_en_idx;


--
-- Name: auditoria_2026_10_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.auditoria_pkey ATTACH PARTITION public.auditoria_2026_10_pkey;


--
-- Name: auditoria_2026_11_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_auditoria_entidad ATTACH PARTITION public.auditoria_2026_11_empresa_id_entidad_entidad_id_creado_en_idx;


--
-- Name: auditoria_2026_11_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.auditoria_pkey ATTACH PARTITION public.auditoria_2026_11_pkey;


--
-- Name: auditoria_2026_12_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_auditoria_entidad ATTACH PARTITION public.auditoria_2026_12_empresa_id_entidad_entidad_id_creado_en_idx;


--
-- Name: auditoria_2026_12_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.auditoria_pkey ATTACH PARTITION public.auditoria_2026_12_pkey;


--
-- Name: auditoria_2027_01_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_auditoria_entidad ATTACH PARTITION public.auditoria_2027_01_empresa_id_entidad_entidad_id_creado_en_idx;


--
-- Name: auditoria_2027_01_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.auditoria_pkey ATTACH PARTITION public.auditoria_2027_01_pkey;


--
-- Name: auditoria_2027_02_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_auditoria_entidad ATTACH PARTITION public.auditoria_2027_02_empresa_id_entidad_entidad_id_creado_en_idx;


--
-- Name: auditoria_2027_02_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.auditoria_pkey ATTACH PARTITION public.auditoria_2027_02_pkey;


--
-- Name: auditoria_2027_03_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_auditoria_entidad ATTACH PARTITION public.auditoria_2027_03_empresa_id_entidad_entidad_id_creado_en_idx;


--
-- Name: auditoria_2027_03_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.auditoria_pkey ATTACH PARTITION public.auditoria_2027_03_pkey;


--
-- Name: auditoria_2027_04_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_auditoria_entidad ATTACH PARTITION public.auditoria_2027_04_empresa_id_entidad_entidad_id_creado_en_idx;


--
-- Name: auditoria_2027_04_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.auditoria_pkey ATTACH PARTITION public.auditoria_2027_04_pkey;


--
-- Name: auditoria_2027_05_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_auditoria_entidad ATTACH PARTITION public.auditoria_2027_05_empresa_id_entidad_entidad_id_creado_en_idx;


--
-- Name: auditoria_2027_05_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.auditoria_pkey ATTACH PARTITION public.auditoria_2027_05_pkey;


--
-- Name: auditoria_2027_06_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_auditoria_entidad ATTACH PARTITION public.auditoria_2027_06_empresa_id_entidad_entidad_id_creado_en_idx;


--
-- Name: auditoria_2027_06_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.auditoria_pkey ATTACH PARTITION public.auditoria_2027_06_pkey;


--
-- Name: auditoria_2027_07_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_auditoria_entidad ATTACH PARTITION public.auditoria_2027_07_empresa_id_entidad_entidad_id_creado_en_idx;


--
-- Name: auditoria_2027_07_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.auditoria_pkey ATTACH PARTITION public.auditoria_2027_07_pkey;


--
-- Name: auditoria_2027_08_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_auditoria_entidad ATTACH PARTITION public.auditoria_2027_08_empresa_id_entidad_entidad_id_creado_en_idx;


--
-- Name: auditoria_2027_08_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.auditoria_pkey ATTACH PARTITION public.auditoria_2027_08_pkey;


--
-- Name: auditoria_2027_09_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_auditoria_entidad ATTACH PARTITION public.auditoria_2027_09_empresa_id_entidad_entidad_id_creado_en_idx;


--
-- Name: auditoria_2027_09_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.auditoria_pkey ATTACH PARTITION public.auditoria_2027_09_pkey;


--
-- Name: auditoria_2027_10_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_auditoria_entidad ATTACH PARTITION public.auditoria_2027_10_empresa_id_entidad_entidad_id_creado_en_idx;


--
-- Name: auditoria_2027_10_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.auditoria_pkey ATTACH PARTITION public.auditoria_2027_10_pkey;


--
-- Name: auditoria_2027_11_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_auditoria_entidad ATTACH PARTITION public.auditoria_2027_11_empresa_id_entidad_entidad_id_creado_en_idx;


--
-- Name: auditoria_2027_11_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.auditoria_pkey ATTACH PARTITION public.auditoria_2027_11_pkey;


--
-- Name: auditoria_2027_12_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_auditoria_entidad ATTACH PARTITION public.auditoria_2027_12_empresa_id_entidad_entidad_id_creado_en_idx;


--
-- Name: auditoria_2027_12_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.auditoria_pkey ATTACH PARTITION public.auditoria_2027_12_pkey;


--
-- Name: auditoria_default_empresa_id_entidad_entidad_id_creado_en_idx; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.idx_auditoria_entidad ATTACH PARTITION public.auditoria_default_empresa_id_entidad_entidad_id_creado_en_idx;


--
-- Name: auditoria_default_pkey; Type: INDEX ATTACH; Schema: public; Owner: -
--

ALTER INDEX public.auditoria_pkey ATTACH PARTITION public.auditoria_default_pkey;


--
-- Name: asiento trg_asiento_transicion; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_asiento_transicion BEFORE UPDATE ON public.asiento FOR EACH ROW EXECUTE FUNCTION public.validar_transicion_asiento();


--
-- Name: TRIGGER trg_asiento_transicion ON asiento; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TRIGGER trg_asiento_transicion ON public.asiento IS 'Rechaza toda actualización que no sea el paso a REVERTIDO (ADR-019).';


--
-- Name: asiento_linea trg_partida_doble; Type: TRIGGER; Schema: public; Owner: -
--

CREATE CONSTRAINT TRIGGER trg_partida_doble AFTER INSERT OR DELETE OR UPDATE ON public.asiento_linea DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.validar_partida_doble();


--
-- Name: TRIGGER trg_partida_doble ON asiento_linea; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TRIGGER trg_partida_doble ON public.asiento_linea IS 'Valida el asiento de cada línea insertada, modificada o borrada al COMMIT (CLAUDE.md §9.3).';


--
-- Name: asiento trg_partida_doble_cabecera; Type: TRIGGER; Schema: public; Owner: -
--

CREATE CONSTRAINT TRIGGER trg_partida_doble_cabecera AFTER INSERT ON public.asiento DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.validar_partida_doble();


--
-- Name: TRIGGER trg_partida_doble_cabecera ON asiento; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TRIGGER trg_partida_doble_cabecera ON public.asiento IS 'Garantiza que un asiento sin líneas no pase el COMMIT (ADR-036, decisión 7).';


--
-- Name: activo_fijo activo_fijo_empresa_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activo_fijo
    ADD CONSTRAINT activo_fijo_empresa_id_fkey FOREIGN KEY (empresa_id) REFERENCES public.empresa(id);


--
-- Name: api_key api_key_empresa_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api_key
    ADD CONSTRAINT api_key_empresa_id_fkey FOREIGN KEY (empresa_id) REFERENCES public.empresa(id);


--
-- Name: asiento asiento_empresa_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asiento
    ADD CONSTRAINT asiento_empresa_id_fkey FOREIGN KEY (empresa_id) REFERENCES public.empresa(id);


--
-- Name: configuracion_contable configuracion_contable_empresa_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.configuracion_contable
    ADD CONSTRAINT configuracion_contable_empresa_id_fkey FOREIGN KEY (empresa_id) REFERENCES public.empresa(id);


--
-- Name: correlativo_asiento correlativo_asiento_empresa_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.correlativo_asiento
    ADD CONSTRAINT correlativo_asiento_empresa_id_fkey FOREIGN KEY (empresa_id) REFERENCES public.empresa(id);


--
-- Name: cuenta_contable cuenta_contable_empresa_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cuenta_contable
    ADD CONSTRAINT cuenta_contable_empresa_id_fkey FOREIGN KEY (empresa_id) REFERENCES public.empresa(id);


--
-- Name: depreciacion_registrada depreciacion_registrada_empresa_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.depreciacion_registrada
    ADD CONSTRAINT depreciacion_registrada_empresa_id_fkey FOREIGN KEY (empresa_id) REFERENCES public.empresa(id);


--
-- Name: empresa_aplicacion empresa_aplicacion_aplicacion_codigo_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.empresa_aplicacion
    ADD CONSTRAINT empresa_aplicacion_aplicacion_codigo_fkey FOREIGN KEY (aplicacion_codigo) REFERENCES public.aplicacion(codigo);


--
-- Name: empresa_aplicacion empresa_aplicacion_empresa_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.empresa_aplicacion
    ADD CONSTRAINT empresa_aplicacion_empresa_id_fkey FOREIGN KEY (empresa_id) REFERENCES public.empresa(id);


--
-- Name: empresa empresa_propietario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.empresa
    ADD CONSTRAINT empresa_propietario_id_fkey FOREIGN KEY (propietario_id) REFERENCES public.usuario(id);


--
-- Name: empresa_usuario empresa_usuario_empresa_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.empresa_usuario
    ADD CONSTRAINT empresa_usuario_empresa_id_fkey FOREIGN KEY (empresa_id) REFERENCES public.empresa(id);


--
-- Name: empresa_usuario empresa_usuario_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.empresa_usuario
    ADD CONSTRAINT empresa_usuario_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id);


--
-- Name: activo_fijo fk_activo_fijo_operacion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activo_fijo
    ADD CONSTRAINT fk_activo_fijo_operacion FOREIGN KEY (empresa_id, operacion_id) REFERENCES public.operacion(empresa_id, id);


--
-- Name: asiento_linea fk_asiento_linea_asiento; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asiento_linea
    ADD CONSTRAINT fk_asiento_linea_asiento FOREIGN KEY (empresa_id, asiento_id) REFERENCES public.asiento(empresa_id, id);


--
-- Name: asiento_linea fk_asiento_linea_base; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asiento_linea
    ADD CONSTRAINT fk_asiento_linea_base FOREIGN KEY (asiento_id, linea_base_id) REFERENCES public.asiento_linea(asiento_id, id);


--
-- Name: asiento_linea fk_asiento_linea_cuenta; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asiento_linea
    ADD CONSTRAINT fk_asiento_linea_cuenta FOREIGN KEY (empresa_id, cuenta_id) REFERENCES public.cuenta_contable(empresa_id, id);


--
-- Name: asiento fk_asiento_reversion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asiento
    ADD CONSTRAINT fk_asiento_reversion FOREIGN KEY (empresa_id, asiento_reversion_id) REFERENCES public.asiento(empresa_id, id);


--
-- Name: asiento fk_asiento_revertido; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asiento
    ADD CONSTRAINT fk_asiento_revertido FOREIGN KEY (empresa_id, asiento_revertido_id) REFERENCES public.asiento(empresa_id, id);


--
-- Name: configuracion_contable fk_configuracion_contable_iva_credito; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.configuracion_contable
    ADD CONSTRAINT fk_configuracion_contable_iva_credito FOREIGN KEY (empresa_id, cuenta_iva_credito_id) REFERENCES public.cuenta_contable(empresa_id, id);


--
-- Name: configuracion_contable fk_configuracion_contable_iva_debito; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.configuracion_contable
    ADD CONSTRAINT fk_configuracion_contable_iva_debito FOREIGN KEY (empresa_id, cuenta_iva_debito_id) REFERENCES public.cuenta_contable(empresa_id, id);


--
-- Name: cuenta_contable fk_cuenta_contable_padre; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cuenta_contable
    ADD CONSTRAINT fk_cuenta_contable_padre FOREIGN KEY (empresa_id, cuenta_padre_id) REFERENCES public.cuenta_contable(empresa_id, id);


--
-- Name: depreciacion_registrada fk_depreciacion_activo; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.depreciacion_registrada
    ADD CONSTRAINT fk_depreciacion_activo FOREIGN KEY (empresa_id, activo_id) REFERENCES public.activo_fijo(empresa_id, id);


--
-- Name: depreciacion_registrada fk_depreciacion_asiento; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.depreciacion_registrada
    ADD CONSTRAINT fk_depreciacion_asiento FOREIGN KEY (empresa_id, asiento_id) REFERENCES public.asiento(empresa_id, id);


--
-- Name: operacion fk_operacion_asiento; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operacion
    ADD CONSTRAINT fk_operacion_asiento FOREIGN KEY (empresa_id, asiento_id) REFERENCES public.asiento(empresa_id, id);


--
-- Name: regla_contabilizacion fk_regla_contabilizacion_cuenta; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.regla_contabilizacion
    ADD CONSTRAINT fk_regla_contabilizacion_cuenta FOREIGN KEY (empresa_id, cuenta_id) REFERENCES public.cuenta_contable(empresa_id, id);


--
-- Name: saldo_cuenta_mensual fk_saldo_cuenta_mensual_cuenta; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.saldo_cuenta_mensual
    ADD CONSTRAINT fk_saldo_cuenta_mensual_cuenta FOREIGN KEY (empresa_id, cuenta_id) REFERENCES public.cuenta_contable(empresa_id, id);


--
-- Name: operacion operacion_empresa_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.operacion
    ADD CONSTRAINT operacion_empresa_id_fkey FOREIGN KEY (empresa_id) REFERENCES public.empresa(id);


--
-- Name: plantilla_configuracion_contable plantilla_configuracion_contable_cuenta_iva_credito_codigo_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plantilla_configuracion_contable
    ADD CONSTRAINT plantilla_configuracion_contable_cuenta_iva_credito_codigo_fkey FOREIGN KEY (cuenta_iva_credito_codigo) REFERENCES public.plantilla_cuenta(codigo);


--
-- Name: plantilla_configuracion_contable plantilla_configuracion_contable_cuenta_iva_debito_codigo_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plantilla_configuracion_contable
    ADD CONSTRAINT plantilla_configuracion_contable_cuenta_iva_debito_codigo_fkey FOREIGN KEY (cuenta_iva_debito_codigo) REFERENCES public.plantilla_cuenta(codigo);


--
-- Name: plantilla_regla_contabilizacion plantilla_regla_contabilizacion_cuenta_codigo_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plantilla_regla_contabilizacion
    ADD CONSTRAINT plantilla_regla_contabilizacion_cuenta_codigo_fkey FOREIGN KEY (cuenta_codigo) REFERENCES public.plantilla_cuenta(codigo);


--
-- Name: regla_contabilizacion regla_contabilizacion_empresa_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.regla_contabilizacion
    ADD CONSTRAINT regla_contabilizacion_empresa_id_fkey FOREIGN KEY (empresa_id) REFERENCES public.empresa(id);


--
-- Name: activo_fijo; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.activo_fijo ENABLE ROW LEVEL SECURITY;

--
-- Name: activo_fijo aislamiento_empresa; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY aislamiento_empresa ON public.activo_fijo TO pilot_app USING ((empresa_id = (current_setting('app.empresa_id'::text))::uuid)) WITH CHECK ((empresa_id = (current_setting('app.empresa_id'::text))::uuid));


--
-- Name: POLICY aislamiento_empresa ON activo_fijo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON POLICY aislamiento_empresa ON public.activo_fijo IS 'pilot_app solo ve y escribe activos de la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';


--
-- Name: api_key aislamiento_empresa; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY aislamiento_empresa ON public.api_key TO pilot_app USING ((empresa_id = (current_setting('app.empresa_id'::text))::uuid)) WITH CHECK ((empresa_id = (current_setting('app.empresa_id'::text))::uuid));


--
-- Name: POLICY aislamiento_empresa ON api_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON POLICY aislamiento_empresa ON public.api_key IS 'pilot_app solo ve y crea claves de la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';


--
-- Name: asiento aislamiento_empresa; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY aislamiento_empresa ON public.asiento TO pilot_app USING ((empresa_id = (current_setting('app.empresa_id'::text))::uuid)) WITH CHECK ((empresa_id = (current_setting('app.empresa_id'::text))::uuid));


--
-- Name: POLICY aislamiento_empresa ON asiento; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON POLICY aislamiento_empresa ON public.asiento IS 'pilot_app solo ve y escribe asientos de la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';


--
-- Name: asiento_linea aislamiento_empresa; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY aislamiento_empresa ON public.asiento_linea TO pilot_app USING ((empresa_id = (current_setting('app.empresa_id'::text))::uuid)) WITH CHECK ((empresa_id = (current_setting('app.empresa_id'::text))::uuid));


--
-- Name: POLICY aislamiento_empresa ON asiento_linea; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON POLICY aislamiento_empresa ON public.asiento_linea IS 'pilot_app solo ve y escribe líneas de la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';


--
-- Name: auditoria aislamiento_empresa; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY aislamiento_empresa ON public.auditoria USING ((empresa_id = (current_setting('app.empresa_id'::text))::uuid)) WITH CHECK ((empresa_id = (current_setting('app.empresa_id'::text))::uuid));


--
-- Name: POLICY aislamiento_empresa ON auditoria; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON POLICY aislamiento_empresa ON public.auditoria IS 'Solo se ven y escriben filas de la empresa fijada en app.empresa_id; sin ese parámetro la consulta falla (intencional).';


--
-- Name: configuracion_contable aislamiento_empresa; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY aislamiento_empresa ON public.configuracion_contable TO pilot_app USING ((empresa_id = (current_setting('app.empresa_id'::text))::uuid)) WITH CHECK ((empresa_id = (current_setting('app.empresa_id'::text))::uuid));


--
-- Name: POLICY aislamiento_empresa ON configuracion_contable; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON POLICY aislamiento_empresa ON public.configuracion_contable IS 'pilot_app solo ve y escribe la configuración de la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';


--
-- Name: correlativo_asiento aislamiento_empresa; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY aislamiento_empresa ON public.correlativo_asiento TO pilot_app USING ((empresa_id = (current_setting('app.empresa_id'::text))::uuid)) WITH CHECK ((empresa_id = (current_setting('app.empresa_id'::text))::uuid));


--
-- Name: POLICY aislamiento_empresa ON correlativo_asiento; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON POLICY aislamiento_empresa ON public.correlativo_asiento IS 'pilot_app solo ve y escribe correlativos de la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';


--
-- Name: cuenta_contable aislamiento_empresa; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY aislamiento_empresa ON public.cuenta_contable TO pilot_app USING ((empresa_id = (current_setting('app.empresa_id'::text))::uuid)) WITH CHECK ((empresa_id = (current_setting('app.empresa_id'::text))::uuid));


--
-- Name: POLICY aislamiento_empresa ON cuenta_contable; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON POLICY aislamiento_empresa ON public.cuenta_contable IS 'pilot_app solo ve y escribe cuentas de la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';


--
-- Name: depreciacion_registrada aislamiento_empresa; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY aislamiento_empresa ON public.depreciacion_registrada TO pilot_app USING ((empresa_id = (current_setting('app.empresa_id'::text))::uuid)) WITH CHECK ((empresa_id = (current_setting('app.empresa_id'::text))::uuid));


--
-- Name: POLICY aislamiento_empresa ON depreciacion_registrada; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON POLICY aislamiento_empresa ON public.depreciacion_registrada IS 'pilot_app solo ve y escribe depreciaciones de la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';


--
-- Name: empresa aislamiento_empresa; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY aislamiento_empresa ON public.empresa TO pilot_app USING ((id = (current_setting('app.empresa_id'::text))::uuid)) WITH CHECK ((id = (current_setting('app.empresa_id'::text))::uuid));


--
-- Name: POLICY aislamiento_empresa ON empresa; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON POLICY aislamiento_empresa ON public.empresa IS 'pilot_app solo ve y escribe la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';


--
-- Name: empresa_aplicacion aislamiento_empresa; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY aislamiento_empresa ON public.empresa_aplicacion TO pilot_app USING ((empresa_id = (current_setting('app.empresa_id'::text))::uuid)) WITH CHECK ((empresa_id = (current_setting('app.empresa_id'::text))::uuid));


--
-- Name: POLICY aislamiento_empresa ON empresa_aplicacion; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON POLICY aislamiento_empresa ON public.empresa_aplicacion IS 'pilot_app solo ve e instala apps de la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';


--
-- Name: empresa_usuario aislamiento_empresa; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY aislamiento_empresa ON public.empresa_usuario TO pilot_app USING ((empresa_id = (current_setting('app.empresa_id'::text))::uuid)) WITH CHECK ((empresa_id = (current_setting('app.empresa_id'::text))::uuid));


--
-- Name: POLICY aislamiento_empresa ON empresa_usuario; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON POLICY aislamiento_empresa ON public.empresa_usuario IS 'pilot_app solo ve y escribe membresías de la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';


--
-- Name: idempotencia aislamiento_empresa; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY aislamiento_empresa ON public.idempotencia USING ((empresa_id = (current_setting('app.empresa_id'::text))::uuid)) WITH CHECK ((empresa_id = (current_setting('app.empresa_id'::text))::uuid));


--
-- Name: POLICY aislamiento_empresa ON idempotencia; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON POLICY aislamiento_empresa ON public.idempotencia IS 'Solo se ven y escriben filas de la empresa fijada en app.empresa_id; sin ese parámetro la consulta falla (intencional).';


--
-- Name: operacion aislamiento_empresa; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY aislamiento_empresa ON public.operacion TO pilot_app USING ((empresa_id = (current_setting('app.empresa_id'::text))::uuid)) WITH CHECK ((empresa_id = (current_setting('app.empresa_id'::text))::uuid));


--
-- Name: POLICY aislamiento_empresa ON operacion; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON POLICY aislamiento_empresa ON public.operacion IS 'pilot_app solo ve y escribe operaciones de la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';


--
-- Name: regla_contabilizacion aislamiento_empresa; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY aislamiento_empresa ON public.regla_contabilizacion TO pilot_app USING ((empresa_id = (current_setting('app.empresa_id'::text))::uuid)) WITH CHECK ((empresa_id = (current_setting('app.empresa_id'::text))::uuid));


--
-- Name: POLICY aislamiento_empresa ON regla_contabilizacion; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON POLICY aislamiento_empresa ON public.regla_contabilizacion IS 'pilot_app solo ve y escribe reglas de la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';


--
-- Name: saldo_cuenta_mensual aislamiento_empresa; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY aislamiento_empresa ON public.saldo_cuenta_mensual TO pilot_app USING ((empresa_id = (current_setting('app.empresa_id'::text))::uuid)) WITH CHECK ((empresa_id = (current_setting('app.empresa_id'::text))::uuid));


--
-- Name: POLICY aislamiento_empresa ON saldo_cuenta_mensual; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON POLICY aislamiento_empresa ON public.saldo_cuenta_mensual IS 'pilot_app solo ve y escribe saldos de la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';


--
-- Name: api_key; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.api_key ENABLE ROW LEVEL SECURITY;

--
-- Name: asiento; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.asiento ENABLE ROW LEVEL SECURITY;

--
-- Name: asiento_linea; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.asiento_linea ENABLE ROW LEVEL SECURITY;

--
-- Name: auditoria; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.auditoria ENABLE ROW LEVEL SECURITY;

--
-- Name: api_key busqueda_sin_empresa; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY busqueda_sin_empresa ON public.api_key FOR SELECT TO pilot_busqueda USING (true);


--
-- Name: POLICY busqueda_sin_empresa ON api_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON POLICY busqueda_sin_empresa ON public.api_key IS 'Lectura sin filtro para pilot_busqueda; solo la usan las funciones SECURITY DEFINER (ADR-026).';


--
-- Name: empresa busqueda_sin_empresa; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY busqueda_sin_empresa ON public.empresa FOR SELECT TO pilot_busqueda USING (true);


--
-- Name: POLICY busqueda_sin_empresa ON empresa; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON POLICY busqueda_sin_empresa ON public.empresa IS 'Lectura sin filtro para pilot_busqueda; solo la usan las funciones SECURITY DEFINER (ADR-026).';


--
-- Name: empresa_usuario busqueda_sin_empresa; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY busqueda_sin_empresa ON public.empresa_usuario FOR SELECT TO pilot_busqueda USING (true);


--
-- Name: POLICY busqueda_sin_empresa ON empresa_usuario; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON POLICY busqueda_sin_empresa ON public.empresa_usuario IS 'Lectura sin filtro para pilot_busqueda; solo la usan las funciones SECURITY DEFINER (ADR-026).';


--
-- Name: configuracion_contable; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.configuracion_contable ENABLE ROW LEVEL SECURITY;

--
-- Name: correlativo_asiento; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.correlativo_asiento ENABLE ROW LEVEL SECURITY;

--
-- Name: cuenta_contable; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.cuenta_contable ENABLE ROW LEVEL SECURITY;

--
-- Name: depreciacion_registrada; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.depreciacion_registrada ENABLE ROW LEVEL SECURITY;

--
-- Name: empresa; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.empresa ENABLE ROW LEVEL SECURITY;

--
-- Name: empresa_aplicacion; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.empresa_aplicacion ENABLE ROW LEVEL SECURITY;

--
-- Name: empresa_usuario; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.empresa_usuario ENABLE ROW LEVEL SECURITY;

--
-- Name: idempotencia; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.idempotencia ENABLE ROW LEVEL SECURITY;

--
-- Name: operacion; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.operacion ENABLE ROW LEVEL SECURITY;

--
-- Name: regla_contabilizacion; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.regla_contabilizacion ENABLE ROW LEVEL SECURITY;

--
-- Name: saldo_cuenta_mensual; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.saldo_cuenta_mensual ENABLE ROW LEVEL SECURITY;

--
-- Name: SCHEMA public; Type: ACL; Schema: -; Owner: -
--

GRANT USAGE ON SCHEMA public TO pilot_app;
GRANT USAGE ON SCHEMA public TO pilot_busqueda;


--
-- Name: FUNCTION api_key_por_prefijo(p_prefijo character varying); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.api_key_por_prefijo(p_prefijo character varying) FROM PUBLIC;
GRANT ALL ON FUNCTION public.api_key_por_prefijo(p_prefijo character varying) TO pilot_app;


--
-- Name: FUNCTION membresia_activa(p_usuario_id uuid, p_empresa_id uuid); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.membresia_activa(p_usuario_id uuid, p_empresa_id uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION public.membresia_activa(p_usuario_id uuid, p_empresa_id uuid) TO pilot_app;


--
-- Name: FUNCTION membresias_de_usuario(p_usuario_id uuid); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.membresias_de_usuario(p_usuario_id uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION public.membresias_de_usuario(p_usuario_id uuid) TO pilot_app;


--
-- Name: TABLE activo_fijo; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT ON TABLE public.activo_fijo TO pilot_app;


--
-- Name: COLUMN activo_fijo.estado; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(estado) ON TABLE public.activo_fijo TO pilot_app;


--
-- Name: COLUMN activo_fijo.version; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(version) ON TABLE public.activo_fijo TO pilot_app;


--
-- Name: TABLE api_key; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT ON TABLE public.api_key TO pilot_app;
GRANT SELECT ON TABLE public.api_key TO pilot_busqueda;


--
-- Name: COLUMN api_key.revocada_en; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(revocada_en) ON TABLE public.api_key TO pilot_app;


--
-- Name: COLUMN api_key.ultimo_uso_en; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(ultimo_uso_en) ON TABLE public.api_key TO pilot_app;


--
-- Name: TABLE aplicacion; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.aplicacion TO pilot_app;


--
-- Name: TABLE asiento; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT ON TABLE public.asiento TO pilot_app;


--
-- Name: COLUMN asiento.estado; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(estado) ON TABLE public.asiento TO pilot_app;


--
-- Name: COLUMN asiento.asiento_reversion_id; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(asiento_reversion_id) ON TABLE public.asiento TO pilot_app;


--
-- Name: COLUMN asiento.version; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(version) ON TABLE public.asiento TO pilot_app;


--
-- Name: TABLE asiento_linea; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT ON TABLE public.asiento_linea TO pilot_app;


--
-- Name: TABLE auditoria; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT ON TABLE public.auditoria TO pilot_app;


--
-- Name: TABLE auditoria_global; Type: ACL; Schema: public; Owner: -
--

GRANT INSERT ON TABLE public.auditoria_global TO pilot_app;


--
-- Name: TABLE configuracion_contable; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT ON TABLE public.configuracion_contable TO pilot_app;


--
-- Name: COLUMN configuracion_contable.modo_precio_defecto; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(modo_precio_defecto) ON TABLE public.configuracion_contable TO pilot_app;


--
-- Name: COLUMN configuracion_contable.cuenta_iva_debito_id; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(cuenta_iva_debito_id) ON TABLE public.configuracion_contable TO pilot_app;


--
-- Name: COLUMN configuracion_contable.cuenta_iva_credito_id; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(cuenta_iva_credito_id) ON TABLE public.configuracion_contable TO pilot_app;


--
-- Name: COLUMN configuracion_contable.actualizado_en; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(actualizado_en) ON TABLE public.configuracion_contable TO pilot_app;


--
-- Name: COLUMN configuracion_contable.actualizado_por; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(actualizado_por) ON TABLE public.configuracion_contable TO pilot_app;


--
-- Name: COLUMN configuracion_contable.version; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(version) ON TABLE public.configuracion_contable TO pilot_app;


--
-- Name: TABLE correlativo_asiento; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT ON TABLE public.correlativo_asiento TO pilot_app;


--
-- Name: COLUMN correlativo_asiento.ultimo; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(ultimo) ON TABLE public.correlativo_asiento TO pilot_app;


--
-- Name: TABLE cuenta_contable; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT ON TABLE public.cuenta_contable TO pilot_app;


--
-- Name: COLUMN cuenta_contable.codigo; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(codigo) ON TABLE public.cuenta_contable TO pilot_app;


--
-- Name: COLUMN cuenta_contable.nombre; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(nombre) ON TABLE public.cuenta_contable TO pilot_app;


--
-- Name: COLUMN cuenta_contable.nivel; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(nivel) ON TABLE public.cuenta_contable TO pilot_app;


--
-- Name: COLUMN cuenta_contable.cuenta_padre_id; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(cuenta_padre_id) ON TABLE public.cuenta_contable TO pilot_app;


--
-- Name: COLUMN cuenta_contable.naturaleza; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(naturaleza) ON TABLE public.cuenta_contable TO pilot_app;


--
-- Name: COLUMN cuenta_contable.acepta_movimientos; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(acepta_movimientos) ON TABLE public.cuenta_contable TO pilot_app;


--
-- Name: COLUMN cuenta_contable.activa; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(activa) ON TABLE public.cuenta_contable TO pilot_app;


--
-- Name: COLUMN cuenta_contable.actualizado_en; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(actualizado_en) ON TABLE public.cuenta_contable TO pilot_app;


--
-- Name: COLUMN cuenta_contable.actualizado_por; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(actualizado_por) ON TABLE public.cuenta_contable TO pilot_app;


--
-- Name: COLUMN cuenta_contable.version; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(version) ON TABLE public.cuenta_contable TO pilot_app;


--
-- Name: TABLE depreciacion_registrada; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT ON TABLE public.depreciacion_registrada TO pilot_app;


--
-- Name: COLUMN depreciacion_registrada.estado; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(estado) ON TABLE public.depreciacion_registrada TO pilot_app;


--
-- Name: COLUMN depreciacion_registrada.version; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(version) ON TABLE public.depreciacion_registrada TO pilot_app;


--
-- Name: TABLE empresa; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT ON TABLE public.empresa TO pilot_app;
GRANT SELECT ON TABLE public.empresa TO pilot_busqueda;


--
-- Name: COLUMN empresa.nit; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(nit) ON TABLE public.empresa TO pilot_app;


--
-- Name: COLUMN empresa.nrc; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(nrc) ON TABLE public.empresa TO pilot_app;


--
-- Name: COLUMN empresa.nombre; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(nombre) ON TABLE public.empresa TO pilot_app;


--
-- Name: COLUMN empresa.nombre_comercial; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(nombre_comercial) ON TABLE public.empresa TO pilot_app;


--
-- Name: COLUMN empresa.actualizado_en; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(actualizado_en) ON TABLE public.empresa TO pilot_app;


--
-- Name: COLUMN empresa.actualizado_por; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(actualizado_por) ON TABLE public.empresa TO pilot_app;


--
-- Name: COLUMN empresa.version; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(version) ON TABLE public.empresa TO pilot_app;


--
-- Name: TABLE empresa_aplicacion; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT ON TABLE public.empresa_aplicacion TO pilot_app;


--
-- Name: TABLE empresa_usuario; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT ON TABLE public.empresa_usuario TO pilot_app;
GRANT SELECT ON TABLE public.empresa_usuario TO pilot_busqueda;


--
-- Name: COLUMN empresa_usuario.rol; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(rol) ON TABLE public.empresa_usuario TO pilot_app;


--
-- Name: COLUMN empresa_usuario.estado; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(estado) ON TABLE public.empresa_usuario TO pilot_app;


--
-- Name: COLUMN empresa_usuario.actualizado_en; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(actualizado_en) ON TABLE public.empresa_usuario TO pilot_app;


--
-- Name: COLUMN empresa_usuario.actualizado_por; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(actualizado_por) ON TABLE public.empresa_usuario TO pilot_app;


--
-- Name: TABLE idempotencia; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,DELETE ON TABLE public.idempotencia TO pilot_app;


--
-- Name: TABLE operacion; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT ON TABLE public.operacion TO pilot_app;


--
-- Name: COLUMN operacion.estado; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(estado) ON TABLE public.operacion TO pilot_app;


--
-- Name: COLUMN operacion.version; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(version) ON TABLE public.operacion TO pilot_app;


--
-- Name: TABLE plantilla_configuracion_contable; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.plantilla_configuracion_contable TO pilot_app;


--
-- Name: TABLE plantilla_cuenta; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.plantilla_cuenta TO pilot_app;


--
-- Name: TABLE plantilla_regla_contabilizacion; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.plantilla_regla_contabilizacion TO pilot_app;


--
-- Name: TABLE plantilla_vida_util; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.plantilla_vida_util TO pilot_app;


--
-- Name: TABLE regla_contabilizacion; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT ON TABLE public.regla_contabilizacion TO pilot_app;


--
-- Name: COLUMN regla_contabilizacion.cuenta_id; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(cuenta_id) ON TABLE public.regla_contabilizacion TO pilot_app;


--
-- Name: COLUMN regla_contabilizacion.activa; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(activa) ON TABLE public.regla_contabilizacion TO pilot_app;


--
-- Name: COLUMN regla_contabilizacion.actualizado_en; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(actualizado_en) ON TABLE public.regla_contabilizacion TO pilot_app;


--
-- Name: COLUMN regla_contabilizacion.actualizado_por; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(actualizado_por) ON TABLE public.regla_contabilizacion TO pilot_app;


--
-- Name: COLUMN regla_contabilizacion.version; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(version) ON TABLE public.regla_contabilizacion TO pilot_app;


--
-- Name: TABLE saldo_cuenta_mensual; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT ON TABLE public.saldo_cuenta_mensual TO pilot_app;


--
-- Name: COLUMN saldo_cuenta_mensual.total_debe; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(total_debe) ON TABLE public.saldo_cuenta_mensual TO pilot_app;


--
-- Name: COLUMN saldo_cuenta_mensual.total_haber; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(total_haber) ON TABLE public.saldo_cuenta_mensual TO pilot_app;


--
-- Name: COLUMN saldo_cuenta_mensual.actualizado_en; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(actualizado_en) ON TABLE public.saldo_cuenta_mensual TO pilot_app;


--
-- Name: TABLE tasa_impuesto; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.tasa_impuesto TO pilot_app;


--
-- Name: TABLE usuario; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT ON TABLE public.usuario TO pilot_app;


--
-- Name: COLUMN usuario.correo; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(correo) ON TABLE public.usuario TO pilot_app;


--
-- Name: COLUMN usuario.nombre; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(nombre) ON TABLE public.usuario TO pilot_app;


--
-- Name: COLUMN usuario.telefono; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(telefono) ON TABLE public.usuario TO pilot_app;


--
-- Name: COLUMN usuario.recomendaciones_aceptadas_en; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(recomendaciones_aceptadas_en) ON TABLE public.usuario TO pilot_app;


--
-- Name: COLUMN usuario.recomendaciones_retiradas_en; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(recomendaciones_retiradas_en) ON TABLE public.usuario TO pilot_app;


--
-- Name: COLUMN usuario.actualizado_en; Type: ACL; Schema: public; Owner: -
--

GRANT UPDATE(actualizado_en) ON TABLE public.usuario TO pilot_app;


--
-- PostgreSQL database dump complete
--

\unrestrict HfOEsRHFy8VAJ98oEmFFVCXC9soEPlOz8JnPBpINFjcHOU9JgZ5EqcUn5YTpDkM

