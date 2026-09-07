package neo.reducecognitivecomplexity.mcp.tools;

import neo.reducecognitivecomplexity.mcp.service.DockerAnalysisService;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** Expone start_analysis: lanza el análisis en segundo plano, avisando a un webhook distinto de n8n al terminar dependiendo del valor de isBotMode. */
@Component
public class AnalysisTools {

	private final DockerAnalysisService dockerAnalysisService;

	public AnalysisTools(DockerAnalysisService dockerAnalysisService) {
		this.dockerAnalysisService = dockerAnalysisService;
	}

	@McpTool(
		name = "start_analysis",
		description = "Inicia el analisis de un proyecto Java ya montado en el workspace, en segundo plano, y termina inmediatamente sin "
			+ "esperar a que acabe. IMPORTANTE sobre isBotMode: ponlo a true UNICAMENTE si se te ha indicado de forma explicita, con "
			+ "owner/repo/baseBranch ya dados en el propio mensaje que has recibido. Termina tu turno justo despues de llamar a esta tool."
	)
	public String startAnalysis(
		@McpToolParam(description = "Nombre del proyecto; debe coincidir con el <name> de su .project", required = true)
		String projectName,
		@McpToolParam(description = "Solver a usar (por defecto ILP)", required = false)
		String solver,
		@McpToolParam(description = "true SOLO si se te ha indicado de forma explicita; false en cualquier otro caso", required = true)
		Boolean isBotMode,
		@McpToolParam(description = "Dueño del repositorio en GitHub; solo si isBotMode=true", required = false)
		String owner,
		@McpToolParam(description = "Nombre del repositorio en GitHub; solo si isBotMode=true", required = false)
		String repo,
		@McpToolParam(description = "Rama principal del repositorio; solo si isBotMode=true", required = false)
		String baseBranch
	) {
		String resolvedSolver = (solver == null || solver.isBlank()) ? "ILP" : solver;
		boolean botMode = isBotMode != null && isBotMode;
		return dockerAnalysisService.startAnalysis(projectName, resolvedSolver, botMode, owner, repo, baseBranch);
	}
}