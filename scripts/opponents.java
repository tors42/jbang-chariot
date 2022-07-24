//DEPS io.github.tors42:chariot:0.0.43
//JAVA 17+
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.*;
import java.util.stream.*;

import chariot.Client;
import chariot.model.Game;
import chariot.model.GameUser.User;

class opponents {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("""
                Run the program with the following parameters,

                    $ opponents <userId> [maxNumberOfOpponents]

                userId: The Lichess user id of the user you want to check top opponents for
                maxNumberOfOpponents: (Optional) A limit of how many opponents you want to show (default 10)
                """);
            return;
        }

        String userId = args[0];
        int maxOpponents = args.length > 1 ? Integer.valueOf(args[1]) : 10;

        printTopOpponents(userId, maxOpponents);
    }

    static void printTopOpponents(String userId, int maxOpponents) {

        var map = fetchGamesAgainstHumanOpponents(userId);

        // Todo, include W/D/L?
        record Opponent(String id, int numGames) {}

        var opponentList = map.entrySet().stream()
            .map(e -> new Opponent(e.getKey(), e.getValue().size()))
            .sorted(Comparator.comparing(Opponent::numGames).reversed().thenComparing(Opponent::id))
            .limit(maxOpponents)
            .toList();

        opponentList.forEach(opponent -> System.out.format("%20s %3d%n", opponent.id(), opponent.numGames()));
    }

    static Map<String, List<Game>> fetchGamesAgainstHumanOpponents(String userId) {

        Predicate<String> notSelf = Predicate.not(userId::equals);

        Predicate<Game> onlyHumans = game -> Stream.of(
                game.players().white(),
                game.players().black())
            .allMatch(player -> player instanceof User);

        Function<Game, String> opponentName = game -> Stream.of(
                game.players().white(),
                game.players().black())
            .map(u -> (User) u)
            .map(u -> u.user().id())
            .filter(notSelf)
            .findAny().orElseThrow();

        var client = Client.basic();

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

}
