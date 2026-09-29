import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

/** Console menu for the Grocery Inventory System. */
public class Main {

    // Fixed store sections. Each item stores an index into this array.
    static final String[] SECTIONS = {
        "Entrance", "Produce", "Dairy", "Bakery", "Meat & Seafood",
        "Canned Goods", "Grains & Pasta", "Beverages", "Checkout"
    };

    static final String[] SECTION_PREFIXES = {
        "ENT", "PRO", "DAI", "BAK", "MEA", "CAN", "GRA", "BEV", "CHK"
    };

    static final int MAX_AMOUNT = 100000;   // sanity cap so quantities cannot overflow
    static final double MAX_PRICE = 1000000;
    static final String DATA_FILE = "inventory.txt";
    static final String SEP = "|";          // field separator in the save file

    static final InventoryList itemList = new InventoryList();
    static final InventoryTable inventory = new InventoryTable();
    static final ExpiryTree expiryTree = new ExpiryTree();
    static final Scanner in = new Scanner(System.in);

    public static void main(String[] args) {
        if (loadFromFile()) {
            System.out.println("Loaded " + inventory.size() + " item(s) from " + DATA_FILE + ".");
        } else {
            seedSampleData();
            saveToFile();
            System.out.println("No save file found. Started with sample data.");
        }

        System.out.println("=====================================");
        System.out.println("     GROCERY INVENTORY SYSTEM");
        System.out.println("=====================================");

        boolean running = true;
        while (running) {
            printMenu();
            int choice = readInt("Choose an option: ", 0, 9);
            System.out.println();

            switch (choice) {
                case 1: searchItem();     break;
                case 2: viewAllItems();   break;
                case 3: addStock();       break;
                case 4: addNewItem();     break;
                case 5: setPrice();       break;
                case 6: removeStock();    break;
                case 7: deleteItem();     break;
                case 8: browseSections(); break;
                case 9: expiryReport();   break;
                case 0: running = false;  break;
            }
            System.out.println();
        }

        saveToFile();
        System.out.println("Inventory saved. Goodbye!");
    }

    // ---------------------------------------------------------------- menu

    static void printMenu() {
        System.out.println("-------------------------------------");
        System.out.println(" MAIN MENU");
        System.out.println("-------------------------------------");
        System.out.println(" 1. Search for an item");
        System.out.println(" 2. View all items");
        System.out.println(" 3. Add stock to an item");
        System.out.println(" 4. Add a new item");
        System.out.println(" 5. Set an item's price");
        System.out.println(" 6. Remove stock from an item");
        System.out.println(" 7. Delete an item completely");
        System.out.println(" 8. Browse by category");
        System.out.println(" 9. Expiry report (nearest expiry first)");
        System.out.println(" 0. Save and exit");
        System.out.println("-------------------------------------");
    }

    // ------------------------------------------------- keeping all structures in sync

    // Every item lives in all three structures:
    //   linked list -> ordered storage of all items
    //   hash table -> fast lookup by product code
    //   BST        -> sorted by expiry date
    static void registerItem(Item item) {
        Item old = inventory.get(item.getCode());
        if (old != null) {                 // same code replaces the old item everywhere
            itemList.remove(old);
            expiryTree.remove(old);
        }
        itemList.addLast(item);
        inventory.put(item);
        expiryTree.insert(item);
    }

    // ------------------------------------------------------------- actions

