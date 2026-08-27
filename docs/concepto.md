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
2. **Archaeo** — encontrar una señal, confirmarla con catas, **plantar un campamento** y trabajarlo a lo largo de jornadas. No es un `/claim`.

Experiencia vanilla (expedición):

> 📡 Rastreador → 🔎 cata (kit de prospección) → ⛺ kit de excavación (campamento) → 📖 panel → ⛏️ jornadas → 🏺 estudio / museo.

Experiencia Archaeo (campaña):

> 📡 Rastreador → 🔎 cata → ⛺ establecer excavación → 📖 panel y personal → ⛏️ minijuego → hallazgos al registro de la excavación → interpretar / museo.

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
5. **Facciones opcionales.** Pueden recibir **acceso a excavar** (lista del site). No son dueñas del yacimiento ni Archaeo protege bloques: eso sigue siendo el plugin de claims.
6. **La fragilidad importa.** El terreno y los restos deben poder alterarse o perderse; Archaeo no debe convertir una excavación en un generador de objetos sin riesgo.
7. **Una excavación es un proyecto, no un loot ni un plot.** Se descubre, se confirma y se planta en el mundo. Queda **fija** a esa localización.
8. **El minijuego es el mundo.** Pico / martillo / pincel en el chunk; HUD mínimo. La gestión (personal, visibilidad) vive en el **panel del campamento**, no en comandos de jugador.
9. **Archaeo no es un plugin de protección.** Sin permiso arqueológico no se usan las herramientas del minijuego. Romper tierra “vanilla” lo deciden facciones/claims.

---

## 1. Yacimientos — propuesta

Un **yacimiento** es un lugar persistente con identidad, no un chunk anónimo.

### Tipos

| Tipo | Origen | Cómo se “encuentra” |
| --- | --- | --- |
| **Vanilla** | Pirámide, fuente, ruinas oceánicas, ruinas perdidas | Fuera del alcance inicial: el mapa personalizado no las genera. |
| **Ruina administrada** | Chunk seleccionado por el staff en el mapa personalizado | Se crea con un comando que registra el chunk, el nivel de interés y los datos iniciales del lugar. Es el tipo de la primera versión. |
| **Campaña (excavación del jugador)** | El jugador la **establece** con un kit en un chunk ya confirmado por cata | Proyecto persistente, visible en el mundo (§1d). |

Una ruina administrada pasa a **excavación** cuando alguien la confirma con cata y planta el kit. Las construcciones del mapa las ponen los moderadores; el comando de staff solo registra el chunk oculto.

### Descubrimiento

- El staff registra manualmente los chunks que contienen una ruina mediante un comando; el plugin no intenta descubrirlos automáticamente en el mapa personalizado.
- El comando permite asignar un nivel de interés inicial y, opcionalmente, nombre, tipo y descripción interna.
- La localización para jugadores es **rastreo + prospección**, no una brújula al chunk ni un árbol de pistas.
- Al **establecer** la excavación queda **registrada para siempre** en esas coords (no se borra ni se mueve a capricho).
- El que planta el kit es el **director** inicial y puede nombrarla.
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

El jugador **no** usa comandos. Flujo: rastreador → zona sospechosa → **cata** (confirma y da las primeras características) → **kit de excavación** (campamento persistente). Detalle §1d.

Se elimina el sistema de fragmentos de conocimiento, la libreta compartida, la brújula al chunk y cualquier `/claim` de jugador.

#### Rastreador — propuesta

Ítem de plugin (detector / radar). Al usarlo, emite **pitidos** cuya frecuencia
depende de la distancia al yacimiento **no descubierto** más cercano que esté
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
Sigue la **cata** y, si se confirma, el **kit de excavación** (§1d).

**Alcance.** Radio según tamaño/interés (`detection-radius`). Sitios **ya establecidos** o agotados no llaman al rastreador (o se silencian en config).

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
“reclamar” el chunk: eso es plantar el kit.

Al **establecer** la excavación, la riqueza efectiva se fija en el dossier.

La excavación es una **entidad Archaeo** sobre el mundo: no es un claim de
facciones. El director no es dueño del terreno. Facciones/claims siguen
protegiendo bloques; Archaeo solo dice quién puede usar herramientas de excavación.

---

## 1c. Cómo se lleva a cabo en Minecraft — propuesta

Esta sección baja el concepto a cosas que el jugador **ve y hace** con herramientas vanilla. Objetivo: claro, pocas GUIs, mucho mundo.

### Qué es un yacimiento para Archaeo

Un yacimiento es un **volumen persistente** en el plugin, no “cualquier bloque sospechoso”.

Datos mínimos:

