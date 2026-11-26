# Manual de funcionamento — financeiro

> Status: **implementado (ETI-81, ETI-85)**. Código em `modulos/financeiro/`. Especificação em
> `docs/features/ETI-81-assinar-gold.md` e `docs/features/ETI-85-renovar-gold.md`. O usuário logado chega ao controller como
> `UsuarioAutenticado` (`@AuthenticationPrincipal`), lido do token pelo `JwtAuthFilter`.

## `GET /financeiro/gold` (ETI-81 — consultar o plano)

1. `GoldController.consultar` chama `ConsultarGoldUseCase.executar(usuarioId, papelAtivo)`. Papel
   diferente de `PRESTADOR` → 403 `ACESSO_NEGADO`.
2. O usecase busca o `Plano` ativo de código `GOLD` (`PlanoService`) e o `MeioPagamento` do usuário, se
   houver (`MeioPagamentoService`), e devolve os dois num `ConsultaGold` (record de domínio, não HTTP).
3. `GoldController` usa `GoldMapper.toResponse(ConsultaGold)` → `ConsultarGoldResponse` (200).
   `cartaoSalvo` é `null` quando o Prestador não tem cartão salvo ainda.

## `POST /financeiro/gold/assinatura` (ETI-81 — assinar)

1. Controller desserializa em `AssinarGoldRequest` (`codigoPlano`, `gatewayToken`). Bean Validation
   exige os dois não-vazios; falha → 400.
2. `AssinarGoldUseCase.executar(usuarioId, papelAtivo, codigoPlano, gatewayToken)` — a cobrança é efeito
   externo irreversível, então nunca roda dentro de transação; o fluxo é em três passos:
   - papel diferente de `PRESTADOR` → 403 `ACESSO_NEGADO`, sem tocar em nada mais;
   - **passo 1, `reservar` (transação curta):** trava por usuário (`TravaTransacional`,
     `EscopoTrava.ASSINATURA_GOLD`); plano de `codigoPlano` precisa existir e estar ativo, senão 404
     `PLANO_NAO_ENCONTRADO`; já tem `AssinaturaGold` `ATIVA`? 409 `ASSINATURA_JA_ATIVA`; já tem
     `PENDENTE` (cobrança em andamento de uma tentativa anterior)? 409 `COBRANCA_EM_ANDAMENTO`; senão
     cria/reinicia `AssinaturaGold` e `Cobranca`, ambas `PENDENTE`, com uma chave de idempotência nova —
     a transação comita e solta o lock aqui, antes de chamar o gateway;
   - **passo 2, fora de transação:** chama `GatewayPagamento.cobrar(...)` (hoje
     `GatewayPagamentoSimulado`) com a chave de idempotência do passo 1; gateway fora do ar →
     `GatewayIndisponivelException` (503) propagada direto — a pendência fica `PENDENTE` pra reconciliar
     depois pelo aviso do gateway (ETI-85);
   - **passo 3, `confirmar` (segunda transação curta):** recusado → `AssinaturaGold` vira `CANCELADA`,
     `Cobranca` vira `RECUSADA`, lança 402 `PAGAMENTO_RECUSADO`; aprovado → grava/atualiza
     `MeioPagamento` (bandeira/últimos4 vêm do gateway), `AssinaturaGold` vira `ATIVA`, `Cobranca` vira
     `APROVADA`, grava `DocumentoFiscal`, publica `GoldAssinadoEvent`.
3. `ReciboEventosService` (`@TransactionalEventListener(AFTER_COMMIT)`) escuta `GoldAssinadoEvent`,
   busca o e-mail do usuário via `ConsultaDadosUsuario` (porta de `auth`) e manda o recibo
   (`financeiro.actions.service.ReciboGoldEmailService`). Falha de envio é só logada.
4. Controller usa `GoldMapper.toResponse(AssinaturaGold)` → `AssinarGoldResponse` (201).

## `POST /financeiro/gold/cartao` (ETI-81 — tokenizar cartão, só com `financeiro.gateway.provedor=asaas`)

1. `CartaoController` monta `DadosCartao` e `TitularCartao` (tipos de domínio do módulo, não o record
   HTTP) a partir de `TokenizarCartaoRequest`.
2. `TokenizarCartaoUseCase.executar(usuarioId, papelAtivo, cartao, titular, remoteIp)` chama
   `GatewayPagamento.tokenizar(...)` — existe sempre (sem `@ConditionalOnProperty`): o simulado devolve
   um token fake, o Asaas delega pro `AsaasClient` via `ClienteAsaasResolver`. Salva o token em
   `MeioPagamento`. A rota em si segue condicional no controller — é decisão de produto (teste contra o
   sandbox Asaas sem tela de cadastro de cartão), não limitação da porta.

## Rotina de renovação (ETI-85)

1. `RenovarGoldUseCase.executar()` (`@Scheduled`, `financeiro.gold.renovacao.cron`) busca as assinaturas `ATIVA` com
   o ciclo vencido e sem nova tentativa marcada para depois (`AssinaturaGoldService.buscarIdsParaRenovar`).
