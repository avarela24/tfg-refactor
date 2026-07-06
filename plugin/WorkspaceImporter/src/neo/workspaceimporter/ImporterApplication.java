package neo.workspaceimporter;

import java.io.File;
import java.util.logging.Logger;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

/**
 * Aplicación Eclipse headless que registra en el workspace cualquier
 * proyecto que ya tenga un archivo .project en disco, pero que el
 * workspace todavía no conozca.
 *
 * Por qué existe: IProject.exists() consulta el REGISTRO interno del
 * workspace (la carpeta .metadata), no el disco. Dejar una carpeta con
 * .project dentro del workspace no basta para que Eclipse la reconozca
 * como proyecto — hay que "importarla" explícitamente, que es justo lo
 * que hace esta clase, usando la misma API que usaría la UI de Eclipse
 * (Import > Existing Projects), pero sin UI.
 *
 * Se ejecuta como un primer paso separado (ver docker/entrypoint.sh),
 * antes de lanzar ReduceCognitiveComplexity.application, para dejar el
 * workspace listo sin tener que tocar el código del plugin original.
 */
public class ImporterApplication implements IApplication {

	private static final Logger LOGGER = Logger.getLogger(ImporterApplication.class.getName());

	@Override
	public Object start(IApplicationContext context) throws Exception {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		File workspaceRoot = workspace.getRoot().getLocation().toFile();

		// Recorre todas las subcarpetas del workspace (no solo una), para que
		// un mismo workspace pueda traer varios proyectos montados a la vez.
		File[] candidates = workspaceRoot.listFiles(File::isDirectory);
		if (candidates == null) {
			LOGGER.info("Workspace root has no subfolders to import.");
			return IApplication.EXIT_OK;
		}

		for (File candidate : candidates) {
			// Solo nos interesan carpetas que ya parezcan un proyecto Eclipse.
			File dotProject = new File(candidate, ".project");
			if (!dotProject.exists()) {
				continue;
			}

			// Carga el .project real (nombre, naturaleza, builders) y lo usa
			// para dar de alta el proyecto en el registro del workspace.
			IPath dotProjectPath = new Path(dotProject.getAbsolutePath());
			IProjectDescription description = workspace.loadProjectDescription(dotProjectPath);
			IProject project = workspace.getRoot().getProject(description.getName());

			if (project.exists()) {
				// Ya registrado en una ejecución anterior sobre el mismo
				// workspace; no hay nada que hacer.
				LOGGER.info("Already registered, skipping: " + description.getName());
				continue;
			}

			project.create(description, new NullProgressMonitor());
			project.open(new NullProgressMonitor());
			LOGGER.info("Imported project: " + description.getName());
		}

		return IApplication.EXIT_OK;
	}

	@Override
	public void stop() {
		// Nada que limpiar: la aplicación no mantiene ningún estado propio.
	}
}
