//DEPS io.github.tors42:chariot:0.0.46
//JAVA 17+

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpServer;

import chariot.Client;
import chariot.api.Account.UriAndTokenExchange;

class oauth2pkce {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) { System.out.println("Supply the Web Server URL, i.e http://example.com:8080/"); return; }
        URI url = URI.create(args[0]);

        System.out.println("Visit " + url + "demo.html");

        String host = url.getHost();
        int port = switch(url.getPort()) {
            case -1 -> 80;
            default -> url.getPort();
        };
        //:

        URI redirectUri = URI.create(url.toString() + "redirect");

        // Typically one would just register an URL handler in some environment where the application is deployed,
        // but here we start the web server ourselves, so we will actually be the one binding the socket address.
        var bindAddress = new InetSocketAddress(InetAddress.getByName(host), port);
        var httpServer = HttpServer.create(bindAddress, 0);

        var client = Client.basic();
        BlockingQueue<UriAndTokenExchange> bq = new ArrayBlockingQueue<>(1);

        httpServer.createContext("/", exchange -> {
            switch(exchange.getRequestURI().getPath()) {
                case "/demo.html" -> {
                    String email = "";
                    String query = exchange.getRequestURI().getQuery();
                    if (query != null && query.contains("email=")) {
                        email = query.substring(query.indexOf("email=") + "email=".length());
                    }

                    byte[] response = """
                        <html>
                            <head><title>Demo</title></head>
                            <body>
                                %s
                                <form action="/fetch_email">
                                    <input type="submit" value="Fetch E-Mail address, using Lichess OAuth2 PKCE">
                                </form>
                            </body>
                        </html>
                        """.formatted(email)
                        .getBytes();

                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.close();
                }

                case "/fetch_email" -> {
                    var urlAndTokenExchange = client.account().oauthPKCEwithCustomRedirect(
                            redirectUri,
                            Client.Scope.email_read
                            );
                    bq.add(urlAndTokenExchange);

                    String lichessUrl = urlAndTokenExchange.url().toURL().toString();
                    exchange.getResponseHeaders().put("Location", List.of(lichessUrl));
                    exchange.sendResponseHeaders(302, -1);
                }

                case "/redirect" -> {
                    var query = exchange.getRequestURI().getQuery();
                    var inparams = Arrays.stream(query.split("&"))
                        .collect(Collectors.toMap(
                                    s -> s.split("=")[0],
                                    s -> s.split("=")[1]
                                    ));
                    String code = inparams.get("code");
                    String state = inparams.get("state");

                    String emailAddress = "";
                    try {
                        var urlAndTokenExchange = bq.take();
                        var token = urlAndTokenExchange.token(code, state).get();
                        var auth = Client.auth(token);
                        emailAddress = auth.account().emailAddress().get().email();
                        auth.account().revokeToken();
                    } catch (Exception e) {}

                    var headers = exchange.getResponseHeaders();
                    headers.put("Location", List.of("/demo.html?email=%s".formatted(emailAddress)));
                    exchange.sendResponseHeaders(302, -1);
                }

                default -> {
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                }
            }
        });
        httpServer.start();
    }
}
