# Histórico — preferencianotificacao

Registre aqui, em ordem cronológica, decisões e mudanças de regra que não ficam óbvias só lendo o código.

Formato de cada entrada: novo arquivo `AAAA-MM-titulo-curto.md` nesta pasta, com contexto do "porquê" da mudança.

## 2026-10 — especificação do módulo `preferencianotificacao` (ETI-34)

Documentada, sem código, a feature de consultar e atualizar preferências de notificação (Push, E-mail, SMS e
WhatsApp) do usuário logado. Detalhe da regra em `docs/features/ETI-34-preferencias-de-notificacao.md` e fluxo
previsto em `docs/funcionalidade/EXPLICACAO_CLASSES.md`.

Decisões registradas: módulo próprio (não colunas em `usuario`); `PUT` de estado completo; `AceiteTermo` gravado só
na transição do WhatsApp de desligado para ligado; regras de envio (WhatsApp sem consentimento, e-mails de segurança
sempre enviados) ficam com os emissores de notificação, não com esta feature.

A autenticação (JWT) pertence a outra tarefa e não foi especificada aqui; a feature apenas exige usuário autenticado.

Pendências em aberto (padrão do `sms`, opt-in de WhatsApp no cadastro, origem de `documento`/`versao`/`ip` do aceite,
caminhos HTTP) estão listadas no arquivo da feature. Atualizar esta página quando forem resolvidas e quando a
implementação começar.
