import java.util.ArrayList;
import java.util.List;

public class TableSchema {
    private String tableName;
    private List<Column> columns;

    public TableSchema(String tableName) {
        this.tableName = tableName.toLowerCase();
        this.columns = new ArrayList<>();
    }

    public void addColumn(String columnName, String dataType) {
        columns.add(new Column(columnName, dataType));
    }

    public String getTableName() {
        return tableName;
    }

    public List<Column> getColumns() {
        return columns;
    }

    /**
     * Retrieves a column by name
     */
    public Column getColumnByName(String columnName) {
        for (Column col : columns) {
            if (col.getName().equalsIgnoreCase(columnName)) {
                return col;
            }
        }
        return null;
    }
}