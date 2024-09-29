import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        // Check if at least one input file is provided
        if (args.length < 1) {
            System.err.println("Usage: java Main <input1.sql> [<input2.sql> ...]");
            System.exit(1);
        }

        List<String> sqlStatements = new ArrayList<>();

        // Read each SQL file and collect the statements
        for (String filePath : args) {
            // Read the SQL script from the file
            String sqlScript = new String(Files.readAllBytes(Paths.get(filePath))).trim();

            if (!sqlScript.isEmpty()) {
                sqlStatements.add(sqlScript);
            }
        }

        MyTrinoVisitor visitor = new MyTrinoVisitor();
        // Process each SQL statement
        for (String sqlStatement : sqlStatements) {
            // Initialize ANTLR lexer and parser
            TrinoLexer lexer = new TrinoLexer(CharStreams.fromString(sqlStatement));
            TrinoParser parser = new TrinoParser(new CommonTokenStream(lexer));

            // Parse
            ParseTree tree = parser.parse();

            // Visit the parse tree
            visitor.visit(tree);
        }
    }
}