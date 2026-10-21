# Pendência (sem ticket ainda) — deep link do app pros fluxos de senha

## Contexto

TICKET-0033 (recuperar senha) e TICKET-0034 (encerrar conta) geram link de e-mail que aponta pra
`${app.deeplink.url}` + path (`/nova-senha?token=...`, `/recuperar-senha`).

Decisão 2026-10-16: **custom URL scheme**, não Universal Link/App Link. Recuperar/alterar senha é só
app, sem fallback web nenhum atrás do link — sem página web pra cair, o setup de Universal Link
(domínio https, `apple-app-site-association`, `assetlinks.json`, verificação Apple/Google) não compensa.
Scheme registrado: **`limio`** (`limio://...`).

`app.deeplink.url` já tem default fixo (`limio://`, em `application.properties`) — é igual em todo
ambiente, não precisa mais de env var por deploy. O `APP_FRONTEND_URL` que tava setado na Railway
(resquício de quando a ideia era Universal Link) pode ser removido.

## O que falta implementar, quando este card for aberto

- [ ] Confirmar que o app registra o scheme `limio` (iOS: `CFBundleURLTypes` no `Info.plist`; Android:
      `intent-filter` com `<data android:scheme="limio" />`).
- [ ] Telas no app que os paths abrem: `limio://nova-senha?token=...` (lê `token` da query string) e
      `limio://recuperar-senha` (sem parâmetro, só "soube que foi você?").
- [ ] Nada a fazer no back-end além do que já existe — `EmailService` já monta o link certo.

## Por que não precisa mais

- Sem Universal Link: não precisa de domínio, `/.well-known/apple-app-site-association` nem
  `assetlinks.json`.
- Sem página web: card relacionado [auth] [BACK-END] "Páginas web Confirmar e-mail/Nova senha" não se
  aplica a este fluxo — se ele ainda existir pra outra coisa, é independente disto.
- Trade-off aceito (decisão do produto, não técnica): link não funciona se o app não tiver instalado
  (abre nada/erro do SO) — ok porque não tem alternativa nenhuma mesmo sem o app.
