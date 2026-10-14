# Pendência (sem ticket ainda) — Universal Link / Deep Link do app

## Contexto

TICKET-0033 (recuperar senha) e TICKET-0034 (encerrar conta) geram link de e-mail que aponta pra
`${app.deeplink.url}` + path (`/nova-senha?token=...`, `/recuperar-senha`). Produto é app **e** web —
o core é app nativo, mas landing page e páginas pontuais (as próprias "Confirmar e-mail"/"Nova senha",
card relacionado já citado no TICKET-0033) são web. Decidido em 2026-10-14 usar Universal Link (iOS) /
App Link (Android) em vez de custom scheme (`limio://...`): o link do e-mail abre o app quando
instalado, e cai de volta na página web normal (já outro card) quando não — **o fallback não é uma
página nova, é a própria página web que já tá planejada**.

Hoje o link **não abre o app de verdade** — só existe pra não quebrar o boot (`app.deeplink.url` é
`@Value` sem default) e sair um link sintaticamente válido no e-mail.

## O que falta implementar, quando este card for aberto

- [ ] `GET /.well-known/apple-app-site-association` no domínio onde a página web mora — JSON sem
      extensão, Content-Type `application/json`, com `appID`/`paths` do app iOS
      (`/nova-senha`, `/recuperar-senha`, `/confirmar-email` se existir).
- [ ] `GET /.well-known/assetlinks.json` — mesma ideia pro Android (package name + SHA-256 do
      certificado de assinatura, debug e release separados se forem diferentes).
- [ ] Time mobile: Associated Domains (iOS) / App Links com `autoVerify` (Android) no projeto do app,
      apontando pro mesmo domínio da página web.
- [ ] Time mobile: telas que os paths abrem (`/nova-senha` lê `token` da query string) e o parsing.
- [ ] Nenhuma mudança na página web em si — ela já é o fallback natural, não precisa de versão "sem
      app" separada.

## Decisão precisa de (antes de abrir o card)

- Domínio final: o mesmo domínio onde a página web "Nova senha"/"Confirmar e-mail" vai morar
  (recomendado — evita decisão duplicada) — depende de quem/onde essas páginas web serão hospedadas.
- App ID + Team ID (iOS), pra montar o `appID` do AASA.
- Package name + SHA-256 do certificado de assinatura (Android), debug e release se forem diferentes.

## Enquanto isso

`app.deeplink.url` (renomeado de `app.frontend.url` nesta sessão — nome antigo pressupunha site)
continua configurável por env, sem default. Valor usado pra destravar deploy, até este card ser
aberto: ver `AUTH_RETENCAO_*`/`APP_DEEPLINK_URL` em `application.properties`.
