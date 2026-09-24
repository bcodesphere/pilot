# Consulta profesional sobre el tratamiento del IVA en la facturación — Borrador de respuestas

**Dirigido a:** Contador Público Autorizado
**Asunto:** Criterio profesional sobre la aplicación de precios con IVA incluido y precios más IVA en documentos tributarios electrónicos
**Fecha del borrador:** 2026-09-23
**Estado:** BORRADOR PARA REVISIÓN — no validado por contador

---

> **Advertencia importante**
>
> Estas respuestas son un **borrador de trabajo** preparado para facilitar la revisión del contador. **No constituyen una opinión profesional firmada.** Todas las respuestas deben ser confirmadas, corregidas o ampliadas por un Contador Público Autorizado antes de aplicarse.
>
> Los artículos citados se basan en la Ley de Impuesto a la Transferencia de Bienes Muebles y a la Prestación de Servicios (Ley de IVA), el Código Tributario (CT), el Código de Comercio y la normativa DTE del Ministerio de Hacienda (MH). **Toda cita marcada `[VERIFICAR]` debe contrastarse con el texto vigente**, porque esas normas se han reformado varias veces.
>
> El formulario en blanco se conserva en `formulario-iva.md` para que el contador lo responda sin influencia de este borrador, si así lo prefiere.

---

## Sección 1. Criterio general

**1.1** ¿En qué casos la ley exige que el precio se presente al cliente **con el IVA incluido** y en qué casos exige que se presente **separado del precio**?

> **Respuesta:** Todo depende del documento que se emite.
> - **Factura**, cuando se vende a un consumidor final o a quien no se identifica como contribuyente inscrito: el precio se presenta **con el IVA incluido** y el impuesto no se separa ante el cliente.
> - **Comprobante de Crédito Fiscal (CCF)**, cuando se vende a un contribuyente inscrito en IVA: el precio se presenta **sin IVA** y el impuesto se muestra separado. Así el comprador puede documentar su crédito fiscal.
>
> **Base legal:** CT arts. 107 (obligación de emitir documentos) y 114 (requisitos de CCF y Factura) `[VERIFICAR]` literales exactos; Ley de IVA art. 65 (el crédito fiscal solo se respalda con CCF).

**1.2** ¿Qué determina el tratamiento: el tipo de documento que se emite (Factura o Comprobante de Crédito Fiscal), la calidad del comprador (consumidor final o contribuyente inscrito) o ambos?

> **Respuesta:** Ambos, en cadena: **la calidad del comprador determina el documento, y el documento determina la presentación del precio.** Si el comprador acredita su NRC vigente, corresponde CCF (precio más IVA). Si no lo acredita, corresponde Factura (IVA incluido).
>
> La forma en que la empresa fija internamente sus precios no cambia esta regla. Es una decisión comercial, no fiscal.
>
> **Base legal:** CT art. 107 `[VERIFICAR]`.

**1.3** Una empresa que publica sus precios al público con IVA incluido, como una tienda o un restaurante, ¿cómo debe determinar el precio neto y el débito fiscal cuando el cliente pide un Comprobante de Crédito Fiscal?

> **Respuesta:** Divide el precio publicado entre 1.13 para obtener el neto, y el débito fiscal es el 13 % de ese neto.
>
> Ejemplo: un precio publicado de $22.60 da un neto de $20.00 y un IVA de $2.60, con un total de $22.60. El cliente paga lo mismo; solo cambia la presentación.
>
> **Base legal:** Ley de IVA arts. 47 (base imponible) y 54 (tasa de 13 %) `[VERIFICAR]` número del artículo de base imponible.

**1.4** Una empresa mayorista que cotiza y negocia sus precios sin IVA, ¿qué precio debe mostrar en la Factura cuando vende a un consumidor final?

> **Respuesta:** El precio neto más el 13 %, presentado como un solo precio con IVA incluido.
>
> Ejemplo: un neto de $50.00 se presenta en la Factura como $56.50.
>
> Se recomienda advertir en la cotización que el precio es "más IVA", para evitar reclamos del consumidor.
>
> **Base legal:** CT art. 114 `[VERIFICAR]`.

**1.5** ¿En listas de precios, cotizaciones, pedidos y rótulos al público, existe alguna obligación de indicar si el precio incluye el IVA? ¿Alguna norma de protección al consumidor lo exige?

> **Respuesta:** Sí.
> - Frente al **consumidor final**, la Ley de Protección al Consumidor exige informar el **precio total**, con impuestos incluidos.
> - En operaciones **entre empresas** es práctica aceptada cotizar "más IVA", siempre que se diga expresamente.
>
> **Base legal:** Ley de Protección al Consumidor, artículos sobre información de precios `[VERIFICAR]` artículo exacto.

---

## Sección 2. Factura a consumidor final

**2.1** En la Factura, ¿el IVA debe mostrarse separado al cliente, informarse solo al Ministerio de Hacienda o no mostrarse?

> **Respuesta:** Ante el cliente, **no se separa**: el precio y el total incluyen el IVA.
>
> En la Factura electrónica, el IVA contenido en cada línea y en el total **sí se informa al MH**, como dato de control dentro del documento electrónico. La representación impresa puede mostrar una leyenda informativa del IVA incluido, pero no debe presentarlo como impuesto trasladable.
>
> **Base legal:** CT art. 114 `[VERIFICAR]`; normativa y esquema DTE de la Factura `[VERIFICAR]`.

**2.2** ¿Cuál es la forma correcta de extraer el débito fiscal de un precio con IVA incluido? Ejemplo: de un precio de $11.30, ¿se aplica 11.30 × 13 / 113?

> **Respuesta:** Sí. El IVA es 11.30 × 13 / 113 = **$1.30**. Es equivalente a 11.30 − (11.30 / 1.13).
>
> Se redondea a dos decimales con redondeo aritmético: 0.005 sube al centavo siguiente.

