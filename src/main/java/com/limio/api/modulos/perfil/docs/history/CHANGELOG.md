# Histórico — perfil

Registre aqui, em ordem cronológica, decisões e mudanças de regra que não ficam óbvias só lendo o código.

Formato de cada entrada: novo arquivo `AAAA-MM-titulo-curto.md` nesta pasta, com contexto do "porquê" da mudança.

## 2026-10-15 — TICKET-0048: documentação da feature "perfil do Empregador" (etapa 1, sem código)

Primeira documentação do módulo `perfil`, que até aqui só tinha `package-info.java`. Criado
`docs/features/TICKET-0048-consultar-e-editar-perfil-empregador.md` com contrato de API proposto, regras de
negócio, limitações e decisões arquiteturais. 

A análise do card mostrou que "mesmas regras do cadastro (ETI-30)" não existe no código: o UC01 implementado não
envia OTP nem confirmação de e-mail, não há fluxo UC04/UC05 nem gateway de SMS, e o telefone é gravado só com
dígitos (`VARCHAR(11)`), não como `+55DDNNNNNNNNN`. Também não existem `Midia`, avaliações nem `endereco_salvo`.
Esses pontos estão registrados como dependências, e o `GET` proposto devolve `null` (não existe) e `0`
(contagem real zero) conforme o caso.

As decisões de arquitetura (troca de contato como operação do `auth`, porta em `comum/` para leitura de
contato, criação do perfil sob demanda) estão marcadas como **propostas**; aguardam tech lead/PO.

## 2026-10-16 — TICKET-0048: perfil lê dados do usuário por porta em `comum/` e nasce no primeiro acesso

O módulo `perfil` precisa de nome completo, e-mail, telefone e datas de confirmação do usuário, que são do `auth`
(ADR-0001: módulo não importa outro módulo). Criada a porta `comum/portas/ConsultaDadosUsuario`, implementada no
`auth`. Primeira interface do tipo em `comum/` — a ADR não a lista, **aguarda confirmação do tech lead**. A regra de uso (leitura síncrona; reação continua sendo evento) está em
`../../../auth/docs/funcionalidade/EXPLICACAO_CLASSES.md`.

O perfil não pode ser criado no cadastro (o `auth` importaria o `perfil`) e o projeto não tem eventos entre módulos
(criar o primeiro exigiria infraestrutura nova e backfill das contas existentes). Por isso `GET` e `PATCH`
criam o perfil no primeiro acesso, com o nome completo do cadastro, de forma segura contra acesso simultâneo
(`ON CONFLICT DO NOTHING`). `PATCH` passou a devolver o mesmo corpo do `GET`.

## 2026-10-16 — TICKET-0048 contato trocado no `auth` aparece no perfil

A troca de e-mail e de telefone é operação do `auth` (`PATCH /auth/email`, `PATCH /auth/telefone`); o `perfil` não
mudou. O `GET /conta/perfil-empregador` já devolve o contato novo, com as confirmações nulas, pela porta
`ConsultaDadosUsuario` (coberto por story). O telefone sai em formato internacional (`+55...`): o banco guarda só os
dígitos nacionais e quem implementa a porta (`auth`) converte, então o `perfil` não conhece a regra. A confirmação de e-mail (UC05) e de telefone (UC04) não existe; o link
enviado na troca de e-mail não conclui a confirmação.

## 2026-10 — criação lazy do perfil num só lugar (`garantirPerfil`)

Code review: `ConsultarMeuPerfilUseCase` e `EditarNomeExibicaoUseCase` repetiam "buscar `DadosUsuario` ou 404" e
"`buscarOuCriar` com o nome inicial"; mudar a regra em um só faria os endpoints divergirem. Agora
`PerfilEmpregadorService.garantirPerfil(usuarioId)` faz a consulta à porta, o 404 e a criação, e devolve
`MeuPerfilEmpregador`. O corte do nome inicial em 80 caracteres saiu do helper `NomeExibicaoInicial` para
`PerfilEmpregador.nomeExibicaoInicialDe` (regra da entity; o service não pode importar `actions/helper`).

## 2026-10-21 — TICKET-0050: endereços salvos do Empregador (listar, criar, editar, remover, marcar padrão)

Nova subentidade `EnderecoSalvo` (`perfil/endereco/`, tabela `endereco_salvo`, migration `V14`) e as rotas
`/conta/perfil-empregador/enderecos` (`GET`, `POST`, `PUT /{id}`, `DELETE /{id}`, `PATCH /{id}/padrao`). Contrato,
regras e pendências em `docs/features/TICKET-0050-enderecos-salvos-do-empregador.md`.

Decisões que não ficam óbvias só lendo o código:

- **`PerfilEmpregador`, não `PerfilContratante`**: o card usa o nome antigo; o código o renomeou na V4.
- **`rua`, `numero`, `complemento` em vez de `logradouroCompleto`** (decisão do tech lead). O DER, fora deste
  repositório, precisa ser atualizado.
