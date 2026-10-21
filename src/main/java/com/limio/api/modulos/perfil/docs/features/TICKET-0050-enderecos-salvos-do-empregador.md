# TICKET-0050 — Cadastrar, editar, remover e marcar endereço padrão (Empregador)

## Link do card

ETI-50 ([empregador] [BACK-END] Cadastrar, editar, remover e marcar endereço padrão).

Relacionados: TICKET-0048 (perfil do Empregador — o `GET` prevê `enderecos.quantidade` e `enderecos.apelidoPadrao`),
TICKET-0034 (encerrar conta e anonimização), módulo `anuncio` (ainda inexistente).

## Status

Implementado (listar, criar, editar, remover e marcar padrão) na branch `feature/50`, com specs e stories.
As decisões marcadas **Proposto** abaixo foram implementadas como propostas e seguem aguardando confirmação do
tech lead (ver "Pontos para o tech lead"). Migration: `V14__criar_tabela_endereco_salvo.sql` — o número pode
mudar no merge.

## User Story

Como um Empregador, eu quero que a API guarde meus endereços e saiba qual é o padrão, para que eu crie anúncios
mais rápido.

## Critérios de aceitação (Definido)

### Campos de `EnderecoSalvo`

`apelido`, `cep`, `rua`, `numero`, `complemento`, `bairroRegiao`, `cidade`, `uf`, `latitude`, `longitude`, `isPadrao`.

### Validações

- CEP com 8 dígitos.
- UF entre as 27 siglas oficiais, "mesma regra do cadastro" — ver "Nomenclatura e divergências".

### Fluxo

- **Listar**: devolve os endereços do Empregador logado; não devolve registros com `removidoEm` preenchido.
- **Criar e editar**: grava o endereço com `latitude` e `longitude`.
- **Remover**: preenche `removidoEm`; o endereço some da listagem e permanece no banco.
- **Marcar como padrão**: marca `isPadrao` no escolhido e desmarca os outros do mesmo Empregador.

### Regra de negócio

- Só um endereço pode ser o padrão por Empregador.
- Endereço usado somente em anúncios terminais pode ser removido. Anúncios `Combinado` guardam snapshot do
  endereço; remover ou editar o endereço não altera o serviço combinado.
- A checagem futura usa o módulo `anuncio`, que ainda não existe. **Enquanto `anuncio` não existir, a remoção é
  sempre permitida.**
- **Acesso**: só o dono do perfil visualiza e altera os endereços.

### Banco (Flyway)

Tabela `endereco_salvo` conforme o DER.

### Testes

Criar, editar, remover, marcar padrão, marcar outro (o anterior deixa de ser padrão), acessar e alterar endereço de
outra pessoa.

## Nomenclatura e divergências entre o card e o código

- **`PerfilContratante` → `PerfilEmpregador`.** O card e o DER citam "contratante"; o código renomeou para
  empregador (V4) e `perfil/package-info.java` proíbe o termo antigo. Tabela de referência: `perfil_empregador`.
- **"UF — mesma regra do cadastro".** O cadastro não tem regra de UF: `Usuario.cidadeUf` é `String` livre, só
  `@NotBlank`. Não existe lista das 27 siglas nem validação de CEP no projeto. **Proposto**: criar `enum Uf` em
  `perfil/enums` (valor fechado de domínio, `@Enumerated(EnumType.STRING)`); UF fora da lista vira 400
  `VALIDACAO_FALHOU`. Não há motivo para subir para `comum/` (só o `perfil` usa hoje).
- **DER fora do repositório.** O DER fica em `limio/docs/`. Tipos, tamanhos e nulabilidade da migration abaixo são
  **Propostos** e precisam ser conferidos contra o DER antes do código.

## Contrato da API (**Implementado**, rotas **Propostas** — o card deixa as rotas "a definir")

Prefixo `/conta/perfil-empregador`, mesmo padrão do TICKET-0048. O dono vem sempre do token
(`@AuthenticationPrincipal UsuarioAutenticado`); o id do dono nunca vai na URL nem no corpo.

