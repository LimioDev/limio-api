# UC15 - Configurar preferências de notificação

> Status: **implementado (GET/PUT), exceto o registro do `AceiteTermo`** — ver pendências. Origem: ETI-34 — [IAM] Gerenciar preferências de comunicação e
> notificação. Card: `[conta] [BACK-END] Consultar e atualizar preferências de notificação`. Card par:
> `[conta] [FRONT-END] Toggles de notificação em Ajustes`.

## Regra de negócio implementada

Usuário autenticado consulta e atualiza os canais de notificação (Push, E-mail, SMS e WhatsApp) da própria conta.
Critérios de aceite cobertos:

- `push`, `email`, `sms` e `whatsapp` são booleanos e obrigatórios (validação no record de request; ausente ou não
  booleano → `400`).
- Cada canal liga e desliga de forma independente.
- O usuário só vê e altera as próprias preferências (`PreferenciaNotificacao` do usuário logado).
- Padrões de conta nova: Push ligado; E-mail, SMS e WhatsApp desligados. O card prevê WhatsApp igual ao opt-in do
  cadastro; enquanto o cadastro não coleta esse opt-in, o padrão é `false` (ver pendências).
- Consultar: `200` com o estado salvo. Atualizar: `200` com o estado salvo. Sem login: `401`.
- WhatsApp:
  - **Ligar** deve gravar o consentimento em `AceiteTermo` (`documento`, `versao`, `origem = configuracoes`, `ip`,
    `aceitoEm`). **Ainda não implementado:** `AceiteTermo` não existe, então `PUT` com `whatsapp = true` é rejeitado
    com `422` (`WHATSAPP_INDISPONIVEL`) e nada é gravado. Só `false` é aceito até o aceite existir.
  - **Desligar** grava `whatsapp = false` em `PreferenciaNotificacao`.
  - Este canal é diferente do WhatsApp liberado como contato entre as partes depois da escolha do prestador
    (módulo prestador); não compartilham flag nem regra.
- Regras de envio (contrato para quem notifica): sem consentimento ativo nada sai pelo WhatsApp; e-mails de segurança
  (senha alterada, exclusão de conta, novo login) saem sempre, mesmo com `email = false`.
- Banco (Flyway): migration `V9` cria a tabela `preferencia_notificacao` (`CREATE TABLE` simples, sem `IF NOT EXISTS`; o Flyway
  garante a execução única).

### Contrato HTTP

| Operação  | Proposta                              | Sucesso                  | Erros                            |
|-----------|---------------------------------------|--------------------------|----------------------------------|
| Consultar | `GET /conta/preferencias-notificacao` | `200` com o estado salvo | `401` sem login                  |
| Atualizar | `PUT /conta/preferencias-notificacao` | `200` com o estado salvo | `400` validação; `401` sem login |

```json
{ "push": true, "email": false, "sms": false, "whatsapp": false }
```

### Autenticação

A feature exige usuário autenticado e atua só sobre as preferências dele. Usa a autenticação do TICKET-0032: o
`JwtAuthFilter` (`comum/seguranca/`) lê o token de acesso e o `PreferenciaNotificacaoController` recebe
`UsuarioAutenticado` via `@AuthenticationPrincipal`; o dono da preferência é sempre `usuario.id()`, nunca o corpo ou a
rota. Sem token válido, o `SecurityConfig` responde `401` (`NAO_AUTENTICADO`) antes do controller.

## Decisão técnica

- **Subentidade `preferencianotificacao/` do módulo `modulos/notificacao/`**, seguindo ADR-0001: depende só de
  `comum/`, nunca importa classe interna de `modulos/auth`.
- **Tabela `preferencia_notificacao` com um registro por usuário** (unicidade em `usuario_id`; colunas `push`,
  `email`, `sms`, `whatsapp` `BOOLEAN NOT NULL`, `criado_em`, `atualizado_em`), no padrão de `usuario` e
  `cpf_bloqueado`. Alternativa descartada: colunas de preferência dentro de `usuario` — acoplaria um assunto de
  notificação ao cadastro de conta.
- **Atualização de estado completo** (`PUT` com os quatro campos obrigatórios), em vez de `PATCH` parcial: casa com o
  critério "booleanos e obrigatórios" e evita ambiguidade entre "ausente" e "false".
- **Sem registro = padrões de conta nova.** O `GET` só consulta: quando o usuário nunca salvou, devolve os padrões sem
  gravar nada. O primeiro `PUT` cria o registro; os seguintes atualizam o mesmo (um por usuário).
- **`AceiteTermo` só na transição `false → true` do WhatsApp.** Reenviar `true` com o canal já ligado não gera novo
  aceite (a confirmar). Desligar não apaga aceites anteriores: só grava `whatsapp = false`.
- **`mapper` chamado só pelo controller**; `usecase` trabalha com entity e tipos de domínio, nunca com record
  (ADR-0001).
- **Regras de envio não são implementadas aqui.** A feature guarda e expõe as preferências; cada emissor de
  notificação aplica as regras. Como módulos não se chamam diretamente, a forma de o envio consultar a preferência
  (evento de domínio, fila) fica para quando existir o primeiro emissor.

## Fora de escopo (não implementado aqui)

- Implementação de `AceiteTermo` além do uso descrito acima.
- UI (card par de front-end).
- Envio das notificações por canal.
- WhatsApp como contato entre as partes (módulo prestador).

## Pendências a confirmar antes de implementar

1. ~~Padrão do `sms`~~ — definido: `false`.
2. **"WhatsApp igual ao opt-in do cadastro"**: o cadastro atual (UC01) não coleta opt-in de WhatsApp; até lá o padrão é
   `false`. Quando o opt-in existir, a escolha precisa ser gravada no cadastro.
3. **`documento` e `versao` do `AceiteTermo`**: fixos no backend, enviados pelo cliente ou configuração?
4. **`ip`**: origem do IP do cliente (cabeçalho de proxy no Railway vs. IP direto).
5. ~~Caminhos HTTP~~ — definidos no contrato do card (`/conta/preferencias-notificacao`). O código ficou em
   `modulos/notificacao/` (com a subentidade `preferencianotificacao/`), não num módulo `preferencianotificacao`
   próprio.
6. **Reenvio de `whatsapp = true` já ligado**: confirmar que não gera novo aceite.
7. **Coerção de tipo no JSON** (comportamento conhecido, mantido por decisão): o Jackson aceita `1`/`0` e
   `"true"`/`"false"` como booleano (resposta `200`). Valor não conversível (ex.: `"talvez"`) ou `null` dá `400`. A
   configuração global do Jackson não foi alterada.
8. **Limpeza na exclusão de conta (registros órfãos)**: `usuario_id` é coluna crua, sem FK (ADR-0001 §1.6). O
   mecanismo previsto no lugar — evento de domínio publicado por `modulos.auth` na exclusão da conta, com listener em
   `notificacao` apagando a linha — **não existe**: o caso de uso de exclusão de conta ainda não foi implementado.
   Hoje a linha permanece para sempre depois da exclusão. Além disso, um access token ainda válido de conta excluída
   consegue criar registro novo via `PUT` até expirar. Resolver junto com a exclusão de conta: evento + listener de
   remoção e, se necessário, rejeitar conta excluída/token revogado.
