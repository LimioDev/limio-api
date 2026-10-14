package com.limio.api.comum.excecao.enums;

/**
 * Contrato estável de erro entre back e front — código + mensagem padrão.
 * Novo cenário de negócio ganha um valor aqui, nunca uma String solta.
 */
public enum CodigoErro {

    VALIDACAO_FALHOU("Há campos inválidos no formulário."),
    REQUISICAO_INVALIDA("Requisição inválida."),
    CADASTRO_IDADE_MINIMA("É necessário ter 18 anos ou mais para se cadastrar."),
    CADASTRO_CPF_INVALIDO("CPF inválido."),
    CADASTRO_DADOS_INDISPONIVEIS("Não foi possível concluir o cadastro com os dados informados."),
    CREDENCIAIS_INVALIDAS("E-mail ou senha incorretos."),
    LOGIN_TEMPORARIAMENTE_BLOQUEADO("Muitas tentativas de login. Aguarde alguns minutos e tente novamente."),
    CONTA_SUSPENSA("Sua conta está suspensa. Fale com o suporte."),
    NAO_AUTENTICADO("Faça login para continuar."),
    ACESSO_NEGADO("Você não tem permissão para acessar este recurso."),
    SESSAO_INVALIDA("Sua sessão expirou. Faça login novamente."),
    PAPEL_INVALIDO("Só é possível alternar entre Empregador e Prestador."),
    TOKEN_RECUPERACAO_INVALIDO("Link de recuperação de senha inválido ou expirado."),
    SENHA_ATUAL_INCORRETA("Senha atual incorreta."),
    SENHA_IGUAL_ANTERIOR("A nova senha deve ser diferente da atual."),
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
