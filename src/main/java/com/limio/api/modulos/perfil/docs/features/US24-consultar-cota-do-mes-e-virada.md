# US24 — Consultar a cota do mês e zerar na virada do mês

## Link do ticket

`feature/us24-cota-mensal` — [perfil] [BACK-END] Consultar a cota do mês e zerar na virada do mês. Origem: Backlog
US24 · [prestador] Controlar a cota mensal FREE (sem card original no Jira). Card par: [perfil] [FRONT-END] Faixa de
cota no mural e tela "Cota do mês". Relacionados: consumo na escolha (ETI-69/ETI-70), bloqueio da candidatura
(ETI-75/ETI-76, PR #19), estorno no cancelamento do Empregador (ETI-94/ETI-95, Sprint 7). Insumos: Figma LIMIO
(28-mural, 33-cota-esgotada, 33b-cota-do-mes-parcial), DER, planilha de casos de uso.

## User Story

Como plataforma, quero mostrar ao Prestador FREE quanto da cota de 4 escolhas ele já usou e renová-la todo mês, para
que ele saiba quando pode se candidatar e o GOLD tenha valor.

## Regra de negócio implementada

- **Consultar minha cota**: plano (FREE/GOLD), mês (`anoMes`), escolhas usadas (`escolhasConsumidas`), teto
  (`tetoAplicado`, 4 no FREE), `esgotada` e a data da renovação (`renovaEm`: dia 1 do mês seguinte, 00:00,
  America/Sao_Paulo).
- **"Onde você usou sua cota"**: os serviços em que o Prestador foi escolhido no mês (`escolhidaEm` dentro do mês),
  com título, data do serviço, data da escolha e status atual da candidatura, da escolha mais recente pra mais antiga.
- **GOLD**: plano `GOLD`, `tetoAplicado = null`, `esgotada = false`. As escolhas consumidas enquanto era FREE
  continuam aparecendo.
- **Virada do mês**: no dia 1, 00:00 de São Paulo, o mês corrente passa a ser outro e a contagem começa em zero.
- **Acesso**: só o próprio Prestador (o id vem do token). Papel ativo Empregador → 403 `ACESSO_NEGADO`.

## Decisões técnicas

| Decisão | Por quê / alternativa descartada |
|---|---|
| Virada **lazy**: mês sem linha em `cota_mensal` = 0 escolhas, com o teto em vigor | Sem job agendado nem `UPDATE` em massa à meia-noite: não tem o que falhar nem rodar duas vezes. A linha do mês nasce na primeira escolha consumida (ETI-69/ETI-70). Job de virada descartado: mais peça móvel pro mesmo resultado. |
| `escolhas_consumidas` é a fonte da contagem, não `count(aplicacao)` | Card: "escolhas feitas enquanto era GOLD não contam no teto depois que volta para FREE" e "cancelamento com 24h ou mais estorna 1". As duas regras quebram uma contagem derivada das candidaturas. A lista "Onde você usou" vem de `aplicacao` (é exibição). |
| `teto_aplicado` gravado por mês | Mudar `perfil.cota.teto-free` não reescreve um mês já em andamento. Mês sem linha mostra o teto atual. |
| Fuso `America/Sao_Paulo` (`CotaMensal.FUSO`) | O card fixa. O mês vem do `Clock` (`comum/config/RelogioConfig`), pra o story testar a virada sem esperar a meia-noite. |
| `cota_mensal.perfil_prestador_id` com FK e `ON DELETE CASCADE` | Subentidade de `perfil_prestador` (DER, `package-info`). O perfil é apagado na anonimização da conta e a cota vai junto. UUID cru na entity: a cota não entra na coleção do perfil (cresce 1 linha por mês). |
| Prestador sem perfil salvo → cota zerada, sem erro | Não há o que consumir sem perfil; 404 obrigaria o front a tratar um caso que não é erro. |
| Plano pela porta `ConsultaPlanoPrestador`, implementada no `financeiro` devolvendo sempre FREE | A assinatura GOLD ainda não existe. Quando o card dela entrar, muda só `ConsultaPlanoPrestadorService`. O story do GOLD troca a porta. |
| Lista pela porta `ConsultaEscolhasDoPrestador`, implementada no `anuncio` | ADR-0003 (porta pra consulta). `status` vai como texto porque `StatusAplicacao` é interno do `anuncio`. |
| 403 `ACESSO_NEGADO` em vez de `PAPEL_INVALIDO` (nome sugerido no card) | `PAPEL_INVALIDO` não existe na develop. A PR #19 o cria com mensagem de candidatura ("Troque para o papel de Prestador para se candidatar"). O precedente da develop (endereços, publicar anúncio) é `ACESSO_NEGADO`. Se a #19 entrar e o grupo preferir, a troca é só na `SomentePrestadorConsultaCotaException`. |
| Rota `GET /perfil/prestador/cota` | O card deixa "a definir". Segue `/perfil/prestador/atuacao`, do mesmo módulo. |

## Contrato da API (compartilhado com o card [FRONT-END])

| Operação | Rota | Autenticação | Entrada | Saída |
|---|---|---|---|---|
| Consultar minha cota | `GET /perfil/prestador/cota` | `Bearer <token>`, papel ativo Prestador | — | 200 `CotaDoMesResponse` |

```json
{
  "plano": "FREE",
  "anoMes": "2026-10",
  "escolhasConsumidas": 2,
  "tetoAplicado": 4,
  "esgotada": false,
  "renovaEm": "2026-11-01T00:00:00-03:00",
  "escolhas": [
    {
      "anuncioId": "…",
      "titulo": "Faxina",
      "dataHoraServico": "2026-10-23T12:00:00Z",
      "escolhidaEm": "2026-10-20T12:00:00Z",
      "status": "ESCOLHIDA"
    }
  ]
}
```

- GOLD: `"plano": "GOLD"`, `"tetoAplicado": null`, `"esgotada": false`.
- `status` de cada item: `ESCOLHIDA`, `AGUARDANDO_VALIDACAO`, `CONCLUIDA`, `ENCERRADA` (o anúncio foi encerrado
  depois da escolha) ou, em tese, `RECUSADA`/`ENVIADA` se um fluxo futuro desfizer a escolha.
- Falhas: 401 `NAO_AUTENTICADO` (sem token), 403 `ACESSO_NEGADO` (papel Empregador). Corpo `ErroResponse`.

## Onde a cota muda (outros cards)

- **Consome**: só na escolha (ETI-69/ETI-70) — cria a linha do mês se não houver, com `teto_aplicado` = teto FREE, e
  soma 1. Não consome no GOLD. Nunca na candidatura.
- **Bloqueia**: com 4 escolhas no mês a candidatura é recusada (ETI-75/ETI-76).
- **Estorna**: cancelamento do Empregador com 24h ou mais subtrai 1 (ETI-94/ETI-95, Sprint 7). Nesta sprint a cota só
  sobe.
- **Candidatura antiga**: candidatura `ENVIADA` de outro mês não é apagada quando a cota esgota; só a escolha é recusada.

## Pendências e compatibilidade com PRs abertas

- **PR #19 (ETI-75, candidatar-se)** conta escolhas direto em `aplicacao.escolhida_em` e usa o mês **UTC**. Pelo card,
  o mês é o de São Paulo: entre 21:00 do último dia e 00:00 do dia 1 (horário de Brasília) a #19 já considera o mês
  seguinte, e a tela de cota não. Quando a escolha (ETI-69) passar a gravar `cota_mensal`, a #19 deveria ler a cota
  daqui (porta) em vez de contar candidaturas; até lá, ao menos trocar `ZoneOffset.UTC` por `America/Sao_Paulo`. O teto
  também está duplicado: `anuncio.candidatura.max-escolhas-gratuitas` (#19) e `perfil.cota.teto-free` (aqui).
- **Migração V27**: V22 e V23 são da #19 e da #20, V24 é do chat (PR #21), V25 e V26 são da #23. Quem entrar
  depois usa V28. O Flyway roda com `out-of-order=true`, então só número repetido quebra.
- **Figma** (33b) não foi visto no detalhe: se a tela mostrar a data da escolha em vez da data do serviço, os dois
  campos já vêm.
- **GOLD** e a regra "escolha feita no GOLD não conta" dependem da assinatura (`financeiro`) e da escolha (ETI-69).

## Testes

- `spec/CotaMensalTest`: mês de São Paulo vs UTC, início/renovação, dezembro → janeiro, esgotada.
- `spec/ConsultarCotaDoMesUseCaseTest`: cota gravada, mês sem linha, sem perfil, virada, GOLD, Empregador.
- `spec/CotaDoMesMapperTest`, `anuncio/spec/ConsultaEscolhasDoPrestadorServiceTest`.
- `story/CotaDoMesStoryTest`: FREE com 0, 2 e 4 escolhas, GOLD, virada do mês (31/10 23:59:59 → 01/11 00:00 em São
  Paulo), sem perfil, Empregador 403, sem token 401, cota apagada junto com o perfil.
