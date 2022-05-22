//DEPS io.github.tors42:chariot:0.0.34
//JAVA 18+
//JAVAC_OPTIONS --enable-preview --release 18
//JAVA_OPTIONS  --enable-preview

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import chariot.Client;
import chariot.model.Broadcast;
import chariot.model.Broadcast.Round;

class autoscore {

    record Tag(String name, String value) {
        static Tag parse(String line) {
            return new Tag(
                    line.substring(1, line.indexOf(' ')),
                    line.substring(line.indexOf('"')+1, line.length()-2)
                    );
        }
        @Override
        public String toString() { return "[%s \"%s\"]".formatted(name, value); }
    }

    record Pgn(List<Tag> tags, String moves) {
        @Override
        public String toString() {
            return String.join("\n\n",
                    String.join("\n", tags.stream().map(Object::toString).toList()),
                    moves
                    );
        }

        Map<String, String> tagMap() {
            return tags().stream().collect(Collectors.toMap(Tag::name, Tag::value));
        }
    }

    /**
     * An iterator of Pgn-modelled games.
     * It lazily reads line after line of PGN data, possibly many games,
     * and assembles these lines into Pgn models.
     */
    record PgnSpliterator(Iterator<String> iterator) implements Spliterator<Pgn> {
        @Override
        public boolean tryAdvance(Consumer<? super Pgn> action) {
            List<String> tagList = readGroup(iterator);
            List<String> moveList = readGroup(iterator);
            if (tagList.isEmpty() && moveList.isEmpty()) return false;

            var moves = String.join(" ", moveList);
            var tags = tagList.stream().map(Tag::parse).toList();
            action.accept(new Pgn(tags, moves));
            return true;
        }

        List<String> readGroup(Iterator<String> iterator) {
            var list = new ArrayList<String>();
            while (iterator.hasNext()) {
                String line = iterator.next();
                if (! line.isBlank()) {
                    list.add(line);
                    continue;
                }
                if (! list.isEmpty()) break;
            }
            return list;
        }

        @Override public Spliterator<Pgn> trySplit() { return null; }
        @Override public long estimateSize() { return Long.MAX_VALUE; }
        @Override public int characteristics() { return ORDERED; }
    }

    public static void main(String[] args) {

        System.out.println("""

                Note,
                this tool doesn't know about any specific tournament rules, i.e
                if points are awarded in some special way, or if players are
                eliminated in rounds etc etc. It tries to estimate a result
                table based on games played with Win 1, Draw 0.5, Loss 0

                """);

        var client = chariot.Client.basic();

        // 1. Get official broadcast list, show ongoing. Todo, option to show finished too
        var notFinishedBroadcasts = client.broadcasts().official().stream()
            .filter(broadcast -> broadcast.rounds().stream().anyMatch(round -> round.finished() == false))
            .toList();

        // 2. Interactively let use choose a broadcast
        var broadcast = interactivelyPickOne(notFinishedBroadcasts, bc -> bc.tour().name()).orElseThrow();

        // 3. Render the results table for that tournament
        render(client, broadcast);

    }

    static <T> Optional<T> interactivelyPickOne(List<T> choices, Function<T, String> toString) {
        if (choices.isEmpty()) return Optional.empty();

        System.out.println("Ongoing Broadcasts");
        for (int i = 0; i < choices.size(); i++)
            System.out.println("%2d. %s".formatted(i+1, toString.apply(choices.get(i))));

        boolean choosing = true;
        do {
            System.out.print("\nPick a tournament [1-%d]: ".formatted(choices.size()));
            String line = java.util.Objects.toString(System.console().readLine(), "").trim();
            try {
                int choice = Integer.parseInt(line);
                if (choice >= 1 && choice <= choices.size()) return Optional.of(choices.get(choice-1));
            } catch (Exception e) {}
        } while(choosing);
        return Optional.empty();
    }

    static void render(Client client, Broadcast broadcast) {
        var subEventList = parseSubEvents(broadcast);
        if (subEventList.size() > 1) {
            System.out.println(broadcast.tour().name());
        }
        for (var subEvent : subEventList) {
            render(client, subEvent);
        }
    }

    record SubEvent(String name, List<Round> rounds) {}