- `id`
- mundo + **caja** (cuboide): p. ej. 16×16 hasta 32×32 en planta, y un rango de Y (desde la superficie del campamento hasta N bloques hacia abajo)
- tipo, nombre, descubridor
- dossier: estratos, presupuesto, indicios, estado

Un punto de excavación está “en el yacimiento” si sus coordenadas caen **dentro de esa caja**. Fuera de la caja el plugin no genera hallazgos de campaña.

**Tamaño de campaña — un chunk (16×16), alineado a la cuadrícula de Minecraft.**

Un bloque ≈ un metro. En arqueología real las cuadrículas suelen ser de 1×1 m o 5×5 m; un chunk entero es ya una zanja grande (256 m²). No hace falta más suelo: el trabajo largo está en **bajar estratos**, no en comerse tres chunks.

| Tamaño | Por qué sí / no |
| --- | --- |
| Menos (8×8) | Corto de más; se acaba el solar como un sótano, no como un yacimiento. |
| **Un chunk (16×16)** | Encaja con mapas, Dynmap y facciones. Límite = chunk del **campamento**. |
| Más (2×2 chunks) | Es una cantera. Vaciarlo a pala deja de sentirse arqueología. |

Profundidad: no es un chunk hacia bedrock. Al **establecer** la excavación, el plugin define **bandas de Y** a partir de la superficie (unos 3–5 bloques por estrato presente).

---

### Campamento

El hito en el mundo es el **campamento** que sale al usar el kit de excavación:
mesa de arqueología, tablón, cajas, quizá una carpa (§1d). El jugador puede
construir más alrededor. El plugin no exige un atril.

Clic en la **mesa o el tablón** = panel de la excavación. El chunk de ese
campamento es el solar.

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

El plugin registra el chunk al comando de staff. Tras establecer: coords del
campamento + dossier.

Sí se guardan:

- el chunk y el campamento de las excavaciones establecidas;
- el dossier al plantar el kit;

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
| Yacimiento arqueológico confirmado | Ya se puede plantar el kit |

Ejemplos: *Muestra de tierra analizada. Se han detectado restos de actividad
humana.* / *Yacimiento arqueológico confirmado.*

**¿Obligatoria?** Sí para **establecer** la excavación. No para saber que hay
algo: eso lo hizo el rastreador.

La lectura usa el interés del staff + variación. Nunca inventa un yacimiento
donde el staff no registró chunk.

Las estructuras vanilla quedan fuera del alcance inicial.

---

## 1d. Establecer excavación, panel y acceso — propuesta

No hay comando de jugador. Tras **yacimiento confirmado**, se coloca el **kit
de excavación arqueológica**.

Aparece un campamento reconocible: mesa de arqueología, carpa, cajas,
herramientas, tablón.

> Has establecido una excavación arqueológica.

```
Excavación #027
Ruinas del valle
Descubierta por: Alex
Director (Archaeo): Alex
```

El que planta el kit es el **director** inicial. Controla quién trabaja la
excavación. **No** es dueño del terreno.

**Permanencia.** No se borra ni se mueve a capricho. Queda atada a esas
coordenadas. Puede ser un lugar del servidor. El jugador no se lleva el
yacimiento en el inventario. Staff puede intervenir.

### Panel

Clic en mesa, tablón o campamento:

```
RUINAS DEL VALLE
Director: Alex
Estrato actual: III · 700–900 años
Progreso: ██████░░░░
Hallazgos: 7 · Evidencias: 12
[EXCAVAR]  [PERSONAL]  [INFORMACIÓN]
```

**EXCAVAR** no es un menú del minijuego: el trabajo es en el corte con HUD (§2).
**PERSONAL** y **INFORMACIÓN** sí abren gestión.

### Personal (v1: puede excavar sí/no)

El director añade jugadores. Roles más adelante si hacen falta: Director,
Arqueólogo, Excavador, Visitante.

### Facciones

**Añadir facción** además de jugador. Archaeo pregunta al otro plugin los
miembros. Una facción autorizada trabaja como proyecto colectivo.

### Sin permiso

*No tienes autorización para trabajar en esta excavación.* No hay jornada ni
hallazgos. Archaeo **no** bloquea el minado vanilla.

### Visibilidad

| Estado | Quién excava |
| --- | --- |
| **Privada** | Director + autorizados |
| **Por invitación** | Se puede solicitar acceso |
| **Pública** | Cualquiera en el minijuego |

### Secuencia

1. Rastreador → zona sospechosa.
2. Catas → confirmado.
3. Kit → campamento + director + dossier.
4. Formas ocultas en el chunk (§2).

---

### Cómo se procesan los puntos de excavación (código)

