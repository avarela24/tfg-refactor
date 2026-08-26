package neo.reducecognitivecomplexity.mcp.model;

import java.util.ArrayList;
import java.util.List;

/** Un método con complejidad cognitiva alta, y lo que el solver propone para él. */
public class MethodCandidate {
	public String methodName;
	public String relativePath; // ruta del .java relativa a la raíz del proyecto; también sirve para buscar su código completo en AnalysisResult.classCodeByFile
	public int complexity;
	public String solution;
	public List<ExtractionSnippet> extractions = new ArrayList<>();
}