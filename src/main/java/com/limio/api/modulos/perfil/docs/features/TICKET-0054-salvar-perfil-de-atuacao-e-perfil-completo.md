# TICKET-0054 — Salvar o perfil de atuação e calcular "perfil completo"

## Link do ticket

`feature/54` — [prestador] [BACK-END] Salvar o perfil de atuação e calcular "perfil completo". Card par:
[prestador] [FRONT-END] Telas "Perfil de atuação" e "Especialidades". Insumos: Figma LIMIO (10-perfil-atuacao.png,
Especialidades), planilha "Gerenciar perfil prestador", DER, planilha de casos de uso.

## User Story

Como Prestador, quero que a API guarde minhas especialidades, meu raio, meu município-base e meus horários, para ver
no mural só os anúncios que combinam comigo.

## Regra de negócio implementada

- **Listar categorias**: as ativas, em ordem de exibição (`ordem`, depois nome). "Outros" sempre vem — é protegida, e
  o banco não deixa categoria protegida ficar inativa (`ck_categoria_protegida_ativa`).
- **Consultar perfil de atuação**: especialidades, raio, município-base/UF, janelas e `completo`. Quem nunca salvou
  recebe 200 com tudo vazio e `completo = false` (nada é gravado).
- **Salvar perfil de atuação**: estado completo (PUT). Cria o `PerfilPrestador` no primeiro salvamento; substitui as
  especialidades (`prestador_categoria`) e as janelas (`janela_atendimento`); resolve `coordenadasBase` (latitude/longitude)
  a partir do município-base; recalcula `completo`.
- **Especialidades vazias**: aceito (UC) — o perfil fica incompleto e o mural fica vazio até haver ao menos uma.
- **Especialidade inválida**: id inexistente ou de categoria inativa recusa a gravação inteira
  (422 `CATEGORIA_INVALIDA`).
- **Perfil completo** (critério PROVISÓRIO, pendência do PO): ao menos 1 especialidade + município-base/UF + raio —
  o exemplo do card. Janelas não entram. Calculado a cada gravação, num único método
  (`PerfilPrestador.calcularCompleto`) — quando o PO travar o critério, a mudança é só ali.
- **Acesso**: o perfil é sempre o do usuário do token (`UsuarioAutenticado.id`), nunca vem do corpo nem da rota —
  ninguém lê nem altera o perfil de outro. Não exige papel ativo `PRESTADOR` (ver decisões).

## Validação (mesma regra no record e no Zod)

- `especialidades`: lista de ids de `GET /categorias`, obrigatória mas pode ser vazia; id repetido conta uma vez.
- `raioAtendimentoKm`: inteiro de 1 a 40.
- `municipioBase`: obrigatório, até 100 caracteres (espaços das pontas são removidos). `ufBase`: uma das 27 UFs
  oficiais (sigla, ex.: `"SP"`).
- `janelas`: lista obrigatória, pode ser vazia. Cada janela tem `diaSemana`, `horaInicio` e `horaFim` (`HH:mm`), com
  início antes do fim (igual também é recusado).

## Contrato da API (compartilhado com o card [FRONT-END])

| Operação | Rota | Autenticação | Entrada | Saída |
|---|---|---|---|---|
| Listar categorias | `GET /categorias` | `Bearer <token>` | — | 200 `[{ id, nome, grupo }]` |
| Consultar perfil de atuação | `GET /perfil/prestador/atuacao` | `Bearer <token>` | — | 200 `PerfilAtuacao` |
| Salvar perfil de atuação | `PUT /perfil/prestador/atuacao` | `Bearer <token>` | `{ especialidades, raioAtendimentoKm, municipioBase, ufBase, janelas }` | 200 `PerfilAtuacao` |