**2.3** Cuando una Factura tiene varias líneas, ¿el débito fiscal se calcula **línea por línea** o sobre el **total del documento**? ¿Cómo se trata la diferencia de centavos que resulta?

*Ejemplo: tres artículos de $1.00 cada uno, con IVA incluido.*
- *IVA calculado por línea: 3 × $0.12 = $0.36.*
- *IVA calculado sobre el total: $3.00 × 13 / 113 = $0.35.*

> **Respuesta:** El débito fiscal que se declara es **el que consta en el documento emitido**.
>
> Criterio recomendado:
> 1. Calcular el IVA sobre el **total gravado del documento**, que es el valor más cercano al 13 % real ($0.35 en el ejemplo).
> 2. Asignar la diferencia de centavo a la última línea, para que la suma de las líneas coincida con el total.
>
> De esta forma no se genera ninguna diferencia contable: el libro de ventas y la contabilidad registran exactamente lo que dice cada documento.
>
> **Base legal o práctica aceptada:** Práctica aceptada. Confirmar la tolerancia de diferencias que admite el MH al recibir el documento `[VERIFICAR]`.

**2.4** ¿Existe un monto a partir del cual la Factura debe identificar al comprador con nombre y documento de identidad? ¿Cuál es ese monto?

> **Respuesta:** Hay dos referencias que deben confirmarse:
> - El CT exige consignar el nombre y el documento de identidad del comprador en Facturas de **$200.00 o más** `[VERIFICAR]` monto y artículo vigentes.
> - La normativa de la Factura electrónica exige identificar al receptor en operaciones de **$25,000.00 o más** `[VERIFICAR]`.
>
> Recomendación: pedir siempre el documento de identidad a partir del monto menor que resulte vigente.
>
> **Base legal:** CT art. 114 `[VERIFICAR]`; normativa DTE `[VERIFICAR]`.

**2.5** Si un contribuyente inscrito pide Factura en lugar de Comprobante de Crédito Fiscal, ¿puede usar ese IVA como crédito fiscal? ¿Qué debe advertirle el vendedor?

> **Respuesta:** **No.** El IVA contenido en una Factura no es deducible como crédito fiscal. Para el comprador, ese IVA es parte del costo o del gasto.
>
> El vendedor no tiene una obligación expresa de advertirlo. Sin embargo, si el comprador se identifica como contribuyente y pide CCF, el vendedor **está obligado a emitirlo**. La buena práctica es preguntar al cliente si necesita CCF antes de emitir.
>
> **Base legal:** Ley de IVA art. 65; CT art. 107 `[VERIFICAR]`.

---

## Sección 3. Comprobante de Crédito Fiscal

**3.1** ¿Qué datos del comprador son obligatorios para emitir un Comprobante de Crédito Fiscal? Por ejemplo: NIT, NRC, nombre, giro, dirección.

> **Respuesta:** Nombre o razón social, NIT, NRC, actividad económica (giro), dirección y, en el documento electrónico, un correo electrónico para la entrega.
>
> **Base legal:** CT art. 114 `[VERIFICAR]`; normativa DTE del CCF `[VERIFICAR]` lista de datos obligatorios.

**3.2** Si el cliente no presenta su NRC vigente, ¿debe emitirse Factura en su lugar? ¿Qué responsabilidad asume el vendedor si emite el Comprobante sin verificar el registro?

> **Respuesta:** Sí: sin un NRC vigente se emite Factura. Se recomienda verificar el NRC en la consulta pública del MH.
>
> Si el vendedor emite un CCF a quien no es contribuyente, o con datos falsos, se expone a observaciones y multas por emitir documentos sin los requisitos legales. Además, el comprador podría usar indebidamente un crédito fiscal.
>
> **Base legal:** CT arts. 107 y 239 (sanciones por incumplimiento en la emisión de documentos) `[VERIFICAR]`.

**3.3** ¿El precio unitario del Comprobante de Crédito Fiscal puede llevar más de dos decimales cuando proviene de un precio con IVA incluido dividido entre 1.13? ¿Cómo se redondea?

*Ejemplo: $5.00 / 1.13 = $4.424778…*

> **Respuesta:** Sí. El formato del documento electrónico admite precios unitarios con más de dos decimales `[VERIFICAR]` cuántos.
>
> Criterio recomendado:
> 1. Conservar el precio unitario con sus decimales ($4.42477876).
> 2. Redondear a dos decimales solo el **valor de la línea** y los **totales**.
>
> Así, el total con IVA cuadra con el precio original publicado: $4.42 de neto + $0.58 de IVA = $5.00.

**3.4** ¿El débito fiscal del Comprobante se calcula sobre la suma de las ventas gravadas del documento o sobre cada línea?

> **Respuesta:** Sobre la **suma de las ventas gravadas del documento, después de descuentos**. En el CCF el IVA se presenta una sola vez, en el resumen del documento. No se informa por línea.
>
> **Base legal:** Ley de IVA art. 55 (débito fiscal); normativa DTE del CCF `[VERIFICAR]`.

---

## Sección 4. Descuentos, rebajas y bonificaciones

**4.1** ¿Los descuentos otorgados en el mismo documento reducen la base del IVA? ¿Existe alguna diferencia entre la Factura y el Comprobante de Crédito Fiscal?

> **Respuesta:** Sí. Los descuentos que **constan en el mismo documento** y son usuales en el comercio reducen la base imponible.
>
> No hay diferencia de fondo entre Factura y CCF; solo cambia la presentación. En la Factura el descuento se aplica sobre precios con IVA; en el CCF se aplica sobre el neto.
>
> **Base legal:** Ley de IVA, artículo sobre partidas que no forman parte de la base imponible (arts. 51–52) `[VERIFICAR]` número exacto.

