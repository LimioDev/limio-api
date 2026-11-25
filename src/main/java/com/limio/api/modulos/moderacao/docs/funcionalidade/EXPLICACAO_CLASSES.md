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

**Porta `ConsultaAvaliacoes` (ETI-105, perfil público em `perfil`)**
`actions/service/ConsultaAvaliacoesService` implementa a porta de `comum/portas/`: `resumir` (lote, só papel
`PRESTADOR`, via `AvaliacaoRepository.resumirPorAvaliados`/`ResumoAvaliacaoProjecao` — consumida também por
`ListarCandidatosUseCase`, ETI-67) e `listarRecentes` (top-N por avaliado+papel, nome do avaliador abreviado por
`comum/texto/NomeHelper`; conta encerrada do avaliador aparece como "Conta encerrada", nunca cai da lista).

**`POST /denuncias` (ETI-114)**
`DenunciaController.registrar` (`RegistrarDenunciaRequest` validado como campos de formulário, parte `foto` opcional) →
`DenunciaMapper.toAlvo` → `RegistrarDenunciaUseCase` (resolve o alvo pela porta certa: `ConsultaChat.buscarMensagem`,
`ConsultaCandidatura`, `ConsultaServico` ou `ConsultaDadosUsuario`; recusa com `AlvoNaoAcessivelException`; a foto passa
por `ImagemHelper`, vai pro storage, publica `EvidenciaDenunciaEnviadaEvent` e vira `Midia.evidenciaDenuncia`;
`Denuncia.registrar` + `DenunciaService.salvarAgora`) → `DenunciaMapper.toResponse` → `DenunciaRegistradaResponse`.

**`GET /admin/denuncias` (ETI-116, só administrador)**
`ModeracaoAdminController.listarFila` → `ListarFilaDeDenunciasUseCase` (motivos do filtro, com `Severidade.motivos()`;
`DenunciaService.buscarAbertas` ordenado por prazo; nomes por `ConsultaContasParaModeracao`) → `ItemDaFila` →
`ModeracaoAdminMapper` → `PageResponse<FilaDenunciaItemResponse>`.

**`GET /admin/denuncias/{id}` (ETI-116)**
`ModeracaoAdminController.analisar` → `AnalisarDenunciaUseCase` (`ConsultaServico` para o título e o tipo do alvo;
`ConsultaContasParaModeracao`; evidência e fotos do check-out por `MidiaService` + `ArmazenamentoService`; chat por
`ConsultaChat.listarMensagensOriginais`/`listarMensagensOriginaisDoServico`; histórico por
`DenunciaService.buscarOutrasContraODenunciado` e `DecisaoModeracaoService.buscarSancoesDoUsuario`) →
`AnaliseDaDenuncia` → `ModeracaoAdminMapper` → `AnaliseDenunciaResponse`.

**`POST /admin/denuncias/{id}/decisao` (ETI-116)**
`ModeracaoAdminController.decidir` (`DecidirDenunciaRequest` validado; `ModeracaoAdminMapper.toPedido`) →
`DecidirDenunciaUseCase` (trava a denúncia; 409 se já decidida; coerência do pedido; `DecisaoModeracao.registrar` +
`Denuncia.resolver`; sanção: `ADVERTENCIA` só grava, `OCULTAR_AVALIACAO` oculta e publica `AvaliacaoOcultadaEvent`,
`SUSPENSAO` publica `ContaSuspensaEvent`, `ENCERRAMENTO_DE_CONTA` publica `EncerramentoDeContaDecididoEvent`;
`FotoPerfilRemovidaPelaModeracaoEvent` se pedido; `LogAuditoria` `DENUNCIA_DECIDIDA`) → `DenunciaDecidida` →
`ModeracaoAdminMapper` → `DenunciaDecididaResponse`.

## Classes

