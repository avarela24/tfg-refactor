package neo.reducecognitivecomplexity.mcp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Clona un repositorio Git en el workspace local mediante un contenedor
 * efímero (alpine/git) — no depende de que git esté instalado en el host,
 * y nunca toca el remoto: es una copia de trabajo local, como pedía el
 * "Modo B" (repos ajenos o propios, sin escribir nunca al original).
 */
@Service
public class DockerCloneService {

	@Value("${reducecc.workspace-dir}")
	private String workspaceDir;

	public CloneResult cloneProject(String gitUrl, String projectName, String branch) throws Exception {
		String resolvedName = (projectName == null || projectName.isBlank())
				? deriveProjectName(gitUrl)
				: projectName;

		Path targetDir = Paths.get(workspaceDir, resolvedName);
		if (Files.exists(targetDir)) {
			CloneResult result = new CloneResult();
			result.success = false;
			result.projectName = resolvedName;
			result.message = "Ya existe una carpeta '" + resolvedName
					+ "' en el workspace. Bórrala primero si quieres re-clonar.";
			return result;
		}

		List<String> command = new ArrayList<>(List.of(
				"docker", "run", "--rm",
				"-v", workspaceDir + ":/workspace",
				"alpine/git",
				"clone", "--depth", "1"));
		if (branch != null && !branch.isBlank()) {
			command.add("--branch");
			command.add(branch);
		}
		command.add(gitUrl);
		command.add("/workspace/" + resolvedName);

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.redirectErrorStream(true);
		Process process = pb.start();

		StringBuilder log = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			String line;
			while ((line = reader.readLine()) != null) {
				log.append(line).append("\n");
			}
		}

		boolean finished = process.waitFor(5, TimeUnit.MINUTES);
		if (!finished) {
			process.destroyForcibly();
			throw new IllegalStateException("El clonado no terminó a tiempo.");
		}

		boolean cloned = Files.isDirectory(targetDir) && hasContent(targetDir);

		CloneResult result = new CloneResult();
		result.projectName = resolvedName;
		result.success = cloned;
		result.message = cloned
				? "Repositorio clonado en workspace/" + resolvedName
				: "Fallo al clonar. Salida:\n" + log;
		return result;
	}

	private boolean hasContent(Path dir) throws Exception {
		try (var stream = Files.list(dir)) {
			return stream.findAny().isPresent();
		}
	}

	private String deriveProjectName(String gitUrl) {
		String cleaned = gitUrl.trim();
		if (cleaned.endsWith(".git")) {
			cleaned = cleaned.substring(0, cleaned.length() - 4);
		}
		if (cleaned.endsWith("/")) {
			cleaned = cleaned.substring(0, cleaned.length() - 1);
		}
		int lastSlash = cleaned.lastIndexOf('/');
		return lastSlash >= 0 ? cleaned.substring(lastSlash + 1) : cleaned;
	}
}