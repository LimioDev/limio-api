# TICKET-0056 — Enviar e trocar a foto de perfil

## Link do ticket

`feature/56` — ETI-56, [perfil] [BACK-END] Enviar e trocar a foto de perfil. Card par: [perfil] [FRONT-END] Trocar a
foto em "Meu perfil". Insumos: Figma LIMIO (08-meu-perfil.png), planilha "Enviar foto de perfil", DER, planilha de
casos de uso.

## User Story

Como usuário, quero que a API receba e guarde minha foto, para que as pessoas me reconheçam no mural e no perfil.

## Regra de negócio implementada

- **Uma foto por usuário**, a mesma para os dois papéis (Empregador e Prestador). Trocar em "Meu perfil" troca nos dois.
- **Enviar foto**: valida, remove os metadados (EXIF, inclusive localização), guarda no storage e grava uma `Midia`
  com `tipo = FOTO_PERFIL`, `urlS3`, `mime`, `largura`, `altura`, `bytes` e `exifRemovido = true`.
- **Trocar foto**: a anterior recebe `expurgoEm` e, depois do commit, o objeto dela é apagado do storage.
- **Arquivo inválido**: recusado com 422 `FOTO_INVALIDA` e mensagem do caso (vazio, grande demais, formato).
- **Acesso**: a foto é sempre a do usuário do token (`UsuarioAutenticado.id`); nada vem da rota nem do corpo.
- **Moderação** (admin remove a foto): fora deste card — US58.

## Validação (critério PROVISÓRIO até o PO decidir)

- **Formatos**: JPEG e PNG, identificados pelo **conteúdo** do arquivo. Extensão e Content-Type enviados pelo app
  são ignorados (um `.jpg` que não é imagem é recusado). GIF, WebP, HEIC, BMP etc. são recusados — HEIC do iPhone,
  o front converte antes.
- **Tamanho**: até 5 MB (`perfil.foto.tamanho-maximo`, env `PERFIL_FOTO_TAMANHO_MAXIMO`).
- **Dimensões**: acima de 100 milhões de pixels declarados no cabeçalho é recusado sem decodificar.

## Contrato da API (compartilhado com o card [FRONT-END])

| Operação | Rota | Autenticação | Entrada | Saída |
|---|---|---|---|---|
| Enviar/trocar foto | `PUT /conta/foto-perfil` | `Bearer <token>` | `multipart/form-data`, parte `arquivo` | 200 `{ url, expiraEm }` |
| Consultar foto atual | `GET /conta/foto-perfil` | `Bearer <token>` | — | 200 `{ url, expiraEm }` ou 204 (sem foto) |

- `url` é um **link temporário** (assinado) de leitura: o bucket é privado. Vale até `expiraEm` (1 h,
  `perfil.foto.validade-link`); depois, o app consulta o `GET` de novo. Não guardar a URL como permanente.
- O `GET /conta/perfil-empregador` (TICKET-0048) continua sem a foto: o app usa `GET /conta/foto-perfil`.

Erros (`ErroResponse { codigo, mensagem, timestamp, path }`):

| `codigo` | HTTP | Quando |
|---|---|---|
| `FOTO_INVALIDA` | 422 | Arquivo vazio, acima de 5 MB, ou que não é JPEG/PNG legível |
| `ARQUIVO_GRANDE_DEMAIS` | 413 | Upload acima do limite do multipart (10 MB) — recusado antes de chegar ao controller |
| `REQUISICAO_INVALIDA` | 400 | Sem a parte `arquivo` ou corpo que não é multipart |
| `NAO_AUTENTICADO` | 401 | Sem token de acesso válido |
| `ARMAZENAMENTO_INDISPONIVEL` | 503 | Storage fora do ar, recusando, ou não configurado — tentar de novo |

## Decisão técnica e alternativas descartadas

Pendências do card resolvidas. **Decisão do tech lead (2026-10-26)**: aprovadas as técnicas (storage, envio pela API); formatos e tamanho seguem com
o PO:

- **Qual storage / onde ficam as chaves (tech lead)**: o bucket S3-compatível que já existe
  (`comum/config/StorageConfig`, Railway hoje). Chaves nas envs `STORAGE_BUCKET`, `STORAGE_ENDPOINT`,
  `STORAGE_REGION`, `STORAGE_ACCESS_KEY_ID`, `STORAGE_SECRET_ACCESS_KEY`. Sem `STORAGE_BUCKET`, a API sobe e as
  rotas da foto respondem 503.
- **App envia direto para a API ou para o storage (tech lead)**: direto para a API (multipart). O card exige remover
  o EXIF e gravar largura/altura/bytes; com link de upload o arquivo iria cru para o bucket, e a API teria de baixá-lo
  e regravá-lo depois — e, até isso acontecer, a foto com localização estaria guardada. Descartado.
- **Onde fica `Midia`**: o package-info do `anuncio` previa `midia` lá, mas o `perfil` não pode importar `anuncio`
  (ADR-0001). Pelo critério da ADR (sobe pra `comum/` só com 2+ módulos usando **hoje**), a entity nasce em
  `perfil/midia/`. A tabela já é genérica (`tipo`); quando anúncio/chat precisarem, a entity sobe para `comum/`.
