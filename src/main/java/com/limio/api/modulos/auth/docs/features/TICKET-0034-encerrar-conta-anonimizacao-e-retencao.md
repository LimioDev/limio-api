# TICKET-0034 — Excluir conta, anonimização e regras de retenção (LGPD)

## Link do card

ETI-36 ([IAM] Excluir conta, anonimização e regras de retenção). UC10.

Card par: [conta] [FRONT-END] Tela "Encerrar conta".
Relacionado: [anuncio] [BACK-END] Encerrar anúncios, candidaturas e combinados de conta encerrada.

## Status

Implementado. A reação de `auth` veio aqui; as de `anuncio` (TICKET-0049), `perfil` e `notificacao` (TICKET-0047)
vieram depois, cada uma num listener próprio de `ContaEncerradaEvent`.

## Regra de negócio implementada

- **`POST /auth/encerrar-conta`** (autenticada): `{ confirmacao: true }` obrigatório
  (`@AssertTrue`, sem isso nem chega no usecase — 400 `VALIDACAO_FALHOU`).
- **`EncerrarContaUseCase`** (UC10), tudo numa transação:
  1. Bloqueia o CPF original em `CpfBloqueado` (`bloqueadoEm=agora`, `liberadoEm=agora+12 meses`) —
     **antes** de anonimizar, porque `Usuario.encerrar()` apaga o campo.
  2. `Usuario.encerrar(agora)`: apaga `cpf`/`dataNascimento`/`cidadeUf`; anonimiza `nomeCompleto`
     (`"Conta encerrada"`), `email` (`encerrada+<uuid>@anonimizado.limio` — libera o original pra um
     cadastro novo) e `telefone`; marca `anonimizadoEm`. Não mexe em `senhaHash` (login já recusa
     antes de olhar a senha) nem em `papelAtivo`/`statusConta` (encerrada = só `anonimizadoEm`
     preenchido, igual já valia desde o TICKET-0032/`isEncerrada()`).
  3. `SessaoService.revogarTodas(usuarioId, null, agora)` — todas, inclusive a atual (sem "aparelho a
     manter" aqui, diferente do UC07/alterar-senha).
  4. Grava uma linha em `LogAuditoria` (`entidade="Usuario"`, `tipoAcao=CONTA_ENCERRADA`, `ip` de quem
     chamou).
  5. Publica `ContaEncerradaEvent(usuarioId, agora)` via `ApplicationEventPublisher`.
- **Idempotente**: conta já encerrada (`isEncerrada()`) → não faz nada de novo (sem duplicar
  `CpfBloqueado` nem reabrir sessão numa repetição de requisição) — mesmo espírito do logout.
- **Login/sessão depois do encerramento**: já cobertos por código existente — `CREDENCIAIS_INVALIDAS`
  no login (`AutenticarUsuarioUseCase` trata `isEncerrada()` igual e-mail inexistente) e
  `SESSAO_INVALIDA` na renovação de outro aparelho (`Usuario.exigirAtiva()`).

### Rotina diária de limpeza (`LimparRetencaoUseCase`, `@Scheduled`)

`auth.retencao.cron` (default `0 0 3 * * *`, 3h da manhã):

- `CpfBloqueado` com `liberadoEm` no passado → apaga.
- `LogAuditoria` com mais de `auth.retencao.log-auditoria.validade` (default 5 anos) → apaga.
- `Sessao` encerrada (`revogadaEm`) ou expirada (`expiraEm`) há mais de
  `auth.retencao.sessao.validade` (default 6 meses, Marco Civil da Internet art. 15 — ver "Decisão
  técnica") → apaga.

## Decisão técnica

- **Regra do bloqueio de CPF corrigida pra bater com o card.** O código de antes (UC01/TICKET-0044)
  checava `bloqueadoEm > (agora - 12 meses) AND liberadoEm IS NULL` — janela calculada no momento da
  checagem, com `liberadoEm` só usado pra liberação antecipada administrativa. O card descreve algo
  mais simples: "recusa quando acha um registro com `liberadoEm` ainda no futuro". Como nenhum código
  anterior de fato *criava* uma linha em `CpfBloqueado` (só checava — o encerramento de conta não
  existia até este ticket), não havia dado em produção pra migrar: troquei a regra pra bater com o
  card. `CpfBloqueadoService.bloquear()` agora sempre grava `liberadoEm = bloqueadoEm + 12 meses`, e
  `estaBloqueado()` só olha `liberadoEm > agora`. Liberação antecipada continua possível — só
  atualizar `liberadoEm` pra uma data no passado — sem precisar de um "nulo" especial.
  `CadastrarUsuarioUseCase` simplificado (perdeu o cálculo de janela, que virou desnecessário).
- **"Registros de acesso" (Marco Civil, 6 meses) = `Sessao`, não uma tabela nova.** O card separa
  "Auditoria" (`LogAuditoria`, 5 anos) de "Registros de acesso" (6 meses) na retenção, mas só cita uma
  tabela nova (`log_auditoria`) no Flyway. `Sessao` já guarda IP + timestamps por login/conexão — é
  exatamente o que o Marco Civil da Internet art. 15 pede retido. A limpeza de "sessões encerradas ou
  expiradas há mais de 6 meses" (item explícito do card) é essa categoria aplicada à tabela que já
  existe, não uma tabela nova.
- **`LogAuditoria` fica em `modulos/auth/auditoria/`, não em `comum/`.** Só `auth` escreve nela hoje —
  critério do ADR-0001 ("usado por 2+ módulos hoje", não "pode vir a ser usado") é explícito sobre
  isso. Sobe pra `comum/` quando um segundo módulo precisar (ex.: moderação, denúncia — o enum
  `TipoAcaoAuditoria` do diagrama já reserva espaço pra isso, mas só ganha os valores que alguém de
  fato grava).
- **`comum/eventos/` é pasta nova** — ADR-0001 não previa essa subpasta em `comum/`. Um evento de
  domínio entre módulos só funciona se qualquer módulo que um dia precisar escutá-lo conseguir
  importar o tipo — isso não é "lógica usada por 2+ módulos hoje" (critério de helper), é a definição
  do próprio contrato. `ContaEncerradaEvent` fica lá; `auth` publica, ninguém escuta ainda (ver "Fora
  de escopo").
- **`EncerrarContaUseCase` não é um listener do próprio evento.** Ele publica `ContaEncerradaEvent`
  *depois* de fazer a própria anonimização direto — publicar e escutar o próprio evento só pra rodar
  código síncrono no mesmo módulo seria indireção sem ganho.
- **Rota `POST`, não `DELETE`.** Really delete-like semanticamente, mas os outros endpoints de ação do
  módulo (`/auth/login`, `/auth/refresh`, `/auth/recuperar-senha`, ...) são todos `POST`; `DELETE` com
  corpo JSON (precisa da confirmação) é tecnicamente válido mas incomum e alguns clientes/proxies
  tratam mal. Fiquei com `POST /auth/encerrar-conta` por consistência com o resto do controller.
- **`usuario.cpf`/`dataNascimento`/`cidadeUf` viraram nullable** (migration V8) — eram `NOT NULL` desde
  a V1 e o card pede *apagar* (não só mascarar). `uk_usuario_cpf` continua valendo: Postgres permite
  múltiplos `NULL` numa `UNIQUE`.
- **E-mail/telefone "livres pra cadastro novo"**: não dava pra só mascarar meio e-mail, porque
  `uk_usuario_email` é `UNIQUE` e o valor original precisa ficar livre pra sempre (não só até a
  próxima colisão). Troquei pelo valor derivado do próprio UUID (`encerrada+<id>@anonimizado.limio`),
  único por construção, sem precisar de token aleatório nem checagem extra de unicidade.
- **`entidadeId` em `LogAuditoria` não é tratado como dado pessoal** — guarda o UUID do `Usuario`
  (necessário pra a entrada servir de algo), mas depois do encerramento essa linha não identifica mais
  ninguém, já que o `Usuario` associado está anonimizado. É assim que a frase do card "sem identificar
  o usuário quando possível" foi interpretada: nenhum campo de `LogAuditoria` recebe nome/e-mail/CPF.

## Fora de escopo (não implementado aqui)

> **Atualização (TICKET-0047):** o reator de `perfil` agora existe —
> `perfil/actions/usecase/AnonimizarPerfilDaContaEncerradaUseCase` anonimiza o nome de exibição do
> `PerfilEmpregador`, apaga o `PerfilPrestador` (localização, raio, especialidades e janelas) e expurga a foto
> de perfil, apagando o objeto do storage depois do commit. `notificacao` também ganhou o seu
> (`RemoverPreferenciaDaContaEncerradaUseCase`). `AceiteTermo` continua sem existir e, como explicado abaixo, não
> precisa de reator. O texto abaixo registra o motivo de, na época, não terem entrado neste card.

Os reatores de `perfil` e "Consentimentos" ao `ContaEncerradaEvent` **não foram implementados** —
decisão consciente, não esquecimento:

- `PerfilEmpregador` (`PerfilContratante` no diagrama é o nome velho — regra de nomenclatura em
  `perfil/package-info.java`: sempre "empregador", nunca "contratante"), `PerfilPrestador`,
  `EnderecoSalvo` e `Midia` (card: anonimizar nome de exibição,
  apagar localização do prestador, apagar mídia do storage) não existem em código — `perfil` continua
  só o esqueleto do TICKET-0044. Construir essas entidades do zero só como efeito colateral deste card
  fugiria do padrão "um ticket de cada vez" que o resto do projeto segue (ver
  `TICKET-0044-separacao-modulos-por-subentidade.md`), e inventaria requisitos (quais campos são
  obrigatórios, como o upload de `Midia` funciona) que não são deste card.
- "Consentimentos" também não existe em código — é `AceiteTermo` no diagrama (ver "Decisão técnica"
  abaixo). O ADR-0001 já reservava `aceite_termo` pra um ticket futuro de `auth` (junto com `sessao`,
  `verificacao_contato` — os outros dois já vieram nos TICKET-0032/0033).
- Quando `perfil` for implementado, o listener dele importa
  `com.limio.api.comum.eventos.ContaEncerradaEvent` e reage via `@EventListener` (ou
  `@TransactionalEventListener(phase = AFTER_COMMIT)`, se o trabalho não puder arriscar rollback junto
  com a transação de `auth`) — zero mudança em `auth` quando isso acontecer. `AceiteTermo` **não**
  precisa de listener nenhum — ver decisão abaixo.

### "Consentimentos" = `AceiteTermo`, e "encerra os ativos" não exige nenhum código

Resolvido (decisão de tech lead, sem precisar voltar pro grupo): "Consentimentos" do card é a
entidade `AceiteTermo` do diagrama (`documento`, `versao`, `origem`, `ip`, `aceitoEm`, FK pra
`Usuario`). Ela não tem campo de estado ativo/revogado porque não precisa — cada linha já é um fato
imutável ("este usuário aceitou esta versão deste documento neste instante"); o consentimento
"vigente" pra qualquer documento é implicitamente a linha mais recente por `(usuario, documento)`, sem
precisar de flag.

Por isso, "encerra os ativos" no contexto do UC10 não corresponde a nenhuma mutação em `AceiteTermo`:
não existe um booleano "ativo" pra desligar, e já não existe mais usuário ativo pra aquele
consentimento valer depois do encerramento (`Usuario.anonimizadoEm` cobre isso). "Guarda o histórico
como registro legal" é automático — a tabela nunca tem `UPDATE`/`DELETE` por conta do encerramento de
conta, só os `INSERT`s que já existiam. **Consequência prática**: quando `AceiteTermo` for
implementado (ticket próprio de `auth`, como `sessao`/`verificacao_contato` foram), ele não precisa de
listener de `ContaEncerradaEvent` — não há nada pra fazer ali quando uma conta encerra.

## Banco (Flyway)

`V8__preparar_usuario_para_anonimizacao_e_log_auditoria.sql`: `usuario.cpf`/`data_nascimento`/
`cidade_uf` viram nullable; cria `log_auditoria`. `cpf_bloqueado` já existia (TICKET-0044/UC01) com os
campos certos (`cpf_hmac`, `bloqueado_em`, `liberado_em`) — nenhuma migration nova pra ela, só mudança
de como o código usa `liberado_em` (ver "Decisão técnica").

## Contrato da API

| Operação | Rota | Autenticação | Entrada | Saída |
|---|---|---|---|---|
| Encerrar conta | `POST /auth/encerrar-conta` | `Bearer <token>` | `{ confirmacao: true }` | 200, sem corpo |

Nenhum `CodigoErro` novo — confirmação ausente/falsa cai em `VALIDACAO_FALHOU` (Bean Validation
`@AssertTrue`), igual qualquer outro campo obrigatório do módulo.

## Testes

`spec/`: `EncerrarContaUseCaseTest` (bloqueio antes da anonimização, idempotência, evento publicado),
`CpfBloqueadoServiceTest` (regra nova do cooldown), `LimparRetencaoUseCaseTest` (cada tabela limpa com
a validade certa). `story/` (`EncerrarContaStoryTest`): fluxo completo end-to-end, login depois de
encerrar (`CREDENCIAIS_INVALIDAS`), outro aparelho depois de encerrar (`SESSAO_INVALIDA` no refresh),
sem confirmação (400), sem token (401), repetição idempotente, recadastro com o mesmo CPF dentro de 12
meses (recusado), e a rotina de limpeza rodada diretamente (sem esperar o cron) apagando só o que
estourou a retenção.
