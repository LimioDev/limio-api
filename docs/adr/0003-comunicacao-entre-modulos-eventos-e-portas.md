# ADR-0003 — Comunicação entre módulos: eventos para reação, portas para consulta

- **Status:** aceita pelo tech lead em 2026-10-26.
- **Complementa:** ADR-0001 (§ "modulos/* depende só de comum/; comunicação entre módulos é evento de domínio ou fila").
- **Formaliza:** duas práticas que já estavam no código como "proposta pendente de ADR": a porta
  `comum/portas/ConsultaDadosUsuario` (TICKET-0048) e a separação entre eventos internos e eventos entre módulos
  (PR #7 e TICKET-0048).

## Contexto

O ADR-0001 só previa "evento de domínio ou fila" entre módulos. Na prática apareceram duas necessidades:

1. **Reagir** ao que outro módulo fez. Exemplo: `anuncio`, `perfil` e `notificacao` limpam seus dados quando `auth`
   encerra uma conta.
2. **Ler**, de forma síncrona, um dado de outro módulo. Exemplo: `perfil` mostra nome, e-mail e telefone do usuário,
   que pertencem a `auth`.

Evento não resolve leitura síncrona. Importar a classe do outro módulo quebra o ADR-0001.

## Decisão

### Eventos para reação

- **Evento entre módulos** fica em `comum/eventos/` (ex.: `ContaEncerradaEvent`, `AvisoDeAnuncioEvent`): é contrato
  público e todo módulo precisa conseguir importar o tipo.
- **Evento interno** de um módulo fica em `modulos/<modulo>/eventos/` (ex.: `auth/eventos/ConfirmacaoEmailSolicitadaEvent`,
  `perfil/eventos/FotoPerfilTrocadaEvent`). Ninguém de fora escuta, e ele não sobe para `comum/`.
- **Quem reage depois do commit de outro módulo** usa `@TransactionalEventListener(phase = AFTER_COMMIT)` com
  `@Transactional(propagation = REQUIRES_NEW)` (no `AFTER_COMMIT` não há transação para participar). Falha é logada e
  não volta para quem publicou.
- **Efeito externo** (e-mail, storage) sai sempre em `AFTER_COMMIT`, nunca dentro da transação. Sem
  `fallbackExecution`: fora de transação o evento é descartado.

### Portas para consulta

- Interface + record imutável em `comum/portas/` (hoje `ConsultaDadosUsuario` e `DadosUsuario`).
- O módulo dono do dado implementa em `actions/service/`. O consumidor injeta só a interface.
- **Só leitura**, devolve record e nunca entity. Escrita ou reação em outro módulo continua sendo evento.
- Nasce pelo mesmo critério de `comum/`: um consumidor precisa do dado **hoje**.

### Regra prática

*Eventos para reação, portas para consulta.* Não criar um terceiro canal: chamada direta a usecase de outro módulo,
import de entity alheia ou tabela compartilhada continuam proibidos.

## Consequências

- O `CHANGELOG` de `auth` ("local provisório, ADR pendente") e o `EXPLICACAO_CLASSES` de `auth` ("proposta pendente de
  ADR") passam a apontar para este ADR.
- Novo módulo que precise de dado de outro abre uma porta em `comum/portas/`, não importa o módulo.
