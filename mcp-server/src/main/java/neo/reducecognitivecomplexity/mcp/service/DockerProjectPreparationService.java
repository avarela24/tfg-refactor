package neo.reducecognitivecomplexity.mcp.service;

import neo.reducecognitivecomplexity.mcp.model.PrepareResult;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Genera .project/.classpath para proyectos que todavía no los tengan,
 * detectando Maven, Gradle o Java simple. Para Maven, resuelve las
 * dependencias reales con un contenedor Maven efímero (mismo volumen de
 * caché que luego monta reduce-cc, para que las rutas absolutas cuadren).
 */
@Service
public class DockerProjectPreparationService {

	private static final String MAVEN_CACHE_VOLUME = "mcp-maven-cache";

	@Autowired
	private ReduceCcLock reduceCcLock;

	@Value("${reducecc.workspace-dir}")
	private String workspaceDir;

	public PrepareResult prepareProject(String projectName) throws Exception {
		reduceCcLock.lock();
		try {
			Path projectDir = Paths.get(workspaceDir, projectName);

			if (!Files.isDirectory(projectDir)) {
				return PrepareResult.error("No existe la carpeta " + projectDir + " en el workspace.");
			}

			if (Files.exists(projectDir.resolve(".project"))) {
				return PrepareResult.skipped("El proyecto ya tiene .project/.classpath; no se ha tocado nada.");
			}

			Path pomXml = projectDir.resolve("pom.xml");
			Path buildGradle = projectDir.resolve("build.gradle");
			Path buildGradleKts = projectDir.resolve("build.gradle.kts");

			if (Files.exists(pomXml)) {
				return prepareMavenProject(projectName, projectDir, pomXml);
			} else if (Files.exists(buildGradle) || Files.exists(buildGradleKts)) {
				return PrepareResult.error("Proyecto Gradle detectado; la generación automática para Gradle todavía no está implementada.");
			} else {
				return preparePlainJavaProject(projectName, projectDir);
			}
		} finally {
			reduceCcLock.unlock();
		}
	}

	private PrepareResult prepareMavenProject(String projectName, Path projectDir, Path pomXml) throws Exception {
		String pomContent = Files.readString(pomXml, StandardCharsets.UTF_8);
		String javaVersion = detectJavaVersion(pomContent);

		String classpathFileName = ".mcp-classpath.txt";
		List<String> command = List.of(
				"docker", "run", "--rm",
				"-v", projectDir.toAbsolutePath() + ":/project",
				"-v", MAVEN_CACHE_VOLUME + ":/root/.m2",
				"-w", "/project",
				"maven:3.9-eclipse-temurin-21",
				"mvn", "-B", "-q", "dependency:build-classpath",
				"-Dmdep.outputFile=" + classpathFileName);

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.redirectErrorStream(true);
		Process process = pb.start();

		StringBuilder log = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			String line;
			while ((line = reader.readLine()) != null) {
				log.append(line).append("\n");
			}
		}

		boolean finished = process.waitFor(10, TimeUnit.MINUTES);
		if (!finished) {
			process.destroyForcibly();
			return PrepareResult.error("La resolución de dependencias Maven no terminó a tiempo.");
		}

		Path classpathFile = projectDir.resolve(classpathFileName);
		if (!Files.exists(classpathFile)) {
			return PrepareResult.error("No se pudo resolver el classpath de Maven. Salida:\n" + log);
		}

		String rawClasspath = Files.readString(classpathFile, StandardCharsets.UTF_8).trim();
		Files.deleteIfExists(classpathFile);

		List<String> jars = new ArrayList<>();
		if (!rawClasspath.isEmpty()) {
			for (String entry : rawClasspath.split(":")) {
				if (!entry.isBlank()) {
					jars.add(entry.trim());
				}
			}
		}

		List<String> sourceFolders = new ArrayList<>();
		if (Files.isDirectory(projectDir.resolve("src/main/java"))) sourceFolders.add("src/main/java");
		if (Files.isDirectory(projectDir.resolve("src/test/java"))) sourceFolders.add("src/test/java");
		if (sourceFolders.isEmpty()) sourceFolders.add("src");

		writeDotProject(projectDir, projectName);
		writeDotClasspath(projectDir, sourceFolders, jars, javaVersion);

		PrepareResult result = new PrepareResult();
		result.success = true;
		result.message = "Proyecto Maven preparado: " + jars.size() + " dependencias resueltas, "
				+ sourceFolders.size() + " carpeta(s) fuente, JavaSE-" + javaVersion + ".";
		return result;
	}

	private PrepareResult preparePlainJavaProject(String projectName, Path projectDir) throws Exception {
		if (!Files.isDirectory(projectDir.resolve("src"))) {
			return PrepareResult.error("No se encontró pom.xml, build.gradle ni una carpeta src/ reconocible.");
		}

		writeDotProject(projectDir, projectName);
		writeDotClasspath(projectDir, List.of("src"), List.of(), "21");

		PrepareResult result = new PrepareResult();
		result.success = true;
		result.message = "Proyecto Java simple preparado (sin gestor de dependencias detectado).";
		return result;
	}

	private String detectJavaVersion(String pomContent) {
		String[] properties = { "maven.compiler.release", "maven.compiler.target", "maven.compiler.source", "java.version" };
		for (String property : properties) {
			Matcher m = Pattern.compile(
					"<" + Pattern.quote(property) + ">\\s*([\\w.]+)\\s*</" + Pattern.quote(property) + ">")
					.matcher(pomContent);
			if (m.find()) {
				return normalizeJavaVersion(m.group(1).trim());
			}
		}
		return "21";
	}

	private String normalizeJavaVersion(String version) {
		if (version.equals("8") || version.equals("1.8")) {
			return "1.8";
		}
		return version;
	}

	private void writeDotProject(Path projectDir, String projectName) throws Exception {
		String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
				+ "<projectDescription>\n"
				+ "\t<name>" + projectName + "</name>\n"
				+ "\t<comment></comment>\n"
				+ "\t<projects>\n\t</projects>\n"
				+ "\t<buildSpec>\n"
				+ "\t\t<buildCommand>\n"
				+ "\t\t\t<name>org.eclipse.jdt.core.javabuilder</name>\n"
				+ "\t\t\t<arguments>\n\t\t\t</arguments>\n"
				+ "\t\t</buildCommand>\n"
				+ "\t</buildSpec>\n"
				+ "\t<natures>\n"
				+ "\t\t<nature>org.eclipse.jdt.core.javanature</nature>\n"
				+ "\t</natures>\n"
				+ "</projectDescription>\n";
		Files.writeString(projectDir.resolve(".project"), content, StandardCharsets.UTF_8);
	}

	private void writeDotClasspath(Path projectDir, List<String> sourceFolders, List<String> jars, String javaVersion)
			throws Exception {
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<classpath>\n");
		for (String src : sourceFolders) {
			sb.append("\t<classpathentry kind=\"src\" path=\"").append(src).append("\"/>\n");
		}
		sb.append("\t<classpathentry kind=\"con\" path=\"org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-")
				.append(javaVersion).append("\"/>\n");
		for (String jar : jars) {
			sb.append("\t<classpathentry kind=\"lib\" path=\"").append(jar).append("\"/>\n");
		}
		sb.append("\t<classpathentry kind=\"output\" path=\"bin\"/>\n");
		sb.append("</classpath>\n");
		Files.writeString(projectDir.resolve(".classpath"), sb.toString(), StandardCharsets.UTF_8);
	}
}