Los hallazgos se sortean al **establecer** (plantilla + forma conexa en una banda
de Y). El terreno no cambia hasta la jornada. **§2**.

Minecraft **no** tiene estratos arqueológicos. Césped sobre tierra sobre piedra es geología tosca. La arcilla, la grava y el barro salen en **manchas**, no en capas continuas. **No** vamos a rellenar el chunk como un sándwich de arcilla ni a preguntar “¿el último bloque era grava?”.

El estrato lo define el plugin, al establecer, como **profundidad**:

```
superficie del chunk (césped, arena, lo que haya)
  banda I     p. ej. 0 a −4 bloques bajo la superficie    más reciente
  banda II    −5 a −9
  banda III   −10 a −14
  banda IV    −15 a −19   (si el dossier dice que existe)
por debajo    fuera del yacimiento: picas piedra o lo que sea, no salen restos Archaeo
```

Da igual que en un rincón haya piedra a −3 y en otro tierra a −12. Si el bloque está a **esa profundidad relativa**, es esa capa. Pico y martillo retiran relleno; el pincel trabaja las celdas de un hallazgo ya tocado.

Al realizar una acción de excavación válida dentro de un yacimiento en campaña,
Archaeo calcula procedencia, capa y contexto, gasta una acción de la jornada y
aplica el presupuesto de artefactos.

### Cómo conoce el usuario las capas

No depende del último bloque ni de “modo estrato II”. Puedes abrir un pozo a la banda III y luego desbrozar la I (mala praxis real; el hallazgo hondo puede marcarse *secuencia invertida*). Cada hallazgo mira **la Y de la celda trabajada**.

**Mientras está en el chunk de una excavación establecida**, un HUD mínimo muestra el estrato y las acciones de hoy (§2). El **panel del campamento** es la ficha del solar, no el GPS.

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
| indicios copiados al catalogar |
| interpretación(es) |
| nombre de reliquia, si la hay |
| material, procedimiento actual y completados |
| tamaño/plantilla del hallazgo (opcional, lore) |

#### Qué va en `sites/<id>.yml` o `.json`

| Dato |
| --- |
| mundo, chunk, coords del campamento (mesa/tablón) |
| tipo, nombre, nº de excavación, director, fecha |
| visibilidad, jugadores y facciones con permiso de excavar |
| riqueza, indicios, radio de detección |
| hallazgos en corte + contadores recuperados / evidencias |
| por capa: ¿existe?, banda de Y, revuelto/ausente |
| jornada actual: pico / martillo / pincel restantes, id de día de mundo |
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

## 2. Excavar — el minijuego en el chunk — propuesta

La excavación de **campaña** no es una GUI ni un cooldown por clic. Se activa
en el chunk de una excavación **establecida**. Solo quien tiene permiso
arqueológico usa pico/martillo/pincel de jornada; si no: *No tienes autorización
para trabajar en esta excavación.* Fuera del chunk, o sin permiso, Minecraft
normal (claims aparte).

Inspiración: el Subsuelo de Pokémon — no ves dónde están los hallazgos hasta
que retiras material. El núcleo no es “picar menos por capricho”, sino **gastar
un presupuesto de jornada** (pico preciso vs martillo rápido vs pincel de
rescate).

### HUD mínimo

Al entrar en una excavación activa aparece un HUD pequeño (action bar / bossbar
discreta / título corto; no un inventario). Muestra el estrato de la
profundidad actual y las acciones que quedan **hoy**:

```
ESTRATO III · 700–900 años
⛏️ 5    🔨 2    🖌️ 8
```

Opcional: una barra `Jornada: ███████░░░`.

El HUD **cambia de estrato** al bajar (o subir) de banda de Y, sin abrir menús:

```
ESTRATO IV · 900–1200 años
⛏️ 3    🔨 1    🖌️ 6
```

El resto de información solo cuando hace falta, un instante:

- *Has encontrado parte de un objeto.*
- *El objeto parece extenderse hacia el este.*
- *Hallazgo descubierto: espada antigua.*
- *Hallazgo recuperado.*
- *La evidencia ha resultado dañada.*
- *La jornada de excavación ha terminado.*

### Herramientas y acciones

| Acción | En el mundo | Efecto | Riesgo |
| --- | --- | --- | --- |
| **Pico** (`⛏️`) | Clic/romper 1 bloque de relleno | Área pequeña. Puede dejar tierra o **tocar una celda** de un hallazgo (pasa a parcial). | Bajo |
| **Martillo** (`🔨`) | Un golpe en un punto | Varios bloques. Más riesgo de **dañar celdas** de un hallazgo. | Alto |
| **Pincel** (`🖌️`) | Sobre celdas ya tocadas / expuestas | Revela más de la **forma**. Al estar *descubierto*, recupera la pieza. | Muy bajo; no abre tierra a ciegas |

