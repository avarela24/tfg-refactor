package neo.reducecognitivecomplexity.mcp.tools;

import neo.reducecognitivecomplexity.mcp.service.DockerGitService;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** Expone create_pull_request: reúne los archivos modificados y abre la PR, sin que el LLM genere JSON. */
@Component
public class GitTools {

	private final DockerGitService gitService;

	public GitTools(DockerGitService gitService) {
		this.gitService = gitService;
	}

	@McpTool(name = "create_pull_request", description = "Reúne todos los archivos modificados tras aplicar una o más extracciones con apply_names, y le pide directamente al bot de GitHub que cree la rama, suba los archivos y abra la Pull Request. No generes ningún JSON ni copies el contenido de ningún archivo — esta tool lo hace todo por su cuenta, leyendo directamente del disco. Solo tiene sentido llamarla cuando owner/repo/baseBranch estén disponibles (modo bot); en modo conversacional no hay ningún repositorio de GitHub sobre el que abrir una PR.")
	public String createPullRequest(
			@McpToolParam(description = "Nombre del proyecto en el workspace", required = true) String projectName,
			@McpToolParam(description = "Dueño del repositorio en GitHub", required = true) String owner,
			@McpToolParam(description = "Nombre del repositorio en GitHub", required = true) String repo,
			@McpToolParam(description = "Rama principal del repositorio", required = true) String baseBranch)
			throws Exception {
		return gitService.createPullRequest(projectName, owner, repo, baseBranch);
	}
}