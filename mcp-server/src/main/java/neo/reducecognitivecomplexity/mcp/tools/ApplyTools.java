package neo.reducecognitivecomplexity.mcp.tools;

import neo.reducecognitivecomplexity.mcp.model.ApplyResult;
import neo.reducecognitivecomplexity.mcp.service.DockerApplyService;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class ApplyTools {

	private final DockerApplyService dockerApplyService;

	public ApplyTools(DockerApplyService dockerApplyService) {
		this.dockerApplyService = dockerApplyService;
	}

	@McpTool(name = "apply_names", description = "Aplica de verdad, sobre el archivo real del proyecto, la extracción de método calculada por analyze_project, usando los nombres indicados para cada método extraído en vez de los autogenerados.")
	public ApplyResult applyNames(
			@McpToolParam(description = "Nombre del proyecto", required = true) String projectName,
			@McpToolParam(description = "Ruta del archivo .java relativa a la raíz del proyecto", required = true) String relativePath,
			@McpToolParam(description = "Complejidad cognitiva conocida del método", required = true) int complexity,
			@McpToolParam(description = "Cadena 'solution' tal como la devolvió analyze_project (p.ej. [[393]])", required = true) String solution,
			@McpToolParam(description = "Nombre para cada extracción, en el mismo orden en que aparecen", required = true) List<String> names)
			throws Exception {
		return dockerApplyService.applyNames(projectName, relativePath, complexity, solution, names);
	}
}