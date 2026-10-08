# UC15 - Configurar preferências de notificação

> Status: **especificado, ainda não implementado.** Origem: ETI-34 — [IAM] Gerenciar preferências de comunicação e
> notificação. Card: `[conta] [BACK-END] Consultar e atualizar preferências de notificação`. Card par:
> `[conta] [FRONT-END] Toggles de notificação em Ajustes`.

## Regra de negócio implementada

Usuário autenticado consulta e atualiza os canais de notificação (Push, E-mail, SMS e WhatsApp) da própria conta.
Critérios de aceite cobertos:

- `push`, `email`, `sms` e `whatsapp` são booleanos e obrigatórios (validação no record de request; ausente ou não
  booleano → `400`).
- Cada canal liga e desliga de forma independente.
- O usuário só vê e altera as próprias preferências (`PreferenciaNotificacao` do usuário logado).
- Padrões de conta nova: Push ligado; E-mail desligado; WhatsApp igual ao opt-in do cadastro; SMS não definido no card
  (ver pendências).
- Consultar: `200` com o estado salvo. Atualizar: `200` com o estado salvo. Sem login: `401`.
- WhatsApp:
  - **Ligar** grava o consentimento em `AceiteTermo` (`documento`, `versao`, `origem = configuracoes`, `ip`,
    `aceitoEm`).
  - **Desligar** grava `whatsapp = false` em `PreferenciaNotificacao`.
  - Este canal é diferente do WhatsApp liberado como contato entre as partes depois da escolha do prestador
    (módulo prestador); não compartilham flag nem regra.
- Regras de envio (contrato para quem notifica): sem consentimento ativo nada sai pelo WhatsApp; e-mails de segurança
  (senha alterada, exclusão de conta, novo login) saem sempre, mesmo com `email = false`.
- Banco (Flyway): tabela `preferencia_notificacao`, criada somente se ainda não existir.

### Contrato HTTP (caminhos propostos — o card não os define)

| Operação  | Proposta                              | Sucesso                  | Erros                            |
|-----------|---------------------------------------|--------------------------|----------------------------------|
| Consultar | `GET /conta/preferencias-notificacao` | `200` com o estado salvo | `401` sem login                  |
| Atualizar | `PUT /conta/preferencias-notificacao` | `200` com o estado salvo | `400` validação; `401` sem login |

```json
{ "push": true, "email": false, "sms": false, "whatsapp": false }
```

### Autenticação

A feature exige usuário autenticado e atua só sobre as preferências dele. **Como** o usuário é identificado
tecnicamente (sessão/token, claims, tipo recebido pelo controller) não é definido aqui: pertence à tarefa de
autenticação, ainda inexistente no projeto. Os cenários E2E só ficam testáveis de ponta a ponta depois dela.

## Decisão técnica

- **Módulo próprio** `modulos/preferencianotificacao/` (nome sugerido), seguindo ADR-0001: depende só de `comum/`,
  nunca importa classe interna de `modulos/auth`.
- **Tabela `preferencia_notificacao` com um registro por usuário** (unicidade em `usuario_id`; colunas `push`,
  `email`, `sms`, `whatsapp` `BOOLEAN NOT NULL`, `criado_em`, `atualizado_em`), no padrão de `usuario` e
  `cpf_bloqueado`. Alternativa descartada: colunas de preferência dentro de `usuario` — acoplaria um assunto de
  notificação ao cadastro de conta.
- **Atualização de estado completo** (`PUT` com os quatro campos obrigatórios), em vez de `PATCH` parcial: casa com o
  critério "booleanos e obrigatórios" e evita ambiguidade entre "ausente" e "false".
- **Sem registro = padrões de conta nova.** Consultar não falha quando o usuário nunca salvou; devolve os padrões.
- **`AceiteTermo` só na transição `false → true` do WhatsApp.** Reenviar `true` com o canal já ligado não gera novo
  aceite (a confirmar). Desligar não apaga aceites anteriores: só grava `whatsapp = false`.
- **`mapper` chamado só pelo controller**; `usecase` trabalha com entity e tipos de domínio, nunca com record
  (ADR-0001).
- **Regras de envio não são implementadas aqui.** A feature guarda e expõe as preferências; cada emissor de
  notificação aplica as regras. Como módulos não se chamam diretamente, a forma de o envio consultar a preferência
  (evento de domínio, fila) fica para quando existir o primeiro emissor.

## Fora de escopo (não implementado aqui)

- Autenticação/sessão (JWT, filtro, representação do usuário logado, `SecurityConfig`).
- Implementação de `AceiteTermo` além do uso descrito acima.
- UI (card par de front-end).
- Envio das notificações por canal.
- WhatsApp como contato entre as partes (módulo prestador).

## Pendências a confirmar antes de implementar

1. **Padrão do `sms`** em conta nova — o card não informa.
2. **"WhatsApp igual ao opt-in do cadastro"**: o cadastro atual (UC01) não coleta opt-in de WhatsApp. Definir se o
   cadastro passa a coletá-lo (e onde grava) ou se o padrão é `false` até lá.
3. **`documento` e `versao` do `AceiteTermo`**: fixos no backend, enviados pelo cliente ou configuração?
4. **`ip`**: origem do IP do cliente (cabeçalho de proxy no Railway vs. IP direto).
5. **Caminhos HTTP e nome do módulo** propostos acima.
6. **Reenvio de `whatsapp = true` já ligado**: confirmar que não gera novo aceite.
