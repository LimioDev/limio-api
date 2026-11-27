# ETI-71 — Consultar histórico de anúncios e serviços com filtros

## Status

Implementado. Dúvidas ainda abertas com o produto ao final deste documento.

## Link do card

ETI-71 — [BACK-END] Consultar histórico de anúncios e serviços com filtros (UC23).

## User Story

Como Empregador ou Prestador, eu quero consultar o histórico dos meus anúncios e serviços, filtrando por status e
período, para acompanhar o que já foi concluído, cancelado ou expirou e avaliar a outra parte.

## Contrato da API

`GET /anuncios/historico` — autenticado. O papel ativo do token escolhe a visão; não há id na rota nem na query
(parâmetros como `usuarioId` são ignorados).

| Parâmetro | Tipo | Regra |
|---|---|---|
| `status` | `CONCLUIDO`, `CANCELADO` ou `EXPIRADO` | opcional; ausente = os três |
| `dataInicio` | `yyyy-MM-dd` | opcional; dia de Brasília, incluso |
| `dataFim` | `yyyy-MM-dd` | opcional; dia de Brasília, incluso; `dataInicio` não pode vir depois dele (mesmo dia vale) |
| `pagina` | inteiro | opcional; a partir de 0, padrão 0 |
| `tamanho` | inteiro | opcional; de 1 a 50, padrão 20 |

Saída: `PageResponse<HistoricoItemResponse>`.

```json
{
  "content": [
    { "anuncioId": "6f1c…", "titulo": "Limpeza pós-obra", "data": "2026-10-12T17:40:00Z",
      "status": "CONCLUIDO", "outraParte": "Maria Souza Lima",
      "avaliacao": { "notaRecebida": 5, "podeAvaliar": false } },
    { "anuncioId": "9a2e…", "titulo": "Faxina", "data": "2026-10-03T12:00:00Z",
      "status": "EXPIRADO", "outraParte": null, "avaliacao": null }
  ],
  "pagina": 0, "tamanho": 20, "totalElementos": 2, "totalPaginas": 1
}
```

- `data`: o desfecho, `validadoEm` (concluído) ou `encerradoEm` (cancelado/expirado). Ordem: desfecho decrescente, depois `id`.
- `outraParte`: nome da contraparte; `null` se ninguém foi escolhido (Empregador: cancelado antes da escolha ou expirado).
- `avaliacao`: só em `CONCLUIDO` do qual quem consulta participou, senão `null`. `notaRecebida` é `null` se a outra
  parte não avaliou; `podeAvaliar` é `true` com a janela de 7 dias aberta e sem avaliação dele.
- Sem resultado: 200 com `content` vazio. Erros: 400 `VALIDACAO_FALHOU` (status ou data inválidos, período invertido,
  paginação) e 401 sem token. Não existe 403/404, pois não há recurso por id.
- O item não tem telefone, e-mail, endereço nem coordenadas: não há contato a vazar pela resposta.

## Regra de negócio

| Regra | Implementação |
|---|---|
| Cancelado × expirado | Ambos são `ENCERRADO`; a nova coluna `anuncio.causa_encerramento` (`CANCELAMENTO`/`EXPIRACAO`, V41, com backfill pelo motivo) os separa. `StatusHistorico.de(status, causa)` junta os dois. Saída de conta, cancelamento pelo Empregador e cancelamento do serviço combinado gravam `CANCELAMENTO`. |
| Empregador | `AnuncioRepository.buscarHistoricoDoContratante`: `contratanteId` do token. |
| Prestador | `buscarHistoricoDoPrestador`: `EXISTS` de candidatura dele no anúncio (não join: ele pode ter várias, e o anúncio aparece uma vez). |
| Período | `PeriodoHistoricoHelper`: `[dataInicio 00:00, dataFim+1 00:00)` em `America/Sao_Paulo`, sobre `coalesce(validadoEm, encerradoEm)`. |
| Contraparte | Escolhido vê o nome completo do outro; Prestador não escolhido vê o Empregador abreviado ("Maria S."); conta encerrada = "Conta encerrada". Com dois escolhidos no mesmo anúncio (um desistiu), vale o mais recente, nas duas visões: o anterior conta como não escolhido (nome abreviado, sem avaliação). |
| Nota e janela de 7 dias | Porta `ConsultaAvaliacoes.situacaoPorServico` (`comum/portas`, `SituacaoAvaliacao`); a regra segue só em `moderacao` (`JanelaAvaliacaoHelper`). |
| Sem N+1 | Por página: anúncios (+ count), candidaturas, nomes e duas consultas de avaliação, todas em lote. |

Camadas: `HistoricoController` → `ListarHistoricoUseCase` → `AnuncioService`/`AplicacaoService` e portas;
`HistoricoMapper` só no controller; o usecase devolve `Page<ItemHistorico>`.

## Diferença para `GET /candidaturas/minhas?aba=HISTORICO` (ETI-77)

Aquela lista candidaturas do Prestador (uma linha por candidatura). Esta lista anúncios em estado final, para os dois
papéis, uma linha por anúncio.

## Migration

`V41__adicionar_causa_encerramento_ao_anuncio.sql`: coluna `causa_encerramento VARCHAR(20)`, backfill
(`'Expirado sem candidatos'` → `EXPIRACAO`, demais `ENCERRADO` → `CANCELAMENTO`), CHECK `ck_anuncio_causa_encerramento` (valores) e CHECK
`ck_anuncio_causa_so_no_encerrado` (só `ENCERRADO` tem causa, e todo `ENCERRADO` tem).

## Dúvidas em aberto

- **Prestador não escolhido em anúncio concluído por outro**: hoje aparece como `CONCLUIDO`, sem avaliação e com o
  Empregador abreviado, o que pode sugerir que ele fez o serviço. Alternativas: campo `escolhido` ou ocultar.
- **Concluído por silêncio** (ETI-107): a validação tácita ainda não grava `validadoEm`; esses serviços saem sem data
  de conclusão (usa `encerradoEm`, que pode ser nulo) e sem janela de avaliação.
- Campo de data do filtro (`coalesce(validadoEm, encerradoEm)`, não `dataHoraPretendida`) e código de erro
  (`VALIDACAO_FALHOU`, não `DADOS_INVALIDOS`, que não existe) seguem as recomendações do plano, sem confirmação do produto.
- Cancelamento por saída de conta aparece como `CANCELADO` também para a contraparte.
- Não há `avaliarAte` nem comentário da avaliação recebida.

## Testes

- `spec/StatusHistoricoTest`, `PeriodoHistoricoHelperTest`, `CriterioDoHistoricoTest`, `HistoricoFiltroTest`,
  `HistoricoMapperTest`, `ListarHistoricoUseCaseTest`; em `moderacao`, `ConsultaAvaliacoesServiceTest`; em `Anuncio`,
  `ExpiracaoDominioTest` e `CancelamentoServicoDominioTest` (causa gravada).
- `story/ConsultasDoHistoricoStoryTest`: queries e finders em lote com banco real.
- `story/HistoricoAnunciosStoryTest`: ponta a ponta com login real (status, período e fuso, papéis, anti-IDOR,
  avaliação e janela de 7 dias, validação 400, paginação).
