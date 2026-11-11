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
2. `AssinarGoldUseCase.executar(usuarioId, papelAtivo, codigoPlano, gatewayToken)`, em ordem:
   - papel diferente de `PRESTADOR` → 403 `ACESSO_NEGADO`, sem tocar em nada mais;
   - trava transacional por usuário (`TravaTransacional`, `EscopoTrava.ASSINATURA_GOLD`) — serializa
     clique duplo;
   - plano de `codigoPlano` precisa existir e estar ativo, senão 404 `PLANO_NAO_ENCONTRADO`;
   - já tem `AssinaturaGold` com `status = ATIVA`? 409 `ASSINATURA_JA_ATIVA`;
   - chama `GatewayPagamento.cobrar(...)` (hoje `GatewayPagamentoSimulado`) com uma chave de
     idempotência gerada aqui;
   - gateway fora do ar → `GatewayIndisponivelException` (503), propagada da própria chamada;
   - recusado → 402 `PAGAMENTO_RECUSADO`, nada é persistido;
   - aprovado → grava/atualiza `MeioPagamento` (bandeira/últimos4 vêm do gateway), cria ou reativa
     `AssinaturaGold` (`ATIVA`, ciclo de 1 mês a partir de agora), grava `Cobranca` (`APROVADA`) e
     `DocumentoFiscal`, publica `GoldAssinadoEvent`.
3. `ReciboEventosService` (`@TransactionalEventListener(AFTER_COMMIT)`) escuta `GoldAssinadoEvent`,
   busca o e-mail do usuário via `ConsultaDadosUsuario` (porta de `auth`) e manda o recibo
   (`financeiro.actions.service.EmailService`). Falha de envio é só logada.
4. Controller usa `GoldMapper.toResponse(AssinaturaGold)` → `AssinarGoldResponse` (201).

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
| `GatewayPagamento` / `GatewayPagamentoSimulado` | Porta de cobrança + simulação (nenhum provedor real ainda) |
| `ConsultaPlanoPrestadorService` | Implementa `comum/portas/ConsultaPlanoPrestador` a partir de `AssinaturaGold` |
| `GoldAssinadoEvent` / `ReciboEventosService` | Evento interno + envio do recibo pós-commit |
| `EmailService` (financeiro) | Envio do e-mail do recibo via Resend (bean próprio, não o de `auth`) |

Atualize esta página sempre que uma classe mudar de responsabilidade.
