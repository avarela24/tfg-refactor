package neo.reducecognitivecomplexity.mcp.service;

import neo.reducecognitivecomplexity.mcp.model.AnalysisResult;
import neo.reducecognitivecomplexity.mcp.model.ExtractionSnippet;
import neo.reducecognitivecomplexity.mcp.model.FileAnalysis;
import neo.reducecognitivecomplexity.mcp.model.MethodCandidate;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DockerAnalysisService {

	private static final Logger LOGGER = Logger.getLogger(DockerAnalysisService.class.getName());

	@Autowired
	private ReduceCcLock reduceCcLock;

	@Value("${reducecc.workspace-dir}")
	private String workspaceDir;

	@Value("${reducecc.output-dir}")
	private String outputDir;

	@Value("${reducecc.docker-image:reduce-cc}")
	private String dockerImage;

	@Value("${reducecc.analysis-callback-url-bot:http://127.0.0.1:5678/webhook/analysis-done}")
	private String botCallbackUrl;

	@Value("${reducecc.analysis-callback-url-chat:http://127.0.0.1:5678/webhook/analysis-done-chat}")
	private String chatCallbackUrl;

	private final ExecutorService analysisExecutor = Executors.newCachedThreadPool();

	// Lanza el análisis en segundo plano y avisa a un webhook u otro según isBotMode.
	public String startAnalysis(String projectName, String solver, boolean isBotMode,
			String owner, String repo, String baseBranch) {
		LOGGER.info("start_analysis recibida: projectName=" + projectName + " solver=" + solver
				+ " isBotMode=" + isBotMode);

		analysisExecutor.submit(() -> {
			try {
				AnalysisResult result = runAnalysis(projectName, solver);
				if (isBotMode) {
					notify(botCallbackUrl, botPayload(projectName, owner, repo, baseBranch, "done", result, null));
				} else {
					notify(chatCallbackUrl, chatPayload(projectName, "done", result, null));
				}
			} catch (Throwable t) {
				LOGGER.log(Level.SEVERE, "Fallo analizando " + projectName, t);
				if (isBotMode) {
					notify(botCallbackUrl, botPayload(projectName, owner, repo, baseBranch, "failed", null, String.valueOf(t.getMessage())));
				} else {
					notify(chatCallbackUrl, chatPayload(projectName, "failed", null, String.valueOf(t.getMessage())));
				}
			}
		});

		return "Análisis de " + projectName + " iniciado en segundo plano.";
	}

	private Map<String, Object> chatPayload(String projectName, String status, AnalysisResult result, String error) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("projectName", projectName);
		payload.put("status", status);
		payload.put("result", result);
		payload.put("error", error);
		return payload;
	}

	private Map<String, Object> botPayload(String projectName, String owner, String repo, String baseBranch,
			String status, AnalysisResult result, String error) {
		Map<String, Object> payload = chatPayload(projectName, status, result, error);
		payload.put("owner", owner);
		payload.put("repo", repo);
		payload.put("baseBranch", baseBranch);
		return payload;
	}
	
	private void notify(String callbackUrl, Map<String, Object> payload) {
		try {
			String json = new ObjectMapper().writeValueAsString(payload);
			HttpClient client = HttpClient.newBuilder()
					.version(HttpClient.Version.HTTP_1_1)
					.connectTimeout(java.time.Duration.ofSeconds(10))
					.build();
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(callbackUrl))
					.timeout(java.time.Duration.ofMinutes(2))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
					.build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			LOGGER.info("Aviso enviado a " + callbackUrl + " para " + payload.get("projectName")
					+ " (status=" + payload.get("status") + "): HTTP " + response.statusCode());
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "No se pudo avisar a " + callbackUrl + " del resultado de "
					+ payload.get("projectName"), e);
		}
	}

	// Invoca reduce-cc y espera a que termine.
	private AnalysisResult runAnalysis(String projectName, String solver) throws Exception {
		List<String> command = List.of(
				"docker", "run", "--rm",
				"-v", workspaceDir + ":/workspace",
				"-v", "mcp-maven-cache:/root/.m2",
				"-v", outputDir + ":/config/output",
				dockerImage, projectName, solver, "false");

		reduceCcLock.lock();
		Path logFile = null;
		try {
			logFile = Files.createTempFile("reduce-cc-" + projectName + "-", ".log");
			LOGGER.info(">>> Salida del análisis redirigida a: " + logFile);

			ProcessBuilder pb = new ProcessBuilder(command);
			pb.redirectErrorStream(true);
			pb.redirectOutput(logFile.toFile());
			Process process = pb.start();
			LOGGER.info(">>> Proceso Docker arrancado con éxito, PID=" + process.pid());

			long stallLimitMillis = TimeUnit.MINUTES.toMillis(30);
			while (process.isAlive()) {
				Thread.sleep(TimeUnit.SECONDS.toMillis(30));
				long silence = System.currentTimeMillis() - Files.getLastModifiedTime(logFile).toMillis();
				if (silence > stallLimitMillis) {
					process.destroyForcibly();
					throw new IllegalStateException(
							"El análisis lleva más de 30 minutos sin ninguna señal de actividad — parece atascado, no simplemente lento.");
				}
			}

			int exitCode = process.exitValue();
			String fullOutput = Files.readString(logFile, StandardCharsets.UTF_8);
			LOGGER.info(">>> Proceso Docker terminado, exitCode=" + exitCode + ", " + fullOutput.length() + " caracteres de salida");

			return parseOutput(projectName, fullOutput);
		} finally {
			reduceCcLock.unlock();
			if (logFile != null) {
				try {
					Files.deleteIfExists(logFile);
				} catch (Exception e) {
					LOGGER.warning("No se pudo borrar el archivo temporal " + logFile + ": " + e.getMessage());
				}
			}
		}
	}

	private AnalysisResult parseOutput(String projectName, String output) {
		Map<String, FileAnalysis> byFile = new LinkedHashMap<>();
		Map<String, List<String>> rawSolutionsByFile = new LinkedHashMap<>();

		Pattern methodHeader = Pattern.compile("Processing method: (\\S+) \\(CC: (\\d+)\\)");
		Matcher headerMatcher = methodHeader.matcher(output);

		List<Integer> starts = new ArrayList<>();
		List<String> names = new ArrayList<>();
		List<Integer> complexities = new ArrayList<>();
		while (headerMatcher.find()) {
			starts.add(headerMatcher.start());
			names.add(headerMatcher.group(1));
			complexities.add(Integer.parseInt(headerMatcher.group(2)));
		}

		for (int i = 0; i < starts.size(); i++) {
			int start = starts.get(i);
			int end = (i + 1 < starts.size()) ? starts.get(i + 1) : output.length();
			String block = output.substring(start, end);

			Matcher solutionMatcher = Pattern.compile("Refactoring operations:\\s*\\n(.*)").matcher(block);
			String solution = solutionMatcher.find() ? solutionMatcher.group(1).trim() : null;

			if (solution == null || solution.equals("[]")) {
				continue;
			}

			MethodCandidate candidate = new MethodCandidate();
			candidate.methodName = names.get(i);
			candidate.complexity = complexities.get(i);

			Matcher extractionMatcher = Pattern
					.compile("EXTRACTION (\\d+)\\s*\\n.*?CODE: \\[(.*?)\\]\\s*\\n", Pattern.DOTALL)
					.matcher(block);
			while (extractionMatcher.find()) {
				ExtractionSnippet snippet = new ExtractionSnippet();
				snippet.index = Integer.parseInt(extractionMatcher.group(1));
				snippet.code = extractionMatcher.group(2).trim();
				candidate.extractions.add(snippet);
			}

			Matcher pathMatcher = Pattern.compile(
					"COMPILATION UNIT /" + Pattern.quote(projectName) + "/(.+)").matcher(block);
			if (!pathMatcher.find()) {
				continue;
			}
			String relativePath = pathMatcher.group(1).trim();

			FileAnalysis fileAnalysis = byFile.computeIfAbsent(relativePath, path -> {
				FileAnalysis fa = new FileAnalysis();
				fa.relativePath = path;
				fa.classCode = readClassCode(projectName, path);
				fa.methods = new ArrayList<>();
				return fa;
			});
			fileAnalysis.methods.add(candidate);
			rawSolutionsByFile.computeIfAbsent(relativePath, k -> new ArrayList<>()).add(solution);
		}

		for (FileAnalysis fileAnalysis : byFile.values()) {
			List<String> allGroups = new ArrayList<>();
			for (String rawSolution : rawSolutionsByFile.get(fileAnalysis.relativePath)) {
				allGroups.addAll(extractSequenceGroups(rawSolution));
			}
			allGroups.sort(Comparator.comparingInt(DockerAnalysisService::firstOffsetOf));
			fileAnalysis.solution = "[" + String.join(", ", allGroups) + "]";
		}

		AnalysisResult result = new AnalysisResult();
		result.project = projectName;
		result.files = new ArrayList<>(byFile.values());
		return result;
	}

	private String readClassCode(String projectName, String relativePath) {
		try {
			Path file = Paths.get(workspaceDir, projectName, relativePath);
			return Files.readString(file, StandardCharsets.UTF_8);
		} catch (Exception e) {
			LOGGER.warning("No se pudo leer el código completo de la clase (" + relativePath + "): " + e.getMessage());
			return null;
		}
	}

	private static List<String> extractSequenceGroups(String solution) {
		List<String> groups = new ArrayList<>();
		Matcher m = Pattern.compile("\\[[^\\[\\]]*\\]").matcher(solution);
		while (m.find()) {
			groups.add(m.group());
		}
		return groups;
	}

	private static int firstOffsetOf(String group) {
		Matcher m = Pattern.compile("\\d+").matcher(group);
		return m.find() ? Integer.parseInt(m.group()) : Integer.MAX_VALUE;
	}
}