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
`auth`. Primeira interface do tipo em `comum/` — a ADR não a lista, **aguarda confirmação do tech lead**.

O perfil não pode ser criado no cadastro (o `auth` importaria o `perfil`) e o projeto não tem eventos entre módulos
(criar o primeiro exigiria infraestrutura nova e backfill das contas existentes). Por isso `GET` e `PATCH`
criam o perfil no primeiro acesso, com o nome completo do cadastro, de forma segura contra acesso simultâneo
(`ON CONFLICT DO NOTHING`). `PATCH` passou a devolver o mesmo corpo do `GET`.
