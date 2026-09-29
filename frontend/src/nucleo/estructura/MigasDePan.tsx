import { Fragment } from 'react';
import { Link, useLocation } from 'react-router-dom';
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/compartido/ui/breadcrumb';
import { migasDeRuta } from './migasDeRuta';

/**
 * Migas de pan de la barra superior (spec F4.5 §7.4): "Inicio › Contabilidad › Libro Diario"…
 * El último nivel no lleva enlace (es la pantalla actual). El cálculo de las migas vive en
 * `migasDeRuta.ts` (función pura, sin componentes).
 */
export function MigasDePan() {
  const migas = migasDeRuta(useLocation().pathname);
  return (
    <Breadcrumb>
      <BreadcrumbList>
        {migas.map((miga, indice) => {
          const esUltima = indice === migas.length - 1;
          return (
            // Fragment: el separador es un <li> hermano, nunca anidado dentro del <BreadcrumbItem>
            <Fragment key={`${miga.etiqueta}-${indice}`}>
              <BreadcrumbItem>
                {esUltima || !miga.href ? (
                  <BreadcrumbPage>{miga.etiqueta}</BreadcrumbPage>
                ) : (
                  <BreadcrumbLink asChild>
                    <Link to={miga.href}>{miga.etiqueta}</Link>
                  </BreadcrumbLink>
                )}
              </BreadcrumbItem>
              {!esUltima && <BreadcrumbSeparator />}
            </Fragment>
          );
        })}
      </BreadcrumbList>
    </Breadcrumb>
  );
}