    static void searchItem() {
        String query = readLine("Search by product code or name (Enter to cancel): ");
        if (query.isEmpty()) return;

        // Exact code -> O(1) hash table lookup
        Item exact = inventory.get(query.toUpperCase());
        if (exact != null) {
            printHeader();
            printItem(exact);
            return;
        }

        // Otherwise: name search scans the linked list.
        String q = query.toLowerCase();
        List<Item> matches = new ArrayList<>();
        for (Item item : itemList) {
            if (item.getName().toLowerCase().contains(q)) matches.add(item);
        }

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
        int room = MAX_AMOUNT - item.getQuantity();
        if (room <= 0) {
            System.out.println("Stock is already at the maximum (" + MAX_AMOUNT + ").");
            return;
        }
        int amount = readInt("How many to add? ", 1, room);
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
        int amount = readInt("How many to remove? ", 1, item.getQuantity());
        item.setQuantity(item.getQuantity() - amount);
        saveToFile();
        System.out.println("Removed " + amount + ". New stock of " + item.getName() + ": " + item.getQuantity());

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
            itemList.remove(item);   // >>> NEW
            expiryTree.remove(item);
            saveToFile();
            System.out.println(item.getName() + " was deleted.");
        } else {
            System.out.println("Cancelled. Nothing was deleted.");
        }
    }

    /** Lists the categories with item counts; lets the user "visit" one at a time. */
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
            System.out.println(" 0. Back to main menu");

            int choice = readInt("Choose which category? ", 0, SECTIONS.length);
            if (choice == 0) return;

            System.out.println();
            showSection(choice - 1);
            System.out.println();
        }
    }

    /** Prints every item whose sectionIndex matches, sorted by name. */
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

    // expiry report -------------------------------------------------

    /**
     * Lists items nearest-to-expiry first, straight from the BST's in-order traversal.
     * Already-expired items come first and are flagged. Items with 0 stock are skipped,
     * since there is nothing left on the shelf to expire.
     */
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
        System.out.printf("%-8s %-20s %-15s %6s  %-10s  %s%n",
                "CODE", "NAME", "SECTION", "QTY", "EXPIRES", "STATUS");

        for (Item item : inStock) {
            long left = ChronoUnit.DAYS.between(today, item.getExpiryDate());
            String status;
            if (left < 0)       status = "EXPIRED (" + (-left) + " day(s) ago)";
            else if (left == 0) status = "EXPIRES TODAY";
            else                status = "in " + left + " day(s)";

            System.out.printf("%-8s %-20.20s %-15.15s %6d  %-10s  %s%n",
                    item.getCode(), item.getName(), SECTIONS[item.getSectionIndex()],
                    item.getQuantity(), item.getExpiryDate(), status);
        }
        System.out.println(inStock.size() + " item(s).");
    }

    // ---------------------------------------------------------- file storage

    /** Writes every item to DATA_FILE, one line per item, fields joined by '|'. */
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

    /**
     * Reads DATA_FILE into all three structures (list, hash table, BST).
     * Returns false if there is no file yet (first run), true otherwise.
     * A damaged line is skipped with a warning instead of crashing the program.
     */
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

    // ------------------------------------------------------------- display

    static void printHeader() {
        System.out.printf("%-8s %-20s %-15s %6s %9s  %-10s%n",
                "CODE", "NAME", "SECTION", "QTY", "PRICE", "EXPIRES");
    }

    static void printItem(Item i) {
        System.out.printf("%-8s %-20.20s %-15.15s %6d %9.2f  %-10s%s%n",
                i.getCode(), i.getName(), SECTIONS[i.getSectionIndex()],
                i.getQuantity(), i.getPrice(), i.getExpiryDate(),
                i.isLowStock() ? "  <-- LOW STOCK" : "");
    }

    // --------------------------------------------------------------- input

    /** Asks for a product code or name and returns the matching item, or null if cancelled or not found. */
    static Item askForItem() {
        String input = readLine("Enter product code or name (Enter to cancel): ");
        if (input.isEmpty()) return null;

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
            System.out.printf("  %d. %s [%s]%n", i + 1, match.getName(), match.getCode());
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

    // ---------------------------------------------------------- sample data

    static void seedSampleData() {
        registerItem(new Item("DAI-0001", "Milk 1L",     2, 20, 5,  LocalDate.of(2026, 10, 5), 89.50));
        registerItem(new Item("GRA-0001", "Rice 5kg",    6, 40, 10, LocalDate.of(2027, 3, 1),  285.00));
        registerItem(new Item("DAI-0002", "Eggs (tray)", 2, 4,  6,  LocalDate.of(2026, 10, 1), 210.00));
        registerItem(new Item("BAK-0001", "Bread Loaf",  3, 15, 5,  LocalDate.of(2026, 9, 30), 65.00));
    }
}