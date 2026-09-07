package neo.reducecognitivecomplexity.mcp.tools;

import neo.reducecognitivecomplexity.mcp.model.CloneResult;
import neo.reducecognitivecomplexity.mcp.service.DockerCloneService;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class CloneTools {

	private final DockerCloneService cloneService;

	public CloneTools(DockerCloneService cloneService) {
		this.cloneService = cloneService;
	}

	@McpTool(
		name = "clone_project",
		description = "Clona un repositorio Git en el workspace local. Con forceClean=true, empieza de cero si ya existe. Con commit, se "
			+ "situa en ese commit concreto del repositorio y si no se añade, se clona el último commit de la rama indicada."
	)
	public CloneResult cloneProject(
		@McpToolParam(description = "URL del repositorio", required = true)
		String gitUrl,
		@McpToolParam(description = "Nombre de carpeta a usar en el workspace", required = false)
		String projectName,
		@McpToolParam(description = "Rama a clonar (ignorado si se indica commit)", required = false)
		String branch,
		@McpToolParam(description = "Hash de commit exacto en el que situarse", required = false)
		String commit,
		@McpToolParam(description = "Si true, borra la carpeta existente y clona de nuevo desde cero", required = false)
		Boolean forceClean
	) throws Exception {
		return cloneService.cloneProject(gitUrl, projectName, branch, commit, forceClean != null && forceClean);
	}
}