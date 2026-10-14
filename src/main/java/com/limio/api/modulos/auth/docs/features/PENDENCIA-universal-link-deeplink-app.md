# Pendência (sem ticket ainda) — Universal Link / Deep Link do app

## Contexto

TICKET-0033 (recuperar senha) e TICKET-0034 (encerrar conta) geram link de e-mail que aponta pra
`${app.deeplink.url}` + path (`/nova-senha?token=...`, `/recuperar-senha`). Produto é só app mobile,
sem web — decidido em 2026-10-14 usar Universal Link (iOS) / App Link (Android), não custom scheme
(`limio://...`), porque funciona melhor em cliente de e-mail e tem fallback se o app não tiver
instalado.

Hoje o link **não abre o app de verdade** — só existe pra não quebrar o boot (`app.deeplink.url` é
`@Value` sem default) e sair um link sintaticamente válido no e-mail.

## O que falta implementar, quando este card for aberto

- [ ] Decidir o domínio final (ver "Decisão precisa de" abaixo).
- [ ] `GET /.well-known/apple-app-site-association` nesse domínio — JSON sem extensão, Content-Type
      `application/json`, com `appID`/`paths` do app iOS. Se o domínio escolhido for o da própria
      `limio-api` (opção mais simples), isso é um endpoint novo aqui.
- [ ] `GET /.well-known/assetlinks.json` — mesma ideia pro Android (package name + SHA-256 do
      certificado de assinatura).
- [ ] Time mobile: Associated Domains (iOS) / App Links (Android) no projeto do app, apontando pro
      domínio escolhido.
- [ ] Time mobile: telas que os paths abrem (`/nova-senha` lê `token` da query string, `/recuperar-senha`
      sem parâmetro) e o parsing.
- [ ] Decidir o que acontece se o link for aberto sem o app instalado (hoje: nada, 404. Produto pode
      querer uma página mínima "baixe o app").

## Decisão precisa de (antes de abrir o card)

- Domínio final: reaproveitar o domínio Railway da própria API (zero infra nova) ou subdomínio próprio
  tipo `links.limio.com.br` (mais bonito, precisa DNS). Ver conversa do TICKET-0034 — ainda em aberto.
- App ID + Team ID (iOS), pra montar o `appID` do AASA.
- Package name + SHA-256 do certificado de assinatura (Android), debug e release se forem diferentes.

## Enquanto isso

`app.deeplink.url` (renomeado de `app.frontend.url` nesta sessão — nome antigo pressupunha site)
continua configurável por env, sem default. Valor usado pra destravar deploy, até este card ser
aberto: ver `AUTH_RETENCAO_*`/`APP_DEEPLINK_URL` em `application.properties`.
