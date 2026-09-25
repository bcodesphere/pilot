package com.bcodesphere.pilot.plataforma.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcodesphere.pilot.compartido.ClavesMdc;
import com.bcodesphere.pilot.compartido.EmpresaId;
import com.bcodesphere.pilot.compartido.GeneradorId;
import com.bcodesphere.pilot.plataforma.BasePlataformaIT;
import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transacciones anidadas (REQUIRES_NEW) del gestor con contexto de empresa. Va aparte de {@link TransaccionesIT}
 * porque esa clase limita el pool a UNA conexión y una transacción anidada necesita una segunda.
 */
class TransaccionesAnidadasIT extends BasePlataformaIT {

    @Autowired
    private PlatformTransactionManager gestor;

    /** Caso: tras un REQUIRES_NEW el MDC de la transacción externa (con empresa) se reanuda intacto (doResume). */
    @Test
    void elMdcSeReanudaTrasUnaTransaccionAnidada() {
        EmpresaId empresa = new EmpresaId(GeneradorId.nuevo());

        String[] tras = ContextoEmpresa.ejecutarCon(
                empresa,
                "u-externa",
                () -> new TransactionTemplate(gestor).execute(s0 -> {
                    TransactionTemplate interna = new TransactionTemplate(gestor);
                    interna.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    interna.execute(s -> 1);
                    return new String[] {MDC.get(ClavesMdc.EMPRESA_ID), MDC.get(ClavesMdc.USUARIO_ID)};
                }));

        assertThat(tras).containsExactly(empresa.toString(), "u-externa");
    }

    /**
     * Caso (los tres estados de doResume): tras un REQUIRES_NEW en modo sin empresa el MDC reanudado lleva solo el
     * usuario, sin empresa.
     */
    @Test
    void elMdcSeReanudaSoloConUsuarioEnModoSinEmpresa() {
        String[] tras = ContextoEmpresa.ejecutarSinEmpresa(
                "u-sin",
                () -> new TransactionTemplate(gestor).execute(s0 -> {
                    TransactionTemplate interna = new TransactionTemplate(gestor);
                    interna.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    interna.execute(s -> 1);
                    return new String[] {MDC.get(ClavesMdc.EMPRESA_ID), MDC.get(ClavesMdc.USUARIO_ID)};
                }));

        assertThat(tras[0]).isNull();
        assertThat(tras[1]).isEqualTo("u-sin");
    }
}
