# CHANGELOG — módulo moderacao

## 2026-11-03 — ETI-107: avaliação entre as partes

Primeira entidade do módulo, que até aqui só tinha `package-info.java`: `Avaliacao` (`avaliacao/`, tabela da V28).
Fora do DER, a coluna `papel_avaliado`, para a média ser por papel sem consultar o `anuncio`. A média continua no
`perfil`: `perfil_prestador` ganhou `media_avaliacoes` e `qtd_avaliacoes` na V29, com a regra de `perfil_empregador`.
Detalhes e pendências em `docs/features/ETI-107-avaliar-a-outra-parte-ou-pular.md`.

## 2026-11-10 — ETI-105 (perfil público, em `perfil`): `ConsultaAvaliacoes` migra pra este módulo

Antes do ETI-107, `ConsultaAvaliacoes` (avaliações recebidas, usada pelos candidatos do anúncio — ETI-67) era um
stub provisório em `modulos.perfil`: nem módulo nem tabela de avaliação existiam. Com `Avaliacao` real agora aqui,
a implementação (`actions/service/ConsultaAvaliacoesService`) migrou pra este módulo — dono do dado, como já
previa o comentário do stub antigo. Ganhou também `listarRecentes(avaliadoId, papel, limite)`, pro card de
reputação do perfil público (ETI-105): top-N avaliações de um usuário num papel, com o nome do avaliador
abreviado. Avaliador com conta encerrada aparece como "Conta encerrada" em vez de desaparecer da lista.

## 2026-11-19 — ETI-114: registrar denúncia

Segunda entidade do módulo: `Denuncia` (`denuncia/`, tabela da V39), registrada por `POST /denuncias`
(`multipart/form-data`, foto opcional). O alvo é um perfil, um anúncio ou serviço, uma candidatura (chat) ou uma
mensagem. O acesso é conferido pelas portas `ConsultaDadosUsuario`, `ConsultaServico`, `ConsultaCandidatura` e
`ConsultaChat.buscarMensagem` (nova). A foto vira `Midia` `EVIDENCIA_DENUNCIA` (valor novo em `comum/midia`), e a
denúncia aponta pra ela em `evidencia_id`. Nasce `ABERTA` com prazo de análise (72h provisórias) e entra na fila do
admin (US58). Não toca no serviço. Detalhe em `../features/ETI-114-registrar-denuncia.md`.

## 2026-11-22 — ETI-117 (dashboard do administrador, em `administracao`): porta de totais

`actions/service/ConsultaTotaisDeDenunciasService` implementa a porta `ConsultaTotaisDeDenuncias`: conta as denúncias `ABERTA`,
o mesmo número do título da fila, para o dashboard do administrador. Detalhe em
`administracao/docs/features/ETI-117-dashboard-administrativo.md`.

## 2026-11-22 — ETI-116: fila, análise e decisão do administrador

`DecisaoModeracao` e `Sancao` entram na subentidade `denuncia/` (V43). A denúncia ganha `analisada_em` e sai da fila
como `PROCEDENTE` ou `IMPROCEDENTE`, e a avaliação ganha `oculta_em`/`oculta_por`. Rotas em `/admin/denuncias`, só do
administrador (token do ETI-117). As quatro sanções são aceitas, mesmo as que o painel v1 não mostra. Suspensão,
encerramento de conta, remoção da foto e nova média saem por evento para `auth`, `anuncio` e `perfil`. Avaliação oculta
deixou de contar em todas as consultas de avaliação. Detalhe em `../features/ETI-116-analisar-denuncia-e-aplicar-sancao.md`.

## 2026-11-25 — ETI-114 (review da PR #48): denúncia por anúncio encerrado e denúncia repetida

- **Anúncio que já teve escolhido segue entre as partes.** Antes, "sem Prestador escolhido" vinha de
  `ResumoServico.prestadorId == null`, que só enxerga a candidatura em `ESCOLHIDA`/`AGUARDANDO_VALIDACAO`/`CONCLUIDA`.
  Com o serviço cancelado depois da escolha, o dono não conseguia denunciar o ex-escolhido pelo anúncio e qualquer
  terceiro conseguia denunciar o dono. A denúncia agora usa `ConsultaServico.buscarPartes` (novo, `PartesAnuncio`),
  que traz o escolhido por `escolhidaEm` (permanente) e se o anúncio ainda está no mural. Anúncio fora do mural que
  nunca teve escolhido responde `ALVO_NAO_ACESSIVEL`.
- **Denúncia repetida não vira outra.** Mesmo denunciante, mesmo alvo (denunciado e contexto) e mesmo motivo com uma
  denúncia ainda em aberto devolvem a existente, sem gravar nem enviar foto. A trava `EscopoTrava.DENUNCIA` por
  denunciante cobre dois envios simultâneos (toque duplo). Limite de denúncias diferentes por par fica com o PO.

## 2026-11-25 — ETI-116 (review da PR #53): início e ciclo de vida da suspensão

- **A suspensão começa na decisão.** `DecidirDenunciaUseCase` recusa `inicioEm` no futuro (5 min de tolerância para o
  relógio do navegador) e grava na `Sancao` o instante da decisão como início. Antes, uma suspensão marcada para a
  semana seguinte bloqueava a conta na hora e a sanção registrava outro período. O usecase passou a usar o `Clock`.
- **Tipo do alvo e chat da análise na hora da denúncia.** `AnalisarDenunciaUseCase` usa `ConsultaServico.buscarPartes`
  (`PartesAnuncio` ganhou `escolhidoEm`): `SERVICO` só se a escolha já tinha acontecido quando a denúncia foi feita.
- **`StatusResolucao.isEmAberto`** (do ETI-114) trata `PROCEDENTE` e `IMPROCEDENTE` como fechadas, e
  `Denuncia.isAberta` usa a mesma regra.
- Do lado de `auth` e `anuncio`: ver os CHANGELOGs desses módulos.
