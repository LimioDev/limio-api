# Módulo moderacao — o que cada classe faz

## Fluxos

**`GET /anuncios/{id}/avaliacao-pendente`**
`AvaliacaoController.consultarPendente` → `ConsultarAvaliacaoPendenteUseCase` (`ConsultaServico` confere se quem pede é
parte; a outra parte por `ConsultaPerfilEmpregador.buscarResumoSemCriar` ou `ConsultaDadosUsuario` +
`ConsultaFotoPerfil`; `JanelaAvaliacaoHelper` e `AvaliacaoService.jaAvaliou` decidem `podeAvaliar`) →
`AvaliacaoPendente` → `AvaliacaoMapper` → `AvaliacaoPendenteResponse`.

**`POST /anuncios/{id}/avaliacoes`**
`AvaliacaoController.avaliar` (`AvaliarRequest` validado) → `AvaliarUseCase` (nota e comentário, `ConsultaServico`,
concluído, janela, `AvaliacaoService.travarRecebidas`, duplicata, `Avaliacao.publicar` + `salvarNova`, `recalcular`) →
publica `AvaliacaoPublicadaEvent` → no `perfil`, `RegistrarMediaAvaliacoesUseCase` grava a média na mesma transação →
`AvaliacaoMapper` → `AvaliacaoResponse`.

## Classes

| Classe | Papel |
|---|---|
| `avaliacao/Avaliacao` | Entity da tabela `avaliacao`. Imutável depois de publicada; `publicar(...)` é a única forma de criar. |
| `avaliacao/AvaliacaoRepository` | Duplicata por anúncio e avaliador, contagem e média por avaliado e papel. |
| `avaliacao/AvaliacaoService` | Persistência: `salvarNova` (índice único vira `AvaliacaoDuplicadaException`), `travarRecebidas` (advisory lock) e `recalcular`. |
| `avaliacao/AvaliacoesRecebidas` | Total de um usuário num papel: média com uma casa e quantidade. |
| `AvaliacaoPendente`, `OutraParte` | O que a tela "Avaliação" precisa, antes de virar contrato HTTP. |
| `actions/helper/JanelaAvaliacaoHelper` | Os 7 dias depois da conclusão: `avaliarAte` e `aberta`. |
| `actions/usecase/ConsultarAvaliacaoPendenteUseCase` | Consulta da tela; serviço não concluído não é erro, só `podeAvaliar = false`. |
| `actions/usecase/AvaliarUseCase` | Regras do UC "Avaliar a outra parte" e publicação do evento com o total. |
| `actions/controller/AvaliacaoController` | As duas rotas. Sem checagem de papel: o acesso é ser uma das partes. |
| `actions/mapper/AvaliacaoMapper` | `AvaliacaoPendente` e `Avaliacao` para os records de resposta. |
| `excecao/*` | Uma por cenário: `ServicoNaoEncontrado` (404), `ServicoNaoConcluido`, `JanelaAvaliacaoEncerrada` e `AvaliacaoDuplicada` (409), `ComentarioSemNota` e `NotaObrigatoria` (400). |

Fora do módulo: a porta `comum/portas/ConsultaServico` (implementada por `anuncio/actions/service/ConsultaServicoService`)
e o evento `comum/eventos/AvaliacaoPublicadaEvent` (ouvido por `perfil/actions/usecase/RegistrarMediaAvaliacoesUseCase`).