| Operação | Rota | Autenticação | Entrada | Saída |
|---|---|---|---|---|
| Listar endereços | `GET /conta/perfil-empregador/enderecos` | `Bearer <token>` | — | 200 lista de `EnderecoSalvoResponse` |
| Criar endereço | `POST /conta/perfil-empregador/enderecos` | `Bearer <token>` | `EnderecoSalvoRequest` | 201 `EnderecoSalvoResponse` |
| Editar endereço | `PUT /conta/perfil-empregador/enderecos/{id}` | `Bearer <token>` | `EnderecoSalvoRequest` | 200 `EnderecoSalvoResponse` |
| Remover endereço | `DELETE /conta/perfil-empregador/enderecos/{id}` | `Bearer <token>` | — | 204 |
| Marcar como padrão | `PATCH /conta/perfil-empregador/enderecos/{id}/padrao` | `Bearer <token>` | — | 200 `EnderecoSalvoResponse` (idempotente) |

Pontos de contrato:

- **Sem paginação** na listagem: o volume por Empregador é pequeno e `PageResponse` não existe no código.
- **`PUT` substitui todos os campos**; é o primeiro `PUT`/`DELETE` do projeto (hoje só `POST`/`PATCH`).
  **Pendente**: confirmar com o tech lead e com o front; alternativa é `PATCH` para editar.
- `isPadrao` no corpo de criar/editar é aceito; marcar `true` desmarca o anterior (mesma regra da rota `/padrao`).

### Corpo (`EnderecoSalvoRequest` / `EnderecoSalvoResponse`)

```json
{
  "apelido": "Casa",
  "cep": "01310100",
  "rua": "Avenida Paulista",
  "numero": "1000",
  "complemento": "Apto 12",
  "bairroRegiao": "Bela Vista",
  "cidade": "São Paulo",
  "uf": "SP",
  "latitude": -23.5614,
  "longitude": -46.6559,
  "isPadrao": true
}
```

A resposta acrescenta `id`. `removidoEm` não é exposto. Normalização no compact constructor do record: `strip`
nos textos e só dígitos no CEP, antes da validação (padrão de `EditarNomeExibicaoRequest`).

### Códigos de erro

| CodigoErro | Situação | Estado |
|---|---|---|
| `ENDERECO_NAO_ENCONTRADO` (404) | id inexistente, removido ou **de outro Empregador** | **novo, Definido** (sugerido no card) |
| `VALIDACAO_FALHOU` (400) | CEP ≠ 8 dígitos, UF fora das 27, campo obrigatório vazio, coordenada ausente/fora da faixa | existente — cobre o `DADOS_INVALIDOS` do card, **não criar** código novo |
| `NAO_AUTENTICADO` (401) | sem token ou token inválido | existente |
| `ENDERECO_PADRAO_CONFLITO` (409) | rede de segurança do índice único do padrão (duas escritas simultâneas escaparam do lock); o cliente pode repetir a chamada | **novo**, criado na Parte 5 — a decisão inicial era "sem código novo", mas a rede de segurança precisa de um erro para devolver em vez de 500 |
| `VALIDACAO_FALHOU` (400) também para `{id}` que não é UUID | `MethodArgumentTypeMismatchException` tratada no `GlobalExceptionHandler` | **novo handler** (antes caía em 500) |
| `ENDERECO_EM_USO` (409) | remoção bloqueada por anúncio ativo | **Dependência** — só criar quando `anuncio` existir (hoje seria código morto) |

## Regras de negócio

### Acesso e isolamento (**Proposto**)

Todo acesso por id busca por `id` **e** dono. Se não achar, responde 404 `ENDERECO_NAO_ENCONTRADO` — não 403 —,
para não revelar que o id existe em outra conta (mesma linha anti-enumeração do `auth`). A listagem filtra sempre
pelo perfil do token.

**Pendente**: nenhum controller hoje confere o papel ativo (`UsuarioAutenticado.papel()`; as authorities do token
são vazias). Proposta: manter consistente com `PerfilEmpregadorController` e decidir a checagem de papel
`EMPREGADOR` num card próprio, para todas as rotas de empregador.

