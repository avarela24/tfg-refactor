package neo.reducecognitivecomplexity.mcp;

import java.util.ArrayList;
import java.util.List;

/** Un método con complejidad cognitiva alta, y lo que el solver propone para él. */
public class MethodCandidate {
	public String methodName;
	public int complexity;
	public String solution; // p.ej. "[[393]]" o "[]" si no encontró nada
	public List<ExtractionSnippet> extractions = new ArrayList<>();
}