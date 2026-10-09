import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

// Core store model for inventory, pricing, sales records, and recommendation logic.
// The class keeps the in-memory data structures synchronized with saved files.
// File overview: Core store model for inventory, sales, and stock alerts.
// This class keeps the related logic together for easier reading and maintenance.
public class Store {

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
    static final String SALES_FILE = "sales.txt";
    static final String BASKETS_FILE = "baskets.txt";
    public static final int SUGGEST_HOPS = 2;
    static final String SEP = "|";
    static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final InventoryList itemList = new InventoryList();
    private final InventoryTable inventory = new InventoryTable();
    private final ExpiryTree expiryTree = new ExpiryTree();
    private final List<String> warnings = new ArrayList<>();
    private final ProductGraph graph = new ProductGraph();
    private final UndoStack history = new UndoStack();
    private int nextReceiptNo = 1;

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

    public static class Summary {
        public int sales, units;
        public double revenue;
    }

    public static class SalesLogEntry {
        public final int receiptNo;
        public final LocalDateTime time;
        public final int units;
        public final double total;

        SalesLogEntry(int receiptNo, LocalDateTime time, int units, double total) {
            this.receiptNo = receiptNo;
            this.time = time;
            this.units = units;
            this.total = total;
        }
    }

    public static class Suggestion {
        public final Item item;
        public final int hops;
        public int strength;

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

    // Loads persisted inventory and sales data, creating sample data only when needed.
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

    public List<String> getWarnings() {
        return warnings;
    }

    public Item get(String code) {
        return inventory.get(code);
    }

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

    public static String statusOf(Item i) {
        if (i.getQuantity() == 0) return "OUT OF STOCK";
        if (i.isExpired())        return "EXPIRED";
        if (i.isLowStock())       return "LOW STOCK";
        return "OK";
    }

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

    // Creates a new product while enforcing stock, pricing, and section validation rules.
    public Item addProduct(String name, int section, int quantity, int reorder,
                           LocalDate expiry, double price) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Item name can't be empty.");
        if (name.contains(SEP)) throw new IllegalArgumentException("Names can't contain the '" + SEP + "' character.");
        if (section < 0 || section >= SECTIONS.length) throw new IllegalArgumentException("Choose a section.");
        if (quantity < 0 || quantity > MAX_AMOUNT) throw new IllegalArgumentException("Quantity must be from 0 to " + MAX_AMOUNT + ".");
        if (reorder < 0 || reorder > MAX_AMOUNT) throw new IllegalArgumentException("Low-stock alert threshold must be from 0 to " + MAX_AMOUNT + ".");
        checkPrice(price);

        Item item = new Item(generateProductCode(section), name.trim(), section, quantity, reorder, expiry, price);
        registerItem(item);
        save();
        return item;
    }