- **Latitude/longitude** são `NOT NULL`. Vinham do cliente; hoje o servidor as obtém (ver a entrada
  de geocodificação abaixo).
- **Soft delete** por `removido_em`; todo finder filtra `removidoEm is null`. Remover o padrão deixa o Empregador
  sem padrão (0..1 padrão, sem promoção automática).
- **Um único padrão por Empregador** em duas camadas: índice único parcial no banco e lock pessimista no
  `PerfilEmpregador` em toda escrita. A ordem "desmarcar os outros e só depois marcar" é obrigatória (o índice
  parcial não é deferível).
- **Recurso alheio, removido ou inexistente → 404** `ENDERECO_NAO_ENCONTRADO`, nunca 403 (anti-enumeração).
- **`ENDERECO_PADRAO_CONFLITO` (409)** criado como rede de segurança do índice; o plano inicial previa "sem código
  novo". `ENDERECO_EM_USO` **não** foi criado (depende do módulo `anuncio`).
- **`GlobalExceptionHandler`**: id de rota que não é UUID passou de 500 para 400 `VALIDACAO_FALHOU`.
- **Jackson 3** recusa `boolean` primitivo ausente em record; por isso `isPadrao` é `Boolean` no request e é
  normalizado para `false` no compact constructor.
## 2026-10-14 — TICKET-0054: perfil de atuação do Prestador

Entram `categoria/` (entity raiz, taxonomia), `perfilprestador/` (entity raiz) e
`perfilprestador/janelaatendimento/` (subentidade, sem ciclo de vida próprio), ao lado do `empregador/` do
TICKET-0048. Migration `V11__criar_tabelas_perfil_prestador.sql` com a carga inicial das categorias — `V11` porque a
`develop` já tem a `V10` (perfil do Empregador) e as PRs abertas #6 e #7 disputam a `V8`/`V9`.

Desvios do DER: `categoria.grupo` e `categoria.ordem` (a tela agrupa e ordena; o DER não tem como). As quatro
tabelas nasceram só com os campos que o card usa.

Coordenadas do município-base vêm do Nominatim (OpenStreetMap) — escolha do dev, a validar com o tech lead. O
servidor público tem política de uso (1 req/s, User-Agent próprio, cache, atribuição), então a URL é configurável e o
usecase reaproveita coordenadas que algum perfil já gravou pro mesmo município antes de consultar. Se a
geocodificação falha, o perfil não é salvo (503): sem coordenadas, o mural ficaria vazio sem aviso.

`completo` usa critério provisório (exemplo do card: 1+ especialidade + município/UF + raio) enquanto o PO não
decide. Fica num único método, `PerfilPrestador.calcularCompleto`.

O perfil não exige papel ativo `PRESTADOR`, só o dono do token: a troca pra Prestador (UC09) tende a exigir perfil
completo, e exigir o papel aqui criaria um ciclo em que ninguém consegue virar Prestador.

Detalhe completo em `docs/features/TICKET-0054-salvar-perfil-de-atuacao-e-perfil-completo.md`.

## 2026-10-19 — TICKET-0056: foto de perfil e a entity `Midia` no `perfil`

A foto é do usuário, não do papel: uma só para Empregador e Prestador. Por isso não existe `foto_midia_id` nos
perfis; a foto atual é a `Midia` `FOTO_PERFIL` sem `expurgo_em`, garantida única pelo índice parcial
`uk_midia_foto_perfil_ativa` (desvio do DER, que liga `fotoMidia` ao perfil).

`Midia` estava prevista no `anuncio`, mas o `perfil` não pode importá-lo (ADR-0001). Como só o `perfil` usa hoje, a
entity nasce em `perfil/midia/`; a tabela já é genérica (`tipo`) e sobe para `comum/` quando anúncio/chat precisarem.

O app envia o arquivo para a API (multipart), não direto ao bucket: o EXIF (localização) tem de sair antes de a foto
ser guardada. O bucket é privado; `url_s3` guarda a chave do objeto e o app recebe um link assinado que expira em 1 h.

Formatos (JPEG/PNG) e tamanho (5 MB) são provisórios até o PO decidir. Detalhe em
`docs/features/TICKET-0056-enviar-e-trocar-foto-de-perfil.md`.

## 2026-10-21 — TICKET-0054: ajustes do review da PR #9

- `GET /perfil/prestador/atuacao` devolvia janelas repetidas com 2+ especialidades e 2+ janelas: as duas coleções
  vinham no mesmo fetch join (produto cartesiano, e a `List` de janelas não deduplica). Agora são duas queries.
- Janelas sobrepostas no mesmo dia passam a ser recusadas (400 `VALIDACAO_FALHOU`).
- Resposta 200 do Nominatim fora do formato (lat/lon não numérico, `place_rank` ausente) vira 503
  `GEOCODIFICACAO_INDISPONIVEL`, não mais 500 genérico.
