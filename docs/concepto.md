# Archaeo — Documento de concepto

Documento vivo. Las secciones marcan el estado de cada idea:

- **borrador** — idea inicial, aún se discute
- **propuesta** — diseño concreto pendiente de acuerdo
- **acordado** — se da por bueno hasta que lo cambiemos

Plugin: `archeology-plugin` (`com.nowko`). Nombre de producto tentativo: **Archaeo**.
Documentación necesaria para el desarrollo en https://hub.spigotmc.org/javadocs/spigot/index.html

---

## Intención

Ampliar la arqueología de Minecraft para que los jugadores descubran, excaven, estudien y conserven restos del pasado del mundo en un mapa personalizado.

Arqueología vanilla vs Archaeo:

1. **Vanilla** — recorrer el mundo, detectar algo, pincelar, llevarse un hallazgo. Corto, azaroso.
2. **Archaeo** — encontrar una señal, confirmarla con catas, **establecer un campamento junto al yacimiento** y trabajarlo a lo largo de jornadas. No es un `/claim` de jugador.

Experiencia vanilla (expedición):

> 📡 Rastreador → 🔎 cata (kit de prospección) → ⛺ kit de establecimiento (campamento) → 📖 panel → ⛏️ jornadas → 🏺 estudio / museo.

Experiencia Archaeo (campaña):

> 📡 Rastreador → 🔎 cata → ⛺ kit de establecimiento (chunk + orientación) → 📖 panel y personal → ⛏️ excavación por golpes → hallazgos al registro de la excavación → interpretar / museo.

---

## Qué hace ya Minecraft (base que no hay que reinventar)

La arqueología vanilla (1.20 / Trails & Tales, vigente en 1.21) es un sistema pequeño y frágil:

| Pieza | Comportamiento |
| --- | --- |
| Pincel | Extrae el contenido de un bloque sospechoso (~4,8 s). |
| Arena / grava sospechosa | Solo dan botín si se **generaron de forma natural** (o si un plugin les asigna loot table). Si se rompen, caen o las mueve un pistón, el hallazgo se pierde. |
| Botín | Depende de la **estructura**, no del tipo de bloque. |
| Fragmentos de cerámica | 20 variantes; se combinan con ladrillos en vasija decorada. |
| Vasija decorada | Crafting + 1 slot de almacenamiento; se puede romper y recuperar piezas. |

Estructuras vanilla con arqueología:

| Estructura | Bloque | Lectura de contexto (vanilla) |
| --- | --- | --- |
| Pirámide del desierto | Arena sospechosa | Entierro / ofrenda / trampa |
| Fuente del desierto | Arena sospechosa | Uso ritual o cotidiano |
| Ruinas oceánicas cálidas | Arena sospechosa | Asentamiento costero; huevo de sniffer |
| Ruinas oceánicas frías | Grava sospechosa | Asentamiento costero |
| Ruinas perdidas (*trail ruins*) | Grava sospechosa | Poblado antiguo, caminos, adobe, disco *Relic*, moldes |

Lo que vanilla **no** da, y Archaeo sí debe dar:

- identidad persistente de un lugar o de un objeto
- descubrimiento frente a “ya está en el mapa”
- datación / estratos
- interpretación disputable
- archivo, reliquias y museos
- compatibilidad con lore de servidor y facciones

---

## Principios de diseño

1. **Mapa personalizado primero.** Archaeo debe respetar el terreno existente y añadir contexto, persistencia e interpretación alrededor de los puntos de excavación definidos por el staff.
2. **Descubrir, no consultar.** Las ruinas no se listan. Se buscan con un **rastreador** (pitidos y pulsos) y luego se prospectan. Nada de fragmentos de conocimiento ni libreta compartida.
3. **El plugin no escribe la historia oficial.** Ofrece indicios e interpretaciones posibles. La historia canónica del servidor la marcan los admins.
4. **Tres capas de verdad** (plugin / admin / jugador) coexisten y no se pisan.
5. **Facciones opcionales.** Pueden recibir **acceso a excavar** (lista del site). No son dueñas del yacimiento. El claim de terreno sigue siendo el plugin de facciones/claims.
6. **La fragilidad importa.** El terreno y los restos deben poder alterarse o perderse; Archaeo no debe convertir una excavación en un generador de objetos sin riesgo.
7. **Una excavación es un proyecto, no un loot ni un plot.** Se descubre, se confirma y se **establece en el mundo** (campamento en un chunk vecino). Queda **fija**. El campamento no se planta sobre el volumen excavable.
8. **La excavación es el mundo.** Hand Pick en el **prisma de estratos**; HUD mínimo (estrato, jornada, conservación si el hallazgo ya se detectó). No hay barra de fuerza, ni fracciones de bloque (`3/6`), ni minijuego en una interfaz. La gestión vive en el **panel del campamento**, no en comandos de jugador.
9. **Archaeo no es un `/claim` de jugador.** El chunk de **establecimiento** (campamento) se bloquea mientras la excavación esté activa. El chunk arqueológico **puede** protegerse entero (`establish.protect-dig-site`): todo el prisma, todas las bandas presentes, sin calcular si un bloque está al descubierto. Si está desactivado, el minado vanilla en el prisma hiere el sustrato. Los hallazgos son datos, no bloques en el mundo. El Hand Pick trabaja el relleno del prisma. Aire, agua y construcciones no se sustituyen por relleno. Eso no sustituye un claim de terreno.

---

## 1. Yacimientos — propuesta

Un **yacimiento** es un lugar persistente con identidad, no un chunk anónimo.

### Tipos

| Tipo | Origen | Cómo se “encuentra” |
| --- | --- | --- |
| **Vanilla** | Pirámide, fuente, ruinas oceánicas, ruinas perdidas | Fuera del alcance inicial: el mapa personalizado no las genera. |
| **Ruina administrada** | Chunk seleccionado por el staff en el mapa personalizado | Se crea con un comando que registra el chunk, el nivel de interés y los datos iniciales del lugar. Es el tipo de la primera versión. |
| **Campaña (excavación del jugador)** | El jugador la **establece** con el kit en un chunk **vecino** al yacimiento ya confirmado por cata | Proyecto persistente; campamento visible; prisma de estratos en el chunk arqueológico (§1c, §1d). |

Una ruina administrada pasa a **excavación** cuando alguien confirma con cata y **confirma la colocación del kit**. Las construcciones del mapa las ponen los moderadores; el comando de staff solo registra el chunk oculto.

### Descubrimiento

- El staff registra manualmente los chunks que contienen una ruina mediante un comando; el plugin no intenta descubrirlos automáticamente en el mapa personalizado.
- El comando permite asignar un nivel de interés inicial y, opcionalmente, nombre, tipo y descripción interna.
- La localización para jugadores es **rastreo + prospección**, no una brújula al chunk ni un árbol de pistas.
- Al **establecer** la excavación queda **registrada para siempre**: chunk arqueológico + chunk de establecimiento (no se borra ni se mueve a capricho).
- El que confirma el kit es el **director** inicial y puede nombrarla.
- Si no lo nombra, el sistema usa un nombre provisional (`Yacimiento del desierto #14`, coordenadas ofuscadas o bioma + rumbo).

Ficha mínima:

```
Excavación #027 — Ruinas del valle
Director: Alex
Estratos: I–III (IV no excavado)
Visibilidad: privada | invitación | pública
Estado: activa / agotada
```

### Registro administrativo — primera versión

El staff registra la ruina desde el chunk que quiere convertir en yacimiento.
Una sintaxis orientativa es:

```text
/archaeo ruin create <bajo|medio|alto|excepcional> [nombre]
/archaeo ruin info [nombre]
/archaeo ruin set-interest <bajo|medio|alto|excepcional>
/archaeo ruin delete [nombre]
```

