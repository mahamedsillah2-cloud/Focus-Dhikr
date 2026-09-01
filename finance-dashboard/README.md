# Panel Financiero multi-negocio

Panel de control financiero diario para varios negocios de e-commerce, en **un único archivo**
`index.html` (HTML + CSS + JavaScript). Sin backend, sin instalación y sin registro: los datos se
guardan de forma automática y permanente en el `localStorage` del navegador.

## Cómo usarlo

- **Doble clic** sobre `index.html`, o súbelo a cualquier hosting estático (GitHub Pages, Netlify, Vercel…).
- Se recomienda usar siempre el mismo navegador y dispositivo: los datos viven en ese navegador.
- Para llevarlos a otro sitio, usa **💾 Copia de seguridad** (descarga un JSON con todo) y **↩️ Restaurar**.

## Qué incluye

- **Pestañas por negocio** independientes (vienen creadas *Draselle* y *SD Sneakers*), con color de
  marca propio. Se pueden crear, renombrar y eliminar sin límite; cada una tiene su histórico,
  cálculos, récords y gráficos separados.
- **⚙️ Configuración por negocio**: plan de comisiones de Shopify Payments y gastos recurrentes.
- **Alta de pedidos en segundos**: fecha, cliente, producto, precio, coste, publicidad y notas.
  Se guarda al pulsar *Guardar pedido* (la fecha se mantiene para seguir metiendo pedidos del mismo día).
  Atajo: `Ctrl`/`Cmd` + `Enter`.
- **Comisión de Shopify Payments** descontada en cada pedido: `(precio de venta × %) + cuota fija`.
  El plan se elige en *⚙️ Configuración* (Basic 2,1 % · Grow 1,8 % · Advanced 1,6 % · Personalizado),
  con Draselle en Basic y SD Sneakers en Grow por defecto. La comisión se muestra como una columna
  propia en el histórico, en el resumen diario y en el desglose de cada pedido.
- **Gastos recurrentes** por negocio (apps, plan de Shopify, dominio…) con importe y frecuencia
  mensual o anual; los anuales se convierten a su equivalente mensual. Se restan una vez al mes del
  beneficio neto del negocio, nunca del beneficio de cada pedido.
- **Cálculos automáticos** por pedido, día y mes: facturación, coste, ads, comisiones, beneficio neto,
  margen y ticket medio, más la comparación con el mes anterior.
  - Beneficio del pedido = venta − coste − publicidad − comisión de Shopify Payments.
  - Beneficio neto del mes = facturación − coste − publicidad − comisiones − gastos fijos − gastos diarios.
- **Récords**: mejor mes y mejor día históricos, mejor y peor día del mes, y aviso cuando el mes en
  curso va camino de batir el récord.
- **Gráficos** (Chart.js vía CDN): beneficio diario del mes y evolución mensual de los últimos 12 meses.
  Si no hay conexión, el resto del panel sigue funcionando.
- **📅 Gastos diarios**: sección propia (pestaña de primer nivel) con selector de negocio, alta rápida
  (fecha, descripción, importe y negocio), **calendario mensual** que marca los días con gasto y su
  importe, detalle al hacer clic en un día, resumen del mes (total, media diaria, desglose por negocio
  y mayores gastos) y tabla completa con filtros, edición, borrado y exportación a CSV.
  El total mensual de cada negocio se resta del beneficio neto de ese negocio. Es opcional: si no
  apuntas nada, todo queda a 0 € y no afecta a ningún cálculo.
- **💰 Saldo en caja** por negocio: se parte de un saldo inicial (⚙️ Configuración) y se le suma el
  beneficio neto de cada mes ya cerrado, con el mes en curso mostrado aparte como proyección. El botón
  *Ajustar* permite cuadrarlo con el banco real guardando solo la diferencia, sin recalcular el histórico.
  Verde si es positivo, rojo si es negativo.
- **🌍 Visión global**: pestaña de solo lectura con el saldo total combinado, el saldo de cada negocio y
  su peso en el total, el beneficio neto y los gastos (diarios, fijos y comisiones) del mes sumando
  todos los negocios, y un gráfico de la evolución del saldo total mes a mes.
- **Aviso de inactividad** por negocio cuando pasan más de 24 h sin registrar pedidos.
- **Histórico completo** con filtros por rango de fechas, búsqueda libre, edición, borrado y export a CSV.
- **Importación**: pegar el listado de pedidos copiado de Shopify (detecta cliente y total) o pegar
  filas de Excel/Sheets (fecha, cliente, producto, precio, coste, ads). Siempre con vista previa editable.
- **Ranking de productos** con unidades, precio medio, facturación, beneficio y margen medio por producto.

## Colores

Verde = beneficio positivo · Rojo = pérdida · Gris = sin pedidos. Este criterio es igual en todos los
negocios; el color de marca solo cambia los acentos de la interfaz.
