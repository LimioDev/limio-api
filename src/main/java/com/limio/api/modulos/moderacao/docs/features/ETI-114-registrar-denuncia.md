# ETI-114 — Registrar denúncia

Card: [ETI-114](https://kaueamaral96.atlassian.net/browse/ETI-114) · Backlog US57 · card par: [moderacao] [FRONT-END]
Tela "Abrir denúncia" (tela 46).

## Regra de negócio

- **Alvo:** o usuário denunciado e, conforme o caso, o contexto onde o problema aconteceu: o anúncio (ou o serviço,
  quando já tem Prestador escolhido), a candidatura (o chat é 1:1 por candidatura) ou uma mensagem do chat. Denúncia de
  perfil só tem o denunciado.
- **Quem pode (alvo acessível ao denunciante):**

  | Alvo informado | Quem pode | Denunciado |
  |---|---|---|
  | `mensagemId` | quem participa do chat da mensagem | o remetente |
  | `candidaturaId` | o Empregador dono do anúncio ou o Prestador da candidatura | a outra parte |
  | `anuncioId` com Prestador escolhido (serviço) | o Empregador dono ou o Prestador escolhido | a outra parte |
  | `anuncioId` sem escolhido | qualquer usuário, menos o dono | o Empregador dono |
  | só `denunciadoId` (perfil) | qualquer usuário | a conta, que precisa estar ativa |

  Ninguém denuncia a si mesmo, e isso inclui a própria mensagem e o próprio anúncio. Se `denunciadoId` vier junto com um
  contexto, ele precisa ser o denunciado que sai do contexto. Qualquer recusa responde 404 `ALVO_NAO_ACESSIVEL`, igual a
  um id inexistente, pra não revelar o que existe.
- **Motivo:** obrigatório, um de `NAO_PAGOU_VALOR_COMBINADO`, `SERVICO_NAO_REALIZADO`, `COMPORTAMENTO_INADEQUADO`,
  `SUSPEITA_DE_FRAUDE` e `OUTRO_MOTIVO` (tela 46). O texto exibido fica no app.
- **Descrição:** obrigatória, até 1000 caracteres, sem espaços nas pontas.
- **Evidência:** uma foto opcional, JPG ou PNG, até 5 MB. Ela é regravada sem EXIF, como as outras fotos, e vira
  `Midia` `EVIDENCIA_DENUNCIA`.
- **Registro:** a denúncia nasce `ABERTA`, com `prazoAnalise` = registro + 72h, e entra na fila do admin (US58).
- **Não muda nada no serviço:** não reabre, não muda o status do anúncio nem da candidatura e não move dinheiro.
  Denúncia de não pagamento depois de `CONCLUIDO` ou em `AGUARDANDO_VALIDACAO` é registrada igual às outras, e o admin
  não marca "pago" nem "não pago".

## Contrato

| Operação | Rota | Entrada (`multipart/form-data`) | Saída |
|---|---|---|---|
| Registrar denúncia | `POST /denuncias` | campos `denunciadoId`, `anuncioId`, `candidaturaId`, `mensagemId`, `motivo`, `descricao`; parte `foto` (opcional) | `201 { id, numero, statusResolucao, prazoAnalise }` |

- O alvo é `denunciadoId` e/ou **no máximo um** entre `anuncioId`, `candidaturaId` e `mensagemId`. Com um contexto,
  `denunciadoId` é opcional.
- `numero` é o número da denúncia pra mostrar ao usuário: sequencial, separado do `id`.
- Erros:

  | Situação | Status | `codigo` |
  |---|---|---|
  | sem motivo, motivo desconhecido, sem alvo, dois contextos, descrição vazia ou longa | 400 | `VALIDACAO_FALHOU` |
  | alvo inexistente, inacessível ou o próprio denunciante | 404 | `ALVO_NAO_ACESSIVEL` |
  | foto vazia, acima de 5 MB ou fora de JPG/PNG | 422 | `FOTO_INVALIDA` |
  | sem login | 401 | `NAO_AUTENTICADO` |

  O card sugeria `DADOS_INVALIDOS`. Usei o `VALIDACAO_FALHOU` que a API já devolve em toda validação de campo.

## Decisões técnicas

- **Subentidade `moderacao/denuncia/`:** o módulo já existia por causa da avaliação (ETI-107), então não precisei de
  módulo novo. Tabela `denuncia` na V39, com ids crus para os outros módulos (ADR-0001 §1.6) e FK só para `midia`, que
  é de `comum/`.
- **Acesso pelas portas de leitura (ADR-0003):** `ConsultaDadosUsuario` (perfil), `ConsultaServico` (anúncio ou
  serviço), `ConsultaCandidatura` (candidatura) e `ConsultaChat.buscarMensagem` (mensagem, método novo e
  `ResumoMensagem` novo, implementados no `chat`).
- **O contexto completo é gravado:** a denúncia de mensagem guarda também a candidatura e o anúncio, e a de candidatura
  guarda o anúncio. Assim o admin vê de onde veio sem precisar consultar o `chat`.
- **Número da denúncia:** `numero BIGINT GENERATED ALWAYS AS IDENTITY`, lido de volta pelo Hibernate (`@Generated`).
  `DenunciaService.salvarAgora` faz o flush pra resposta já sair com ele.
- **A evidência fica na denúncia (`evidencia_id`), não na mídia:** a `midia` só tem a coluna `anuncio_id` de contexto.
  Apontar da denúncia para a mídia evita uma coluna nova numa tabela genérica, e a regra de `anuncio_id` (V30) já deixa
  a evidência de denúncia sem anúncio, sem migration na `midia`.
- **Storage:** chave `evidencias-denuncia/{denuncianteId}/{uuid}.{ext}`. Se a transação desfizer depois do upload,
  `EvidenciaDenunciaEnviadaEvent` (interno) leva `EvidenciaDenunciaEventosService` a apagar o objeto em
  `AFTER_ROLLBACK`, com a mesma limpeza das fotos do anúncio.
- **Ordem das recusas:** primeiro a validação dos campos (400), depois o alvo (404) e por último a foto (422). Assim um
  terceiro não descobre nada pelo erro da foto.
- **Campos de formulário (`@ModelAttribute`), não uma parte JSON:** o `FormData` do app não define o `Content-Type` de
  cada parte com facilidade. É o mesmo formato do check-out (ETI-90).

## Pendências e limitações

- **Prazo de análise de 72h é provisório:** o UC não diz o valor. Está em
  `moderacao.denuncia.prazo-analise-horas` e precisa da confirmação do PO.
- **"Ter visto o perfil" não é conferido:** nada registra quem viu qual perfil, então qualquer usuário logado denuncia
  um perfil ativo. Se o PO quiser restringir (por exemplo, só quem já teve contato), o lugar é `alvoDoPerfil`.
- **Anúncio sem escolhido não confere o status:** a porta `ConsultaServico` não traz o status, então um anúncio que
  ainda não está no mural também pode ser denunciado por quem tem o id.
- **Sem limite de denúncias repetidas:** o card não pede. A mesma pessoa pode denunciar o mesmo alvo mais de uma vez.
- **Fila, análise e decisão:** ficam para o card do admin (US58). Os outros status de `StatusResolucao` entram com ele.
- **Conta encerrada:** a evidência e a denúncia ficam guardadas. A retenção e a anonimização ficam para o card de
  moderação ou de privacidade que tratar disso.