El comando `create` guarda mundo, coordenadas del chunk, nivel de interés,
nombre, autor y fecha. El nombre puede omitirse para generar uno provisional.
También guarda en el dossier una lista de **hallazgos** (plantilla, forma conectada
de varias celdas, estrato). El terreno se ve normal hasta la jornada. No se
colocan bloques sospechosos visibles.
`info`, `set-interest` y `delete` requieren
permisos de administración; cambiar el interés de una ruina con campaña activa
debe estar restringido o dejar un registro explícito para no cambiar su dossier
retroactivamente.

### Cómo lo investiga el usuario

El jugador **no** usa comandos. Flujo: rastreador → zona sospechosa → **cata** (confirma) → **kit de establecimiento** (elige chunk vecino + orientación, confirma) → excavación. Detalle §1d.

Se elimina el sistema de fragmentos de conocimiento, la libreta compartida, la brújula al chunk y cualquier `/claim` de jugador para “quedarse” el yacimiento.

#### Rastreador — propuesta

Ítem de plugin (detector / radar). Al usarlo, emite **pitidos** cuya frecuencia
depende de la distancia al yacimiento **aún no reclamado** más cercano que esté
dentro de su radio de detección.

| Distancia | Señal |
| --- | --- |
| Muy lejos (o fuera de radio) | Silencio, o un pitido cada varios segundos si apenas entra en rango |
| Media | Pitidos más seguidos |
| Cerca | Pitidos rápidos |
| Muy cerca | Casi continuos |

Cada pitido puede ir con un **pulso visual** en el terreno (onda desde el
jugador, o sesgada hacia el rumbo aproximado). No marca el campamento ni las
coordenadas. El jugador tiene que **moverse** y probar direcciones: si se
aleja, los pitidos se espacian; si se acerca, se densifican.

```
📡 PIIP          onda débil
(avanza al norte)
📡 PIIP… PIIP…   ondas más frecuentes
📡 PIIP-PIIP-PIIP  ya entra en la zona de interés
```

Cuando está lo bastante cerca (config, p. ej. borde del chunk o unos bloques):

> Señal arqueológica detectada.
> Realiza una prospección para determinar la ubicación del yacimiento.

Ahí **aún no** hay excavación oficial. El rastreador solo dice “por aquí hay algo”.
Sigue la **cata** y, si se confirma, el **kit de establecimiento** (§1d).

**Alcance.** Radio según tamaño/interés (`detection-radius`). Sitios **ya establecidos** o agotados no llaman al rastreador: la excavación se vuelve a encontrar por el campamento, marcadores y límites temporales (§1d), no por el radar.

El objetivo: encontrar un yacimiento **es explorar**. Señal ambigua → interpretar
intensidad → caminar el mapa → acotar → prospectar.

#### Interés del chunk — propuesta

Cada ruina administrada tiene un nivel de interés fijado por el staff al crearla.
Ese nivel es la base de la riqueza de la campaña: cuanto mayor sea, más
artefactos, capas o contexto puede ofrecer. Incluso el nivel mínimo representa
un yacimiento válido; no existen chunks estériles dentro del sistema.

El comando acepta cuatro niveles narrativos, por ejemplo **bajo**, **medio**,
**alto** y **excepcional**. El nombre visible puede ser distinto del valor
interno y debe ser configurable. El nivel no tiene que ser una verdad matemática
del terreno: es una decisión de diseño y de lore que el staff puede usar para
equilibrar el mapa.

La cata **convierte una sospecha en yacimiento confirmado** y enseña las primeras
características (interés, indicios). No es un trámite vacío ni el acto de
reclamar: eso es **confirmar el kit de establecimiento**.

Al **establecer** la excavación, la riqueza efectiva se fija en el dossier.

La excavación queda **registrada a nombre del jugador** (director). Eso no es un
claim de facciones ni un `/claim` de terreno. El director dirige el proyecto;
facciones/claims siguen decidiendo el minado vanilla salvo, si se activa, la
protección del **chunk de establecimiento** y, si `protect-dig-site` está
activo, del **prisma de excavación** (todas las bandas).

---

## 1c. Cómo se lleva a cabo en Minecraft — propuesta

Esta sección baja el concepto a cosas que el jugador **ve y hace** con herramientas vanilla. Objetivo: claro, pocas GUIs, mucho mundo.

### Qué es un yacimiento para Archaeo

Un yacimiento es un **volumen persistente** en el plugin, no “cualquier bloque sospechoso”.

Datos mínimos:

- `id`
- mundo + chunk arqueológico (16×16) + **cota de referencia** (`datumY`) + bandas de Y de cada estrato
- chunk de **establecimiento** (campamento), distinto del volumen excavable
- tipo, nombre, descubridor / director
- dossier: estratos, presupuesto, indicios, estado

Un punto de excavación está “en el yacimiento” si cae **dentro del prisma**: misma planta que el chunk registrado y Y dentro de las bandas de estrato. Fuera de él el plugin no genera hallazgos de campaña. El campamento está **fuera** de ese prisma.

**Tamaño de campaña — un chunk (16×16), alineado a la cuadrícula de Minecraft.**

Un bloque ≈ un metro. En arqueología real las cuadrículas suelen ser de 1×1 m o 5×5 m; un chunk entero es ya una zanja grande (256 m²). No hace falta más suelo: el trabajo largo está en **bajar estratos**, no en comerse tres chunks.

| Tamaño | Por qué sí / no |
| --- | --- |
| Menos (8×8) | Corto de más; se acaba el solar como un sótano, no como un yacimiento. |
| **Un chunk (16×16)** | Encaja con mapas, Dynmap y facciones. El **prisma** = chunk de la ruina entre cota y última banda. El **campamento** = chunk vecino (§1d). |
| Más (2×2 chunks) | Es una cantera. Vaciarlo a pala deja de sentirse arqueología. |

El terreno **no** tiene que estar igualado y el plugin **no** allana ni abre una zanja al establecer. No hay una fase de “corte” construido. Lo que se fija al confirmar el kit es una **cota de referencia** y, a partir de ella, las bandas de estrato de la config (unos 3–5 bloques por estrato presente). Detalle más abajo y en §1d.

---

### Cota de referencia y prisma — propuesta

La Y del campamento **no** es la cota del yacimiento. El campamento está en un chunk vecino; un desnivel de varios bloques entre la mesa y la ruina es normal. Si las bandas colgaran de la tienda, el estrato I podría ser aire sobre un valle o tierra bajo un cerro.

**Al confirmar el kit**, una sola vez, el plugin calcula `datumY` sobre el **chunk arqueológico**:

1. En cada una de las 256 columnas, toma la Y de suelo (primer sólido de terreno; ignora hojas, nieve, hierba alta). El agua no cuenta como suelo: se usa el primer sólido no acuático (el lecho, no la lámina).
2. Se guarda la **mediana** de esas 256 cotas, no el máximo ni el mínimo, para que un árbol, un hoyo o un pilar no desplacen toda la estratigrafía.
3. Las bandas de la config son rangos absolutos a partir de ese valor (estrato I = `datumY` … `datumY − n`, y así sucesivamente).
4. Si `|datumY − Y de la mesa|` es enorme, puede quedar un aviso para staff; no se recalcula la cota con el campamento.

El jugador ve “profundidad respecto al suelo del yacimiento”, no respecto a la tienda. El chunk sigue siendo una decisión técnica; la interfaz habla de excavación, estrato y, más adelante, cuadro.

```
por encima de datumY     no hay hallazgos Archaeo; minería vanilla
  banda I                p. ej. datumY a datumY−4     más reciente
  banda II               datumY−5 a datumY−9
  banda III              datumY−10 a datumY−14
  banda IV               datumY−15 a datumY−19        (si el dossier dice que existe)
por debajo               fuera del prisma: minería vanilla, no salen restos Archaeo
```

