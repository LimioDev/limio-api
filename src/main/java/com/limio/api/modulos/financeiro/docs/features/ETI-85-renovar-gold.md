# ETI-85 — Renovação mensal do GOLD: cobrança, falha, novas tentativas e volta ao FREE

Origem: Backlog US28 · "[prestador] Tratar falha de cobrança do GOLD" (sem card original no Jira). Também cobre a
renovação aprovada e o fim do ciclo depois do cancelamento (US27). Relacionados: ETI-81 (assinar o GOLD) e o card
"Gerenciar a assinatura GOLD".

## User Story

Como plataforma, eu quero renovar o GOLD todo mês, tentar de novo quando a cobrança falhar e voltar o Prestador para
FREE só quando não houver jeito, para que ele não perca o GOLD por um problema pontual.

## Contrato da API

Rota chamada pelo gateway, não pelo app.

| Operação | Rota | Entrada | Saída |
|---|---|---|---|
| Receber aviso do gateway | `POST /financeiro/gold/webhook/asaas` (só com `financeiro.gateway.provedor=asaas`) | webhook do Asaas: header `asaas-access-token`, corpo `event` + `payment.id`/`payment.externalReference` | 200 |

`CodigoErro`: `WEBHOOK_INVALIDO` (401), token do header ausente ou diferente de `asaas.webhook.token`, sem processar.

Eventos do Asaas que resolvem uma cobrança: `PAYMENT_CONFIRMED` e `PAYMENT_RECEIVED` aprovam;
`PAYMENT_CREDIT_CARD_CAPTURE_REFUSED` e `PAYMENT_REPROVED_BY_RISK_ANALYSIS` recusam. Os demais respondem 200 sem
efeito.

## Como funciona

A renovação é do LIMIO, não do Asaas: a ETI-81 cobra o cartão salvo com uma cobrança avulsa (`creditCardToken`), sem
assinatura recorrente no gateway. Por isso a renovação também é uma cobrança avulsa, disparada por uma rotina.

- **`RenovarGoldUseCase`** (`@Scheduled`, `financeiro.gold.renovacao.cron`, padrão a cada 15 min). Pega as
  assinaturas `ATIVA` com `data_fim_ciclo` vencido e sem `proxima_tentativa_em` no futuro. Para cada uma:
  - renovação cancelada (`renovacao_cancelada_em`): encerra (`ENCERRADA`, `encerrada_em`), sem cobrar;
  - senão cobra o próximo ciclo no cartão salvo (`meio_pagamento`). Mesmos três passos da ETI-81: reserva a
    `Cobranca` `PENDENTE` numa transação curta, cobra fora de transação e confirma numa segunda transação curta.
- **`ProcessarAvisoPagamentoUseCase`** (webhook): acha a cobrança pela `chaveIdempotencia`, que o LIMIO manda como
  `externalReference`, ou pelo id da cobrança no gateway. Aplica o resultado só se a cobrança ainda estiver sem
  resultado (`PENDENTE` ou `FALHOU`).
- **`ResolverCobrancaGoldHelper`**: o efeito do resultado, igual para as duas origens.

| Resultado | Cobrança | Assinatura | Evento |
|---|---|---|---|
| Renovação aprovada | `APROVADA` + `DocumentoFiscal` | `dataInicioCiclo` ← `dataFimCiclo`, `dataFimCiclo` + 1 mês | `CobrancaGoldAprovadaEvent` (aviso "Cobrança GOLD" e e-mail do recibo) |
| Recusada, com tentativas sobrando | `RECUSADA` (`tentativa` = n) | segue `ATIVA` (GOLD), `proximaTentativaEm` = agora + intervalo | `CobrancaGoldRecusadaEvent` com `proximaTentativaEm` |
| Recusada, última tentativa | `RECUSADA` | `ENCERRADA`, `encerradaEm` = agora (FREE) | `CobrancaGoldRecusadaEvent` com `proximaTentativaEm = null` |
| Gateway sem resposta | `FALHOU` | segue `ATIVA`, `proximaTentativaEm` = agora + intervalo | nenhum (não conta como tentativa) |
| Sem cartão salvo | `RECUSADA`, sem chamar o gateway | igual à recusa | igual à recusa |
| Aviso de aprovação da cobrança da assinatura (ETI-81) que ficou `PENDENTE` | `APROVADA` + recibo | `ATIVA` | `GoldAssinadoEvent` |

- **Ciclo**: o ciclo novo começa onde o anterior terminou, mesmo que a renovação só tenha sido aprovada numa nova
  tentativa dias depois.
