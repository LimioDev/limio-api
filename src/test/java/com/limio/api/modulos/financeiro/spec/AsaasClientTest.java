package com.limio.api.modulos.financeiro.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.limio.api.modulos.financeiro.actions.service.asaas.AsaasClient;
import com.limio.api.modulos.financeiro.excecao.GatewayIndisponivelException;
import com.sun.net.httpserver.HttpServer;

/**
 * {@link AsaasClient} contra um Asaas falso (servidor HTTP do próprio JDK): exercita o header
 * {@code access_token}, o corpo mandado e a leitura da resposta, sem rede e sem Spring context — mesmo
 * padrão de {@code NominatimGeocodificadorTest}.
 */
class AsaasClientTest {

    private static final String API_KEY = "chave-de-teste";

    private HttpServer servidor;
    private volatile String tokenRecebido;
    private volatile String corpoRecebido;

    @AfterEach
    void pararServidor() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    private AsaasClient clienteContra(String caminho, int status, String corpo) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidor.createContext(caminho, troca -> {
            tokenRecebido = troca.getRequestHeaders().getFirst("access_token");
            corpoRecebido = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            troca.sendResponseHeaders(status, bytes.length);
            try (OutputStream saida = troca.getResponseBody()) {
                saida.write(bytes);
            }
        });
        servidor.start();
        return new AsaasClient("http://127.0.0.1:" + servidor.getAddress().getPort(), API_KEY, Duration.ofSeconds(2));
    }

    @Test
    void dadoCustomerCriadoComSucesso_quandoCriarCliente_entaoDevolveIdEMandaTokenECpf() throws IOException {
        AsaasClient cliente = clienteContra("/customers", 200, "{\"id\":\"cus_000123\"}");

        String id = cliente.criarCliente("Rafael Souza", "11144477735", "usuario-externo-1");

        assertThat(id).isEqualTo("cus_000123");
        assertThat(tokenRecebido).isEqualTo(API_KEY);
        assertThat(corpoRecebido).contains("\"name\":\"Rafael Souza\"").contains("\"cpfCnpj\":\"11144477735\"");
    }

    @Test
    void dadoCobrancaAprovada_quandoCobrar_entaoDevolveIdEStatus() throws IOException {
        AsaasClient cliente = clienteContra("/payments", 200,
                "{\"id\":\"pay_000456\",\"status\":\"CONFIRMED\",\"creditCard\":{\"creditCardBrand\":\"VISA\",\"creditCardNumber\":\"****1234\"}}");

        AsaasClient.CobrancaResposta resposta = cliente.cobrar("cus_000123", "tok_abc", 2990, "chave-1");

        assertThat(resposta.aprovada()).isTrue();
        assertThat(resposta.id()).isEqualTo("pay_000456");
        assertThat(resposta.creditCard().creditCardBrand()).isEqualTo("VISA");
        assertThat(resposta.creditCard().ultimos4()).isEqualTo("1234");
        assertThat(corpoRecebido).contains("\"customer\":\"cus_000123\"").contains("\"billingType\":\"CREDIT_CARD\"")
                .contains("\"creditCardToken\":\"tok_abc\"").contains("\"externalReference\":\"chave-1\"");
    }

    @Test
    void dadoCobrancaRecusadaPeloCartao_quandoCobrar_entaoDevolveNaoAprovadaSemLancarExcecao() throws IOException {
        AsaasClient cliente = clienteContra("/payments", 400,
                "{\"errors\":[{\"code\":\"invalid_creditCard\",\"description\":\"Cartão recusado\"}]}");

        AsaasClient.CobrancaResposta resposta = cliente.cobrar("cus_000123", "tok_abc", 2990, "chave-1");

        assertThat(resposta.aprovada()).isFalse();
    }

    @Test
    void dadoChaveDeApiInvalida_quandoCobrar_entaoGatewayIndisponivel() throws IOException {
        AsaasClient cliente = clienteContra("/payments", 401, "{\"errors\":[{\"code\":\"invalid_access_token\"}]}");

        assertThatThrownBy(() -> cliente.cobrar("cus_000123", "tok_abc", 2990, "chave-1"))
                .isInstanceOf(GatewayIndisponivelException.class);
    }

    @Test
    void dadoAsaasForaDoAr_quandoCriarCliente_entaoGatewayIndisponivel() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidor.start();
        int portaFechada = servidor.getAddress().getPort();
        servidor.stop(0);
        servidor = null;
        AsaasClient cliente = new AsaasClient("http://127.0.0.1:" + portaFechada, API_KEY, Duration.ofMillis(500));

        assertThatThrownBy(() -> cliente.criarCliente("Rafael Souza", "11144477735", "usuario-externo-1"))
                .isInstanceOf(GatewayIndisponivelException.class);
    }
}
