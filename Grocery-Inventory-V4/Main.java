import java.io.*;
import java.time.*;
import java.time.format.*;
import java.time.temporal.*;
import java.util.*;

// Command-line entry point for the grocery point-of-sale system.
// It coordinates the interactive menu, customer checkout flow, and inventory actions.

// File overview: CLI entry point for the grocery POS and inventory menu.
// This class keeps the related logic together for easier reading and maintenance.
public class Main {

    static final String[] SECTIONS = {
        "Produce", "Dairy", "Bakery", "Meat & Seafood",
        "Canned Goods", "Grains & Pasta", "Beverages", "Checkout"
    };

    static final String[] SECTION_PREFIXES = {
        "PRO", "DAI", "BAK", "MEA", "CAN", "GRA", "BEV", "CHK"
    };

    static final int MAX_AMOUNT = 100000;
    static final double MAX_PRICE = 1000000;
    static final int EXPIRY_ALERT_DAYS = 7;
    static final String DATA_FILE = "inventory.txt";
    static final String SALES_FILE = "sales.txt";
    static final String SEP = "|";
    static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static final InventoryList itemList = new InventoryList();
    static final InventoryTable inventory = new InventoryTable();
    static final ExpiryTree expiryTree = new ExpiryTree();
    static final Scanner in = new Scanner(System.in);

    static int nextReceiptNo = 1;

    // Boots the application, loads persisted data, and starts the interactive menu loop.
    public static void main(String[] args) {
        if (loadFromFile()) {
            System.out.println("Loaded " + inventory.size() + " item(s) from " + DATA_FILE + ".");
        } else {
            seedSampleData();
            saveToFile();
            System.out.println("No save file found. Started with sample data.");
        }
        loadReceiptCounter();

        System.out.println("=====================================");
        System.out.println("  GROCERY POINT OF SALE & INVENTORY");
        System.out.println("=====================================");
        printAlertSummary();
        System.out.println();

        boolean running = true;
        while (running) {
            printMenu();
            int choice = readInt("Choose an option: ", 0, 5);
            System.out.println();

            switch (choice) {
                case 1: newSale();         break;
                case 2: searchItem();      break;
                case 3: salesSummary();    break;
                case 4: stockAlerts();     break;
                case 5: inventoryMenu();   break;
                case 0: running = false;   break;
            }
            System.out.println();
        }

        saveToFile();
        System.out.println("Inventory saved. Goodbye!");
    }

    // Displays the cashier menu so the user can choose a core store operation.
    static void printMenu() {
        System.out.println("-------------------------------------");
        System.out.println(" CASHIER MENU");
        System.out.println("-------------------------------------");
        System.out.println(" 1. New sale (checkout)");
        System.out.println(" 2. Price check / search for an item");
        System.out.println(" 3. Today's sales summary");
        System.out.println(" 4. Stock alerts (low stock & expiring)");
        System.out.println(" 5. Inventory management");
        System.out.println(" 0. Save and exit");
        System.out.println("-------------------------------------");
    }

    // Handles inventory maintenance tasks like stock movement, pricing, and expiry checks.
    static void inventoryMenu() {
        while (true) {
            System.out.println("-------------------------------------");
            System.out.println(" INVENTORY MANAGEMENT");
            System.out.println("-------------------------------------");
            System.out.println(" 1. View all items");
            System.out.println(" 2. Receive a delivery (add stock)");
            System.out.println(" 3. Add a new product");
            System.out.println(" 4. Set an item's price");
            System.out.println(" 5. Write off stock (damaged / expired / lost)");
            System.out.println(" 6. Delete a product completely");
            System.out.println(" 7. Browse by category");
            System.out.println(" 8. Expiry report (nearest expiry first)");
            System.out.println(" 0. Back to cashier menu");
            System.out.println("-------------------------------------");

            int choice = readInt("Choose an option: ", 0, 8);
            System.out.println();

            switch (choice) {
                case 1: viewAllItems();   break;
                case 2: addStock();       break;
                case 3: addNewItem();     break;
                case 4: setPrice();       break;
                case 5: removeStock();    break;
                case 6: deleteItem();     break;
                case 7: browseSections(); break;
                case 8: expiryReport();   break;
                case 0: return;
            }
            System.out.println();
        }
    }

