import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.ParserRuleContext;

public class MyTrinoVisitor extends TrinoParserBaseVisitor<Void> {
    private SymbolTable symbolTable;


    public MyTrinoVisitor() {
        this.symbolTable = new SymbolTable();
    }


    /**
     * Visits a CREATE TABLE statement to extract and store table schema for later use.
     */
    @Override
    public Void visitCreateTable(TrinoParser.CreateTableContext ctx) {
        // Extract table name
        String tableName = ctx.qualifiedName().getText().toLowerCase();

        // Initialize a new TableSchema for the table
        TableSchema tableSchema = new TableSchema(tableName);

        // Iterate over each table element (e.g., column definitions)
        for (TrinoParser.TableElementContext tableElementCtx : ctx.tableElement()) {
            TrinoParser.ColumnDefinitionContext colDefCtx = tableElementCtx.columnDefinition();

            // If the table element is a column definition, extract details
            if (colDefCtx != null) {
                String columnName = colDefCtx.identifier().getText().toLowerCase();
                String dataType = colDefCtx.type().getText().toUpperCase();

                // Add the column to the table schema
                tableSchema.addColumn(columnName, dataType);
            }
        }

        // Add the completed table schema to the symbol table
        symbolTable.addTable(tableSchema);
        System.out.println("Registered table: " + tableName);

        return null;
    }


    /**
     * Visits a SET OPERATION (e.g., UNION) to perform type compatibility checks.
     */
    @Override
    public Void visitSetOperation(TrinoParser.SetOperationContext ctx) {
        // Extract the set operation operator (e.g., UNION)
        String operator = ctx.operator.getText().toUpperCase();

        // We only handle UNION
        if ("UNION".equals(operator)) {
            // Extract left and right QueryTermContexts representing the two SELECT statements
            TrinoParser.QueryTermContext leftTerm = ctx.left;
            TrinoParser.QueryTermContext rightTerm = ctx.right;

            // Extract QuerySpecificationContexts (SELECT statements) from both terms
            TrinoParser.QuerySpecificationContext leftSelect = extractSelectStatement(leftTerm);
            TrinoParser.QuerySpecificationContext rightSelect = extractSelectStatement(rightTerm);

            // Proceed only if both SELECT statements are successfully extracted
            if (leftSelect != null && rightSelect != null) {
                // Process each SELECT statement to retrieve selected columns and their types
                SelectStatement leftSelectStmt = processSelectStatement(leftSelect);
                SelectStatement rightSelectStmt = processSelectStatement(rightSelect);

                // If either SELECT statement processing failed, skip further checks
                if (leftSelectStmt == null || rightSelectStmt == null) {
                    return null;
                }

                // Check if both SELECT statements have the same number of columns
                if (leftSelectStmt.getColumnTypes().size() != rightSelectStmt.getColumnTypes().size()) {
                    System.out.println("Error: SELECT statements in UNION have different number of columns.");
                    System.out.println("UNION operation type check: FAILED");
                    return null;
                }

                System.out.println("UNION operation number check: SUCCESS");

                // Verify type compatibility for each column pair
                boolean isCompatible = true;
                for (int i = 0; i < leftSelectStmt.getColumnTypes().size(); i++) {
                    String leftType = leftSelectStmt.getColumnTypes().get(i);
                    String rightType = rightSelectStmt.getColumnTypes().get(i);

                    if (!leftType.equalsIgnoreCase(rightType)) {
                        System.out.println(String.format(
                                "Error: Column types '%s' and '%s' are not compatible in UNION.",
                                leftType, rightType
                        ));
                        isCompatible = false;
                    }
                }

                if (isCompatible) {
                    System.out.println("UNION operation type check: SUCCESS");
                } else {
                    System.out.println("UNION operation type check: FAILED");
                }
            } else {
                System.out.println("Error: Unable to extract SELECT statements from UNION.");
            }
        }

        return null;
    }


