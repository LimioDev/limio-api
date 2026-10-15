# TICKET-0048 — Consultar e editar o perfil de Empregador

## Link do card

ETI-48 ([empregador] [BACK-END] Consultar e editar o perfil de Empregador).

Card par: [empregador] [FRONT-END] Telas "Meu perfil" e "Dados de contato".
Relacionados: UC01 (ETI-30, cadastro), UC04 (confirmar telefone por OTP), UC05 (confirmar e-mail),
[auth] [BACK-END] Páginas web "Confirmar e-mail".

## Status

Documentado — não implementado 

Legenda usada neste documento:

- **Definido** — vem do card.
- **Proposto** — sugestão da equipe de desenvolvimento, aguardando aprovação. **Não é decisão tomada.**
- **Pendente** — decisão de tech lead ou PO ainda em aberto.
- **Dependência** — depende de outro card/módulo que ainda não existe.

## User Story

Como um Empregador, eu quero que a API mostre e salve meu nome de exibição e meus contatos, para que os
prestadores me reconheçam e consigam falar comigo depois da escolha.

## Critérios de aceitação (Definido)

### UI/Frontend

Não se aplica. A interface está no card par [FRONT-END].

### Validação

- **Nome de exibição**: obrigatório.
- **E-mail**: formato válido.
- **Telefone**: mesma regra do cadastro — DDD + 9 dígitos começando por 9. Gravado como `+55DDNNNNNNNNN`.
  Ver divergência com o código atual em "Regras de negócio → Trocar telefone".

### Fluxo

- **Consultar meu perfil** devolve:
  - `PerfilEmpregador.nomeExibicao`, a foto (`Midia`), `mediaAvaliacoes` e `qtdAvaliacoes`;
  - `Usuario.email` e `Usuario.telefone`, com a informação de confirmado (`emailConfirmadoEm`,
    `telefoneConfirmadoEm`) — a tela mostra "verificado";
  - a quantidade de endereços salvos e o apelido do endereço padrão (tela 08: "2 endereços · Casa é o padrão").
- **Editar nome de exibição**: grava `PerfilEmpregador.nomeExibicao`.
- **Trocar e-mail ou telefone**: grava o novo valor em `Usuario`; limpa `emailConfirmadoEm` ou
  `telefoneConfirmadoEm`; envia novo link (e-mail) ou novo código (SMS) por `VerificacaoContato`, com as mesmas
  regras do cadastro (ETI-30). Até confirmar de novo, o usuário não publica anúncio nem se candidata (texto da
  tela "Dados de contato").

### Regra de negócio

- **Acesso**: só o próprio usuário lê e altera o seu perfil (`UsuarioAutenticado`).
- **E-mail já usado por outra conta**: recusado com mensagem genérica, sem dizer que o e-mail existe
  (anti-enumeração, mesma regra do cadastro).
- **Anúncios**: anúncios futuros usam os dados novos; anúncios Combinado mantêm o snapshot do momento da
  escolha (UC).

### Banco (Flyway)

Tabela `perfil_contratante`, conforme o DER (no código: `perfil_empregador`, ver "Nomenclatura").

### Testes `story/`

Consultar, editar nome, trocar e-mail (com e sem duplicidade) e trocar telefone.

### Continuidade do fluxo (E2E)

- Sucesso: 200 com o perfil atualizado.
- Falha: `ErroResponse` com o `CodigoErro`.

## Contrato da API (**Proposto** — o card deixa as rotas "a definir"; compartilhado com o card [FRONT-END])

| Operação | Rota | Autenticação | Entrada | Saída |
|---|---|---|---|---|
| Consultar meu perfil | `GET /conta/perfil-empregador` | `Bearer <token>` | — | 200 `PerfilEmpregadorResponse` |
| Editar nome de exibição | `PATCH /conta/perfil-empregador/nome-exibicao` | `Bearer <token>` | `{ nomeExibicao }` | 200 `PerfilEmpregadorResponse` (perfil atualizado) |
| Trocar e-mail | `PATCH /auth/email` | `Bearer <token>` | `{ novoEmail }` | 200, confirmação de que o link foi enviado |
| Trocar telefone | `PATCH /auth/telefone` | `Bearer <token>` | `{ novoTelefone }` | 200, confirmação da troca (**sem** envio de código — ver Dependências) |

