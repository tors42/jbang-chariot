//DEPS io.github.tors42:chariot:0.0.46
//JAVA 17+
import chariot.Client;

class membercount {

    public static void main(String[] args) {
        String teamId = args.length == 1 ? args[0] : "lichess-swiss";

        var client = Client.basic();

        String message = client.teams().byTeamId(teamId)
            .map(team -> "%s has %d members!%n".formatted(team.name(), team.nbMembers()))
            .orElse("Couldn't find team with id %s".formatted(teamId));

        System.out.println(message);
    }
}
