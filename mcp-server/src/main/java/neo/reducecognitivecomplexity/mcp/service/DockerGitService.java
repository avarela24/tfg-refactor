package neo.reducecognitivecomplexity.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DockerGitService {

	private static final Logger LOGGER = Logger.getLogger(DockerGitService.class.getName());

	@Autowired
	private ReduceCcLock reduceCcLock;

	@Value("${reducecc.workspace-dir}")
	private String workspaceDir;

	@Value("${reducecc.pr-bot-url:http://127.0.0.1:3000/tfg-bot/pr-ready}")
	private String prBotUrl;

	// Reúne los archivos modificados y le pide al bot que abra la Pull Request.
	public String createPullRequest(String projectName, String owner, String repo, String baseBranch) throws Exception {
		List<String> modifiedPaths = listModifiedFiles(projectName);

		if (modifiedPaths.isEmpty()) {
			return "No hay ningún archivo modificado — no se ha abierto ninguna Pull Request.";
		}

		List<Map<String, String>> files = new ArrayList<>();
		for (String path : modifiedPaths) {
			String content = readFileContent(projectName, path);
			if (content == null) {
				LOGGER.warning("No se pudo leer " + path + " para la PR; se omite.");
				continue;
			}
			Map<String, String> file = new HashMap<>();
			file.put("path", path);
			file.put("content", content);
			files.add(file);
		}

		Map<String, Object> payload = new HashMap<>();
		payload.put("owner", owner);
		payload.put("repo", repo);
		payload.put("baseBranch", baseBranch);
		payload.put("files", files);

		return sendToBot(payload);
	}

	private String sendToBot(Map<String, Object> payload) throws Exception {
		String json = new ObjectMapper().writeValueAsString(payload);

		HttpClient client = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(10))
				.build();
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(prBotUrl))
				.timeout(Duration.ofMinutes(2))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
				.build();

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
		JsonNode result = new ObjectMapper().readTree(response.body());

		if (result.path("success").asBoolean(false)) {
			String prUrl = result.path("prUrl").asText(null);
			return prUrl != null
					? "Pull Request abierta correctamente: " + prUrl
					: "Solicitud procesada correctamente (sin archivos que subir).";
		} else {
			return "No se pudo abrir la Pull Request: " + result.path("error").asText("motivo desconocido");
		}
	}

	private List<String> listModifiedFiles(String projectName) throws Exception {
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

		return modified;
	}

	private String readFileContent(String projectName, String relativePath) {
		try {
			Path file = Paths.get(workspaceDir, projectName, relativePath);
			return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
		} catch (Exception e) {
			return null;
		}
	}
}