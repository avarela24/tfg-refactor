package neo.reducecognitivecomplexity.mcp;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Escribe la petición de aplicación (apply-request.properties) en el
 * workspace compartido y ejecuta el modo "apply" del contenedor reduce-cc.
 */
@Service
public class DockerApplyService {

	@Value("${reducecc.workspace-dir}")
	private String workspaceDir;

	@Value("${reducecc.output-dir}")
	private String outputDir;

	@Value("${reducecc.docker-image:reduce-cc}")
	private String dockerImage;

	public ApplyResult applyNames(String projectName, String relativePath, int complexity,
			String solution, List<String> names) throws Exception {

		String namesLine = (names == null || names.isEmpty()) ? "" : String.join(",", names);

		String requestContent = "project=" + projectName + "\n"
				+ "relativePath=" + relativePath + "\n"
				+ "complexity=" + complexity + "\n"
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

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.redirectErrorStream(true);
		Process process = pb.start();

		StringBuilder fullOutput = new StringBuilder();
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

		ApplyResult result = new ApplyResult();
		result.success = fullOutput.toString().contains("Extracción aplicada correctamente");
		result.log = fullOutput.toString();
		return result;
	}
}