```json
// PUT /perfil/prestador/atuacao
{
  "especialidades": ["<id da categoria>", "<id da categoria>"],
  "raioAtendimentoKm": 10,
  "municipioBase": "Campinas",
  "ufBase": "SP",
  "janelas": [{ "diaSemana": "SEGUNDA", "horaInicio": "08:00", "horaFim": "12:00" }]
}

// PerfilAtuacao (resposta do GET e do PUT)
{
  "especialidades": [{ "id": "...", "nome": "Elétrica", "grupo": "Casa e reparos" }],
  "raioAtendimentoKm": 10,
  "municipioBase": "Campinas",
  "ufBase": "SP",
  "janelas": [{ "diaSemana": "SEGUNDA", "horaInicio": "08:00", "horaFim": "12:00" }],
  "completo": true
}
```

- `diaSemana`: `SEGUNDA`, `TERCA`, `QUARTA`, `QUINTA`, `SEXTA`, `SABADO`, `DOMINGO`.
- Especialidades voltam em ordem de exibição; janelas por dia da semana (segunda primeiro) e horário de início.
- `coordenadasBase` não sai na resposta — são internas (mural).

Erro: `ErroResponse { codigo, mensagem, timestamp, path }`, sempre via `GlobalExceptionHandler`.

| `codigo` | HTTP | Quando |
|---|---|---|
| `VALIDACAO_FALHOU` | 400 | Campo ausente, raio fora de 1–40, UF fora das 27, janela com início depois do (ou igual ao) fim, janelas sobrepostas no mesmo dia, `diaSemana`/horário em formato inválido |
| `NAO_AUTENTICADO` | 401 | Sem token de acesso válido |
| `CATEGORIA_INVALIDA` | 422 | Especialidade inexistente ou inativa |
| `MUNICIPIO_NAO_ENCONTRADO` | 422 | A geocodificação não achou município com esse nome na UF |
| `GEOCODIFICACAO_INDISPONIVEL` | 503 | Serviço de geocodificação fora do ar, lento, recusando consulta ou devolvendo resposta fora do formato (lat/lon não numérico, `place_rank` ausente) — tentar de novo |

`DADOS_INVALIDOS` (nome sugerido no card) não foi criado: `VALIDACAO_FALHOU` já cumpre esse papel no projeto todo.

## Decisão técnica e alternativas descartadas

Pendências do card resolvidas com o dev. **Decisão do tech lead (2026-10-26)**: aprovadas as partes técnicas (Nominatim, enum `DiaSemana`, DER);
as de produto seguem com o PO:

- **Coordenadas do município-base (tech lead)**: geocodificação pelo Nominatim (OpenStreetMap), em
  `actions/service/GeocodificacaoService`. Consulta estruturada `city` + `state` (nome da UF por extenso) +
  `countrycodes=br` + `featureType=city`; só aceita resultado com `place_rank` ≤ 16 (município; Brasília vem como 15)
  — num teste manual, "Centro"/RJ devolvia um distrito de Nova Friburgo (rank 18). Descartados: tabela de municípios
  do IBGE carregada por migration (offline e determinística, mas 5.570 linhas de dados de terceiro no repo); deixar
  as coordenadas nulas até decidir (o mural não teria como calcular distância).
