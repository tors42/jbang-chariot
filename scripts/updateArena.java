//DEPS io.github.tors42:chariot:0.1.8
//JAVA 21+
import chariot.*;
import chariot.Client.*;
import chariot.model.*;

import java.util.prefs.Preferences;
import java.time.Duration;

class updateArena {

    public static void main(String[] args) {

        ClientAuth client = initializeClient();

        var res = client.account().profile();

        if (! (res instanceof Entry<UserAuth> profileResult)) {
            System.err.println("Couldn't find user " + res);
            return;
        }

        var user = profileResult.entry();

        var nonStartedTournament = client.tournaments()
            .arenasCreatedByUserId(user.id(), TourInfo.Status.created)
            .stream().findFirst();

        if (nonStartedTournament.isEmpty()) {
            System.out.println("Couldn't find an upcoming arena, so nothing to link old tournaments to");
            return;
        }

        ArenaLight toLink = nonStartedTournament.get();

        String link = "Updated Link: https://lichess.org/tournament/" + toLink.id();

        var endedTournamentWithoutGames = client.tournaments()
            .arenasCreatedByUserId(user.id(), TourInfo.Status.finished)
            .stream()
            .filter(tournament -> client.tournaments().gamesByArenaId(tournament.id())
                    .stream()
                    .allMatch(game -> !game.moves().isPresent()))
            .map(tournament -> client.tournaments().arenaById(tournament.id()))
            .filter(One::isPresent)
            .map(One::get)
            .findFirst();

        if (endedTournamentWithoutGames.isEmpty()) {
            System.out.println("Couldn't find finished arena without games");
            return;
        }

        Arena tournament = endedTournamentWithoutGames.get();

        if (tournament.tourInfo().description() instanceof Some<String> d && d.value().contains("Updated Link:")) {
            System.out.println("Previous finished arena without games was already linked\n" + d.value());
            return;
        }

        System.out.println("Found non-linked arena with only empty games: " + tournament.id());

        var result = client.tournaments().updateArena(tournament.id(), params -> params
                .clock(tournament.tourInfo().clock())
                .description(link));

        if (result instanceof Fail<?> fail) {
            System.err.println("Couldn't update arena description: " + fail);
            return;
        }

        System.out.println("Updated description:\n" + link);
    }

    static ClientAuth initializeClient() {
        Preferences prefs = Preferences.userRoot().node("updateArena");
        var client = Client.load(prefs);

        if (client instanceof ClientAuth auth
            && auth.scopes().stream().toList().contains(Client.Scope.tournament_write)) {
            return auth;
        }

        var authResult = client.withPkce(uri -> System.out.println("""

            Visit %s and choose to grant access to this application or not.

            """.formatted(uri)),
            pkce -> pkce.scope(Client.Scope.tournament_write));

        if (! (authResult instanceof AuthOk ok)) {
            System.err.println("Authentication failed: " + authResult);
            System.exit(1);
            return null;
        }

        ok.client().store(prefs);
        return ok.client();
    }

}
