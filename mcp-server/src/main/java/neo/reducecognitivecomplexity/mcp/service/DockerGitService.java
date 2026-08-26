package neo.reducecognitivecomplexity.mcp.service;

import neo.reducecognitivecomplexity.mcp.model.FileContentResult;
import neo.reducecognitivecomplexity.mcp.model.ModifiedFilesResult;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Consulta qué archivos ha modificado apply_names, usando git dentro del propio clon. */
@Service
public class DockerGitService {

	@Autowired
	private ReduceCcLock reduceCcLock;

	@Value("${reducecc.workspace-dir}")
	private String workspaceDir;

	public ModifiedFilesResult listModifiedFiles(String projectName) throws Exception {
		Path projectDir = Paths.get(workspaceDir, projectName);

		List<String> command = List.of(
				"docker", "run", "--rm",
				"-v", projectDir.toAbsolutePath() + ":/project",
				"-w", "/project",
				"alpine/git",
				"status", "--porcelain");

		List<String> modified = new ArrayList<>();

		reduceCcLock.lock();
		try {
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.redirectErrorStream(true);
			Process process = pb.start();

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.length() > 3) {
						modified.add(line.substring(3).trim());
					}
				}
			}

			boolean finished = process.waitFor(1, TimeUnit.MINUTES);
			if (!finished) {
				process.destroyForcibly();
				throw new IllegalStateException("git status no terminó a tiempo.");
			}
		} finally {
			reduceCcLock.unlock();
		}

		ModifiedFilesResult result = new ModifiedFilesResult();
		result.modifiedFiles = modified;
		return result;
	}

	public FileContentResult getFileContent(String projectName, String relativePath) throws Exception {
		Path file = Paths.get(workspaceDir, projectName, relativePath);
		FileContentResult result = new FileContentResult();
		result.relativePath = relativePath;
		if (!Files.exists(file)) {
			result.success = false;
			result.message = "No existe: " + file;
			return result;
		}
		result.success = true;
		result.content = Files.readString(file, StandardCharsets.UTF_8);
		return result;
	}
}