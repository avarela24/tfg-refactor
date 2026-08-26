package neo.reducecognitivecomplexity.app;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.dom.CompilationUnit;

import neo.reducecognitivecomplexity.core.Solution;
import neo.reducecognitivecomplexity.core.eclipse.Utils;

/**
 * Aplica la extracción ya calculada por un solver (ES-LSF, ES-SSF
 * o ILP), a partir del "solution string" que esos solvers escriben
 * en la columna "solution" del CSV de resultados.
 *
 * Entrada: un properties file (ruta por defecto
 * /workspace/apply-request.properties, o la indicada en
 * -DapplyRequest=...), con:
 *   project=NombreDelProyecto
 *   relativePath=src/.../Clase.java   (relativo a la raíz del proyecto)
 *   complexity=17                     (complejidad cognitiva del método)
 *   solution=[[393]]                  (columna "solution" del CSV)
 *   names=nombre1,nombre2             (nombres de los métodos, opcional)
 */
public class ApplyCodeExtractions implements IApplication {

	private static final Logger LOGGER = Logger.getLogger(ApplyCodeExtractions.class.getName());

	@Override
	public Object start(IApplicationContext context) throws Exception {
		Utils.initializeEnvironment();
		Utils.initializeWorkspace();
		Utils.disableAutoBuild();

		// Leer el archivo de petición
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

		// Extraer y validar las propiedades de la petición
		String project = props.getProperty("project");
		String relativePath = props.getProperty("relativePath");
		String complexityStr = props.getProperty("complexity");
		String solutionString = props.getProperty("solution");

		if (project == null || relativePath == null || solutionString == null || complexityStr == null) {
			LOGGER.severe("Faltan propiedades obligatorias (project, relativePath, complexity, solution) en "
					+ requestPath);
			return -1;
		}

		int complexity;
		try {
			complexity = Integer.parseInt(complexityStr);
		} catch (NumberFormatException e) {
			LOGGER.severe("El valor de 'complexity' no es un número válido: " + complexityStr);
			return -1;
		}

		// 'names' es opcional: sin él, se usan los nombres autogenerados
		String namesProperty = props.getProperty("names");
		List<String> customNames = null;
		if (namesProperty != null && !namesProperty.isBlank()) {
			customNames = Arrays.stream(namesProperty.split(","))
					.map(String::trim)
					.collect(Collectors.toList());
		}

		// Asegurar que el proyecto está registrado, abierto y compilado.
		// getProject() devuelve null (sin lanzar excepción) si el proyecto
		// no existe en el workspace, así que hay que comprobarlo aquí y no
		// dejar que el fallo aparezca más adelante con un mensaje confuso.
		if (Utils.getProject(project) == null) {
			LOGGER.severe("El proyecto '" + project + "' no existe en el workspace (¿se registró con WorkspaceImporter?).");
			return -1;
		}

		// Localizar el archivo dentro del workspace
		String workspaceRelativePath = project + File.separator
				+ relativePath.replace('/', File.separatorChar);

		CompilationUnit cu = neo.reducecognitivecomplexity.core.jdt.Utils
				.createCompilationUnitFromFileInWorkspace(workspaceRelativePath);

		if (cu == null || cu.getJavaElement() == null || !cu.getJavaElement().exists()) {
			LOGGER.severe("No se pudo resolver el archivo en el workspace: " + workspaceRelativePath
					+ " (¿relativePath incorrecto?)");
			return -1;
		}

		// Reconstruir la solución sobre ese archivo y comprobar que de
		// verdad contiene algo que extraer
		Solution solution = new Solution(cu, solutionString, complexity);

		if (solution.getSequenceList() == null || solution.getSequenceList().isEmpty()) {
			LOGGER.severe("La solución no contiene ninguna secuencia válida tras resolverla contra el archivo "
					+ workspaceRelativePath + " — revisa que 'solution' y 'relativePath' correspondan al mismo análisis.");
			return -1;
		}

		// Aplicar la extracción sobre el archivo
		boolean applied = solution.applyExtractMethodsRefactoring(true, customNames);

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
		// Nada que limpiar: la aplicación no mantiene ningún estado propio.
	}
}