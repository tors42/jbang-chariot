//JAVA 17+
//DEPS io.github.tors42:chariot:0.0.62

import chariot.util.Board;

var input = java.util.Arrays.stream(args).toList();

if (input.isEmpty()) {

    System.out.println("""

        Usage: jbang uci2san.jsh [fen="..."] [moves]

            If not specifying a fen,
            the standard initial position will be used,
            i.e as if fen="rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1" had been specified

            moves to translate to SAN format, i.e
            "e2e4 e7e5 g1f3 b8c6"

        Example:

            jbang uci2san.jsh fen="rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1" e2e4 e7e5 g1f3 b8c6
    """);

}

String fen = input.stream().
    filter(arg -> arg.startsWith("fen=")).
    map(arg -> arg.substring("fen=".length())).
    findFirst().
    orElse(Board.fromStandardPosition().toFEN());

String moves = String.join(" ", input.stream().
    filter(arg -> ! arg.startsWith("fen=")).
    toList());

if (moves.isBlank()) moves = "e2e4 e7e5 g1f3 b8c6";

Board board  = Board.fromFEN(fen);
String sans  = board.toSAN(moves);

System.out.println("fen  : " + fen);
System.out.println("moves: " + moves);
System.out.println("sans : " + sans);


//System.out.println(board.play(moves));
