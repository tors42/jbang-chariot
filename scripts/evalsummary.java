//DEPS io.github.tors42:chariot:0.0.44
//JAVA 17+

import chariot.Client;

class evalsummary {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) { System.out.println("Supply a game id"); return; }
        String gameId = args[0].length() > 8 ? args[0].substring(0,8) : args[0];

        var client = Client.basic();

        client.games().byGameId(gameId).ifPresent(game -> {

            if (game.analysis().isEmpty()) {
                System.out.println("""
                    The game has not been analyzed. Try with an analyzed game.

                    White (%s)
                     Inaccuracies: ?
                     Mistakes:     ?
                     Blunders:     ?
                     ACPL:         ?

                    Black (%s)
                     Inaccuracies: ?
                     Mistakes:     ?
                     Blunders:     ?
                     ACPL:         ?
                     """.formatted(
                         game.players().white().name(),
                         game.players().black().name()));
                 return;
            }

            var wa = game.players().white().analysis().get();
            var ba = game.players().black().analysis().get();

            System.out.println("""
                    White (%s)
                     Inaccuracies: %3d
                     Mistakes:     %3d
                     Blunders:     %3d
                     ACPL:         %3d

                    Black (%s)
                     Inaccuracies: %3d
                     Mistakes:     %3d
                     Blunders:     %3d
                     ACPL:         %3d
                    """.formatted(
                        game.players().white().name(),
                        wa.inaccuracy(), wa.mistake(), wa.blunder(), wa.acpl(),
                        game.players().black().name(),
                        ba.inaccuracy(), ba.mistake(), ba.blunder(), ba.acpl()
                        ));
            });
    }
}
