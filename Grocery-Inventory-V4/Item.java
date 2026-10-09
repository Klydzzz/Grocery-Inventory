import java.time.*;


// File overview: Inventory item value object for prices, stock, and expiry.
// This class keeps the related logic together for easier reading and maintenance.
public class Item {
    private final String code;
    private String name;
    private int sectionIndex;
    private int quantity;
    private int reorderLevel;
    private LocalDate expiryDate;
    private double price;

    public Item(String code, String name, int sectionIndex, int quantity,
                int reorderLevel, LocalDate expiryDate, double price) {
        this.code = code;
        this.name = name;
        this.sectionIndex = sectionIndex;
        this.quantity = quantity;
        this.reorderLevel = reorderLevel;
        this.expiryDate = expiryDate;
        this.price = price;
    }

    public String getCode()            { return code; }
    public String getName()            { return name; }
    public int getSectionIndex()       { return sectionIndex; }
    public int getQuantity()           { return quantity; }
    public int getReorderLevel()       { return reorderLevel; }
    public LocalDate getExpiryDate()   { return expiryDate; }
    public double getPrice()           { return price; }

    public void setName(String name)                { this.name = name; }
    public void setSectionIndex(int sectionIndex)   { this.sectionIndex = sectionIndex; }
    public void setQuantity(int quantity)           { this.quantity = quantity; }
    public void setReorderLevel(int reorderLevel)   { this.reorderLevel = reorderLevel; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public void setPrice(double price)              { this.price = price; }

    public boolean isLowStock() {
        return quantity <= reorderLevel;
    }

    public boolean isExpired() {
        return expiryDate.isBefore(LocalDate.now());
    }

    @Override
    public String toString() {
        return String.format("[%s] %s | section %d | qty %d (reorder at %d) | exp %s | price %.2f",
                code, name, sectionIndex, quantity, reorderLevel, expiryDate, price);
    }
}