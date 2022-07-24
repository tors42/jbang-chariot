//DEPS io.github.tors42:chariot:0.0.43
//JAVA 18+
//JAVAC_OPTIONS --enable-preview --release 18
//JAVA_OPTIONS  --enable-preview

import chariot.model.Pgn;

import java.nio.file.*;
import java.util.*;

class flatten {

    record HeadTail(Moves head, Moves tail) implements Moves {}
    record Variation(Moves variation)       implements Moves {}
    record Result(String result)            implements Moves {}
    record NumBegin(int move, String san)   implements Moves {}
    record NumEnd(int move, String san)     implements Moves {}
    record Empty()                          implements Moves {}
    record End(String san)                  implements Moves {}
    record Comment(String comment)          implements Moves {}

    sealed interface Moves {

        static String render(Moves moves) {
            return switch(moves) {
                case HeadTail ht && twoVariationsInRowDoubleSpaceWorkaround(ht)
                                 -> "%s  %s".formatted( render(ht.head()), render(ht.tail()) );
                case HeadTail ht -> "%s %s" .formatted( render(ht.head()), render(ht.tail()) );
                case Variation v -> "(%s)".formatted( render(v.variation()) );
                case NumBegin nb -> "%d. %s".formatted(nb.move(), nb.san());
                case NumEnd ne   -> "%d... %s".formatted(ne.move(), ne.san());
                case Comment c   -> "{%s}".formatted(c.comment());
                case Empty e     -> "";
                case End e       -> e.san();
                case Result r    -> r.result();
            };
        }

        static void flatList(Moves moves, List<Moves> resultList) {
            switch(moves) {
                case HeadTail ht -> {
                    resultList.add(ht.head());
                    flatList(ht.tail(), resultList);
                }
                case Moves m -> resultList.add(m);
            };
        }

        // Allows for input == output in: String input -> Model -> String output
        static boolean twoVariationsInRowDoubleSpaceWorkaround(HeadTail ht) {
            return ht.head() instanceof Variation &&
                (ht.tail() instanceof Variation ||
                 ht.tail() instanceof HeadTail tail && tail.head() instanceof Variation);
        }

        static Moves parse(String moves) {
            return switch(moves.trim()) {
                case String s && s.isEmpty()
                    -> new Empty();
                case String s && Set.of("*", "1-0", "0-1", "1/2-1/2").contains(s)
                    -> new Result(s);
                case String s && Character.isDigit(s.charAt(0))
                    -> {
                        int dotPos = s.indexOf(".");
                        int move = Integer.parseInt(s.substring(0, dotPos));
                        boolean manyDots = s.charAt(dotPos+1) == '.';
                        int sanBegin = s.indexOf(" ", dotPos+1);
                        while(Character.isWhitespace(s.charAt(sanBegin))) sanBegin++;
                        int sanEnd = indexOfOrEnd(" ", sanBegin, s);
                        String san = s.substring(sanBegin, sanEnd);
                        yield chain(manyDots ?
                                new NumEnd(move, san) : new NumBegin(move, san),
                                parse(s.substring(sanEnd)));
                    }
                case String s && '(' == s.charAt(0)
                    -> {
                        boolean inComment = false;
                        int nest = 1; int pos = 1;
                        while (nest != 0) {
                            inComment = switch(s.charAt(pos)) {
                                case '{' -> true;
                                case '}' -> false;
                                default -> inComment;
                            };
                            if (! inComment) {
                                nest = switch(s.charAt(pos)) {
                                    case '(' -> nest+1;
                                    case ')' -> nest-1;
                                    default -> nest;
                                };
                            }
                            pos++;
                        }
                        var variation = parse(s.substring(1, pos-1));
                        yield chain(new Variation(variation), parse(s.substring(pos)));
                    }
                case String s && '{' == s.charAt(0)
                    -> {
                        int end = s.indexOf('}');
                        yield chain(new Comment(s.substring(1, end)), parse(s.substring(end+1)));
                    }
                case String s
                    -> {
                        int sanEnd = indexOfOrEnd(" ", 0, s);
                        yield chain(new End(s.substring(0, sanEnd)), parse(s.substring(sanEnd)));
                    }
            };
            //:
        }

