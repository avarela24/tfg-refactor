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

	@McpTool(
  		name = "apply_names",
  		description = "Aplica sobre el archivo real del proyecto la extraccion de metodo calculada por start_analysis, usando los "
    		+ "nombres indicados para cada metodo extraido."
	)
	public ApplyResult applyNames(
		@McpToolParam(description = "Nombre del proyecto", required = true)
		String projectName,
		@McpToolParam(description = "Ruta del archivo .java relativa a la raiz del proyecto", required = true)
		String relativePath,
		@McpToolParam(description = "Cadena 'solution' tal como llego en el resultado del analisis", required = true)
		String solution,
		@McpToolParam(description = "Nombre para cada extraccion, en el mismo orden en que aparecen", required = true)
		List<String> names
	) throws Exception {
		return dockerApplyService.applyNames(projectName, relativePath, solution, names);
	}
}