En una columna más alta que la mediana sobra relleno por encima del datum; en una más baja el estrato I ya puede ser aire o agua. Eso es yacimiento irregular, no un error.

**Vallado del perímetro.** Opcional. En esta versión **se omite**: el campamento ya ancla el sitio. Las vallas de la plantilla del campamento son decoración del recinto, no el borde del prisma. Si más adelante se añade un vallado de yacimiento, no define qué bloques son excavables.

---

### Campamento

El hito en el mundo es el **campamento** que sale al confirmar el kit de
establecimiento: mesa de arqueología, tablón, cajas, quizá una carpa (§1d). El
jugador puede construir más alrededor. El plugin no exige un atril.

Clic en la **mesa o el tablón** = panel de la excavación. Ese chunk es el área
de establecimiento, **no** el solar excavable.

### Cómo lo investiga el usuario

Ver **§1** (rastreador) y **§1d** (cata + kit). Aquí solo el cálculo de la cata.

#### Interés del chunk — propuesta

En el mapa personalizado, Archaeo no intenta descubrir ruinas ni generar una
distribución automática. El staff decide qué chunks son excavables mediante un
comando y asigna a cada uno un nivel de interés: **bajo**, **medio**, **alto** o
**excepcional**. Todo chunk registrado es un yacimiento válido, incluso el de
interés mínimo.

El nivel de interés es una decisión de diseño y de lore, no una afirmación que
Bukkit tenga que deducir del terreno. Determina la riqueza base de la campaña:
cuanto mayor sea, más artefactos, estratos o contexto podrá ofrecer.

La cata no descubre si el chunk es válido: estima el nivel que el staff asignó y
puede mostrar una lectura aproximada. No se tienen en cuenta condiciones del
terreno ni datos del mundo:

| Factor | Cómo interviene |
| --- | --- |
| Nivel asignado por el staff | Es la base de la riqueza y garantiza que el chunk es un yacimiento válido. |
| Variación configurada | Hace que la lectura de una cata sea aproximada sin modificar el nivel real del yacimiento. |

Todos los chunks registrados parten del nivel asignado por el staff. La variación
solo modifica la lectura mostrada y no cambia la riqueza efectiva de la campaña.

#### Qué comprueba el plugin

La primera versión solo comprueba que el jugador está en una ruina registrada y
lee su nivel de interés. No inspecciona bioma, agua, relieve, altitud, semilla ni
estructuras para calcular riqueza. La variación de la cata se obtiene del nivel
configurado, por lo que el comportamiento es determinista, barato de probar y
fácil de explicar al jugador.

#### Cálculo de la cata

Cada nivel define una lectura base y un rango de variación. La cata selecciona
un valor dentro de ese rango usando una semilla estable y el punto de la cata:

```text
nivel = nivelRegistradoEnElChunk
base = config.interest-levels[nivel].base-wealth
variacion = config.interest-levels[nivel].variation
lectura = base + variacionEstable(seed, chunkX, chunkZ, puntoDeCata, variacion)
```

La variación solo afecta al mensaje que recibe el jugador. No cambia el nivel
registrado ni la riqueza definitiva. Al **establecer** la excavación, el plugin guarda
el dossier generado desde el nivel administrativo.

#### ¿Se guardan las coordenadas?

El plugin registra el chunk arqueológico al comando de staff. Tras establecer:
chunk de establecimiento, coords del campamento y dossier.

Sí se guardan:

- el chunk arqueológico, el chunk de establecimiento y las coords del campamento;
- el dossier al confirmar el kit;

#### Ejemplo de configuración

Los nombres son orientativos; lo importante es que el staff pueda ajustar el
ritmo sin editar código:

```yaml
tracker:
   enabled: true
   # radios en bloques; el más restrictivo entre esto y el de la ruina gana
   default-max-range: 256
   near-range: 48
   detect-message-range: 16
   pulse-particles: true
interest-levels:
   bajo:
      base-wealth: 1
      variation: 0
      detection-radius: 64
   medio:
      base-wealth: 3
      variation: 1
      detection-radius: 128
   alto:
      base-wealth: 6
      variation: 1
      detection-radius: 256
   excepcional:
      base-wealth: 10
      variation: 2
      detection-radius: 512
prospection:
   search-tool:
      enabled: true
```

La configuración define la riqueza base y la variación permitida para cada nivel.
No hay multiplicadores ambientales ni cálculos sobre el terreno. Una campaña ya
iniciada conserva el dossier que se generó al habilitarla.

La configuración no contiene una lista de chunks. Contiene radios del rastreador,
niveles, riqueza, variación, textos y reglas de la cata.
Las coordenadas y el nivel sí se guardan en el archivo de cada ruina registrada.
Si se cambia la configuración, los sitios ya habilitados conservan su dossier.

#### Repetir una cata — propuesta

El nivel registrado del chunk es estable y común para todos. La lectura de una
cata, en cambio, no tiene por qué ser una respuesta exacta y repetible:

- El punto exacto y el momento de la cata pueden producir una lectura parcial o
   ambigua.
- Dos jugadores pueden recibir estimaciones ligeramente distintas, pero nunca
   una lectura inferior al interés mínimo registrado.
- Repetir la cata sirve para comparar impresiones antes de invertir en el
   campamento; no modifica el nivel ni genera una recompensa.
- La herramienta debe tener durabilidad, tiempo de uso o un pequeño coste para
   que hacer clic repetidamente en el mismo bloque no sea la estrategia óptima.

La variación de la lectura puede ser efímera. El dato persistente del staff es
el comando de ruina; el de jugador empieza al **confirmar** catas y **establecer**.

#### Cata de tierra

**No** es pala/pincel genéricos. Es el **kit de prospección arqueológica**.

Se usa sobre **varios puntos** del terreno (unos segundos cada uno). Informa y
**descubre**; no reclama el chunk.

| Resultado | Qué significa |
| --- | --- |
| No se han encontrado indicios suficientes | Seguir catando u otro punto |
| Indicios débiles de actividad humana | Hay algo; aún no basta para establecer |
| Posible yacimiento | Cerca de confirmar |
| Yacimiento arqueológico confirmado | Ya se puede usar el kit de establecimiento |

Ejemplos: *Muestra de tierra analizada. Se han detectado restos de actividad
humana.* / *Yacimiento arqueológico confirmado.*

**¿Obligatoria?** Sí para **establecer** la excavación. No para saber que hay
algo: eso lo hizo el rastreador.

La lectura usa el interés del staff + variación. Nunca inventa un yacimiento
donde el staff no registró chunk.

Las estructuras vanilla quedan fuera del alcance inicial.

---

## 1d. Establecer excavación, panel y acceso — propuesta

No hay comando de jugador. Tras **yacimiento confirmado** por cata, el jugador
usa un **kit de establecimiento** (palo / `STICK` personalizado en la primera
versión). No representa una estaca clavada: es la herramienta de replanteo.

Mientras el yacimiento **no** esté reclamado, cualquiera puede detectarlo con el
radar, prospectarlo y establecerlo. No hay excavación activa ni dueño Archaeo.
Al confirmar la colocación del kit, el yacimiento **pasa a su nombre** en un
solo gesto: no hay un segundo comando de “reclamar”.

```
📡 Rastreador
    ↓
🔎 Catas → yacimiento confirmado
    ↓
⛺ Kit de establecimiento
    ↓
Elegir chunk vecino + orientación
    ↓
Confirmar colocación
    ↓
🏺 Yacimiento reclamado · excavación creada
```

> Has establecido una excavación arqueológica.

```
Excavación #027
Ruinas del valle
Descubierta por: Alex
Director (Archaeo): Alex
```

El que confirma el kit es el **director** inicial. Controla quién trabaja la
excavación. **Permanencia:** no se borra ni se mueve a capricho. Staff puede
intervenir.

### Tres piezas

