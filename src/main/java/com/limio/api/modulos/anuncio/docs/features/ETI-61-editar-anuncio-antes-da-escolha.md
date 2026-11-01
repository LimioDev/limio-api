# ETI-61 — Editar anúncio antes da escolha

## Status

Implementado.

## Link do card

[ETI-61](https://kaueamaral96.atlassian.net/jira/software/projects/ETI/boards/2?selectedIssue=ETI-61) —
[anuncio] [BACK-END] Editar anúncio antes da escolha (UC19, RF26). 

## User Story

Como um Empregador, eu quero que a API aceite correções no meu anúncio enquanto ninguém foi escolhido,
para que ele fique correto para os candidatos.

## Escopo implementado

- `PUT /anuncios/{id}`: substitui título, categoria, descrição, data/hora e os opcionais (`tamanhoLocal`,
  `numeroPessoas`, `observacoes`). Devolve o anúncio atualizado, na mesma forma de `GET /anuncios/{id}`.
- `DELETE /anuncios/{id}/fotos/{fotoId}`: remove uma foto do anúncio.
- Adicionar foto **não é novo**: reusa `POST /anuncios/{id}/fotos` (ETI-59), sem alteração de contrato. O
  limite de 5 fotos conta só as ativas, então remover libera vaga.

**Fora**:

- UI (card par `[FRONT-END]`).
- Reagendamento depois da combinação (RF69, UC51/52): hoje todo estado pós-escolha cai em
  `ANUNCIO_ESTADO_INVALIDO`.
- Evento `AnuncioEditado` (ver "Pontos em aberto").
- Edição do endereço.
- O usecase de escolher candidato.

## Decisões

**Sem `USE_REAGENDAMENTO`.** O card sugere esse código, que não existe, e o reagendamento (RF69) também não.
Anúncio combinado, em andamento, concluído ou encerrado responde `ANUNCIO_ESTADO_INVALIDO` (409), igual ao
cancelar e ao anexar fotos. `DADOS_INVALIDOS` do card é o `VALIDACAO_FALHOU` que já existe (400).

**Sem endereço na edição.** O card lista título, descrição, data, hora, categoria, fotos e opcionais. Editar o
endereço exigiria geocodificar de novo e refazer o retrato (`*Snapshot`); fica pra um card próprio.

**Data validada sempre.** `PUT` substitui os campos, então a data enviada tem que estar no futuro mesmo se não
mudou. Consequência: anúncio com data já vencida não aceita edição de outro campo. Mesma regra da criação.

**Validação compartilhada com a criação.** `EditarAnuncioRequest` repete as anotações de `CriarAnuncioRequest`
(record não herda anotação) com as mesmas chaves de `ValidationMessages.properties`. Data futura e categoria
ativa moram em `ValidacaoDoAnuncioService`, usado por `PublicarAnuncioUseCase` e `EditarAnuncioUseCase`.

**Sem evento ao editar.** `AnuncioPublicadoEvent` e `AvisoDeAnuncioEvent` já existem sem nenhum ouvinte, e o
módulo `notificacao` não envia nada. Publicar `AnuncioEditado` agora seria código sem consumidor.

**Concorrência com a escolha: trava, sem `@Version`.** Ver abaixo.

## Regra de negócio

| Critério do card | Implementação |
|---|---|
| Só em `PUBLICADO` (`AguardandoCandidatos`) sem candidatura `ESCOLHIDA` | `Anuncio.permiteAlteracao(AplicacaoService.existeEscolhida)`, a regra única de anexar fotos, cancelar e editar. `AnuncioEstadoInvalidoException` (409). |
| Só o dono edita; de outra pessoa é 404 | `buscarPorId(...).filter(pertenceA)`: inexistente e de outro caem no mesmo `ANUNCIO_NAO_ENCONTRADO`. |
| Título 5–80, descrição ≤ 600, opcionais | Bean Validation em `EditarAnuncioRequest`; 400 `VALIDACAO_FALHOU`. |
| Data/hora no futuro | `ValidacaoDoAnuncioService.exigirDataFutura`; 422 `DATA_NO_PASSADO`. |
| Categoria ativa | `ValidacaoDoAnuncioService.exigirCategoriaAtiva`; 422 `CATEGORIA_INVALIDA`. |
| Remover foto: `Midia` sai, arquivo sai, `expurgoEm` preenchido | `Midia.expurgar` na transação; objeto apagado do bucket em `AFTER_COMMIT`. |
| Mural mostra a nova versão | Não há mural, query nem índice no código. A edição grava na mesma tabela `anuncio`, então qualquer leitura futura já vê a versão nova. Nada a atualizar. |

Ordem no `EditarAnuncioUseCase`: data futura e categoria ativa (sem trava) → trava `ANUNCIO` → dono → estado →
grava. Um terceiro com payload inválido recebe 400/422 antes do 404; isso não revela se o anúncio existe,
porque essas validações não consultam o anúncio.

## Concorrência: editar × escolher

Hoje nada grava `ESCOLHIDA`: o usecase de escolher candidato (ETI-69/70) não existe, e não há `@Lock` nem
`@Version` em `anuncio`. A única proteção existente é o índice parcial `idx_aplicacao_escolhida_unica` (V13),
que só impede duas escolhidas. Esta ETI define o contrato de trava:

- Novo `EscopoTrava.ANUNCIO` (`TravaTransacional`, advisory lock por id do anúncio, solto no fim da transação).
- `EditarAnuncioUseCase`, `RemoverFotoAnuncioUseCase` e `AnexarFotosAnuncioUseCase` pegam a trava **antes** de
  ler o estado.
- Quando o usecase também pega `FOTOS_ANUNCIO`, a ordem é sempre `ANUNCIO` e depois `FOTOS_ANUNCIO`.
- **O usecase de escolher candidato deve pegar `EscopoTrava.ANUNCIO` antes de gravar `ESCOLHIDA`.** Sem isso a
  garantia abaixo não vale.

Resultado: se a escolha confirma primeiro, a edição lê `existeEscolhida = true` e responde 409, sem
sobrescrever nada; se a edição confirma primeiro, a escolha enxerga os dados novos. `@Version` foi descartado:
a escolha altera `aplicacao`, não `anuncio`, então o lock otimista não pegaria a corrida e exigiria migration.

## Fotos e storage

- **Adicionar:** `POST /anuncios/{id}/fotos`, sem mudança. Agora pega a trava `ANUNCIO` antes da de fotos.
- **Remover:** `RemoverFotoAnuncioUseCase`. Mesmas checagens de dono e estado. A foto precisa estar ativa e ser
  daquele anúncio; senão 404 `ENTIDADE_NAO_ENCONTRADA` (`FotoNaoEncontradaException`), sem revelar foto de
  outro anúncio. Expurga com `Midia.expurgar` (mesmo padrão de `TrocarFotoPerfilUseCase`) e publica
  `FotoAnuncioRemovidaEvent(chave)`; `FotoAnuncioEventosService` apaga o objeto em `AFTER_COMMIT`.
- **Consistência banco × storage:** se o commit falha, o objeto não é apagado. Se o commit passa e o storage
  falha, o objeto fica órfão no bucket: `ArmazenamentoService.remover` só loga. Não há job que varra
  `expurgoEm`, e esta ETI não cria um. Risco aceito, igual ao da foto de perfil.

## Contrato da API

| Operação | Rota | Entrada | Saída |
|---|---|---|---|
| Editar anúncio | `PUT /anuncios/{id}` | `EditarAnuncioRequest`: `categoriaId`, `titulo`, `descricao`, `dataHoraPretendida`, `tamanhoLocal`, `numeroPessoas`, `observacoes` | 200, `MeuAnuncioResponse` |
| Remover foto | `DELETE /anuncios/{id}/fotos/{fotoId}` | ids no path | 204, sem corpo |

Erros: 400 `VALIDACAO_FALHOU`, 401 `NAO_AUTENTICADO`, 404 `ANUNCIO_NAO_ENCONTRADO`, 409 `ANUNCIO_ESTADO_INVALIDO`,
422 `DATA_NO_PASSADO` e `CATEGORIA_INVALIDA`; na remoção, 404 `ENTIDADE_NAO_ENCONTRADA` para foto inexistente.
**Nenhum `CodigoErro` novo.** Opcional não enviado no `PUT` é limpo (substituição, não patch).

## Pontos em aberto (decisão do PO)

- `USE_REAGENDAMENTO`: o 409 deve distinguir "mudou data/hora depois da combinação"? Depende do RF69.
- `AnuncioEditado`: avisar os candidatos? Precisa de um consumidor (módulo `notificacao`) antes de existir.
- Edição do endereço do anúncio.
- Data inalterada que já passou: bloquear a edição (como está) ou só validar a data quando ela muda?

## Testes

`spec/EditarAnuncioUseCaseTest`: sucesso (campos trocados, opcional limpo, o resto intacto), ordem trava →
leitura de estado, candidatura escolhida, cada status diferente de `PUBLICADO`, dono errado, inexistente, data
no passado e categoria inativa/inexistente (ambos antes da trava).

`spec/RemoverFotoAnuncioUseCaseTest`: sucesso (expurga e publica o evento), ordem das travas, escolhida, cada
status diferente de `PUBLICADO`, dono errado, inexistente, foto inexistente. `spec/AnexarFotosAnuncioUseCaseTest`
ganhou a ordem das travas.

`story/EditarAnuncioStoryTest`: os quatro cenários do card (sucesso, escolhida, data no passado, anúncio de
outra pessoa), mais categoria inativa, título curto, anúncio encerrado, inexistente, 401 e a corrida: uma
"escolha" segura a trava `ANUNCIO` e grava `ESCOLHIDA`, a edição espera e recebe 409 sem alterar nada. A escolha é
simulada porque o usecase real não existe.

`story/RemoverFotoAnuncioStoryTest`: 204 com `expurgoEm` e `remover` no storage só da foto certa, foto já
removida, foto de outro anúncio do mesmo dono, escolhida (409, foto intacta), dono errado e 401.
