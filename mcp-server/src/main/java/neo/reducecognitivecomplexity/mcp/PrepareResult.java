package neo.reducecognitivecomplexity.mcp;

public class PrepareResult {
	public boolean success;
	public String message;

	public static PrepareResult error(String message) {
		PrepareResult r = new PrepareResult();
		r.success = false;
		r.message = message;
		return r;
	}

	public static PrepareResult skipped(String message) {
		PrepareResult r = new PrepareResult();
		r.success = true;
		r.message = message;
		return r;
	}
}