**4.2** Un descuento sobre un precio con IVA incluido, por ejemplo 10 % sobre $11.30, ¿se aplica antes o después de separar el IVA? ¿Cómo se presenta en el documento?

> **Respuesta:** El resultado es el mismo en ambos casos.
> - **Aplicando el descuento primero:** 11.30 × 0.90 = 10.17 con IVA. Neto $9.00 e IVA $1.17.
> - **Separando el IVA primero:** 10.00 × 0.90 = 9.00 neto, más IVA de $1.17, total $10.17.
>
> En la Factura se muestra el precio con IVA de $11.30, un descuento de $1.13 y un total de $10.17. El IVA contenido ($1.17) se informa al MH.

**4.3** ¿Cuál es el tratamiento de las bonificaciones en especie, del producto regalado y del "2 × 1"? ¿Generan débito fiscal? ¿Sobre qué valor?

> **Respuesta:** Hay dos casos distintos.
> - **"2 × 1" o bonificación dentro de la misma venta:** es un descuento documentado. Ambos artículos se incluyen en el documento, uno con descuento del 100 %. La base es lo efectivamente cobrado y no se genera débito adicional.
> - **Obsequio o retiro de bienes sin venta** (promoción, regalo o uso propio): la ley lo trata como transferencia gravada (autoconsumo) y genera débito fiscal. La base es el valor que fija la ley, generalmente el costo o el precio corriente de mercado.
>
> **Base legal:** Ley de IVA art. 11 (retiro de bienes como hecho generador) y el artículo de base imponible del autoconsumo `[VERIFICAR]`.

**4.4** Un descuento concedido **después** de la venta, por pronto pago o por volumen, ¿se documenta con Nota de Crédito o de otra forma?

> **Respuesta:**
> - **Sobre un CCF:** se documenta con **Nota de Crédito**, que reduce el débito fiscal del vendedor y el crédito fiscal del comprador.
> - **Sobre una Factura:** la Nota de Crédito no aplica. Se recomienda no otorgar descuentos posteriores sobre Facturas y, si ocurren, tratarlos como gasto comercial sin ajustar el IVA `[VERIFICAR]` criterio del contador.
>
> **Base legal:** CT art. 110; Ley de IVA art. 62 `[VERIFICAR]`.

---

## Sección 5. Operaciones exentas, no sujetas y exportaciones

**5.1** En un mismo documento que combina bienes gravados, exentos y no sujetos, ¿cómo deben presentarse los precios de cada uno cuando el precio al público incluye el IVA?

> **Respuesta:** Cada venta se presenta en su propia columna: gravada, exenta o no sujeta.
> - Solo las **ventas gravadas** incluyen el IVA en la Factura, o lo llevan separado en el CCF.
> - Las ventas **exentas y no sujetas** se presentan a su precio final, que no contiene IVA.
>
> Nunca debe extraerse IVA de un precio exento o no sujeto.
>
> **Base legal:** CT art. 114; Ley de IVA arts. 45–46 (exenciones) `[VERIFICAR]`.

**5.2** Para una empresa comercial típica, mencione ejemplos de operaciones **exentas** y de operaciones **no sujetas**, con su base legal.

> **Respuesta:**
> - **Exentas:** las que la ley grava en principio pero libera expresamente. Ejemplos: arrendamiento de inmuebles para vivienda, servicios de educación y de salud prestados por instituciones autorizadas, transporte público de pasajeros, ciertos servicios financieros y seguros de personas (Ley de IVA art. 46 `[VERIFICAR]` lista vigente). En la venta de bienes muebles de una empresa comercial típica, las exenciones son poco frecuentes.
> - **No sujetas:** las que no entran en el hecho generador. Ejemplos: venta de inmuebles (el impuesto recae sobre bienes muebles), transferencia de acciones y títulos valores, y servicios prestados en relación de dependencia.
>
> **Base legal:** Ley de IVA arts. 4, 19 y 46 `[VERIFICAR]`.

**5.3** En las exportaciones con tasa 0 %, ¿el precio se expresa siempre sin IVA? ¿Cómo se registra en el libro de ventas?

> **Respuesta:** Sí. La exportación está gravada con **tasa cero**, así que el precio no lleva IVA. Se documenta con Factura de Exportación.
>
> En el libro de ventas se registra en la columna de exportaciones. Aunque no genera débito fiscal, permite deducir el crédito fiscal de las compras relacionadas y solicitar su reintegro.
>
> **Base legal:** Ley de IVA arts. 74–76 `[VERIFICAR]`.

**5.4** ¿Cómo se determina la proporcionalidad del crédito fiscal cuando la empresa realiza operaciones gravadas y exentas al mismo tiempo?

> **Respuesta:**
> - El crédito fiscal **directamente atribuible a ventas gravadas** se deduce completo.
> - El atribuible **solo a ventas exentas** no se deduce.
> - El crédito **común** a ambas se deduce en proporción a las ventas gravadas sobre las ventas totales del período.
>
> Ejemplo: ventas gravadas de $80,000 y exentas de $20,000 dan una proporción del 80 %. Un crédito común de $1,000 permite deducir $800. Los $200 restantes se cargan al costo o al gasto.
>
> **Base legal:** Ley de IVA art. 66 `[VERIFICAR]`.

---

## Sección 6. Notas de Crédito, Notas de Débito y devoluciones

**6.1** La Nota de Crédito y la Nota de Débito ¿se emiten únicamente para ajustar un Comprobante de Crédito Fiscal? ¿Qué ocurre con las devoluciones sobre Facturas?

