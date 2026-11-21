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