- **Quantidade de tentativas**: `financeiro.gold.renovacao.max-tentativas` (padrão 3) e
  `financeiro.gold.renovacao.intervalo-tentativa` (padrão `1d`). O card sugere uma chave em `Parametro`, mas essa
  tabela não existe no projeto. Ficou em propriedade, como os outros parâmetros (`moderacao.denuncia.*`,
  `anuncio.reagendamento.*`).
- **Webhook repetido**: a cobrança já resolvida não muda mais. O segundo aviso, ou o aviso de uma cobrança que a
  resposta síncrona já resolveu, não grava cobrança, recibo nem evento.
- **Aviso atrasado**: resultado de um ciclo que já não está em aberto (renovado ou encerrado por outra via) só fica na
  cobrança. Se for uma aprovação, o log pede para conferir estorno.
- **Concorrência**: rotina, webhook e assinar usam a trava `ASSINATURA_GOLD` do usuário e releem o estado depois dela.
  A confirmação da rotina não aplica nada se o aviso já resolveu a cobrança nesse meio tempo.
- **Cobrança da renovação sem resposta** (o processo caiu entre cobrar e confirmar): espera o aviso do gateway por um
  intervalo de tentativa. Depois disso vira `FALHOU` e a renovação cobra de novo.
- **Depois de voltar para FREE**: só as novas escolhas contam no teto de 4. Já era assim: escolha feita com GOLD
  ativo não consome cota (`aplicacao.cota_consumida`, ETI-69/ETI-94), e a cota volta a ser lida pela porta
  `ConsultaPlanoPrestador`, que devolve FREE para `ENCERRADA`.
- **`isGoldAtivo`**: o card fala em `isGoldAtivo = false`, mas esse campo não existe (decisão da ETI-81). GOLD é
  `assinatura_gold.status = ATIVA`, e `ENCERRADA` é FREE.
- **Assinar de novo** depois de `ENCERRADA` reativa a mesma linha (`AssinarGoldUseCase`) e limpa `encerrada_em`,
  `renovacao_cancelada_em` e `proxima_tentativa_em`.

## Migration

`V45__renovar_assinatura_gold.sql`: `assinatura_gold` ganha `proxima_tentativa_em`, `renovacao_cancelada_em` e
`encerrada_em`; `cobranca` ganha `inicio_ciclo_cobrado` (nulo na cobrança da assinatura) e `tentativa`. V41 está na
PR #51 e V42–V44 no painel admin.

## Testes

- `spec/ResolverCobrancaGoldHelperTest`, `spec/RenovarGoldUseCaseTest`, `spec/ProcessarAvisoPagamentoUseCaseTest`,
  `spec/AvisoPagamentoMapperTest`.
- `story/RenovarGoldStoryTest` (gateway simulado): renovação aprovada; recusada com nova tentativa aprovada; todas
  recusadas (volta para FREE e pode assinar de novo); fim de ciclo com renovação cancelada; cancelada com o ciclo
  ainda vigente; gateway sem resposta; sem cartão salvo.
- `story/AvisoPagamentoGoldStoryTest` (Asaas falso): webhook válido (200) que renova; webhook duplicado; recusa pelo
  webhook; assinatura falsa e sem header (401 `WEBHOOK_INVALIDO`, sem processar); assinatura pendente da ETI-81
  confirmada pelo aviso; evento que não resolve cobrança; renovação pelo `GatewayPagamentoAsaas` seguida do aviso.

## Pendências (fora deste card)

- **Avisos da tela 43** (US56): os eventos `CobrancaGoldAprovadaEvent` e `CobrancaGoldRecusadaEvent` ficam em
  `comum/eventos`, mas o ouvinte é do card de notificações, como no reagendamento (ETI-99).
- **Cancelar a renovação** (card "Gerenciar a assinatura GOLD"): grava `renovacao_cancelada_em`. Este card só lê.
- **Estorno e chargeback** (`PAYMENT_REFUNDED`, `PAYMENT_CHARGEBACK_*`): fora do card, só logados.
- **Quantidade e intervalo das tentativas**: valores provisórios até o PO definir.
- **Aprovação da assinatura pelo aviso não salva o cartão**: o aviso não traz o token. Se isso acontecer, a primeira
  renovação recusa por falta de cartão até o Prestador cadastrar de novo.
- **Corrida rara na ETI-81**: se o aviso de aprovação da assinatura chegar entre a resposta síncrona e a confirmação
  do `AssinarGoldUseCase`, o GOLD fica ativo, mas o app recebe erro (o recibo já existe). A confirmação do
  `AssinarGoldUseCase` não toma a trava; ficou de fora para não conflitar com a PR #50 (ETI-139), que reescreve esse
  trecho.
