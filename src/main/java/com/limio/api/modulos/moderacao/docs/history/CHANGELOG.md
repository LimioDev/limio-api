# CHANGELOG — módulo moderacao

## 2026-11-03 — ETI-107: avaliação entre as partes

Primeira entidade do módulo, que até aqui só tinha `package-info.java`: `Avaliacao` (`avaliacao/`, tabela da V28).
Fora do DER, a coluna `papel_avaliado`, para a média ser por papel sem consultar o `anuncio`. A média continua no
`perfil`: `perfil_prestador` ganhou `media_avaliacoes` e `qtd_avaliacoes` na V29, com a regra de `perfil_empregador`.
Detalhes e pendências em `docs/features/ETI-107-avaliar-a-outra-parte-ou-pular.md`.
