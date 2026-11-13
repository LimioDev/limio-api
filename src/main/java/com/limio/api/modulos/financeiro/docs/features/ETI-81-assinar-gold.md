# ETI-81 — Assinar o plano GOLD

Origem: Backlog US25 · "[prestador] Assinar o plano GOLD". Card par (frontend):
"[financeiro] [FRONT-END] Tela 'Assinatura' (assinar GOLD)".

## User Story

Como um Prestador, eu quero que a API contrate o GOLD pelo gateway e libere o plano na hora, para que eu
não tenha limite de escolhas no mês.

## Contrato da API

| Operação | Rota | Entrada | Saída |
|---|---|---|---|
| Consultar plano GOLD | `GET /financeiro/gold` | — | `codigoPlano`, `nome`, `precoCentavos`, `moeda`, `ciclo`, `cartaoSalvo` (`bandeira`+`ultimos4`, ou `null`) |
| Assinar GOLD | `POST /financeiro/gold/assinatura` | `codigoPlano`, `gatewayToken` | `status`, `renovaEm` |

`CodigoErro`: `PLANO_NAO_ENCONTRADO` (404), `ASSINATURA_JA_ATIVA` (409), `COBRANCA_EM_ANDAMENTO` (409),
`PAGAMENTO_RECUSADO` (402), `GATEWAY_INDISPONIVEL` (503), `ACESSO_NEGADO` (403, papel diferente de
PRESTADOR).

## Regra de negócio

- **Dados do cartão**: só o token do gateway é salvo (`meio_pagamento.gateway_token`) — nunca número nem
  CVV. `bandeira`/`ultimos4` vêm da resposta do gateway à cobrança, não do request.
- **A cobrança nunca roda dentro de transação** (efeito externo irreversível, timeout de segundos) — ver
  `AssinarGoldUseCase` e o `CHANGELOG` (correções de review da PR #36). Fluxo em três passos: reservar
  (transação curta, grava `AssinaturaGold`+`Cobranca` `PENDENTE`) → cobrar (fora de transação) →
  confirmar (segunda transação curta, ativa ou cancela).
- **Já é GOLD**: assinatura com `status = ATIVA` recusa uma segunda assinatura
  (`ASSINATURA_JA_ATIVA`). `CANCELADA` cobre tanto o card futuro de cancelamento (US26/US27) quanto uma
  tentativa recusada pelo gateway — os dois reativam a mesma linha.
- **Cobrança em andamento**: assinatura `PENDENTE` (reservada por uma tentativa anterior, cobrança ainda
  não confirmada) recusa uma nova tentativa (`COBRANCA_EM_ANDAMENTO`), sem chamar o gateway de novo.
- **Pagamento aprovado**: grava `AssinaturaGold` (`ATIVA`, ciclo de 1 mês a partir de agora), `Cobranca`
  (`APROVADA`), `DocumentoFiscal` (recibo) e publica `GoldAssinadoEvent` pro e-mail do recibo
  (`AFTER_COMMIT`). `ConsultaPlanoPrestadorService` (porta `ConsultaPlanoPrestador`, ETI-79) passa a
  devolver GOLD pro usuário a partir daqui — é isso que libera o teto de cota em `perfil`.
- **Pagamento recusado**: `AssinaturaGold` vira `CANCELADA` e `Cobranca` vira `RECUSADA` (ao contrário da
  decisão original deste card — ver `CHANGELOG`), `PAGAMENTO_RECUSADO`, o Prestador permanece FREE.
- **Clique duplo**: trava transacional por usuário (`EscopoTrava.ASSINATURA_GOLD`) serializa o passo de
  reserva; depois que ela comita e solta o lock, uma segunda tentativa encontra a linha `PENDENTE`/`ATIVA`
  e é recusada sem chamar o gateway de novo. `cobranca.chave_idempotencia` (índice único) é a rede de
  segurança se essa checagem falhar sob corrida real.
- **Pendência sem reconciliação automática**: uma falha entre cobrar e confirmar deixa a linha
  `PENDENTE` — o webhook do Asaas só loga hoje, reconciliação é manual.
- **Gateway**: simulado por padrão (`GatewayPagamentoSimulado`) ou Asaas
  (`financeiro.gateway.provedor=asaas`, `GatewayPagamentoAsaas`). No simulado, tokens
  `financeiro.gateway.token-recusado` e `financeiro.gateway.token-indisponivel` controlam o resultado nos
  testes; qualquer outro token aprova.

## Testes

- `spec/ConsultarGoldUseCaseTest`, `spec/AssinarGoldUseCaseTest` — unidade, mocka services/gateway.
- `story/GoldStoryTest` — BDD HTTP: aprovado, recusado, gateway indisponível, já-GOLD, clique duplo,
  papel errado, sem token.

## Pendências (fora deste card)

- Tela "Assinatura" (card par `[FRONT-END]`).
- Cadastrar/atualizar meio de pagamento fora do fluxo de assinar (insumo citado no card original, sem
  ticket próprio ainda).
- Cancelar assinatura GOLD (US26/US27) — vai produzir `StatusAssinatura.CANCELADA`, que o schema e o
  `AssinarGoldUseCase` já sabem tratar.
- Recibo fiscal de fato (hoje `documento_fiscal.numero` é só `"NF-" + id da cobrança`, sem emissão real).
