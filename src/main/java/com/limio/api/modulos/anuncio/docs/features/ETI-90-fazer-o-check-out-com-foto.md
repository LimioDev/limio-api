# ETI-90 — Fazer o check-out com foto

## Status

Implementado.

## Link do card

ETI-90 — [anuncio] [BACK-END] Fazer o check-out com foto. Origem: UC40 (sem link do Jira neste repositório).

## User Story

Como Prestador escolhido, eu quero que a API receba as fotos do serviço pronto, para que o Empregador possa
validar o serviço à distância.

## Escopo implementado

`POST /anuncios/{anuncioId}/checkout`, multipart com `arquivos` (uma ou mais fotos) e `observacao` (opcional).
As fotos viram `Midia` do tipo `EVIDENCIA_CHECKOUT`, o anúncio vai para `AGUARDANDO_VALIDACAO` com `checkoutEm`
preenchido, a candidatura do Prestador vai para `AGUARDANDO_VALIDACAO` e `CheckoutEnviadoEvent` é publicado.
O prazo do Empregador é `checkoutEm + prazoValidacaoHoras`.

Migrations: `V30__permitir_evidencia_de_checkout_em_midia.sql` (constraint e índice de `midia`) e
`V31__adicionar_observacao_de_checkout_ao_anuncio.sql` (coluna `anuncio.observacao_checkout`).

**Fora**: validação do Empregador (ETI-92/93), conclusão automática por silêncio (ETI-98), aviso ao
Empregador (ETI-112, só o evento é publicado aqui), UI. O Prestador nunca marca o serviço como `CONCLUIDO`.

## Decisões

- **"Combinado" do UC40 é `PUBLICADO` com candidatura escolhida.** Não existe status `COMBINADO` em
  `StatusAnuncio`. O check-out parte de `EM_ANDAMENTO` ou de `PUBLICADO`, já que iniciar o serviço (ETI-88) é
  opcional. A máquina de estados não mudou: só ganhou a transição de saída.
- **Não escolhido recebe 404.** Mesmo critério do ETI-88: anúncio inexistente, Empregador dono, outro
  Prestador e candidatura que não está mais escolhida recebem `SERVICO_NAO_ENCONTRADO`. A checagem vem antes de
  qualquer conferência de foto, então um terceiro sem foto também recebe 404, não 422.
- **Ordem das recusas:** 404 (quem é), 409 (estado), 422 (fotos).
- **`FOTO_OBRIGATORIA` para lista vazia.** Código novo, só do check-out. Foto de perfil e fotos do anúncio
  continuam respondendo `FOTO_INVALIDA` quando vazias. Foto em formato ou tamanho inválido segue
  `FOTO_INVALIDA`. A parte `arquivos` não é obrigatória no Spring para a falta de foto virar 422 e não um 400
  genérico.
- **Mesmas regras de foto do anúncio.** Reaproveita `ImagemHelper` (JPEG/PNG pelo conteúdo, EXIF removido,
  lado máximo 2048) e `anuncio.foto.tamanho-maximo`/`anuncio.foto.quantidade-maxima`. Nenhum limite novo.
  Esses limites ainda dependem do PO.
- **Tudo ou nada.** Todas as fotos são conferidas antes do primeiro upload. Se algo falhar depois, a transação
  desfaz e os objetos já enviados saem do bucket por `FotoAnuncioEnviadaEvent`, reaproveitado das fotos do
  anúncio porque a limpeza é idêntica.
- **Candidatura acompanha o anúncio.** Vai para `AGUARDANDO_VALIDACAO` (valor que já existia em
  `StatusAplicacao` e que `isEscolhida()` já cobre). O horário fica só em `Anuncio.checkoutEm`.
- **Observação em coluna nova.** O card não diz onde guardar. `anuncio.observacao_checkout` é `TEXT`, sem
  limite definido pelo PO, e fica `null` quando vazia ou só espaços.
- **Prazo é derivado.** `Anuncio.prazoDeValidacao()` soma `prazoValidacaoHoras`, retrato gravado na
  publicação. Sem coluna nova. Anúncio sem prazo gravado devolve `prazoValidacaoEm` nulo, sem fallback.
- **Trava no anúncio contra clique duplo.** `TravaTransacional` com `EscopoTrava.ANUNCIO` (advisory lock por id
  do anúncio, solto no fim da transação), tomada antes de ler o anúncio. A segunda requisição espera o lock, vê
  `AGUARDANDO_VALIDACAO` e recebe 409, sem duplicar fotos nem evento.