Justificativa das rotas (**Pendente**: validar com tech lead e com o front):

- Troca de e-mail/telefone no prefixo `/auth`, com `PATCH` para alteração parcial — mesmo padrão de
  `PATCH /auth/senha` e `PATCH /auth/papel-ativo` (TICKET-0032/0033).
- Recurso de perfil do próprio usuário sob `/conta/...` — mesmo padrão da branch `feature/34`
  (`/conta/preferencias-notificacao`, `GET` + `PUT`), ainda não mergeada. Se o front preferir recurso inteiro,
  `PUT /conta/perfil-empregador` substitui o `PATCH` de nome.
- Sem `/me`: não há precedente no repositório.

### Resposta do `GET` (**Proposto**)

```json
{
  "nomeExibicao": "Maria",
  "foto": null,
  "mediaAvaliacoes": null,
  "qtdAvaliacoes": 0,
  "email": "maria@exemplo.com",
  "telefone": "+5511999998888",
  "emailConfirmadoEm": null,
  "telefoneConfirmadoEm": null,
  "enderecos": { "quantidade": 0, "apelidoPadrao": null }
}
```

Princípio para dados cujas funcionalidades ainda não existem: **`null` = não existe / não se aplica; `0` =
contagem real igual a zero**.

- `foto`: `null` (não existe `Midia`).
- `mediaAvaliacoes`: `null` enquanto não há avaliação — nunca `0.0` (RB11: sem "média fantasma").
- `qtdAvaliacoes`: `0`.
- `enderecos.quantidade`: `0`; `enderecos.apelidoPadrao`: `null`.
- `emailConfirmadoEm` / `telefoneConfirmadoEm`: `null` = não confirmado (hoje é o estado de todas as contas,
  ver Dependências).

### Códigos de erro

| CodigoErro | Situação | Estado |
|---|---|---|
| `VALIDACAO_FALHOU` (400) | nome vazio/grande demais, e-mail ou telefone em formato inválido | existente — cobre o `DADOS_INVALIDOS` sugerido no card |
| `CONTATO_NAO_PERMITIDO` | e-mail de outra conta; mensagem genérica, sem revelar que existe | **novo, Proposto**; status HTTP a definir |
| `NAO_AUTENTICADO` (401) | sem token/token inválido | existente |
| Perfil inexistente | depende de como o perfil é criado (ver decisão 3) | **Pendente** |
| Excesso de pedidos de reenvio do link | silencioso (como `POST /auth/recuperar-senha`) ou erro explícito | **Pendente** |

## Regras de negócio (detalhe)

### Consultar o perfil

- Só o próprio usuário (id vem de `UsuarioAutenticado`, nunca de parâmetro da requisição).
- `nomeExibicao` e contadores vêm de `perfil_empregador`; e-mail, telefone e datas de confirmação vêm do
  `auth` (dono de `Usuario`). Mecanismo de leitura: decisão arquitetural 2.

### Editar o nome de exibição

- Obrigatório; `trim` aplicado no record.
- Tamanho máximo: **Pendente (PO)**; valor provisório de 80 caracteres, constante única de fácil ajuste.

### Trocar e-mail (**Proposto**)

- Formato válido; normalizado com `trim` e minúsculas (igual ao cadastro).
- E-mail já usado por outra conta → `CONTATO_NAO_PERMITIDO` com mensagem genérica, no mesmo espírito do
  `CadastroIndisponivelException` do cadastro. Corrida entre duas trocas simultâneas é coberta pela
  constraint única de e-mail.
- Grava o novo e-mail em `Usuario`, zera `emailConfirmadoEm` e emite novo link por
  `VerificacaoContato(canal = EMAIL, finalidade = CONFIRMACAO_EMAIL)`, reaproveitando o padrão de
  `SolicitarRecuperacaoSenhaUseCase`: token de uso único, só o hash persistido, limite de solicitações por hora
  via `VerificacaoContatoService.contarDesde`, envio por `EmailService`.
- Falha no envio não propaga nem desfaz a troca (mesmo comportamento já adotado em TICKET-0033).
- `statusConta` **não** muda (decisão 5).

