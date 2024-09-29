public class Column {
    private String name;
    private String type;

    public Column(String name, String type) {
        this.name = name.toLowerCase();
        this.type = type.toUpperCase();
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }
}