- A geocodificação saiu de dentro da transação: não segura mais conexão do pool durante a chamada externa.
- `Categoria.desativar()` recusa "Outros" com erro de negócio (`CATEGORIA_PROTEGIDA`), antes de chegar no CHECK do
  banco.
- Advisory lock virou helper em `comum/persistencia/` (`TravaTransacional` + `EscopoTrava`), usado pelo perfil e pelo
  login — o lock do login passou pra forma de duas chaves, com escopo `LOGIN`.
- O geocodificador continua em `modulos/perfil` (só ele usa hoje — critério do ADR-0001); hoje é a porta
  `Geocodificador` com `NominatimGeocodificador` (ver a entrada de geocodificação).

## 2026-10-22 — TICKET-0050: endereços obtêm latitude/longitude no servidor

O backend geocodifica o endereço; o cliente deixa de enviar `latitude`/`longitude`.
Contrato, falhas e pendências em `docs/features/TICKET-0050-enderecos-salvos-do-empregador.md`.

- **Porta `Geocodificador`** (`perfil/actions/service/`), com `NominatimGeocodificador` (antes
  `GeocodificacaoService`, do 0054) como implementação. Os dois use cases de endereço e o do 0054 dependem só da
  interface; trocar de provedor é nova implementação + propriedade.
- **`Uf` unificado** em `perfil/enums` (agora com nome por extenso); o `Uf` de `perfilprestador/enums` foi removido.
- **Consulta só com rua, número, cidade e UF**: sem CEP (esparso no OSM brasileiro), sem bairro (a busca estruturada
  não aceita) e sem complemento. Resultado só vale com `place_rank` ≥ 26 e mesma UF; senão, 422
  `ENDERECO_NAO_LOCALIZADO` (novo). Falha do provedor → 503 `GEOCODIFICACAO_INDISPONIVEL`, sem retry.
- **Fora da transação** (`TransactionOperations`, como o `SalvarPerfilAtuacaoUseCase`). Coordenadas seguem
  `NOT NULL`; falha impede a gravação; nenhuma migration nova.
- **Edição só reconsulta** se rua, número, cidade ou UF mudarem. Risco aceito: duas edições simultâneas do mesmo
  endereço podem misturar texto e coordenadas.
- **Contrato**: `EnderecoSalvoRequest` sem `latitude`/`longitude` (enviadas, são ignoradas); a resposta continua
  devolvendo as duas. O front precisa remover os campos e tratar 422/503.
- Pendente com o tech lead: provedor em produção (servidor público: 1 req/s e sem SLA), taxa de "não localizado" no
  Brasil, LGPD (endereço enviado a terceiro), DER.

## 2026-10-21 — TICKET-0056: ajustes do review da PR #10

- Advisory lock da foto saiu do `MidiaRepository` e passou pro helper `comum/persistencia/TravaTransacional`
  (escopo `EscopoTrava.FOTO_PERFIL`), o mesmo que o login usa — e o mesmo arquivo da PR #9 (TICKET-0054), que traz o
  escopo `PERFIL_PRESTADOR`. Quem entrar depois resolve só o conflito do enum.
- Runtime sem writer ImageIO vira 500 (problema do servidor), não mais 422 `FOTO_INVALIDA`.
- Scanner de EXIF para em segmento com tamanho < 2 e em IFD0 fora do APP1. Os dois casos já davam "sem rotação" por
  acaso; a regra ficou explícita.
- `ArmazenamentoService`: sai o `contentLength` manual, o `RequestBody.fromBytes` já define.

## 2026-10-27 — ETI-59: mídia e `Uf` sobem pra `comum/`, portas de categoria e endereço

O `anuncio` passou a gravar fotos (ETI-59), então a mídia bateu o critério do ADR-0001 ("2+ módulos usando hoje"):
`Midia`, `MidiaRepository`, `MidiaService`, `LinkTemporario`, `TipoMidia`, `ArmazenamentoService`, `ImagemHelper`,
`FotoInvalidaException` e `ArmazenamentoIndisponivelException` saíram de `perfil` pra `comum/midia/`. Mesma coisa com
`Uf` (`comum/localizacao/enums/`), que o endereço avulso do anúncio usa. Só mudou o pacote; o comportamento da foto de
perfil é o mesmo. A propriedade `perfil.foto.validade-link` virou `midia.validade-link` e continua lendo
`PERFIL_FOTO_VALIDADE_LINK` se `MIDIA_VALIDADE_LINK` não existir (deploy não precisa mudar).

`perfil` implementa duas portas novas de `comum/portas/` (ADR-0003), usadas na criação do anúncio:
`ConsultaCategoria` (categoria por id) e `ConsultaEndereco` (endereço salvo do usuário, ou avulso geocodificado, com o
texto de uma linha que o anúncio guarda como retrato). `EnderecoSalvoRepository.findAtivoDoUsuario` busca o endereço
pelo id do usuário (join com `perfil_empregador`), sem criar perfil.
