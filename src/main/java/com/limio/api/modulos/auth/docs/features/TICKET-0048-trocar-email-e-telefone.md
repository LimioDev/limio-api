# TICKET-0048 — Consultar e editar o perfil de Empregador

## Link do card

ETI-48 ([empregador] [BACK-END] Consultar e editar o perfil de Empregador) — este documento cobre só a troca de
contato, que é operação do `auth` (dono de `Usuario`). O restante do card está em
`modulos/perfil/docs/features/TICKET-0048-consultar-e-editar-perfil-empregador.md`.

## Status

Implementado: `PATCH /auth/email` e `PATCH /auth/telefone`.

**Não implementado, e o que isso significa:**

- **Confirmação de e-mail (UC05) NÃO está implementada.** A troca de e-mail emite um link, mas **não existe endpoint
  que o consuma**: o link enviado **não permite concluir a confirmação** hoje, e `emailConfirmadoEm` continua `null`
  até a UC05 existir. Gerar e enviar o token não é confirmar.
- **SMS/OTP (UC04) NÃO existe e NADA é simulado.** A troca de telefone grava o número e limpa `telefoneConfirmadoEm`;
  não envia código, não cria `VerificacaoContato`, não há `SmsService` nem stub. O telefone fica "não confirmado" até
  existir esse fluxo.
- Bloqueio de anúncio/candidatura enquanto o contato não está confirmado: depende do módulo `anuncio` (inexistente);
  aqui só se garante o dado (`*ConfirmadoEm = null`).

## Contrato da API

| Operação | Rota | Autenticação | Entrada | Saída |
|---|---|---|---|---|
| Trocar e-mail | `PATCH /auth/email` | `Bearer <token>` | `{ novoEmail, senhaAtual }` | 200 `{ email, telefone, emailConfirmadoEm, telefoneConfirmadoEm }` |
| Trocar telefone | `PATCH /auth/telefone` | `Bearer <token>` | `{ novoTelefone, senhaAtual }` | 200 `{ email, telefone, emailConfirmadoEm, telefoneConfirmadoEm }` |

`senhaAtual` é exigida nas duas (decisão D1): o e-mail é o canal de recuperação de senha, então token de acesso
roubado + troca de e-mail seria tomada de conta. Mesmo padrão de `PATCH /auth/senha`.

`CodigoErro`: `VALIDACAO_FALHOU` (400), `NAO_AUTENTICADO` (401), `SESSAO_INVALIDA` (401, conta encerrada),
`CONTA_SUSPENSA` (403), `SENHA_ATUAL_INCORRETA` (401), **novos** `CONTATO_NAO_PERMITIDO` (409 — e-mail de outra conta,
mensagem sempre genérica) e `TROCA_CONTATO_LIMITE_EXCEDIDO` (429).

## Regra de negócio

### Trocar e-mail

- E-mail normalizado (`trim` + minúsculo), formato válido, até 255 caracteres.
- **Outra conta já usa o e-mail** (comparação sem diferenciar caixa) → 409 `CONTATO_NAO_PERMITIDO`, sem efeito algum.
- Grava o novo e-mail, zera `emailConfirmadoEm`, **invalida** os tokens `CONFIRMACAO_EMAIL` pendentes, cria um token novo
  e envia o link `app.frontend.url + /confirmar-email?token=...` **depois do commit**.
- **E-mail igual ao atual** (decisão D8): se ainda **não confirmado**, **reemite** o link (conta no limite); se já
  **confirmado**, não faz nada.
- **Limite**: 3 emissões por hora por conta (`auth.confirmacao-email.*`), valendo para troca e reemissão; excesso → 429.
  Token vale 24 h.
- **Aviso ao e-mail antigo**: numa troca real (não na reemissão), o endereço que deixou de ser o da conta recebe, depois
  do commit e em envio separado do link, um aviso com o endereço novo (`EmailService.enviarEmailAlterado`). Sem link de
  recuperação de senha: ela sai para o e-mail atual da conta, que já é o novo. Falha de envio só é logada.
- `statusConta` não muda; outras sessões não são revogadas.

### Trocar telefone

- Regra do card: celular com DDD (11–99) + 9 dígitos começando por 9. **Entrada** tolerante: aceita máscara e prefixo
  `55`/`+55`. Fixo, DDD inválido e tamanho errado → 400.
- **Formato**: o **banco guarda só os dígitos nacionais** (`11999998888`, `VARCHAR(11)`, o mesmo do cadastro); o `+55`
  existe apenas no **contrato da API** e nas integrações. As respostas (`PATCH /auth/email`, `PATCH /auth/telefone`) e
  a porta `ConsultaDadosUsuario` (logo, o `GET /conta/perfil-empregador`) devolvem `+5511999998888`. A conversão mora em
  um único lugar: `TelefoneHelper.normalizar` (entrada → dígitos) e `TelefoneHelper.paraInternacional` (saída e futuras
  integrações, como o gateway de SMS). Não há coexistência de formatos nem migration.
- Linhas legadas de 10 dígitos (fixo, aceitas pelo cadastro) continuam como estão e saem como `+55` + 10 dígitos; podem
  ser substituídas por um celular válido, mas seguem sem OTP possível. A decisão do PO sobre o cadastro recusar fixo
  continua pendente e não bloqueia esta feature.
- Número igual ao atual → não faz nada (não há código a reenviar); como o formato é o mesmo do banco, a comparação vale
  também para números cadastrados antes. Não toca `emailConfirmadoEm`.

## Decisão técnica

