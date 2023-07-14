//DEPS io.github.tors42:chariot:0.0.68
//JAVA 17+
import chariot.*;
import chariot.Client.*;
import chariot.model.*;

import java.util.prefs.Preferences;
import java.time.Duration;

class updateArena {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Expected <arenaId> and <description>");
            System.exit(1);
        }
        update(args[0], args[1]);
    }

    static void update(String arenaId, String description) {

        ClientAuth client = initializeClient();

        if (! (client.tournaments().arenaById(arenaId) instanceof Entry<Arena> one)) {
            System.err.println("Couldn't find arena with id " + arenaId);
            return;
        }
        Arena arena = one.entry();

        var result = client.tournaments().updateArena(arenaId, params -> params
                .clock(Duration.ofSeconds(arena.clock().limit()).toMinutes(), arena.clock().increment())
                .description(description));

        if (result instanceof Fail<?> fail) {
            System.err.println("Couldn't update arena description: " + fail);
            return;
        }
    }

    static ClientAuth initializeClient() {
        Preferences prefs = Preferences.userRoot().node("updateArena");
        var client = Client.load(prefs);

        if (client instanceof ClientAuth auth
            && auth.scopes().contains(Client.Scope.tournament_write)) {
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