### Soft delete (**Proposto**)

- `removido_em` anulável; remover grava `Instant.now()` e mantém a linha.
- Todas as leituras filtram `removidoEm IS NULL` por métodos de repository explícitos (o projeto não usa filtro
  global do Hibernate). `BaseService.remover` (delete físico) não é usado.
- Remover um endereço já removido → 404.
- **Remover o endereço padrão**: permitido; limpa `is_padrao` do removido e o Empregador fica sem padrão.
- **LGPD**: a linha removida continua guardando dado pessoal, e o `ContaEncerradaEvent` ainda não tem listener no
  `perfil`. **Dependência/Pendente**: anonimizar ou apagar os endereços ao encerrar a conta, em card próprio.

### Garantia de um único padrão (**Proposto**)

1. **Banco (verdade final):** índice único parcial `uk_endereco_salvo_padrao ON endereco_salvo
   (perfil_empregador_id) WHERE is_padrao AND removido_em IS NULL`.
2. **Aplicação:** toda escrita (criar, editar, remover, marcar) trava a linha do `PerfilEmpregador` com
   `@Lock(PESSIMISTIC_WRITE)` (novo `findByIdParaAtualizar`, espelhando `UsuarioRepository`) dentro da transação
   do use case. Operações do mesmo Empregador são serializadas.
3. **Ordem em "marcar padrão":** primeiro `UPDATE … SET is_padrao = false` nos demais do perfil (com flush), depois
   `is_padrao = true` no escolhido — o índice parcial não é deferível, então a ordem importa.
4. **Rede de segurança (implementada):** `EnderecoSalvoService.salvar` usa `saveAndFlush` e converte
   `DataIntegrityViolationException` cujo `constraintName` seja `uk_endereco_salvo_padrao` (padrão de
   `UsuarioService.salvarComEmailUnico`) em `EnderecoPadraoConflitoException` (409 `ENDERECO_PADRAO_CONFLITO`).
   Com o lock do passo 2 esse caminho só deve ocorrer se uma escrita escapar do lock; outras violações de
   integridade são relançadas sem conversão.
5. Sem `@Version`: o projeto não usa locking otimista; o lock pessimista é o precedente existente.

### Existência de endereço padrão (**Pendente** — o card não define)

| Pergunta | Recomendação |
|---|---|
| Pode existir Empregador sem nenhum padrão? | Sim (0 ou 1 padrão). É o que o card diz ("só um") e evita escolha forçada. |
| Exatamente um padrão quando houver ≥ 1 endereço? | Exigiria promover o primeiro automaticamente e eleger outro ao remover o padrão. Mais complexo; **decisão do tech lead**. |
| Remover o endereço que é o padrão? | Permitir e deixar sem padrão, sem promoção automática. |
| Criar já com `isPadrao = true` quando existe outro padrão? | Permitir; desmarca o anterior. |

### Remoção e módulo `anuncio` (**Dependência**)

Checagem não implementada. O `RemoverEnderecoSalvoUseCase` é o ponto de extensão; quando `anuncio`
existir, usar uma porta em `comum/portas` (padrão de `ConsultaDadosUsuario`), nunca dependência direta de módulo
(ADR-0001), e só então criar `ENDERECO_EM_USO`.

## Decisão técnica: latitude e longitude (**Pendente**)

Achados no projeto: não há geocodificação, ViaCEP, `RestClient` em uso nem PostGIS/hibernate-spatial. Coordenadas
são `DOUBLE PRECISION` comuns. `RestClient` já vem no `spring-boot-starter-webmvc` (sem dependência nova).

| Opção | Prós | Contras |
|---|---|---|
| **1. Cliente envia lat/long** | Sem dependência nem falha externa; determinístico em teste; o app já geocodifica na tela "Novo endereço" | Confia no cliente (valida só faixa) |
| 2. Backend geocodifica o endereço completo (Google/Nominatim) | Dado confiável e centralizado | Custo, chave, rate limit, envio de dado pessoal a terceiro (LGPD), precisa de fallback |
| 3. Backend geocodifica pelo CEP (ViaCEP/BrasilAPI) | Simples | Precisão baixa (centro do CEP), dependência externa |

