# Archaeo — Documento de concepto

Documento vivo. Las secciones marcan el estado de cada idea:

- **borrador** — idea inicial, aún se discute
- **propuesta** — diseño concreto pendiente de acuerdo
- **acordado** — se da por bueno hasta que lo cambiemos

Plugin: `archeology-plugin` (`com.nowko`). Nombre de producto tentativo: **Archaeo**.

---

## Intención

Ampliar la arqueología de Minecraft para que los jugadores descubran, excaven, estudien y conserven restos del pasado del mundo en un mapa personalizado.

Arqueología vanilla vs Archaeo:

1. **Vanilla** — recorrer el mundo, detectar algo, pincelar, llevarse un hallazgo. Corto, azaroso.
2. **Archaeo** — habilitar un yacimiento como *lugar de trabajo*, volver a él, construirlo y excavarlo con calma, como una casa. Largo, laborioso, roleplay.

Experiencia vanilla (expedición):

> Explorar → detectar algo extraño → investigar → descubrir un yacimiento → (opcional) habilitar campaña → excavar → encontrar un objeto → …

Experiencia Archaeo (campaña):

> Elegir / confirmar un sitio → montar el campamento → desbrozar → abrir estratos poco a poco → hallazgos espaciados → interpretar → nombrar → registrar → exponer.

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
2. **Descubrir, no consultar.** Las ruinas no se listan automáticamente; la comunidad debe desbloquear su investigación mediante fragmentos.
3. **El plugin no escribe la historia oficial.** Ofrece indicios e interpretaciones posibles. La historia canónica del servidor la marcan los admins.
4. **Tres capas de verdad** (plugin / admin / jugador) coexisten y no se pisan.
5. **Facciones opcionales.** Si el plugin de facciones está, se usa como dato de contexto (quién controla el territorio *ahora*). Si no está, Archaeo funciona igual.
6. **La fragilidad importa.** El terreno y los restos deben poder alterarse o perderse; Archaeo no debe convertir una excavación en un generador de objetos sin riesgo.
7. **Un yacimiento puede ser un sitio, no un loot.** Habilitar una campaña convierte un lugar en proyecto a medio plazo. No todo hallazgo tiene que ocurrir en una sola sesión.

---

## 1. Yacimientos — propuesta

Un **yacimiento** es un lugar persistente con identidad, no un chunk anónimo.

### Tipos

| Tipo | Origen | Cómo se “encuentra” |
| --- | --- | --- |
| **Vanilla** | Pirámide, fuente, ruinas oceánicas, ruinas perdidas | Fuera del alcance inicial: el mapa personalizado no las genera. |
| **Ruina administrada** | Chunk seleccionado por el staff en el mapa personalizado | Se crea con un comando que registra el chunk, el nivel de interés y los datos iniciales del lugar. Es el tipo de la primera versión. |
| **Campaña (habilitado por jugador)** | El jugador abre un frente de excavación en un sitio viable | No aparece magia de golpe: se *trabaja* a lo largo de días. Ver sección 1b. |

Una ruina administrada puede **pasar a campaña** si alguien la habilita. Las estructuras que existan en el mapa serán construidas por moderadores y registradas con el comando.

### Descubrimiento

- El staff registra manualmente los chunks que contienen una ruina mediante un comando; el plugin no intenta descubrirlos automáticamente en el mapa personalizado.
- El comando permite asignar un nivel de interés inicial y, opcionalmente, nombre, tipo y descripción interna.
- La localización para jugadores se resolverá mediante investigaciones y brújulas vinculadas.
- Al descubrirse, el yacimiento queda **registrado para siempre**.
- El descubridor puede **nombrarlo** (con reglas de longitud, uniquedad y moderación).
- Si no lo nombra, el sistema usa un nombre provisional (`Yacimiento del desierto #14`, coordenadas ofuscadas o bioma + rumbo).

Ficha mínima:

