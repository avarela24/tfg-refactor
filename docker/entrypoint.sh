#!/usr/bin/env bash
set -uo pipefail

# Uso: docker run ... reduce-cc <NombreProyecto> <ES-LSF|ES-SSF|ILP> <true|false>
#
# Dos pasos, dos aplicaciones Eclipse headless distintas:
#   1) WorkspaceImporter.import           -> registra en el workspace los
#      proyectos que ya estén en /workspace con un .project (ver
#      plugin/WorkspaceImporter). Sin esto, ReduceCognitiveComplexity no
#      "ve" el proyecto aunque los archivos estén físicamente ahí.
#   2) ReduceCognitiveComplexity.application -> el análisis real, tal cual
#      lo expone el proyecto original.
#
# Xvfb: aunque todo esto es headless, el plugin original inicializa un
# Device/Color de SWT al arrancar, así que necesita un display X real (o
# virtual) para no fallar con SWTError.

Xvfb :99 -screen 0 1280x1024x24 -nolisten tcp &
XVFB_PID=$!
export DISPLAY=:99
sleep 2

WORKSPACE_DIR="${WORKSPACE_DIR:-/workspace}"
LAUNCHER_JAR=$(find /opt/reduce-cc/plugins -maxdepth 1 -name 'org.eclipse.equinox.launcher_*.jar' | head -n1)

if [ -z "$LAUNCHER_JAR" ]; then
  echo "ERROR: no se encontró org.eclipse.equinox.launcher_*.jar en /opt/reduce-cc/plugins" >&2
  kill "$XVFB_PID" 2>/dev/null || true
  exit 1
fi

cd /config

echo "== Paso 1/2: registrando proyectos del workspace =="
java -jar "$LAUNCHER_JAR" -nosplash -consoleLog -data "$WORKSPACE_DIR" -application WorkspaceImporter.import

echo "== Paso 2/2: análisis de complejidad cognitiva =="
java \
  -Xmx8G \
  -Djava.library.path="${CPLEX_NATIVE_LIB_PATH:-/opt/cplex/bin/x86-64_linux}" \
  -jar "$LAUNCHER_JAR" \
  -nosplash -consoleLog \
  -data "$WORKSPACE_DIR" \
  -application ReduceCognitiveComplexity.application \
  "$@"
EXIT_CODE=$?

kill "$XVFB_PID" 2>/dev/null || true
exit $EXIT_CODE