        static int indexOfOrEnd(String target, int from, String source) {
            return switch(Integer.valueOf(source.indexOf(target, from))) {
                case Integer position && position == -1 -> source.length();
                case Integer position -> position.intValue();
            };
            //:
        }

        static Moves chain(Moves head, Moves tail) {
            return tail instanceof Empty ? head : new HeadTail(head, tail);
        }
    }

    static List<Pgn> flattenPgn(Pgn pgn) {
        List<Moves> flatList = new ArrayList<>();
        Moves.flatList(Moves.parse(pgn.moves()), flatList);
        List<List<Moves>> listOfFlatLists = new ArrayList<>();
        listOfFlatLists.add(flatList);
        while(true) {
            List<List<Moves>> newLists = new ArrayList<>();
            boolean foundVariation = false;
            for (var list : listOfFlatLists) {
                List<Moves> newList = new ArrayList<>(list.size());
                newLists.add(newList);
                for (int i = 0; i < list.size(); i++) {
                    if (!foundVariation && list.get(i) instanceof Variation v) {
                        List<Moves> variationList = new ArrayList<>(list.subList(0, i-1));
                        Moves.flatList(v.variation(), variationList);
                        var previousMove = variationList.get(i-2);
                        var variationMove = variationList.remove(i-1);
                        if (previousMove instanceof NumBegin nb && variationMove instanceof NumEnd ne) {
                            variationMove = new End(ne.san());
                        }
                        variationList.add(i-1, variationMove);
                        newLists.add(variationList);
                        foundVariation = true;
                    } else {
                        newList.add(list.get(i));
                    }
                }
            }
            listOfFlatLists = newLists;
            if (! foundVariation) {
                break;
            }
        }

        List<Pgn> pgns = new ArrayList<>();
        final int lines = listOfFlatLists.size();
        for (int i = 0; i < lines; i++) {
            final int count = i+1;
            String pgnData = String.join(" ", listOfFlatLists.get(i).stream().map(Moves::render).toList());
            List<Pgn.Tag> tags = pgn.tags().stream()
                .map(tag -> tag.name().equals("Event") ?
                        Pgn.Tag.of("Event", tag.value() + " (%d/%d)".formatted(count, lines)) :
                        tag
                        )
                .toList();
            pgns.add(Pgn.of(tags, pgnData));
        }
        return pgns;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Supply a study Id");
            System.exit(0);
        }

        String studyId = args[0];

        var client = args.length == 2 ? chariot.Client.auth(args[1]) : chariot.Client.basic();

        var chapterList = client.studies()
            .exportChaptersByStudyId(studyId)
            .stream()
            .toList();


        if (chapterList.isEmpty()) { System.out.println("No chapters found in " + studyId); return; }

        var tmpDir = Files.createTempDirectory("study-" + studyId + "-");

        for (int i = 0; i < chapterList.size(); i++) {
            var pgn = chapterList.get(i);

            var chapterFile = Files.createTempFile(tmpDir, "chapter-%d-".formatted(i+1), ".pgn");
            Files.writeString(chapterFile, pgn.toString() + "\n",
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
            System.out.println("Wrote chapters to " + chapterFile.toAbsolutePath());

            String flattenedPgns = String.join("\n\n", flattenPgn(pgn).stream()
                    .map(Object::toString)
                    .toList());

            var flattenedLinesFile = Files.createTempFile(tmpDir, "flattened-", ".pgn");
            Files.writeString(flattenedLinesFile, flattenedPgns + "\n",
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
            System.out.println("Wrote flattened lines to " + flattenedLinesFile.toAbsolutePath());
        }
    }
}
