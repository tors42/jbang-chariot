//DEPS io.github.tors42:chariot:0.0.50
//JAVA 18+
//JAVAC_OPTIONS --enable-preview --release 18
//JAVA_OPTIONS  --enable-preview

import chariot.model.Pgn;
import chariot.model.Pgn.*;

import java.nio.file.*;
import java.util.*;

class flatten {

    static List<Pgn> flattenPgn(Pgn pgn) {
        List<Move> initialMoveList = pgn.moveList();
        List<List<Move>> listOfMoveListsBeingFlattened = new ArrayList<>();
        listOfMoveListsBeingFlattened.add(initialMoveList);
        while(true) {
            List<List<Move>> newLists = new ArrayList<>();
            boolean foundVariation = false;
            for (var list : listOfMoveListsBeingFlattened) {
                List<Move> newList = new ArrayList<>(list.size());
                newLists.add(newList);
                for (int i = 0; i < list.size(); i++) {
                    if (!foundVariation && list.get(i) instanceof Variation v) {
                        List<Move> variationList = new ArrayList<>(list.subList(0, i-1));
                        variationList.addAll(v.variation());
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
            listOfMoveListsBeingFlattened = newLists;
            if (! foundVariation) {
                break;
            }
        }

        List<Pgn> pgns = new ArrayList<>();
        final int lines = listOfMoveListsBeingFlattened.size();
        for (int i = 0; i < lines; i++) {
            final int count = i+1;
            String pgnData = String.join(" ", listOfMoveListsBeingFlattened.get(i).stream().map(Move::render).toList());
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
