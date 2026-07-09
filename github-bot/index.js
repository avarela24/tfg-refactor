export default (app) => {
  app.log.info("Yay, the app was loaded!");

  const AUTO_MERGE = process.env.AUTO_MERGE === "true";
  const BOT_BRANCH_PREFIX = "bot/refactor-";

  app.on("push", async (context) => {
    const ref = context.payload.ref;

    // Corta cualquier push a una rama creada por el propio bot, para no
    // disparar el flujo contra sus propios commits (y evitar un bucle si
    // AUTO_MERGE está activo: la fusión a main también sería un push).
    if (ref.includes(BOT_BRANCH_PREFIX)) {
      app.log.info("Push a una rama del propio bot, ignorado: " + ref);
      return;
    }

    const repo = context.payload.repository;
    const owner = repo.owner.login;
    const repoName = repo.name;
    const baseBranch = repo.default_branch;

    // Ignora también el push que resulta de fusionar la propia PR del bot.
    if (context.payload.head_commit &&
        context.payload.head_commit.message.startsWith("Merge pull request") &&
        context.payload.head_commit.message.includes(BOT_BRANCH_PREFIX)) {
      app.log.info("Push resultante de fusionar una PR del bot, ignorado.");
      return;
    }

    app.log.info(`Push recibido en: ${repo.full_name}`);

    let n8nResponse;

    try {
      const response = await fetch("http://localhost:5678/webhook/bot-trigger", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          projectName: repoName,
          gitUrl: repo.clone_url,
          branch: ref.replace("refs/heads/", ""),
        }),
      });
      const text = await response.text();
      app.log.info("Respuesta de n8n: " + text);
      n8nResponse = extractJson(text);
    } catch (err) {
      app.log.error("Fallo llamando a n8n: " + err.message);
      return;
    }

    if (!n8nResponse || !Array.isArray(n8nResponse.files) || n8nResponse.files.length === 0) {
      app.log.info("El agente no propuso ningún cambio; nada que hacer.");
      return;
    }

    const branchName = BOT_BRANCH_PREFIX + Date.now();

    // 1. Crear la rama nueva a partir del último commit de la rama base
    const { data: baseRef } = await context.octokit.rest.git.getRef({
      owner, repo: repoName, ref: `heads/${baseBranch}`,
    });
    await context.octokit.rest.git.createRef({
      owner, repo: repoName, ref: `refs/heads/${branchName}`, sha: baseRef.object.sha,
    });
    app.log.info("Rama creada: " + branchName);

    // 2. Subir cada archivo modificado (vía la API de contenidos, sin git local)
    for (const file of n8nResponse.files) {
      let existingSha;
      try {
        const { data: existing } = await context.octokit.rest.repos.getContent({
          owner, repo: repoName, path: file.path, ref: branchName,
        });
        existingSha = existing.sha;
      } catch (err) {
        existingSha = undefined;
      }

      await context.octokit.rest.repos.createOrUpdateFileContents({
        owner, repo: repoName, path: file.path,
        message: `Refactor: reduce complejidad cognitiva en ${file.path}`,
        content: Buffer.from(file.content, "utf-8").toString("base64"),
        branch: branchName,
        sha: existingSha,
      });
      app.log.info("Archivo actualizado: " + file.path);
    }

    // 3. Abrir la Pull Request
    const { data: pr } = await context.octokit.rest.pulls.create({
      owner, repo: repoName,
      title: "Refactorización automática: reducción de complejidad cognitiva",
      head: branchName, base: baseBranch,
      body: "Cambios propuestos automáticamente por el bot del TFG (analyze_project + apply_names) a partir de un push a " + baseBranch + ".",
    });
    app.log.info("Pull Request abierta: " + pr.html_url);

    // 4. Auto-merge, solo si está configurado explícitamente
    if (AUTO_MERGE) {
      await context.octokit.rest.pulls.merge({ owner, repo: repoName, pull_number: pr.number });
      app.log.info("PR fusionada automáticamente.");
    } else {
      app.log.info("Human-in-the-loop: la PR queda esperando tu aprobación manual: " + pr.html_url);
    }
  });
};

function extractJson(text) {
  try {
    return JSON.parse(text);
  } catch (e) {
    const match = text.match(/\{[\s\S]*\}/);
    if (match) {
      try { return JSON.parse(match[0]); } catch (e2) { return null; }
    }
    return null;
  }
}