```
Yacimiento: Las Ruinas del Este
Tipo: ruina administrada | campaña
Descubierto por: Alex
Fecha: …
Estratos conocidos: I–III (IV no excavado)
Estado: activo / agotado / disturbado
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
También prepara los bloques sospechosos de tierra o grava según el nivel de
interés y los reparte entre las capas configuradas, sin sustituir aire, agua,
lava, cofres, construcciones protegidas ni bloques colocados por jugadores.
`info`, `set-interest` y `delete` requieren
permisos de administración; cambiar el interés de una ruina con campaña activa
debe estar restringido o dejar un registro explícito para no cambiar su dossier
retroactivamente.

### Cómo lo investiga el usuario

En el mapa personalizado, el staff decide primero qué chunks son ruinas. El
jugador no necesita saber las coordenadas. La localización se desbloquea mediante
una investigación: al obtener conocimiento suficiente sobre una ruina, puede
conseguir una brújula vinculada que le guía hasta ella. La investigación comienza
con una pista, no con una lista de todos los yacimientos.

#### Investigación y brújula — propuesta

Cada ruina registrada tiene una investigación asociada, pero permanece bloqueada
para los jugadores. La investigación tiene tres estados:

1. **Rumor:** el jugador recibe una pista incompleta, sin nombre exacto ni
   coordenadas.
2. **Conocimiento:** reúne las pruebas necesarias y desbloquea la ruina en su
   cuaderno. Todavía no recibe su ubicación automáticamente.
3. **Brújula:** canjea o fabrica una brújula vinculada a esa investigación. La
   brújula apunta al chunk del yacimiento mientras exista y tenga permiso para
   consultarlo.

La brújula no descubre ruinas nuevas ni muestra un mapa. Es la recompensa por
haber investigado una historia concreta. Si el jugador pierde la brújula, puede
obtener otra mientras conserve el conocimiento desbloqueado.

El jugador comienza con un **cuaderno de campo** y una investigación general
inicial, no con todas las ruinas disponibles. Esa investigación enseña el oficio
o permite registrar la primera pista encontrada, pero no revela ningún yacimiento
por sí misma.

#### Cómo aparecen las pistas sin intervención del staff

El cuaderno de conocimientos generales es común para todo el servidor. Se
fabrica en la mesa de arqueología y no contiene las ubicaciones de las ruinas:
solo permite consultar las investigaciones descubiertas por la comunidad.

Al registrar una ruina, el plugin crea su investigación y divide la pista en un
número configurable de fragmentos. Esos fragmentos no los entrega un admin: cada
uno puede aparecer como recompensa aleatoria al realizar acciones configuradas
en el mundo, por ejemplo excavar, explorar, pescar, abrir cofres o completar
otras actividades arqueológicas. La dificultad controla la probabilidad de que
aparezca el fragmento pertinente.

El fragmento aparece como objeto en la mano del jugador. Al hacer clic derecho,
se consume y se registra en el cuaderno común. El servidor guarda el progreso
global, por ejemplo `3/5 pistas`, y un fragmento ya registrado no vuelve a
contar. Para evitar mala suerte extrema, la probabilidad puede aumentar tras
cada intento fallido o existir un límite de acciones antes de garantizar un
hallazgo.

Cada ruina puede configurarse con un número de fragmentos y una dificultad
distinta. El staff solo define esos parámetros al crearla; no tiene que colocar
libros, escribir pistas ni dirigir la entrega a cada jugador.

Cuando se registran todos los fragmentos, la investigación queda disponible en
la mesa de arqueología. En la primera versión consiste en leer durante un tiempo:
una barra de progreso, sonido de páginas y una interrupción si el jugador se
aleja. Más adelante este paso podrá sustituirse por un puzle de reconstrucción de
mapa sin cambiar el progreso ni el cuaderno.

Al completar la investigación, el jugador obtiene una brújula vinculada al
`siteId`. La brújula guía hasta las coordenadas del yacimiento. Perderla no borra
el conocimiento: se puede fabricar u obtener un reemplazo con el coste que defina
la configuración.

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

La cata sirve para comunicar al jugador una estimación de ese interés. Puede
incluir una pequeña variación controlada, de modo que dos catas no tengan que
mostrar exactamente la misma lectura, pero nunca debe superar las condiciones
del dossier que el staff asignó. Al habilitar la campaña, la riqueza efectiva se
fija y se guarda.

Habilitar no tiene por qué ser un claim de facciones. Es una ficha Archaeo: director, colaboradores, si es público o privado.

- Si hay facciones, se puede exigir estar en territorio propio o aliado para habilitar.
- El terreno sigue siendo el del otro plugin; Archaeo no duplica claims.
- Alguien puede construir un campamento precioso y que otra facción dispute el chunk: eso es lore presente, no arqueología.

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
| **Un chunk (16×16)** | Encaja con mapas, Dynmap y facciones (casi siempre claiman por chunk). El plugin comprueba “¿este bloque está en el chunk del atril?”. El jugador entiende el límite: el borde del chunk. |
| Más (2×2 chunks) | Es una cantera. Vaciarlo a pala deja de sentirse arqueología. |

Profundidad: no es un chunk hacia bedrock. Al habilitar, el plugin define **bandas de Y** a partir de la superficie de ese chunk (unos 3–5 bloques por estrato *presente* en el dossier).

---

### El atril del yacimiento (qué era el “mojón”)

*Mojón* era un hito de piedra de linde. Sobran dos objetos. **Un solo bloque hace de hito: un atril (lectern) vanilla.**

En una excavación de verdad se clava un **punto de datum**: origen desde el que se mide todo el yacimiento. En Minecraft ese datum es el atril.

Qué hace el jugador:

1. En zona de señal clara, coloca un **atril**.
2. El plugin lo reconoce como “este chunk es ahora un yacimiento” y pide el **nombre** (cartel o anvil-GUI corto).
3. Sobre el atril aparece (o se actualiza) un **libro escrito** vanilla. Eso es el diario de campo. El jugador pulsa el atril y **lee**, igual que un atril de pueblo.

El libro **no** es un ítem que tenga que llevar encima. Vive en el atril. Si se lo lleva, el yacimiento sigue existiendo (datos en disco); el atril vacío se puede “reponer” el diario. Si rompe el atril, el sitio no se borra, pero deja de haber hito en el mundo hasta que ponga otro en el **mismo chunk**.

El campamento (techo, vallas, cofres) lo construye el jugador **alrededor** del atril. El plugin no construye nada. Las vallas en el borde del chunk son opcionales y muy claras: “hasta aquí cava Archaeo”.

Resumen: **atril = placa del yacimiento. Libro del atril = qué se sabe de este solar. Chunk del atril = el solar.**

### Cómo lo investiga el usuario

El jugador necesita saber si merece la pena invertir horas en un chunk antes de
montar allí una excavación. En la primera versión el staff ya ha registrado la
ruina y su interés; la prospección del jugador sirve para localizarla y decidir
si quiere asumir el trabajo de la campaña.

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
registrado ni la riqueza definitiva. Al habilitar la campaña, el plugin guarda
el dossier generado desde el nivel administrativo.

#### ¿Se guardan las coordenadas?

No hace falta guardar candidatos automáticos, porque en esta versión no existen.
El plugin registra las coordenadas al ejecutar el comando administrativo.

Sí se guardan:

- el chunk y el atril de los yacimientos confirmados;
- el dossier generado al habilitar una campaña;

#### Ejemplo de configuración

Los nombres son orientativos; lo importante es que el staff pueda ajustar el
ritmo sin editar código:

```yaml
prospection:
   search-tool:
      enabled: false
      radius: 32
      cooldown-seconds: 30
   interest-levels:
      bajo:
         base-wealth: 1
      medio:
         base-wealth: 2
      alto:
         base-wealth: 4
      excepcional:
         base-wealth: 6
   interest-levels:
      bajo:
         base-wealth: 1
         variation: 0
      medio:
         base-wealth: 3
         variation: 1
      alto:
         base-wealth: 6
         variation: 1
      excepcional:
         base-wealth: 10
         variation: 2
