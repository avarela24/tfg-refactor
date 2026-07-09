package neo.reducecognitivecomplexity.mcp.tools;

import neo.reducecognitivecomplexity.mcp.model.FileContentResult;
import neo.reducecognitivecomplexity.mcp.model.ModifiedFilesResult;
import neo.reducecognitivecomplexity.mcp.service.DockerGitService;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class GitTools {

	private final DockerGitService gitService;

	public GitTools(DockerGitService gitService) {
		this.gitService = gitService;
	}

	@McpTool(name = "list_modified_files", description = "Lista los archivos que han sido modificados (respecto al commit clonado) en el proyecto, tras aplicar una o más extracciones con apply_names.")
	public ModifiedFilesResult listModifiedFiles(
			@McpToolParam(description = "Nombre del proyecto en el workspace", required = true) String projectName)
			throws Exception {
		return gitService.listModifiedFiles(projectName);
	}

	@McpTool(name = "get_file_content", description = "Devuelve el contenido actual de un archivo del proyecto, para poder subirlo a GitHub.")
	public FileContentResult getFileContent(
			@McpToolParam(description = "Nombre del proyecto", required = true) String projectName,
			@McpToolParam(description = "Ruta del archivo relativa a la raíz del proyecto", required = true) String relativePath)
			throws Exception {
		return gitService.getFileContent(projectName, relativePath);
	}
}