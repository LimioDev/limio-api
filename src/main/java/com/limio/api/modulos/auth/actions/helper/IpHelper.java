package com.limio.api.modulos.auth.actions.helper;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Diz se um IP é de cliente na internet ou da rede interna (proxy da Railway, loopback, rede privada).
 * O limite de falhas por IP do login só vale para IP público: se o IP real não chegou (cabeçalho do proxy
 * não lido), todo mundo apareceria com o IP do proxy e um limite por IP bloquearia o login de todos.
 */
public final class IpHelper {

    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

    private IpHelper() {
    }

    public static boolean ehPublico(String ip) {
        if (ip == null || !(IPV4.matcher(ip).matches() || ip.contains(":"))) {
            return false; // só literal: nunca deixa InetAddress fazer consulta DNS
        }
        try {
            InetAddress endereco = InetAddress.getByName(ip);
            byte[] bytes = endereco.getAddress();
            boolean cgnat = bytes.length == 4 && (bytes[0] & 0xff) == 100 && (bytes[1] & 0xc0) == 64; // 100.64/10
            boolean ipv6Local = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc; // fc00::/7
            return !(endereco.isLoopbackAddress() || endereco.isSiteLocalAddress() || endereco.isLinkLocalAddress()
                    || endereco.isAnyLocalAddress() || cgnat || ipv6Local);
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
