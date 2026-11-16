# Histórico — financeiro

Registre aqui, em ordem cronológica, decisões e mudanças de regra que não ficam óbvias só lendo o código.

Formato: uma seção `## AAAA-MM — título` por entrada neste arquivo, com o contexto do "porquê" da mudança.

## 2026-10 — esqueleto do módulo (TICKET-0044)

Primeiro desenho do módulo `financeiro`: só a porta `ConsultaPlanoPrestador` implementada
(`ConsultaPlanoPrestadorService`), sempre devolvendo FREE — `plano`, `assinatura_gold`, `cobranca`,
`meio_pagamento` e `documento_fiscal` ainda não existiam.

## 2026-11 — assinar o plano GOLD (ETI-81)

Implementação completa do módulo: entidades `plano`, `meio_pagamento`, `assinatura_gold`, `cobranca`
(subentidade de `assinatura_gold`) e `documento_fiscal` (subentidade de `cobranca`); `GoldController`
(`GET /financeiro/gold`, `POST /financeiro/gold/assinatura`); gateway de pagamento simulado
(`GatewayPagamentoSimulado` — nenhum provedor real escolhido ainda pelo tech lead).

Decisões:

- **`usuarioId` em vez de `perfilPrestadorId`.** `assinatura_gold` e `meio_pagamento` guardam o id do
  usuário, igual `ConsultaPlanoPrestador.planoAtual(UUID prestadorId)` já fazia — evita `financeiro`
  importar `modulos.perfil` só pra saber o id do perfil (ADR-0001).
- **GOLD é derivado de `assinatura_gold.status = ATIVA`, não um campo em `PerfilPrestador`.** A redação
  original do card ("marca `PerfilPrestador.isGoldAtivo`") preexiste à porta `ConsultaPlanoPrestador`
  (ETI-79): `financeiro` não pode escrever num campo do módulo `perfil` sem evento/import cruzado, e a
  porta já existia pronta pra ler o status direto daqui. `ConsultaPlanoPrestadorService` passou a
  consultar `AssinaturaGoldService` em vez de devolver FREE fixo — nenhuma mudança em `perfil`.
- **Cobrança recusada não persiste nada.** O AC só pede gravar `Cobranca` na aprovação; na recusa o
  Prestador permanece FREE e nada é escrito (nem a tentativa). Evita decidir, sem o PO, o que uma trilha
  de auditoria de recusas precisaria guardar.
- **`chaveIdempotencia` gerada no servidor.** O contrato da API não tem campo de idempotência do
  cliente (entrada é só `codigoPlano` + `gatewayToken`). A proteção real contra clique duplo é a trava
  transacional (`EscopoTrava.ASSINATURA_GOLD`, por usuário) + a unicidade de `assinatura_gold.usuario_id`;
  `chave_idempotencia` (índice único em `cobranca`) é a rede de segurança se a trava falhar sob corrida
  real, mesmo padrão de `uk_aplicacao_par_ativo` em `anuncio`.
- **`bandeira`/`ultimos4` vêm da resposta do gateway, não do request.** Mantém o contrato de entrada
  igual ao da tabela do card (`codigoPlano`, `gatewayToken`); o gateway real devolve esses dados na
  tokenização/cobrança, então `GatewayPagamentoSimulado` já simula isso na resposta.
- **`EmailService` próprio em `financeiro`**, não o de `auth`: módulo não importa classe de outro módulo
  (ADR-0001). Bean renomeado (`@Component("financeiroEmailService")`) porque o nome padrão colidia com o
  de `auth` no contexto Spring.
- **`StatusAssinatura.CANCELADA`** existe no enum mas nenhum fluxo o produz ainda — nasce com o card
  futuro "Cancelar assinatura GOLD" (US26/US27). `AssinarGoldUseCase` já sabe reativar a mesma linha
  nesse estado em vez de criar outra, pra não precisar de migração de dado quando o cancelamento chegar.

## 2026-11 — ETI-81: correções de review (PR #36)

Achados de code review no próprio PR, corrigidos antes do merge:

- **[Bloqueante] cobrança fora da transação.** A versão anterior cobrava dentro da `@Transactional` do
  `AssinarGoldUseCase`, segurando o advisory lock e uma conexão do pool durante a chamada HTTP (timeout
  de 10s) — uma falha depois da cobrança aprovada (gravar `AssinaturaGold`/`Cobranca`/`DocumentoFiscal`)
  desfazia a transação e deixava o cartão cobrado sem registro. Agora o fluxo é em três passos, no mesmo
  espírito do `PublicarAnuncioUseCase`: (1) `reservar` — transação curta que trava
  `EscopoTrava.ASSINATURA_GOLD`, valida e grava `AssinaturaGold` + `Cobranca` ambas `PENDENTE`; (2)
  cobra no gateway, fora de transação; (3) `confirmar` — segunda transação curta que ativa/grava o recibo
  (aprovado) ou cancela a pendência (recusado). `StatusAssinatura` ganhou `PENDENTE` e `StatusCobranca`
  ganhou `PENDENTE`, sem migração nova (coluna já era `VARCHAR`, nenhum valor passou do tamanho
  reservado). Consequência: **cobrança recusada agora persiste** `AssinaturaGold` `CANCELADA` e
  `Cobranca` `RECUSADA` (ao contrário da decisão original acima) — é o que sustenta a trava contra
  clique duplo depois que o lock solta no fim do passo 1.
- **Clique duplo / retry concorrente.** Como o índice único em `usuario_id` é só uma linha por usuário, a
  segunda tentativa que chega depois do passo 1 da primeira (lock já solto) encontra a linha `PENDENTE` e
  é recusada por `CobrancaEmAndamentoException` (409 `COBRANCA_EM_ANDAMENTO`), sem chamar o gateway de
  novo — não precisou mudar a chave de idempotência em si (ela já nasce só depois dessa checagem passar,
  então nunca se repete de fato entre duas tentativas que cheguem a chamar o gateway).
- **Pendência conhecida:** uma falha entre o passo 2 (cobrança) e o passo 3 (confirmação) deixa a linha
  `PENDENTE` — sem reconciliação automática ainda (o webhook do Asaas só loga). Novas tentativas do
  mesmo usuário recebem `COBRANCA_EM_ANDAMENTO` até alguém resolver manualmente.
- **[Bloqueante] `ClienteAsaas`/`ClienteAsaasRepository`/`ClienteAsaasService` saíram de
  `actions/service/asaas/` pra `financeiro/clienteasaas/`** — `actions/service/` é só integração externa
  (ADR-0001); entidade e repository ficam na raiz do módulo ou em subentidade. `ClienteAsaasResolver`
  (integração de fato: fala com o Asaas) continua em `actions/service/asaas/`. Efeito colateral bom: como
  a cobrança não roda mais dentro de transação, o cache do customer (criado antes da cobrança, dentro da
  chamada ao gateway) deixou de ser desfeito numa recusa — não nasce mais customer órfão no Asaas a cada
  tentativa recusada.
- **[Bloqueante] `TokenizarCartaoUseCase` não depende mais do `AsaasClient`.** Ganhou `tokenizar(...)` na
  porta `GatewayPagamento` (`GatewayPagamentoSimulado` devolve um token fake, `GatewayPagamentoAsaas`
  delega pro `AsaasClient`); o usecase passou a depender só da porta e perdeu o `@ConditionalOnProperty`
  (existe sempre, como qualquer outro usecase). `CartaoController` manteve o dele — é decisão de produto
  (rota de teste pro sandbox Asaas, card ainda sem ticket), não limitação técnica.
- **`executar` do `TokenizarCartaoUseCase`** tinha 14 parâmetros, a maioria `String`, com `holderName`
  (nome impresso no cartão) e `holderNome` (nome do titular) lado a lado. Agora recebe `DadosCartao` e
  `TitularCartao` (`actions/service/`, sem Spring, sem ser o record HTTP).
- **`Plano.fimDoCiclo(inicio)`** substitui o `switch` sobre `CicloPlano` que estava no usecase — regra do
  próprio plano, não de quem assina.
- **`exigirPrestador`** estava repetido em `AssinarGoldUseCase`, `ConsultarGoldUseCase` e
  `TokenizarCartaoUseCase` — extraído pra `ExigirPrestadorHelper` (`actions/helper/`).
- **`AsaasWebhookController`**: a comparação do token do header virou `MessageDigest.isEqual` via
  `WebhookTokenHelper` (tempo constante, recomendado pra segredo) em vez de `String.equals`; o payload
  (`AsaasWebhookPayload` → `AsaasWebhookRequest`) saiu do controller pra `records/`, padrão do projeto.
- **`EmailService` renomeado pra `ReciboGoldEmailService`** — nome específico em vez do qualificador
  `@Component("financeiroEmailService")` pra evitar colisão com o bean de `auth`.