> **Respuesta:** Sí. La NC y la ND solo ajustan un **CCF**, y deben emitirse al mismo comprador del documento original.
>
> Las devoluciones sobre **Facturas** (y sobre Facturas de Exportación y de Sujeto Excluido) se documentan, desde la normativa DTE 2.0, con el **Evento de Retorno**.
>
> **Base legal:** CT art. 110 `[VERIFICAR]`; Normativa de Cumplimiento DTE 2.0 `[VERIFICAR]`.

**6.2** Con la normativa DTE 2.0, ¿cuándo corresponde el **Evento de Retorno** y cuándo la **invalidación** del documento? ¿Cuál es el efecto de cada uno en el débito fiscal y en los libros?

> **Respuesta:**
>
> | | Invalidación | Evento de Retorno |
> |---|---|---|
> | Cuándo | El documento tiene un **error** o la operación **no se realizó** (rescisión) | La venta fue válida, pero el cliente **devuelve** bienes o recibe un **reembolso** |
> | Plazo | Factura: tres meses desde el sello. CCF y demás documentos: hasta el día siguiente `[VERIFICAR]` | Tres meses desde el sello del documento original `[VERIFICAR]` |
> | Efecto en el débito fiscal | El documento se tiene por no emitido; su débito desaparece del período | Reduce el débito fiscal por el monto devuelto |
> | Efecto en los libros | Se excluye del libro de ventas o se anota como anulado; si corresponde, se registra el documento que lo reemplaza | Se registra como disminución de ventas en el período en que se emite el retorno |
>
> **Base legal:** Normativa de Cumplimiento DTE 2.0 y Manual Funcional del MH `[VERIFICAR]`.

**6.3** ¿Existe un plazo máximo para emitir la Nota de Crédito después del Comprobante original? ¿En qué período tributario se refleja el ajuste: en el de la venta o en el de la Nota?

> **Respuesta:**
> - **Período:** el ajuste se refleja en el **período en que se emite la Nota de Crédito**, no en el de la venta original. No se rectifica la declaración anterior.
> - **Plazo:** debe confirmarse el plazo vigente para emitir la Nota respecto del documento original `[VERIFICAR]`.
>
> **Base legal:** Ley de IVA art. 62; CT art. 110 `[VERIFICAR]`.

**6.4** Si el Comprobante original se emitió con precio con IVA incluido convertido a neto, ¿cómo se calcula la Nota de Crédito por una devolución parcial para que el IVA coincida con el original?

> **Respuesta:** Se usa el **mismo precio unitario neto del CCF original**, con todos sus decimales, multiplicado por la cantidad devuelta. Luego se aplica el 13 %.
>
> Ejemplo: el original tiene 10 unidades a $4.42477876, con un neto de $44.25 y un IVA de $5.75. Si se devuelven 4 unidades, el neto es $17.70 y el IVA $2.30, total $20.00, que coincide con 4 × $5.00.
>
> Si en una devolución total quedara un centavo de diferencia, se ajusta para que la Nota no supere el IVA del documento original.

---

## Sección 7. Retención, percepción y anticipo a cuenta de IVA

**7.1** Explique los requisitos para aplicar la **retención del 1 %**:
- ¿quién retiene y a quién?
- ¿sobre qué base se calcula?
- ¿a partir de qué monto aplica?
- ¿en qué documento se refleja?

> **Respuesta:**
> - **Quién retiene:** el contribuyente clasificado como **gran contribuyente** (u otro agente designado por el MH) cuando **compra** a un contribuyente que no es gran contribuyente.
> - **Base:** el precio de venta **sin IVA**.
> - **Monto mínimo:** operaciones de **$100.00 o más** `[VERIFICAR]`.
> - **Documentos:** se refleja en el CCF del vendedor como IVA retenido, que disminuye el total a cobrar. El agente además emite un **Comprobante de Retención**.
>
> **Base legal:** CT art. 162 `[VERIFICAR]`.

**7.2** Explique los mismos requisitos para la **percepción del 1 %**.

> **Respuesta:**
> - **Quién percibe:** el **gran contribuyente** designado como agente de percepción, cuando **vende** a un contribuyente que no es gran contribuyente.
> - **Base:** el precio de venta sin IVA.
> - **Monto mínimo:** operaciones de **$100.00 o más** `[VERIFICAR]`.
> - **Documento:** se suma al total del CCF como IVA percibido.
>
> **Base legal:** CT art. 163 `[VERIFICAR]`.

**7.3** ¿Pueden aplicarse retención y percepción en una misma operación?

> **Respuesta:** **No.** Son situaciones opuestas:
> - la retención ocurre cuando el gran contribuyente es el **comprador**;
> - la percepción ocurre cuando el gran contribuyente es el **vendedor**.
>
> Entre dos grandes contribuyentes no aplica ninguna de las dos.

**7.4** Si la operación se paga en cuotas o mediante varios documentos, cada uno menor de $100.00, ¿el monto mínimo para retener o percibir se evalúa por documento o por operación?

> **Respuesta:** En la práctica se evalúa **por documento**.
>
> Sin embargo, el fraccionamiento artificial de una misma operación para no alcanzar el monto mínimo puede ser objetado por la Administración Tributaria. Si varios documentos corresponden a una sola operación pactada, el criterio prudente es aplicar la retención o percepción.
>
> **Base legal:** CT arts. 162–163 `[VERIFICAR]`; confirmar criterio del contador.

**7.5** En las ventas cobradas con tarjeta de crédito o débito, ¿aplica el anticipo a cuenta de IVA retenido por el emisor de la tarjeta? ¿Cómo se registra?

> **Respuesta:** Sí. Las entidades emisoras de tarjetas retienen un **anticipo a cuenta de IVA del 2 %** sobre el monto pagado sin IVA `[VERIFICAR]` tasa y base vigentes.
>
> Se registra como un activo ("IVA anticipo a cuenta – tarjetas") y se deduce en el F-07 del período.
>
> Ejemplo: una venta de $113.00 cobrada con tarjeta genera un anticipo de $2.00, calculado como el 2 % de $100.00.
>
> **Base legal:** CT art. 162, inciso sobre tarjetas de crédito y débito `[VERIFICAR]`.

