package neo.reducecognitivecomplexity.app;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.logging.Logger;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.dom.CompilationUnit;

import neo.reducecognitivecomplexity.core.Solution;
import neo.reducecognitivecomplexity.core.eclipse.Utils;

/**
 * Aplica de verdad una extracción ya calculada por un solver (ES-LSF,
 * ES-SSF o ILP), a partir del "solution string" que esos solvers ya
 * escriben en la columna "solution" de su CSV de resultados.
 *
 * Rellena el punto de extensión que el propio plugin.xml original ya
 * declaraba (ReduceCognitiveComplexity.applyCodeExtractions) pero que
 * nunca tuvo clase asociada: Solution.applyExtractMethodsRefactoring()
 * ya existía y funciona, solo faltaba quien lo invocase.
 *
 * Entrada: un properties file (ruta por defecto
 * /workspace/apply-request.properties, o la indicada en
 * -DapplyRequest=...), con:
 *   project=NombreDelProyecto
 *   relativePath=src/.../Clase.java   (relativo a la raíz del proyecto)
 *   complexity=17                     (complejidad conocida del método)
 *   solution=[[393]]                  (tal cual la columna "solution" del CSV)
 *
 * De momento usa siempre los nombres autogenerados (_extraction_N) — el
 * enganche para nombres elegidos por el LLM es el siguiente paso, una vez
 * confirmado que esto escribe bien el archivo.
 */
public class ApplyCodeExtractions implements IApplication {

	private static final Logger LOGGER = Logger.getLogger(ApplyCodeExtractions.class.getName());

	@Override
	public Object start(IApplicationContext context) throws Exception {
		Utils.initializeEnvironment();
		Utils.initializeWorkspace();
		Utils.disableAutoBuild();

		String requestPath = System.getProperty("applyRequest", "/workspace/apply-request.properties");
		File requestFile = new File(requestPath);
		if (!requestFile.exists()) {
			LOGGER.severe("No se encontró el archivo de petición: " + requestPath);
			return -1;
		}

		Properties props = new Properties();
		try (FileInputStream in = new FileInputStream(requestFile)) {
			props.load(in);
		}

		String project = props.getProperty("project");
		String relativePath = props.getProperty("relativePath");
		String complexityStr = props.getProperty("complexity");
		String solutionString = props.getProperty("solution");

		if (project == null || relativePath == null || solutionString == null || complexityStr == null) {
			LOGGER.severe("Faltan propiedades obligatorias (project, relativePath, complexity, solution) en "
					+ requestPath);
			return -1;
		}

		// Asegura que el proyecto está registrado, abierto y compilado
		// (mismo helper que usa el resto del plugin antes de analizar).
		Utils.getProject(project);

		String workspaceRelativePath = project + File.separator
				+ relativePath.replace('/', File.separatorChar);

		CompilationUnit cu = neo.reducecognitivecomplexity.core.jdt.Utils
				.createCompilationUnitFromFileInWorkspace(workspaceRelativePath);

		int complexity = Integer.parseInt(complexityStr);
		Solution solution = new Solution(cu, solutionString, complexity);

		boolean applied = solution.applyExtractMethodsRefactoring(true);

		if (applied) {
			LOGGER.info("Extracción aplicada correctamente sobre el método: " + solution.getMethodName());
			return IApplication.EXIT_OK;
		} else {
			LOGGER.severe("No se pudo aplicar la extracción.");
			return -1;
		}
	}

	@Override
	public void stop() {
		// Nada que limpiar.
	}
}