- **"Liga a foto ao perfil (fotoMidia)"**: como a foto é do usuário e não do papel, não há `foto_midia_id` em
  `perfil_empregador`/`perfil_prestador`. A foto atual é a `Midia` `FOTO_PERFIL` sem `expurgo_em` do usuário, e o
  índice único parcial `uk_midia_foto_perfil_ativa` garante no máximo uma. Desvio do DER, documentado aqui.
- **`url_s3` guarda a chave do objeto**, não uma URL: o bucket é privado e o endereço entregue ao app é assinado e
  expira. Guardar a URL assinada não serviria (vence em 1 h); guardar URL pública exigiria bucket público.
- **Remoção do EXIF**: a imagem é decodificada e regravada a partir dos pixels (ImageIO, sem dependência nova), com
  metadados nulos — nada do original sobrevive (EXIF, GPS, XMP, ICC). Por isso `exifRemovido` é sempre `true`.
  Antes, a orientação do EXIF é aplicada aos pixels: sem isso, foto de celular em pé apareceria deitada.
- **Redução**: o lado maior fica em até 2048 px. A leitura já usa subamostragem, então uma foto de 48 MP não é
  decodificada inteira na memória. `largura`/`altura`/`bytes` gravados são os da foto guardada.
- **JPEG continua JPEG (qualidade 0,9) e PNG continua PNG** (mantém transparência).
- **Runtime sem writer ImageIO** pro formato (JRE enxuto): `ImagemHelper.EscritorIndisponivelException` sobe como 500
  `ERRO_INTERNO` (com log), não como 422 `FOTO_INVALIDA` — é problema do servidor, não da foto.
- **Ordem da troca** (`TrocarFotoPerfilUseCase`, numa transação):
  1. valida e processa a imagem (antes de qualquer acesso ao banco ou ao storage);
  2. advisory lock por usuário (`comum/persistencia/TravaTransacional`, escopo `EscopoTrava.FOTO_PERFIL` — mesmo
     helper do lock do login) — duas trocas simultâneas da mesma conta não deixam duas fotos ativas;
  3. sobe o objeto novo (`fotos-perfil/<usuarioId>/<uuid>.<ext>`);
  4. publica `FotoPerfilTrocadaEvent`;
  5. expurga a anterior e grava já (`saveAndFlush`) — o Hibernate faria o insert antes do update, e o índice de "uma
     ativa" recusaria;
  6. grava a nova `Midia` e devolve o link.
  `FotoPerfilEventosService` apaga o objeto **anterior** depois do commit, ou o objeto **novo** se a transação for
  desfeita. Falha ao apagar só é logada: um objeto órfão no bucket não afeta o usuário.
- **Upload dentro da transação**: a conexão com o banco fica presa enquanto o storage responde. Aceito pelo volume
  baixo de trocas de foto (mesma decisão da geocodificação no TICKET-0054).
- **Limite do multipart (10 MB) acima do limite da foto (5 MB)**: entre 5 e 10 MB a resposta é a do cenário da foto
  (422 `FOTO_INVALIDA` com a mensagem certa); acima de 10 MB o servlet recusa antes (413 `ARQUIVO_GRANDE_DEMAIS`,
  tratado no `GlobalExceptionHandler`, genérico para qualquer upload futuro).

## Banco (Flyway)

`V12__criar_tabela_midia.sql`: tabela `midia` com os campos do card, `usuario_id` cru (sem FK para `usuario`,
ADR-0001 §1.6), `uk_midia_url_s3` e o índice único parcial `uk_midia_foto_perfil_ativa`.

`V12` porque a `develop` tem a `V10`, a PR #9 (TICKET-0054) usa a `V11` e as PRs #6 e #7 disputam a `V8`/`V9`.

## Testes

- `spec/`: `ImagemHelperTest` (JPEG/PNG, EXIF removido, orientação 6 e 8, redução, GIF, texto, truncado, cabeçalho
  com dimensões absurdas, runtime sem writer JPEG), `TrocarFotoPerfilUseCaseTest` (primeira foto, troca e ordem das operações, vazio, grande
  demais, GIF, storage fora), `ConsultarFotoPerfilUseCaseTest`, `FotoPerfilEventosServiceTest` (commit e rollback),
  `ArmazenamentoServiceTest` (link assinado real, storage não configurado).
- `story/` (`FotoPerfilStoryTest`, storage mockado): os quatro do card — foto válida (com EXIF de localização, que
  não chega ao storage), tipo inválido, arquivo grande demais e troca de foto — + consulta antes/depois (204/200),
  dois usuários sem interferência, storage fora (503, foto atual mantida), sem a parte `arquivo` e 401 sem token.
- `ImagensDeTeste` (em `spec/`) gera as imagens em memória, inclusive o JPEG com segmento EXIF.

## Fora de escopo / pendências

- **PO**: formatos e tamanho definitivos (hoje JPEG/PNG até 5 MB).
- ~~**Tech lead**: confirmar envio pela API (multipart) e o bucket privado com link temporário.~~ **Decisão do tech lead (2026-10-26)**: aprovado.
- **Moderação** (admin remove a foto) — US58. Basta expurgar a `Midia` e apagar o objeto, como na troca.
- **Mural e perfil público** mostrarem a foto de outros usuários: ticket do mural (consulta por `usuario_id`).
- **Remover a foto sem enviar outra**: não pedido no card.
- **Limpeza de órfãos** no bucket (falha ao apagar fica só no log): job futuro, se o volume justificar.
