//DEPS io.github.tors42:chariot:0.0.46
//JAVA 17+
import chariot.Client;
import chariot.model.Enums.GameVariant;
import java.nio.file.*;
import java.util.concurrent.atomic.LongAdder;

class nonthematic {

    public static void main(String... args) throws Exception {
        if (args.length == 0) { System.out.println("No username provided"); return; }
        String username = args[0];
        var path = Path.of(username + ".pgn");
        var writer = Files.newBufferedWriter(path,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        var client = Client.basic(); // speedup: var client = Client.auth("token")

        client.users().byId(username).ifPresentOrElse(user -> {
            System.out.println("Fetching games of " + username);

            int total = user.count().all();
            var counter = new LongAdder();
            var written = new LongAdder();

            client.games().byUserId(user.id(), p -> p.pgnInJson(true)).stream()
                .peek(__ -> {
                    counter.increment();
                    System.out.format("\r%d/%d", counter.intValue(), total);
                })
                .filter(game -> game.variant() != GameVariant.fromPosition)
                .forEach(game -> {
                    try {
                        writer.append(game.pgn());
                        writer.flush();
                        written.increment();
                    } catch(Exception e) {
                        throw new RuntimeException(e);
                    }});

            if (written.intValue() > 0) {
                System.out.format("%nWrote %d non-thematic games to %s%n",
                        written.intValue(), path);
            } else {
                System.out.println("\nNo non-thematic games found!");
            }
        },
        () -> System.out.println("Couldn't find " + username));
    }
}
