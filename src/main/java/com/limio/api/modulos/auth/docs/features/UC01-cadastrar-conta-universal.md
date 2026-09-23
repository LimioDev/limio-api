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
- Papel ativo padrão após cadastro: `CONTRATANTE` (rótulo de produto "Empregador").

## Decisão técnica

- **Sem OTP nesta primeira implementação.** A planilha UC01 descreve um passo de verificação de telefone por SMS antes
  de criar a conta; os critérios de aceite do card, porém, não exigem OTP. Implementamos o cadastro síncrono
  (recusa/cria na mesma requisição) e deixamos `telefone_confirmado_em`/`email_confirmado_em` como colunas nulas,
  prontas para um módulo de verificação futuro (gateway SMS ainda não escolhido). Ver `docs/history/` quando essa
  decisão for revisitada.
- **`cpf_bloqueado` como tabela própria**, não um status `EXCLUIDA` em `usuario`: mantém a regra de cooldown isolada e
  não obriga o módulo de exclusão de conta (fora de escopo deste card) a decidir agora como fica o registro do
  usuário excluído.
- **Erro genérico único** para duplicidade e bloqueio por exclusão: ambos os cenários usam
  `CadastroIndisponivelException`/`CADASTRO_DADOS_INDISPONIVEIS`, para que o motivo real (qual campo colidiu, ou que
  o CPF pertenceu a uma conta excluída) nunca vaze pro visitante.
- **`cidade_uf` como campo único** (não `cidade` + `uf` separados), seguindo a coluna já definida no modelo de dados
  (ERD anexado ao card) e o texto do critério de aceite ("cidade/UF").

## Fora de escopo (não implementado aqui)

- Verificação de telefone por OTP/SMS e confirmação de e-mail (steps 5–7 do fluxo principal da planilha UC01).
- Login/autenticação (`POST /auth/login`) e emissão de JWT — `comum/seguranca/` ainda não existe.
- Caso de uso de exclusão de conta, que é quem populará `cpf_bloqueado` na prática.
