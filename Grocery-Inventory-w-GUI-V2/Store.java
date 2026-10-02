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
    static final String BASKETS_FILE = "baskets.txt"; // receipt no | product codes in that sale (feeds the graph)
    public static final int SUGGEST_HOPS = 2;         // how many links away a suggestion may be
    static final String SEP = "|";
    static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final InventoryList itemList = new InventoryList();
    private final InventoryTable inventory = new InventoryTable();
    private final ExpiryTree expiryTree = new ExpiryTree();
    private final List<String> warnings = new ArrayList<>();
    private final ProductGraph graph = new ProductGraph();   // which products are bought together
    private final UndoStack history = new UndoStack();       // stock / price changes that can be undone
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

    /** A product suggested because it is linked to something in the basket (or to an out-of-stock item). */
    public static class Suggestion {
        public final Item item;
        public final int hops;          // 1 = bought together directly, 2 = one step further away
        public int strength;            // how many times it was bought together

        Suggestion(Item item, int hops, int strength) {
            this.item = item;
            this.hops = hops;
            this.strength = strength;
        }

        @Override
        public String toString() {
            return item.getName() + "  [" + item.getCode() + "]  "
                    + (hops == 1 ? "bought together " + strength + "x" : "related");
        }
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
        loadBaskets();
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

        final boolean freshBatch = item.getQuantity() == 0;      // remembered so undo can restore the old expiry date
        final LocalDate oldExpiry = item.getExpiryDate();

        if (item.getQuantity() == 0) {
            if (newExpiry == null) throw new IllegalArgumentException("Enter the expiry date of this delivery.");
            if (newExpiry.isBefore(LocalDate.now())) throw new IllegalArgumentException("That expiry date has already passed.");
            expiryTree.remove(item);        // the BST finds items by expiry date: remove BEFORE changing it
            item.setExpiryDate(newExpiry);
            expiryTree.insert(item);
        }
        item.setQuantity(item.getQuantity() + amount);
        history.push(UndoStack.of("received " + amount + " x " + item.getName(), () -> {
            requireStillStocked(item);
            if (item.getQuantity() < amount)
                throw new IllegalArgumentException("Can't undo the delivery of " + item.getName()
                        + ": some of those units have already been sold or written off.");
            if (freshBatch) {
                expiryTree.remove(item);        // BST finds items by expiry date: remove BEFORE changing it
                item.setExpiryDate(oldExpiry);
                expiryTree.insert(item);
            }
            item.setQuantity(item.getQuantity() - amount);
        }));
        save();
    }

    /** Manual stock removal for damaged, expired or lost goods. */
    public void writeOff(Item item, int amount) {
        if (amount < 1 || amount > item.getQuantity())
            throw new IllegalArgumentException("Amount must be from 1 to " + item.getQuantity() + ".");
        item.setQuantity(item.getQuantity() - amount);
        history.push(UndoStack.of("wrote off " + amount + " x " + item.getName(), () -> {
            requireStillStocked(item);
            if (item.getQuantity() + amount > MAX_AMOUNT)
                throw new IllegalArgumentException("Can't undo: stock of " + item.getName() + " would go over " + MAX_AMOUNT + ".");
            item.setQuantity(item.getQuantity() + amount);
        }));
        save();
    }

    public void setPrice(Item item, double price) {
        checkPrice(price);
        final double oldPrice = item.getPrice();
        item.setPrice(price);
        history.push(UndoStack.of(String.format("price of %s (%.2f back to %.2f)", item.getName(), price, oldPrice), () -> {
            requireStillStocked(item);
            item.setPrice(oldPrice);
        }));
        save();
    }

    // ------------------------------------------------------------------ undo

    /** True when there is a stock or price change that can be undone. */
    public boolean canUndo() {
        return !history.isEmpty();
    }

    /**
     * Reverses the most recent stock or price change (delivery, write-off, price change).
     * Returns a short description of what was undone. Throws IllegalArgumentException if there
     * is nothing to undo or the change can no longer be reversed (that entry is then discarded).
     * Product deletions are not undoable, and neither are sales.
     */
    public String undoLast() {
        UndoStack.Action action = history.pop();
        if (action == null) throw new IllegalArgumentException("Nothing to undo.");
        action.undo();
        save();
        return action.description();
    }

    /** An undo must not touch a product that has been deleted since. */
    private void requireStillStocked(Item item) {
        if (inventory.get(item.getCode()) != item)
            throw new IllegalArgumentException(item.getName() + " was deleted, so that change can't be undone.");
    }

    public void deleteItem(Item item) {
        inventory.remove(item.getCode());
        itemList.remove(item);
        expiryTree.remove(item);
        graph.removeProduct(item.getCode());
        forgetInBaskets(item.getCode());
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
        recordBasket(receiptNo, cart);
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

    // ------------------------------------------------ bought-together graph

    /** Items worth adding to this basket: linked to something already in it, in stock and not expired. */
    public List<Suggestion> suggestionsFor(Cart cart, int max) {
        List<String> inBasket = new ArrayList<>();
        for (Cart.Line line : cart) inBasket.add(line.getItem().getCode());
        if (inBasket.isEmpty()) return new ArrayList<>();
        return collect(inBasket, max);
    }

    /** In-stock items that customers buy together with this product (handy when it is out of stock). */
    public List<Suggestion> relatedTo(Item item, int max) {
        return collect(Collections.singletonList(item.getCode()), max);
    }

    /** Groups of products that are connected by links of at least minStrength (found with DFS). */
    public List<List<Item>> productGroups(int minStrength) {
        List<List<Item>> out = new ArrayList<>();
        for (List<String> group : graph.groups(minStrength)) {
            List<Item> items = new ArrayList<>();
            for (String code : group) {
                Item item = inventory.get(code);
                if (item != null) items.add(item);
            }
            if (items.size() >= 2) out.add(items);
        }
        return out;
    }

    /**
     * DEMO ONLY: adds made-up purchases to the graph in memory so the feature can be shown
     * before real sales exist. Nothing is saved and no sale or stock is affected.
     * Products are split into blocks of three that are usually bought together,
     * with an occasional random basket linking different blocks.
     */
    public int addDemoPurchases() {
        List<Item> all = allItems();
        if (all.size() < 3) throw new IllegalArgumentException("Add at least 3 products first.");

        Random rnd = new Random(7);
        int blocks = all.size() / 3 + (all.size() % 3 >= 2 ? 1 : 0);   // only blocks with 2+ products
        int made = 0;
        for (int k = 0; k < 40; k++) {
            List<Integer> pool = new ArrayList<>();
            int take;
            if (k % 10 == 9) {                                   // occasional basket across the store
                for (int i = 0; i < all.size(); i++) pool.add(i);
                take = 2;
            } else {
                int from = rnd.nextInt(blocks) * 3;
                int to = Math.min(from + 3, all.size());
                for (int i = from; i < to; i++) pool.add(i);
                take = (pool.size() > 2) ? 2 + rnd.nextInt(2) : pool.size();
            }
            if (pool.size() < 2) continue;
            Collections.shuffle(pool, rnd);
            List<String> basket = new ArrayList<>();
            for (int i = 0; i < take; i++) basket.add(all.get(pool.get(i)).getCode());
            graph.recordBasket(basket);
            made++;
        }
        return made;
    }

    /** BFS from each source product; keeps sellable products, merges duplicates, nearest and strongest first. */
    private List<Suggestion> collect(List<String> sources, int max) {
        List<Suggestion> out = new ArrayList<>();
        for (String source : sources) {
            for (ProductGraph.Reach r : graph.bfs(source, SUGGEST_HOPS)) {
                if (sources.contains(r.code)) continue;
                Item item = inventory.get(r.code);
                if (item == null || item.getQuantity() == 0 || item.isExpired()) continue;

                Suggestion existing = null;
                for (Suggestion s : out) {
                    if (s.item == item) { existing = s; break; }
                }
                if (existing == null) {
                    out.add(new Suggestion(item, r.hops, r.strength));
                } else if (r.hops < existing.hops) {
                    out.remove(existing);
                    out.add(new Suggestion(item, r.hops, r.strength));
                } else if (r.hops == existing.hops) {
                    existing.strength += r.strength;
                }
            }
        }
        out.sort((a, b) -> {
            if (a.hops != b.hops) return a.hops - b.hops;
            if (a.strength != b.strength) return b.strength - a.strength;
            return a.item.getName().compareToIgnoreCase(b.item.getName());
        });
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /** Adds the sale to the graph and appends it to BASKETS_FILE. One-item sales link nothing, so they are skipped. */
    private void recordBasket(int receiptNo, Cart cart) {
        if (cart.size() < 2) return;
        List<String> codes = new ArrayList<>();
        for (Cart.Line line : cart) codes.add(line.getItem().getCode());
        graph.recordBasket(codes);
        try (PrintWriter out = new PrintWriter(new FileWriter(BASKETS_FILE, true))) {
            out.println(receiptNo + SEP + String.join(",", codes));
        } catch (IOException e) {
            warnings.add("Could not write to " + BASKETS_FILE + " (" + e.getMessage() + ").");
        }
    }

    /** Rebuilds the graph from BASKETS_FILE. Codes no longer in the inventory are ignored. */
    private void loadBaskets() {
        File file = new File(BASKETS_FILE);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] p = line.split("\\|", -1);
                if (p.length != 2) continue;                      // damaged line
                List<String> codes = new ArrayList<>();
                for (String code : p[1].split(",")) {
                    if (inventory.contains(code)) codes.add(code);
                }
                if (codes.size() >= 2) graph.recordBasket(codes);
            }
        } catch (IOException e) {
            warnings.add("Could not read " + BASKETS_FILE + " (" + e.getMessage() + ").");
        }
    }

    /**
     * Removes a deleted product's code from the saved baskets. Needed because product codes are
     * re-used: without this, a new product given the same code would inherit the old links.
     */
    private void forgetInBaskets(String code) {
        File file = new File(BASKETS_FILE);
        if (!file.exists()) return;
        List<String> kept = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] p = line.split("\\|", -1);
                if (p.length != 2) continue;
                List<String> codes = new ArrayList<>();
                for (String c : p[1].split(",")) {
                    if (!c.equals(code)) codes.add(c);
                }
                if (codes.size() >= 2) kept.add(p[0] + SEP + String.join(",", codes));
            }
        } catch (IOException e) {
            warnings.add("Could not read " + BASKETS_FILE + " (" + e.getMessage() + ").");
            return;
        }
        try (PrintWriter out = new PrintWriter(new FileWriter(BASKETS_FILE))) {
            for (String line : kept) out.println(line);
        } catch (IOException e) {
            warnings.add("Could not write to " + BASKETS_FILE + " (" + e.getMessage() + ").");
        }
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