**Recomendação:** opção 1 neste card. O backend valida as faixas (-90..90 e -180..180) e persiste na mesma
transação; colunas `NOT NULL`; coordenada ausente → 400 `VALIDACAO_FALHOU`. Não há falha de serviço externo a
tratar.

Se o tech lead escolher geocodificação no servidor: `GeocodificacaoService` em `perfil/actions/service/`
(`@Component` com propriedades no `application.properties`, como `EmailService`/`StorageProperties`), chamada
**fora** da transação de escrita, e latitude/longitude passam a ser anuláveis (criar/editar sem coordenadas quando
a geocodificação falhar). Essa decisão muda a migration; por isso precisa sair antes do código.

## Decisão técnica: Rua, Número e Complemento (**Definido** pelo tech lead)

Não existe modelo de endereço reutilizável no projeto (só `Usuario.cidadeUf`, string). O card lista
`logradouroCompleto`, mas a tela "Novo endereço" tem Rua, Número e Complemento. **Decisão do tech lead:** o
endereço guarda os três campos separados, no banco e na API, em vez de `logradouroCompleto`. Isso muda o DER
(`logradouro_completo` é substituído por `rua`, `numero` e `complemento`) — o DER precisa ser atualizado por quem o
mantém (fora deste repositório).

- `rua`: obrigatório, até 255 caracteres.
- `numero`: obrigatório, **texto** (até 20), porque aceita "S/N" e "123-A".
- `complemento`: opcional (`NULL` quando não informado), até 100 caracteres.
- Quem precisar de uma linha única de endereço (ex.: o snapshot do anúncio, futuro módulo `anuncio`) monta o texto a
  partir dos três campos; o `perfil` não grava texto montado. Não há parsing de texto nem coluna redundante.

## Implementação

- **Migration** `V14`: tabela `endereco_salvo` com CHECKs de CEP (8 dígitos), UF (27 siglas) e faixas de
  latitude/longitude; índice único parcial `uk_endereco_salvo_padrao`; índice `idx_endereco_salvo_perfil`.
- **Use cases** (todos `@Transactional`, travam o `PerfilEmpregador` com `garantirPerfilParaAtualizar`, exceto
  listar): `Listar`, `Criar`, `Editar`, `Remover`, `MarcarEnderecoSalvoPadrao`. `Criar`/`Editar` com
  `isPadrao = true` desmarcam o padrão atual antes de gravar.
- **Marcar padrão** é idempotente: se o endereço já é o padrão, devolve-o sem gravar nada.
- **Remover** chama `EnderecoSalvo.remover(Instant)`: preenche `removidoEm` e zera `isPadrao`.
- **Lat/long**: o cliente envia; o backend valida só a faixa. **Rua/número/complemento**: três campos.
- **Listagem** ordenada por padrão primeiro e depois por apelido.
- **Testes**: specs de cada use case, do service (inclusive a rede de segurança), do request, do mapper, da
  entity e de `Uf`; stories de persistência/índice, criar-listar-editar (inclui 8 criações simultâneas como
  padrão), remover e marcar padrão (inclui 8 marcações simultâneas).

## Fora de escopo

- Checagem de endereço em uso por anúncio e o código `ENDERECO_EM_USO` (depende de `anuncio`).
- Anonimização/expurgo de endereços ao encerrar a conta (LGPD; listener de `ContaEncerradaEvent`).
- Checagem do papel ativo `EMPREGADOR` nas rotas.
- Preencher `enderecos.{quantidade, apelidoPadrao}` no `GET /conta/perfil-empregador`: **não feito** neste card;
  continua o placeholder `{"quantidade": 0, "apelidoPadrao": null}` do TICKET-0048. Já dá para implementar
  (`EnderecoSalvoService` conta os ativos e acha o padrão), mas exige a porta/ligação no `PerfilEmpregadorMapper`
  e decisão de card próprio.
- Geocodificação no servidor, salvo decisão em contrário do tech lead.