| Concepto | Qué es | Dónde |
| --- | --- | --- |
| **Yacimiento** | Zona con evidencias; dónde se puede excavar | Chunk + `datumY` + bandas (§1c) |
| **Excavación** | Ese yacimiento reclamado: progreso, hallazgos, permisos | Datos persistentes (`sites/`) |
| **Área de establecimiento** | Sitio del campamento; no se excava | Un **chunk distinto**, fuera del prisma |

```
        ÁREA DE ESTABLECIMIENTO
   ┌───────────────────────────┐
   │       ⛺ 📦 🏺 📦         │
   └─────────────┬─────────────┘
                 │
   ┌─────────────┴─────────────┐
   │    ÁREA ARQUEOLÓGICA      │
   │           ⛏️              │
   └───────────────────────────┘
```

El campamento **no** ocupa la superficie excavable.

### Kit y plantilla

El kit lleva una **plantilla de campamento** (ejemplo: campamento básico) con
piezas y posiciones relativas:

```
Campamento básico

   ⛺
📦 🏺 📦
🚧   🚧
```

Mesa, cajas, tablón, carpa, etc. El jugador puede construir más alrededor
después. Las vallas de la plantilla son el recinto del **campamento**, no el
perímetro del prisma; el vallado del yacimiento se omite por ahora. Construir
el campamento **no** gasta la jornada (§2).

### Chunk válido y previsualización

Al usar el kit, el jugador elige un **chunk completo** alrededor del área
arqueológica, no una coordenada suelta. Los chunks válidos se marcan en el
mundo. Tienen que quedar **fuera** del terreno que se va a excavar.

Al apuntar a un chunk válido, el plugin muestra una **previsualización** de la
plantilla, adaptada a ese chunk. La **orientación** sigue la mirada del
jugador:

```
Orientación A              Orientación B

      ⛺                      📦
   📦 🏺 📦                 📦 🏺 ⛺
      🚧
```

El jugador se mueve y gira hasta que chunk + orientación encajen en el terreno.
La preview debe delatar problemas **antes** de confirmar:

- terreno insuficiente, agua, bloques incompatibles;
- construcciones existentes;
- conflicto con claims;
- piezas de la plantilla fuera del chunk elegido.

Si no es válido: indicación visual y **no** se puede confirmar.

### Al confirmar

En el mismo instante:

- el yacimiento queda registrado a su nombre;
- el jugador es director;
- nace la excavación (dossier, riqueza fijada);
- se guarda el chunk de establecimiento;
- se calcula y guarda `datumY` (mediana del suelo del chunk arqueológico);
- se generan las bandas de estrato a partir de esa cota y del dossier;
- se genera el campamento de la plantilla;
- el chunk de establecimiento (campamento) queda bloqueado;
- el prisma de excavación **puede** protegerse entero (`establish.protect-dig-site`);
- nadie más puede reclamar ese yacimiento;
- el radar deja de usarse para localizarlo.

### Cómo se vuelve a encontrar

Tras marcharse días, sin campamento “custom” o sin recordar coords, el radar
ya no es el medio principal. Sirven:

- el **campamento** (referencia física permanente);
- **marcadores** visuales propios de la excavación;
- **límites temporales** del prisma (opcional).

Los bordes del prisma **no** tienen que estar siempre visibles y **no** hay
vallado obligatorio. Si el dueño o un autorizado está cerca y mira hacia el
yacimiento, el plugin puede mostrarlos un rato (partículas, líneas, bloques
fantasma u otro sistema). Se ocultan al dejar de mirar o al alejarse. Desde el
panel del campamento: **«Mostrar límites»** para forzar esa vista. El Hand Pick
puede hacer de herramienta contextual (HUD de excavación / estrato al
equiparlo dentro del prisma; aviso al apuntar fuera).

```
       ✨──────────✨
      /              \
     /                \
    ✨   EXCAVACIÓN   ✨
     \                /
      \______/
```

### Principio

Debe sentirse como **instalar una excavación en el mundo**, no como un comando
de protección. Encontrar → confirmar (cata) → elegir emplazamiento → colocar →
excavar. El radar queda para **yacimientos nuevos**; las excavaciones propias
se reencuentran por campamento, marcadores y límites.

### Panel

Clic en mesa, tablón o campamento:

```
RUINAS DEL VALLE
Director: Alex
Estrato actual: III · 700–900 años
Progreso: ██████░░░░
Hallazgos: 7 · Evidencias: 12
[EXCAVAR]  [PERSONAL]  [INFORMACIÓN]  [Mostrar límites]
```

**EXCAVAR** no es un menú: el trabajo es en el prisma con HUD (§2).
**PERSONAL** y **INFORMACIÓN** sí abren gestión.

### Personal (v1: puede excavar sí/no)

El director añade jugadores. Roles más adelante si hacen falta: Director,
Arqueólogo, Excavador, Visitante.

### Facciones

**Añadir facción** además de jugador. Archaeo pregunta al otro plugin los
miembros. Una facción autorizada trabaja como proyecto colectivo.

### Sin permiso

*No tienes autorización para trabajar en esta excavación.* No hay jornada ni
hallazgos. Si `protect-dig-site` está activo, el relleno de **todas** las bandas
del prisma tampoco se retira con pico vanilla. Si está apagado, un túnel
vanilla hiere el sustrato. El campamento queda bloqueado por el establecimiento.
Claims y facciones siguen decidiendo el terreno alrededor.

### Visibilidad

| Estado | Quién excava |
| --- | --- |
| **Privada** | Director + autorizados |
| **Por invitación** | Se puede solicitar acceso |
| **Pública** | Cualquiera con permiso de excavación |

### Secuencia

1. Rastreador → zona sospechosa (yacimiento no reclamado).
2. Catas → confirmado; aún sin dueño.
3. Kit → preview en chunk vecino + orientación.
4. Confirmar → campamento, director, dossier, yacimiento reclamado.
5. Formas ocultas en el **chunk arqueológico** (§2).

---

### Cómo se procesan los puntos de excavación (código)

Los hallazgos se sortean al **establecer** (plantilla + forma conexa **en un
solo Y** dentro de una banda). El terreno no cambia hasta la jornada. **§2**.

Minecraft **no** tiene estratos arqueológicos. Césped sobre tierra sobre piedra es geología tosca. La arcilla, la grava y el barro salen en **manchas**, no en capas continuas. **No** vamos a rellenar el chunk como un sándwich de arcilla ni a preguntar “¿el último bloque era grava?”.

El estrato lo define el plugin, al establecer, como **banda de Y absoluta** respecto a `datumY` (mediana del suelo del chunk arqueológico; §1c). No es “N bloques bajo el césped de esta columna”:

```
por encima de datumY     fuera del prisma (vanilla)
  banda I                p. ej. datumY … datumY−4
  banda II               …
por debajo de la última  fuera del prisma (vanilla)
```

Da igual que en un rincón haya piedra a `datumY−3` y en otro tierra a `datumY−12`. Si el bloque está en esa banda de Y, es esa capa. El Hand Pick retira relleno y es la herramienta de campo (§2).

Al realizar una acción de excavación válida dentro de un yacimiento en campaña,
Archaeo calcula procedencia, capa y contexto, gasta una acción de la jornada y
aplica el presupuesto de artefactos.

### Cómo conoce el usuario las capas

No depende del último bloque ni de “modo estrato II”. Puedes abrir un pozo a la banda III y luego desbrozar la I (mala praxis real; el hallazgo hondo puede marcarse *secuencia invertida*). Cada hallazgo mira **la Y de la celda trabajada**.

**Mientras está en el prisma de una excavación establecida**, un HUD mínimo muestra el estrato y las acciones de hoy (§2). El **panel del campamento** es la ficha del proyecto, no el GPS.

---

### Cadena de un hallazgo (qué se hace de verdad, qué hay en Minecraft)

