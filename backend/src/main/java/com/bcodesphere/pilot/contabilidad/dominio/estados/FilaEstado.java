package com.bcodesphere.pilot.contabilidad.dominio.estados;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.Cuenta;

/**
 * Fila de un rubro de estado financiero: una cuenta (de cualquier nivel entre 2 y el nivel pedido) con su monto ya
 * en positivo según la naturaleza de la clase (CLAUDE.md 10.4, ADR-038 §7).
 *
 * @param cuenta cuenta de agrupación o de detalle
 * @param nivel nivel de la cuenta (2 a 5; el nivel 1 es la clase, representada por el {@link RubroEstado})
 * @param monto monto del período o de la fecha de corte, en positivo
 */
public record FilaEstado(Cuenta cuenta, int nivel, Dinero monto) {}