    static void registerItem(Item item) {
        Item old = inventory.get(item.getCode());
        if (old != null) {
            itemList.remove(old);
            expiryTree.remove(old);
        }
        itemList.addLast(item);
        inventory.put(item);
        expiryTree.insert(item);
    }

    // Runs the checkout loop: users add items, preview the basket, and complete the sale.
    static void newSale() {
        Cart cart = new Cart();
        System.out.println("NEW SALE");
        System.out.println("Type a product code or name to add it to the basket.");
        System.out.println("Commands: PAY = take payment, VOID = remove a line, CANCEL = abandon sale,");
        System.out.println("          (just press Enter to show the basket)");

        while (true) {
            String input = readLine("Item> ");
            String cmd = input.toLowerCase();

            if (input.isEmpty()) {
                printCart(cart);
            } else if (cmd.equals("cancel")) {
                if (cart.isEmpty() || readYesNo("Cancel this sale and empty the basket? (y/n): ")) {
                    System.out.println("Sale cancelled. No stock was changed.");
                    return;
                }
            } else if (cmd.equals("pay")) {
                if (cart.isEmpty()) {
                    System.out.println("The basket is empty. Add at least one item first.");
                } else if (checkout(cart)) {
                    return;
                }
            } else if (cmd.equals("void")) {
                voidLine(cart);
            } else {
                addToCart(cart, input);
            }
        }
    }

    static void addToCart(Cart cart, String input) {
        Item item = resolveItem(input);
        if (item == null) return;

        if (item.isExpired()) {
            System.out.println(item.getName() + " EXPIRED on " + item.getExpiryDate()
                    + " and cannot be sold. Please take it off the shelf.");
            return;
        }

        int available = item.getQuantity() - cart.qtyOf(item.getCode());
        if (available <= 0) {
            if (item.getQuantity() == 0) {
                System.out.println(item.getName() + " is OUT OF STOCK.");
            } else {
                System.out.println("Only " + item.getQuantity() + " " + item.getName()
                        + " in stock, and all of them are already in the basket.");
            }
            return;
        }

        int qty = readIntOrDefault("Quantity (Enter = 1, max " + available + "): ", 1, 1, available);
        cart.add(item, qty);
        System.out.printf("Added %d x %s @ %.2f%n", qty, item.getName(), item.getPrice());
        if (item.getExpiryDate().equals(LocalDate.now())) {
            System.out.println("Note: " + item.getName() + " expires TODAY.");
        }
        printCart(cart);
    }

    static void voidLine(Cart cart) {
        if (cart.isEmpty()) {
            System.out.println("The basket is empty.");
            return;
        }
        printCart(cart);
        int n = readInt("Line number to remove (0 = back): ", 0, cart.size());
        if (n == 0) return;
        Cart.Line line = cart.lineAt(n - 1);
        cart.remove(line.getItem().getCode());
        System.out.println("Removed " + line.getItem().getName() + " from the basket.");
        printCart(cart);
    }

    static boolean checkout(Cart cart) {
        double total = cart.total();
        printCart(cart);

        double paid = 0;
        if (total > 0) {
            while (true) {
                String s = readLine("Cash received (Enter = back to basket): ");
                if (s.isEmpty()) return false;
                try {
                    paid = Double.parseDouble(s);
                } catch (NumberFormatException e) {
                    System.out.println("  That's not a valid amount (example: 500).");
                    continue;
                }
                if (!(paid >= total)) {
                    System.out.printf("  Not enough. Amount due is %.2f.%n", total);
                } else if (paid > MAX_PRICE) {
                    System.out.println("  That amount is too large.");
                } else {
                    break;
                }
            }
        }
        paid = Cart.round2(paid);
        double change = Cart.round2(paid - total);

        for (Cart.Line line : cart) {
            Item item = line.getItem();
            item.setQuantity(item.getQuantity() - line.getQuantity());
        }
        int receiptNo = nextReceiptNo++;
        LocalDateTime now = LocalDateTime.now();
        saveToFile();
        logSale(receiptNo, now, cart.totalUnits(), total);

        printReceipt(receiptNo, now, cart, total, paid, change);
        printPostSaleAlerts(cart);
        return true;
    }

