# github-bot

GitHub App (basada en [Probot](https://github.com/probot/probot)) que
dispara el pipeline de refactorización automática ante cada `push` a un
repositorio:

1. Recibe el evento `push` (vía [smee.io](https://smee.io) en desarrollo
   local; ver `WEBHOOK_PROXY_URL` en `.env`).
2. Llama al workflow de n8n (`http://localhost:5678/webhook/bot-trigger`),
   que clona/prepara/analiza el proyecto y deja que un agente LLM decida
   los nombres de los métodos extraídos, vía las tools del `mcp-server`.
3. Con la respuesta (`{"files": [...]}`), crea una rama, sube los archivos
   modificados (API de Contents, sin `git` local) y abre una Pull Request.
4. Según `AUTO_MERGE` en `.env`: deja la PR esperando aprobación manual
   (`false`, human-in-the-loop) o la fusiona sola (`true`).

Ver el `README.md` de la raíz del repositorio para el sistema completo.

## Puesta en marcha

```sh
npm install
npm start
```

Necesita un `.env` con `APP_ID`, `PRIVATE_KEY` (o `PRIVATE_KEY_PATH`),
`WEBHOOK_SECRET` y `WEBHOOK_PROXY_URL` — ver `.env.example`. Añade también
`AUTO_MERGE=true` o `false` según la preferencia deseada.

## Docker (sin usar todavía en este TFG)

```sh
docker build -t github-bot .
docker run -e APP_ID=<app-id> -e PRIVATE_KEY=<pem-value> github-bot
```

## Licencia

[ISC](LICENSE) © 2026 avarela24