**7.6** Cuando la empresa es agente de retención, ¿debe emitir un Comprobante de Retención por cada operación o puede agruparlas? ¿En qué plazo?

> **Respuesta:** El criterio general es emitir el Comprobante de Retención **por cada operación en la que se retiene**, al momento de pagar o de registrar la compra. Debe confirmarse si la normativa DTE permite agrupar varias operaciones del mismo proveedor en el período y cuál es el plazo de emisión y entrega `[VERIFICAR]`.
>
> El IVA retenido se entera en el F-07 del período en que se retuvo.
>
> **Base legal:** CT arts. 112 y 162 `[VERIFICAR]`; normativa DTE del Comprobante de Retención `[VERIFICAR]`.

---

## Sección 8. Libros de IVA

**8.1** En el **Libro de Ventas a Consumidores Finales**:
- ¿se registran las ventas con IVA incluido o separado?
- ¿se registra por día, por documento o por rango de correlativos?
- ¿cuáles son las columnas obligatorias?

> **Respuesta:**
> - **Montos:** se registran **con IVA incluido**. El débito fiscal se determina al totalizar el mes (total gravado × 13 / 113).
> - **Frecuencia:** se permite un **resumen diario** por establecimiento, indicando del primer al último documento del día.
> - **Columnas habituales:** fecha; número del primer y del último documento; ventas exentas; ventas no sujetas; ventas gravadas locales; exportaciones; total de ventas. Con documentos electrónicos, el anexo del MH puede requerir datos adicionales por documento `[VERIFICAR]` formato vigente.
>
> **Base legal:** CT art. 141 y su Reglamento `[VERIFICAR]`.

**8.2** En el **Libro de Ventas a Contribuyentes**, ¿cuáles son las columnas obligatorias? ¿Cómo se registran las Notas de Crédito y de Débito?

> **Respuesta:**
> - **Columnas habituales:** correlativo; fecha de emisión; número del documento (número de control o código de generación); NRC y nombre del cliente; ventas exentas; ventas no sujetas; ventas gravadas; débito fiscal; ventas y débito por cuenta de terceros; IVA retenido o percibido; total.
> - **Notas:** la **Nota de Crédito** se registra en el mismo libro con **valores negativos** (o en columna de disminución) en el período de su emisión. La **Nota de Débito** se registra con valores positivos.
>
> **Base legal:** CT art. 141 `[VERIFICAR]`.

**8.3** En el **Libro de Compras**:
- ¿cómo se registran las compras con retención o percepción?
- ¿cómo se registran las compras a sujetos excluidos?
- ¿cómo se registran las importaciones?

> **Respuesta:**
> - **Retención o percepción:** la compra se registra por su neto y su crédito fiscal. El **IVA percibido** se anota en una columna propia, porque es un anticipo deducible. El **IVA retenido** por el comprador no va en el libro de compras; se controla aparte como obligación a enterar.
> - **Sujetos excluidos:** se registran en su columna, respaldadas con la Factura de Sujeto Excluido emitida por el comprador. No generan crédito fiscal.
> - **Importaciones:** se registran con la declaración de mercancías. El IVA pagado en aduana es crédito fiscal.
>
> Además, el crédito fiscal solo puede deducirse dentro del plazo legal desde la emisión del documento, que se ha interpretado como el período de emisión y los tres siguientes `[VERIFICAR]`.
>
> **Base legal:** CT art. 141; Ley de IVA art. 65 `[VERIFICAR]`.

**8.4** ¿Dentro de cuántos días debe asentarse cada documento en los libros? ¿Qué requisitos de legalización y conservación aplican cuando los libros se llevan en forma electrónica?

> **Respuesta:**
> - **Plazo de registro:** los libros de IVA no pueden tener un atraso mayor de **15 días calendario**.
> - **Autorización:** los libros de IVA se autorizan por un contador público autorizado `[VERIFICAR]`.
> - **Libros contables:** los libros legales (diario, mayor, estados financieros) se legalizan conforme al Código de Comercio.
> - **Libros electrónicos:** deben poder imprimirse y consultarse en el establecimiento a requerimiento de la Administración Tributaria.
> - **Conservación:** mínimo **10 años**.
>
> **Base legal:** CT arts. 141 y 147; Código de Comercio, artículos sobre contabilidad formal (arts. 435 y siguientes) `[VERIFICAR]`.

**8.5** Los documentos firmados y entregados que aún no tienen sello de recepción del Ministerio de Hacienda (estado transitorio o contingencia), ¿se registran en el libro del mes en que se emitieron?

> **Respuesta:** **Sí.** El impuesto se causa al emitir el documento o entregar el bien, lo que ocurra primero, no al recibir el sello.
>
> La normativa DTE 2.0 exige además registrarlos contablemente con su código de generación y fecha de emisión. Antes de cerrar el mes debe verificarse que todos hayan obtenido sello. Si alguno se rechaza definitivamente, se corrige con el documento que lo sustituya.
>
> **Base legal:** Ley de IVA art. 8 (momento de causación); Normativa DTE 2.0 `[VERIFICAR]`.

---

## Sección 9. Registro contable

**9.1** Confirme o corrija las siguientes partidas propuestas.

*a) Venta con Factura, al contado, por $113.00 con IVA incluido*

| Cuenta | Debe | Haber |
|---|---|---|
| Caja | 113.00 | |
| Ventas gravadas | | 100.00 |
| IVA débito fiscal | | 13.00 |