    static void printCart(Cart cart) {
        if (cart.isEmpty()) {
            System.out.println("  Basket is empty.");
            return;
        }
        System.out.println("  BASKET");
        System.out.printf("  %-3s %-20s %5s %9s %10s%n", "#", "ITEM", "QTY", "PRICE", "AMOUNT");
        int n = 1;
        for (Cart.Line line : cart) {
            System.out.printf("  %-3d %-20.20s %5d %9.2f %10.2f%n", n++,
                    line.getItem().getName(), line.getQuantity(),
                    line.getUnitPrice(), line.getLineTotal());
        }
        System.out.printf("  TOTAL (%d unit(s)): %.2f%n", cart.totalUnits(), cart.total());
    }

    static void printReceipt(int receiptNo, LocalDateTime time, Cart cart,
                             double total, double paid, double change) {
        System.out.println();
        System.out.println("=========================================");
        System.out.println("  GROCERY STORE RECEIPT");
        System.out.printf("  Receipt #%04d      %s%n", receiptNo, time.format(TIME_FORMAT));
        System.out.println("-----------------------------------------");
        for (Cart.Line line : cart) {
            System.out.printf("  %3d x %-16.16s @ %7.2f %9.2f%n",
                    line.getQuantity(), line.getItem().getName(),
                    line.getUnitPrice(), line.getLineTotal());
        }
        System.out.println("-----------------------------------------");
        System.out.printf("  %-28s %10.2f%n", "TOTAL", total);
        System.out.printf("  %-28s %10.2f%n", "CASH", paid);
        System.out.printf("  %-28s %10.2f%n", "CHANGE", change);
        System.out.println("=========================================");
        System.out.println("  Thank you for shopping with us!");
    }

    static void printPostSaleAlerts(Cart cart) {
        boolean header = false;
        for (Cart.Line line : cart) {
            Item item = line.getItem();
            String msg = null;
            if (item.getQuantity() == 0) {
                msg = item.getName() + " is now OUT OF STOCK.";
            } else if (item.isLowStock()) {
                msg = item.getName() + " is LOW (" + item.getQuantity()
                        + " left, reorder level " + item.getReorderLevel() + ").";
            }
            if (msg != null) {
                if (!header) {
                    System.out.println();
                    System.out.println("STOCK ALERTS:");
                    header = true;
                }
                System.out.println("  ! " + msg);
            }
        }
    }

    static void logSale(int receiptNo, LocalDateTime time, int units, double total) {
        try (PrintWriter out = new PrintWriter(new FileWriter(SALES_FILE, true))) {
            out.println(receiptNo + SEP + time.format(TIME_FORMAT) + SEP + units + SEP + total);
        } catch (IOException e) {
            System.out.println("WARNING: could not write to " + SALES_FILE + " (" + e.getMessage() + ").");
        }
    }

