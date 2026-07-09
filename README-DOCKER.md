# Build Linux/Tycho + Docker para SoftwareCognitiveComplexityReducer

Este documento cubre solo la pieza de análisis/aplicación en Docker
(`reduce-cc`). Para el sistema completo (servidor MCP, bot de GitHub),
ver el `README.md` de la raíz del repositorio.

## 1. Estructura de esta pieza

```
.
├── pom.xml                    Reactor Tycho (raíz)
├── target-platform/
│   └── reduce-cc.target       Plataforma de compilación (linux/gtk/x86_64)
├── plugin/
│   ├── ReduceCognitiveComplexity/ Herramienta original + ApplyCodeExtractions
│   └── WorkspaceImporter/     Registra proyectos en el workspace
├── product/                   Ensambla el producto Eclipse headless ejecutable
├── docker/
│   ├── Dockerfile             Build multi-stage (Tycho + CPLEX -> runtime)
│   ├── entrypoint.sh          Orquesta los pasos (importar / analizar / apply)
│   └── config.properties      Config del analizador dentro del contenedor
└── cplex-linux/
    ├── cplex-installer.bin    Instalador CPLEX para Linux (no va al repo)
    └── install.properties     Config de instalación silenciosa
```

## 2. Qué hace falta para construir la imagen

- Docker Desktop (con WSL2 en Windows).
- Tu propio `cplex-linux/cplex-installer.bin` — instalador de **CPLEX Free
  (Community) Edition para Linux x86-64**, descargado desde
  `ibm.com/products/ilog-cplex-optimization-studio`. No se distribuye con
  el repo (licencia de IBM); cada persona debe descargar el suyo.

```powershell
docker build -f docker/Dockerfile -t reduce-cc .
```

## 3. Cómo ejecutarlo

```powershell
docker run --rm `
  -v "${PWD}\workspace:/workspace" `
  -v "${PWD}\output:/config/output" `
  reduce-cc <NombreProyecto> <ES-LSF|ES-SSF|ILP> <true|false>
```

- `<NombreProyecto>`: debe coincidir exactamente (mayúsculas incluidas)
  con el `<name>` del `.project` del proyecto, que debe estar ya en
  `workspace\<NombreProyecto>\` con su `.project`/`.classpath`.
- Solver: `ILP` (requiere CPLEX, es el que da mejores resultados),
  `ES-LSF`/`ES-SSF` (heurísticos — **ver aviso importante más abajo**).
- El tercer argumento (`generateGraphs`) es del programa original.

Smoke test rápido: coloca cualquier proyecto Java con `.project`/`.classpath`
en `workspace\<NombreProyecto>\` (si no los tiene, generarlos es tarea de
la tool `prepare_project` del servidor MCP, no de este contenedor) y:
```powershell
mkdir workspace, output -ea 0
docker run --rm -v "${PWD}\workspace:/workspace" -v "${PWD}\output:/config/output" reduce-cc <NombreProyecto> ILP false
```

**Modo "apply"**: aplica de verdad, sobre el archivo real, una extracción
ya calculada (con nombres personalizados en vez de los autogenerados).
Espera un `workspace/apply-request.properties` con `project`,
`relativePath`, `complexity`, `solution` y `names` (ver
`plugin/ReduceCognitiveComplexity/src/.../app/ApplyCodeExtractions.java`
para el formato exacto):
```powershell
docker run --rm -v "${PWD}\workspace:/workspace" -v "${PWD}\output:/config/output" reduce-cc apply
```
En la práctica, esto lo invoca el servidor MCP (tool `apply_names`), no
se suele ejecutar a mano.

## 4. Aviso importante: bug conocido en los solvers heurísticos

`ES-LSF` y `ES-SSF` devuelven sistemáticamente una solución vacía en
métodos con más de un bloque extraíble, aunque existan candidatos
factibles con reducción de complejidad real (confirmado con evidencia,
no es una sospecha). El fallo está aislado al mecanismo de generación de
combinaciones del propio proyecto original (`ExhaustiveEnumeration` +
`IteratorYield`, que simula `yield return` con hilos y usa `Thread.stop()`,
obsoleto en JDKs modernos) — no es nada de este Dockerfile ni de nuestra
integración. Detalle completo, con pasos de reproducción, en el informe
preparado para el autor original (pedidlo si no lo tenéis a mano).

**Usad `ILP` mientras este bug no se arregle.** Con CPLEX instalado
(sección 2), `ILP` funciona correctamente y encuentra soluciones óptimas.

## 5. Decisiones de diseño que conviene recordar

- **Java 25 en el runtime** (`eclipse-temurin:25-jdk-jammy`, JDK completo,
  no JRE): hace falta un JDK real porque JDT necesita `ct.sym` para
  resolver classpaths con `--release` cuando el proyecto analizado pide
  una versión de Java distinta a la del runtime. El propio bundle
  (`ReduceCognitiveComplexity`) sigue compilando con `release 17`
  (ver `pom.xml` raíz) — Java 25 solo ejecuta, no cambia eso.
- **`WorkspaceImporter`** existe porque `IProject.exists()` consulta el
  registro interno del workspace, no el disco: una carpeta con
  `.project` dentro de `/workspace` no se reconoce como proyecto sin un
  paso explícito de importación. Ver comentarios en
  `plugin/WorkspaceImporter/src/.../ImporterApplication.java`.
- **Xvfb en el runtime**: confirmado que hace falta de verdad (el
  plugin original inicializa un `Device`/`Color` de SWT al arrancar), no
  es solo precaución.
- **`target-platform/reduce-cc.target` apunta a `releases/2026-06/`**
  (fechado, no `releases/latest`) para que el build sea reproducible en
  el tiempo.
- **`workspace/` y `output/` no se suben al repo** (están en
  `.gitignore`): son estado de ejecución, no código. Los proyectos de
  caso de estudio (como GildedRose) se colocan ahí en tiempo de uso, no
  viven dentro de este repositorio.

## 6. Qué queda fuera de esta pieza (ver README.md raíz)

Esto (`reduce-cc`) es deliberadamente solo el motor de análisis/aplicación.
Todo lo demás vive en otros módulos del repositorio:

- Aplicar una extracción con nombres elegidos por un LLM: ya resuelto
  (`ApplyCodeExtractions` + modo `apply`), pero quien decide el nombre y
  orquesta la llamada es el servidor MCP, no este contenedor.
- Clonar repos, generar `.project`/`.classpath` si faltan, invocar este
  contenedor y exponerlo como tools: `mcp-server/`.
- Disparo automático ante un push de GitHub y apertura de PR: `github-bot/`.

Pendiente real, todavía sin resolver: el bug de los solvers heurísticos
(sección 4) y el soporte de Gradle en la generación de `.classpath`
(`mcp-server`, de momento solo detecta Gradle y avisa, no lo resuelve).
