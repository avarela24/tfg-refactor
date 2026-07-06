package toy;

public class OrderProcessor {

	// Método deliberadamente enrevesado para superar el umbral por defecto (15).
	public String classify(int quantity, int priority, boolean express, String region, int[] history) {
		String result = "unknown";

		if (quantity > 0) {
			if (priority > 5) {
				if (express || quantity > 100) {
					for (int i = 0; i < history.length; i++) {
						if (history[i] > 10) {
							if (history[i] % 2 == 0) {
								result = "high-even";
							} else {
								result = "high-odd";
							}
						} else if (history[i] < 0) {
							result = "invalid";
						}
					}
				} else {
					switch (region) {
						case "north":
							result = "north-standard";
							break;
						case "south":
							if (quantity > 50 && priority > 2) {
								result = "south-priority";
							} else {
								result = "south-standard";
							}
							break;
						default:
							result = "other";
					}
				}
			} else if (priority > 0) {
				result = "low-priority";
			}
		} else {
			result = "empty";
		}

		return result;
	}
}
