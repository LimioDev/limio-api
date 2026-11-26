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

## 2026-11 — SMS sai dos canais de notificação (ETI-134)

`PreferenciaNotificacao` perdeu a coluna `sms` (`V36`) e o campo correspondente em entity, request, response,
mapper e usecases — canais passam a ser só `push`, `email`, `whatsapp`. O canal nunca chegou a disparar
nenhuma notificação de fato (ETI-112 não implementado); não tem relação com a confirmação de telefone por OTP
(UC04), que saiu de escopo no mesmo card por motivo independente — ver `auth/docs/history/CHANGELOG.md`.

## 2026-11 — notificações do usuário: listar e marcar como lidas (ETI-110)

Primeira entidade raiz do módulo: `Notificacao` (tabela `notificacao`, V36), uma linha por notificação mostrada na
tela 43, com `usuario_id` e `anuncio_id` crus. `GET /notificacoes` (página de 20 + total de não lidas pro sino),
`POST /notificacoes/{id}/lida` e `POST /notificacoes/lidas`; as duas marcações devolvem o novo total de não lidas.
Notificação de outra pessoa responde 404 `NOTIFICACAO_NAO_ENCONTRADA`, igual a id inexistente.

`TipoNotificacao` tem um valor por evento de domínio que já pede aviso; o envio (ETI-112), que grava as linhas, pode
acrescentar valores sem migration (coluna sem `CHECK`). Conta encerrada passa a apagar também as notificações
(`RemoverNotificacoesDaContaEncerradaUseCase`), pelo mesmo motivo da preferência. Detalhe em
`../features/ETI-110-listar-notificacoes-e-marcar-como-lidas.md`.

## 2026-11 — envio dos avisos respeitando as preferências (ETI-112)

O módulo passa a reagir aos eventos de aviso dos outros módulos: `RegistrarAvisosUseCase` grava a `Notificacao` pra
lista do app e `EntregarNotificacaoUseCase` envia, fora da requisição (`@Async`, primeiro uso no projeto,
`comum/config/AssincronoConfig`), só pelos canais ligados. Push pelo Expo Push Service (aparelhos registrados em
`aparelho_push`, rotas `POST /notificacoes/aparelhos` e `/remover`), e-mail pelo Resend. WhatsApp nunca sai: o
consentimento exige `AceiteTermo`, que não existe. Cada tentativa vira uma linha em `envio_notificacao` (V37).

O padrão de conta nova da preferência saiu do usecase de consulta pra `PreferenciaNotificacao.padraoContaNova`, porque
a entrega usa o mesmo padrão. Conta encerrada passa a apagar também os aparelhos de push. `ServicoCanceladoEvent` (#38)
e `AnuncioExpiradoEvent` (#39) ficam pra depois do merge delas. Detalhe em
`../features/ETI-112-envio-de-notificacoes-respeitando-preferencias.md`.

## 2026-11 — ajustes do review da #43 (ETI-112)

Cada canal virou uma `CanalDeEnvioService` (push, e-mail, WhatsApp) e o `EntregarNotificacaoUseCase` só itera: canal
novo não mexe no usecase. Envio desligado no ambiente não grava mais `ENVIADO`. O push trata cada lote à parte e não
perde os tokens inválidos quando outro lote falha. Fila do executor cheia roda o envio em quem publicou, em vez de
descartar. Os não escolhidos da escolha saem numa consulta só (`ConsultaCandidatura.buscarPrestadoresIds`).

## 2026-11-20 — eventos de reagendamento disponíveis para os avisos (ETI-99)

O módulo `anuncio` passou a publicar `ReagendamentoPropostoEvent`, `ReagendamentoCanceladoEvent`,
`ReagendamentoAceitoEvent`, `ReagendamentoRecusadoEvent` e `ReagendamentoExpiradoEvent` (`comum/eventos/`), dentro da
transação de cada operação. Ainda não há ouvinte em `RegistrarAvisosUseCase`: ligar cada evento a um aviso é da US56
(ouvinte `AFTER_COMMIT` + `REQUIRES_NEW`, como os demais). Todos trazem `contratanteId`, `prestadorId` e `proponenteId`
para decidir o destinatário: o aceite avisa as duas partes; proposta e cancelamento, a contraparte; recusa, o
proponente; expiração, as duas. Detalhe em `anuncio/docs/features/ETI-99-reagendar-o-servico.md`.

## 2026-11-25 — aviso de conclusão distingue o silêncio (ETI-98)

`ServicoConcluidoEvent` ganhou `origem`; na conclusão automática o aviso diz "foi concluído automaticamente". Mesmo
tipo (`SERVICO_CONCLUIDO`) e mesmos destinatários.
