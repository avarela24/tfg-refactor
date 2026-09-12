package neo.reducecognitivecomplexity.mcp.model;

import java.util.ArrayList;
import java.util.List;

// Un método con complejidad cognitiva alta, y lo que el solver propone para él.
public class MethodCandidate {
	public String methodName;
	public List<ExtractionSnippet> extractions = new ArrayList<>();
}