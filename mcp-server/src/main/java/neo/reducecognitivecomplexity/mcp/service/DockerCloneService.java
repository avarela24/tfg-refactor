package neo.reducecognitivecomplexity.mcp.service;

import neo.reducecognitivecomplexity.mcp.model.CloneResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DockerCloneService {

	@Autowired
	private ReduceCcLock reduceCcLock;

	@Value("${reducecc.workspace-dir}")
	private String workspaceDir;

	public CloneResult cloneProject(String gitUrl, String projectName, String branch, String commit, boolean forceClean) throws Exception {
		String resolvedName = (projectName == null || projectName.isBlank())
				? deriveProjectName(gitUrl)
				: projectName;

		Path targetDir = Paths.get(workspaceDir, resolvedName);
		if (Files.exists(targetDir) && !forceClean) {
			return failure(resolvedName, "Ya existe una carpeta '" + resolvedName
					+ "' en el workspace. Usa forceClean=true para volver a clonar desde cero.");
		}

		StringBuilder log = new StringBuilder();
		boolean cloned;

		reduceCcLock.lock();
		try {
			if (Files.exists(targetDir)) {
				deleteRecursively(targetDir);
			}

			List<String> command = new ArrayList<>(List.of(
					"docker", "run", "--rm",
					"-v", workspaceDir + ":/workspace",
					"alpine/git",
					"clone"));
			if (commit == null || commit.isBlank()) {
				command.add("--depth");
				command.add("1");
				if (branch != null && !branch.isBlank()) {
					command.add("--branch");
					command.add(branch);
				}
			}
			command.add(gitUrl);
			command.add("/workspace/" + resolvedName);

			if (!runGitCommand(command, log)) {
				return failure(resolvedName, "Fallo al clonar. Salida:\n" + log);
			}

			if (commit != null && !commit.isBlank()) {
				if (!runGitCommand(gitCommand(resolvedName, "checkout", commit), log)) {
					return failure(resolvedName, "Clonado, pero falló el checkout a " + commit + ". Salida:\n" + log);
				}
			}

			cloned = Files.isDirectory(targetDir) && hasContent(targetDir);
		} finally {
			reduceCcLock.unlock();
		}

		CloneResult result = new CloneResult();
		result.projectName = resolvedName;
		result.success = cloned;
		result.message = cloned
				? "Repositorio clonado en workspace/" + resolvedName
						+ (commit != null && !commit.isBlank() ? ", en el commit " + commit : "")
				: "Fallo al clonar. Salida:\n" + log;
		return result;
	}

	private List<String> gitCommand(String projectDirName, String... gitArgs) {
		List<String> command = new ArrayList<>(List.of(
				"docker", "run", "--rm",
				"-v", workspaceDir + ":/workspace",
				"-w", "/workspace/" + projectDirName,
				"alpine/git"));
		command.addAll(List.of(gitArgs));
		return command;
	}

	private boolean runGitCommand(List<String> command, StringBuilder log) throws Exception {
		ProcessBuilder pb = new ProcessBuilder(command);
		pb.redirectErrorStream(true);
		Process process = pb.start();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			String line;
			while ((line = reader.readLine()) != null) {
				log.append(line).append("\n");
			}
		}
		boolean finished = process.waitFor(5, TimeUnit.MINUTES);
		if (!finished) {
			process.destroyForcibly();
			throw new IllegalStateException("Comando git no terminó a tiempo: " + command);
		}
		return process.exitValue() == 0;
	}

	private CloneResult failure(String projectName, String message) {
		CloneResult result = new CloneResult();
		result.success = false;
		result.projectName = projectName;
		result.message = message;
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

	private void deleteRecursively(Path dir) throws Exception {
		try (var walk = Files.walk(dir)) {
			walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
				File f = p.toFile();
				f.setWritable(true);
				if (!f.delete()) {
					throw new RuntimeException("No se pudo borrar: " + p);
				}
			});
		}
	}
}