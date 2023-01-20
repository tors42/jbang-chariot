//DEPS io.github.tors42:chariot:0.0.58
//DEPS info.picocli:picocli:4.7.0
//JAVA 17+
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.*;
import java.util.stream.*;

import chariot.Client;
import chariot.model.*;
import chariot.model.Player.User;

import picocli.CommandLine;
import picocli.CommandLine.*;

@CommandLine.Command(name = "opponents", sortOptions = false, sortSynopsis = false, usageHelpAutoWidth = true, showDefaultValues = true)
class opponents implements Runnable {

    @Option(names = "--user", required = true, description = "The Lichess user id of the user you want to check top opponents.")
    String userId;

    @Option(names = "--max", defaultValue = "10", description = "A limit of how many opponents you want to show")
    int maxOpponents;

    @ArgGroup(exclusive = true)
    Exclusive option;

    static class Exclusive {
        @Option(names = "--generate-token",
                description = "(Not needed - enables faster download) " +
                              "Use OAuth2 to dynamically create a token " +
                              "by visiting Lichess URL and choosing to grant access or not.")
        boolean oauth = false;
        @Option(names = "--token",
                showDefaultValue = CommandLine.Help.Visibility.NEVER,
                defaultValue = "${env:LICHESS_API_TOKEN}",
                description = "(Not needed - enables faster download) " +
                              "A pre-created API token. Note, no scopes needed. " +
                              "Token can be created with URL: " +
                              "https://lichess.org/account/oauth/token/create?description=Faster+game+download " +
                              "May be set by environment variable LICHESS_API_TOKEN")
        String token = null;
    }

    @Option(names = { "-h", "--help" }, usageHelp = true, description = "Shows this help message")
    boolean help = false;

    Runnable deleteGeneratedToken = () -> {};

    public void run() {

        var client = Client.basic();

        if (option != null) {
            if (option.oauth) {
                var urlAndToken = client.account().oauthPKCE();
                System.out.println("""

                        Visit the following URL and choose to grant access or not,
                        %s

                        """.formatted(urlAndToken.url()));
                try {
                    var auth = Client.auth(urlAndToken.token().get());
                    client = auth;
                    deleteGeneratedToken = () -> auth.account().revokeToken();
                } catch (Exception e) {
                    System.out.println("OAuth2 failed, continuing with slow download. (%s)".formatted(e.getMessage()));
                }
            } else if (option.token != null) {
                var auth = Client.auth(option.token);
                if (auth.account().profile() instanceof Fail<?> f) {
                    System.out.println("Token failed, continuing with slow download. (%s)".formatted(f));
                } else {
                    System.out.println("Using token for faster download");
                    client = auth;
                }
            }
        }

        var map = fetchGamesAgainstHumanOpponents(userId, client);

        record Opponent(String id, int numGames) {}

        var opponentList = map.entrySet().stream()
            .map(e -> new Opponent(e.getKey(), e.getValue().size()))
            .sorted(Comparator.comparing(Opponent::numGames).reversed().thenComparing(Opponent::id))
            .limit(maxOpponents)
            .toList();

        opponentList.forEach(opponent -> System.out.format("%20s %3d%n", opponent.id(), opponent.numGames()));

        deleteGeneratedToken.run();
    }

    static Map<String, List<Game>> fetchGamesAgainstHumanOpponents(String userId, Client client) {

        Predicate<String> notSelf = Predicate.not(userId::equals);

        Predicate<Game> onlyHumans = game -> Stream.of(
                game.players().white(),
                game.players().black())
            .allMatch(player -> player instanceof User);

        Function<Game, String> opponentName = game -> Stream.of(
                game.players().white(),
                game.players().black())
            .map(User.class::cast)
            .map(User::id)
            .filter(notSelf)
            .findAny().orElseThrow();

        int totalGames = client.users().byId(userId).get().count().all();

        Consumer<Integer> print = number -> System.out.println("Downloaded %d of %d games".formatted(number, totalGames));

        print.accept(0);

        if (totalGames == 0) return Map.of();

        int every100games = 100;
        var adder = new LongAdder();

        Consumer<Game> progress = every100games == 0 ? __ -> {} :
            __ -> {
                adder.increment();
                if (adder.intValue() % every100games == 0) {
                    print.accept(adder.intValue());
                }
            };

        var map = client.games().byUserId(userId).stream()
            .peek(progress)
            .filter(onlyHumans)
            .collect(Collectors.groupingBy(opponentName));

        print.accept(totalGames);

        return map;
    }

    public static void main(String[] args) throws Exception {
        int exitCode = new picocli.CommandLine(new opponents()).execute(args);
        System.exit(exitCode);
    }
}
