# TICKET-0044 — Separar subentidades acopladas em `auth` por bounded context

## Link do ticket

`feature/44`

## Contexto

Revisão de arquitetura (DER + diagrama de classes atualizados) mostrou que, se todas as entidades que hoje
referenciam `Usuario` fossem implementadas dentro de `modulos/auth`, o módulo viraria um monólito — ferindo o
próprio princípio do ADR-0001 ("todo domínio vive em `modulos/`, auth é só mais um bounded context"). O DER tem
~35 tabelas; só `usuario` e `cpf_bloqueado` existem em código hoje. As demais nunca deveriam nascer dentro de
`auth` só porque têm FK pra `usuario_id`.

## Regra de negócio implementada

Nenhuma regra de negócio nova. Esta ticket é puramente estrutural:

1. Corrige `CpfBloqueado` pra bater com o DER atual: `cpf_hmac` (hash, não CPF em texto puro — exigência de
   privacidade/LGPD que o DER já previa e o código ainda não implementava) + `bloqueado_em` + `liberado_em`
   (hoje só existia `criado_em` herdado, sem suportar liberação antecipada do cooldown).
2. Adiciona `anonimizado_em` em `Usuario` (coluna do DER ausente no código).
3. Cria o pacote-esqueleto (`package-info.java`, mesmo padrão que `auth` teve antes da revisão 2 do ADR-0001)
   de cada bounded context que o DER revela e que hoje não tem lar nenhum: `perfil`, `anuncio`, `moderacao`,
   `financeiro`, `chat`, `notificacao`, `administracao`, `auditoria`, `privacidade`. Implementação de entidade/
   usecase de cada um fica para ticket futuro, um módulo por vez — mesma estratégia incremental que o próprio
   ADR recomenda na seção "Negativas/custos".
4. Atualiza ADR-0001 (revisão 3): documenta a decisão de separação, anexa o novo diagrama de classes e DER, e
   lista o mapeamento tabela → módulo.

## Decisão técnica e alternativas descartadas

- **Hash do CPF (`cpf_hmac`) em vez de CPF em texto puro em `cpf_bloqueado`**: a tabela existe só pra checar
  "esse CPF foi excluído há menos de 12 meses" — não precisa do valor legível, só precisa comparar igualdade.
  HMAC-SHA256 com chave de servidor (`security.cpf.hmac-secret`, nunca versionada) evita guardar CPF em texto
  puro numa tabela cujo único propósito é bloqueio, reduzindo superfície de dado sensível. Alternativa
  descartada: manter texto puro como estava — rejeitada porque o próprio DER já sinalizava a mudança.
- **Módulos novos nascem só com `package-info.java`**: mesma estratégia que `auth` usou antes de ser
  implementado (ver `docs/adr/0001-...md`, "O backend já tinha um esqueleto..."). Evita criar
  `controller`/`usecase`/`mapper` vazios pra entidade que ainda não tem usecase definido — preenche a pasta só
  quando a feature daquele módulo for de fato especificada.
- **`sessao`, `verificacao_contato`, `aceite_termo` continuam em `auth`**: são sobre o ciclo de vida da própria
  conta (login, confirmação de contato, aceite de termo no cadastro) — não têm dono natural fora de `auth`.
- **`bloqueio_usuario` vai para `moderacao`, não fica em `auth`**: apesar de FK direto pra `usuario`
  (`bloqueador_id`/`bloqueado_id`), é bloqueio social entre usuários (ex.: silenciar contato), não suspensão de
  conta — não é regra de autenticação.
- **Pendência não resolvida nesta ticket**: `PapelUsuario.ADMIN` (enum em `Usuario`) e a tabela `administrador`
  (login próprio: nome/email/senha_hash) coexistem no DER sem explicar a relação entre os dois. Não alteramos o
  enum aqui — fica registrado em `docs/history/` e na ADR como decisão em aberto.