En laboratorio real, por cada día de campo suele haber **varios** de mesa. Lo importante no es el agua: es **no perder la procedencia** (de qué cuadro y qué capa salió) y tratar cada material distinto.

Qué hacen de verdad (resumido):

| Paso real | Detalle |
| --- | --- |
| Registrar en el corte | Foto, bolsa etiquetada, mismo lote = mismo sitio/capa. *Whatever you do, don’t lose provenience.* |
| Secar / no mezclar lotes | Una procedencia cada vez. |
| Limpiar según material | **Cerámica, vidrio, mucha piedra:** agua y cepillo suave. **Metales, carbón, hueso frágil, tejidos, cerámica pintada/cruda:** **no se lavan**; se cepillan en seco o se dejan. |
| Clasificar | Montones por material (cerámica, metal, hueso…). |
| Inventariar | Número de catálogo, descripción, base de datos. |
| Conservar | Estabilizar (sobre todo hierro), a veces consolidar o reconstruir una vasija. Mínima intervención. |
| Interpretar / exponer | Informe y, si toca, vitrina. |

El caldero con agua **sí existe** en arqueología, pero **solo para algunos materiales**. Trasladarlo a “todo se lava en el caldero” sería falso y, de paso, aburrido (un único clic).

Traducción a Minecraft: cada artefacto pertenece a un **material** y cada
material define una cadena de tratamientos obligatorios antes de interpretar o
exponer la pieza. El artefacto no guarda una cadena propia: guarda su material,
su tratamiento actual y los pasos ya completados.

| Material | Tratamiento de ejemplo | Secuencia |
| --- | --- | --- |
| Cerámica | limpieza, lavado, secado, fotografiado, dibujo | limpiar → lavar → secar → fotografiar → dibujar → interpretar → exponer |
| Metal | limpieza en seco, estabilización, fotografiado | limpiar → estabilizar → fotografiar → interpretar → exponer |
| Hueso | limpieza suave, secado, fotografiado | limpiar → secar → fotografiar → interpretar → exponer |
| Papel o tela | secado, conservación, fotografiado | secar → conservar → fotografiar → interpretar → exponer |

Los materiales y sus secuencias son configurables en `materials.yml`. Cada
procedimiento corresponde a una mecánica del plugin: usar pincel o mesa para
limpiar, caldero para lavar, soporte para secar, cámara para fotografiar y mesa
de dibujo para dibujar. La primera versión puede implementar pocas mecánicas y
dejar las demás como pasos bloqueados o futuros, sin cambiar el modelo.

El objeto siempre muestra estado visible para el jugador, por ejemplo:

```text
Material: cerámica
Estado: lavado
Siguiente paso: secar
Progreso: 2/5 procedimientos
```

El plugin rechaza procedimientos fuera de orden o incompatibles con el material.
Cuando se completan todos los pasos previos, se habilitan interpretación,
catalogación y exposición. La procedencia se conserva en el PDC durante toda la
cadena.

Los gestos dependen del material: el jugador siempre recibe una indicación clara
del siguiente procedimiento y no tiene que memorizar la cadena.

Sin catalogar: puedes guardarlo en un cofre (la bolsa de campo). El museo no enseña ficha completa.

---

### Metadatos: disco = yacimientos; pieza = ficha

Acuerdo de persistencia:

- **`sites/`** — sí. Solar, campamento, dossier, hallazgos (formas + estado), jornada, personal, visibilidad, contadores del panel.
- **Ítem (PDC)** — sí. Ficha de la pieza recuperada. Si se pierde, se perdió (el panel puede seguir el recuento).
- **`knowledge/`, `finds/`, `players/`, `museums/`** — no.

El JSON del site guarda cada hallazgo **aún en el corte** (plantilla, celdas,
estado). Al recuperar: ítem con PDC, el hallazgo sale del corte, sube el
contador del panel (y un nombre corto en el registro de la excavación). Eso no
es un archivo `finds/` global.

#### Qué va en el ítem (PDC + lore)

| Dato |
| --- |
| `siteId`, nombre del yacimiento |
| capa / antigüedad |
| descubridor, fecha |
| estado de laboratorio |
| conservación al recuperar (calidad %; visible en la pieza; dos copias del mismo template pueden valer distinto) |
| indicios copiados al catalogar |
| interpretación(es) |
| nombre de reliquia, si la hay |
| material, procedimiento actual y completados |
| tamaño/plantilla del hallazgo (opcional, lore) |

#### Qué va en `sites/<id>.yml` o `.json`

| Dato |
| --- |
| mundo, chunk arqueológico, `datumY`, chunk de establecimiento, coords del campamento (mesa/tablón) |
| tipo, nombre, nº de excavación, director, fecha |
| visibilidad, jugadores y facciones con permiso de excavar |
| riqueza, indicios, radio de detección |
| hallazgos en corte (formas, exposición, conservación, heridas por celda) + contadores recuperados / evidencias |
| daño de relleno solo donde hace falta varios pases (sobre todo celdas de hallazgo); la tierra vacía de un ciclo no se persiste |
| por capa: ¿existe?, banda de Y, revuelto/ausente |
| jornada actual: Hand Pick restantes, id de día de mundo |
| estado activo / agotado |

`config.yml`, `hints.yml`, `interpretations.yml`, `materials.yml`, `finds.yml`
(plantillas de forma), radios del rastreador. No es partida.

### Riqueza: interés configurado

Al registrar la ruina, el staff asigna su nivel de interés. Ese nivel determina
directamente el presupuesto de artefactos y el mínimo de hallazgos de contexto;
no se recalcula después según el terreno ni mediante factores ocultos.

Todo yacimiento de **campaña** garantiza:

- un **mínimo de reliquias** (p. ej. 1, 2 si rico)
- un mínimo de hallazgos de contexto (cerámica, carbón, clavos…), para que el sitio “cuente algo” aunque no sea un tesoro

Lo que varía con el interés: **cuántos** hallazgos extra, si hay estrato IV y si
hay una reliquia especialmente rara. El jugador lo nota porque el yacimiento da
para más sesiones o se queda corto.

Expedición vanilla: no se garantiza reliquia de plugin; el botín es el de Mojang. Catalogar un fragmento vanilla **puede** convertirlo en reliquia si el jugador lo restaura y nombra (acto deliberado), con límites para no relicar cada palo.

### Indicios → interpretación (config YAML)

Sí: **todo el catálogo sale de YAML** (`hints.yml` + `interpretations.yml`, o secciones en `config.yml`). El plugin no lleva textos de historia hardcodeados. El staff puede añadir, quitar, traducir, o ligarlos a lore del servidor.

Hay dos listas distintas:

1. **Indicios del yacimiento** — al establecer (2–4). Van al panel. Al identificar en la **mesa del campamento** se copian al PDC.
2. **Interpretaciones** — las elige el jugador sobre esa pieza. También al PDC. El plugin no dice cuál es correcta.
3. **Artefactos o reliquias** - artefactos.yml para configurar objetos predefinidos? podrían venir con interpretaciones prestablecidas o dejarse en blanco y que las seleccione el jugador de forma estándar a partir de la config de interpretaciones. En la config de interpretaciones también se podría vincular la interpretación a objectos de artifacts, así al descubrir ese objeto solo se sugerirían esas interpretaciones. Al artefacto también se le podría configurar ya el sustrato al que pertenece, podría ser uno o varios.
4. **Sustratos o capas** - se podrían configurar también, con su nombre, su época, y orden de profundidad para saber cuáles van por encima de cuáles. A un sustrato se podrían asignar los artefacts que es posible encontrar en ese sustrato. Faltaría definir qué hacer con las reliquias de minecraft vanilla, tendría sentido que sigan apareciendo y que si se quiere personalizar su información que se incluyan los id de los objetos que pueden encontrarse como reliquia en la config. Hay evento para saber cuándo un jugador descubre reliquia en minecraft vanilla? No parece, habría que ver cómo podemos saber cuándo el usuario obtiene un artefacto de forma vanilla.
5. **Datos ocultos**: El jugador que identifique el artefacto podría escoger si quiere que el descubridor del artefacto sea o no anónimo, o si quiere que se revele o no el yacimiento donde se obtuvo.

