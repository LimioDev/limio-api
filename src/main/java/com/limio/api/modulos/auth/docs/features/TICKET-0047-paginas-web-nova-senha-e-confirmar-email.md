# TICKET-0047 — Páginas web "Nova senha" e "Confirmar e-mail"

## Link do card

ETI-47 ([auth] [BACK-END] Páginas web Confirmar e-mail/Nova senha). Origem: ETI-30 (Cadastro) e ETI-33
(Recuperar senha). UC05 e UC06.

Decisão de arquitetura: [ADR-0002](../../../../../../../../../../docs/adr/0002-links-de-email-https-com-paginas-web.md).

## Status

Implementado. Falta o design (Figma ainda não existe) e a validação do tech lead para o ADR-0002.

## User story

Como um usuário que recebeu um link por e-mail, eu quero abri-lo no navegador e concluir a ação, para confirmar meu
e-mail ou definir uma nova senha.

## Regra de negócio implementada

| Critério do card | Como ficou |
|---|---|
| Página "Nova senha": campos de nova senha e confirmação | `GET /nova-senha` → `static/paginas/nova-senha.html` |
| Requisitos visíveis (8 caracteres, letra e número) | Lista que marca cada requisito enquanto a pessoa digita (`nova-senha.js`) |
| Nova senha de 8 a 72, com 1 letra e 1 número; confirmação igual | Checado na página como ajuda; a regra que vale é a do `RedefinirSenhaRequest` (`@Pattern`). A confirmação só existe na página e não vai para a API |
| Página "Confirmar e-mail" mostra o resultado | `GET /confirmar-email` → `static/paginas/confirmar-email.html`, que confirma ao abrir |
| "Nova senha" chama a redefinição pelo link (ETI-33) | `POST /auth/redefinir-senha` (já existia) |
| "Confirmar e-mail" chama a confirmação (ETI-30) | `POST /auth/confirmar-email` (novo, `ConfirmarEmailUseCase`, UC05) |
| Páginas servidas pela `limio-api`, em rotas públicas | `comum/config/PaginasWebConfig` + `SecurityConfig` |
| O link traz um código único que identifica o usuário (`VerificacaoContato`) | Token de 256 bits; no banco só o SHA-256 (`token_hash`; o card chama de `codigoHash`) |
| Validade e uso único continuam nos usecases | `RedefinirSenhaUseCase` e `ConfirmarEmailUseCase`; a página não repete regra |
| A redefinição encerra todas as sessões | `SessaoService.revogarTodas` (regra do ETI-33, mantida) |
| Sucesso (senha): orientação para entrar pela tela 05 | "Senha alterada" + botão "Abrir o app" (`limio://login`) |
| Sucesso (e-mail): mensagem de e-mail confirmado | "E-mail confirmado" + "Abrir o app" |
| Link inválido, expirado ou usado: recusa | Seção "Este link não vale mais" nas duas páginas |
| E-mail: pedir outro em "Reenviar link" no app | Texto da recusa + `POST /auth/reenviar-confirmacao-email` (novo) |
| Senha: pedir outro em "Esqueci minha senha" | Texto da recusa + `POST /auth/recuperar-senha` (já existia) |

### O que o ETI-30 ainda não tinha e entrou aqui

O card diz que validade e uso único "continuam nos usecases do ETI-30". Mas o ETI-30 (`feature/30`) só implementou o
cadastro: nenhum código emitia link no cadastro nem consumia o token `CONFIRMACAO_EMAIL`. Para a página ter o que
chamar, entraram:

- **Cadastro emite o link de confirmação** (`CadastrarUsuarioUseCase` → `EmitirConfirmacaoEmailUseCase`). O e-mail sai
  depois do commit. O login continua sem esperar a confirmação (`StatusConta` não muda).
- **`POST /auth/confirmar-email`** (UC05), público. Segue o contrato que o TICKET-0048 deixou para a UC05: trava a
  conta, relê o token travado, recusa conta encerrada e consome sob o lock. Token inexistente, expirado, já usado ou de
  conta encerrada têm a mesma resposta: 401 `LINK_CONFIRMACAO_EMAIL_INVALIDO`. Sucesso preenche
  `usuario.email_confirmado_em`.
- **`POST /auth/reenviar-confirmacao-email`** (o "Reenviar link" do app), público e anti-enumeração como
  `/auth/recuperar-senha`: sempre 200, com ou sem conta. Só reenvia se a conta existe, não está encerrada, o e-mail
  ainda não foi confirmado e o limite não bateu.
- **Limite único de 3 e-mails de confirmação por hora e por conta**: cadastro, troca de e-mail e reenvio contam
  juntos (`auth.confirmacao-email.*`). Cada emissão consome os links anteriores, então só o último vale.

## Contrato da API

