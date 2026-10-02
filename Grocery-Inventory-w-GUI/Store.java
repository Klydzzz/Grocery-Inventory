import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

/**
 * The store's data and rules, with no console or window code in it, so the GUI
 * (or any other front end) can use it. It owns the three data structures:
 *   linked list -> ordered storage of all items
 *   hash table  -> fast lookup by product code
 *   BST         -> items sorted by expiry date
 * All three hold the SAME Item object, so a quantity change is seen by all of them.
 *
 * Methods that can be refused (bad input, not enough stock...) throw
 * IllegalArgumentException with a message that is safe to show to the cashier.
 */
public class Store {

    // Same sections / prefixes / file formats as Main.java.
    public static final String[] SECTIONS = {
        "Produce", "Dairy", "Bakery", "Meat & Seafood",
        "Canned Goods", "Grains & Pasta", "Beverages", "Checkout"
    };
    public static final String[] SECTION_PREFIXES = {
        "PRO", "DAI", "BAK", "MEA", "CAN", "GRA", "BEV", "CHK"
    };

    public static final int MAX_AMOUNT = 100000;
    public static final double MAX_PRICE = 1000000;
    public static final int EXPIRY_ALERT_DAYS = 7;

    static final String DATA_FILE = "inventory.txt";
    static final String SALES_FILE = "sales.txt";   // receipt no | date-time | units | total
    static final String SEP = "|";
    static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final InventoryList itemList = new InventoryList();
    private final InventoryTable inventory = new InventoryTable();
    private final ExpiryTree expiryTree = new ExpiryTree();
    private final List<String> warnings = new ArrayList<>();
    private int nextReceiptNo = 1;

    /** Result of a completed sale. */
    public static class Sale {
        public final int receiptNo;
        public final LocalDateTime time;
        public final double total, paid, change;

        Sale(int receiptNo, LocalDateTime time, double total, double paid, double change) {
            this.receiptNo = receiptNo;
            this.time = time;
            this.total = total;
            this.paid = paid;
            this.change = change;
        }
    }

    /** Today's takings. */
    public static class Summary {
        public int sales, units;
        public double revenue;
    }

    // -------------------------------------------------------------- startup

    /** Loads the save file (or sample data on the very first run). Returns true if sample data was used. */
    public boolean startup() {
        boolean seeded = false;
        if (!loadFromFile()) {
            seedSampleData();
            save();
            seeded = true;
        }
        loadReceiptCounter();
        return seeded;
    }

    /** Problems found while loading (damaged lines etc.). */
    public List<String> getWarnings() {
        return warnings;
    }

    // --------------------------------------------------------------- lookup

    public Item get(String code) {
        return inventory.get(code);
    }

    /** All items in the order they were added. */
    public List<Item> allItems() {
        List<Item> all = new ArrayList<>();
        for (Item item : itemList) all.add(item);
        return all;
    }

    public int itemCount() {
        return inventory.size();
    }

    public List<Item> findByName(String query) {
        String q = query.toLowerCase();
        List<Item> matches = new ArrayList<>();
        for (Item item : itemList) {
            if (item.getName().toLowerCase().contains(q)) matches.add(item);
        }
        return matches;
    }

    /** Text label for an item's state, shown in tables. */
    public static String statusOf(Item i) {
        if (i.getQuantity() == 0) return "OUT OF STOCK";
        if (i.isExpired())        return "EXPIRED";
        if (i.isLowStock())       return "LOW STOCK";
        return "OK";
    }

    // -------------------------------------------------------- inventory edits

    private void registerItem(Item item) {
        Item old = inventory.get(item.getCode());
        if (old != null) {
            itemList.remove(old);
            expiryTree.remove(old);
        }
        itemList.addLast(item);
        inventory.put(item);
        expiryTree.insert(item);
    }

    public Item addProduct(String name, int section, int quantity, int reorder,
                           LocalDate expiry, double price) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Item name can't be empty.");
        if (name.contains(SEP)) throw new IllegalArgumentException("Names can't contain the '" + SEP + "' character.");
        if (section < 0 || section >= SECTIONS.length) throw new IllegalArgumentException("Choose a section.");
        if (quantity < 0 || quantity > MAX_AMOUNT) throw new IllegalArgumentException("Quantity must be from 0 to " + MAX_AMOUNT + ".");
        if (reorder < 0 || reorder > MAX_AMOUNT) throw new IllegalArgumentException("Reorder level must be from 0 to " + MAX_AMOUNT + ".");
        checkPrice(price);

