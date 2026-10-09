# TICKET-0033 — Recuperar senha esquecida e alterar senha autenticada

## Link do card

ETI-33 ([IAM] Recuperar senha esquecida e alterar senha autenticada). Planilha: UC06, UC07.

Card par: [auth] [FRONT-END] Telas de solicitar recuperação e de alterar senha.
Relacionado: [auth] [BACK-END] Páginas web "Confirmar e-mail" e "Nova senha".

## Status

Pendências do tech lead resolvidas (ver "Decisão técnica" abaixo) — liberado pra implementação.

## User Story

Como usuário, quero redefinir a senha esquecida por um link seguro e alterar a senha quando estiver logado, para
manter o controle do meu acesso.

## Critérios de aceitação

### UI/Frontend

Não se aplica aqui — telas ficam no card par [FRONT-END] e no card das páginas web.

### Validação (mesma regra no record e no Zod)

- Senha: 8 a 72 caracteres (72 é o limite do BCrypt); pelo menos 1 letra e 1 número; caractere especial não é
  obrigatório.
- E-mail em formato válido.
- Senha atual obrigatória na alteração (fluxo logado).

### Fluxo

- **Solicitar recuperação**: gera token de uso único, válido por 1h; grava em `VerificacaoContato` só o hash do
  token; envia por e-mail o link pra página web "Nova senha".
- **Redefinir pelo link ("Esqueci minha senha")**: valida o token e grava a nova senha; marca o token como usado
  (`consumidoEm`); encerra todas as sessões do usuário.
- **Alterar senha (logado)**: confere a senha atual e grava a nova; mantém a sessão deste aparelho e entrega
  tokens novos pra ela; encerra as sessões dos outros aparelhos; envia o e-mail "Sua senha foi alterada. Não foi
  você?", com link de recuperação.

### Regra de negócio

- **Anti-enumeração**: a solicitação sempre recebe a mesma resposta genérica, exista o e-mail ou não. Limite de 3
  solicitações por hora por e-mail — acima disso nada é enviado, mas a resposta continua a mesma.
- **Recusas**: link expirado, já usado ou inexistente → `TOKEN_RECUPERACAO_INVALIDO`; senha atual errada →
  `SENHA_ATUAL_INCORRETA`; nova senha igual à atual → `SENHA_IGUAL_ANTERIOR`.

### Banco (Flyway)

- Tabela `VerificacaoContato`, se ainda não existir (compartilhada com o Cadastro — UC01).

### Testes `story/`

- Link válido, expirado e já usado.
- Senha atual incorreta e senha igual à anterior.
- Outras sessões encerradas após a troca.

### Continuidade do fluxo (E2E)

- Sucesso: 200.
- Falha: `ErroResponse` com o `CodigoErro`.

## Contrato da API (compartilhado com o card [FRONT-END] e o card das páginas web)

| Operação | Rota | Entrada | Saída |
|---|---|---|---|
| Solicitar recuperação | a definir | `{ email }` | resposta genérica |
| Redefinir pelo link | a definir | `{ token, novaSenha }` | sucesso |
| Alterar senha | a definir | `{ senhaAtual, novaSenha }` | novos tokens da sessão atual |

`CodigoErro` novos (nomes sugeridos pelo card): `TOKEN_RECUPERACAO_INVALIDO`, `SENHA_ATUAL_INCORRETA`,
`SENHA_IGUAL_ANTERIOR`.

## Decisão técnica (tech lead)

- **Provedor de e-mail**: Resend. Comportamento em dev a definir na implementação (ex.: sandbox/log em vez de
  envio real) — não bloqueia este card.
- **Diferenciar recuperação de senha de confirmação de e-mail**: `CanalVerificacao` continua só o meio de envio
  (`EMAIL`/`SMS`, sem valor por operação). `VerificacaoContato` ganha campo novo de finalidade — enum
  `FinalidadeVerificacao` (`CONFIRMACAO_EMAIL`, `RECUPERACAO_SENHA`, ...) — separado do canal. Repõe o que o
  campo `proposito` fazia no diagrama antigo, sem acoplar finalidade ao meio de envio.

## Nomenclatura

Nome de produto é sempre "empregador" — nunca "contratante" nem "solicitante" (regra registrada em
`modulos/perfil/package-info.java`). Nenhum texto deste card usa os termos antigos, mas qualquer rota, record ou
e-mail criado aqui que mencione o papel do usuário segue essa nomenclatura.

## Insumos

- Planilha (UC06, UC07): https://docs.google.com/spreadsheets/d/1kWNyoly5FkJhXvtluj5rvOOADmTCcx7xr4R0tdRfUdw/edit
- Diagrama de classes: anexado ao card original na ferramenta de gestão (não versionado aqui).
