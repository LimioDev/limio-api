# ADR-0002 — Links de e-mail em `https`, abrindo páginas web servidas pela API

- **Status:** aceita pelo time de desenvolvimento em 2026-10-26 (TICKET-0047). Falta a validação do tech lead.
- **Substitui:** a decisão de 2026-10-16 ("custom URL scheme `limio://`, sem página web"), registrada em
  `modulos/auth/docs/features/PENDENCIA-universal-link-deeplink-app.md`. O arquivo foi removido e o histórico
  ficou em `modulos/auth/docs/history/CHANGELOG.md`.
- **Complementa:** ADR-0001. Nenhuma regra de camadas ou módulos muda.

## Contexto

Os e-mails de "redefinir senha" (UC06, ETI-33) e "confirmar e-mail" (UC05, ETI-30) levavam links `limio://...`
direto para o app. O card ETI-47 pede o contrário: o usuário abre o link **no navegador** e conclui a ação numa
página servida pela própria `limio-api`.

Problemas do link `limio://` em e-mail:

1. **Clientes de e-mail bloqueiam o link.** Vários (o Gmail, por exemplo) removem ou não deixam clicar em link que
   não seja `http(s)`.
2. **Sem o app, o link não faz nada.** Abrir o e-mail no computador, ou num celular sem o app, dá erro do sistema.
3. **Segurança.** Qualquer app instalado pode registrar o esquema `limio` e receber o token de redefinição de senha.

O que se faz normalmente em produto mobile é usar link `https` sempre. Primeiro ele cai numa página web que conclui
a ação. Depois, se o time quiser, o mesmo endereço passa a abrir o app direto (Universal Links no iOS, App Links no
Android) e a página fica como alternativa para quem não tem o app.

## Decisão

1. **Todo link de e-mail é `https`** e aponta para a própria API: `<app.web.url>/nova-senha#token=...` e
   `<app.web.url>/confirmar-email#token=...`. A propriedade `app.web.url` (env `APP_WEB_URL`) substitui
   `app.deeplink.url`.
2. **Token no fragmento (`#`), não na query string.** O navegador nunca envia o fragmento ao servidor, então o
   token não aparece em log de acesso, de proxy nem no Referer. A página lê o fragmento por JavaScript e o apaga da
   barra de endereço.
3. **Páginas em HTML estático + JavaScript**, em `src/main/resources/static/paginas/`, servidas pelo Spring sem
   dependência nova. O stack do ADR-0001 não tem template engine e não ganha um (Thymeleaf foi descartado). URLs
   limpas via `comum/config/PaginasWebConfig`.
4. **A página não tem regra.** Ela chama os mesmos endpoints JSON que o app usa (`POST /auth/redefinir-senha`,
   `POST /auth/confirmar-email`). Validade, uso único e encerramento das sessões ficam nos usecases. A validação de
   senha na página (8 a 72 caracteres, letra e número, confirmação igual) só ajuda o preenchimento. A regra que vale
   é a do `RedefinirSenhaRequest`.
5. **Rotas públicas no `SecurityConfig`**: `GET /nova-senha`, `GET /confirmar-email`, `GET /paginas/**` e os POSTs
   `/auth/confirmar-email` e `/auth/reenviar-confirmacao-email`.
6. **Cabeçalhos de proteção em toda resposta:** `Content-Security-Policy: default-src 'self'; frame-ancestors 'none';
   base-uri 'none'; form-action 'self'` e `Referrer-Policy: no-referrer`. As páginas não usam script, estilo nem
   fonte de fora.
7. **O app continua sendo o centro.** Depois do sucesso, a página orienta o usuário a voltar ao app (botão
   `limio://login`, que só funciona com o app instalado; o texto explica o caminho mesmo sem ele). Link inválido ou
   expirado orienta a pedir outro: "Reenviar link" (e-mail) ou "Esqueci minha senha" (senha), ambos no app.
8. **E-mail sem página correspondente não leva link.** O aviso "sua senha foi alterada" passou a orientar
   "Esqueci minha senha" no app por texto, em vez de um `limio://recuperar-senha`.

## Alternativas descartadas

- **Manter `limio://`** (decisão de 2026-10-16): pelos três problemas acima e porque contraria o card ETI-47.
- **Thymeleaf / renderização no servidor:** adiciona dependência e um segundo jeito de responder HTTP. A página
  estática que chama a API JSON reaproveita os endpoints e as mensagens de erro que já existem.
- **HTML montado em String no controller:** difícil de manter e mistura apresentação com tradução HTTP.
- **Universal Links / App Links agora:** exigem domínio `https` fixo, `apple-app-site-association`, `assetlinks.json`
  e a configuração no app. Fica como próximo passo e não muda os links: eles já são `https` no domínio da API.

## Consequências

- Deploy precisa definir `APP_WEB_URL` com o domínio `https` público da API. Sem ela, o padrão é
  `http://localhost:8080`, que só serve para dev.
- `APP_DEEPLINK_URL` (e o resto de `APP_FRONTEND_URL`, se ainda existir na Railway) pode ser removida do ambiente.
- No `limio-app`, a tela `nova-senha` (aberta por `limio://nova-senha?token=...`) deixa de ser o destino do e-mail.
  O app continua precisando das telas de login (05), "Esqueci minha senha" e "Confirmar e-mail" com "Reenviar link".
  Esse "Reenviar link" agora tem endpoint: `POST /auth/reenviar-confirmacao-email`.
- Para abrir o app direto pelo link no futuro, basta configurar Universal Links / App Links no domínio de
  `APP_WEB_URL` para os caminhos `/nova-senha` e `/confirmar-email`, sem mudar a API.
- Design das duas páginas e textos finais ainda dependem do Figma (pendência de Design no card). As páginas usam as
  cores do guia do app (`limio-app/tailwind.config.js`) até lá.
