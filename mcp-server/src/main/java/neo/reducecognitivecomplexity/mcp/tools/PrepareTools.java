package neo.reducecognitivecomplexity.mcp.tools;

import neo.reducecognitivecomplexity.mcp.model.PrepareResult;
import neo.reducecognitivecomplexity.mcp.service.DockerProjectPreparationService;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class PrepareTools {

	private final DockerProjectPreparationService preparationService;

	public PrepareTools(DockerProjectPreparationService preparationService) {
		this.preparationService = preparationService;
	}

	@McpTool(name = "prepare_project", description = "Genera .project/.classpath de Eclipse para un proyecto Maven del workspace que aún no los tenga. Debe llamarse antes de analyze_project si el proyecto no viene ya preparado. Los proyectos Gradle se detectan pero no se preparan todavía (devuelve un error explícito); los proyectos sin gestor de dependencias reconocido se preparan como Java simple, sin dependencias externas.")
	public PrepareResult prepareProject(
			@McpToolParam(description = "Nombre de la carpeta del proyecto dentro del workspace", required = true) String projectName)
			throws Exception {
		return preparationService.prepareProject(projectName);
	}
}