*b) Venta con Comprobante de Crédito Fiscal, al crédito, por $1,000.00 más IVA, a un gran contribuyente que retiene el 1 %*

| Cuenta | Debe | Haber |
|---|---|---|
| Cuentas por cobrar | 1,130.00 | |
| Ventas gravadas | | 1,000.00 |
| IVA débito fiscal | | 130.00 |

*Cobro:*

| Cuenta | Debe | Haber |
|---|---|---|
| Bancos | 1,120.00 | |
| IVA retenido a favor | 10.00 | |
| Cuentas por cobrar | | 1,130.00 |

*c) Nota de Crédito por devolución de $200.00 más IVA*

| Cuenta | Debe | Haber |
|---|---|---|
| Devoluciones sobre ventas | 200.00 | |
| IVA débito fiscal | 26.00 | |
| Cuentas por cobrar | | 226.00 |

> **Observaciones:** Las tres partidas son correctas. Hay tres precisiones:
> 1. **Partida (b):** como el CCF ya refleja la retención, puede registrarse la cuenta por cobrar directamente por $1,120.00 y el IVA retenido a favor por $10.00 al momento de la venta. Así el saldo del cliente coincide con lo que realmente pagará. Ambas formas son aceptables; la segunda facilita la conciliación de cartera.
> 2. **Partida (c):** si hubo devolución física de mercadería, se agrega el reingreso al inventario al costo:
>
>    | Cuenta | Debe | Haber |
>    |---|---|---|
>    | Inventario | costo | |
>    | Costo de ventas | | costo |
>
> 3. **Todas las ventas:** debe registrarse también el costo de ventas correspondiente.

**9.2** ¿Recomienda registrar la venta de mostrador con Factura **documento por documento** o mediante una **partida resumen diaria**?

> **Respuesta:** **Partida resumen diaria por establecimiento**, respaldada por el detalle de documentos del día. Es consistente con el libro de ventas a consumidores finales y mantiene el libro diario legible.
>
> Las ventas al crédito con Factura sí conviene registrarlas por documento, para controlar la cuenta de cada cliente.

**9.3** Las diferencias de centavos por redondeo entre el IVA de los documentos y el IVA calculado sobre el total del mes, ¿en qué cuenta deben registrarse?

> **Respuesta:** Si la contabilidad registra el IVA **tal como consta en cada documento** (criterio de la pregunta 2.3), **no se producen diferencias**. El débito fiscal declarado es la suma de los documentos, no un recálculo sobre el total del mes.
>
> Si aun así surgiera una diferencia menor al liquidar, se registra en "Otros ingresos" u "Otros gastos – diferencias por redondeo" y se documenta su origen.

**9.4** ¿Qué nombres y qué códigos de cuenta recomienda para las cuentas de IVA en un catálogo de cuentas bajo NIIF para PYMES? Por ejemplo: IVA débito fiscal, IVA crédito fiscal, IVA retenido a favor, IVA percibido a favor, IVA por pagar y remanente.

> **Respuesta:** Propuesta referencial. Los códigos deben ajustarse al catálogo de cada empresa.
>
> | Código | Cuenta | Naturaleza |
> |---|---|---|
> | 1106 | IVA – Crédito fiscal | Activo corriente |
> | 1106.01 | Crédito fiscal por compras locales | |
> | 1106.02 | Crédito fiscal por importaciones | |
> | 1107 | IVA – Remanente de crédito fiscal | Activo corriente |
> | 1108 | IVA – Retenciones a favor | Activo corriente |
> | 1109 | IVA – Percepciones a favor | Activo corriente |
> | 1110 | IVA – Anticipo a cuenta (tarjetas) | Activo corriente |
> | 2105 | IVA – Débito fiscal | Pasivo corriente |
> | 2106 | IVA – Por pagar | Pasivo corriente |
> | 2107 | IVA – Retenido por pagar (como agente) | Pasivo corriente |
> | 2108 | IVA – Percibido por pagar (como agente) | Pasivo corriente |

---

## Sección 10. Liquidación mensual y declaración (F-07)

**10.1** Confirme el orden correcto de la liquidación mensual:
1. débito fiscal;
2. menos crédito fiscal;
3. menos remanente del mes anterior;
4. menos retenciones, percepciones y anticipos a favor;
5. igual a impuesto a pagar o nuevo remanente.

> **Respuesta:** Correcto, con una precisión:
> - si el crédito fiscal y el remanente superan al débito, la diferencia es el **nuevo remanente de crédito fiscal**, y las retenciones, percepciones y anticipos quedan sin aplicar ese mes;
> - si la empresa es agente de retención o percepción, al impuesto resultante se **suman** el IVA retenido y el percibido a terceros, que debe enterar.
>
> **Base legal:** Ley de IVA arts. 64 y 67; CT arts. 162–163 `[VERIFICAR]`.

**10.2** Resuelva el siguiente caso y elabore la partida de liquidación.

| Concepto | Monto |
|---|---|
| Ventas con Factura (IVA incluido) | $11,300.00 |
| Ventas con Comprobante de Crédito Fiscal (neto) | $20,000.00 |
| Notas de Crédito emitidas (neto) | $1,000.00 |
| Compras con Comprobante de Crédito Fiscal (neto) | $15,000.00 |
| Remanente del mes anterior | $200.00 |
| IVA retenido por clientes | $150.00 |
| IVA percibido por proveedores | $80.00 |

