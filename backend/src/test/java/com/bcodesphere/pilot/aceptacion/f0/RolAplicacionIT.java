package com.bcodesphere.pilot.aceptacion.f0;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcodesphere.pilot.plataforma.BasePlataformaIT;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Aceptación F0, tarea 4 del plan ("usuarios pilot_owner y pilot_app (sin BYPASSRLS)") y regla 1.2.11 de CLAUDE.md:
 * la aplicación nunca se conecta con un usuario dueño, superusuario ni con BYPASSRLS. Sin ello, RLS no protegería nada.
 */
class RolAplicacionIT extends BasePlataformaIT {

    @Autowired
    private JdbcClient jdbc;

    /** Criterio F0 (rol de la aplicación): current_user no es superusuario ni tiene BYPASSRLS. */
    @Test
    void laConexionDeLaAplicacionNoEsSuperusuarioNiTieneBypassRls() {
        // 1. Atributos del rol con el que la aplicación está conectada realmente
        Map<String, Object> rol = jdbc.sql("SELECT rolname, rolsuper, rolbypassrls, rolcreaterole, rolcreatedb"
                        + " FROM pg_roles WHERE rolname = current_user")
                .query()
                .singleRow();

        // 2. Sin privilegios que se salten RLS o permitan administrar la base
        assertThat(rol.get("rolname")).isEqualTo("pilot_app");
        assertThat(rol.get("rolsuper")).isEqualTo(false);
        assertThat(rol.get("rolbypassrls")).isEqualTo(false);
        assertThat(rol.get("rolcreaterole")).isEqualTo(false);
        assertThat(rol.get("rolcreatedb")).isEqualTo(false);
    }

    /** Criterio F0 (rol de la aplicación): pilot_app no es dueña de las tablas, así que FORCE RLS le aplica. */
    @Test
    void laAplicacionNoEsDuenaDeLasTablasDeNegocio() {
        String duenio = jdbc.sql(
                        "SELECT tableowner FROM pg_tables WHERE schemaname = 'public' AND tablename = 'idempotencia'")
                .query(String.class)
                .single();

        assertThat(duenio).isEqualTo("pilot_owner");
    }
}