- **Evento em `comum/eventos/`.** `CheckoutEnviadoEvent` leva `anuncioTitulo` e `contratanteId` soltos, como
  `ServicoIniciadoEvent`. É publicado dentro da transação; o consumidor deve escutar em `AFTER_COMMIT`
  (ADR-0003). Nenhum listener existe até o ETI-112.
- **`midia.anuncio_id` agora vale para dois tipos.** A V30 troca `ck_midia_anuncio_so_em_foto_de_anuncio` por
  `ck_midia_anuncio_so_em_midia_de_anuncio`: obrigatório em `FOTO_ANUNCIO` e `EVIDENCIA_CHECKOUT`, proibido em
  `FOTO_PERFIL`. As consultas por tipo existentes não misturam as evidências.

## Regra de negócio

| Critério | Implementação |
|---|---|
| Só o Prestador da candidatura `ESCOLHIDA` | `AplicacaoService.buscarDoPrestadorNoAnuncio` + `Aplicacao.isEscolhida`; os demais recebem 404 `SERVICO_NAO_ENCONTRADO`. |
| `EM_ANDAMENTO`, ou `PUBLICADO` com escolhida | `Anuncio.permiteCheckout`, `switch` sem `default`; fora disso, 409 `SERVICO_ESTADO_INVALIDO`. |
| Ao menos uma foto | Lista vazia: 422 `FOTO_OBRIGATORIA` (`FotoObrigatoriaException`). |
| Tipo e tamanho como as fotos do anúncio | `ImagemHelper` e `anuncio.foto.*`; recusa com 422 `FOTO_INVALIDA`. |
| Fotos como `Midia` `EVIDENCIA_CHECKOUT` com `anuncio_id` | `Midia.evidenciaCheckout`; chave `fotos-checkout/{anuncioId}/{uuid}.ext`. |
| Anúncio vai para `AGUARDANDO_VALIDACAO` com `checkoutEm` | `Anuncio.registrarCheckout(agora, observacao)`. |
| Prazo de validação | `Anuncio.prazoDeValidacao` = `checkoutEm + prazoValidacaoHoras` (padrão 24h). |
| Nunca `CONCLUIDO` | Nenhum caminho do check-out muda `validadoEm` nem conclui. |
| Publica `CheckoutEnviado` | `CheckoutEnviadoEvent`, só em operação bem-sucedida. |

## Contrato da API

| Operação | Rota | Entrada | Saída |
|---|---|---|---|
| Fazer check-out | `POST /anuncios/{anuncioId}/checkout` | multipart: `arquivos` (1 a N), `observacao` (opcional) | 200 `{ "status": "AGUARDANDO_VALIDACAO", "checkoutEm": "<instant>", "prazoValidacaoEm": "<instant>" }` |

Erros: 404 `SERVICO_NAO_ENCONTRADO`, 409 `SERVICO_ESTADO_INVALIDO`, 422 `FOTO_OBRIGATORIA`, 422
`FOTO_INVALIDA`, 503 `ARMAZENAMENTO_INDISPONIVEL`, 401 sem token. `CodigoErro` novo: `FOTO_OBRIGATORIA`.

## Testes

`spec/CheckoutDominioTest`: `permiteCheckout` por status, `registrarCheckout`, observação, prazo e candidatura.

`spec/CheckoutContratoTest`: resposta com e sem prazo e `FotoObrigatoriaException`.

`spec/RealizarCheckoutUseCaseTest`: sucesso em `PUBLICADO` e `EM_ANDAMENTO`, evento de limpeza após o upload,
404, 409 por status, foto obrigatória, foto inválida sem nenhum upload, limite de tamanho e de quantidade,
storage fora do ar e uma evidência por foto.

`story/CheckoutStoryTest`: os cinco cenários do card, prazo de 24h, observação gravada, Empregador dono,
GIF, foto grande, excesso de fotos, storage fora do ar sem alterar o anúncio, clique duplo com duas
requisições paralelas reais e 401 sem token.

`comum/midia/MidiaEvidenciaCheckoutTest`: a V30 aceita a evidência com `anuncio_id`, recusa sem ele e
continua recusando `anuncio_id` em foto de perfil.