    static List<SubEvent> parseSubEvents(Broadcast broadcast) {
        var allRounds = broadcast.rounds().stream().sorted(Comparator.comparingLong(Round::startsTime)).toList();

        if (allRounds.isEmpty()) return List.of();

        String firstWord = firstWord(allRounds.get(0));
        List<SubEvent> subEvents = new ArrayList<>();
        List<Round> eventRounds = new ArrayList<>();
        for (int i = 0; i < allRounds.size(); i++) {
            var round = allRounds.get(i);

            if (! round.name().startsWith(firstWord)) {
                subEvents.add(new SubEvent(firstWord, List.copyOf(eventRounds)));
                eventRounds.clear();
                firstWord = firstWord(round);
            }

            eventRounds.add(round);
        }

        String name = subEvents.isEmpty() ? broadcast.tour().name() : firstWord;
        subEvents.add(new SubEvent(name, List.copyOf(eventRounds)));

        return subEvents;
    }

    static String firstWord(Round round) {
        String firstWord = round.name().split(" ")[0];
        if (firstWord.endsWith(":")) firstWord = firstWord.substring(0, firstWord.length()-1);
        return firstWord;
    }

    static void render(Client client, SubEvent subEvent) {
        System.out.println();
        System.out.println("%s".formatted(subEvent.name()));
        subEvent.rounds().stream().forEach(round -> {
            String icon = switch(round) {
                case Round r && r.finished() -> "\uD83D\uDD35";
                case Round r && r.ongoing()  -> "\uD83D\uDD34";
                default                      -> "\u2B55";
            };
            System.out.println(" %s %s".formatted(icon, round.name()));
        });
        //:

        // Todo, render ongoing games too

        var finishedRounds = subEvent.rounds().stream().filter(r -> r.finished()).toList();
        if (finishedRounds.isEmpty()) return;

        var finishedGames = finishedRounds.stream()
            .flatMap(round ->
                    StreamSupport.stream(
                        new PgnSpliterator(
                            client.broadcasts().exportOneRoundPgn(round.id())
                            .stream()
                            .iterator()),
                        false))
            .toList();

        Set<String> players = finishedGames.stream()
            .flatMap(pgn -> pgn.tags().stream())
            .filter(tag -> tag.name().equals("White") || tag.name().equals("Black"))
            .map(Tag::value)
            .collect(Collectors.toSet());

        Map<String, List<Pgn>> gamesByPlayer = players.stream()
            .collect(Collectors.toMap(
                        player -> player,
                        player -> finishedGames.stream()
                                      .filter(pgn -> pgn.tags().contains(new Tag("White", player)) ||
                                                     pgn.tags().contains(new Tag("Black", player)))
                                      .toList()));

        var totals = players.stream()
            .map(player -> new Total(
                        player,
                        gamesByPlayer.getOrDefault(player, List.of())
                            .stream()
                            .mapToDouble(pgn -> pointsForPlayer(player, pgn))
                            .sum()))
            .sorted(comparator(finishedGames))
            .toList();

        System.out.println();
        System.out.println("Standings:");
        for (var total : totals) {
            System.out.println("%3s %s".formatted(total.points(), total.player()));
        }
    }

    record Total(String player, double points) {}

    static double pointsForPlayer(String player, Pgn pgn) {
        return switch(pgn.tagMap().getOrDefault("Result", "*")) {
            case String s && s.equals("1/2-1/2") -> 0.5;
            case String s && s.equals("1-0") && pgn.tagMap().getOrDefault("White", "").equals(player) -> 1.0;
            case String s && s.equals("0-1") && pgn.tagMap().getOrDefault("Black", "").equals(player) -> 1.0;
            default -> 0;
        };
    }

    static Comparator<Total> comparator(List<Pgn> allGames) {
        return Comparator.comparingDouble(Total::points)
            .reversed()
            .thenComparing( (t1, t2) -> {
                var gamesBetweenBoth = allGames.stream()
                    .filter(pgn -> Set.of(t1.player(), t2.player())
                            .containsAll(List.of(pgn.tagMap().getOrDefault("White",""),
                                                 pgn.tagMap().getOrDefault("Black","")))
                           )
                    .toList();
                double player1Points = gamesBetweenBoth.stream()
                    .mapToDouble(pgn -> pointsForPlayer(t1.player(), pgn))
                    .sum();
                double player2Points = gamesBetweenBoth.stream()
                    .mapToDouble(pgn -> pointsForPlayer(t2.player(), pgn))
                    .sum();
                return Double.compare(player1Points, player2Points);
            });
    }
}
