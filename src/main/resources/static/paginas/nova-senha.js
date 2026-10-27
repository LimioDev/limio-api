// Página "Nova senha" (TICKET-0047): chama POST /auth/redefinir-senha (UC06, ETI-33).
// Validação aqui é só ajuda de preenchimento: a regra que vale é a do RedefinirSenhaRequest no back.
"use strict";

(function () {
  var TAMANHO_MIN = 8;
  var TAMANHO_MAX = 72;

  var token = Limio.lerTokenDoLink();
  if (!token) {
    Limio.irPara("recusado");
    return;
  }
  Limio.irPara("formulario");

  var form = document.getElementById("form-nova-senha");
  var campoSenha = document.getElementById("nova-senha");
  var campoConfirmacao = document.getElementById("confirmacao");
  var botao = document.getElementById("botao-salvar");
  var erroEnvio = document.getElementById("erro-envio");

  function requisitos(senha) {
    return {
      tamanho: senha.length >= TAMANHO_MIN && senha.length <= TAMANHO_MAX,
      letra: /[A-Za-z]/.test(senha),
      numero: /\d/.test(senha),
    };
  }

  function atualizarRequisitos() {
    var r = requisitos(campoSenha.value);
    document.getElementById("req-tamanho").classList.toggle("ok", r.tamanho);
    document.getElementById("req-letra").classList.toggle("ok", r.letra);
    document.getElementById("req-numero").classList.toggle("ok", r.numero);
    return r.tamanho && r.letra && r.numero;
  }

  function confirmacaoConfere() {
    var confere = campoConfirmacao.value === campoSenha.value;
    var mostrarErro = !confere && campoConfirmacao.value.length > 0;
    document.getElementById("erro-confirmacao").hidden = !mostrarErro;
    campoConfirmacao.setAttribute("aria-invalid", mostrarErro ? "true" : "false");
    return confere;
  }

  function mostrarErroEnvio(mensagem) {
    erroEnvio.textContent = mensagem;
    erroEnvio.hidden = false;
  }

  campoSenha.addEventListener("input", function () {
    atualizarRequisitos();
    if (campoConfirmacao.value) confirmacaoConfere();
  });
  campoConfirmacao.addEventListener("input", confirmacaoConfere);

  form.addEventListener("submit", function (evento) {
    evento.preventDefault();
    erroEnvio.hidden = true;

    var senhaValida = atualizarRequisitos();
    campoSenha.setAttribute("aria-invalid", senhaValida ? "false" : "true");
    if (!senhaValida) {
      mostrarErroEnvio("A nova senha precisa ter de 8 a 72 caracteres, com pelo menos 1 letra e 1 número.");
      campoSenha.focus();
      return;
    }
    if (!confirmacaoConfere() || !campoConfirmacao.value) {
      document.getElementById("erro-confirmacao").hidden = false;
      campoConfirmacao.focus();
      return;
    }

    botao.disabled = true;
    botao.textContent = "Salvando…";
    Limio.postar("/auth/redefinir-senha", { token: token, novaSenha: campoSenha.value })
      .then(function (resposta) {
        if (resposta.status === 200) {
          Limio.irPara("sucesso");
        } else if (resposta.corpo && resposta.corpo.codigo === "TOKEN_RECUPERACAO_INVALIDO") {
          Limio.irPara("recusado");
        } else if (resposta.corpo && resposta.corpo.mensagem) {
          mostrarErroEnvio(resposta.corpo.mensagem);
        } else {
          mostrarErroEnvio("Não foi possível salvar agora. Tente novamente em instantes.");
        }
      })
      .catch(function () {
        mostrarErroEnvio("Sem conexão. Verifique sua internet e tente novamente.");
      })
      .finally(function () {
        botao.disabled = false;
        botao.textContent = "Salvar nova senha";
      });
  });
})();
