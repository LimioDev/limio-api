-- Assinar o plano GOLD (ETI-81): plano, meio_pagamento, assinatura_gold, cobranca e documento_fiscal.
-- usuario_id é coluna crua em assinatura_gold e meio_pagamento, sem FK pra usuario (ADR-0001 §1.6 —
-- financeiro não se acopla a auth nem a perfil).
--
-- preco_centavos do GOLD é valor PROVISÓRIO (pendência do PO, mesmo padrão de outras colunas
-- provisórias do projeto); ciclo "MENSAL" é o único hoje, mas a coluna existe pra outros ciclos
-- (UC "preço e ciclo parametrizáveis").

CREATE TABLE plano (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo         VARCHAR(20)  NOT NULL,
    nome           VARCHAR(100) NOT NULL,
    preco_centavos BIGINT       NOT NULL,
    moeda          VARCHAR(3)   NOT NULL,
    ciclo          VARCHAR(10)  NOT NULL,
    ativo          BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_plano_codigo UNIQUE (codigo),
    CONSTRAINT ck_plano_preco_centavos CHECK (preco_centavos > 0)
);

INSERT INTO plano (codigo, nome, preco_centavos, moeda, ciclo, ativo)
VALUES ('GOLD', 'Plano GOLD', 2990, 'BRL', 'MENSAL', TRUE);

-- Token do gateway por usuário (nunca número nem CVV). No máximo um por usuário: assinar de novo
-- com um cartão novo substitui o token salvo.
CREATE TABLE meio_pagamento (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id    UUID         NOT NULL,
    gateway_token VARCHAR(255) NOT NULL,
    bandeira      VARCHAR(20)  NOT NULL,
    ultimos4      VARCHAR(4)   NOT NULL,
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_meio_pagamento_usuario_id UNIQUE (usuario_id)
);

-- No máximo uma por usuário (igual perfil_prestador): reassinar depois de cancelada reativa a mesma
-- linha, nunca cria outra (card futuro US26/US27 de cancelamento).
CREATE TABLE assinatura_gold (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id        UUID        NOT NULL,
    plano_id          UUID        NOT NULL,
    status            VARCHAR(10) NOT NULL,
    data_inicio_ciclo TIMESTAMPTZ NOT NULL,
    data_fim_ciclo    TIMESTAMPTZ NOT NULL,
    criado_em         TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_assinatura_gold_usuario_id UNIQUE (usuario_id),
    CONSTRAINT fk_assinatura_gold_plano FOREIGN KEY (plano_id) REFERENCES plano (id)
);

-- chave_idempotencia com índice único: segunda cobrança da mesma tentativa nunca grava duas vezes,
-- ainda que a trava transacional do usecase (EscopoTrava.ASSINATURA_GOLD) falhe sob corrida real.
CREATE TABLE cobranca (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    assinatura_gold_id  UUID         NOT NULL,
    valor_centavos      BIGINT       NOT NULL,
    status              VARCHAR(10)  NOT NULL,
    gateway_cobranca_id VARCHAR(100),
    chave_idempotencia  VARCHAR(100) NOT NULL,
    criado_em           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_cobranca_assinatura_gold FOREIGN KEY (assinatura_gold_id) REFERENCES assinatura_gold (id),
    CONSTRAINT uk_cobranca_chave_idempotencia UNIQUE (chave_idempotencia),
    CONSTRAINT ck_cobranca_valor_centavos CHECK (valor_centavos > 0)
);

-- Recibo da cobrança aprovada (UC "enviar o recibo"). Uma cobrança nunca emite dois documentos.
CREATE TABLE documento_fiscal (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    cobranca_id   UUID         NOT NULL,
    numero        VARCHAR(100) NOT NULL,
    emitido_em    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_documento_fiscal_cobranca FOREIGN KEY (cobranca_id) REFERENCES cobranca (id),
    CONSTRAINT uk_documento_fiscal_cobranca_id UNIQUE (cobranca_id)
);
