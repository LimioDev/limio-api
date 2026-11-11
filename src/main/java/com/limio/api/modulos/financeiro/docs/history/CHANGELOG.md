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
