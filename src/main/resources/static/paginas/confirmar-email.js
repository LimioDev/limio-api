// Página "Confirmar e-mail" (TICKET-0047): chama POST /auth/confirmar-email (UC05, ETI-30) ao abrir.
// Não há o que preencher: o resultado da confirmação é a página.
"use strict";

(function () {
  var token = Limio.lerTokenDoLink();
  if (!token) {
    Limio.irPara("recusado");
    return;
  }

  function confirmar() {
    Limio.irPara("confirmando");
    Limio.postar("/auth/confirmar-email", { token: token })
      .then(function (resposta) {
        if (resposta.status === 200) {
          Limio.irPara("sucesso");
        } else if (resposta.status >= 400 && resposta.status < 500) {
          Limio.irPara("recusado");
        } else {
          Limio.irPara("falha");
        }
      })
      .catch(function () {
        Limio.irPara("falha");
      });
  }

  document.getElementById("tentar-de-novo").addEventListener("click", confirmar);
  confirmar();
})();