### Trocar telefone (**Proposto**)

- Regra do card: DDD + 9 dígitos começando por 9; gravado como `+55DDNNNNNNNNN`.
- **Divergência com o código atual**: o cadastro hoje valida `\d{10,11}` (aceita fixo de 10 dígitos), remove
  não-dígitos e grava só dígitos em `usuario.telefone` (`VARCHAR(11)`, V1). Telefone não é usado em login, não é
  único e não aparece em `UsuarioResponse`. Adequar ao formato do card exigiria migration (`VARCHAR(14)` e
  normalização das linhas existentes), ajuste de `CadastroRequest`/`UsuarioMapper`/mensagens de validação e dos
  testes que usam telefone, e **mudaria a regra do UC01** (deixaria de aceitar fixo de 10 dígitos).
  **Pendente (PO + tech lead)**: nada disso é alterado na etapa 1.
- Grava o novo telefone em `Usuario` e zera `telefoneConfirmadoEm`. **Não envia SMS** (Dependência UC04).

### Efeito da reconfirmação

"Até confirmar de novo, o usuário não publica anúncio nem se candidata": esta feature só garante o dado
(`emailConfirmadoEm`/`telefoneConfirmadoEm` nulos). O bloqueio em si é do módulo `anuncio` (Dependência).

## Banco (Flyway) — **Proposto, nada criado**

`perfil_empregador`:

- `usuario_id` — coluna crua, `UNIQUE`, sem relação JPA com `Usuario` (ADR-0001 §1.6);
- `nome_exibicao`;
- `media_avaliacoes` — `NULL` enquanto não há avaliação;
- `qtd_avaliacoes` — `INT NOT NULL DEFAULT 0`.

Média e quantidade ficam no próprio perfil, como previsto em `perfil/package-info.java`. Sem coluna de foto até
existir `Midia`; sem tabela de endereços até existir `endereco_salvo`.

## Decisões arquiteturais

Nenhuma das decisões abaixo foi aprovada; todas dependem de confirmação do tech lead.

### 1. Separação `perfil` × `auth` — Proposto / Pendente

- `perfil` é dono de nome de exibição, avaliações e endereços do empregador.
- `auth` continua dono de `Usuario` (e-mail e telefone). A troca de contato é **usecase do `auth`**
  (`TrocarEmail`/`TrocarTelefone`), nunca do `perfil`, que não altera `Usuario` (ADR-0001: módulo não mexe em
  entidade de outro módulo).
- Alternativas: (a) rotas em `perfil` acionando o `auth` por evento — descartada por complicar a resposta
  síncrona ("link enviado"); (b) a troca virar card `[auth]` próprio — **em aberto**, pergunta do card.

### 2. Leitura de contato para o `GET` — Proposto / Pendente

- Porta (interface) em `comum/`, por exemplo `ConsultaDadosUsuario` (nome completo, e-mail, telefone, datas de
  confirmação), implementada no `auth` e consumida pelo `perfil`. Cumpre o critério "usado por 2+ módulos hoje".
- A ADR-0001 não lista portas entre o conteúdo de `comum/` → precisa de confirmação.
- Alternativa: o front consultar dois endpoints (um do `perfil`, um do `auth`) — quebraria o contrato único do
  card.

### 3. Criação do `PerfilEmpregador` — Proposto / Pendente

- **Sob demanda (get-or-create)** no primeiro acesso, com `nomeExibicao = nomeCompleto`; corrida protegida por
  `UNIQUE(usuario_id)`. Cobre contas já existentes sem backfill e não exige infraestrutura nova.
- Descartadas: (a) criar dentro do cadastro — o `auth` importaria o `perfil`, violando a ADR; (b) evento
  `UsuarioCadastradoEvent` — seria o primeiro evento do projeto (não há `ApplicationEventPublisher` nem
  listener hoje), exigiria backfill SQL das contas existentes e não eliminaria a porta da decisão 2.
- Pergunta do card ("em que momento o perfil é criado?") segue **aberta**.

### 4. Reuso do mecanismo de verificação — Proposto