        Item item = new Item(generateProductCode(section), name.trim(), section, quantity, reorder, expiry, price);
        registerItem(item);
        save();
        return item;
    }

    /**
     * Receives a delivery: only the quantity goes up, so the item keeps its expiry date.
     * If the shelf is empty (quantity 0) the delivery is a fresh batch and newExpiry is required.
     */
    public void receiveDelivery(Item item, int amount, LocalDate newExpiry) {
        if (item.getQuantity() > 0 && item.isExpired()) {
            throw new IllegalArgumentException("The remaining stock expired on " + item.getExpiryDate()
                    + ". Write it off first, then receive the delivery.");
        }
        int room = MAX_AMOUNT - item.getQuantity();
        if (room <= 0) throw new IllegalArgumentException("Stock is already at the maximum (" + MAX_AMOUNT + ").");
        if (amount < 1 || amount > room) throw new IllegalArgumentException("Amount must be from 1 to " + room + ".");

        if (item.getQuantity() == 0) {
            if (newExpiry == null) throw new IllegalArgumentException("Enter the expiry date of this delivery.");
            if (newExpiry.isBefore(LocalDate.now())) throw new IllegalArgumentException("That expiry date has already passed.");
            expiryTree.remove(item);        // the BST finds items by expiry date: remove BEFORE changing it
            item.setExpiryDate(newExpiry);
            expiryTree.insert(item);
        }
        item.setQuantity(item.getQuantity() + amount);
        save();
    }

    /** Manual stock removal for damaged, expired or lost goods. */
    public void writeOff(Item item, int amount) {
        if (amount < 1 || amount > item.getQuantity())
            throw new IllegalArgumentException("Amount must be from 1 to " + item.getQuantity() + ".");
        item.setQuantity(item.getQuantity() - amount);
        save();
    }

    public void setPrice(Item item, double price) {
        checkPrice(price);
        item.setPrice(price);
        save();
    }

    public void deleteItem(Item item) {
        inventory.remove(item.getCode());
        itemList.remove(item);
        expiryTree.remove(item);
        save();
    }

    private static void checkPrice(double price) {
        if (!(price >= 0 && price <= MAX_PRICE))
            throw new IllegalArgumentException("Price must be from 0 to " + (long) MAX_PRICE + ".");
    }

    private String generateProductCode(int sectionIndex) {
        String prefix = SECTION_PREFIXES[sectionIndex];
        for (int number = 1; number <= 99999; number++) {
            String code = prefix + "-" + String.format("%04d", number);
            if (!inventory.contains(code)) return code;
        }
        throw new IllegalStateException("No available product code for section " + prefix + ".");
    }

    // ----------------------------------------------------------------- sales

    /**
     * Completes a sale: checks everything one more time, deducts the stock, saves the
     * inventory file and logs the sale. Throws (and changes nothing) if a line can't be sold.
     */
    public Sale completeSale(Cart cart, double paid) {
        if (cart.isEmpty()) throw new IllegalArgumentException("The basket is empty.");

        for (Cart.Line line : cart) {
            Item item = line.getItem();
            if (inventory.get(item.getCode()) != item)
                throw new IllegalArgumentException(item.getName() + " is no longer in the inventory. Remove it from the basket.");
            if (item.isExpired())
                throw new IllegalArgumentException(item.getName() + " has expired. Remove it from the basket.");
            if (line.getQuantity() > item.getQuantity())
                throw new IllegalArgumentException("Not enough stock of " + item.getName()
                        + " (" + item.getQuantity() + " left). Remove it or lower the quantity.");
        }

        double total = cart.total();
        if (!(paid >= total)) throw new IllegalArgumentException(String.format("Not enough cash. Amount due is %.2f.", total));
        paid = Cart.round2(paid);
        double change = Cart.round2(paid - total);

        for (Cart.Line line : cart) {
            Item item = line.getItem();
            item.setQuantity(item.getQuantity() - line.getQuantity());
        }
        int receiptNo = nextReceiptNo++;
        LocalDateTime now = LocalDateTime.now();
        save();
        logSale(receiptNo, now, cart.totalUnits(), total);
        return new Sale(receiptNo, now, total, paid, change);
    }

    private void logSale(int receiptNo, LocalDateTime time, int units, double total) {
        try (PrintWriter out = new PrintWriter(new FileWriter(SALES_FILE, true))) {
            out.println(receiptNo + SEP + time.format(TIME_FORMAT) + SEP + units + SEP + total);
        } catch (IOException e) {
            warnings.add("Could not write to " + SALES_FILE + " (" + e.getMessage() + ").");
        }
    }

    private void loadReceiptCounter() {
        File file = new File(SALES_FILE);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] p = line.split("\\|", -1);
                try {
                    nextReceiptNo = Math.max(nextReceiptNo, Integer.parseInt(p[0]) + 1);
                } catch (NumberFormatException e) {
                    // ignore damaged lines
                }
            }
        } catch (IOException e) {
            warnings.add("Could not read " + SALES_FILE + " (" + e.getMessage() + ").");
        }
    }

    public Summary todaySummary() {
        String today = LocalDate.now().toString();
        Summary s = new Summary();
        File file = new File(SALES_FILE);
        if (!file.exists()) return s;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] p = line.split("\\|", -1);
                if (p.length < 4 || !p[1].startsWith(today)) continue;
                try {
                    // last two fields are always units and total
                    s.units += Integer.parseInt(p[p.length - 2]);
                    s.revenue += Double.parseDouble(p[p.length - 1]);
                    s.sales++;
                } catch (NumberFormatException e) {
                    // skip damaged line
                }
            }
        } catch (IOException e) {
            warnings.add("Could not read " + SALES_FILE + " (" + e.getMessage() + ").");
        }
        return s;
    }

    // ---------------------------------------------------------------- alerts

    /** Items at or below their reorder level (including out of stock), emptiest first. */
    public List<Item> lowStockItems() {
        List<Item> low = new ArrayList<>();
        for (Item item : itemList) {
            if (item.isLowStock()) low.add(item);
        }
        low.sort(Comparator.comparingInt(Item::getQuantity)
                .thenComparing(Item::getName, String.CASE_INSENSITIVE_ORDER));
        return low;
    }

    /** In-stock items expiring within EXPIRY_ALERT_DAYS (or already expired), nearest first, from the BST. */
    public List<Item> expiringSoon() {
        List<Item> out = new ArrayList<>();
        for (Item item : expiryTree.expiringOnOrBefore(LocalDate.now().plusDays(EXPIRY_ALERT_DAYS))) {
            if (item.getQuantity() > 0) out.add(item);
        }
        return out;
    }

    // ---------------------------------------------------------- file storage

    public void save() {
        try (PrintWriter out = new PrintWriter(new FileWriter(DATA_FILE))) {
            for (Item i : itemList) {
                out.println(i.getCode() + SEP + i.getName() + SEP + i.getSectionIndex() + SEP
                        + i.getQuantity() + SEP + i.getReorderLevel() + SEP
                        + i.getExpiryDate() + SEP + i.getPrice());
            }
        } catch (IOException e) {
            warnings.add("Could not save to " + DATA_FILE + " (" + e.getMessage() + ").");
        }
    }

    /** Returns false if there is no save file yet. A damaged line is skipped and reported in getWarnings(). */
    private boolean loadFromFile() {
        File file = new File(DATA_FILE);
        if (!file.exists()) return false;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.trim().isEmpty()) continue;

                String[] p = line.split("\\|", -1);
                try {
                    if (p.length != 7) throw new IllegalArgumentException("expected 7 fields");

                    int section = Integer.parseInt(p[2]);
                    if (section < 0 || section >= SECTIONS.length)
                        throw new IllegalArgumentException("bad section number");

                    int quantity = Integer.parseInt(p[3]);
                    int reorder = Integer.parseInt(p[4]);
                    double price = Double.parseDouble(p[6]);
                    if (quantity < 0 || reorder < 0 || price < 0)
                        throw new IllegalArgumentException("negative number");

                    registerItem(new Item(p[0], p[1], section, quantity, reorder,
                            LocalDate.parse(p[5]), price));
                } catch (IllegalArgumentException | DateTimeParseException e) {
                    warnings.add("Skipped line " + lineNo + " in " + DATA_FILE + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            warnings.add("Could not read " + DATA_FILE + " (" + e.getMessage() + ").");
            return false;
        }
        return true;
    }

    // Used only on the very first run (no inventory.txt yet). Dates are relative to today.
    private void seedSampleData() {
        LocalDate today = LocalDate.now();
        registerItem(new Item("DAI-0001", "Milk 1L",             1, 20, 5,  today.plusDays(7),   89.50));
        registerItem(new Item("DAI-0002", "Eggs (tray)",         1, 4,  6,  today.plusDays(14), 210.00));
        registerItem(new Item("BAK-0001", "Bread Loaf",          2, 15, 5,  today.plusDays(3),   65.00));
        registerItem(new Item("GRA-0001", "Rice 5kg",            5, 40, 10, today.plusDays(300), 285.00));
        registerItem(new Item("CAN-0001", "Canned Tuna",         4, 30, 10, today.plusDays(400), 42.00));
        registerItem(new Item("BEV-0001", "Bottled Water 500ml", 6, 60, 20, today.plusDays(365), 15.00));
    }
}
