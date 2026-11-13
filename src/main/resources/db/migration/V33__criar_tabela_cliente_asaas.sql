-- Integração Asaas (ETI-81): mapeia usuario_id pro customer do Asaas, pra não recriar cliente a cada
-- cobrança. Específico do gateway Asaas (não entra em meio_pagamento, que é agnóstico de provedor).
CREATE TABLE cliente_asaas (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id        UUID        NOT NULL,
    asaas_customer_id VARCHAR(50) NOT NULL,
    criado_em         TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_cliente_asaas_usuario_id UNIQUE (usuario_id)
);
