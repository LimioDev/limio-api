// Utilitários das páginas dos links de e-mail (TICKET-0047). Sem dependência externa.
"use strict";

window.Limio = (function () {
  /**
   * Lê o token do fragmento (#token=...) e o tira da barra de endereço: o fragmento nunca vai ao servidor,
   * e apagá-lo evita que o token fique no histórico ou seja copiado junto com a URL.
   */
  function lerTokenDoLink() {
    var parametros = new URLSearchParams(window.location.hash.slice(1));
    var token = parametros.get("token");
    if (window.history && window.history.replaceState) {
      window.history.replaceState(null, "", window.location.pathname);
    }
    return token && token.trim() ? token.trim() : null;
  }

  /** POST JSON pra própria API. Resolve com {status, corpo}; corpo é o ErroResponse quando falha. */
  function postar(caminho, dados) {
    return fetch(caminho, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(dados),
      credentials: "omit",
    }).then(function (resposta) {
      return resposta.text().then(function (texto) {
        var corpo = null;
        try {
          corpo = texto ? JSON.parse(texto) : null;
        } catch (e) {
          corpo = null;
        }
        return { status: resposta.status, corpo: corpo };
      });
    });
  }

  function mostrar(id) {
    document.getElementById(id).hidden = false;
  }

  function esconder(id) {
    document.getElementById(id).hidden = true;
  }

  /** Mostra só a etapa pedida, dentre as seções com o atributo data-etapa. */
  function irPara(id) {
    document.querySelectorAll("[data-etapa]").forEach(function (secao) {
      secao.hidden = secao.id !== id;
    });
    var titulo = document.querySelector("#" + id + " h1");
    if (titulo) {
      titulo.setAttribute("tabindex", "-1");
      titulo.focus();
    }
  }

  return { lerTokenDoLink: lerTokenDoLink, postar: postar, mostrar: mostrar, esconder: esconder, irPara: irPara };
})();
