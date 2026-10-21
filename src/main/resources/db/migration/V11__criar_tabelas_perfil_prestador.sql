-- Perfil de atuação do Prestador (TICKET-0054): perfil_prestador, categoria, prestador_categoria e
-- janela_atendimento, só com os campos que o card usa. Fora do DER: categoria.grupo e categoria.ordem
-- (a tela "Especialidades" agrupa e ordena as categorias, e o DER não tem como). usuario_id é coluna
-- crua, sem FK pra usuario (ADR-0001 §1.6 — perfil não se acopla a auth).

CREATE TABLE categoria (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome          VARCHAR(100) NOT NULL,
    grupo         VARCHAR(100) NOT NULL,
    ordem         INTEGER      NOT NULL,
    ativa         BOOLEAN      NOT NULL DEFAULT TRUE,
    protegida     BOOLEAN      NOT NULL DEFAULT FALSE,
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_categoria_nome UNIQUE (nome),
    -- Categoria protegida ("Outros") não pode ser desativada (UC): ainda não existe tela de admin
    -- pra isso, então a regra fica garantida aqui pra quem vier a implementar.
    CONSTRAINT ck_categoria_protegida_ativa CHECK (ativa OR NOT protegida)
);

-- latitude/longitude: centro do município-base, resolvido pela geocodificação ao salvar.
CREATE TABLE perfil_prestador (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id          UUID             NOT NULL,
    raio_atendimento_km INTEGER          NOT NULL,
    municipio_base      VARCHAR(100)     NOT NULL,
    uf_base             VARCHAR(2)       NOT NULL,
    latitude_base       DOUBLE PRECISION NOT NULL,
    longitude_base      DOUBLE PRECISION NOT NULL,
    completo            BOOLEAN          NOT NULL,
    criado_em           TIMESTAMPTZ      NOT NULL DEFAULT now(),
    atualizado_em       TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT uk_perfil_prestador_usuario_id UNIQUE (usuario_id),
    CONSTRAINT ck_perfil_prestador_raio CHECK (raio_atendimento_km BETWEEN 1 AND 40)
);

-- Cache da geocodificação: busca as coordenadas de um município que outro perfil já resolveu
-- (upper: é o que o IgnoreCase do Spring Data gera).
CREATE INDEX idx_perfil_prestador_uf_municipio ON perfil_prestador (uf_base, upper(municipio_base));

CREATE TABLE prestador_categoria (
    perfil_prestador_id UUID NOT NULL,
    categoria_id        UUID NOT NULL,
    CONSTRAINT pk_prestador_categoria PRIMARY KEY (perfil_prestador_id, categoria_id),
    CONSTRAINT fk_prestador_categoria_perfil_prestador FOREIGN KEY (perfil_prestador_id) REFERENCES perfil_prestador (id),
    CONSTRAINT fk_prestador_categoria_categoria FOREIGN KEY (categoria_id) REFERENCES categoria (id)
);

CREATE TABLE janela_atendimento (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    perfil_prestador_id UUID        NOT NULL,
    dia_semana          VARCHAR(10) NOT NULL,
    hora_inicio         TIME        NOT NULL,
    hora_fim            TIME        NOT NULL,
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_janela_atendimento_perfil_prestador FOREIGN KEY (perfil_prestador_id) REFERENCES perfil_prestador (id),
    CONSTRAINT ck_janela_atendimento_intervalo CHECK (hora_inicio < hora_fim)
);

CREATE INDEX idx_janela_atendimento_perfil_prestador_id ON janela_atendimento (perfil_prestador_id);

-- Carga inicial: categorias do Figma ("Especialidades"), agrupadas como na tela. Os itens do grupo
-- "Área externa" ainda não foram definidos (pendência do PO) — entram numa migration própria.
INSERT INTO categoria (nome, grupo, ordem, protegida) VALUES
    ('Elétrica',           'Casa e reparos', 1,  FALSE),
    ('Hidráulica',         'Casa e reparos', 2,  FALSE),
    ('Pintura',            'Casa e reparos', 3,  FALSE),
    ('Montagem de móveis', 'Casa e reparos', 4,  FALSE),
    ('Reparos gerais',     'Casa e reparos', 5,  FALSE),
    ('Limpeza',            'Limpeza',        6,  FALSE),
    ('Limpeza pós-obra',   'Limpeza',        7,  FALSE),
    ('Outros',             'Outros',         99, TRUE);