Los artefactos también podrían tener rareza. Se podría configurar desde la config de artefactos. Según la rareza es más o menos difícil que aparezcan y además sería parte de la información a displayear del objeto.

#### De qué dependen los indicios al generar el sitio

Se filtra el pool del YAML con etiquetas del dossier:

| Filtro | Ejemplo |
| --- | --- |
| Bioma | desierto → recipientes y arena; taiga → carbón, madera, adobe |
| Estructura vanilla | pirámide, ruinas perdidas, océano, ninguna (campaña en campo) |
| Capas presentes | si no hay IV, no salen hints de “cota muy honda” |
| Familia de loot del dossier | cerámica, metal, hueso, semilla, fuego… |
| Riqueza | pobre = menos indicios o más “función desconocida” |
| Azar + peso | para que dos valles no sean clones |

Un indicio es una frase **genérica** + tags internos (el jugador no ve los tags). Esos tags **sugieren** interpretaciones en la mesa (aparecen primero o marcadas), pero **no bloquean** el resto.

#### Catálogo por defecto (indicios)

Textos de ejemplo (editables):

| id | Texto | Tags típicos | Suele salir si… |
| --- | --- | --- | --- |
| `fire_multi` | Restos de fuego o carbón en más de una profundidad. | fuego, doméstico | loot con carbón / varias capas |
| `pots_metal` | Fragmentos de recipientes junto a metal. | cerámica, metal | ambas familias en el dossier |
| `clustered` | Los restos aparecen agrupados, no dispersos. | depósito, ceremonial | riqueza media+ o tag depósito |
| `scattered` | Los restos están muy dispersos en la cota. | abandono, asentamiento | sitio pobre / revuelto |
| `mixed_layer` | Una profundidad está mezclada respecto a las otras. | revuelto | esa capa marcada revuelta |
| `bone` | Hay más hueso que utensilio. | enterramiento, comida | tag hueso |
| `seed_grain` | Semillas o grano junto a la tierra. | asentamiento, comida | trigo, semillas |
| `blade` | Filo o arma, poco ajuar doméstico. | conflicto | armas en el dossier |
| `ornament` | Piezas pequeñas de adorno. | ceremonial, comercio | cuentas, oro, tintes |
| `trade` | Materiales que no encajan con el bioma. | comercio | p. ej. arcilla en desierto |
| `empty_iv` | La cota más honda no se conserva. | erosión | estrato IV ausente |
| `recent_interrupt` | La capa de arriba corta a las de abajo. | abandono, disturbado | I presente y III+ también |
| `unknown` | El conjunto no sugiere un uso claro. | desconocido | relleno / sitios pobres |

#### Catálogo por defecto (interpretaciones del jugador)

Las mismas de siempre, también YAML: conflicto armado, actividad comercial, uso ceremonial, asentamiento, abandono, enterramiento, depósito deliberado, función desconocida. Cada una puede listar `suggested_by: [blade, trade, …]`.

En la mesa:

1. Se muestran los **indicios del site** (solo lectura).
2. El jugador marca 1–2 interpretaciones + confianza.
3. Eso se escribe en el ítem. La ficha del museo (clic en el marco) enseña: indicios + “según Alex: posible conflicto, confianza media”.

---

### Museos

No hay ficha de museo en disco. El jugador construye un edificio y cuelga marcos. Clic en una pieza **catalogada** = menú con el PDC. Si no es de Archaeo, el marco es vanilla.

---

## 2. Excavar — oír y soltar — acordado

La excavación de **campaña** no es una GUI, ni un cooldown por clic, ni una
barra de fuerza, ni un contador tipo `3/6` en la cara del jugador. Se activa
en el **prisma de estratos** de una excavación **establecida**. Solo quien
tiene permiso arqueológico usa el Hand Pick; si no: *No tienes autorización
para trabajar en esta excavación.* Fuera del prisma, Minecraft normal.

Inspiración: el Subsuelo de Pokémon — no ves los hallazgos hasta que retiras
material. El núcleo es **oír y decidir cuándo soltar**, no cargar fuerza ni
llegar a un número de golpes.

No se allana el terreno al establecer. El jugador baja el relleno con el
Hand Pick, ciclo a ciclo.

### Objetivo de diseño

Debe sentirse como Minecraft con otra regla de rotura, no como un minijuego
aparte. Conserva animación del brazo, bloques reales y sonidos reconocibles.
Cada cubo —tenga hallazgo o no— pide un mínimo de atención. El jugador
entiende la regla **con el oído** (y, si no hay sonido, con el **mismo instante**
en partículas + subtítulo), no con un HUD de etapas ni con las grietas
vanilla del bloque.

Dos señales, dos significados, **siempre**:

| Señal | Significado |
| --- | --- |
| **Cling** (timbre de hallazgo: cerámica, metal, “no es tierra”) | Esto no es solo relleno. **Para.** El cubo no se va. |
| **Ting de listo** (chime más fuerte, al final del ciclo en relleno vacío) | **Este cubo puede salir.** En tierra vacía, suelta ahora. |

El mismo sonido no puede a veces romper y a veces no. El cling de hallazgo y
el ting de listo no se confunden.

En **relleno vacío**, el ting de listo va **precedido** de **1, 2 o 3** clings
suaves (misma familia, más bajos). Cuando oyes el primero, sabes que viene el
de romper; no sabes en cuántos tiempos. El recuento se tira **por hold**.

### Ciclo de picado (Hand Pick)

1. Equipar el Hand Pick; apuntar a la cara de trabajo.
2. Mantener **izquierdo**. El plugin impide la rotura vanilla. **No** se
   avanza el overlay de grietas (ni vanilla ni del plugin): la textura no
   adelanta el ritmo. Al soltar no hay “estado de rotura” que recordar.
3. Cada pista (Soon / Release) cae cuando **vanilla habría roto** ese cubo
   (`getBreakSpeed` hasta `1.0`), con las grietas congeladas. Pico vs pala vs
   dureza del bloque (y stats ya puestas en el ítem por MMOItems, etc.) marcan
   el tempo. YAML `mining-speed` / `mining-speed-multiplier` pueden sustituir
   esa velocidad **solo al muestrear el reloj** (vale para vanilla y para lo
   que MMOItems/ItemsAdder ya hayan puesto en el ítem o el jugador).
   Tras el chime de Release, `release-window-ticks` es el margen para soltar
   a tiempo.
4. Al soltar, el plugin aplica **antes / a tiempo / tarde** según las
   señales de ese hold.

**Relleno vacío** (no hay hallazgo en esa celda):

| Cuándo sueltas | Qué pasa |
| --- | --- |
| **Antes** del ting | El cubo sigue. No hace falta guardar `3/6` en disco: el siguiente hold empieza de cero. |
| **En el ting** | Salen `lift-on-ready` cubos con la `break-shape` (siempre el apuntado primero). |
| **Después** del ting (te pasas) | La misma forma, `lift-if-late` cubos. `down` = pozo; `around` = 3×3 esta capa y la de debajo; `random` = baraja ese 3×3×2 (el apuntado sigue primero). |

**Hay hallazgo** en esa celda (o el de abajo es hallazgo):

- El **cling** suena **antes** que el ting de listo. Mensaje: *Material arqueológico detectado. Extensión desconocida.* El cubo **no** se retira. Hace falta **más de un ciclo** en el mismo bloque para que *pudiera* llegar el ting de listo (etapas internas mayores, config). Así cling y ting no coinciden.
- El cling **no** resta conservación: avisa.
- Si **ignoras** el cling y sigues hasta el ting —o sueltas tarde y atraviesas desde el cubo de encima— **sí** heridas la pieza (§ conservación).
- El pico **no** suelta el ítem. Extraer es un paso posterior (cuando la forma esté bastante despejada).