    static void loadReceiptCounter() {
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
            System.out.println("WARNING: could not read " + SALES_FILE + " (" + e.getMessage() + ").");
        }
    }

    static void salesSummary() {
        String today = LocalDate.now().toString();
        int sales = 0, units = 0;
        double revenue = 0;

        File file = new File(SALES_FILE);
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] p = line.split("\\|", -1);
                    if (p.length < 4 || !p[1].startsWith(today)) continue;
                    try {

                        units += Integer.parseInt(p[p.length - 2]);
                        revenue += Double.parseDouble(p[p.length - 1]);
                        sales++;
                    } catch (NumberFormatException e) {

                    }
                }
            } catch (IOException e) {
                System.out.println("WARNING: could not read " + SALES_FILE + " (" + e.getMessage() + ").");
                return;
            }
        }

        System.out.println("SALES SUMMARY FOR " + today);
        System.out.println("  Completed sales : " + sales);
        System.out.println("  Units sold      : " + units);
        System.out.printf("  Total sales     : %.2f%n", revenue);
    }

    static List<Item> lowStockItems() {
        List<Item> low = new ArrayList<>();
        for (Item item : itemList) {
            if (item.isLowStock()) low.add(item);
        }
        low.sort(Comparator.comparingInt(Item::getQuantity)
                .thenComparing(Item::getName, String.CASE_INSENSITIVE_ORDER));
        return low;
    }

    static List<Item> expiringSoon(LocalDate today) {
        List<Item> out = new ArrayList<>();
        for (Item item : expiryTree.expiringOnOrBefore(today.plusDays(EXPIRY_ALERT_DAYS))) {
            if (item.getQuantity() > 0) out.add(item);
        }
        return out;
    }

    static void printAlertSummary() {
        int low = lowStockItems().size();
        int expiring = expiringSoon(LocalDate.now()).size();
        if (low > 0 || expiring > 0) {
            System.out.println("Heads up: " + low + " item(s) low or out of stock, "
                    + expiring + " item(s) expiring soon or expired. See option 4.");
        }
    }

    static void stockAlerts() {
        LocalDate today = LocalDate.now();

        System.out.println("=== LOW / OUT OF STOCK ===");
        List<Item> low = lowStockItems();
        if (low.isEmpty()) {
            System.out.println("None. Everything is above its reorder level.");
        } else {
            printHeader();
            for (Item item : low) printItem(item);
            System.out.println(low.size() + " item(s) to reorder.");
        }

        System.out.println();
        System.out.println("=== EXPIRING WITHIN " + EXPIRY_ALERT_DAYS + " DAYS (or already expired) ===");
        List<Item> expiring = expiringSoon(today);
        if (expiring.isEmpty()) {
            System.out.println("None.");
        } else {
            printExpiryRows(expiring, today);
        }
    }

    static void searchItem() {
        String query = readLine("Search by product code or name (Enter to cancel): ");
        if (query.isEmpty()) return;

        Item exact = inventory.get(query.toUpperCase());
        if (exact != null) {
            printHeader();
            printItem(exact);
            return;
        }

        List<Item> matches = findItemsByName(query);
        if (matches.isEmpty()) {
            System.out.println("No items found for \"" + query + "\".");
            return;
        }
        System.out.println(matches.size() + " match(es):");
        printHeader();
        for (Item item : matches) printItem(item);
    }

    static void viewAllItems() {
        if (inventory.size() == 0) {
            System.out.println("The inventory is empty.");
            return;
        }
        System.out.println(inventory.size() + " item(s) in inventory:");
        printHeader();
        for (Item item : itemList) printItem(item);
    }

    static void addStock() {
        Item item = askForItem();
        if (item == null) return;

        System.out.println("Current stock of " + item.getName() + ": " + item.getQuantity());
        if (item.getQuantity() > 0 && item.isExpired()) {
            System.out.println("The remaining stock expired on " + item.getExpiryDate() + ".");
            System.out.println("Write it off first (Inventory management > Write off stock), then receive the delivery.");
            return;
        }
        int room = MAX_AMOUNT - item.getQuantity();
        if (room <= 0) {
            System.out.println("Stock is already at the maximum (" + MAX_AMOUNT + ").");
            return;
        }
        int amount = readInt("How many to add? ", 1, room);

        LocalDate newExpiry = null;
        if (item.getQuantity() == 0) {
            while (true) {
                newExpiry = readDate("Expiry date of this delivery (YYYY-MM-DD): ");
                if (!newExpiry.isBefore(LocalDate.now())) break;
                System.out.println("  That date has already passed.");
            }
        }

        if (newExpiry != null) {
            expiryTree.remove(item);
            item.setExpiryDate(newExpiry);
            expiryTree.insert(item);
        }
        item.setQuantity(item.getQuantity() + amount);
        saveToFile();
        System.out.println("Added " + amount + ". New stock of " + item.getName() + ": " + item.getQuantity());
    }

    static void addNewItem() {
        String name;
        while (true) {
            name = readNonEmpty("Item name: ");
            if (!name.contains(SEP)) break;
            System.out.println("  Names can't contain the '" + SEP + "' character.");
        }

        System.out.println("Store sections:");
        for (int i = 0; i < SECTIONS.length; i++) {
            System.out.println("  " + (i + 1) + ". " + SECTIONS[i]);
        }
        int section = readInt("Section number: ", 1, SECTIONS.length) - 1;

        String code = generateProductCode(section);
        int quantity = readInt("Starting quantity: ", 0, MAX_AMOUNT);
        int reorder = readInt("Reorder level (alert when stock falls to this number): ", 0, MAX_AMOUNT);
        LocalDate expiry = readDate("Expiry date (YYYY-MM-DD): ");
        double price = readPrice("Price: ");

        registerItem(new Item(code, name, section, quantity, reorder, expiry, price));
        saveToFile();
        System.out.println("Generated product code: " + code);
        System.out.println("Added new item:");
        printHeader();
        printItem(inventory.get(code));
    }

    static void setPrice() {
        Item item = askForItem();
        if (item == null) return;

        System.out.printf("Current price of %s: %.2f%n", item.getName(), item.getPrice());
        double price = readPrice("New price: ");
        item.setPrice(price);
        saveToFile();
        System.out.printf("Price of %s is now %.2f%n", item.getName(), item.getPrice());
    }

    static void removeStock() {
        Item item = askForItem();
        if (item == null) return;

        if (item.getQuantity() == 0) {
            System.out.println(item.getName() + " has no stock to remove.");
            return;
        }
        System.out.println("Current stock of " + item.getName() + ": " + item.getQuantity());
        int amount = readInt("How many to write off? ", 1, item.getQuantity());
        item.setQuantity(item.getQuantity() - amount);
        saveToFile();
        System.out.println("Wrote off " + amount + ". New stock of " + item.getName() + ": " + item.getQuantity());

        if (item.getQuantity() == 0) {
            System.out.println("Note: " + item.getName() + " is now OUT OF STOCK.");
        } else if (item.isLowStock()) {
            System.out.println("Note: " + item.getName() + " is LOW ON STOCK (reorder level " + item.getReorderLevel() + ").");
        }
    }

    static void deleteItem() {
        Item item = askForItem();
        if (item == null) return;

        printHeader();
        printItem(item);
        if (readYesNo("Delete this item from the inventory permanently? (y/n): ")) {
            inventory.remove(item.getCode());
            itemList.remove(item);
            expiryTree.remove(item);
            saveToFile();
            System.out.println(item.getName() + " was deleted.");
        } else {
            System.out.println("Cancelled. Nothing was deleted.");
        }
    }

    static void browseSections() {
        while (true) {
            int[] counts = new int[SECTIONS.length];
            for (Item item : itemList) {
                counts[item.getSectionIndex()]++;
            }

            System.out.println("CATEGORIES");
            for (int i = 0; i < SECTIONS.length; i++) {
                System.out.printf(" %d. %-16s %d item(s)%n", i + 1, SECTIONS[i], counts[i]);
            }
            System.out.println(" 0. Back");

            int choice = readInt("Choose which category? ", 0, SECTIONS.length);
            if (choice == 0) return;

            System.out.println();
            showSection(choice - 1);
            System.out.println();
        }
    }

    static void showSection(int sectionIndex) {
        List<Item> found = new ArrayList<>();
        for (Item item : itemList) {
            if (item.getSectionIndex() == sectionIndex) found.add(item);
        }

        System.out.println("=== " + SECTIONS[sectionIndex].toUpperCase() + " ===");
        if (found.isEmpty()) {
            System.out.println("No items in this section.");
            return;
        }

        found.sort(Comparator.comparing(Item::getName, String.CASE_INSENSITIVE_ORDER));

        int lowCount = 0;
        printHeader();
        for (Item item : found) {
            printItem(item);
            if (item.isLowStock()) lowCount++;
        }
        System.out.println(found.size() + " item(s), " + lowCount + " low on stock.");
    }

    static void expiryReport() {
        int days = readInt("Show items expiring within how many days? (0 = show everything): ", 0, 3650);
        LocalDate today = LocalDate.now();

        List<Item> sorted = (days == 0)
                ? expiryTree.inOrder()
                : expiryTree.expiringOnOrBefore(today.plusDays(days));

        List<Item> inStock = new ArrayList<>();
        for (Item item : sorted) {
            if (item.getQuantity() > 0) inStock.add(item);
        }

        if (inStock.isEmpty()) {
            System.out.println("No items in stock expire within that time.");
            return;
        }

        System.out.println(days == 0
                ? "All items, nearest expiry first:"
                : "Expiring within " + days + " day(s), nearest expiry first:");
        printExpiryRows(inStock, today);
    }

    static void printExpiryRows(List<Item> items, LocalDate today) {
        System.out.printf("%-8s %-20s %-15s %6s  %-10s  %s%n",
                "CODE", "NAME", "SECTION", "QTY", "EXPIRES", "STATUS");

        for (Item item : items) {
            long left = ChronoUnit.DAYS.between(today, item.getExpiryDate());
            String status;
            if (left < 0)       status = "EXPIRED (" + (-left) + " day(s) ago)";
            else if (left == 0) status = "EXPIRES TODAY";
            else                status = "in " + left + " day(s)";

            System.out.printf("%-8s %-20.20s %-15.15s %6d  %-10s  %s%n",
                    item.getCode(), item.getName(), SECTIONS[item.getSectionIndex()],
                    item.getQuantity(), item.getExpiryDate(), status);
        }
        System.out.println(items.size() + " item(s).");
    }

    static void saveToFile() {
        try (PrintWriter out = new PrintWriter(new FileWriter(DATA_FILE))) {
            for (Item i : itemList) {
                out.println(i.getCode() + SEP + i.getName() + SEP + i.getSectionIndex() + SEP
                        + i.getQuantity() + SEP + i.getReorderLevel() + SEP
                        + i.getExpiryDate() + SEP + i.getPrice());
            }
        } catch (IOException e) {
            System.out.println("WARNING: could not save to " + DATA_FILE + " (" + e.getMessage() + ").");
        }
    }

    static boolean loadFromFile() {
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
                    System.out.println("Skipped line " + lineNo + " in " + DATA_FILE + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("WARNING: could not read " + DATA_FILE + " (" + e.getMessage() + ").");
            return false;
        }
        return true;
    }

    static void printHeader() {
        System.out.printf("%-8s %-20s %-15s %6s %9s  %-10s%n",
                "CODE", "NAME", "SECTION", "QTY", "PRICE", "EXPIRES");
    }

    static void printItem(Item i) {
        String flag = "";
        if (i.getQuantity() == 0)  flag = "  <-- OUT OF STOCK";
        else if (i.isExpired())    flag = "  <-- EXPIRED";
        else if (i.isLowStock())   flag = "  <-- LOW STOCK";

        System.out.printf("%-8s %-20.20s %-15.15s %6d %9.2f  %-10s%s%n",
                i.getCode(), i.getName(), SECTIONS[i.getSectionIndex()],
                i.getQuantity(), i.getPrice(), i.getExpiryDate(), flag);
    }

    static Item askForItem() {
        String input = readLine("Enter product code or name (Enter to cancel): ");
        if (input.isEmpty()) return null;
        return resolveItem(input);
    }

    static Item resolveItem(String input) {
        Item item = inventory.get(input.toUpperCase());
        if (item != null) return item;

        List<Item> matches = findItemsByName(input);
        if (matches.isEmpty()) {
            System.out.println("No item matched \"" + input + "\".");
            return null;
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }

        System.out.println("Multiple matches found:");
        for (int i = 0; i < matches.size(); i++) {
            Item match = matches.get(i);
            System.out.printf("  %d. %s [%s]  price %.2f, stock %d%n", i + 1,
                    match.getName(), match.getCode(), match.getPrice(), match.getQuantity());
        }

        int choice = readInt("Choose an item number: ", 1, matches.size()) - 1;
        return matches.get(choice);
    }

    static List<Item> findItemsByName(String query) {
        String q = query.toLowerCase();
        List<Item> matches = new ArrayList<>();
        for (Item item : itemList) {
            if (item.getName().toLowerCase().contains(q)) {
                matches.add(item);
            }
        }
        return matches;
    }

    static String generateProductCode(int sectionIndex) {
        String prefix = SECTION_PREFIXES[sectionIndex];
        for (int number = 1; number <= 99999; number++) {
            String code = prefix + "-" + String.format("%04d", number);
            if (!inventory.contains(code)) {
                return code;
            }
        }
        throw new IllegalStateException("No available product code for section " + prefix + ".");
    }

    static String readLine(String prompt) {
        System.out.print(prompt);
        return in.nextLine().trim();
    }

    static String readNonEmpty(String prompt) {
        while (true) {
            String s = readLine(prompt);
            if (!s.isEmpty()) return s;
            System.out.println("  This can't be empty.");
        }
    }

    static int readInt(String prompt, int min, int max) {
        while (true) {
            String s = readLine(prompt);
            try {
                int value = Integer.parseInt(s);
                if (value >= min && value <= max) return value;
                System.out.println("  Please enter a number from " + min + " to " + max + ".");
            } catch (NumberFormatException e) {
                System.out.println("  That's not a valid whole number.");
            }
        }
    }

    static int readIntOrDefault(String prompt, int def, int min, int max) {
        while (true) {
            String s = readLine(prompt);
            if (s.isEmpty()) return def;
            try {
                int value = Integer.parseInt(s);
                if (value >= min && value <= max) return value;
                System.out.println("  Please enter a number from " + min + " to " + max + ".");
            } catch (NumberFormatException e) {
                System.out.println("  That's not a valid whole number.");
            }
        }
    }

    static double readPrice(String prompt) {
        while (true) {
            String s = readLine(prompt);
            try {
                double value = Double.parseDouble(s);
                if (value >= 0 && value <= MAX_PRICE) return value;
                System.out.println("  Price must be 0 or more.");
            } catch (NumberFormatException e) {
                System.out.println("  That's not a valid price (example: 45.50).");
            }
        }
    }

    static LocalDate readDate(String prompt) {
        while (true) {
            String s = readLine(prompt);
            try {
                return LocalDate.parse(s);
            } catch (DateTimeParseException e) {
                System.out.println("  Use the format YYYY-MM-DD (example: 2026-12-31).");
            }
        }
    }

    static boolean readYesNo(String prompt) {
        while (true) {
            String s = readLine(prompt).toLowerCase();
            if (s.equals("y") || s.equals("yes")) return true;
            if (s.equals("n") || s.equals("no")) return false;
            System.out.println("  Please type y or n.");
        }
    }

    static void seedSampleData() {
        LocalDate today = LocalDate.now();
        registerItem(new Item("DAI-0001", "Milk 1L",      1, 20, 5,  today.plusDays(7),   89.50));
        registerItem(new Item("DAI-0002", "Eggs (tray)",  1, 4,  6,  today.plusDays(14), 210.00));
        registerItem(new Item("BAK-0001", "Bread Loaf",   2, 15, 5,  today.plusDays(3),   65.00));
        registerItem(new Item("GRA-0001", "Rice 5kg",     5, 40, 10, today.plusDays(300), 285.00));
        registerItem(new Item("CAN-0001", "Canned Tuna",  4, 30, 10, today.plusDays(400), 42.00));
        registerItem(new Item("BEV-0001", "Bottled Water 500ml", 6, 60, 20, today.plusDays(365), 15.00));
    }
}