```

La configuración define la riqueza base y la variación permitida para cada nivel.
No hay multiplicadores ambientales ni cálculos sobre el terreno. Una campaña ya
iniciada conserva el dossier que se generó al habilitarla.

La configuración no contiene una lista de chunks. Contiene los niveles
disponibles, su riqueza base, la variación, los textos y las reglas de la cata.
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

La variación de la lectura puede ser efímera y no necesita guardarse. El dato
persistente empieza con el comando que registra la ruina; al habilitar la
campaña se guarda el dossier definitivo.

#### Cata de tierra

Cuando el jugador llega al chunk registrado, hace una **cata** con pala, pincel
o herramienta propia. La cata consulta únicamente el nivel de interés asignado
por el staff y su variación configurada. Devuelve una riqueza orientativa:

1. **Prometedor:** interés bajo o condiciones poco favorables.
2. **Notable:** interés medio o varios factores favorables.
3. **Excepcional:** interés alto o excepcional, reforzado por buenas condiciones.

La cata puede variar ligeramente entre usos para representar una observación
parcial, pero nunca rebaja el interés mínimo registrado. Al colocar el atril, el
plugin calcula y guarda el dossier definitivo de la campaña.

**Cata.** El jugador utiliza la herramienta definida por la ruina sobre un
punto de excavación y recibe una lectura narrativa de su riqueza. No necesita
encontrar un bloque vanilla concreto ni acumular señales en una base de datos.

Tras una cata positiva en un chunk registrado por el staff, el jugador puede
proponer habilitar el yacimiento. La aceptación guarda el resultado final y no
requiere acumular lecturas temporales en una base de datos.

Las estructuras vanilla quedan fuera del alcance inicial porque el mapa
personalizado no las genera. Las construcciones relevantes del mapa son creadas
por moderadores y se registran como parte de una ruina.

### Habilitar (adueñarse)

No es un claim de facciones. Es colocar el **atril del yacimiento** en el chunk.

1. Estar en el chunk registrado y completar una cata positiva.
2. Colocar el atril y **nombrar** el yacimiento (`Las Ruinas del Este`).
3. El plugin lee el interés asignado, calcula la riqueza según la configuración, registra al jugador como director y genera el libro del atril.

Los bloques sospechosos se preparan al crear la ruina, no al habilitar la
campaña. Al colocar el atril solo se guarda el dossier y el jugador ya puede
construir el campamento alrededor.

### Cómo se procesan los puntos de excavación (código)

Los puntos de excavación se preparan al ejecutar el comando, conforme al diseño
de cada ruina. Al habilitar una campaña se registra el estado del sitio y el
jugador trabaja el terreno según sus reglas.

Minecraft **no** tiene estratos arqueológicos. Césped sobre tierra sobre piedra es geología tosca. La arcilla, la grava y el barro salen en **manchas**, no en capas continuas. **No** vamos a rellenar el chunk como un sándwich de arcilla ni a preguntar “¿el último bloque era grava?”.

El estrato lo define el plugin, al habilitar, como **profundidad**:

```
superficie del chunk (césped, arena, lo que haya)
  banda I     p. ej. 0 a −4 bloques bajo la superficie    más reciente
  banda II    −5 a −9
  banda III   −10 a −14
  banda IV    −15 a −19   (si el dossier dice que existe)
