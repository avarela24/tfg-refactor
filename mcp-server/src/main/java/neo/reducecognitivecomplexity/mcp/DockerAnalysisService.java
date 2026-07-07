package neo.reducecognitivecomplexity.mcp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Invoca el contenedor reduce-cc y convierte su salida de consola (que ya
 * incluye complejidad, solución del solver y el código de cada extracción,
 * resuelto por JDT) en un resultado estructurado — sin reimplementar nada
 * de análisis de AST en el lado Java/Spring.
 */
@Service
public class DockerAnalysisService {

	@Value("${reducecc.workspace-dir}")
	private String workspaceDir;

	@Value("${reducecc.output-dir}")
	private String outputDir;

	@Value("${reducecc.docker-image:reduce-cc}")
	private String dockerImage;

	public AnalysisResult analyzeProject(String projectName, String solver) throws Exception {
		List<String> command = List.of(
				"docker", "run", "--rm",
				"-v", workspaceDir + ":/workspace",
				"-v", "mcp-maven-cache:/root/.m2",
				"-v", outputDir + ":/config/output",
				dockerImage, projectName, solver, "false");

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

		boolean finished = process.waitFor(15, TimeUnit.MINUTES);
		if (!finished) {
			process.destroyForcibly();
			throw new IllegalStateException("El análisis no terminó a tiempo (más de 15 minutos).");
		}

		return parseOutput(projectName, solver, fullOutput.toString());
	}

	private AnalysisResult parseOutput(String projectName, String solver, String output) {
		List<MethodCandidate> methods = new ArrayList<>();

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

			MethodCandidate candidate = new MethodCandidate();
			candidate.methodName = names.get(i);
			candidate.complexity = complexities.get(i);

			Matcher solutionMatcher = Pattern.compile("Refactoring operations:\\s*\\n(.*)").matcher(block);
			if (solutionMatcher.find()) {
				candidate.solution = solutionMatcher.group(1).trim();
			}

			Matcher extractionMatcher = Pattern
					.compile("EXTRACTION (\\d+)\\s*\\n.*?CODE: \\[(.*?)\\]\\s*\\n", Pattern.DOTALL)
					.matcher(block);
			while (extractionMatcher.find()) {
				ExtractionSnippet snippet = new ExtractionSnippet();
				snippet.index = Integer.parseInt(extractionMatcher.group(1));
				snippet.code = extractionMatcher.group(2).trim();
				candidate.extractions.add(snippet);
			}

			methods.add(candidate);
		}

		AnalysisResult result = new AnalysisResult();
		result.project = projectName;
		result.solver = solver;
		result.methods = methods;
		return result;
	}
}