# UC01 — Cadastrar conta universal

## Regra de negócio implementada

Visitante cria uma conta única (CPF + e-mail) via `POST /auth/cadastro`. Critérios de aceite cobertos:

- Formulário coleta nome completo, e-mail, telefone, senha, CPF, data de nascimento e cidade/UF.
- Maioridade (18 anos) validada a partir da data de nascimento — menor é recusado sem criar conta.
- CPF validado por formato + dígitos verificadores (`CpfValidator`, módulo `actions/helper`).
- CPF e e-mail únicos na base (`UsuarioRepository.existsByCpf` / `existsByEmailIgnoreCase`).
- CPF de conta excluída há menos de 12 meses bloqueia novo cadastro (`CpfBloqueadoRepository`, tabela `cpf_bloqueado`).
- Duplicidade de CPF/e-mail e bloqueio por exclusão recente retornam o mesmo erro genérico
  (`CadastroIndisponivelException` → `CADASTRO_DADOS_INDISPONIVEIS`) — anti-enumeração, não revela qual campo colidiu.
- Papel ativo padrão após cadastro: `EMPREGADOR`.

## Decisão técnica

- **Sem OTP.** A planilha UC01 descreve um passo de verificação de telefone por SMS antes de criar a conta; os
  critérios de aceite do card, porém, não exigem OTP. Implementamos o cadastro síncrono (recusa/cria na mesma
  requisição) e deixamos `email_confirmado_em` nulo até a confirmação por e-mail (TICKET-0047). UC04 (OTP por SMS)
  saiu de escopo em definitivo no ETI-134: o telefone é dado de contato obrigatório/editável, sem etapa de
  confirmação — e-mail é o único canal. Ver `docs/history/`.
- **`cpf_bloqueado` como tabela própria**, não um status `EXCLUIDA` em `usuario`: mantém a regra de cooldown isolada e
  não obriga o módulo de exclusão de conta (fora de escopo deste card) a decidir agora como fica o registro do
  usuário excluído.
- **Erro genérico único** para duplicidade e bloqueio por exclusão: ambos os cenários usam
  `CadastroIndisponivelException`/`CADASTRO_DADOS_INDISPONIVEIS`, para que o motivo real (qual campo colidiu, ou que
  o CPF pertenceu a uma conta excluída) nunca vaze pro visitante.
- **`cidade_uf` como campo único** (não `cidade` + `uf` separados), seguindo a coluna já definida no modelo de dados
  (ERD anexado ao card) e o texto do critério de aceite ("cidade/UF").

## Fora de escopo (não implementado aqui)

- Verificação de telefone por OTP/SMS (UC04) — fora de escopo em definitivo (ETI-134). A confirmação de e-mail
  entrou no TICKET-0047: o cadastro envia o link e `POST /auth/confirmar-email` o consome.
- Login/autenticação (`POST /auth/login`) e emissão de JWT — entrou no TICKET-0032.
- Caso de uso de exclusão de conta, que é quem populará `cpf_bloqueado` na prática — entrou no TICKET-0034.
