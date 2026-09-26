package com.bcodesphere.pilot.contabilidad.aplicacion;

import com.bcodesphere.pilot.contabilidad.dominio.configuracion.ConfiguracionContable;
import com.bcodesphere.pilot.contabilidad.dominio.configuracion.ModoPrecio;
import java.util.Optional;
import java.util.UUID;

/** Puerto de persistencia de la configuración contable de la empresa activa (una fila). */
public interface RepositorioConfiguracionContable {

    /**
     * Lee la configuración con el resumen de sus dos cuentas de IVA.
     *
     * @return la configuración, o vacío si la empresa no la tiene (Contabilidad no instalada)
     */
    Optional<ConfiguracionContable> obtener();

    /**
     * Actualiza la configuración con control optimista; sube la versión en uno.
     *
     * @param modo modo de precio nuevo
     * @param cuentaIvaDebitoId cuenta de IVA débito nueva
     * @param cuentaIvaCreditoId cuenta de IVA crédito nueva
     * @param versionEsperada versión que el cliente leyó
     * @return {@code false} si la versión ya no coincide
     */
    boolean actualizar(ModoPrecio modo, UUID cuentaIvaDebitoId, UUID cuentaIvaCreditoId, long versionEsperada);
}
