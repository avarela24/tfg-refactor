package neo.reducecognitivecomplexity.mcp.tools;

import neo.reducecognitivecomplexity.mcp.service.DockerAnalysisService;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** Expone start_analysis_chat y start_analysis_bot: lanzan el análisis en segundo plano, cada uno avisando a un webhook distinto de n8n al terminar. */
@Component
public class AnalysisTools {

	private final DockerAnalysisService dockerAnalysisService;

	public AnalysisTools(DockerAnalysisService dockerAnalysisService) {
		this.dockerAnalysisService = dockerAnalysisService;
	}

	@McpTool(name = "start_analysis_chat", description = "Lanza el análisis de un proyecto Java ya montado en el workspace, en segundo plano, y termina inmediatamente sin esperar a que acabe. Úsala solo en conversación directa (sin disparo automático de GitHub). No hace falta comprobar el resultado ni esperar nada: cuando el análisis termine, su resultado llegará automáticamente como una nueva ejecución de n8n. El mensaje devuelto ya trae una confirmación clara de que el análisis ha arrancado — úsalo tal cual. Termina tu turno justo después de llamar a esta tool.")
	public String startAnalysisChat(
			@McpToolParam(description = "Nombre del proyecto; debe coincidir con el <name> de su .project", required = true) String projectName,
			@McpToolParam(description = "Solver a usar (por defecto ILP)", required = false) String solver) {
		String resolvedSolver = (solver == null || solver.isBlank()) ? "ILP" : solver;
		return dockerAnalysisService.startAnalysisChat(projectName, resolvedSolver);
	}

	@McpTool(name = "start_analysis_bot", description = "Lanza el análisis de un proyecto Java ya montado en el workspace, en segundo plano, y termina inmediatamente sin esperar a que acabe. Úsala solo cuando el disparo original venga de un push de GitHub, con owner/repo/baseBranch disponibles — pásalos tal cual, sin derivarlos ni inventarlos. No hace falta comprobar el resultado: cuando el análisis termine, se procesará automáticamente y se abrirá una Pull Request si procede. El mensaje devuelto ya trae una confirmación clara de que el análisis ha arrancado — úsalo tal cual. Termina tu turno justo después de llamar a esta tool.")
	public String startAnalysisBot(
			@McpToolParam(description = "Nombre del proyecto; debe coincidir con el <name> de su .project", required = true) String projectName,
			@McpToolParam(description = "Solver a usar (por defecto ILP)", required = false) String solver,
			@McpToolParam(description = "Dueño del repositorio en GitHub", required = true) String owner,
			@McpToolParam(description = "Nombre del repositorio en GitHub", required = true) String repo,
			@McpToolParam(description = "Rama principal del repositorio", required = true) String baseBranch) {
		String resolvedSolver = (solver == null || solver.isBlank()) ? "ILP" : solver;
		return dockerAnalysisService.startAnalysisBot(projectName, resolvedSolver, owner, repo, baseBranch);
	}
}