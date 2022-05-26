//DEPS io.github.tors42:chariot:0.0.34
//JAVA 18+
//JAVAC_OPTIONS --enable-preview --release 18
//JAVA_OPTIONS  --enable-preview

import java.time.Duration;
import java.time.ZonedDateTime;
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
                this tool doesn't know about all tournament formats,
                but tries to estimate a result table based on number
                of wins etc.

                """);

        var client = chariot.Client.basic();

        // 1. Get official broadcast list, show ongoing. Todo, option to show finished too
        var notFinishedBroadcasts = client.broadcasts().official().stream()
            .filter(broadcast -> broadcast.rounds().stream().anyMatch(round -> round.finished() == false))
            .toList();

        Broadcast broadcast = null;
        if (args.length > 0) {
            try {
                int input = Integer.parseInt(args[0]);
                broadcast = notFinishedBroadcasts.get(input-1);
            } catch(Exception e) {
                // ignore
            }
        }

        if (broadcast == null) {
            // 2. Interactively let use choose a broadcast
            broadcast = interactivelyPickOne(notFinishedBroadcasts, bc -> bc.tour().name()).orElseThrow();
        }

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

    record SubEvent(String name, List<Round> rounds, BiFunction<String, Result, Double> pointsForPlayer) {}

    static List<SubEvent> parseSubEvents(Broadcast broadcast) {
        var allRounds = broadcast.rounds().stream().sorted(Comparator.comparingLong(Round::startsTime)).toList();

        if (allRounds.isEmpty()) return List.of();

        String firstWord = firstWord(allRounds.get(0));
        List<SubEvent> subEvents = new ArrayList<>();
        List<Round> eventRounds = new ArrayList<>();
        for (int i = 0; i < allRounds.size(); i++) {
            var round = allRounds.get(i);

            if (! round.name().startsWith(firstWord)) {
                subEvents.add(new SubEvent(firstWord, List.copyOf(eventRounds), pointsForPlayer(broadcast.tour().name())));
                eventRounds.clear();
                firstWord = firstWord(round);
            }

            eventRounds.add(round);
        }

        String name = subEvents.isEmpty() ? broadcast.tour().name() : firstWord;
        subEvents.add(new SubEvent(name, List.copyOf(eventRounds), pointsForPlayer(broadcast.tour().name())));

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
            var duration = Duration.between(ZonedDateTime.now(), round.startsAt());

            String time = switch(duration) {
                case Duration d && d.isPositive() && d.toMinutes() <= 60 -> "in %d minutes".formatted(d.toMinutes());
                case Duration d && d.isPositive() && d.toHours() <= 24 -> "in %d hours".formatted(d.toHours());
                case Duration d && d.isPositive() && d.toDays() <= 7 -> "in %d days".formatted(d.toDays());
                case Duration d && d.isNegative() && Math.abs(d.toMinutes()) <= 60 -> "%d minutes ago".formatted(Math.abs(d.toMinutes()));
                case Duration d && d.isNegative() && Math.abs(d.toHours()) <= 24 -> "%d hours ago".formatted(Math.abs(d.toHours()));
                case Duration d && d.isNegative() && Math.abs(d.toDays()) <= 7 -> "%d days ago".formatted(Math.abs(d.toDays()));

                default -> "at %s".formatted(round.startsAt().toLocalDate());
            };
            System.out.println(" %s %s - %s".formatted(icon, round.name(), round.ongoing() ? "ongoing" : time));
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

        Map<String, List<Result>> resultsByPlayer = players.stream()
            .collect(Collectors.toMap(
                player -> player,
                player -> finishedGames.stream()
                            .filter(pgn -> pgn.tags().contains(new Tag("White", player)) ||
                                           pgn.tags().contains(new Tag("Black", player)))
                            .map(pgn -> {
                                String white = pgn.tagMap().get("White");
                                String opponent = white.equals(player) ? pgn.tagMap().get("Black") : white;
                                return (Result) switch(pgn.tagMap().getOrDefault("Result", "")) {
                                    case String s && s.equals("1/2-1/2") -> Result.draw(player, opponent, player.equals(white));
                                    case String s && s.equals("1-0")     -> Result.whiteWin(player, opponent, player.equals(white));
                                    case String s && s.equals("0-1")     -> Result.blackWin(player, opponent, player.equals(white));
                                    case String s && s.equals("*")       -> new Result.Ongoing(player, opponent);
                                    default                              -> new Result.NoResult(player, opponent);
                                };
                            })
                            .toList()));

        List<Total> totals = players.stream()
            .map(player -> new Total(
                        player,
                        resultsByPlayer.getOrDefault(player, List.of())
                            .stream()
                            .mapToDouble(result -> subEvent.pointsForPlayer().apply(player, result))
                            .sum()))
            .sorted(comparator(resultsByPlayer.values().stream().flatMap(List::stream).toList()))
            .toList();

        System.out.println();
        System.out.println("Standings:");
        for (var total : totals) {
            System.out.println("%3s %s".formatted(format(total.points()), total.player()));
        }
    }

    static String format(double points) {
        return (int) points == points ? String.valueOf((int)points) : String.valueOf(points);
    }

    record Total(String player, double points) {}

    sealed interface Result {
        String player();
        String opponent();

        static Result draw(String player, String opponent, boolean white) {
            return white ? new Draw.White(player, opponent) : new Draw.Black(player, opponent);
        }

        static Result whiteWin(String player, String opponent, boolean white) {
            return white ? new Win.White(player, opponent) : new Loss.White(player, opponent);
        }

        static Result blackWin(String player, String opponent, boolean white) {
            return white ? new Loss.White(player, opponent) : new Win.Black(player, opponent);
        }


        sealed interface Win extends Result {
            record White(String player, String opponent) implements Win {}
            record Black(String player, String opponent) implements Win {}
        }

        sealed interface Draw extends Result {
            record White(String player, String opponent) implements Draw {}
            record Black(String player, String opponent) implements Draw {}
         }
        sealed interface Loss extends Result {
            record White(String player, String opponent) implements Loss {}
            record Black(String player, String opponent) implements Loss {}
         }

        record Ongoing(String player, String opponent) implements Result {}
        record NoResult(String player, String opponent) implements Result {}
    }

    static BiFunction<String, Result, Double> pointsForPlayer(String event) {
        return switch(event) {
            case String s && s.toLowerCase().contains("champions chess tour") ->
                (player, result) -> Double.valueOf(switch(result) {
                case Result.Win w  && w.player().equals(player) -> 3;
                case Result.Loss l && l.player().equals(player) -> 0;
                case Result.Draw d -> 1;
                default -> 0;
            });
            default ->
                (player, result) -> Double.valueOf(switch(result) {
                case Result.Win w  && w.player().equals(player) -> 1;
                case Result.Loss l && l.player().equals(player) -> 0;
                case Result.Draw d -> 0.5;
                default -> 0;
            });
        };
    }

    static Comparator<Total> comparator(List<Result> results) {
        var points = Comparator.comparingDouble(Total::points);
        var directEncounters = (Comparator<Total>) (t1, t2) ->
                Integer.compare(
                    (int) results.stream()
                            .filter(result -> result.player().equals(t1.player()))
                            .filter(result -> result.opponent().equals(t2.player()))
                            .filter(result -> result instanceof Result.Win)
                            .count(),
                    (int) results.stream()
                            .filter(result -> result.player().equals(t2.player()))
                            .filter(result -> result.opponent().equals(t1.player()))
                            .filter(result -> result instanceof Result.Win)
                            .count()
                    );
        var wins = (Comparator<Total>) (t1, t2) ->
                Integer.compare(
                    (int) results.stream()
                            .filter(result -> result.player().equals(t1.player()))
                            .filter(result -> result instanceof Result.Win)
                            .count(),
                    (int) results.stream()
                            .filter(result -> result.player().equals(t2.player()))
                            .filter(result -> result instanceof Result.Win)
                            .count()
                        );
        var winsWithBlack = (Comparator<Total>) (t1, t2) ->
                Integer.compare(
                    (int) results.stream()
                            .filter(result -> result.player().equals(t1.player()))
                            .filter(result -> result instanceof Result.Win.Black)
                            .count(),
                    (int) results.stream()
                            .filter(result -> result.player().equals(t2.player()))
                            .filter(result -> result instanceof Result.Win.Black)
                            .count()
                        );

        return points
            .thenComparing(directEncounters)
            .thenComparing(wins)
            .thenComparing(winsWithBlack)
            .reversed();
    }
}
