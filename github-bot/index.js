import "dotenv/config";
import Express from "express";
import { createNodeMiddleware, createProbot } from "probot";

/**
 * Bot de GitHub: al recibir un push (que no sea de sus propias ramas),
 * avisa a n8n para que arranque el análisis en segundo plano y termina
 * ahí mismo, sin esperar ningún resultado.
 *
 * Cuando el análisis termina, es el propio servidor MCP quien avisa a
 * n8n con el resultado; el agente decide nombres, los aplica, y manda el
 * JSON final de archivos a la ruta /tfg-bot/pr-ready de este mismo bot
 * — es en ese momento, no antes, cuando se crea la rama, se suben los
 * archivos y se abre la Pull Request.
 *
 * A partir de Probot v14, Probot ya no trae su propio servidor Express
 * por dentro (antes sí, y de ahí salía el "getRouter" que usábamos) —
 * ahora es al revés: nosotros creamos nuestro propio servidor Express, y
 * montamos Probot dentro como un middleware más, junto a nuestras
 * propias rutas.
 */
process.on("unhandledRejection", (reason) => {
  console.error("Unhandled rejection (ignorada, el bot sigue en marcha):", reason);
});

const AUTO_MERGE = process.env.AUTO_MERGE === "true";
const BOT_BRANCH_PREFIX = "bot/refactor-";

// Referencia a la app de Probot, para que la ruta /tfg-bot/pr-ready
// (fuera del manejador de eventos) pueda autenticarse contra GitHub
// igual que el propio manejador del push.
let probotApp;

const appFn = (app) => {
  probotApp = app;
  app.log.info("Yay, the app was loaded!");

  app.on("push", async (context) => {
    const ref = context.payload.ref;

    if (ref.includes(BOT_BRANCH_PREFIX)) {
      app.log.info("Push a una rama del propio bot, ignorado: " + ref);
      return;
    }

    const repo = context.payload.repository;
    const owner = repo.owner.login;
    const repoName = repo.name;
    const baseBranch = repo.default_branch;

    if (context.payload.head_commit &&
        context.payload.head_commit.message.startsWith("Merge pull request") &&
        context.payload.head_commit.message.includes(BOT_BRANCH_PREFIX)) {
      app.log.info("Push resultante de fusionar una PR del bot, ignorado.");
      return;
    }

    app.log.info(`Push recibido en: ${repo.full_name}`);

    try {
      await fetch("http://localhost:5678/webhook/bot-trigger", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          projectName: repoName,
          gitUrl: repo.clone_url,
          branch: ref.replace("refs/heads/", ""),
          owner,
          repo: repoName,
          baseBranch,
        }),
      });
      app.log.info("Análisis disparado en n8n para " + repo.full_name);
    } catch (err) {
      app.log.error("Fallo llamando a n8n: " + err.message);
    }
  });
};

const probot = createProbot();

const middleware = await createNodeMiddleware(appFn, {
  webhooksPath: "/",
  probot,
});

const server = Express();
server.use(middleware);
server.use(Express.json({ limit: "20mb" })); // el código completo de varias clases puede pesar

