# Build Linux/Tycho + Docker para SoftwareCognitiveComplexityReducer

## 1. Estructura del repositorio

```
.
├── pom.xml                        Reactor Tycho (raíz)
├── target-platform/
│   └── reduce-cc.target           Plataforma de compilación (linux/gtk/x86_64)
├── plugin/
│   ├── ReduceCognitiveComplexity/ Cognitive Complexity Reduction Tool
│   └── WorkspaceImporter/         Registra proyectos en el workspace
├── product/                       Ensambla el producto Eclipse headless ejecutable
├── docker/
│   ├── Dockerfile                 Build multi-stage (Tycho + CPLEX -> runtime)
│   ├── entrypoint.sh              Orquesta los 2 pasos (importar + analizar)
│   ├── config.properties          Config del analizador dentro del contenedor
│   └── docker-compose.snippet.yml Cómo invocarlo desde un futuro MCP server/n8n TODO
├── cplex-linux/
│   ├── cplex-installer.bin        Instalador CPLEX para Linux
│   └── install.properties         Config de instalación silenciosa
└── examples/
    └── ProyectoDeJuguete/          Proyecto Java mínimo para smoke tests rápidos
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

Smoke test rápido con el proyecto de ejemplo incluido:
```powershell
mkdir workspace, output -ea 0
Copy-Item -Recurse .\examples\ProyectoDeJuguete .\workspace\
docker run --rm -v "${PWD}\workspace:/workspace" -v "${PWD}\output:/config/output" reduce-cc ProyectoDeJuguete ILP false
```

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

## 6. Pendiente

- Aplicar de verdad la extracción que calcula el solver (`Solution.
  applyExtractMethodsRefactoring()` existe en el código original pero
  ninguna aplicación lo invoca todavía) — es el enganche natural para que
  el LLM nombre los métodos extraídos.
- Servidor MCP en Spring que orqueste todo esto.
- Generación automática de `.project`/`.classpath` para repos de GitHub
  que no los traigan ya (Maven/Gradle "a pelo").
