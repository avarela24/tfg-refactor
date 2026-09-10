package neo.reducecognitivecomplexity.mcp.model;

import java.util.List;

// Resultado completo de analizar un proyecto: qué se analizó y qué métodos salieron.
public class AnalysisResult {
	public String project;
	public List<FileAnalysis> files;
}