> **Desarrollo:**
>
> | Concepto | Base | IVA |
> |---|---|---|
> | Ventas con Factura (11,300.00 / 1.13) | 10,000.00 | 1,300.00 |
> | Ventas con CCF | 20,000.00 | 2,600.00 |
> | Notas de Crédito | (1,000.00) | (130.00) |
> | **Débito fiscal** | | **3,770.00** |
> | Crédito fiscal por compras | 15,000.00 | (1,950.00) |
> | Remanente del mes anterior | | (200.00) |
> | **Impuesto determinado** | | **1,620.00** |
> | Retenciones a favor | | (150.00) |
> | Percepciones a favor | | (80.00) |
> | **IVA a pagar** | | **1,390.00** |
>
> **Partida de liquidación:**
>
> | Cuenta | Debe | Haber |
> |---|---|---|
> | IVA – Débito fiscal | 3,770.00 | |
> | IVA – Crédito fiscal | | 1,950.00 |
> | IVA – Remanente de crédito fiscal | | 200.00 |
> | IVA – Retenciones a favor | | 150.00 |
> | IVA – Percepciones a favor | | 80.00 |
> | IVA – Por pagar | | 1,390.00 |
> | **Sumas iguales** | **3,770.00** | **3,770.00** |
>
> **Partida de pago:**
>
> | Cuenta | Debe | Haber |
> |---|---|---|
> | IVA – Por pagar | 1,390.00 | |
> | Bancos | | 1,390.00 |

**10.3** Si las retenciones y percepciones a favor superan el impuesto del mes, ¿el excedente se acumula como remanente, se solicita en devolución o se pierde?

> **Respuesta:** **No se pierde.** El excedente puede aplicarse en los períodos siguientes. Si se mantiene, puede solicitarse su devolución o compensación ante la Administración Tributaria.
>
> Contablemente permanece en las cuentas de retenciones y percepciones a favor hasta que se aplica.
>
> **Base legal:** CT arts. 162–163 y disposiciones sobre devolución y compensación (arts. 212 y siguientes) `[VERIFICAR]`.

**10.4** ¿Cuál es el plazo de presentación y pago del F-07? ¿Qué anexos deben presentarse y cuál es su formato vigente?

> **Respuesta:**
> - **Plazo:** el período es **mensual**. La declaración y el pago vencen dentro de los **10 primeros días hábiles del mes siguiente**.
> - **Anexos**, que se cargan en archivos electrónicos en el portal del MH:
>   - detalle de ventas a contribuyentes;
>   - detalle de ventas a consumidores finales;
>   - detalle de compras;
>   - compras a sujetos excluidos;
>   - retenciones, percepciones y anticipos (efectuados y recibidos);
>   - documentos anulados o invalidados.
> - **Formato:** debe confirmarse la estructura vigente de cada anexo `[VERIFICAR]`.
>
> **Base legal:** Ley de IVA arts. 93–94 `[VERIFICAR]`.

**10.5** ¿Qué conciliaciones recomienda hacer antes de presentar el F-07? Por ejemplo: libros contra contabilidad, o documentos emitidos contra documentos con sello de recepción.

> **Respuesta:**
> 1. **Documentos emitidos contra documentos sellados por el MH.** No debe quedar ningún documento transitorio ni rechazado sin resolver.
> 2. **Libros de ventas contra la cuenta IVA débito fiscal**, y **libro de compras contra la cuenta IVA crédito fiscal**.
> 3. **Compras registradas contra los documentos recibidos** que aparecen en la consulta del MH. Así se detectan documentos no registrados o invalidados por el proveedor.
> 4. **Retenciones y percepciones a favor contra los comprobantes recibidos** de clientes y proveedores.
> 5. **Anticipos de tarjetas contra los estados de liquidación** del banco emisor.
> 6. **Remanente del mes anterior contra el F-07 anterior presentado.**

---

## Sección 11. Casos prácticos para su criterio

**Caso A.** Una ferretería vende una herramienta con precio al público de $56.50. El comprador es una empresa con NRC y pide Comprobante de Crédito Fiscal.

> **Desarrollo:**
> - **Documento:** CCF.
> - **Precio:** 56.50 / 1.13 = **$50.00** neto.
> - **IVA:** **$6.50**.
> - **Total:** **$56.50**. El cliente paga lo mismo que en el rótulo.
> - No aplica retención, porque es menor de $100.00.
>
> | Cuenta | Debe | Haber |
> |---|---|---|
> | Caja | 56.50 | |
> | Ventas gravadas | | 50.00 |
> | IVA – Débito fiscal | | 6.50 |

**Caso B.** Una distribuidora que es gran contribuyente vende $850.00 más IVA a una tienda que no es gran contribuyente.

> **Desarrollo:**
> - **Documento:** CCF con percepción.
> - **Montos:** neto $850.00 + IVA $110.50 + percepción 1 % $8.50 = **total $969.00**.
>
> **Vendedor (distribuidora), venta al crédito:**
>
> | Cuenta | Debe | Haber |
> |---|---|---|
> | Cuentas por cobrar | 969.00 | |
> | Ventas gravadas | | 850.00 |
> | IVA – Débito fiscal | | 110.50 |
> | IVA – Percibido por pagar | | 8.50 |
>
> **Comprador (tienda):**
>
> | Cuenta | Debe | Haber |
> |---|---|---|
> | Inventario | 850.00 | |
> | IVA – Crédito fiscal | 110.50 | |
> | IVA – Percepciones a favor | 8.50 | |
> | Cuentas por pagar | | 969.00 |

**Caso C.** Una pequeña empresa que no es gran contribuyente vende $2,400.00 más IVA a un gran contribuyente.

> **Desarrollo:**
> - **Documentos:** el vendedor emite CCF con retención. El comprador emite Comprobante de Retención.
> - **Montos:** neto $2,400.00 + IVA $312.00 = $2,712.00. Retención 1 %: $24.00. **Monto a cobrar: $2,688.00**.
>
> **Vendedor:**
>
> | Cuenta | Debe | Haber |
> |---|---|---|
> | Cuentas por cobrar | 2,688.00 | |
> | IVA – Retenciones a favor | 24.00 | |
> | Ventas gravadas | | 2,400.00 |
> | IVA – Débito fiscal | | 312.00 |
>
> **Comprador (gran contribuyente):**
>
> | Cuenta | Debe | Haber |
> |---|---|---|
> | Inventario o gasto | 2,400.00 | |
> | IVA – Crédito fiscal | 312.00 | |
> | Cuentas por pagar | | 2,688.00 |
> | IVA – Retenido por pagar | | 24.00 |

