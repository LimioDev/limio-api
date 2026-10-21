# Histórico — perfil

Registre aqui, em ordem cronológica, decisões e mudanças de regra que não ficam óbvias só lendo o código.

Formato de cada entrada: novo arquivo `AAAA-MM-titulo-curto.md` nesta pasta, com contexto do "porquê" da mudança.

## 2026-10-15 — TICKET-0048: documentação da feature "perfil do Empregador" (etapa 1, sem código)

Primeira documentação do módulo `perfil`, que até aqui só tinha `package-info.java`. Criado
`docs/features/TICKET-0048-consultar-e-editar-perfil-empregador.md` com contrato de API proposto, regras de
negócio, limitações e decisões arquiteturais. 

A análise do card mostrou que "mesmas regras do cadastro (ETI-30)" não existe no código: o UC01 implementado não
envia OTP nem confirmação de e-mail, não há fluxo UC04/UC05 nem gateway de SMS, e o telefone é gravado só com
dígitos (`VARCHAR(11)`), não como `+55DDNNNNNNNNN`. Também não existem `Midia`, avaliações nem `endereco_salvo`.
Esses pontos estão registrados como dependências, e o `GET` proposto devolve `null` (não existe) e `0`
(contagem real zero) conforme o caso.

As decisões de arquitetura (troca de contato como operação do `auth`, porta em `comum/` para leitura de
contato, criação do perfil sob demanda) estão marcadas como **propostas**; aguardam tech lead/PO.

## 2026-10-16 — TICKET-0048: perfil lê dados do usuário por porta em `comum/` e nasce no primeiro acesso

O módulo `perfil` precisa de nome completo, e-mail, telefone e datas de confirmação do usuário, que são do `auth`
(ADR-0001: módulo não importa outro módulo). Criada a porta `comum/portas/ConsultaDadosUsuario`, implementada no
`auth`. Primeira interface do tipo em `comum/` — a ADR não a lista, **aguarda confirmação do tech lead**. A regra de uso (leitura síncrona; reação continua sendo evento) está em
`../../../auth/docs/funcionalidade/EXPLICACAO_CLASSES.md`.

O perfil não pode ser criado no cadastro (o `auth` importaria o `perfil`) e o projeto não tem eventos entre módulos
(criar o primeiro exigiria infraestrutura nova e backfill das contas existentes). Por isso `GET` e `PATCH`
criam o perfil no primeiro acesso, com o nome completo do cadastro, de forma segura contra acesso simultâneo
(`ON CONFLICT DO NOTHING`). `PATCH` passou a devolver o mesmo corpo do `GET`.

## 2026-10-16 — TICKET-0048 contato trocado no `auth` aparece no perfil

A troca de e-mail e de telefone é operação do `auth` (`PATCH /auth/email`, `PATCH /auth/telefone`); o `perfil` não
mudou. O `GET /conta/perfil-empregador` já devolve o contato novo, com as confirmações nulas, pela porta
`ConsultaDadosUsuario` (coberto por story). O telefone sai em formato internacional (`+55...`): o banco guarda só os
dígitos nacionais e quem implementa a porta (`auth`) converte, então o `perfil` não conhece a regra. A confirmação de e-mail (UC05) e de telefone (UC04) não existe; o link
enviado na troca de e-mail não conclui a confirmação.

## 2026-10 — criação lazy do perfil num só lugar (`garantirPerfil`)

Code review: `ConsultarMeuPerfilUseCase` e `EditarNomeExibicaoUseCase` repetiam "buscar `DadosUsuario` ou 404" e
"`buscarOuCriar` com o nome inicial"; mudar a regra em um só faria os endpoints divergirem. Agora
`PerfilEmpregadorService.garantirPerfil(usuarioId)` faz a consulta à porta, o 404 e a criação, e devolve
`MeuPerfilEmpregador`. O corte do nome inicial em 80 caracteres saiu do helper `NomeExibicaoInicial` para
`PerfilEmpregador.nomeExibicaoInicialDe` (regra da entity; o service não pode importar `actions/helper`).

## 2026-10-21 — TICKET-0050: endereços salvos do Empregador (listar, criar, editar, remover, marcar padrão)

Nova subentidade `EnderecoSalvo` (`perfil/endereco/`, tabela `endereco_salvo`, migration `V14`) e as rotas
`/conta/perfil-empregador/enderecos` (`GET`, `POST`, `PUT /{id}`, `DELETE /{id}`, `PATCH /{id}/padrao`). Contrato,
regras e pendências em `docs/features/TICKET-0050-enderecos-salvos-do-empregador.md`.

Decisões que não ficam óbvias só lendo o código:

- **`PerfilEmpregador`, não `PerfilContratante`**: o card usa o nome antigo; o código o renomeou na V4.
- **`rua`, `numero`, `complemento` em vez de `logradouroCompleto`** (decisão do tech lead). O DER, fora deste
  repositório, precisa ser atualizado.
- **Latitude/longitude vêm do cliente** e são `NOT NULL`; o backend só valida a faixa. Sem geocodificação no
  servidor.
- **Soft delete** por `removido_em`; todo finder filtra `removidoEm is null`. Remover o padrão deixa o Empregador
  sem padrão (0..1 padrão, sem promoção automática).
- **Um único padrão por Empregador** em duas camadas: índice único parcial no banco e lock pessimista no
  `PerfilEmpregador` em toda escrita. A ordem "desmarcar os outros e só depois marcar" é obrigatória (o índice
  parcial não é deferível).
- **Recurso alheio, removido ou inexistente → 404** `ENDERECO_NAO_ENCONTRADO`, nunca 403 (anti-enumeração).
- **`ENDERECO_PADRAO_CONFLITO` (409)** criado como rede de segurança do índice; o plano inicial previa "sem código
  novo". `ENDERECO_EM_USO` **não** foi criado (depende do módulo `anuncio`).
- **`GlobalExceptionHandler`**: id de rota que não é UUID passou de 500 para 400 `VALIDACAO_FALHOU`.
- **Jackson 3** recusa `boolean` primitivo ausente em record; por isso `isPadrao` é `Boolean` no request e é
  normalizado para `false` no compact constructor.