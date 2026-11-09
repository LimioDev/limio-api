# ETI-107 — Avaliar a outra parte ou pular

Card: [ETI-107](https://kaueamaral96.atlassian.net/browse/ETI-107) · Backlog US53 · card par: [moderacao] [FRONT-END]
Tela "Avaliação" (telas 44, 45, 45b a 45e).

## Regra de negócio

- **Quando:** só com o anúncio `CONCLUIDO` (por validação ou por silêncio), até 7 dias corridos depois da conclusão.
  Antes da conclusão: 409 `SERVICO_NAO_CONCLUIDO`. Depois dos 7 dias: 409 `JANELA_AVALIACAO_ENCERRADA`.
- **Quem:** só as duas partes, o Empregador dono do anúncio e o Prestador escolhido. Para qualquer outra pessoa o
  serviço não existe: 404 `SERVICO_NAO_ENCONTRADO`, a mesma resposta de um id inexistente.
- **Uma vez só:** uma avaliação por pessoa por serviço. A segunda é recusada (409 `AVALIACAO_DUPLICADA`) e não substitui
  a primeira.
- **Nota e comentário:** nota inteira de 1 a 5, obrigatória. Comentário opcional, até 400 caracteres, sem espaços nas
  pontas; em branco conta como ausente. Comentário sem nota: 400 `COMENTARIO_SEM_NOTA`. Sem nota e sem comentário,
  nota fora de 1 a 5 ou comentário longo demais: 400 `VALIDACAO_FALHOU`.
- **Média:** cada avaliação recalcula `mediaAvaliacoes` (uma casa decimal, arredondamento comercial) e `qtdAvaliacoes`
  do avaliado no papel que ele teve no serviço: `perfil_empregador` ou `perfil_prestador`. Ela aparece no perfil assim
  que a primeira nota é gravada, e fica `null` (nunca 0,0) enquanto não há nenhuma.
- **Pular:** não grava nada e não tem rota. O app fecha a tela, e a consulta continua oferecendo a avaliação enquanto a
  janela estiver aberta.
- **Nunca bloqueia:** nenhuma outra ação da plataforma consulta se a pessoa avaliou.

## Contrato

| Operação | Rota | Entrada | Saída |
|---|---|---|---|
| Consultar avaliação pendente | `GET /anuncios/{anuncioId}/avaliacao-pendente` | — | `200 { servico: { id, titulo, concluidoEm }, outraParte: { nome, fotoUrl, fotoExpiraEm, papel }, podeAvaliar, avaliarAte }` |
| Avaliar | `POST /anuncios/{anuncioId}/avaliacoes` | `{ nota, comentario }` | `201 { id, anuncioId, nota, comentario, publicadaEm }` |

- O "serviço" é o anúncio, como no ETI-88: `servico.id` é o id do anúncio.
- `podeAvaliar` é `false` antes da conclusão, a partir de `avaliarAte` e depois que quem consulta já avaliou. Serviço
  não concluído responde 200 na consulta, com `concluidoEm` e `avaliarAte` nulos. Só o `POST` recusa.
- Anúncio ainda sem Prestador escolhido: o Empregador dono consulta e recebe 200 com `outraParte` nulo (não há outro
  lado), além de `concluidoEm` e `avaliarAte` nulos e `podeAvaliar = false`.
- `outraParte.papel`: `PRESTADOR` para o Empregador e `EMPREGADOR` para o Prestador. O Empregador aparece pelo nome de
  exibição do perfil, e o Prestador pelo nome do cadastro, como no cabeçalho do chat. Conta encerrada aparece como
  "Usuário excluído", sem foto.
- `avaliarAte` = `concluidoEm` + 7 dias, exclusivo.

## Decisões técnicas

- **Porta `ConsultaServico` (`comum/portas/`), implementada no `anuncio`:** devolve título, as duas partes e
  `concluidoEm`. O Prestador do serviço é a candidatura em `ESCOLHIDA`, `AGUARDANDO_VALIDACAO` ou `CONCLUIDA`
  (`AplicacaoService.buscarPrestadorDoServico`).
- **`concluidoEm` vem de `anuncio.validado_em`:** o DER não tem coluna de conclusão. **Pendente para o card que
  concluir o serviço por silêncio:** ele precisa gravar `validado_em` (validação tácita) junto com o `CONCLUIDO`. Senão
  o serviço fica sem janela. Hoje nenhuma rota leva o anúncio a `CONCLUIDO`, por isso a story semeia o anúncio pelo
  repository.
- **`papel_avaliado` na tabela, fora do DER:** a média é por papel, e quem atua nos dois papéis teria as notas
  misturadas. Sem essa coluna, o recálculo precisaria cruzar com o `anuncio`.
- **A média é recalculada por quem tem as notas:** `moderacao` soma todas as avaliações do avaliado naquele papel e
  publica `AvaliacaoPublicadaEvent` (`comum/eventos/`) com o total pronto. O `perfil` só grava. Recalcular do zero, e
  não somar a nota nova à média gravada, evita que o arredondamento de `NUMERIC(2,1)` acumule erro.
- **O evento é ouvido na mesma transação** (`@EventListener`, `MANDATORY`), não em `AFTER_COMMIT`: o card pede a média
  no perfil assim que a nota é gravada, e a avaliação e a média entram ou saem juntas. O ADR-0003 só exige
  `AFTER_COMMIT` para efeito externo (e-mail, storage), e aqui só há banco.
- **Concorrência:** um advisory lock (`EscopoTrava.AVALIACOES_RECEBIDAS`, por avaliado e papel) serializa as
  avaliações que a mesma pessoa recebe. Assim duas avaliações simultâneas não gravam cada uma uma média sem a outra. O
  índice único `(anuncio_id, avaliador_id)` segura o clique duplo que passar pela checagem.
- **`@DynamicUpdate` em `PerfilEmpregador` e `PerfilPrestador`:** a média e o nome de exibição (ou a atuação) mudam
  por caminhos diferentes. Com o UPDATE de todas as colunas, salvar o nome de exibição podia regravar a média antiga
  por cima da nova.
- **`NotaObrigatoriaException` no usecase, não `@NotNull` no record:** com comentário, a falta de nota tem código
  próprio (`COMENTARIO_SEM_NOTA`), e as duas regras ficam no mesmo lugar.

## Limitações conhecidas

- **Prestador sem perfil de atuação** (nunca salvou) fica sem a média: `perfil_prestador` não existe sem os dados de
  atuação. A avaliação fica gravada, e a próxima já leva o total certo ao perfil.
- **Média do Prestador ainda não aparece em nenhuma tela:** a coluna é gravada, mas o perfil público do Prestador não
  existe na API. O do Empregador já mostra (`GET /conta/perfil-empregador` e o detalhe do mural).
- Avaliação não se edita nem se apaga. Denúncia de comentário fica para os cards de denúncia.
