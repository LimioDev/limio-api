# TICKET-0052 — Listar os anúncios do Empregador para o painel

## Status

Implementado.

## Link do card

ETI-52 ([anuncio] [BACK-END] Listar os anúncios do Empregador para o painel). Card par:
[empregador] [FRONT-END] Tela "Início" do Empregador e barra de navegação. Figma: `13-dashboard-empregador.png`.

## User Story

Como um Empregador, eu quero que a API traga o resumo dos meus anúncios, para que o painel mostre o que precisa da
minha ação.

## Contrato da API

`GET /anuncios/painel-empregador` — exige login, sem corpo nem parâmetro (o card deixou a rota "a definir").

```json
{
  "nomeExibicao": "Maria da Silva",
  "fotoUrl": "https://…assinado…",
  "fotoExpiraEm": "2026-10-22T18:00:00Z",
  "anunciosAbertos": 2,
  "novosCandidatos": 2,
  "anuncios": [
    {
      "id": "…",
      "fotoUrl": null,
      "descricao": "Passar roupa",
      "dataHoraPretendida": "2026-10-24T13:00:00Z",
      "bairro": "Pinheiros",
      "status": "PUBLICADO",
      "candidatos": 0,
      "prestadorEscolhido": "Rafael S."
    }
  ]
}
```

| Situação | Resposta |
|---|---|
| Sucesso | 200 com o resumo (lista vazia e contadores 0 se não há anúncio aberto) |
| Sem token | 401 `NAO_AUTENTICADO` |
| Conta inexistente ou encerrada (token ainda vivo) | 404 `PERFIL_EMPREGADOR_NAO_ENCONTRADO` |

## Regra de negócio

| Critério do card | Implementação |
|---|---|
| Nome de exibição e foto (saudação "Olá, …") | `PerfilEmpregador.nomeExibicao` e link temporário da foto de perfil, lidos pela porta `comum/portas/ConsultaPerfilEmpregador` (implementada em `perfil`). Primeiro acesso cria o perfil, igual `GET /conta/perfil-empregador`. Sem foto → `fotoUrl`/`fotoExpiraEm` `null`. |
| Contador "anúncios abertos" | Quantidade de anúncios com `StatusAnuncio.isAberto()` (`PUBLICADO`, `EM_ANDAMENTO`, `AGUARDANDO_VALIDACAO`). |
| Contador "novos candidatos" | Candidaturas `ENVIADA` (ainda sem resposta do Empregador) somadas de todos os anúncios abertos. |
| Lista "Seus anúncios" | Os anúncios abertos, da `dataHoraPretendida` mais próxima pra mais distante. `CONCLUIDO` e `ENCERRADO` não entram. |
| Foto, descrição, data/hora, bairro, status | `fotoUrl` (sempre `null`, ver pendências), `descricao`, `dataHoraPretendida`, `bairroRegiaoSnapshot` (sai como `bairro`), `status`. |
| Quantidade de candidatos ou nome do prestador escolhido | `candidatos` = candidaturas `ENVIADA` do anúncio. `prestadorEscolhido` = nome da candidatura `ESCOLHIDA`/`AGUARDANDO_VALIDACAO` (`Aplicacao.isEscolhida()`), abreviado em primeiro nome + inicial do último sobrenome ("Rafael S."); `null` sem escolhido ou se a conta do prestador foi encerrada. Os dois campos sempre vêm; o front decide qual mostrar. |
| Só os anúncios do usuário logado | `anuncio.contratante_id` = id do token (`UsuarioAutenticado.id()`). O card fala em `PerfilContratante`, mas no código a coluna guarda o id do usuário (TICKET-0049). |

## Decisões técnicas

- **Porta nova em `comum/portas/` em vez do front chamar `/conta/perfil-empregador` e `/conta/foto-perfil`**: o
  card pede tudo numa resposta, e `anuncio` não pode importar `perfil` (ADR-0001). Mesmo padrão do
  `ConsultaDadosUsuario` (porta em `comum/`, implementação no módulo dono do dado).
- **Foto do Empregador não derruba o painel**: se o storage não gerar o link, o resumo sai sem foto (log de aviso),
  em vez de 503 na tela inteira. `GET /conta/foto-perfil` continua respondendo 503 nesse caso.
- **Nome do prestador pela porta `ConsultaDadosUsuario`** (nome completo do cadastro), abreviado em
  `actions/helper/NomeHelper` pra não expor o nome completo. Uma consulta por anúncio com escolhido; a lista é só de
  anúncios abertos do próprio Empregador, então o volume é pequeno.
- **Candidaturas em lote**: `AplicacaoService.buscarAbertasDosAnuncios` (já existia, TICKET-0049) — 1 query pra
  todos os anúncios.
- **Ordenação no banco**: `AnuncioRepository.findByContratanteIdAndStatusInOrderByDataHoraPretendidaAsc`, usa o
  índice `idx_anuncio_contratante_status`. Sem paginação: só anúncios abertos de um Empregador.
- **Sem checagem de papel ativo**: segue os outros endpoints (nenhum controller checa `PapelUsuario` hoje). Um
  usuário com papel Prestador recebe o próprio painel de Empregador, vazio se não tem anúncio.
- **`@Transactional` sem `readOnly`**: o primeiro acesso pode criar o `PerfilEmpregador`.

## Banco (Flyway)

Sem migration.

## Pendências

- **Foto do anúncio**: `anuncio` ainda não tem foto (sem vínculo com `midia`, sem card de criar anúncio
  implementado). `fotoUrl` sai sempre `null` até lá; o contrato já tem o campo.
- **"Novos candidatos"**: definido como candidatura `ENVIADA`. Se o PO quiser "não vistos pelo Empregador", precisa
  de marcação de leitura, que não existe.

## Testes

- `spec/ConsultarPainelEmpregadorUseCaseTest` (5): sem anúncios, contagem só de `ENVIADA` por anúncio e ordem,
  escolhido abreviado, `AGUARDANDO_VALIDACAO` também mostra o prestador, prestador com conta encerrada.
- `spec/NomeHelperTest` (4) e `perfil/spec/ConsultaPerfilEmpregadorServiceTest` (4: com foto, sem foto, storage
  indisponível, conta inexistente).
- `story/ConsultarPainelEmpregadorStoryTest` (5): Empregador sem anúncios recebe lista vazia e contadores zerados
  (critério do card); anúncios abertos por data com candidatos e escolhido, sem `CONCLUIDO`/`ENCERRADO`; anúncios de
  outro Empregador não aparecem; conta encerrada 404; sem token 401.