- **Lock da conta**: ambos os usecases começam com `SELECT ... FOR UPDATE` na linha do usuário
  (`UsuarioService.buscarParaAtualizarOuFalhar`). Requisições simultâneas da mesma conta se enfileiram; no isolamento
  padrão (READ COMMITTED) a contagem do limite de 3/h e a criação do token ocorrem em seção crítica por conta (testado
  com 6 requisições simultâneas: exatamente 3 × 200 e 3 × 429; sem o lock o teste falha com 6 emissões). Só protege quem
  usa o mesmo finder — **qualquer futuro emissor ou consumidor de `CONFIRMACAO_EMAIL` deve usá-lo**. Não há timeout de
  lock efetivo no dialeto PostgreSQL; as transações são curtas porque o envio de e-mail acontece fora delas.
- **Unicidade do e-mail sob concorrência**: a pré-checagem não basta; o backstop é `uk_usuario_email`.
  `UsuarioService.salvarComEmailUnico` faz `saveAndFlush` e traduz a violação dessa constraint em
  `ContatoNaoPermitidoException` (outra violação é relançada). O flush é a primeira escrita; a transação inteira sofre
  rollback (nenhum token, nenhum e-mail). Nada roda depois do erro (o Postgres aborta a transação).
- **E-mail só depois do commit**: o usecase publica `ConfirmacaoEmailSolicitadaEvent` e `EmailContaAlteradoEvent`
  (`auth/eventos/`) e `EmailEventosService` os trata com `@TransactionalEventListener(AFTER_COMMIT)`; se o commit não
  acontece, o e-mail não sai, e um link nunca aponta para um token não persistido. Sem transação ativa o evento é
  descartado (não há `fallbackExecution`), nunca envia antes de persistir. Exceção no envio é logada, não propaga, e um
  envio não derruba o outro.
  Residual: commit ok e envio falhando deixa o token gravado e o e-mail perdido (mitigado pela reemissão D8).
- **Por que o `perfil` não participa**: a troca é do `auth`; o `GET /conta/perfil-empregador` já reflete o novo contato
  pela porta `ConsultaDadosUsuario` (testado). Alternativa descartada: rotas sob `/conta/perfil-empregador` com porta de
  comando em `comum/` (transformaria o `perfil` em repasse).

## Contrato para a futura UC05 (não implementada)

O token foi desenhado para ser consumível por uma UC05: só o SHA-256 (64 caracteres) é gravado, a finalidade
`CONFIRMACAO_EMAIL` é filtrada por `buscarPorTokenHashEFinalidade` (token de recuperação de senha não confirma e-mail e
vice-versa; testado), há `expiraEm` (`isValido`) e `consumidoEm` (`consumir`). Um token antigo não deve confirmar um
endereço novo; hoje isso é garantido **só pela invalidação dos pendentes a cada troca ou reemissão**. A UC05 precisa:

1. carregar o usuário com `UsuarioService.buscarParaAtualizarOuFalhar` e **revalidar o token depois do lock** (fecha a
   corrida entre o consumidor e uma troca concorrente);
2. recusar conta encerrada;
3. consumir o token de forma atômica sob esse lock (o `RedefinirSenhaUseCase` não faz isso hoje);
4. **se a D10 for aprovada**, recusar token cujo `destino_hash` difira do hash do e-mail atual.

## Migration

Nenhuma. A coluna `usuario.telefone` continua `VARCHAR(11)`. (A Parte 3 chegou a ampliá-la para `VARCHAR(14)` numa migration
provisória, removida antes de qualquer commit quando se decidiu guardar só os dígitos nacionais.) Ficam pendentes apenas a
numeração da `V9` do perfil e, se a D10 for aprovada, a migration do `destino_hash`, ambas dependentes da ordem de merge
(`feature/34` e `feature/36` também usam `V8`).

## Pendências

- **D10 (aprovação pendente)**: gravar `destino_hash` (SHA-256 do e-mail-alvo) no token, para ele só valer para o
  endereço emitido, inclusive contra emissores futuros e a corrida com o consumidor. Exigiria coluna anulável em
  `verificacao_contato` e uma segunda migration provisória; **não implementado**. Guardar o e-mail em claro foi evitado de
  propósito: o encerramento de conta (`feature/36`) anonimiza `usuario.email` e não limparia essa cópia.
- **E-mail pendente promovido na confirmação (review)**: hoje o e-mail de login troca na hora; um typo (`@gmial.com`) deixa
  a conta presa, pois a recuperação de senha vai para a caixa errada. O aviso ao e-mail antigo cobre o takeover, não o typo.
  Resolver exige guardar o novo endereço como pendente e promovê-lo na UC05 (coluna/migration nova); não implementado.
- UC05 (endpoint de consumo do link), UC04 (OTP/SMS e gateway), regra de telefone do UC01 (PO: recusar fixo?), ordem de merge e
  numeração das migrations (`V9`, e a da D10 se aprovada), `app.frontend.url` vs `app.deeplink.url` (`feature/36`; o link sai de um único método).
- Riscos aceitos: recusa por e-mail em uso tem status diferente do sucesso e não consome limite (igual ao cadastro); o
  cadastro não trata a violação de `uk_usuario_email` (500 pré-existente).

## Fora de escopo

UC05, UC04, qualquer envio ou simulação de SMS, alteração do cadastro (UC01), normalização de telefones legados,
bloqueio por contato não confirmado (`anuncio`), revogação de outras sessões.

## Nomenclatura

"empregador", nunca "contratante" nem "solicitante".