por debajo    fuera del yacimiento: picas piedra o lo que sea, no salen restos Archaeo
```

Da igual que en un rincón haya piedra a −3 y en otro tierra a −12. Si el bloque está a **esa profundidad relativa**, es esa capa. Pala o pico según lo que haya *ahí*, no según un material impuesto.

Al realizar una acción de excavación válida dentro de un yacimiento registrado,
Archaeo calcula su procedencia, capa y contexto, y aplica el presupuesto de
artefactos de la campaña.

### Cómo conoce el usuario las capas

No depende del último bloque ni de “ahora estás en modo estrato II”. Puedes abrir un pozo a la banda III y luego desbrozar la I: es válido (en la vida real es mala praxis; aquí puede marcar el hallazgo hondo como *secuencia invertida*, datación más floja). Cada hallazgo mira **la Y del punto excavado**, no el historial de picos.

**En el momento de excavación (lo importante):**

Al **empezar a cepillar** (o al terminar), texto claro, una vez:

*Las Ruinas del Este · capa II (300–500 años)*

Eso va al lore del ítem en el acto. No hace falta volver al atril para saberlo.

Si picas en una profundidad sin punto de excavación, no aparece ningún hallazgo.
El presupuesto de artefactos limita el contenido adicional de la campaña.

Si bajas **más hondo que la última banda**: *Ya no es el yacimiento.*

**El atril no es un GPS de “dónde estás ahora”.** Es el estado del **solar**:

- Capa I — quedan restos / agotada  
- Capa II — quedan restos / agotada  
- Capa IV — no se conserva en este sitio  

Se actualiza cuando **sale un hallazgo** (baja el contador) o se agota una banda, no cada vez que picas tierra en un lado y piedra en otro. Puedes leerlo cuando quieras; no sustituye al mensaje al cepillar.

Un pozo de 1×1 en la banda III puede activar un punto de excavación de esa cota,
si la ruina lo define allí. La I sigue intacta hasta que caven a esa cota.
Recorrer el chunk sigue sirviendo: los puntos y el presupuesto de artefactos se
reparten por el solar, no en un único punto.

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

- **`sites/`** — sí. El solar, el dossier, presupuestos por capa, indicios del terreno, director, atril.
- **`knowledge/`** — sí. El cuaderno común, investigaciones, fragmentos registrados y estados completados.
- **Ítem (PDC)** — sí. Toda la ficha de esa pieza (procedencia, hints copiados, interpretación). Si se pierde el objeto, **se perdió**.
- **`finds/`, `players/`, `museums/`** — no. Un museo es un edificio con marcos; el clic lee el PDC. No hay diario de servidor ni historial de piezas.

El atril guarda solo `siteId` (para saber qué JSON recargar en el libro).

El JSON del site puede tener la **lista de restos que aún no han salido** (capa + tipo) y el estado de cada bloque sospechoso generado. Al cepillar, se escribe el ítem, se marca el bloque como procesado y se actualiza el yacimiento. Si el bloque se rompe, se marca como destruido y no se regenera.

#### Qué va en el ítem (PDC + lore)

| Dato |
| --- |
| `siteId`, nombre del yacimiento |
| capa / antigüedad |
| descubridor, fecha |
| estado de laboratorio |
| indicios del yacimiento **copiados** al catalogar |
| interpretación(es) del catalogador |
| nombre de reliquia, si la hay |
| material del artefacto, procedimiento actual y procedimientos completados |

#### Qué va en `sites/<id>.yml` o `.json`

| Dato |
| --- |
| mundo, chunk, coords del atril |
| tipo, nombre, director, fecha |
| riqueza, indicios elegidos (ids del config) |
| por capa: ¿existe?, banda de Y, restos pendientes, revuelto/ausente |
| estado activo / agotado |

`config.yml` (y opcional `hints.yml`, `interpretations.yml`, `research.yml`,
`materials.yml`): textos, acciones que pueden generar fragmentos, dificultades,
requisitos, loot, materiales y procedimientos.
No es partida.

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

1. **Indicios del yacimiento** — los tira el plugin al habilitar (2–4). Van al libro del atril. Al **identificar** en la mesa/atril se **copian al PDC del objeto** (foto de ese momento).
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

## 2. Excavar — propuesta

### Mecánica principal

El jugador usa la herramienta definida por la ruina sobre un punto de excavación. Archaeo registra el éxito y anota contexto (yacimiento, estrato, jugador, tiempo).

La pala puede servir para **desbrozar** tierra normal. La herramienta definida por la ruina se usa después en el punto de excavación, respetando las reglas del mapa.

En una campaña, la pala puede ser el trabajo sucio (abrir el corte) y otra herramienta el trabajo fino. El plugin no debe recompensar el minado masivo fuera de los puntos definidos.

### Estratos

Minecraft no trae capas de ocupación. Las nuestras son **bandas de profundidad del plugin** en ese chunk (ver §1c), no arcilla/grava vanilla.

El marco temporal (configurable; el lore puede usar eras):

| Estrato | Antigüedad (ejemplo) |
| --- | --- |
| I | reciente |
| II | 300–500 años |
| III | 700–900 años |
| IV | 1000–1300 años |

El marco no afirma hechos. Solo acota qué restos son verosímiles. Una capa puede estar ausente o revuelta **en el dossier**, aunque el terreno sea piedra o tierra a esa Y.

### Conservación irregular

No todos los estratos están intactos:

- **Intacto** — datación más fiable.
- **Alterado** — mezcla, datación amplia, hipótesis menos seguras.
- **Revuelto** — objetos de varias épocas juntos; el plugin lo señala como contexto mezclado, no como error.
- **Ausente** — esa época no se conserva (erosión, saqueo antiguo, nunca ocupado).

Esto evita que cada yacimiento sea un sándwich perfecto de cuatro capas.

---

## 3. Hallazgos — propuesta

Todo objeto extraído en un yacimiento registrado (o en el acto que lo descubre) puede convertirse en un **hallazgo** con metadatos persistentes (PDC del ítem + archivo del plugin).

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

Flujo concreto (indicios del libro → etiquetas al catalogar): **§1c**. El plugin no dice qué ocurrió; no hay respuesta correcta.

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

No hay archivo de jugador en disco. Lo que “tienes” es:

- los **sites** que diriges (en `sites/`)
- las **piezas que aún existen** (PDC), en el inventario, cofres o marcos

Perder un objeto es perder esa ficha. El yacimiento sigue.

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

- Si hay plugin de facciones, Archaeo puede anotar “territorio actual: X” en la ficha del yacimiento.
- No copia miembros, power ni claims.
- El control *actual* no explica el pasado: es contexto presente (quién excava con permiso, quién disputa el terreno).
- Sin facciones, esas líneas simplemente no aparecen.

---

## Modelo de datos (esbozo técnico)

Inventario completo de campos y sitio de guardado: **§1c Metadatos**.

Resumen: en disco se guardan **yacimientos** y el progreso global del cuaderno. La ficha de la pieza vive en el **PDC**. El atril guarda `siteId`.

Campaña: al **habilitar**, se conserva el dossier y se activan los puntos de
excavación definidos para la ruina. El estrato del hallazgo es la **Y del punto
excavado**.