| Classe | Papel |
|---|---|
| `avaliacao/Avaliacao` | Entity da tabela `avaliacao`. Imutável depois de publicada; `publicar(...)` é a única forma de criar. A moderação só a oculta (`ocultar`, ETI-116). |
| `avaliacao/AvaliacaoRepository` | Duplicata por anúncio e avaliador, contagem e média por avaliado e papel. |
| `avaliacao/AvaliacaoService` | Persistência: `salvarNova` (índice único vira `AvaliacaoDuplicadaException`), `travarRecebidas` (advisory lock) e `recalcular`. |
| `avaliacao/AvaliacoesRecebidas` | Total de um usuário num papel: média com uma casa e quantidade. |
| `AvaliacaoPendente`, `OutraParte` | O que a tela "Avaliação" precisa, antes de virar contrato HTTP. |
| `actions/helper/JanelaAvaliacaoHelper` | Os 7 dias depois da conclusão: `avaliarAte` e `aberta`. |
| `actions/usecase/ConsultarAvaliacaoPendenteUseCase` | Consulta da tela; serviço não concluído não é erro, só `podeAvaliar = false`. |
| `actions/usecase/AvaliarUseCase` | Regras do UC "Avaliar a outra parte" e publicação do evento com o total. |
| `actions/controller/AvaliacaoController` | As duas rotas. Sem checagem de papel: o acesso é ser uma das partes. |
| `actions/mapper/AvaliacaoMapper` | `AvaliacaoPendente` e `Avaliacao` para os records de resposta. |
| `excecao/*` | Uma por cenário: `DenunciaNaoEncontrada` (404), `DenunciaJaResolvida` (409) e `DecisaoInvalida` (400) da decisão; `AlvoNaoAcessivel` (404, denúncia), `ServicoNaoEncontrado` (404), `ServicoNaoConcluido`, `JanelaAvaliacaoEncerrada` e `AvaliacaoDuplicada` (409), `ComentarioSemNota` e `NotaObrigatoria` (400). |
| `actions/service/ConsultaAvaliacoesService` | Implementa `comum/portas/ConsultaAvaliacoes` (ETI-105): resumo em lote e avaliações recentes, lidas direto de `Avaliacao`. |
| `denuncia/Denuncia` | Entity da tabela `denuncia`. `registrar(...)` é a única forma de criar; `numero` vem do banco. |
| `denuncia/AlvoDenuncia` | Denunciado e contexto (anúncio, candidatura, mensagem), como o cliente mandou ou já resolvido. |
| `denuncia/DenunciaRepository`, `denuncia/DenunciaService` | Persistência; `salvarAgora` faz o flush pra o `numero` sair na resposta; `buscarEmAbertoIgual` acha a mesma denúncia ainda em aberto (`Denuncia.repete`). |
| `enums/MotivoDenuncia`, `enums/StatusResolucao` | Os cinco motivos da tela 46 e o andamento da denúncia (`ABERTA`, `PROCEDENTE`, `IMPROCEDENTE`). |
| `denuncia/DecisaoModeracao`, `denuncia/Sancao` | Entities das tabelas `decisao_moderacao` e `sancao` (ETI-116). Imutáveis; `registrar` e `aplicar` são a única forma de criar. |
| `denuncia/DecisaoModeracaoService` | Persistência da decisão e das sanções dela; histórico de sanções do usuário. |
| `enums/VereditoModeracao`, `enums/TipoSancao` | Veredito do administrador e os quatro tipos de sanção. |
| `enums/Severidade` | Severidade da fila, deduzida do motivo. |
| `enums/TipoAlvoDenuncia` | O que foi denunciado, deduzido do contexto gravado. |
| `ItemDaFila`, `AnaliseDaDenuncia`, `PedidoDeDecisao`, `DenunciaDecidida` | Tipos de domínio da fila, da análise e da decisão, antes do contrato HTTP. |
| `actions/usecase/ListarFilaDeDenunciasUseCase`, `AnalisarDenunciaUseCase`, `DecidirDenunciaUseCase` | Fila, análise e decisão do administrador (ETI-116). |
| `actions/controller/ModeracaoAdminController` | `/admin/denuncias/**`, só administrador (regra no `SecurityConfig`). |
| `actions/mapper/ModeracaoAdminMapper` | Domínio ↔ records da fila, da análise e da decisão. |
| `actions/service/ConsultaTotaisDeDenunciasService` | Implementa `ConsultaTotaisDeDenuncias` (dashboard, ETI-117). |
| `actions/usecase/RegistrarDenunciaUseCase` | Regras do UC "Registrar denúncia": quem pode denunciar o quê, evidência e prazo de análise. |
| `actions/controller/DenunciaController` | `POST /denuncias`, `multipart/form-data`. Sem checagem de papel: o acesso é decidido pelo alvo. |
| `actions/mapper/DenunciaMapper` | Request para `AlvoDenuncia` e `Denuncia` para a resposta. |
| `actions/service/EvidenciaDenunciaEventosService` | Apaga do storage a foto de uma denúncia cuja transação desfez (`AFTER_ROLLBACK`). |
| `eventos/EvidenciaDenunciaEnviadaEvent` | Evento interno: chave da foto enviada, pra limpeza no rollback. |
| `avaliacao/ResumoAvaliacaoProjecao` | Projeção de `resumirPorAvaliados` — id, média e quantidade, direto do banco, sem carregar a entity. |

Fora do módulo: a porta `comum/portas/ConsultaServico` (implementada por `anuncio/actions/service/ConsultaServicoService`)
e o evento `comum/eventos/AvaliacaoPublicadaEvent` (ouvido por `perfil/actions/usecase/RegistrarMediaAvaliacoesUseCase`).
`comum/portas/ConsultaAvaliacoes` também é consumida por `anuncio/actions/usecase/ListarCandidatosUseCase` (ETI-67)
e `perfil/actions/usecase/ConsultarPerfilPublicoUseCase` (ETI-105).