    /**
     * Extracts the QuerySpecificationContext (SELECT statement) from a QueryTermContext.
     */
    private TrinoParser.QuerySpecificationContext extractSelectStatement(TrinoParser.QueryTermContext termCtx) {
        // Check if the QueryTermContext is an instance of QueryTermDefaultContext
        if (termCtx instanceof TrinoParser.QueryTermDefaultContext) {
            TrinoParser.QueryPrimaryContext queryPrimaryCtx = ((TrinoParser.QueryTermDefaultContext) termCtx).queryPrimary();

            // Further check if the QueryPrimaryContext is a QueryPrimaryDefaultContext
            if (queryPrimaryCtx instanceof TrinoParser.QueryPrimaryDefaultContext) {
                return ((TrinoParser.QueryPrimaryDefaultContext) queryPrimaryCtx).querySpecification();
            }
        }

        // Return null if not a simple SELECT statement
        return null;
    }


    /**
     * Visits a QuerySpecificationContext (SELECT statement) to process it.
     */
    @Override
    public Void visitQuerySpecification(TrinoParser.QuerySpecificationContext ctx) {
        // Process the SELECT statement
        processSelectStatement(ctx);
        return null;
    }


    /**
     * Processes a QuerySpecificationContext (SELECT statement) to extract selected columns and their types.
     */
    private SelectStatement processSelectStatement(TrinoParser.QuerySpecificationContext selectCtx) {
        // Extract the RelationContext from the FROM clause
        TrinoParser.RelationContext relationCtx = selectCtx.relation(0);
        String tableName = findTableName(relationCtx);

        // Check if the table exists in the symbol table
        if (!symbolTable.tableExists(tableName)) {
            System.out.println(String.format("Error: Table '%s' does not exist.", tableName));
            return null;
        }

        // Retrieve the table schema from the symbol table
        TableSchema table = symbolTable.getTable(tableName);
        SelectStatement selectStmt = new SelectStatement(tableName);

        // Iterate over each selected item in the SELECT clause
        for (TrinoParser.SelectItemContext itemCtx : selectCtx.selectItem()) {
            if (itemCtx instanceof TrinoParser.SelectSingleContext) {
                TrinoParser.SelectSingleContext selectSingleCtx = (TrinoParser.SelectSingleContext) itemCtx;
                TrinoParser.ExpressionContext exprCtx = selectSingleCtx.expression();
                String exprText = exprCtx.getText().toLowerCase();

                if (isAggregateFunctionCall(exprCtx)) {
                    // Handle aggregate function calls
                    String functionName = getFunctionName(exprCtx).toUpperCase();
                    // Check if it's one of the aggregate functions we chose
                    if (isAggregateFunction(functionName)) {
                        // Get the argument expression
                        TrinoParser.ExpressionContext argExprCtx = getFunctionArgument(exprCtx);
                        if (argExprCtx == null) {
                            System.out.println(String.format(
                                    "Error: Aggregate function '%s' requires an argument.",
                                    functionName
                            ));
                            continue;
                        }

                        String columnName = argExprCtx.getText().toLowerCase();

                        // Check if the column exists
                        Column column = table.getColumnByName(columnName);
                        if (column == null) {
                            System.out.println(String.format(
                                    "Error: Column '%s' does not exist in table '%s'.",
                                    columnName, tableName
                            ));
                            continue;
                        }

                        String columnType = column.getType();

                        // Check if the column type is compatible with the aggregate function
                        if (!isTypeCompatibleWithFunction(functionName, columnType)) {
                            System.out.println(String.format(
                                    "Error: Column '%s' of type '%s' is not compatible with aggregate function '%s'.",
                                    columnName, columnType, functionName
                            ));
                            continue;
                        }

                        // Add the aggregate function result to the select statement
                        selectStmt.addColumn(functionName + "(" + columnName + ")", columnType);
                    }
                } else {
                    // Handle direct column selection without aliases
                    Column column = table.getColumnByName(exprText);
                    if (column == null) {
                        System.out.println(String.format(
                                "Error: Column '%s' does not exist in table '%s'.",
                                exprText, tableName
                        ));
                        continue;
                    }
                    selectStmt.addColumn(column.getName(), column.getType());
                }
            } else if (itemCtx instanceof TrinoParser.SelectAllContext) {
                // Handle SELECT * FROM table
                table.getColumns().forEach(col -> selectStmt.addColumn(col.getName(), col.getType()));
                break;
            }
        }

        return selectStmt;
    }