server.post("/tfg-bot/pr-ready", async (req, res) => {
  let payload;
  try {
    payload = extractJson(JSON.stringify(req.body));
  } catch (e) {
    payload = null;
  }
  if (!payload) payload = req.body;

  const { owner, repo: repoName, baseBranch } = payload || {};
  const files = payload && Array.isArray(payload.files) ? payload.files : [];

  if (!owner || !repoName || !baseBranch) {
    probotApp.log.error("Petición a /pr-ready sin owner/repo/baseBranch: " + JSON.stringify(payload));
    return res.status(400).json({ error: "Faltan owner, repo o baseBranch." });
  }

  const validFiles = files.filter((file) => {
    const ok = file && typeof file.path === "string" && file.path.trim() !== ""
        && typeof file.content === "string";
    if (!ok) {
      probotApp.log.error("Entrada de archivo mal formada, se ignora: " + JSON.stringify(file));
    }
    return ok;
  });

  if (validFiles.length === 0) {
    probotApp.log.info(`Ningún archivo válido para ${owner}/${repoName}; nada que hacer.`);
    return res.json({ success: true, message: "Sin archivos que subir." });
  }

  try {
    const appOctokit = await probotApp.auth();
    const installation = await appOctokit.rest.apps.getRepoInstallation({
      owner, repo: repoName,
    });
    const octokit = await probotApp.auth(installation.data.id);

    const branchName = BOT_BRANCH_PREFIX + Date.now();

    const { data: baseRef } = await octokit.rest.git.getRef({
      owner, repo: repoName, ref: `heads/${baseBranch}`,
    });
    await octokit.rest.git.createRef({
      owner, repo: repoName, ref: `refs/heads/${branchName}`, sha: baseRef.object.sha,
    });
    probotApp.log.info("Rama creada: " + branchName);

    for (const file of validFiles) {
      let existingSha;
      try {
        const { data: existing } = await octokit.rest.repos.getContent({
          owner, repo: repoName, path: file.path, ref: branchName,
        });
        existingSha = existing.sha;
      } catch (err) {
        if (err.status !== 404) {
          throw err;
        }
        existingSha = undefined;
      }

      await octokit.rest.repos.createOrUpdateFileContents({
        owner, repo: repoName, path: file.path,
        message: `Refactor: reduce complejidad cognitiva en ${file.path}`,
        content: Buffer.from(file.content, "utf-8").toString("base64"),
        branch: branchName,
        sha: existingSha,
      });
      probotApp.log.info("Archivo actualizado: " + file.path);
    }

    const { data: pr } = await octokit.rest.pulls.create({
      owner, repo: repoName,
      title: "Refactorización automática: reducción de complejidad cognitiva",
      head: branchName, base: baseBranch,
      body: "Cambios propuestos automáticamente por el bot del TFG (analyze_project + apply_names).",
    });
    probotApp.log.info("Pull Request abierta: " + pr.html_url);

    if (AUTO_MERGE) {
      await octokit.rest.pulls.merge({ owner, repo: repoName, pull_number: pr.number });
      probotApp.log.info("PR fusionada automáticamente.");
    } else {
      probotApp.log.info("Human-in-the-loop: la PR queda esperando tu aprobación manual: " + pr.html_url);
    }

    res.json({ success: true, prUrl: pr.html_url });
  } catch (err) {
    probotApp.log.error("Fallo creando la PR para " + owner + "/" + repoName + ": " + err.message);
    res.status(500).json({ success: false, error: err.message });
  }
});

function extractJson(text) {
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch (e) {
    parsed = null;
  }
  if (parsed && typeof parsed.output === "string") {
    return extractJson(parsed.output);
  }
  if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
    return parsed;
  }
  const match = text.match(/\{[\s\S]*\}/);
  if (match) {
    try {
      return JSON.parse(match[0]);
    } catch (e2) {
      return null;
    }
  }
  return null;
}

const port = process.env.PORT || 3000;
// A partir de Probot v14, ya no hay ningún mecanismo automático que
// conecte con el túnel de smee.io (antes lo gestionaba "probot run" por
// nosotros) — si WEBHOOK_PROXY_URL está definida en .env, lo hacemos
// nosotros mismos, reenviando lo que llegue al mismo sitio donde
// createNodeMiddleware espera los webhooks (webhooksPath, "/").
if (process.env.WEBHOOK_PROXY_URL) {
  const { default: SmeeClient } = await import("smee-client");
  const smee = new SmeeClient({
    source: process.env.WEBHOOK_PROXY_URL,
    target: `http://localhost:${process.env.PORT || 3000}/`,
    logger: console,
  });
  smee.start();
}

server.listen(port, () => {
  console.log(`github-bot escuchando en el puerto ${port}`);
});