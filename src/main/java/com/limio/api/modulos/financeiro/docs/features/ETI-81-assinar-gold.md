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

`CodigoErro`: `PLANO_NAO_ENCONTRADO` (404), `ASSINATURA_JA_ATIVA` (409), `PAGAMENTO_RECUSADO` (402),
`GATEWAY_INDISPONIVEL` (503), `ACESSO_NEGADO` (403, papel diferente de PRESTADOR).

## Regra de negócio

- **Dados do cartão**: só o token do gateway é salvo (`meio_pagamento.gateway_token`) — nunca número nem
  CVV. `bandeira`/`ultimos4` vêm da resposta do gateway à cobrança, não do request.
- **Já é GOLD**: assinatura com `status = ATIVA` recusa uma segunda assinatura
  (`ASSINATURA_JA_ATIVA`). `CANCELADA` (card futuro US26/US27) reativaria a mesma linha — ainda não
  acontece hoje, nenhum fluxo cancela.
- **Pagamento aprovado**: grava `AssinaturaGold` (`ATIVA`, ciclo de 1 mês a partir de agora), `Cobranca`
  (`APROVADA`), `DocumentoFiscal` (recibo) e publica `GoldAssinadoEvent` pro e-mail do recibo
  (`AFTER_COMMIT`). `ConsultaPlanoPrestadorService` (porta `ConsultaPlanoPrestador`, ETI-79) passa a
  devolver GOLD pro usuário a partir daqui — é isso que libera o teto de cota em `perfil`.
- **Pagamento recusado**: nada é persistido, nenhum código de erro novo é inventado alem de
  `PAGAMENTO_RECUSADO`, o Prestador permanece FREE.
- **Clique duplo**: trava transacional por usuário (`EscopoTrava.ASSINATURA_GOLD`) serializa as
  chamadas; a segunda só roda depois da primeira comitar e recusa como `ASSINATURA_JA_ATIVA`, sem chamar
  o gateway de novo. `cobranca.chave_idempotencia` (índice único) é a rede de segurança se a trava falhar
  sob corrida real.
- **Gateway**: simulado (`GatewayPagamentoSimulado`, nenhum provedor real escolhido ainda). Tokens
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