    /**
     * Recursively find the table name.
     */
    private String findTableName(ParserRuleContext ctx) {
        if (ctx == null) {
            return null;
        }

        // Check if the current context is a TableNameContext
        if (ctx instanceof TrinoParser.TableNameContext) {
            TrinoParser.TableNameContext tableNameCtx = (TrinoParser.TableNameContext) ctx;
            return tableNameCtx.qualifiedName().getText().toLowerCase();
        }

        // Recursively check all child contexts
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            if (child instanceof ParserRuleContext) {
                String tableName = findTableName((ParserRuleContext) child);
                if (tableName != null) {
                    return tableName;
                }
            }
        }

        // Return null if TableNameContext is not found in this branch
        return null;
    }


    /**
     * Recursively searches the parse tree starting from a given context to find a FunctionCallContext.
     */
    private TrinoParser.FunctionCallContext findFunctionCall(ParserRuleContext ctx) {
        if (ctx == null) {
            return null;
        }
        if (ctx instanceof TrinoParser.FunctionCallContext) {
            return (TrinoParser.FunctionCallContext) ctx;
        }
        // Recursively search all child contexts
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            if (child instanceof ParserRuleContext) {
                TrinoParser.FunctionCallContext result = findFunctionCall((ParserRuleContext) child);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }


    /**
     * Checks if the expression contains a call to one of the aggregate functions.
     */
    private boolean isAggregateFunctionCall(TrinoParser.ExpressionContext exprCtx) {
        TrinoParser.FunctionCallContext funcCallCtx = findFunctionCall(exprCtx);
        if (funcCallCtx != null) {
            String functionName = funcCallCtx.qualifiedName().getText();
            return isAggregateFunction(functionName);
        }
        return false;
    }


    /**
     * Retrieves the function name from an expression that contains a function call.
     */
    private String getFunctionName(TrinoParser.ExpressionContext exprCtx) {
        TrinoParser.FunctionCallContext funcCallCtx = findFunctionCall(exprCtx);
        if (funcCallCtx != null) {
            return funcCallCtx.qualifiedName().getText();
        }
        return null;
    }

    /**
     * Retrieves the first argument of a function call expression.
     */
    private TrinoParser.ExpressionContext getFunctionArgument(TrinoParser.ExpressionContext exprCtx) {
        TrinoParser.FunctionCallContext funcCallCtx = findFunctionCall(exprCtx);
        if (funcCallCtx != null) {
            List<TrinoParser.ExpressionContext> args = funcCallCtx.expression();
            if (args != null && !args.isEmpty()) {
                return args.get(0); // Assuming the function has at least one argument
            }
        }
        return null;
    }


    /**
     * Checks if the function name is one of the aggregate functions we chose.
     */
    private boolean isAggregateFunction(String functionName) {
        return functionName.equalsIgnoreCase("SUM") ||
                functionName.equalsIgnoreCase("AVG") ||
                functionName.equalsIgnoreCase("MAX") ||
                functionName.equalsIgnoreCase("MIN") ||
                functionName.equalsIgnoreCase("COUNT");
    }


    /**
     * Checks if the column type is compatible with the aggregate function.
     */
    private boolean isTypeCompatibleWithFunction(String functionName, String columnType) {
        // Define sets of compatible types
        Set<String> numericTypes = new HashSet<>(Arrays.asList("INT", "INTEGER", "BIGINT", "SMALLINT", "FLOAT", "DOUBLE", "DECIMAL"));
        Set<String> stringTypes = new HashSet<>(Arrays.asList("CHAR", "VARCHAR", "TEXT", "STRING"));
        switch (functionName.toUpperCase()) {
            case "SUM":
            case "AVG":
                // SUM and AVG require numeric types
                return numericTypes.contains(columnType.toUpperCase());
            case "MAX":
            case "MIN":
                // MAX and MIN can work with numeric or string types
                return numericTypes.contains(columnType.toUpperCase()) ||
                        stringTypes.contains(columnType.toUpperCase());
            case "COUNT":
                // COUNT can work with any type
                return true;
            default:
                // Other functions not handled
                return false;
        }
    }
}