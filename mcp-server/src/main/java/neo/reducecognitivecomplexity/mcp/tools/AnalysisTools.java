package neo.reducecognitivecomplexity.mcp.tools;

import neo.reducecognitivecomplexity.mcp.model.AnalysisResult;
import neo.reducecognitivecomplexity.mcp.service.DockerAnalysisService;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class AnalysisTools {

	private final DockerAnalysisService dockerAnalysisService;

	public AnalysisTools(DockerAnalysisService dockerAnalysisService) {
		this.dockerAnalysisService = dockerAnalysisService;
	}

	@McpTool(name = "analyze_project", description = "Analiza un proyecto Java ya montado en el workspace y devuelve, para cada método con complejidad cognitiva alta, la solución encontrada por el solver (ILP, ES-LSF o ES-SSF) y el código de cada extracción propuesta.")
	public AnalysisResult analyzeProject(
			@McpToolParam(description = "Nombre del proyecto; debe coincidir con el <name> de su .project", required = true) String projectName,
			@McpToolParam(description = "Solver a usar: ILP, ES-LSF o ES-SSF", required = true) String solver) throws Exception {
		return dockerAnalysisService.analyzeProject(projectName, solver);
	}
}