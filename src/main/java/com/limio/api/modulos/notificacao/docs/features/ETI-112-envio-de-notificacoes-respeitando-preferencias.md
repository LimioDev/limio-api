# ETI-112 — Serviço único de envio de notificações, respeitando as preferências

## Status

Implementado para os eventos que já existem. Empilhado sobre a #42 (ETI-110, tabela `notificacao`) e a #41 (ETI-65,
`NovoServicoNaAreaEvent`): só entra depois das duas.

## Link do card

[ETI-112](https://kaueamaral96.atlassian.net/browse/ETI-112) — [notificacao] [BACK-END] Serviço único de envio de
notificações, respeitando as preferências. Origem: Backlog US56. Card par: [notificacao] [FRONT-END] Registrar o
aparelho para push e abrir a tela certa ao tocar no aviso. Relacionados: ETI-34 (preferências), ETI-45 (Resend).

## User Story

Como plataforma, eu quero enviar cada aviso só pelos canais que o usuário ligou e guardar o aviso para a lista do app,
para que ele receba o que quer e como quer.

## Contrato da API

As duas rotas exigem login.

| Operação | Rota | Entrada | Saída |
|---|---|---|---|
| Registrar aparelho para push | `POST /notificacoes/aparelhos` | `{ token, plataforma }` (`ExponentPushToken[...]`, `ANDROID`/`IOS`) | `204` |
| Remover aparelho (logout) | `POST /notificacoes/aparelhos/remover` | `{ token }` | `204` |

- **Erros:** 400 `VALIDACAO_FALHOU` (token vazio, fora do formato do Expo ou plataforma desconhecida). O card sugeria
  `DADOS_INVALIDOS`; o projeto já usa `VALIDACAO_FALHOU` pra isso.
- Registrar de novo o mesmo token não duplica. Se outra conta entrar no mesmo aparelho, o token passa pra ela.
- Remover token de outra conta, ou já removido, responde 204 sem apagar nada: o logout não pode travar por isso.
- Remover é `POST` com corpo porque o token tem colchetes e alguns clientes descartam o corpo de `DELETE`.
- O push leva em `data` o `notificacaoId`, o `tipo` e, se houver, o `anuncioId`, pro app abrir a tela certa.

## Fluxo

1. Um módulo publica um evento de aviso (os módulos só publicam, nenhum chama este — ADR-0003).
2. `RegistrarAvisosUseCase` escuta em `AFTER_COMMIT`, em transação própria: monta o texto (`AvisoHelper`), grava a
   `Notificacao` pra lista do app (ETI-110) e publica `NotificacaoRegistradaEvent` (interno).
3. `EntregarNotificacaoUseCase` escuta em `AFTER_COMMIT`, **fora da requisição** (`@Async`, executor
   `envioDeAvisos`): lê a `PreferenciaNotificacao` (ou o padrão de conta nova) e envia só pelos canais ligados,
   gravando um `EnvioNotificacao` por canal tentado (`ENVIADO` ou `FALHOU`, com o motivo). Cada canal é uma
   `CanalDeEnvioService` (`CanalPushService`, `CanalEmailService`, `CanalWhatsappService`); o usecase só confere a
   preferência (`PreferenciaNotificacao.ligado`) e itera. Canal sem tentativa (sem aparelho, sem e-mail confirmado,
   envio desligado no ambiente, WhatsApp) não grava linha.

| Canal | Como sai |
|---|---|
| Push | `CanalPushService` → `PushExpoService` (Expo Push Service) pros aparelhos registrados, em lotes de 100. Sem aparelho, nada a enviar. Token que o Expo responde como `DeviceNotRegistered` é apagado, mesmo quando outro lote falha; lote com falha vira `FALHOU` com "lote N de M" no motivo. |
| E-mail | `CanalEmailService` → `EmailAvisoService` (Resend, mesma chave do `auth`), só pra e-mail confirmado. Texto escapado no HTML. |
| WhatsApp | `CanalWhatsappService`. Nunca sai: precisa de consentimento ativo (`AceiteTermo` + `whatsapp = true`, decisão do PO), e `AceiteTermo` não existe. Sem provedor escolhido. |
| SMS | Fora de escopo desde o ETI-134. |

## Eventos que geram aviso

| Evento | Quem recebe | Tipo | Texto |
|---|---|---|---|
| `NovoServicoNaAreaEvent` (#41) | Prestador compatível | `NOVO_SERVICO_NA_AREA` | "Novo serviço na sua área" — "(categoria) em (bairro), a 3,4 km de distância." (card) |
| `CandidaturaCriadaEvent` | Empregador | `NOVA_CANDIDATURA` | "Novo candidato" — "Rafael S. se candidatou a '(anúncio)'." (card) |
| `CandidaturaRecusadaEvent` | Prestador | `CANDIDATURA_RECUSADA` | "Candidatura não aceita" — proposta |
| `PrestadorEscolhidoEvent` | Escolhido | `PRESTADOR_ESCOLHIDO` | "Você foi escolhido" — proposta |
| `PrestadorEscolhidoEvent` | Cada não escolhido (`ConsultaCandidatura.buscarPrestadoresIds`, uma consulta só) | `CANDIDATURA_ENCERRADA` | "Vaga preenchida" — proposta |
| `ServicoIniciadoEvent` | Empregador | `SERVICO_INICIADO` | "Serviço iniciado" — proposta |
| `CheckoutEnviadoEvent` | Empregador | `CHECKOUT_ENVIADO` | "Check-out enviado" — "(nome) enviou a foto de '(anúncio)'. Valide para finalizar o serviço." (card) |
| `ServicoConcluidoEvent` | As duas partes | `SERVICO_CONCLUIDO` | "Serviço concluído" — "'(anúncio)' foi concluído. Avalie a outra parte." |
| `AvisoDeAnuncioEvent` | Quem o evento indicar | `AVISO_DE_ANUNCIO` | o assunto do evento |

Nomes abreviados como na lista de candidatos ("Rafael S.", `NomeHelper`). O `ServicoConcluidoEvent` não diz se foi
validação ou silêncio de 24h, então o texto não diz "automaticamente".

**Fora desta entrega** (decisão de 2026-11-16): `ServicoCanceladoEvent` (#38) e `AnuncioExpiradoEvent` (#39) ganham
ouvinte num ajuste curto depois que as PRs entrarem. Reagendamento e cobrança GOLD ainda não publicam evento entre
módulos (o `GoldAssinadoEvent` é interno ao `financeiro`, que já manda o recibo por e-mail).

## Regras do card

| Critério | Implementação |
|---|---|
| Grava a `Notificacao` pra lista do app | `RegistrarAvisosUseCase`, antes de qualquer envio: todo aviso aparece na tela 43, saindo ou não por algum canal. |
| Só pelos canais ligados | `EntregarNotificacaoUseCase` lê a preferência; sem registro, o padrão de conta nova (push ligado, e-mail e WhatsApp desligados), que saiu do usecase de consulta pra `PreferenciaNotificacao.padraoContaNova` e é usado pelos dois. |
| Preenche `enviadaEm` e canal | `Notificacao.enviadaEm` é o instante do aviso; cada canal tentado vira um `EnvioNotificacao` (`canal`, `status`, `ocorridoEm`, `detalhe`). |
| WhatsApp só com consentimento | Ver tabela de canais. |
| E-mails de segurança saem sempre | Já é assim: o `auth` envia senha alterada e e-mail alterado pelo `EmailService` dele, sem olhar a preferência. A story confere com o e-mail desligado. |
| Falha não desfaz a ação | Envio fora da transação e da requisição. Falha num canal vira `FALHOU` e não impede os outros. A story confere que a candidatura continua `ENVIADA`. |
| Remover aparelho no logout | O app chama `POST /notificacoes/aparelhos/remover` antes de sair. Conta encerrada apaga os aparelhos junto com as notificações. |

## Decisões de implementação

- **Expo Push Service** (decisão de 2026-11-16): o app já usa `expo-notifications`, e o `ExponentPushToken` serve
  Android e iOS sem configurar Firebase nem APNs no back. `EXPO_ACCESS_TOKEN` é opcional.
- **Envio assíncrono** (`comum/config/AssincronoConfig`, primeiro `@EnableAsync` do projeto): sem isso, publicar um
  anúncio esperaria um push por Prestador compatível. Pool de 4 threads e fila de 1000, configuráveis. Fila cheia
  roda o envio na thread de quem publicou (`CallerRunsPolicy`) em vez de descartar. Nos testes,
  `notificacao.envio.assincrono=false` roda na mesma thread, menos no `EnvioDeAvisosAssincronoStoryTest`.
- **Escritas da entrega em `REQUIRES_NEW`** (`EnvioNotificacaoService.salvarTodos`, `AparelhoPushService.apagarTokens`):
  a entrega roda depois de um commit. Na mesma thread (testes), uma escrita `REQUIRED` entraria na transação já
  encerrada e não seria gravada.
- **Desligado fora de produção**: `notificacao.push.enviar=false` (padrão) e `email.enviar=false` só logam, igual ao
  `auth`, e não gravam linha em `envio_notificacao` (nada saiu). Em produção: `NOTIFICACAO_PUSH_ENVIAR=true` e
  `EMAIL_ENVIAR=true`.
- **Avisos da escolha numa transação só**: o escolhido e os não escolhidos são gravados juntos. Falha ao gravar é do
  banco e pegaria os outros também; como o envio só começa depois do commit, nenhum push sai pra aviso desfeito.
- **`new Resend(apiKey)`** é a 3ª cópia (`auth`, `financeiro`, `notificacao`), aceitável pelo ADR-0001. Vira bean em
  `comum/config/` quando aparecer uma 4ª ou o cliente precisar de mais que a chave.
- **Uma tabela de envio por canal** (`envio_notificacao`, V37), em vez de coluna na `notificacao`: guarda falha e
  motivo por canal sem mudar a lista do app. Some em cascata com a notificação.
- **Upsert por token** no registro do aparelho (`on conflict (token)`), mesmo idioma da preferência: dois registros
  simultâneos não estouram a restrição única.

## Configuração (Railway)

| Variável | Padrão | Produção |
|---|---|---|
| `NOTIFICACAO_PUSH_ENVIAR` | `false` | `true` |
| `EXPO_ACCESS_TOKEN` | vazio | só se o projeto Expo exigir |
| `NOTIFICACAO_ENVIO_THREADS` / `NOTIFICACAO_ENVIO_FILA` | `4` / `1000` | ajustar com o volume |
| `EMAIL_ENVIAR` | `false` | `true` (já existia) |

## Testes

- `spec/AvisoHelperTest` (3): vírgula decimal, texto do card, corte do título.
- `spec/RegistrarAvisosUseCaseTest` (1): escolhido e não escolhidos, com a busca em lote.
- `spec/EntregarNotificacaoUseCaseTest` (5): padrão de conta nova, falha no push com e-mail saindo, canal sem
  tentativa não grava linha, WhatsApp sem consentimento, canal sem implementação derruba a subida.
- `spec/CanalPushServiceTest` (4) e `spec/CanalEmailServiceTest` (3): dados do toque, token inválido apagado (inclusive
  com outro lote falhando), sem aparelho, envio desligado, e-mail não confirmado.
- `spec/PushExpoServiceTest` (6), com `MockRestServiceServer`: parsing dos tickets, `DeviceNotRegistered` x outros
  erros, lotes de 100, lote 2 falhando sem perder os inválidos do lote 1, resposta sem tickets, envio desligado.
- `comum/config/AssincronoConfigTest` (3): síncrono nos testes, thread do envio, fila cheia roda em quem publicou.
- `story/EnvioDeAvisosAssincronoStoryTest` (1): executor real, push sai na thread `envio-avisos-` e grava a linha.
- `story/EscolherPrestadorStoryTest`: o caminho feliz confere os avisos do escolhido e do não escolhido.
- `story/EnvioDeAvisosStoryTest` (13), com gatilho real (candidatar-se pela API): aviso na lista e push no aparelho;
  canal desligado (não envia); e-mail ligado; WhatsApp sem consentimento; falha do provedor (candidatura continua,
  aviso na lista, e-mail sai); token inválido apagado; e-mail de segurança com e-mail desligado; aparelho registrado
  e removido; registro duplicado; outra conta no mesmo aparelho; remover token de outra conta; token e plataforma
  inválidos.
