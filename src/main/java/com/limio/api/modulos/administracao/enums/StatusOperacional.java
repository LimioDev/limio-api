package com.limio.api.modulos.administracao.enums;

/**
 * Status operacional resumido, mostrado embaixo dos cartões do dashboard (ETI-117). Só
 * {@code OPERANDO_NORMALMENTE} por enquanto: quais outros valores existem e o que os dispara é pendência do tech lead.
 * O texto exibido vai junto na resposta, pra o painel não precisar traduzir.
 */
public enum StatusOperacional {

    OPERANDO_NORMALMENTE("Operando normalmente");

    private final String descricao;

    StatusOperacional(String descricao) {
        this.descricao = descricao;
    }

    public String descricao() {
        return descricao;
    }
}
