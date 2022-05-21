//DEPS io.github.tors42:chariot:0.0.33
//JAVA 17+

import java.util.stream.Collectors;
import static java.util.function.Predicate.not;

import chariot.Client;
import chariot.model.Game.Entry.Oops;
import chariot.model.Game.Entry.Oops.Judgment.Name;
import chariot.model.GameUser;

class evalsummary {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) { System.out.println("Supply a game id"); return; }
        String gameId = args[0].length() > 8 ? args[0].substring(0,8) : args[0];

        var client = Client.basic();

        var game = client.games().byGameId(gameId).get();

        if (game.analysis().isEmpty()) {
            System.out.println("""
                The game has not been analyzed. Try with an analyzed game.

                White (%s)
                 Inaccuracies: ?
                 Mistakes:     ?
                 Blunders:     ?

                Black (%s)
                 Inaccuracies: ?
                 Mistakes:     ?
                 Blunders:     ?
                 """.formatted(
                     name(game.players().white()),
                     name(game.players().black())));
             return;
        }

        record Side(boolean white, Oops oops) {}

        var allOops = game.analysis().stream()
            .filter(Oops.class::isInstance)
            .map(Oops.class::cast)
            .map(oops -> new Side(game.analysis().indexOf(oops) % 2 == 0, oops))
            .toList();

        var whiteStats = allOops.stream()
            .filter(Side::white)
            .map(Side::oops)
            .collect(Collectors.groupingBy(oops -> oops.judgment().name(), Collectors.counting()));

        var blackStats = allOops.stream()
            .filter(not(Side::white))
            .map(Side::oops)
            .collect(Collectors.groupingBy(oops -> oops.judgment().name(), Collectors.counting()));

        System.out.println("""
                White (%s)
                 Inaccuracies: %2d
                 Mistakes:     %2d
                 Blunders:     %2d

                Black (%s)
                 Inaccuracies: %2d
                 Mistakes:     %2d
                 Blunders:     %2d
                """.formatted(
                    name(game.players().white()),
                    whiteStats.getOrDefault(Name.Inaccuracy, 0l),
                    whiteStats.getOrDefault(Name.Mistake, 0l),
                    whiteStats.getOrDefault(Name.Blunder, 0l),
                    name(game.players().black()),
                    blackStats.getOrDefault(Name.Inaccuracy, 0l),
                    blackStats.getOrDefault(Name.Mistake, 0l),
                    blackStats.getOrDefault(Name.Blunder, 0l)
                    ));
    }

    // todo, add this to GameUser
    static String name(GameUser user) {
        if (user instanceof GameUser.Anonymous) return "Anonymous";
        if (user instanceof GameUser.User u) return u.user().name();
        if (user instanceof GameUser.Computer c) return "Stockfish level " + c.aiLevel();
        return "<unknown user>";

        // --enable-preview
        //return switch(user) {
        //    case GameUser.Anonymous a -> "Anonymous";
        //    case GameUser.User u -> u.user().name();
        //    case GameUser.Computer c -> "Stockfish level " + c.aiLevel();
        //};
    }
}