El pico es el **pico vanilla**. El pincel es el **pincel vanilla** (en campo =
recuperar expuesto; en laboratorio = limpiar, §1c). El martillo es un **ítem de
plugin** (o maza, si se prefiere vanilla 1.21) para no confundirlo con el pico.

Las cantidades iniciales dependen del **tipo de excavación** y del **estrato**
(YAML). Ejemplo de jornada:

`⛏️ 8 · 🔨 3 · 🖌️ 10`

Al cambiar de estrato, el presupuesto del día puede recortarse o reconfigurarse
según config (el ejemplo del HUD IV con menos acciones). El jugador decide cómo
gastar lo que le queda.

Sin acciones de esa herramienta: el golpe no retira relleno arqueológico (no se
puede bypassear sacando otro pico del inventario y minando a lo vanilla).

### Hallazgos: forma, no un bloque-premio

Un hallazgo **no** es un único bloque sospechoso que suelta el ítem. Es un
conjunto de **celdas conectadas** (caras adyacentes) en una banda de estrato,
dentro del chunk. El yacimiento contiene **varios** hallazgos independientes.

```
┌─────────────────────────┐
│   🪙                    │  moneda (1)
│          ⚔️⚔️⚔️         │  espada (3–5)
│                🏺🏺      │  vasija (3–6)
│                🏺🏺      │
└─────────────────────────┘
```

Tamaños orientativos (`finds.yml`; el techo real es el chunk):

| Plantilla | Bloques (aprox.) |
| --- | --- |
| Moneda | 1 |
| Fragmento de cerámica | 1 |
| Herramienta | 2–3 |
| Espada | 3–5 |
| Vasija | 3–6 |
| Enterramiento | 8–15 |
| Estructura | 20–50 |

El tamaño puede ser fijo o un rango. La silueta es irregular pero **conexa**.
Al generar el site se eligen plantillas según interés/estrato y se colocan sin
solaparse.

**Tres estados** (por hallazgo, no por bloque suelto):

| Estado | Qué sabe el jugador |
| --- | --- |
| **Oculto** | Nada. El terreno se ve normal. |
| **Parcialmente expuesto** | Ha tocado al menos una celda. No conoce aún tamaño ni forma. |
| **Descubierto** | Hay suficiente superficie despejada (todas las celdas o un % config) para identificarlo y **recuperarlo**. |

Ejemplo de mensajes:

1. Primer pincel/pico en una celda: *Has encontrado parte de un objeto.*
2. Sigue alrededor: *El objeto parece extenderse hacia el este.*
3. Forma completa: *Hallazgo descubierto: espada antigua.*
4. Entonces el pincel **recupera** un solo ítem (la pieza), no un drop por bloque.

Así descubrir la forma **es** el minijuego. Un martillo sobre esas celdas puede
dañar el conjunto (peor estado al recuperar, o pérdida). Dos niveles: yacimiento
→ muchos hallazgos → cada uno 1…N bloques.

### La jornada

No hay cooldown por acción. Hay **presupuesto diario de trabajo**.

Cuando las acciones llegan a 0 (o se acaba el cupo definido): *La jornada de
excavación ha terminado.* El terreno **queda como lo dejó**. Al día siguiente
(día de mundo o día real, config) se recargan acciones y continúa.

Una excavación grande son varios días, por ejemplo:

1. Tocar las primeras celdas de un hallazgo.
2. Seguir la forma con el pincel.
3. Recuperar al estar descubierto.
4. Bajar de estrato.
5. Hallazgos grandes (enterramiento / estructura) en varios días.

Construir el campamento **no** gasta la jornada.

### Relación con pala y cata

La **cata** (kit de prospección) va **después** del rastreador y **antes** del
kit de excavación. No es el minijuego.

### Estratos (recordatorio)

Bandas de Y del plugin, no arcilla vanilla. El HUD dice en qué banda estás; el panel, cuáles quedan. Capas intactas / alteradas / revueltas / ausentes según el dossier.

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
- El director Archaeo no es el claim. Archaeo no protege bloques.
- El control *actual* no explica el pasado: es contexto presente (quién excava con permiso, quién disputa el terreno).
- Sin facciones, esas líneas simplemente no aparecen.

---

## Modelo de datos (esbozo técnico)

Inventario completo de campos y sitio de guardado: **§1c Metadatos**.

Resumen: disco = **excavaciones** (`sites/`). PDC = pieza. Campamento (mesa) =
`siteId`. Sin cuaderno global.

Flujo jugador: rastreador → cata → kit de campamento → panel / jornadas (§2).