- **Política de uso do Nominatim público** (https://operations.osmfoundation.org/policies/nominatim/): no máximo
  1 requisição/s, User-Agent identificando o app, cache do nosso lado, atribuição ao OpenStreetMap, sem autocomplete,
  e o app tem que conseguir trocar de serviço sem versão nova. Como foi atendido:
  - URL e User-Agent configuráveis (`geocodificacao.nominatim.*`, envs `GEOCODIFICACAO_NOMINATIM_URL` /
    `GEOCODIFICACAO_NOMINATIM_USER_AGENT`) — trocar por instância própria ou outro provedor compatível é só env.
  - Cache: antes de chamar o Nominatim, o usecase procura um perfil que já tenha o mesmo município/UF (ignora caixa)
    e reaproveita as coordenadas — inclusive o do próprio Prestador quando ele salva de novo sem trocar de município.
    Só município que nenhum perfil resolveu ainda gera consulta.
  - **Pendente**: atribuição "© OpenStreetMap contributors" — decidir com o front onde exibir.
  - **Risco aceito**: não há fila/limitador global de 1 req/s. Gravação de perfil é rara e o cache corta a maior
    parte das consultas; se o volume crescer, subir instância própria ou trocar de provedor (só configuração).
- **Falha da geocodificação**: não salva (503 `GEOCODIFICACAO_INDISPONIVEL`). Descartado salvar sem coordenadas: o
  mural do Prestador ficaria vazio sem aviso nenhum.
- **`diaSemana` (tech lead: 0–6 ou 1–7?)**: nenhum dos dois — enum `DiaSemana` por extenso (`SEGUNDA`…`DOMINGO`),
  gravado como `STRING`, como todo valor fechado de domínio no projeto. Sem convenção implícita de número entre back e
  front.
- **Agrupamento das categorias (diagrama)**: a `Categoria` do DER não tem grupo, e "em ordem" não tinha critério.
  Acrescentados `categoria.grupo` (texto: "Casa e reparos", "Limpeza", ...) e `categoria.ordem` — desvio do DER
  documentado, como `sessao.expira_em` no TICKET-0032. Texto, não enum: grupo é taxonomia que o admin vai querer
  mexer sem deploy.
- **Acesso por papel**: não exige papel ativo `PRESTADOR`. A troca pra Prestador (UC09) tende a checar perfil
  completo (fluxo alternativo, ainda fora do código — TICKET-0032); se só o Prestador pudesse preencher o perfil,
  ninguém conseguiria virar Prestador. "Só o próprio" é garantido pelo id do token.
- **"Outros" protegida**: `Categoria` não tem setter de `ativa`/`protegida`; `Categoria.desativar()` recusa a
  protegida com `CategoriaProtegidaException` (422 `CATEGORIA_PROTEGIDA`). Ainda não existe tela de admin pra
  desativar categoria — quem implementar já recebe erro de negócio, não o 500 do banco. O banco continua barrando
  (`CHECK (ativa OR NOT protegida)`) quem passar por fora da entity.

Demais decisões:

- **Estado completo (PUT)**: o front sempre manda o perfil inteiro. As listas são obrigatórias (podem ser vazias)
  pra campo esquecido não apagar especialidades/janelas sem querer.
- **Primeiro salvamento concorrente**: advisory lock por usuário (`PerfilPrestadorService.travarPorUsuario`, via
  `comum/persistencia/TravaTransacional` com escopo `EscopoTrava.PERFIL_PRESTADOR` — o mesmo helper do lock do
  login, cada um no seu escopo). Sem isso, um toque duplo em "Salvar" no primeiro salvamento estourava
  `uk_perfil_prestador_usuario_id` como 500.
- **Geocodificação fora da transação**: validação das especialidades e geocodificação rodam antes; só lock + leitura
  + gravação ficam no `TransactionOperations`. Assim a chamada ao Nominatim (até `geocodificacao.timeout`, 5s) não
  segura conexão do pool — PUTs concorrentes pra municípios novos esgotariam o pool e travariam outros endpoints.
- **Janelas sobrepostas**: recusadas no record (`PerfilAtuacaoRequest.isJanelasSemSobreposicao`, 400) — mesmo dia e
  intervalos que se cruzam, inclusive janela repetida; encostar (08–12 e 12–14) pode. Sem constraint no banco:
  exclusão por intervalo exigiria a extensão `btree_gist`, e o PUT é a única porta de escrita.
- **Leitura do perfil em duas queries**: `findByUsuarioId` faz fetch só das especialidades; as janelas vêm num
  `Hibernate.initialize` na mesma transação (`PerfilPrestadorService.buscarPorUsuario`). As duas coleções no mesmo
  join viravam produto cartesiano e a `List` de janelas voltava repetida.
- **`PerfilPrestador` sem setter**: `completo` depende de todos os campos de atuação, então eles só mudam juntos, por
  `atualizarAtuacao`, que recalcula no fim.
- **`usuario_id` cru, sem FK pra `usuario`** (ADR-0001 §1.6): `perfil` não se acopla a `auth`.
- **Coordenadas em `DOUBLE PRECISION`**: centro de município não precisa de precisão decimal exata; facilita o
  cálculo de distância do mural. Na entity, `Coordenadas` é record `@Embeddable` (colunas `latitude_base` /
  `longitude_base`) e valida a faixa (-90..90, -180..180) na criação.
- **`GeocodificacaoService` fica em `modulos/perfil`**: só o perfil geocodifica hoje (critério do ADR-0001 pra
  subir pra `comum/`: 2+ módulos usando agora). Quando o mural precisar, sobe pra `comum/` — e aí, com dois
  chamadores, entra um limitador de 1 req/s compartilhado.

## Banco (Flyway)

`V11__criar_tabelas_perfil_prestador.sql`: `categoria`, `perfil_prestador`, `prestador_categoria`,
`janela_atendimento` e a carga inicial das categorias. Só os campos que o card usa — colunas do DER fora deste card
(nome de exibição, avaliações, cota mensal) entram com os tickets delas.

Carga inicial (Figma "Especialidades"):

| Grupo | Categorias |
|---|---|
| Casa e reparos | Elétrica, Hidráulica, Pintura, Montagem de móveis, Reparos gerais |
| Limpeza | Limpeza, Limpeza pós-obra |
| Outros | Outros (protegida) |

O agrupamento acima foi inferido da ordem do Figma — confirmar com o PO.

`V11` porque a `develop` já tem a `V10` (perfil do Empregador, TICKET-0048) e as PRs abertas #6 (`feature/34`) e
#7 (`feature/36`) disputam a `V8`/`V9`. Migration com versão menor que a última aplicada é recusada pelo Flyway
(out of order) — quem entrar depois de uma versão maior renumera.

## Testes

- `spec/`: `SalvarPerfilAtuacaoUseCaseTest`, `ConsultarPerfilAtuacaoUseCaseTest`, `PerfilPrestadorTest` (critério de
  completo), `PerfilAtuacaoRequestTest` (validação), `PerfilAtuacaoMapperTest` (ordenação),
  `GeocodificacaoServiceTest` (Nominatim falso com o servidor HTTP do JDK — URL, User-Agent, rank, erro, fora do ar).
- `story/` (`PerfilAtuacaoStoryTest`, geocodificação mockada): salvar completo, salvar sem especialidades, raio fora
  de 1–40, janela com início depois do fim (os quatro do card) + listar categorias, perfil nunca salvo, categoria
  inválida, município não encontrado, re-salvamento substituindo especialidades/janelas, cache de coordenadas entre
  perfis, papel `EMPREGADOR`, perfil de outro usuário e 401 sem token.

## Fora de escopo / pendências

- **PO**: critério definitivo de `completo`; itens do grupo "Área externa" (entram numa migration nova); "3 de 12
  selecionadas" — se 12 for o máximo que o Prestador pode escolher (e não o total de categorias), falta o limite no
  record e no usecase.
- **Front/PO**: onde exibir a atribuição ao OpenStreetMap.
- ~~**Tech lead**: validar Nominatim; o DER real das quatro tabelas~~. **Decisão do tech lead (2026-10-26)**: fica o Nominatim público, com o cache
  por município que já existe e o limite de 1 req/s da política de uso; instância própria só se o volume pedir
  (a URL já é configurável). O DER passa a ser o que está na V11.
- Checagem de perfil completo na troca de papel (UC09) — continua fora, como no TICKET-0032; `completo` agora existe
  pra quando ela entrar (via evento ou contrato em `comum/`, nunca import de `perfil` dentro de `auth`).
- Mural (anúncios por especialidade/raio) — consome `prestador_categoria` e as coordenadas, ticket próprio.
- ~~Raio decimal (`10.7`) truncado pelo Jackson~~. **Decisão do tech lead (2026-10-26)**: `ACCEPT_FLOAT_AS_INT` desligado na API toda; decimal em
  campo inteiro responde 400 `VALIDACAO_FALHOU`.