Relleno **blando** (tierra, arena, grava) vs **compacto** (piedra): distinto
tiempo/etapas internas hasta el ting, mismo significado de las señales.
No hace falta una tabla por cada `Material` de Bukkit.

La velocidad de las pistas **sí** sigue el minado vanilla (herramienta ×
bloque × haste / eficiencia). Lo que YAML guarda es lo que vanilla no sabe:
cuántos cubos salen a tiempo o tarde, `break-shape` (pozo o área),
`release-window-ticks`, `workday-cost`.

### Rotura de bloques — prisma

Los hallazgos son datos (formas de celdas), no bloques sospechosos.

Si `establish.protect-dig-site` está **activo**, se protege el **prisma entero**
(todas las bandas presentes del chunk arqueológico). No se calcula si un
bloque tiene cara al aire. Tablones, cobble de obra, máquinas, aire y agua
no son sustrato y no entran en esa protección.

Ahí se cancela la rotura vanilla (fuego / explosiones / pistones igual).
Pico vanilla: *Usa una herramienta de excavación.* El Hand Pick sigue
retirando relleno con su reloj.

Si la protección está **apagada**, el minado vanilla en el prisma está
permitido y hiere el dossier (capa revuelta; hallazgos de esa celda dañados).

| Zona | Rotura vanilla (`protect-dig-site: true`) | Efecto Archaeo |
| --- | --- | --- |
| Por encima de `datumY` | Permitida | Sin hallazgos |
| Prisma (cualquier banda, cubierto o al descubierto) | Solo Hand Pick | El corte |
| Aire, agua, construcciones | Permitida | No se convierten en relleno |
| Por debajo de la última banda | Permitida | Fuera del yacimiento |

Al **confirmar** el kit, las celdas de hallazgo que ya no son terreno se
marcan dañadas. El claim no se rechaza.

El Hand Pick actúa sobre relleno del prisma. No pisa agua ni construcciones.

### HUD mínimo

Con el Hand Pick en el prisma: estrato de la Y actual y acciones de **hoy**.
**No** barra de fuerza. **No** `2/6` ni “strikes” del relleno.

```
ESTRATO III · 700–900 años
⛏️ 5
```

Si el cubo apuntado es un hallazgo **ya detectado**, se añade la
conservación: `92 %`. Dos vasijas iguales pueden valer distinto al recuperar.

Si se mira un **hueco ya abierto** (aire del prisma, o la pared a través de
ese aire), el HUD añade las trazas vecinas: `Ceramic: 1 · Bone: 1` o
`clear`. El relleno aún no abierto no enseña número.

Al apuntar fuera del prisma: *Fuera del área arqueológica* (cooldown).

Avisos cortos cuando hacen falta:

- *Traces of Ceramic: 1 · Bone: 1* (trazas del hueco recién abierto)
- *Material arqueológico detectado. Extensión desconocida.*
- *El material arqueológico puede estar siendo alterado.*
- *La evidencia ha resultado dañada.*
- *Esos restos se han destruido. No se puede recuperar nada de ellos.*
- *La jornada de excavación ha terminado.*
- Más adelante: forma, *Hallazgo descubierto*. *Hallazgo recuperado* al pincelar.

### Herramientas

**Campo:** picos y palas de la whitelist; **pincel** para extraer un hallazgo
ya descubierto. Blando vs compacto (y pico vs pala) cambia el tiempo hasta
el ting por velocidad vanilla, no por un timer YAML.

**Maza / martillo en área:** otro verbo (volumen a cambio de control). No
es el flujo por defecto. Si se hace más adelante: cara en jornada, cualquier
hallazgo en el volumen se trata como fallo (aviso tarde o conservación
abajo). No es un pico 3×3 gratuito.

El Hand Pick es ítem de plugin (PDC). Fuera del corte no sustituye al pico
vanilla.

### Estado interno del relleno

El jugador no ve etapas. Por dentro:

- Tierra vacía: un hold a tiempo basta; **no** persistir `fill-damage` al
  soltar pronto.
- Celdas de hallazgo (varios pases): sí puede guardarse progreso oculto
  entre ciclos para que el ting no llegue en el primer cling.

Las grietas vanilla **no** se usan como pista. El overlay se mantiene a cero
mientras el Hand Pick pica.

### Sonidos

Los golpes de mientras: tierra/grava vs piedra (lectura de blando/compacto).

| Señal | Lectura |
| --- | --- |
| Hits de relleno | Sigue trabajando |
| **Cling suave** (relleno vacío) | Viene el ting; aún no sueltes |
| **Ting de listo** | Este cubo puede salir (en vacío: suelta) |
| **Cling** de hallazgo (otro timbre) | Para; no es tierra |

Cada cling tiene un **gemelo visual** (accesibilidad, `pick.visual-cues`):
polvo sobre el cubo y un subtítulo de un verbo, **sin números**. *Soon* /
*Release* / *Stop* / *Altering*. El action bar sigue siendo estrato y jornada.
No se usan grietas ni `2/6`.

### Hallazgos: forma, no un bloque-premio

Un hallazgo es un conjunto de **celdas conectadas en la misma altura** (un
plano XZ) dentro de una banda de estrato. Varios hallazgos por yacimiento, sin
solaparse. Distintos hallazgos pueden estar en Y distintos; uno solo no se
apila.

```
⬜ ⬜ ⬜
⬜ 🏺 ⬜
⬜ ⬜ ⬜
```

Tamaños orientativos (`finds.yml`):

| Plantilla | Bloques (aprox.) |
| --- | --- |
| Moneda | 1 |
| Fragmento de cerámica | 1 |
| Herramienta | 2–3 |
| Espada | 3–5 |
| Vasija | 3–6 |
| Enterramiento | 8–15 |
| Estructura | 20–50 |

**Trazas vecinas (buscaminas)** — acordado:

Al retirar un cubo de relleno con el Hand Pick, el plugin mira las **seis
caras** del cubo apuntado. Cada vecino que sigue siendo relleno y forma
parte de un hallazgo vivo (no perdido ni recuperado) suma **un cubo** a su
material de catálogo (`artifacts.yml` / `materials.yml`). El recuento es de
cubos, no de piezas: dos artefactos distintos (cerámica y hueso) que tocan
el hueco con una celda cada uno se leen:

```
Traces of Ceramic: 1 · Bone: 1
```

Si tres celdas de la misma vasija tocan el hueco: `Ceramic: 3`. Sin trazas
no hay chat (el corte está limpio). El mismo recuento se puede releer en el
HUD al apuntar al aire. Sirve para decidir si el siguiente golpe puede ser
una herramienta más rápida (hueco `clear` o lejos del material frágil) o
hay que frenar.

Las diagonales no cuentan: si no comparten cara, el hallazgo no gotea hacia
ese hueco y no aparece en las trazas.

**Exposición** (por hallazgo):

| Estado | Qué sabe el jugador |
| --- | --- |
| **Oculto** | Nada. Terreno normal. |
| **Parcialmente expuesto** | Cling en al menos una celda. Las celdas con cara al aire **gotean partículas**; el bloque no cambia. Extensión desconocida. |
| **Descubierto** | Toda la forma restante tiene cara al aire (mismo goteo). El **pincel** puede recuperar. |
| **Recuperado** | La pieza está fuera del corte (ítem con PDC). |

