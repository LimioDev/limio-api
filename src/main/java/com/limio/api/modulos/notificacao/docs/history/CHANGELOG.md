# Histórico — notificacao

Registre aqui, em ordem cronológica, decisões e mudanças de regra que não ficam óbvias só lendo o código.

Formato de cada entrada: novo arquivo `AAAA-MM-titulo-curto.md` nesta pasta, com contexto do "porquê" da mudança.

## 2026-10 — especificação do módulo `preferencianotificacao` (ETI-34)

Documentada, sem código, a feature de consultar e atualizar preferências de notificação (Push, E-mail, SMS e
WhatsApp) do usuário logado. Detalhe da regra em `docs/features/UC15-configurar-preferencias-de-notificacao.md` e fluxo
previsto em `docs/funcionalidade/EXPLICACAO_CLASSES.md`.

Decisões registradas: módulo próprio (não colunas em `usuario`); `PUT` de estado completo; `AceiteTermo` gravado só
na transição do WhatsApp de desligado para ligado; regras de envio (WhatsApp sem consentimento, e-mails de segurança
sempre enviados) ficam com os emissores de notificação, não com esta feature.

A autenticação (JWT) pertence a outra tarefa e não foi especificada aqui; a feature apenas exige usuário autenticado.

Pendências em aberto (padrão do `sms`, opt-in de WhatsApp no cadastro, origem de `documento`/`versao`/`ip` do aceite,
caminhos HTTP) estão listadas no arquivo da feature. Atualizar esta página quando forem resolvidas e quando a
implementação começar.

## 2026-10 — endpoints GET/PUT `/conta/preferencias-notificacao` (ETI-34)

Com a autenticação do TICKET-0032 em `develop`, entrou o `PreferenciaNotificacaoController` (`modulos/notificacao/`):
recebe `UsuarioAutenticado` via `@AuthenticationPrincipal` e só lê/grava a preferência do próprio usuário. Story HTTP
(`PreferenciaNotificacaoStoryTest`) cobre `200` no GET/PUT, isolamento entre usuários, `400` (campo ausente ou não
booleano) e `401` sem login. Mensagens `preferenciaNotificacao.*` adicionadas ao `ValidationMessages.properties`.

Padrões de conta nova definidos: Push ligado; E-mail, SMS e WhatsApp desligados (WhatsApp `false` enquanto o
cadastro não coleta opt-in). O `GET` só consulta e devolve os padrões sem gravar; o primeiro `PUT` cria o registro e
os seguintes o atualizam. A coerção de booleano do Jackson (`1`, `"true"`) foi mantida; a configuração global não foi
alterada.

Continua pendente: o registro em `AceiteTermo` ao ligar o WhatsApp (sem contrato definido).

## 2026-10 — preferência de notificação dentro de `modulos/notificacao/` (ETI-34)

A decisão de "módulo próprio" `modulos/preferencianotificacao/` da especificação foi substituída: a preferência é a
subentidade `preferencianotificacao/` do módulo `modulos/notificacao/` (já previsto no `package-info.java` do módulo),
e os docs foram movidos para `modulos/notificacao/docs/`. Continua valendo a tabela própria `preferencia_notificacao`
em vez de colunas em `usuario`.
