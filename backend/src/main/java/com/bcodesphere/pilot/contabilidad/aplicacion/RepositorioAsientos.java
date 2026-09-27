package com.bcodesphere.pilot.contabilidad.aplicacion;

import com.bcodesphere.pilot.contabilidad.dominio.ExcepcionContabilidad;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.Asiento;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de escritura del Libro Diario de la empresa activa: numeración, inserción, mayorización y paso a
 * {@code REVERTIDO}. Todo corre dentro de la transacción del caso de uso (CLAUDE.md 4.1, ADR-018); las consultas de
 * lectura están en {@link ConsultaAsientos}.
 */
public interface RepositorioAsientos {

    /**
     * Asigna el siguiente número del año con {@code INSERT … ON CONFLICT DO UPDATE … RETURNING} (ADR-036). El bloqueo
     * de la fila serializa la numeración y se libera al terminar la transacción, así que no hay duplicados ni huecos.
     *
     * @param anio año de la fecha contable
     * @return el número asignado, desde 1
     */
    long siguienteNumero(int anio);

    /**
     * Guarda (inserta) la cabecera y las líneas del asiento. El nombre evita el prefijo {@code insert}, que SpotBugs
     * toma por un mutador y marcaría a quien guarde el puerto.
     *
     * @param asiento asiento nuevo, con su número ya asignado
     * @throws ExcepcionContabilidad {@code CON-008} si otra reversión del mismo asiento ganó la carrera
     */
    void guardar(Asiento asiento);

    /**
     * Acumula las líneas del asiento en {@code saldo_cuenta_mensual} (mayorización, CLAUDE.md 10.3), agrupadas por
     * cuenta y mes y en orden de cuenta para evitar interbloqueos.
     *
     * @param asiento asiento ya insertado
     */
    void mayorizar(Asiento asiento);

    /**
     * Lee un asiento con sus líneas bloqueando su cabecera ({@code SELECT … FOR UPDATE}): dos reversiones simultáneas
     * se serializan y la segunda ve el asiento ya revertido.
     *
     * @param id asiento buscado
     * @return el asiento, o vacío si no existe en la empresa activa
     */
    Optional<Asiento> buscarBloqueando(UUID id);

    /**
     * Pasa el asiento a {@code REVERTIDO} enlazando su reversión y subiendo la versión en uno, como exige el trigger
     * de transiciones (ADR-019).
     *
     * @param id asiento original
     * @param reversionId contra-asiento ya insertado
     */
    void marcarRevertido(UUID id, UUID reversionId);
}