    // Records an incoming delivery and keeps the expiry-tracking tree in sync.
    public void receiveDelivery(Item item, int amount, LocalDate newExpiry) {
        if (item.getQuantity() > 0 && item.isExpired()) {
            throw new IllegalArgumentException("The remaining stock expired on " + item.getExpiryDate()
                    + ". Write it off first, then receive the delivery.");
        }
        int room = MAX_AMOUNT - item.getQuantity();
        if (room <= 0) throw new IllegalArgumentException("Stock is already at the maximum (" + MAX_AMOUNT + ").");
        if (amount < 1 || amount > room) throw new IllegalArgumentException("Amount must be from 1 to " + room + ".");

        final boolean freshBatch = item.getQuantity() == 0;
        final LocalDate oldExpiry = item.getExpiryDate();

        if (item.getQuantity() == 0) {
            if (newExpiry == null) throw new IllegalArgumentException("Enter the expiry date of this delivery.");
            if (newExpiry.isBefore(LocalDate.now())) throw new IllegalArgumentException("That expiry date has already passed.");
            expiryTree.remove(item);
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
                expiryTree.remove(item);
                item.setExpiryDate(oldExpiry);
                expiryTree.insert(item);
            }
            item.setQuantity(item.getQuantity() - amount);
        }));
        save();
    }

    // Removes damaged or expired stock and records an undo action for recovery.
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

    public boolean canUndo() {
        return !history.isEmpty();
    }

    public String undoLast() {
        UndoStack.Action action = history.pop();
        if (action == null) throw new IllegalArgumentException("Nothing to undo.");
        action.undo();
        save();
        return action.description();
    }

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

                    s.units += Integer.parseInt(p[p.length - 2]);
                    s.revenue += Double.parseDouble(p[p.length - 1]);
                    s.sales++;
                } catch (NumberFormatException e) {

                }
            }
        } catch (IOException e) {
            warnings.add("Could not read " + SALES_FILE + " (" + e.getMessage() + ").");
        }
        return s;
    }

    public List<SalesLogEntry> salesBetween(LocalDate start, LocalDate end) throws IOException {
        if (start == null || end == null) throw new IllegalArgumentException("Choose both a start date and an end date.");
        if (end.isBefore(start)) throw new IllegalArgumentException("The end date must be on or after the start date.");

        List<SalesLogEntry> sales = new ArrayList<>();
        File file = new File(SALES_FILE);
        if (!file.exists()) return sales;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] p = line.split("\\|", -1);
                if (p.length < 4) continue;
                try {
                    int receiptNo = Integer.parseInt(p[0]);
                    LocalDateTime time = LocalDateTime.parse(p[1], TIME_FORMAT);
                    int units = Integer.parseInt(p[p.length - 2]);
                    double total = Double.parseDouble(p[p.length - 1]);
                    LocalDate date = time.toLocalDate();
                    if (!date.isBefore(start) && !date.isAfter(end)) {
                        sales.add(new SalesLogEntry(receiptNo, time, units, total));
                    }
                } catch (DateTimeParseException | NumberFormatException e) {

                }
            }
        }
        sales.sort(Comparator.comparing((SalesLogEntry sale) -> sale.time).reversed());
        return sales;
    }

    public List<Item> lowStockItems() {
        List<Item> low = new ArrayList<>();
        for (Item item : itemList) {
            if (item.isLowStock()) low.add(item);
        }
        low.sort(Comparator.comparingInt(Item::getQuantity)
                .thenComparing(Item::getName, String.CASE_INSENSITIVE_ORDER));
        return low;
    }

    public List<RestockHeap.Entry> actionQueue() {
        RestockHeap heap = new RestockHeap();
        LocalDate today = LocalDate.now();
        for (Item item : itemList) {
            RestockHeap.Entry entry = RestockHeap.entryFor(item, today);
            if (entry.priority > 0) heap.insert(entry);
        }
        List<RestockHeap.Entry> ranked = new ArrayList<>();
        while (!heap.isEmpty()) ranked.add(heap.extractMax());
        return ranked;
    }

    public List<Item> expiringSoon() {
        List<Item> out = new ArrayList<>();
        for (Item item : expiryTree.expiringOnOrBefore(LocalDate.now().plusDays(EXPIRY_ALERT_DAYS))) {
            if (item.getQuantity() > 0) out.add(item);
        }
        return out;
    }

    public List<Suggestion> suggestionsFor(Cart cart, int max) {
        List<String> inBasket = new ArrayList<>();
        for (Cart.Line line : cart) inBasket.add(line.getItem().getCode());
        if (inBasket.isEmpty()) return new ArrayList<>();
        return collect(inBasket, max);
    }

    public List<Suggestion> relatedTo(Item item, int max) {
        return collect(Collections.singletonList(item.getCode()), max);
    }

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

    public int addDemoPurchases() {
        List<Item> all = allItems();
        if (all.size() < 3) throw new IllegalArgumentException("Add at least 3 products first.");

        Random rnd = new Random(7);
        int blocks = all.size() / 3 + (all.size() % 3 >= 2 ? 1 : 0);
        int made = 0;
        for (int k = 0; k < 40; k++) {
            List<Integer> pool = new ArrayList<>();
            int take;
            if (k % 10 == 9) {
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

    private void loadBaskets() {
        File file = new File(BASKETS_FILE);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] p = line.split("\\|", -1);
                if (p.length != 2) continue;
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