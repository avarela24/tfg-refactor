package neo.reducecognitivecomplexity.mcp.service;

import neo.reducecognitivecomplexity.mcp.model.ApplyResult;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Escribe la petición de aplicación (apply-request.properties) en el
 * workspace compartido y ejecuta el modo "apply" del contenedor reduce-cc.
 */
@Service
public class DockerApplyService {

	@Autowired
	private ReduceCcLock reduceCcLock;

	@Value("${reducecc.workspace-dir}")
	private String workspaceDir;

	@Value("${reducecc.output-dir}")
	private String outputDir;

	@Value("${reducecc.docker-image:reduce-cc}")
	private String dockerImage;

	public ApplyResult applyNames(String projectName, String relativePath, String solution,
			List<String> names) throws Exception {

		String namesLine = (names == null || names.isEmpty()) ? "" : String.join(",", names);

		String requestContent = "project=" + projectName + "\n"
				+ "relativePath=" + relativePath + "\n"
				+ "solution=" + solution + "\n"
				+ "names=" + namesLine + "\n";

		try (FileWriter writer = new FileWriter(workspaceDir + "/apply-request.properties")) {
			writer.write(requestContent);
		}

		List<String> command = List.of(
				"docker", "run", "--rm",
				"-v", workspaceDir + ":/workspace",
				"-v", outputDir + ":/config/output",
				dockerImage, "apply");

		StringBuilder fullOutput = new StringBuilder();
		int exitCode;

		reduceCcLock.lock();
		try {
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.redirectErrorStream(true);
			Process process = pb.start();

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					fullOutput.append(line).append("\n");
				}
			}

			boolean finished = process.waitFor(5, TimeUnit.MINUTES);
			if (!finished) {
				process.destroyForcibly();
				throw new IllegalStateException("La aplicación de la extracción no terminó a tiempo.");
			}
			exitCode = process.exitValue();
		} finally {
			reduceCcLock.unlock();
		}

		ApplyResult result = new ApplyResult();
		result.success = exitCode == 0 && fullOutput.toString().contains("Extracción aplicada correctamente");
		result.log = summarizeLog(fullOutput.toString());
		return result;
	}

	/**
	 * Se queda solo con las líneas realmente informativas del log (qué se
	 * extrajo, con qué nombre, y el resultado final) — descarta el arranque
	 * de Eclipse/OSGi, idéntico en cada llamada y sin ningún valor para
	 * quien lo lee después (el LLM). Si por lo que sea no coincide ninguna
	 * línea con lo esperado, se devuelve el log completo como red de
	 * seguridad, para no ocultar información en un caso inesperado.
	 */
	private String summarizeLog(String fullLog) {
		StringBuilder summary = new StringBuilder();
		for (String line : fullLog.split("\n")) {
			if (line.contains("Extracting code")
					|| line.contains("New method name")
					|| line.contains("SEVERE:")
					|| line.contains("Extracción aplicada correctamente")
					|| line.contains("Extraction applied successfully")
					|| line.contains("No se pudo aplicar la extracción")) {
				summary.append(line.trim()).append("\n");
			}
		}
		return summary.length() > 0 ? summary.toString() : fullLog;
	}
}