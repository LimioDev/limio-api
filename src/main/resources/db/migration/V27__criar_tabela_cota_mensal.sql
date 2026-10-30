-- Cota mensal do Prestador FREE (US24, card "Consultar a cota do mês e zerar na virada do mês").
-- Uma linha por perfil e mês (ano_mes 'AAAA-MM', mês civil de America/Sao_Paulo). Mês sem linha = nenhuma
-- escolha consumida: a virada do mês não apaga nem zera nada, a contagem do mês novo só ainda não existe.
--
-- escolhas_consumidas só sobe na escolha (ETI-69/ETI-70) e desce no estorno (ETI-94/ETI-95), nunca na
-- candidatura. teto_aplicado é o teto FREE em vigor quando o mês começou a contar, pra mudança de config
-- não reescrever um mês já em andamento.
--
-- FK pra perfil_prestador (mesmo módulo, subentidade). ON DELETE CASCADE: o perfil é apagado na
-- anonimização da conta (TICKET-0034) e a cota vai junto.
CREATE TABLE cota_mensal (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    perfil_prestador_id  UUID        NOT NULL,
    ano_mes              VARCHAR(7)  NOT NULL,
    escolhas_consumidas  INTEGER     NOT NULL DEFAULT 0,
    teto_aplicado        INTEGER     NOT NULL,
    criado_em            TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_cota_mensal_perfil_prestador FOREIGN KEY (perfil_prestador_id)
        REFERENCES perfil_prestador (id) ON DELETE CASCADE,
    CONSTRAINT uk_cota_mensal_perfil_ano_mes UNIQUE (perfil_prestador_id, ano_mes),
    CONSTRAINT ck_cota_mensal_ano_mes CHECK (ano_mes ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    CONSTRAINT ck_cota_mensal_escolhas_consumidas CHECK (escolhas_consumidas >= 0),
    CONSTRAINT ck_cota_mensal_teto_aplicado CHECK (teto_aplicado > 0)
);