| Operação | Rota | Autenticação | Entrada | Saída |
|---|---|---|---|---|
| Página "Nova senha" | `GET /nova-senha#token=...` | pública | token no fragmento | HTML |
| Página "Confirmar e-mail" | `GET /confirmar-email#token=...` | pública | token no fragmento | HTML |
| Confirmar e-mail | `POST /auth/confirmar-email` | pública | `{ token }` | 200, sem corpo |
| Reenviar link de confirmação | `POST /auth/reenviar-confirmacao-email` | pública | `{ email }` | 200, sem corpo (sempre o mesmo) |
| Redefinir senha pelo link | `POST /auth/redefinir-senha` | pública | `{ token, novaSenha }` | 200, sem corpo (sem mudança) |

`CodigoErro` novo: `LINK_CONFIRMACAO_EMAIL_INVALIDO` (401, mesmo status do `TOKEN_RECUPERACAO_INVALIDO`).
Token em branco: 400 `VALIDACAO_FALHOU`.

## Decisão técnica

Pendência do tech lead ("como a API gera o HTML") resolvida pelo dev e registrada no ADR-0002, a validar:

- **HTML estático + JavaScript**, sem template engine. A página lê o token do fragmento, apaga o fragmento da barra de
  endereço e faz o POST JSON. Mensagens de erro vêm do `ErroResponse` da API.
- **Token no fragmento (`#token=`)**: nunca chega ao servidor em GET, então não vai para log de acesso nem para o
  cabeçalho Referer. O link `https` substitui o `limio://` (`LinkApp` virou `LinkWeb`, `app.deeplink.url` virou
  `app.web.url`).
- **CSP `default-src 'self'` e `Referrer-Policy: no-referrer`** em todas as respostas (`SecurityConfig`). As páginas
  não usam script, estilo nem fonte de fora, nem CSS inline.
- **"Confirmar e-mail" confirma ao abrir**, sem botão. Risco aceito: um antivírus de e-mail que execute JavaScript pode
  consumir o link antes da pessoa. Mesmo assim, só prova que o e-mail chegou na caixa certa. Se virar problema, basta
  trocar por um botão "Confirmar".
- **Consumo atômico do token nos dois fluxos.** O TICKET-0048 apontava que o `RedefinirSenhaUseCase` não consumia o
  token de forma atômica: dois envios simultâneos do mesmo link passavam juntos. Agora os dois usecases fazem
  "dono do token → lock da conta → token com `SELECT ... FOR UPDATE`" (`VerificacaoContatoService.buscarParaConsumir`).
  A ordem conta-depois-token é a mesma da troca de e-mail, para as duas não se travarem mutuamente. Testado com dois
  envios simultâneos: um 200 e um 401.
- **Cadastro sob concorrência.** Dois cadastros simultâneos com o mesmo e-mail ou CPF passavam pela pré-checagem e o
  segundo estourava `uk_usuario_email`/`uk_usuario_cpf` como 500 (risco aceito no TICKET-0048). Agora
  `UsuarioService.salvarNovo` traduz a violação para a mesma `CadastroIndisponivelException` (409).
- **E-mail "senha alterada" sem link.** Não existe página web para pedir a recuperação, e `limio://` em e-mail é o que
  o ADR-0002 elimina. O texto orienta "Esqueci minha senha" no app.

## Fora de escopo / pendências

- **Design**: desenho das duas páginas e textos finais (Figma ainda não existe). Hoje usam as cores do guia do app.
- **Tech lead**: validar o ADR-0002.
- **limio-app**: ligar o botão "Reenviar link" (`ConfirmarEmailView`, hoje só muda o estado da tela) a
  `POST /auth/reenviar-confirmacao-email`. A tela `nova-senha` do app deixa de ser aberta pelo e-mail.
- **Deploy**: definir `APP_WEB_URL` (domínio `https` da API) e remover `APP_DEEPLINK_URL`/`APP_FRONTEND_URL` da Railway.
- Universal Links / App Links para abrir o app direto pelo mesmo link: próximo passo opcional (ver ADR-0002).

## Testes

- `spec/`: `ConfirmarEmailUseCaseTest`, `EmitirConfirmacaoEmailUseCaseTest`, `ReenviarConfirmacaoEmailUseCaseTest`,
  `LinkWebTest`, e os ajustes de `RedefinirSenhaUseCaseTest`, `CadastrarUsuarioUseCaseTest`, `TrocarEmailUseCaseTest`.
- `story/`: `ConfirmarEmailStoryTest` (cadastro → link → confirmação, reuso, expiração, finalidade trocada, cliques
  simultâneos, reenvio e limite, páginas públicas com CSP) e um caso novo em `RecuperarEAlterarSenhaStoryTest`
  (redefinição simultânea com o mesmo link).
