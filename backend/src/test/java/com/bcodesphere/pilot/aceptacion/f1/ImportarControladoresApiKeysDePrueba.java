package com.bcodesphere.pilot.aceptacion.f1;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Importa, por nombre, la configuración de prueba de F1-06 ({@code ConfiguracionApiKeysDePrueba}, con los controladores
 * bajo {@code /api/v1/integraciones/n8n/**} que hacen las veces del webhook real de F5). Esa clase es privada de su
 * paquete y no se puede modificar en esta tarea; importarla por nombre permite reutilizarla sin tocarla.
 */
class ImportarControladoresApiKeysDePrueba implements ImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        // 1. Nombre binario de la configuración de F1-06 (no se puede referenciar con .class desde otro paquete)
        return new String[] {"com.bcodesphere.pilot.plataforma.api.ConfiguracionApiKeysDePrueba"};
    }
}
