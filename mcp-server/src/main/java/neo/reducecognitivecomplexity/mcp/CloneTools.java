package neo.reducecognitivecomplexity.mcp;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class CloneTools {

	private final DockerCloneService cloneService;

	public CloneTools(DockerCloneService cloneService) {
		this.cloneService = cloneService;
	}

	@McpTool(name = "clone_project", description = "Clona un repositorio Git en el workspace local, sin tocar el remoto. Primer paso para trabajar con un repositorio (propio o ajeno) que todavía no está en el workspace.")
	public CloneResult cloneProject(
			@McpToolParam(description = "URL del repositorio, p.ej. https://github.com/usuario/repo.git", required = true) String gitUrl,
			@McpToolParam(description = "Nombre de carpeta a usar en el workspace; si se omite, se deriva de la URL", required = false) String projectName,
			@McpToolParam(description = "Rama a clonar; si se omite, usa la rama por defecto", required = false) String branch)
			throws Exception {
		return cloneService.cloneProject(gitUrl, projectName, branch);
	}
}