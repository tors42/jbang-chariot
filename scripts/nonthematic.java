//DEPS io.github.tors42:chariot:0.0.32
//JAVA 17+
import chariot.Client;
import chariot.model.Enums.GameVariant;
import java.util.concurrent.atomic.LongAdder;
import java.nio.file.*;

class nonthematic {

    public static void main(String... args) throws Exception {
        if (args.length == 0) { System.out.println("No username provided"); System.exit(1); }
        String username = args[0];

        var client = Client.basic(); // speedup: var client = Client.auth("token")

        var result = client.users().byId(username);
        if (! result.isPresent()) { System.out.println("Couldn't find %s".formatted(username)); System.exit(0); }
        System.out.println("Fetching games of " + username);

        var user = result.get();
        var path = Path.of(username + ".pgn");
        var writer = Files.newBufferedWriter(path,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        int total = user.count().all();
        var counter = new LongAdder();
        var written = new LongAdder();

        client.games().byUserId(user.id(), p -> p.pgnInJson(true)).stream()
            .peek(__ -> {
                counter.increment();
                System.out.print("\r%d/%d".formatted(counter.intValue(), total));
            })
            .filter(game -> game.variant() != GameVariant.fromPosition)
            .forEach(game -> {
                try {
                    writer.append(game.pgn());
                    writer.flush();
                    written.increment();
                } catch(Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }});

        if (written.intValue() > 0) {
            System.out.format("%nWrote %d non-thematic games to %s%n", written.intValue(), path);
        } else {
            System.out.println("\nNo non-thematic games found!");
        }
    }
}
