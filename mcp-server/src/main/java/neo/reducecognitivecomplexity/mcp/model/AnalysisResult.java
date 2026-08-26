package neo.reducecognitivecomplexity.mcp.model;

import java.util.List;
import java.util.Map;

/** Resultado completo de analizar un proyecto: qué se analizó y qué métodos salieron. */
public class AnalysisResult {
	public String project;
	public String solver;
	public List<MethodCandidate> methods;

	// Código completo de cada archivo que contiene algún método señalado,
	// una sola vez por archivo (clave: relativePath) — evita repetir el
	// mismo archivo tantas veces como métodos tenga señalados.
	public Map<String, String> classCodeByFile;
}