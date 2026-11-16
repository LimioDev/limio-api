# Manual de funcionamento — financeiro

> Status: **implementado (ETI-81)**. Código em `modulos/financeiro/`. Especificação em
> `docs/features/ETI-81-assinar-gold.md`. O usuário logado chega ao controller como
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
     depois (webhook só loga hoje, sem reconciliação automática);
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

Atualize esta página sempre que uma classe mudar de responsabilidade.