Reaproveitar `VerificacaoContato`, `FinalidadeVerificacao.CONFIRMACAO_EMAIL`, `TokenRecuperacaoHelper` e
`EmailService` num componente de emissão de confirmação de e-mail que a UC05 também possa usar. Não há hoje
nenhum fluxo de confirmação de e-mail a reutilizar: só a infraestrutura.

### 5. `statusConta` na troca de contato — Proposto / Pendente

Não voltar para `PENDENTE_VERIFICACAO`: o login é permitido antes de confirmar (UC05) e o bloqueio de anúncio e
candidatura é feito por `*ConfirmadoEm` nulo. Pergunta do card, **aberta**.

## Limitações e dependências

| Item | Situação hoje | Resolvido por |
|---|---|---|
| Confirmação de e-mail | Não existe fluxo. Só `FinalidadeVerificacao.CONFIRMACAO_EMAIL` e a tabela `verificacao_contato` existem; nenhum usecase emite. O link enviado nesta feature fica inerte até haver endpoint que o consuma. | UC05 + card [auth] "Confirmar e-mail" |
| SMS / OTP | Não existe `SmsService` nem gateway. `CanalVerificacao.SMS` existe, sem uso. `verificacao_contato.token_hash` (`UNIQUE`, 64 caracteres) não comporta código curto sem novo desenho. | UC04 + escolha de gateway |
| Cadastro sem verificação | O UC01 implementado (ETI-30) declara "Sem OTP" e "Fora de escopo: OTP/SMS e confirmação de e-mail". Por isso `emailConfirmadoEm` e `telefoneConfirmadoEm` são `null` para todas as contas hoje, e "as mesmas regras do cadastro (ETI-30)" não existem no código. | UC04, UC05 |
| Foto | Tabela `midia` não existe (prevista no módulo `anuncio`). | módulo `anuncio` |
| Avaliações | Não existe módulo de avaliação. | UC45–UC47 |
| Endereços salvos | `endereco_salvo` não existe (prevista no módulo `perfil`). | card próprio de endereços |
| Funcionalidades de anúncio | Bloqueio de publicar/candidatar, snapshot de anúncio Combinado, "anúncios futuros usam os dados novos" e contadores de "Atividade" (serviços publicados e concluídos). | módulo `anuncio` |

## Pendências em aberto

Reproduzidas do card, mais as levantadas na análise.

**Tech lead**

- A troca de e-mail/telefone é operação do `auth` chamada por este card ou vira card `[auth]` próprio?
- `statusConta` volta a `PENDENTE_VERIFICACAO` na troca? (proposta: não)
- Em que momento o `PerfilEmpregador` é criado? (proposta: sob demanda; card sugeria no cadastro)
- Os contadores de "Atividade" ficam fora desta sprint?
- Porta em `comum/` para leitura de contato é aceita pela ADR?
- Rotas finais.
- Adequar o formato `+55DDNNNNNNNNN` agora (migration, cadastro, testes) ou tratar em card separado?
- O endpoint exige `papelAtivo == EMPREGADOR`? (proposta: não)

**PO**

- "E-mail de contato" é o mesmo e-mail de login?
- O nome de exibição é um só para os dois papéis ou um por papel? (assumido: só `perfil_empregador` agora)
- Tamanho máximo do nome de exibição.
- O cadastro deve passar a recusar telefone fixo (10 dígitos)?

## Fora de escopo

UC04 (OTP), UC05 (confirmação de e-mail e seu endpoint), qualquer envio de SMS, `Midia`, avaliações,
`endereco_salvo`, funcionalidades do módulo `anuncio` e eventos de domínio.

## Nomenclatura

Nome de produto é sempre "empregador" — nunca "contratante" nem "solicitante" (regra registrada em
`modulos/perfil/package-info.java`). O card e o DER ainda dizem `PerfilContratante`/`perfil_contratante`; no
código usar `PerfilEmpregador`/`perfil_empregador`.

## Insumos

- Casos de uso: UC01, UC04, UC05, UC13 em `Documentos Limio/casos-de-uso-limio.md` (fora deste repo).
- Regras: RF14, RF17, RB11 em `Documentos Limio/requisitos-e-regras-de-negocio.md` (fora deste repo).
- Card original ETI-48 na ferramenta de gestão (não versionado aqui).