El pico no dropea la pieza. Con la forma **descubierta**, clic derecho con el
pincel (`items.brush`) sobre un cubo que aún gotea. La barra
(`excavation.brush.hold-ticks`, por defecto 2 s) es **por cubo**: si miras a
otro lado se pausa; si vuelves a mirar ese cubo con el pincel, se restaura
en el mismo punto. Ese cubo deja de emitir partículas. Tras
`recovery.max-cells-to-clean` cubos distintos (o todos si hay menos), las
celdas restantes pasan a aire y **cae un ítem** con conservación y
procedencia. Conservación 0: sin ítem. No gasta jornada. En cualquier
bloque que **no** sea celda de hallazgo el pincel vanilla no se cancela.

### Conservación (acordado)

Una cifra **0–100 % por hallazgo**, no por cubo. Se ve en el ítem al
recuperar: mismo template, distinto valor.

El objeto se reparte en sus celdas. Un hallazgo de **4 bloques** → cada
celda es el **25 %**. Si esa celda pide **dos** acciones de pico y fallas
la primera pero aciertas la segunda: solo la mitad de esa celda → **−12,5 %**
(~87,5 % restante). Si fallas **todas** las acciones de **una** de las
cuatro celdas → **−25 %**.

En general: \(n\) celdas × \(a\) acciones por celda de hallazgo → cada
**fallo** cuesta \(100 / (n \times a)\). El cling bien escuchado no resta.

Qué cuenta como fallo (ignorar el aviso, no el cling en sí):

| Qué hiciste | Cómo pesa |
| --- | --- |
| Tarde en el cubo **de encima** (rompe el de abajo si es celda del hallazgo) | Esa celda se gasta (rotura visual) |
| Ting de listo / retirar el cubo **de una celda del hallazgo** después del cling | Herida ×2 (golpe directo a la pieza) |

Cada celda: como mucho una rozadura desde arriba y como mucho un golpe
directo. No se acumula picando el mismo aire.

Bandas al recuperar:

| Conservación | Resultado |
| --- | --- |
| ≥ umbral (p. ej. 70 %) | Pieza en buen estado |
| &lt; umbral y &gt; 0 | Se recupera **dañada** (lore / valor) |
| 0 %, o todas las celdas gastadas del todo | **Irrecuperable** (no hay ítem, o solo resto de contexto) |

Una moneda (\(n=1\)) es frágil. Una forma grande aguanta más nicks; machacar
cada celda sí la mata. La información perdida **no vuelve**.

### La jornada

Presupuesto diario de ciclos de Hand Pick (config). No hay cooldown entre
golpes ni gasto extra por “cargar”.

Cupo a 0: *La jornada de excavación ha terminado.* No se borra el mundo ni
la conservación. Al día siguiente se recargan acciones.

Sin acciones: no se retira relleno arqueológico (no se bypassea con pico
vanilla). El campamento **no** gasta jornada.

### Relación con la cata

La **cata** (kit de prospección) va después del rastreador y antes del kit
de establecimiento. No es el picado del prisma.

### Estratos (recordatorio)

Bandas de Y del plugin. El HUD dice la banda; el panel, cuáles quedan.

---

## 3. Hallazgos — propuesta

Un **hallazgo** en el corte es una forma de celdas; al recuperarlo nace **un** ítem con PDC y sube el registro del **panel** de esa excavación.

Pueden ser:

- objetos vanilla (esmeralda, hacha, trigo, molde, disco *Relic*…)
- fragmentos de cerámica vanilla (el plugin añade contexto; no reemplaza el crafting de vasijas)
- piezas de plugin: monedas, fragmentos extra, herramientas/armas temáticas, decorativos, objetos especiales

Cada hallazgo guarda:

| Campo | Notas |
| --- | --- |
| Yacimiento | id persistente |
| Estrato | I–IV o “mezclado” |
| Antigüedad estimada | rango, no fecha exacta |
| Descubridor | UUID + nombre en el momento |
| Fecha | tiempo del servidor / mundo |
| Contexto | intacto / alterado / revuelto; estructura vanilla si aplica |
| Conservación | % visible en la pieza; \(100/(n \times a)\) por fallo; umbral dañado / 0 % irrecuperable (§2) |
| Tipo | vanilla / plugin / reliquia |

No todos los hallazgos son reliquias. Un palo o un ladrillo pueden ser **resto de contexto** (registrado de forma ligera o ni siquiera archivado).

Detalle de cadena (bruto → limpio → catalogado), PDC y reliquias mínimas: **§1c**.

---

## 4. Reliquias — propuesta

Una **reliquia** es un hallazgo al que se le reconoce identidad única.

Criterio **propuesto**: el dossier reserva N reliquias (mínimo 1 en campaña). El resto de hallazgos son contexto. Un hallazgo común no se vuelve reliquia salvo un cupo pequeño de “consagrar” al catalogar.

Nombre: yunque, tras catalogar. Identidad: `findId` en el ítem y en el archivo.

El descubridor puede nombrarla:

```
"La Espada de las Cenizas"
Antigüedad: 700–850 años
Yacimiento: Las Ruinas del Este
Estrato: III
Descubierta por: Alex
```

La reliquia:

- conserva id único aunque cambie de dueño
- puede vivir en inventario, cofre, exposición o registro
- no se convierte sola en lore oficial

---

## 5. Interpretación — propuesta

Flujo concreto (indicios del panel → etiquetas en la mesa): **§1c**.

Catálogo inicial (configurable):

- posible conflicto armado
- posible actividad comercial
- posible uso ceremonial
- posible asentamiento
- posible abandono
- posible enterramiento
- posible depósito deliberado
- función desconocida

Cada interpretación tiene:

- autor
- alcance (hallazgo concreto, estrato o yacimiento entero)
- confianza (baja / media / alta) — subjetiva del jugador, no un “score de verdad”
- fecha

Varios jugadores pueden interpretar el mismo objeto de formas distintas. Las hipótesis conviven. Ninguna pisa a las demás ni al lore admin.

---

## 6. Registro arqueológico — propuesta

No hay diario de jugador en disco. Lo visible es el **panel de la excavación**
(progreso, recuentos) y las **piezas que aún existen** (PDC). Perder un objeto
pierde esa ficha detallada; el recuento del site puede quedar.

---

## 7. Museos — propuesta

Un museo es roleplay: un edificio y marcos. Sin registro. Clic en reliquia catalogada = ficha del ítem. Detalle: **§1c**.

---

## 8. Lore del servidor — propuesta

Tres capas, siempre visibles como tales:

| Capa | Quién la escribe | Autoridad |
| --- | --- | --- |
| **Generada** | Tablas, estratos, indicios, textos genéricos del plugin | “lo que el terreno sugiere” |
| **Oficial** | Admins (fichas de yacimiento, eras, facciones históricas, vetos) | canon del servidor |
| **Interpretada** | Jugadores | opinión; nunca se promociona sola a canon |

Compatible con lore propio: los años de ejemplo pueden ser eras (`Era de la Ceniza`, `Tercer Éxodo`) vía config.

---

## 9. Facciones — propuesta

Integración **débil**:

- Acceso: el director puede autorizar una **facción** entera a excavar (§1d); Archaeo pregunta quién es miembro, no copia el roster.
- Contexto opcional: “territorio actual: X”.
- El director Archaeo no es el claim de facciones. El chunk de **establecimiento** se bloquea mientras la excavación esté activa. El prisma **puede** protegerse entero (`establish.protect-dig-site`); no se protege bloque a bloque según si está al descubierto.
- El control *actual* no explica el pasado: es contexto presente (quién excava con permiso, quién disputa el terreno).
- Sin facciones, esas líneas simplemente no aparecen.

---

## Modelo de datos (esbozo técnico)

Inventario completo de campos y sitio de guardado: **§1c Metadatos**.

Resumen: disco = **excavaciones** (`sites/`). PDC = pieza. Campamento (mesa) =
`siteId`. Sin cuaderno global.

Flujo jugador: rastreador → cata → kit de establecimiento → panel / jornadas (§2).
