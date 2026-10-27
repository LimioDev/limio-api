# Histórico — notificacao

Registre aqui, em ordem cronológica, decisões e mudanças de regra que não ficam óbvias só lendo o código.

Formato: uma seção `## AAAA-MM — título` por entrada neste arquivo, com o contexto do "porquê" da mudança.

## 2026-10 — especificação da preferência de notificação (ETI-34)

Primeiro desenho da feature de consultar e atualizar preferências de notificação (Push, E-mail, SMS e WhatsApp) do
usuário logado. Detalhe da regra em `docs/features/UC15-configurar-preferencias-de-notificacao.md` e fluxo em
`docs/funcionalidade/EXPLICACAO_CLASSES.md`.

Decisões que continuam valendo: tabela própria (não colunas em `usuario`); `PUT` de estado completo; `AceiteTermo`
gravado só na transição do WhatsApp de desligado para ligado; regras de envio (WhatsApp sem consentimento, e-mails de
segurança sempre enviados) ficam com os emissores de notificação, não com esta feature. A decisão de "módulo próprio"
foi substituída — ver a entrada sobre `modulos/notificacao/` abaixo.

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

## 2026-10 — revisão da PR da ETI-34: upsert atômico, WhatsApp bloqueado e usecase sem entidade parcial

- **Upsert atômico.** "Busca, depois salva" deixava dois `PUT` simultâneos de um usuário sem registro violarem
  `uk_preferencia_notificacao_usuario_id` (500). O repository agora faz `INSERT ... ON CONFLICT (usuario_id) DO UPDATE`
  e o usecase, `@Transactional`, grava e relê o registro. Captura da violação + releitura foi descartada: no Postgres a
  transação fica abortada após o erro.
- **WhatsApp `true` rejeitado.** Sem `AceiteTermo`, ligar o canal criaria opt-in sem trilha auditável (LGPD). Enquanto
  o aceite não existe, `PUT` com `whatsapp = true` responde `422` (`WHATSAPP_INDISPONIVEL`) e não grava; `false` segue
  aceito. Ao implementar o aceite, trocar o `throw` pela gravação na transição `false -> true`.
- **Usecase sem entidade parcial.** `executar(usuarioId, push, email, sms, whatsapp)` com `boolean` primitivo, no lugar
  de uma `PreferenciaNotificacao` incompleta. O mapper perdeu o `toEntity` e deixou de implementar `BaseMapper`.
- **Pendência registrada:** limpeza da linha na exclusão de conta (evento de `auth` + listener) não existe — item 8 do
  UC15.
- **Testes.** A story de persistência via usecase foi removida; seus cenários únicos (dois `PUT` mantêm um registro e
  unicidade no banco) passaram para `PreferenciaNotificacaoStoryTest`.

## 2026-10-26 — TICKET-0047: preferência apagada no encerramento de conta

Resolvida a pendência do item 8 do UC15: `RemoverPreferenciaDaContaEncerradaUseCase` escuta `ContaEncerradaEvent`
(`AFTER_COMMIT` + `REQUIRES_NEW`, mesmo padrão do listener de `anuncio`) e apaga a linha de
`preferencia_notificacao`. Falha é logada e não volta para quem encerrou a conta.