**Caso D.** Un restaurante vende un combo de $8.75 con IVA incluido. Se aplica un descuento del 15 % por promoción y el cliente paga con tarjeta de crédito.

> **Desarrollo:**
> - **Documento:** Factura.
> - **Descuento:** 8.75 × 15 % = $1.31. El precio con descuento es **$7.44** (8.75 × 0.85 = 7.4375, redondeado).
> - **Separación del IVA:** 7.44 / 1.13 = **$6.58** de neto e IVA de **$0.86**.
> - **Anticipo por tarjeta:** 2 % de $6.58 = **$0.13** `[VERIFICAR]` tasa y base.
>
> Partida, sin incluir la comisión bancaria, que se registra aparte como gasto financiero:
>
> | Cuenta | Debe | Haber |
> |---|---|---|
> | Cuentas por cobrar – emisor de tarjeta | 7.31 | |
> | IVA – Anticipo a cuenta (tarjetas) | 0.13 | |
> | Ventas gravadas | | 6.58 |
> | IVA – Débito fiscal | | 0.86 |
>
> *Alternativa:* si la empresa desea medir sus promociones, registra las ventas brutas en $7.74 (8.75 / 1.13) y un descuento sobre ventas de $1.16. El neto es el mismo, $6.58.

**Caso E.** Un cliente devuelve, 20 días después de la compra, dos de los cinco artículos que compró con Factura por $45.20 en total.

> **Desarrollo:**
> - **Documento:** Evento de Retorno (normativa DTE 2.0), que está dentro del plazo de tres meses. No corresponde Nota de Crédito, porque el documento original es una Factura.
> - **Precio unitario:** 45.20 / 5 = $9.04 con IVA.
> - **Devolución:** 2 × 9.04 = **$18.08**, con neto de **$16.00** e IVA de **$2.08**.
>
> | Cuenta | Debe | Haber |
> |---|---|---|
> | Devoluciones sobre ventas | 16.00 | |
> | IVA – Débito fiscal | 2.08 | |
> | Caja | | 18.08 |
>
> Reingreso de la mercadería:
>
> | Cuenta | Debe | Haber |
> |---|---|---|
> | Inventario | costo | |
> | Costo de ventas | | costo |
>
> El ajuste al débito fiscal se refleja en el período en que se emite el retorno.

**Caso F.** Una venta con Factura se emitió con el precio equivocado y el error se detecta el mismo día. ¿Corresponde invalidar el documento, emitir un Retorno o emitir un documento nuevo?

> **Desarrollo:** Corresponde **invalidar** la Factura por **error en la información**, dentro del plazo de invalidación, y **emitir una Factura nueva** con el precio correcto que la reemplace. La invalidación de este tipo exige indicar el documento de reemplazo.
>
> **No corresponde el Retorno**, porque no hubo devolución ni reembolso: el documento estaba mal emitido.
>
> Contablemente se revierte la partida de la venta original y se registra la nueva. Si ambas ocurren el mismo día antes del cierre diario, basta con registrar la venta correcta en el resumen diario y dejar la Factura invalidada anotada como tal.

---

## Sección 12. Observaciones adicionales

> **Observaciones:** Aspectos que se recomienda revisar con el contador:
> 1. **Anticipos de clientes:** el cobro anticipado causa IVA al recibirse, porque el pago es uno de los momentos de causación. Debe emitirse el documento en ese momento (Ley de IVA art. 8 `[VERIFICAR]`).
> 2. **Ventas a plazos:** el IVA se causa por el total al emitir el documento o entregar el bien, no a medida que se cobran las cuotas. Los intereses de financiamiento pueden estar gravados `[VERIFICAR]`.
> 3. **Autoconsumo y retiro de bienes:** los bienes para uso del propietario, los obsequios, las muestras y las pérdidas no justificadas pueden generar débito fiscal (Ley de IVA art. 11).
> 4. **Ventas por cuenta de terceros y consignaciones:** se documentan con Comprobante de Liquidación. El débito fiscal corresponde al mandante `[VERIFICAR]`.
> 5. **Propinas en restaurantes:** si son voluntarias y se entregan íntegramente al personal, generalmente no forman parte de la base del IVA. Confirmar tratamiento vigente `[VERIFICAR]`.
> 6. **Compras a sujetos excluidos:** el comprador emite la Factura de Sujeto Excluido y puede tener obligación de retener impuesto sobre la renta. No generan crédito fiscal.
> 7. **Pequeños contribuyentes excluidos del IVA:** las compras que se les hacen no generan crédito fiscal (Ley de IVA art. 28 `[VERIFICAR]` umbrales).
> 8. **Documentos en contingencia:** deben transmitirse al MH dentro de las 72 horas siguientes a superar la contingencia. Un documento rechazado después de entregado exige corrección y comunicación al cliente.
> 9. **Crédito fiscal no deducido a tiempo:** si un CCF de compra no se registra dentro del plazo legal, se pierde el derecho a deducirlo.

---

## Validación

*Esta sección debe completarla el Contador Público Autorizado que revise y valide las respuestas.*

| | |
|---|---|
| Nombre del contador | |
| Número de inscripción en el CVPCPA | |
| Respuestas confirmadas sin cambios | |
| Respuestas corregidas (indicar números) | |
| Firma | |
| Sello | |
| Fecha | |
| Normativa consultada (documento y versión) | |