2. Para cada uma, transação curta sob a trava `ASSINATURA_GOLD`: relê a assinatura; renovação cancelada → `encerrar`
   (FREE), fim. Cobrança anterior ainda `PENDENTE` e recente → espera o aviso do gateway. Senão grava a `Cobranca`
   de renovação `PENDENTE` (`inicioCicloCobrado` = `dataFimCiclo`, `tentativa` = recusadas do ciclo + 1).
3. Fora de transação, cobra o cartão salvo (`GatewayPagamento.cobrar`). Sem cartão = recusada sem chamar o gateway.
4. Segunda transação curta sob a trava: se a cobrança ainda está sem resultado, `ResolverCobrancaGoldHelper` aplica
   aprovação (avança o ciclo, recibo, `CobrancaGoldAprovadaEvent`), recusa (nova tentativa ou `ENCERRADA`,
   `CobrancaGoldRecusadaEvent`) ou falha do gateway (`FALHOU`, nova tentativa).

## `POST /financeiro/gold/webhook/asaas` (ETI-85 — aviso do gateway)

1. `AsaasWebhookController` confere `asaas-access-token` (`WebhookTokenHelper`); errado ou ausente → 401
   `WEBHOOK_INVALIDO`.
2. `AvisoPagamentoMapper.toAviso` traduz o evento do Asaas em `AvisoPagamento` (aprovado/recusado); outros eventos →
   200 sem efeito.
3. `ProcessarAvisoPagamentoUseCase` acha a cobrança pela chave de idempotência (ou pelo id no gateway), trava pelo
   usuário e, se a cobrança ainda está sem resultado, aplica pelo `ResolverCobrancaGoldHelper`. Cobrança já resolvida
   (aviso repetido) → nada. 200.

## Classes envolvidas

| Classe | Papel |
|---|---|
| `GoldController` | Adapter HTTP — `GET`/`POST` de `/financeiro/gold` |
| `ConsultarGoldUseCase` | Regra: ler plano ativo + cartão salvo |
| `AssinarGoldUseCase` | Regra: cobrar, ativar/reativar assinatura, gravar cobrança e recibo |
| `GoldMapper` | `ConsultaGold`/`AssinaturaGold` ⇄ records de resposta |
| `Plano` / `PlanoRepository` / `PlanoService` | Catálogo de planos (hoje só GOLD) |
| `AssinaturaGold` / `AssinaturaGoldRepository` / `AssinaturaGoldService` | Assinatura por usuário, no máximo uma |
| `Cobranca` / `CobrancaRepository` / `CobrancaService` | Subentidade de `AssinaturaGold`: cobrança aprovada |
| `DocumentoFiscal` / `DocumentoFiscalRepository` / `DocumentoFiscalService` | Subentidade de `Cobranca`: recibo |
| `MeioPagamento` / `MeioPagamentoRepository` / `MeioPagamentoService` | Token do gateway por usuário |
| `GatewayPagamento` / `GatewayPagamentoSimulado` / `GatewayPagamentoAsaas` | Porta de cobrança + tokenização, simulado e via Asaas |
| `DadosCartao` / `TitularCartao` | Tipos de domínio pros dados de tokenização, sem Spring, sem ser o record HTTP |
| `ClienteAsaas` / `ClienteAsaasRepository` / `ClienteAsaasService` (`financeiro/clienteasaas/`) | Cache do customer do Asaas por usuário |
| `ClienteAsaasResolver` (`actions/service/asaas/`) | Resolve/cria o customer no Asaas, compartilhado por cobrar e tokenizar |
| `ExigirPrestadorHelper` | Só o Prestador usa o GOLD — checagem repetida pelos três usecases |
| `ConsultaPlanoPrestadorService` | Implementa `comum/portas/ConsultaPlanoPrestador` a partir de `AssinaturaGold` |
| `GoldAssinadoEvent` / `ReciboEventosService` | Evento interno + envio do recibo pós-commit |
| `ReciboGoldEmailService` (financeiro) | Envio do e-mail do recibo via Resend (nome próprio, não o de `auth`) |
| `AsaasWebhookController` / `WebhookTokenHelper` | Recebe eventos do Asaas; token comparado em tempo constante |
| `AvisoPagamentoMapper` / `AvisoPagamento` / `ResultadoAvisoPagamento` | Webhook do Asaas ⇄ aviso de domínio (ETI-85) |
| `ProcessarAvisoPagamentoUseCase` | Regra: aplicar o aviso do gateway a uma cobrança sem resultado, idempotente (ETI-85) |
| `RenovarGoldUseCase` | Rotina: renovar, tentar de novo, encerrar no fim do ciclo (ETI-85) |
| `ResolverCobrancaGoldHelper` | Efeito do resultado de uma cobrança na assinatura, comum à rotina e ao webhook (ETI-85) |
| `CobrancaGoldAprovadaEvent` / `CobrancaGoldRecusadaEvent` (`comum/eventos`) | Pedem os avisos da tela 43 (US56); o aprovado também dispara o recibo |

Atualize esta página sempre que uma classe mudar de responsabilidade.
