import java.util.HashMap;
import java.util.Map;

public class SymbolTable {
    private Map<String, TableSchema> tables;

    public SymbolTable() {
        tables = new HashMap<>();
    }

    /**
     * Adds a table schema to the symbol table.
     */
    public void addTable(TableSchema table) {
        tables.put(table.getTableName(), table);
    }

    /**
     * Retrieves a table schema by table name. Returns null if not found.
     */
    public TableSchema getTable(String tableName) {
        return tables.get(tableName.toLowerCase());
    }

    /**
     * Checks if a table exists in the symbol table.
     */
    public boolean tableExists(String tableName) {
        return tables.containsKey(tableName.toLowerCase());
    }
}