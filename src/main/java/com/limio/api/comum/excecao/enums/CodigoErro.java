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
    TOKEN_RECUPERACAO_INVALIDO("Link de recuperação de senha inválido ou expirado."),
    LINK_CONFIRMACAO_EMAIL_INVALIDO("Link de confirmação de e-mail inválido, expirado ou já usado."),
    SENHA_ATUAL_INCORRETA("Senha atual incorreta."),
    SENHA_IGUAL_ANTERIOR("A nova senha deve ser diferente da atual."),
    WHATSAPP_INDISPONIVEL("Ainda não é possível ativar notificações por WhatsApp."),
    CONTATO_NAO_PERMITIDO("Não foi possível usar o contato informado."),
    TROCA_CONTATO_LIMITE_EXCEDIDO("Muitas solicitações de troca de contato. Tente novamente mais tarde."),
    ENTIDADE_NAO_ENCONTRADA("Registro não encontrado."),
    PERFIL_EMPREGADOR_NAO_ENCONTRADO("Perfil de empregador não encontrado."),
    ENDERECO_NAO_ENCONTRADO("Endereço não encontrado."),
    ENDERECO_PADRAO_CONFLITO("Outro endereço foi marcado como padrão ao mesmo tempo. Tente novamente."),
    ENDERECO_NAO_LOCALIZADO("Não foi possível localizar o endereço informado. Confira rua, número, cidade e UF."),
    CATEGORIA_INVALIDA("Uma ou mais especialidades selecionadas não estão disponíveis."),
    CATEGORIA_PROTEGIDA("Esta categoria não pode ser desativada."),
    MUNICIPIO_NAO_ENCONTRADO("Município não encontrado na UF informada."),
    GEOCODIFICACAO_INDISPONIVEL("Não foi possível localizar o município agora. Tente novamente em instantes."),
    FOTO_INVALIDA("Envie uma foto JPG ou PNG."),
    ARQUIVO_GRANDE_DEMAIS("O arquivo é maior que o permitido."),
    ARMAZENAMENTO_INDISPONIVEL("Não foi possível guardar o arquivo agora. Tente novamente em instantes."),
    CONTATOS_NAO_CONFIRMADOS("Confirme seu e-mail e seu telefone para continuar."),
    DATA_NO_PASSADO("A data e a hora do serviço precisam estar no futuro."),
    ANUNCIO_NAO_ENCONTRADO("Anúncio não encontrado."),
    ANUNCIO_ESTADO_INVALIDO("O anúncio não permite esta ação no estado atual."),
    ANUNCIO_PROPRIO("Você não pode se candidatar ao próprio anúncio."),
    CANDIDATURA_DUPLICADA("Você já tem uma candidatura em aberto para este anúncio."),
    CANDIDATURA_NAO_ENCONTRADA("Candidatura não encontrada."),
    CANDIDATURA_ESTADO_INVALIDO("A candidatura não permite esta ação no estado atual."),
    COTA_ESGOTADA("Você atingiu o limite de candidaturas escolhidas do plano gratuito neste mês."),
    CHAT_NAO_ENCONTRADO("Conversa não encontrada."),
    CHAT_SOMENTE_LEITURA("Esta conversa está encerrada e não recebe novas mensagens."),
    ERRO_INTERNO("Ocorreu um erro inesperado. Tente novamente mais tarde.");

    private final String mensagemPadrao;

    CodigoErro(String mensagemPadrao) {
        this.mensagemPadrao = mensagemPadrao;
    }

    public String mensagemPadrao() {
        return mensagemPadrao;
    }
}
