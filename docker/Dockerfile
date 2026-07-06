# ---------- STAGE 1: build the headless product with Tycho ----------
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# ---------------------------------------------------------------------------
# CPLEX es una dependencia de COMPILACIÓN, no solo de ejecución: las clases
# neo.reducecognitivecomplexity.core.solvers.ilp.IlpSolver / Model importan
# ilog.cplex.IloCplex e ilog.concert.* directamente. El build de Tycho FALLA
# si no hay un cplex.jar disponible, aunque solo penséis usar los solvers
# heurísticos (ES-LSF/ES-SSF).
#
# Se instala aquí abajo (ver "Instalación silenciosa de CPLEX") a partir del
# instalador oficial para Linux x86-64. Ese instalador (cplex-linux/*.bin)
# es software con licencia: nunca debe subirse al repo (está en .gitignore)
# ni quedar embebido en una imagen que se comparta o publique.
# ---------------------------------------------------------------------------

COPY .mvn .mvn
COPY pom.xml .
COPY target-platform target-platform
COPY plugin plugin

# --- Instalación silenciosa de CPLEX Community Edition para Linux ---
COPY cplex-linux/cplex-installer.bin /tmp/cplex-installer.bin
COPY cplex-linux/install.properties /tmp/install.properties
RUN apt-get update && apt-get install -y --no-install-recommends default-jre && \
    chmod +x /tmp/cplex-installer.bin && \
    /tmp/cplex-installer.bin -f /tmp/install.properties && \
    rm /tmp/cplex-installer.bin /tmp/install.properties && \
    apt-get purge -y default-jre && apt-get autoremove -y && rm -rf /var/lib/apt/lists/*

RUN cp /opt/cplex/cplex/lib/cplex.jar plugin/ReduceCognitiveComplexity/lib/cplex.jar

RUN find plugin/ReduceCognitiveComplexity/src/test/resources -maxdepth 1 -name '*.java' -exec mv {} {}.txt \;

COPY product product

RUN mvn -B -ntp verify -Dmaven.test.skip=true

RUN mkdir -p /opt/product && \
    cp -r product/target/products/ReduceCognitiveComplexityProduct/linux/gtk/x86_64/* /opt/product/

# ---------- STAGE 2: minimal runtime ----------
FROM eclipse-temurin:25-jdk-jammy

# xvfb como red de seguridad: el plugin depende de org.eclipse.ui/SWT aunque
# se ejecute headless. Si tras probarlo confirmáis que ningún código toca un
# Display real, se puede quitar y lanzar `java` directamente.
RUN apt-get update && apt-get install -y --no-install-recommends \
      xvfb libgtk-3-0 libxtst6 libxrender1 libxi6 && \
    rm -rf /var/lib/apt/lists/*

COPY --from=builder /opt/product /opt/reduce-cc
COPY --from=builder /opt/cplex/cplex/bin/x86-64_linux /opt/cplex/bin/x86-64_linux

WORKDIR /config
COPY docker/config.properties /config/config.properties

COPY docker/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
