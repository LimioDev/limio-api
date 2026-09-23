package com.limio.api.comum.excecao.enums;

/**
 * Contrato estável de erro entre back e front — código + mensagem padrão.
 * Novo cenário de negócio ganha um valor aqui, nunca uma String solta.
 */
public enum CodigoErro {

    VALIDACAO_FALHOU("Há campos inválidos no formulário."),
    CADASTRO_IDADE_MINIMA("É necessário ter 18 anos ou mais para se cadastrar."),
    CADASTRO_CPF_INVALIDO("CPF inválido."),
    CADASTRO_DADOS_INDISPONIVEIS("Não foi possível concluir o cadastro com os dados informados."),
    ENTIDADE_NAO_ENCONTRADA("Registro não encontrado."),
    ERRO_INTERNO("Ocorreu um erro inesperado. Tente novamente mais tarde.");

    private final String mensagemPadrao;

    CodigoErro(String mensagemPadrao) {
        this.mensagemPadrao = mensagemPadrao;
    }

    public String mensagemPadrao() {
        return mensagemPadrao;
    }
}
