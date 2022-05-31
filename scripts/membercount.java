//DEPS io.github.tors42:chariot:0.0.35
//JAVA 17+
import chariot.Client;

class membercount {

    static void usage() {
        System.out.println(
                """
                Usage: membercount <teamId>

                 teamId (default: lichess-swiss)

                     The id of the team to check number of members for.
                     The id can be found as the last part of the URL of the team page,
                     https://lichess.org/team/lichess-swiss

                Examples:

                 $ membercount the-chess-lounge
                 The Chess Lounge has 593 members!

                 $ membercount
                 Lichess Swiss has 212781 members!

                """
                );
    }

    public static void main(String[] args) {
        String teamId = args.length == 1 ? args[0] : "lichess-swiss";

        var client = Client.basic();

        var result = client.teams().byTeamId(teamId);

        if (! result.isPresent()) {
            System.out.println("Couldn't find %s".formatted(teamId));
            usage();
            System.exit(0);
        }

        var team = result.get();
        System.out.println("%s has %d members!%n".formatted(team.name(), team.nbMembers()));
    }
}
