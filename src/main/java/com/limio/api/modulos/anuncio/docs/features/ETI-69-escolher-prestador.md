# ETI-69 — Escolher prestador: transação atômica, cota e liberação do contato

## Status

Implementado.

## Link do card

[ETI-69](https://kaueamaral96.atlassian.net/jira/software/projects/ETI/boards/2?selectedIssue=ETI-69) —
[anuncio] [BACK-END] Escolher prestador (UC33): transação atômica, cota e liberação do contato.

## User Story

Como um Empregador, eu quero escolher um dos candidatos do meu anúncio, para combinar o serviço com ele e liberar o
contato só para essa pessoa.

## Escopo implementado

- `POST /anuncios/{anuncioId}/candidaturas/{candidaturaId}/escolher`: numa única transação, a candidatura vira
  `ESCOLHIDA` (`escolhidaEm`), as outras `ENVIADA` viram `ENCERRADA` (`encerradaEm`, motivo "Outro prestador
  escolhido"), a cota do Prestador FREE é consumida e `PrestadorEscolhidoEvent` é publicado. Responde `200` com o id do
  anúncio, que passa a representar o serviço combinado.
- O chat dos candidatos não escolhidos vira somente leitura (`OUTRO_PRESTADOR_ESCOLHIDO`) por um listener do módulo
  `chat`; o chat do escolhido segue aberto.
- Os usecases que escrevem no agregado (desistir, candidatar-se, cancelar e iniciar o serviço) passaram a tomar o mesmo
  advisory lock `EscopoTrava.ANUNCIO` da escolha (ver "Concorrência").

**Fora**: aviso ao escolhido e aos não escolhidos (notificação, ETI-112: só o evento é publicado), cancelar o serviço
e estornar a cota (ETI-94 a ETI-97), módulo de assinatura GOLD (o `financeiro` ainda é stub).

## Regra de negócio

| Regra | Implementação |
|---|---|
| Só o dono escolhe; dono errado e anúncio inexistente respondem igual | `buscarPorId(...).filter(pertenceA)` depois da trava: 404 `ANUNCIO_NAO_ENCONTRADO`. |
| Anúncio precisa estar `PUBLICADO` | 409 `ANUNCIO_ESTADO_INVALIDO` para os demais status. **O anúncio continua `PUBLICADO` depois da escolha**: o que muda é a candidatura (ETI-86 e ETI-88 já assumem isso, `Anuncio.permiteInicio` só aceita `PUBLICADO`). |
| Só uma escolha por anúncio | `AplicacaoService.existeEscolhida` → 409 `ESCOLHA_CONFLITO`; rede de segurança no banco: `idx_aplicacao_escolhida_unica` (V13), violação traduzida por `AplicacaoService.salvarEscolha`. |
| Candidatura é do anúncio da URL | Inexistente ou de outro anúncio: 404 `CANDIDATURA_NAO_ENCONTRADA`. |
| Só candidatura `ENVIADA` pode ser escolhida | Do mesmo anúncio e fora de `ENVIADA` (recusada, encerrada, escolhida): 409 `CANDIDATURA_ESTADO_INVALIDO`. |
| Demais `ENVIADA` encerram; `RECUSADA` permanece | `Aplicacao.encerrarPorOutraEscolha`; a busca com lock traz só as `ENVIADA`, então as recusadas nem são carregadas. |
| FREE consome cota; GOLD ativo não | `ConsultaPlanoPrestador.planoAtual` no instante da escolha (não o da candidatura): `GOLD` pula, `FREE` chama `ConsumoCotaPrestador.consumirEscolha`. `switch` sem `default`. |
| Cota esgotada recusa sem alterar nada | A cota é consumida antes de qualquer mutação das candidaturas; se não há saldo, 409 `COTA_ESGOTADA_CANDIDATO` com o nome abreviado do candidato ("Rafael S.") na mensagem. Nada é escolhido, encerrado, consumido nem publicado. |
| Mês da cota | Mês civil de `America/Sao_Paulo` (`CotaMensal.mesDe`), limite `perfil.cota.teto-free` (4). |
| Liberação de telefone e endereço | Consequência de gravar `escolhidaEm`: `ConsultarServicoUseCase` (ETI-86) só entrega `enderecoCompletoSnapshot` e o telefone do Empregador a quem tem a candidatura escolhida; demais candidatos recebem 404 `SERVICO_NAO_ENCONTRADO`. Nenhuma coluna ou DTO novo. Não existe campo WhatsApp: o telefone vem de `Usuario.telefone` pela porta `ConsultaDadosUsuario`. |
| Atomicidade | Tudo em um `@Transactional`; qualquer falha, inclusive depois de a cota ser consumida, desfaz tudo (cota, candidaturas e evento). |

## Contrato da API

| Operação | Rota | Entrada | Saída |
|---|---|---|---|
| Escolher prestador | `POST /anuncios/{anuncioId}/candidaturas/{candidaturaId}/escolher` | sem corpo | `200 { anuncioId }` |

Mesmo padrão de `.../recusar` (ETI-67): sub-recurso por `POST`, id do usuário vem do token.

Erros: 401 sem token; 404 `ANUNCIO_NAO_ENCONTRADO`; 404 `CANDIDATURA_NAO_ENCONTRADA`; 409 `ANUNCIO_ESTADO_INVALIDO`; 409
`CANDIDATURA_ESTADO_INVALIDO`; 409 `ESCOLHA_CONFLITO` (outro candidato já foi escolhido, inclusive por uma requisição
concorrente); 409 `COTA_ESGOTADA_CANDIDATO`.

`CodigoErro` novos: `ESCOLHA_CONFLITO` e `COTA_ESGOTADA_CANDIDATO`.

## Concorrência

O mapeamento das travas existentes mostrou quatro mecanismos que não se enxergam: o advisory lock `ANUNCIO` (só a
recusa o usava), o `FOR UPDATE` na linha do `Anuncio` (só iniciar o serviço), o `FOR UPDATE` na linha da `Aplicacao`
(só desistir) e os índices únicos. Advisory lock e `FOR UPDATE` não se bloqueiam no Postgres: o advisory lock só
serializa quem também o toma, o `FOR UPDATE` serializa qualquer escritor da mesma linha. Por isso não são
intercambiáveis, e a escolha usa os dois em ordem fixa, sem criar mecanismo novo.

**Ordem única de aquisição** (documentada em `EscopoTrava`): `ANUNCIO` → linhas de `aplicacao` (`FOR UPDATE`, por id
crescente) → `COTA_PRESTADOR`. `FOTOS_ANUNCIO` continua depois de `ANUNCIO`.

1. **Advisory lock `ANUNCIO`**, primeira instrução, antes de ler qualquer estado. Duas escolhas, ou escolha contra
   recusa, desistência, candidatura, cancelamento e início do serviço, entram em fila e a seguinte vê o estado já
   commitado. A segunda escolha espera, relê, encontra a escolhida e recebe 409.
2. **`FOR UPDATE` nas candidaturas `ENVIADA` do anúncio** (`AplicacaoRepository.findEnviadasDoAnuncioParaAtualizar`).
   Cobre quem não toma o advisory lock (por exemplo o encerramento por exclusão de conta) e lê a candidatura alvo
   já fresca.
3. **`COTA_PRESTADOR`** (advisory lock por Prestador, novo escopo do mesmo `TravaTransacional`), dentro da porta de
   consumo. O `ANUNCIO` é por anúncio e não protege o mesmo Prestador escolhido em dois anúncios ao mesmo tempo; este
   enfileira só essas escolhas, sem ciclo de espera. Não foi usado `FOR UPDATE` em `cota_mensal` porque a linha do mês
   pode não existir ainda (linha inexistente não se trava).

**Alinhamento dos outros fluxos** (necessário para a ordem valer):

- `DesistirCandidaturaUseCase`: toma `ANUNCIO` e só depois trava a linha da candidatura. Antes só travava a linha e
  podia sobrescrever uma recusa ou escolha em andamento. O `anuncioId` é lido antes por consulta **escalar**
  (`AplicacaoService.buscarAnuncioId`): carregar a entidade antes da trava a deixaria gerenciada, e o `FOR UPDATE`
  seguinte devolveria a cópia velha em vez do estado commitado (bug encontrado pelo teste de corrida desistência x
  recusa, que respondia 200 nas duas).
- `CandidatarSeUseCase`: toma `ANUNCIO` antes de ler o anúncio; sem isso a candidatura nascia `ENVIADA` depois de a
  escolha encerrar as demais e ficava órfã.
- `CancelarAnuncioUseCase`: toma `ANUNCIO` antes de ler o anúncio e a existência de escolhida.
- `IniciarServicoUseCase`: trocou o `FOR UPDATE` da linha do anúncio pelo advisory lock; o `AnuncioRepository` e o
  `AnuncioService` perderam `findByIdParaAtualizar`/`buscarParaAtualizar`, que ficaram sem uso.

**Limitações conhecidas** (fora do escopo): não há `lock_timeout`, então a espera por uma trava é indefinida e uma
contenção forte pode esgotar o pool de conexões; `hashtext` pode colidir (aceito no javadoc de `TravaTransacional`);
`GlobalExceptionHandler` não mapeia exceções de lock ou deadlock do Postgres (viram 500).

## Decisões técnicas

- **Porta de ESCRITA da cota (`comum/portas/ConsumoCotaPrestador`).** `cota_mensal` é do `perfil` e o `anuncio` não
  pode importá-lo. Todas as portas até aqui eram só de consulta; esta muda estado, de propósito: o consumo precisa
  estar **na mesma transação** da escolha (a cota só sobe se a candidatura virar `ESCOLHIDA`, e volta junto num
  rollback), o que um evento `AFTER_COMMIT` não garante. É a exceção ao "portas só leitura" da ADR-0003 e fica
  registrada aqui, no javadoc da porta e nos CHANGELOGs; **a ADR-0003 não foi alterada**. A implementação
  (`perfil/actions/service/ConsumoCotaPrestadorService`) usa `Propagation.MANDATORY`: fora da transação da escolha,
  falha. A regra está em `ConsumirCotaDoMesUseCase`.
- **Alternativa descartada: contar aplicações `ESCOLHIDA`** (como o `CandidatarSe` faz) sem gravar em `cota_mensal`.
  Não cumpre o requisito de incrementar `escolhasConsumidas`, e a tela "Cota do mês" (ETI-79) lê essa tabela.
- **`cotaUsadaMes` não existe no código**; só `CotaMensal.escolhasConsumidas`. A task citava o nome inexistente.
- **Duas fontes de cota continuam divergentes** (follow-up): `CandidatarSeUseCase.cotaEsgotada` conta aplicações por
  `escolhidaEm` com `anuncio.candidatura.max-escolhas-gratuitas` e ignora o GOLD, enquanto a escolha e a tela de cota
  usam `cota_mensal` com `perfil.cota.teto-free`. Após a escolha os dois números batem, mas são dois tetos e dois
  cálculos de mês. Pra fechar: apontar o `CandidatarSe` para a mesma porta de cota.
- **GOLD é stub.** `ConsultaPlanoPrestadorService` (financeiro) sempre devolve `FREE`, e `isGoldAtivo` não existe; a
  escolha usa `planoAtual() == GOLD`. O ramo GOLD só é exercido por `@MockitoBean` até o módulo de assinatura chegar.
- **Nome do candidato só na mensagem.** `NegocioException` e `ErroResponse` não têm campo extra; o nome abreviado
  (`NomeHelper.abreviar`, mesmo critério da lista de candidatos) vai em `COTA_ESGOTADA_CANDIDATO`. Alternativa
  descartada: estender `ErroResponse` com um mapa de dados, que mexeria em `comum/` e em todos os testes que o constroem.
- **`COTA_ESGOTADA_CANDIDATO` é 409**, não 403 como o `COTA_ESGOTADA` do Prestador: o impedimento é o estado do
  candidato, não a permissão de quem pediu.
- **Evento `PrestadorEscolhidoEvent`** (`comum/eventos/`): `anuncioId`, `anuncioTitulo`, `candidaturaEscolhidaId`,
  `prestadorId`, `contratanteId`, `candidaturasNaoEscolhidasIds` (as que a escolha encerrou) e `ocorridoEm`. A lista
  evita o `chat` consultar o `anuncio` e garante que o chat do escolhido não é tocado; as já recusadas ou desistentes
  ficam de fora, seus chats já estão fechados com o motivo próprio. Publicado dentro da transação; o listener roda em
  `AFTER_COMMIT` + `REQUIRES_NEW` (falha do listener só é logada, como em todos os listeners do projeto).
- **Prestador sem perfil de atuação** ao consumir a cota: `IllegalStateException` (500). Quem se candidata já tem
  perfil, e sem ele não há onde gravar (FK de `cota_mensal`).
- **Sem migration:** `escolhida_em`, `encerrada_em`, `motivo_encerramento` e o índice único de escolhida existem desde
  a V13, e `cota_mensal` desde a V27.

## Testes

`anuncio/spec/EscolherPrestadorUseCaseTest`: FREE com cota (transições, `escolhidaEm`, motivo, evento), GOLD sem
consumo, FREE sem cota (nome na mensagem, nada alterado, sem evento), ordem de aquisição (`InOrder`: trava, anúncio,
escolhida, linhas, cota, gravação, evento), segunda escolha, candidatura de outro anúncio e inexistente (404), fora de
`ENVIADA` (409), dono errado, anúncio fora de `PUBLICADO`, falha ao gravar sem evento.

`anuncio/spec/{Desistir,Candidatar,Cancelar,Iniciar}...UseCaseTest`: a trava `ANUNCIO` vem antes de qualquer leitura.

`perfil/spec/ConsumirCotaDoMesUseCaseTest` e `CotaMensalTest`: criação da linha do mês, saldo, esgotada, virada de
mês, sem perfil, `consumirEscolha`.

`chat/spec/DeixarChatSomenteLeituraUseCaseTest`: só os não escolhidos fecham, lista vazia, candidatura sem chat,
primeiro motivo preservado.

`anuncio/story/EscolherPrestadorStoryTest` (Testcontainers), conferindo o **estado final do banco**: FREE e GOLD,
cota esgotada, candidatura recusada preservada, candidatura de outro anúncio (404), segunda escolha (409
`ESCOLHA_CONFLITO`), contato liberado só ao escolhido (404 para os demais), chats após o commit (escolhido aberto,
não escolhidos somente leitura, recusado mantém o motivo), rollback total com falha depois de consumir a cota, escolha
em fila atrás da trava. Corridas com threads reais: duas escolhas no mesmo anúncio (1 escolhida, cota consumida
uma vez); o mesmo Prestador com uma vaga em dois anúncios (cota fecha em 4, nunca 5); escolha x desistência e escolha
x recusa (estado final é o do vencedor, cota coerente); candidatura nova durante a escolha (nunca sobra `ENVIADA`
órfã, 5 rodadas).

`perfil/story/ConsumoCotaPrestadorStoryTest`: linha do mês, quinta escolha recusada, virada de mês, rollback sem deixar
a linha, `MANDATORY` fora da transação e 8 consumos simultâneos sem linha prévia (exatamente 4 passam).

`anuncio/story/RecusarCandidatoStoryTest`: desistência espera a trava do anúncio; recusa x desistência simultâneas.
