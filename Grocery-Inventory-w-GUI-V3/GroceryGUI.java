import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.time.*;
import java.time.format.*;
import java.time.temporal.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.util.List;   // needed: java.awt.* also has a List

/**
 * Simple Swing front end for the grocery store. Run with:  java GroceryGUI
 * Tabs: Sale (checkout), Inventory (manage products), Reports (sales and inventory reports).
 * All the real work is done by Store, so the rules are the same as in the console version.
 */
public class GroceryGUI extends JFrame {

    private final Store store = new Store();
    private Cart cart = new Cart();
    private final UndoStack basketUndo = new UndoStack();     // basket actions of the current sale

    // ---- Sale tab
    private final JTextField itemField = new JTextField(18);
    private final JSpinner qtySpinner = new JSpinner(new SpinnerNumberModel(1, 1, Store.MAX_AMOUNT, 1));
    private final DefaultTableModel cartModel = newModel("#", "Item", "Qty", "Price", "Amount");
    private final JTable cartTable = new JTable(cartModel);
    private final JLabel totalLabel = new JLabel("TOTAL: 0.00");
    private final JTextField cashField = new JTextField(10);
    private final JLabel changeLabel = new JLabel("Change: -");
    private final JLabel statusLabel = new JLabel(" ");
    private final DefaultListModel<Store.Suggestion> suggestModel = new DefaultListModel<>();
    private final JList<Store.Suggestion> suggestList = new JList<>(suggestModel);

    // ---- Inventory tab
    private final JTextField searchField = new JTextField(20);
    private final JLabel inventoryCountLabel = new JLabel(" ");
    private final DefaultTableModel invModel =
            newModel("Code", "Name", "Section", "Qty", "Low-stock alert at", "Expires", "Price", "Status");
    private final JTable invTable = new JTable(invModel);

    private final JComboBox<String> sortKeyBox =
            new JComboBox<>(new String[]{"Added order", "Name", "Price", "Quantity", "Expiry date"});
    private final JComboBox<String> sortAlgoBox =
            new JComboBox<>(new String[]{"Insertion sort", "Selection sort", "Bubble sort"});
    private final JLabel sortInfoLabel = new JLabel(" ");

    // ---- Priority tab
    private final DefaultTableModel priorityModel =
            newModel("Rank", "Code", "Name", "Action", "Qty", "Low-stock alert at", "Expires", "Score");

    // ---- Benchmark tab
    private final DefaultTableModel benchModel = newModel("Dataset Size", "Operation",
            "Algorithm / Structure", "Time (ns)", "Comparisons / Movements", "Observation");
    private final JButton benchRunBtn = new JButton("Run benchmark");
    private final JButton benchExportBtn = new JButton("Export CSV");
    private final JLabel benchStatus = new JLabel("Press \"Run benchmark\" (takes a few seconds).");
    private List<BenchmarkRunner.Row> benchRows = new ArrayList<>();

    // ---- Reports tab
    private final JLabel salesLabel = new JLabel(" ");
    private final DefaultTableModel lowModel =
            newModel("Code", "Name", "Section", "Qty", "Low-stock alert at", "Status");
    private final DefaultTableModel expModel = newModel("Code", "Name", "Qty", "Expires", "Status");
    private final DefaultTableModel salesLogModel = newModel("Receipt #", "Date / Time", "Units Sold", "Total");
    private final DefaultTableModel groupModel = newModel("Group", "Items", "Products");
    private final JSpinner strengthSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 99, 1));
    private final JTextField salesStartField =
            new JTextField(LocalDate.now().withDayOfMonth(1).toString(), 10);
    private final JTextField salesEndField = new JTextField(LocalDate.now().toString(), 10);
    private final JLabel salesLogStatus = new JLabel(" ");

    private JTabbedPane tabs;

    public GroceryGUI() {
        super("Grocery Point of Sale & Inventory");

        boolean seeded = store.startup();
        if (!store.getWarnings().isEmpty()) {
            JOptionPane.showMessageDialog(null, String.join("\n", store.getWarnings()),
                    "Problems while loading", JOptionPane.WARNING_MESSAGE);
            store.getWarnings().clear();
        }

        tabs = new JTabbedPane();
        tabs.addTab("Reports", buildReportsTab());
        tabs.addTab("Sale", buildSaleTab());
        tabs.addTab("Inventory", buildInventoryTab());
        tabs.addTab("Priority", buildPriorityTab());
        tabs.addTab("Benchmark", buildBenchmarkTab());
        tabs.setSelectedIndex(0);
        tabs.addChangeListener(e -> refreshAll());

        add(tabs, BorderLayout.CENTER);
        statusLabel.setBorder(new EmptyBorder(4, 10, 4, 10));
        add(statusLabel, BorderLayout.SOUTH);
        applyControlSizing(getContentPane());

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                store.save();
            }
        });
        setSize(1080, 740);
        setMinimumSize(new Dimension(820, 560));
        setLocationRelativeTo(null);

        refreshAll();
        if (seeded) setStatus("No save file found. Started with sample data.", false);
        else setStatus("Loaded " + store.itemCount() + " item(s).", false);
    }

    // ================================================================ SALE TAB

    private JPanel buildSaleTab() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Product code or name:"));
        top.add(itemField);
        top.add(new JLabel("Qty:"));
        top.add(qtySpinner);
        JButton addBtn = new JButton("Add to basket");
        top.add(addBtn);
        addBtn.addActionListener(e -> addToCart());
        itemField.addActionListener(e -> addToCart());      // Enter adds the item
        itemField.setToolTipText("Enter a product code or part of its name, then press Enter.");
        qtySpinner.setToolTipText("Choose how many units to add.");
        cashField.setToolTipText("Enter the amount received. Press Enter to complete payment.");
        p.add(top, BorderLayout.NORTH);

        configureTable(cartTable);
        p.add(new JScrollPane(cartTable), BorderLayout.CENTER);

        // "Customers also buy" - suggestions from the bought-together graph (BFS)
        suggestList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) useSuggestion();
            }
        });
        JScrollPane suggestPane = new JScrollPane(suggestList);
        suggestPane.setBorder(new TitledBorder("Customers also buy (double-click to fill in)"));
        suggestPane.setPreferredSize(new Dimension(300, 100));
        suggestList.setToolTipText("Double-click a suggestion to enter its product code.");
        p.add(suggestPane, BorderLayout.EAST);

        totalLabel.setFont(totalLabel.getFont().deriveFont(Font.BOLD, 24f));
        JPanel totalRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        totalRow.add(totalLabel);

        JButton removeBtn = new JButton("Remove selected line");
        JButton cancelBtn = new JButton("Cancel sale");
        JButton undoBtn = new JButton("Undo last action");
        removeBtn.addActionListener(e -> removeSelectedLine());
        cancelBtn.addActionListener(e -> cancelSale());
        undoBtn.addActionListener(e -> undoBasketAction());
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftButtons.add(removeBtn);
        leftButtons.add(cancelBtn);
        leftButtons.add(undoBtn);

        JButton payBtn = new JButton("PAY");
        payBtn.setFont(payBtn.getFont().deriveFont(Font.BOLD, 14f));
        payBtn.addActionListener(e -> pay());
        cashField.addActionListener(e -> pay());            // Enter in the cash box pays
        cashField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { updateChange(); }
            public void removeUpdate(DocumentEvent e)  { updateChange(); }
            public void changedUpdate(DocumentEvent e) { updateChange(); }
        });
        JPanel payRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        payRow.add(new JLabel("Cash received:"));
        payRow.add(cashField);
        payRow.add(changeLabel);
        payRow.add(payBtn);

        JPanel buttonRow = new JPanel(new BorderLayout());
        buttonRow.add(leftButtons, BorderLayout.WEST);
        buttonRow.add(payRow, BorderLayout.EAST);

        JPanel bottom = new JPanel(new GridLayout(2, 1));
        bottom.add(totalRow);
        bottom.add(buttonRow);
        p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    private void addToCart() {
        String input = itemField.getText().trim();
        if (input.isEmpty()) return;

        Item item = chooseItem(input);
        if (item == null) return;

        if (item.isExpired()) {
            setStatus(item.getName() + " EXPIRED on " + item.getExpiryDate()
                    + " and cannot be sold. Please take it off the shelf.", true);
            return;
        }
        int available = item.getQuantity() - cart.qtyOf(item.getCode());
        if (available <= 0) {
            if (item.getQuantity() == 0) setStatus(item.getName() + " is OUT OF STOCK." + relatedText(item), true);
            else setStatus("Only " + item.getQuantity() + " " + item.getName()
                    + " in stock, and all of them are already in the basket.", true);
            return;
        }
        int qty = (Integer) qtySpinner.getValue();
        if (qty > available) {
            setStatus("Only " + available + " of " + item.getName() + " available.", true);
            return;
        }

        cart.add(item, qty);
        final String addedCode = item.getCode();
        final int addedQty = qty;
        basketUndo.push(UndoStack.of("added " + qty + " x " + item.getName(),
                () -> cart.removeQuantity(addedCode, addedQty)));
        refreshCart();
        itemField.setText("");
        qtySpinner.setValue(1);
        itemField.requestFocusInWindow();
        String note = item.getExpiryDate().equals(LocalDate.now()) ? "  (expires TODAY)" : "";
        setStatus(String.format("Added %d x %s @ %.2f%s", qty, item.getName(), item.getPrice(), note), false);
    }

    /** Exact code first, then name search; if several names match the cashier picks one. */
    private Item chooseItem(String input) {
        Item item = store.get(input.toUpperCase());
        if (item != null) return item;

        List<Item> matches = store.findByName(input);
        if (matches.isEmpty()) {
            setStatus("No item matched \"" + input + "\".", true);
            return null;
        }
        if (matches.size() == 1) return matches.get(0);

        String[] options = new String[matches.size()];
        for (int i = 0; i < options.length; i++) {
            Item m = matches.get(i);
            options[i] = String.format("%s [%s]  price %.2f, stock %d", m.getName(), m.getCode(), m.getPrice(), m.getQuantity());
        }
        Object choice = JOptionPane.showInputDialog(this, "Several items match. Choose one:", "Choose item",
                JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (choice == null) return null;
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(choice)) return matches.get(i);
        }
        return null;
    }

    private void removeSelectedLine() {
        int row = cartTable.getSelectedRow();
        if (row < 0) {
            setStatus("Click a line in the basket first.", true);
            return;
        }
        Cart.Line line = cart.lineAt(row);
        cart.remove(line.getItem().getCode());
        final Item removedItem = line.getItem();
        final int removedQty = line.getQuantity();
        basketUndo.push(UndoStack.of("removed " + removedQty + " x " + removedItem.getName(),
                () -> cart.add(removedItem, removedQty)));
        refreshCart();
        setStatus("Removed " + line.getItem().getName() + " from the basket.", false);
    }

    private void cancelSale() {
        if (cart.isEmpty()) return;
        int r = JOptionPane.showConfirmDialog(this, "Cancel this sale and empty the basket?",
                "Cancel sale", JOptionPane.YES_NO_OPTION);
        if (r != JOptionPane.YES_OPTION) return;
        cart = new Cart();
        basketUndo.clear();
        cashField.setText("");
        refreshCart();
        setStatus("Sale cancelled. No stock was changed.", false);
    }

    /** Reverses the most recent basket action (an add, or a removed line), newest first. */
    private void undoBasketAction() {
        UndoStack.Action action = basketUndo.pop();
        if (action == null) {
            setStatus("Nothing to undo in this basket.", false);
            return;
        }
        action.undo();
        refreshCart();
        setStatus("Undid: " + action.description() + ".", false);
    }

    /** Reverses the most recent stock or price change made in the Inventory tab. */
    private void undoInventoryChange() {
        try {
            String what = store.undoLast();
            refreshAll();
            setStatus("Undid: " + what + ".", false);
        } catch (IllegalArgumentException ex) {
            refreshAll();
            showError(ex.getMessage());
        }
    }

    private void pay() {
        if (cart.isEmpty()) {
            setStatus("The basket is empty. Add at least one item first.", true);
            return;
        }
        double total = cart.total();
        double paid = 0;
        if (total > 0) {
            Double entered = parseMoney(cashField.getText());
            if (entered == null) {
                setStatus("Enter the cash received as a number (example: 500).", true);
                cashField.requestFocusInWindow();
                return;
            }
            paid = entered;
        }

        Store.Sale sale;
        try {
            sale = store.completeSale(cart, paid);          // deducts stock, saves, logs
        } catch (IllegalArgumentException ex) {
            setStatus(ex.getMessage(), true);
            return;
        }

        String text = buildReceipt(sale) + stockAlertsText();
        int lines = text.split("\n", -1).length;
        JTextArea area = new JTextArea(text, Math.min(lines + 1, 28), 46);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setEditable(false);
        cart = new Cart();
        basketUndo.clear();                                 // a paid sale can't be undone
        cashField.setText("");
        refreshAll();
        setStatus(String.format("Sale #%04d completed. Change: %.2f", sale.receiptNo, sale.change), false);
        JOptionPane.showMessageDialog(this, new JScrollPane(area),
                String.format("Receipt #%04d", sale.receiptNo), JOptionPane.INFORMATION_MESSAGE);
        itemField.requestFocusInWindow();
    }

    private String buildReceipt(Store.Sale sale) {
        StringBuilder sb = new StringBuilder();
        sb.append("=========================================\n");
        sb.append("  GROCERY STORE RECEIPT\n");
        sb.append(String.format("  Receipt #%04d      %s%n", sale.receiptNo, sale.time.format(Store.TIME_FORMAT)));
        sb.append("-----------------------------------------\n");
        for (Cart.Line line : cart) {
            sb.append(String.format("  %3d x %-16.16s @ %7.2f %9.2f%n", line.getQuantity(),
                    line.getItem().getName(), line.getUnitPrice(), line.getLineTotal()));
        }
        sb.append("-----------------------------------------\n");
        sb.append(String.format("  %-28s %10.2f%n", "TOTAL", sale.total));
        sb.append(String.format("  %-28s %10.2f%n", "CASH", sale.paid));
        sb.append(String.format("  %-28s %10.2f%n", "CHANGE", sale.change));
        sb.append("=========================================\n");
        sb.append("  Thank you for shopping with us!\n");
        return sb.toString();
    }

    /** Warnings about sold items that are now low or out of stock (cart still holds the sold lines). */
    private String stockAlertsText() {
        StringBuilder sb = new StringBuilder();
        for (Cart.Line line : cart) {
            Item item = line.getItem();
            if (item.getQuantity() == 0) {
                sb.append("  ! ").append(item.getName()).append(" is now OUT OF STOCK.\n");
            } else if (item.isLowStock()) {
                sb.append("  ! ").append(item.getName()).append(" is LOW (").append(item.getQuantity())
                  .append(" left, low-stock alert at ").append(item.getReorderLevel()).append(" or less).\n");
            }
        }
        return sb.length() == 0 ? "" : "\nSTOCK ALERTS:\n" + sb;
    }

    private void refreshCart() {
        cartModel.setRowCount(0);
        int n = 1;
        for (Cart.Line line : cart) {
            cartModel.addRow(new Object[]{String.valueOf(n++), line.getItem().getName(), String.valueOf(line.getQuantity()),
                    String.format("%.2f", line.getUnitPrice()), String.format("%.2f", line.getLineTotal())});
        }
        totalLabel.setText(String.format("TOTAL: %.2f   (%d unit(s))", cart.total(), cart.totalUnits()));
        updateChange();
        refreshSuggestions();
    }

    private void refreshSuggestions() {
        suggestModel.clear();
        for (Store.Suggestion s : store.suggestionsFor(cart, 8)) suggestModel.addElement(s);
    }

    /** Puts the selected suggestion's code in the item box, ready to add. */
    private void useSuggestion() {
        Store.Suggestion s = suggestList.getSelectedValue();
        if (s == null) return;
        itemField.setText(s.item.getCode());
        itemField.requestFocusInWindow();
    }

    /** For an out-of-stock product: what customers usually buy with it, as a short hint. */
    private String relatedText(Item item) {
        List<Store.Suggestion> related = store.relatedTo(item, 3);
        if (related.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("  Customers who buy it also buy: ");
        for (int i = 0; i < related.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(related.get(i).item.getName());
        }
        return sb.append(".").toString();
    }

    private void updateChange() {
        Double paid = parseMoney(cashField.getText());
        if (paid == null || cart.isEmpty()) {
            changeLabel.setText("Change: -");
        } else if (paid >= cart.total()) {
            changeLabel.setText(String.format("Change: %.2f", Cart.round2(paid - cart.total())));
        } else {
            changeLabel.setText(String.format("Short by %.2f", Cart.round2(cart.total() - paid)));
        }
    }

    // ============================================================ INVENTORY TAB

    private JPanel buildInventoryTab() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Search (code or name):"));
        top.add(searchField);
        top.add(new JLabel("Sort by:"));
        top.add(sortKeyBox);
        top.add(sortAlgoBox);
        top.add(inventoryCountLabel);
        top.add(sortInfoLabel);
        searchField.setToolTipText("Filter products by code or name. Clear the field to show all products.");
        sortKeyBox.setToolTipText("Choose the inventory field to sort by.");
        sortAlgoBox.setToolTipText("Choose which sorting method to use.");
        sortKeyBox.addActionListener(e -> refreshInventory());
        sortAlgoBox.addActionListener(e -> refreshInventory());
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { refreshInventory(); }
            public void removeUpdate(DocumentEvent e)  { refreshInventory(); }
            public void changedUpdate(DocumentEvent e) { refreshInventory(); }
        });
        p.add(top, BorderLayout.NORTH);

        invTable.setAutoCreateRowSorter(true);              // click a header to sort
        invTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        configureTable(invTable);
        StatusRenderer renderer = new StatusRenderer();
        invTable.setDefaultRenderer(Object.class, renderer);
        invTable.setDefaultRenderer(Integer.class, renderer);
        invTable.setDefaultRenderer(Double.class, renderer);
        p.add(new JScrollPane(invTable), BorderLayout.CENTER);

        JButton add = new JButton("Add new product");
        JButton receive = new JButton("Receive delivery");
        JButton price = new JButton("Set price");
        JButton writeOff = new JButton("Write off stock");
        JButton delete = new JButton("Delete product");
        JButton undo = new JButton("Undo last stock or price change");
        undo.setToolTipText("Undoes the last delivery, stock write-off, or price change. Does not undo product deletions or sales.");
        add.addActionListener(e -> addProductDialog());
        receive.addActionListener(e -> receiveDialog());
        price.addActionListener(e -> priceDialog());
        writeOff.addActionListener(e -> writeOffDialog());
        delete.addActionListener(e -> deleteDialog());
        undo.addActionListener(e -> undoInventoryChange());
        add.setToolTipText("Create a product with its starting stock and expiry details.");
        receive.setToolTipText("Add delivered units to the selected product.");
        price.setToolTipText("Change the selected product's selling price.");
        writeOff.setToolTipText("Remove damaged, expired, or missing units from the selected product.");
        delete.setToolTipText("Permanently remove the selected product from inventory.");

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(add);
        buttons.add(receive);
        buttons.add(price);
        buttons.add(writeOff);
        buttons.add(delete);
        buttons.add(undo);
        p.add(buttons, BorderLayout.SOUTH);
        return p;
    }

    private void refreshInventory() {
        String q = searchField.getText().trim().toLowerCase();
        invModel.setRowCount(0);
        for (Item i : sortedForDisplay()) {
            if (!q.isEmpty() && !i.getCode().toLowerCase().contains(q)
                    && !i.getName().toLowerCase().contains(q)) continue;
            invModel.addRow(new Object[]{i.getCode(), i.getName(), Store.SECTIONS[i.getSectionIndex()],
                    i.getQuantity(), i.getReorderLevel(), i.getExpiryDate().toString(),
                    i.getPrice(), Store.statusOf(i)});
        }
        inventoryCountLabel.setText(invModel.getRowCount() + " product(s)");
    }

    /**
     * All items, ordered by the sort chosen in the drop-downs. The sorting is our own hand-written
     * code (ItemSorter) running on the actual Item objects; "Added order" means no sorting.
     */
    private Item[] sortedForDisplay() {
        Item[] items = store.allItems().toArray(new Item[0]);
        int key = sortKeyBox.getSelectedIndex();
        if (key <= 0) {
            sortInfoLabel.setText(" ");
            return items;
        }
        invTable.getRowSorter().setSortKeys(null);          // our sort replaces any click-a-header sort
        Comparator<Item> order;
        switch (key) {
            case 1:  order = ItemSorter.BY_NAME;     break;
            case 2:  order = ItemSorter.BY_PRICE;    break;
            case 3:  order = ItemSorter.BY_QUANTITY; break;
            default: order = ItemSorter.BY_EXPIRY;   break;
        }
        ItemSorter.Algorithm algo = ItemSorter.Algorithm.values()[sortAlgoBox.getSelectedIndex()];
        ItemSorter.Result r = ItemSorter.sort(items, order, algo);
        sortInfoLabel.setText(String.format("%,d comparisons, %,d moves", r.comparisons, r.movements));
        return items;
    }

    /** The item for the highlighted row of the inventory table, or null (with a message). */
    private Item selectedItem() {
        int row = invTable.getSelectedRow();
        if (row < 0) {
            setStatus("Select a product in the table first.", true);
            return null;
        }
        String code = (String) invModel.getValueAt(invTable.convertRowIndexToModel(row), 0);
        return store.get(code);
    }

    private void addProductDialog() {
        JTextField name = new JTextField(15);
        JComboBox<String> section = new JComboBox<>(Store.SECTIONS);
        JTextField qty = new JTextField("0");
        JTextField reorder = new JTextField("0");
        JTextField expiry = new JTextField();
        JTextField price = new JTextField();

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Item name:"));                          form.add(name);
        form.add(new JLabel("Section:"));                            form.add(section);
        form.add(new JLabel("Starting quantity:"));                  form.add(qty);
        form.add(new JLabel("Low-stock alert threshold (alert at or below):"));  form.add(reorder);
        form.add(new JLabel("Expiry date (YYYY-MM-DD):"));           form.add(expiry);
        form.add(new JLabel("Price:"));                              form.add(price);

        while (true) {
            int r = JOptionPane.showConfirmDialog(this, form, "Add new product",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return;
            try {
                Item item = store.addProduct(name.getText().trim(), section.getSelectedIndex(),
                        parseInt(qty.getText(), "Starting quantity"),
                        parseInt(reorder.getText(), "Low-stock alert threshold"),
                        parseDate(expiry.getText(), "Expiry date"),
                        parsePrice(price.getText()));
                refreshAll();
                setStatus("Added " + item.getName() + " with product code " + item.getCode() + ".", false);
                return;
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
            }
        }
    }

    private void receiveDialog() {
        Item item = selectedItem();
        if (item == null) return;
        if (item.getQuantity() > 0 && item.isExpired()) {
            showError("The remaining stock of " + item.getName() + " expired on " + item.getExpiryDate()
                    + ".\nWrite it off first, then receive the delivery.");
            return;
        }

        boolean needsDate = item.getQuantity() == 0;      // empty shelf = fresh batch = new expiry date
        JTextField qty = new JTextField("1");
        JTextField expiry = new JTextField();
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Current stock of " + item.getName() + ":"));
        form.add(new JLabel(String.valueOf(item.getQuantity())));
        form.add(new JLabel("Quantity received:"));
        form.add(qty);
        if (needsDate) {
            form.add(new JLabel("Expiry date of delivery (YYYY-MM-DD):"));
            form.add(expiry);
        }

        while (true) {
            int r = JOptionPane.showConfirmDialog(this, form, "Receive delivery",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return;
            try {
                int amount = parseInt(qty.getText(), "Quantity");
                LocalDate newExpiry = needsDate ? parseDate(expiry.getText(), "Expiry date") : null;
                store.receiveDelivery(item, amount, newExpiry);
                refreshAll();
                setStatus("Added " + amount + ". New stock of " + item.getName() + ": " + item.getQuantity(), false);
                return;
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
            }
        }
    }

    private void priceDialog() {
        Item item = selectedItem();
        if (item == null) return;
        while (true) {
            String s = JOptionPane.showInputDialog(this,
                    String.format("New price for %s (current %.2f):", item.getName(), item.getPrice()),
                    "Set price", JOptionPane.QUESTION_MESSAGE);
            if (s == null) return;
            try {
                store.setPrice(item, parsePrice(s));
                refreshAll();
                setStatus(String.format("Price of %s is now %.2f", item.getName(), item.getPrice()), false);
                return;
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
            }
        }
    }

    private void writeOffDialog() {
        Item item = selectedItem();
        if (item == null) return;
        if (item.getQuantity() == 0) {
            showError(item.getName() + " has no stock to write off.");
            return;
        }
        while (true) {
            String s = JOptionPane.showInputDialog(this,
                    "How many " + item.getName() + " to write off (damaged / expired / lost)?\nCurrent stock: "
                            + item.getQuantity(), "Write off stock", JOptionPane.QUESTION_MESSAGE);
            if (s == null) return;
            try {
                int amount = parseInt(s, "Amount");
                store.writeOff(item, amount);
                refreshAll();
                setStatus("Wrote off " + amount + ". New stock of " + item.getName() + ": " + item.getQuantity(), false);
                return;
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
            }
        }
    }

    private void deleteDialog() {
        Item item = selectedItem();
        if (item == null) return;
        int r = JOptionPane.showConfirmDialog(this,
                "Delete " + item.getName() + " [" + item.getCode() + "] from the inventory permanently?",
                "Delete product", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.YES_OPTION) return;
        store.deleteItem(item);
        refreshAll();
        setStatus(item.getName() + " was deleted.", false);
    }

    // ============================================================= REPORTS TAB

    private JPanel buildReportsTab() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(new EmptyBorder(10, 10, 10, 10));

        salesLabel.setFont(salesLabel.getFont().deriveFont(Font.BOLD, 14f));
        p.add(salesLabel, BorderLayout.NORTH);

        JPanel groupControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        groupControls.add(new JLabel("Product groups: link items bought together at least"));
        groupControls.add(strengthSpinner);
        groupControls.add(new JLabel("time(s)"));
        JButton demoBtn = new JButton("Add demo purchases");
        groupControls.add(demoBtn);
        strengthSpinner.addChangeListener(e -> refreshReports());
        demoBtn.addActionListener(e -> {
            try {
                int made = store.addDemoPurchases();
                refreshAll();
                setStatus("Added " + made + " demo purchases to the bought-together graph "
                        + "(demo only: not saved, not counted as sales).", false);
            } catch (IllegalArgumentException ex) {
                setStatus(ex.getMessage(), true);
            }
        });

        JScrollPane lowPane = new JScrollPane(new JTable(lowModel));
        lowPane.setBorder(new TitledBorder("Products at or below their low-stock alert threshold"));
        JScrollPane expPane = new JScrollPane(new JTable(expModel));
        expPane.setBorder(new TitledBorder("Expiring within " + Store.EXPIRY_ALERT_DAYS + " days (or already expired)"));
        JScrollPane salesLogPane = new JScrollPane(new JTable(salesLogModel));
        salesLogPane.setBorder(new TitledBorder("Completed sales in selected date range"));
        JScrollPane groupPane = new JScrollPane(new JTable(groupModel));
        groupPane.setBorder(new TitledBorder("Products usually bought together"));

        JButton salesSearchBtn = new JButton("Show sales");
        salesSearchBtn.addActionListener(e -> refreshSalesLogReport());
        salesStartField.addActionListener(e -> refreshSalesLogReport());
        salesEndField.addActionListener(e -> refreshSalesLogReport());
        JPanel salesControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        salesControls.add(new JLabel("Start date (YYYY-MM-DD):"));
        salesControls.add(salesStartField);
        salesControls.add(new JLabel("End date (YYYY-MM-DD):"));
        salesControls.add(salesEndField);
        salesControls.add(salesSearchBtn);
        salesControls.add(salesLogStatus);
        JPanel salesReport = new JPanel(new BorderLayout(8, 8));
        salesReport.add(salesControls, BorderLayout.NORTH);
        salesReport.add(salesLogPane, BorderLayout.CENTER);

        JPanel groupsReport = new JPanel(new BorderLayout(8, 8));
        groupsReport.add(groupControls, BorderLayout.NORTH);
        groupsReport.add(groupPane, BorderLayout.CENTER);

        JTabbedPane reportTabs = new JTabbedPane();
        configureTable((JTable) lowPane.getViewport().getView());
        configureTable((JTable) expPane.getViewport().getView());
        configureTable((JTable) salesLogPane.getViewport().getView());
        configureTable((JTable) groupPane.getViewport().getView());
        reportTabs.addTab("Low / Out of Stock", lowPane);
        reportTabs.addTab("Expiring / Expired", expPane);
        reportTabs.addTab("Sales Log", salesReport);
        reportTabs.addTab("Bought Together", groupsReport);
        p.add(reportTabs, BorderLayout.CENTER);
        return p;
    }

    private void refreshReports() {
        Store.Summary s = store.todaySummary();
        salesLabel.setText(String.format("Today (%s):  %d sale(s),  %d unit(s) sold,  total %.2f",
                LocalDate.now(), s.sales, s.units, s.revenue));

        lowModel.setRowCount(0);
        for (Item i : store.lowStockItems()) {
            lowModel.addRow(new Object[]{i.getCode(), i.getName(), Store.SECTIONS[i.getSectionIndex()],
                    i.getQuantity(), i.getReorderLevel(), Store.statusOf(i)});
        }

        expModel.setRowCount(0);
        LocalDate today = LocalDate.now();
        for (Item i : store.expiringSoon()) {
            long left = ChronoUnit.DAYS.between(today, i.getExpiryDate());
            String status;
            if (left < 0)       status = "EXPIRED (" + (-left) + " day(s) ago)";
            else if (left == 0) status = "EXPIRES TODAY";
            else                status = "in " + left + " day(s)";
            expModel.addRow(new Object[]{i.getCode(), i.getName(), i.getQuantity(), i.getExpiryDate().toString(), status});
        }

        groupModel.setRowCount(0);
        int g = 1;
        for (List<Item> group : store.productGroups((Integer) strengthSpinner.getValue())) {
            StringBuilder names = new StringBuilder();
            for (Item i : group) {
                if (names.length() > 0) names.append(", ");
                names.append(i.getName());
            }
            groupModel.addRow(new Object[]{g++, group.size(), names.toString()});
        }

        refreshSalesLogReport();
    }

    private void refreshSalesLogReport() {
        LocalDate start;
        LocalDate end;
        try {
            start = parseDate(salesStartField.getText(), "Start date");
            end = parseDate(salesEndField.getText(), "End date");
            if (end.isBefore(start)) {
                salesLogModel.setRowCount(0);
                salesLogStatus.setText("End date must be on or after start date.");
                return;
            }
        } catch (IllegalArgumentException ex) {
            salesLogModel.setRowCount(0);
            salesLogStatus.setText(ex.getMessage());
            return;
        }

        try {
            List<Store.SalesLogEntry> sales = store.salesBetween(start, end);
            salesLogModel.setRowCount(0);
            int units = 0;
            double total = 0;
            for (Store.SalesLogEntry sale : sales) {
                salesLogModel.addRow(new Object[]{sale.receiptNo, sale.time.format(Store.TIME_FORMAT),
                        sale.units, sale.total});
                units += sale.units;
                total += sale.total;
            }
            salesLogStatus.setText(String.format("%d sale(s), %d unit(s), total %.2f",
                    sales.size(), units, total));
        } catch (IOException ex) {
            salesLogModel.setRowCount(0);
            salesLogStatus.setText("Could not read sales log: " + ex.getMessage());
        }
    }

    // ============================================================= PRIORITY TAB

    private JPanel buildPriorityTab() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel info = new JLabel("<html><b>Items that need attention, most urgent first</b> (taken out of a custom max-heap).<br>"
                + "Score = 0.6 x stock shortage + 0.4 x expiry risk, each scaled 0 to 1. "
                + "<i>Stock shortage</i>: how far sellable stock is below the low-stock alert threshold (expired stock counts as 0). "
                + "<i>Expiry risk</i>: rises from 0 to 1 over the last " + Store.EXPIRY_ALERT_DAYS
                + " days before expiry, 1 if already expired.<br>"
                + "Stock has the bigger weight because a missing product loses sales right now, "
                + "while expiring stock can still be discounted.</html>");
        p.add(info, BorderLayout.NORTH);

        JTable table = new JTable(priorityModel);
        configureTable(table);
        table.getColumnModel().getColumn(3).setPreferredWidth(260);
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        return p;
    }

    private void refreshPriority() {
        priorityModel.setRowCount(0);
        int rank = 1;
        for (RestockHeap.Entry e : store.actionQueue()) {
            Item i = e.item;
            priorityModel.addRow(new Object[]{rank++, i.getCode(), i.getName(), e.action, i.getQuantity(),
                    i.getReorderLevel(), i.getExpiryDate().toString(), String.format("%.2f", e.priority)});
        }
    }

    // ============================================================ BENCHMARK TAB

    private JPanel buildBenchmarkTab() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(benchRunBtn);
        top.add(benchExportBtn);
        top.add(benchStatus);
        benchExportBtn.setEnabled(false);
        benchRunBtn.addActionListener(e -> runBenchmark());
        benchExportBtn.addActionListener(e -> exportBenchmark());
        p.add(top, BorderLayout.NORTH);

        JTable table = new JTable(benchModel);
        configureTable(table);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {90, 270, 220, 90, 190, 420};
        for (int c = 0; c < widths.length; c++) table.getColumnModel().getColumn(c).setPreferredWidth(widths[c]);
        p.add(new JScrollPane(table), BorderLayout.CENTER);

        JLabel note = new JLabel("<html>Uses its own temporary test items (100, 500, 1,000 and 5,000), so your inventory is never touched. "
                + "Time is the median of several runs; search and delete rows are averages per operation. "
                + "Comparisons / movements are counted inside the algorithms, so they are exactly repeatable "
                + "(times vary a little from run to run).</html>");
        p.add(note, BorderLayout.SOUTH);
        return p;
    }

    /** Runs the benchmark on a background thread so the window stays responsive. */
    private void runBenchmark() {
        benchRunBtn.setEnabled(false);
        benchExportBtn.setEnabled(false);
        benchModel.setRowCount(0);
        new SwingWorker<List<BenchmarkRunner.Row>, String>() {
            @Override
            protected List<BenchmarkRunner.Row> doInBackground() {
                return BenchmarkRunner.run(this::publish);
            }

            @Override
            protected void process(List<String> messages) {
                benchStatus.setText(messages.get(messages.size() - 1));
            }

            @Override
            protected void done() {
                try {
                    benchRows = get();
                    for (BenchmarkRunner.Row r : benchRows) {
                        benchModel.addRow(new Object[]{String.format("%,d", r.size), r.operation, r.structure,
                                r.timeText(), r.countsText(), r.observation});
                    }
                    benchStatus.setText("Done: " + benchRows.size() + " results.");
                    benchExportBtn.setEnabled(true);
                } catch (Exception ex) {
                    benchStatus.setText("Benchmark failed: " + ex);
                }
                benchRunBtn.setEnabled(true);
            }
        }.execute();
    }

    /** Saves the results table to benchmark_results.csv (opens in Excel). */
    private void exportBenchmark() {
        File file = new File("benchmark_results.csv");
        try (PrintWriter out = new PrintWriter(new FileWriter(file))) {
            out.println("Dataset Size,Operation,Algorithm / Structure,Time (ns),Comparisons / Movements,Observation");
            for (BenchmarkRunner.Row r : benchRows) {
                out.println(r.size + ",\"" + r.operation + "\",\"" + r.structure + "\",\""
                        + r.timeText().replace(",", "") + "\",\"" + r.countsText() + "\",\"" + r.observation + "\"");
            }
            setStatus("Saved " + file.getAbsolutePath(), false);
        } catch (IOException ex) {
            setStatus("Could not save the CSV file (" + ex.getMessage() + ").", true);
        }
    }

    // ================================================================= helpers

    private void refreshAll() {
        refreshCart();
        refreshInventory();
        refreshReports();
        refreshPriority();
    }

    private void setStatus(String msg, boolean error) {
        statusLabel.setText(msg);
        statusLabel.setForeground(error ? new Color(176, 0, 32) : Color.DARK_GRAY);
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Please check", JOptionPane.ERROR_MESSAGE);
    }

    private static void configureTable(JTable table) {
        table.setRowHeight(Math.max(table.getRowHeight(), 29));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowSelectionAllowed(true);
        table.setColumnSelectionAllowed(false);
        table.setShowGrid(true);
        table.setIntercellSpacing(new Dimension(5, 2));
        table.setGridColor(new Color(210, 210, 210));
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setFont(header.getFont().deriveFont(Font.BOLD));
        table.setDefaultRenderer(Double.class, new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                setText(value instanceof Number ? String.format("%.2f", ((Number) value).doubleValue())
                        : String.valueOf(value));
                setHorizontalAlignment(RIGHT);
            }
        });
    }

    private static void applyControlSizing(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof AbstractButton) {
                AbstractButton button = (AbstractButton) component;
                Insets margin = button.getMargin();
                button.setMargin(new Insets(margin.top + 4, margin.left + 8,
                        margin.bottom + 4, margin.right + 8));
                button.setFont(button.getFont().deriveFont(button.getFont().getSize2D() + 1.5f));
            } else if (component instanceof JTabbedPane) {
                JTabbedPane tabbedPane = (JTabbedPane) component;
                tabbedPane.setFont(tabbedPane.getFont().deriveFont(tabbedPane.getFont().getSize2D() + 2f));
            }
            if (component instanceof Container) {
                applyControlSizing((Container) component);
            }
        }
    }

    /** Read-only table model; column class follows the data so numbers sort as numbers. */
    private static DefaultTableModel newModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int col) {
                Object v = getRowCount() > 0 ? getValueAt(0, col) : null;
                return v == null ? Object.class : v.getClass();
            }
        };
    }

    /** Parses a money amount from the cash box; null if it isn't a sensible number. */
    private static Double parseMoney(String s) {
        try {
            double v = Double.parseDouble(s.trim());
            if (Double.isNaN(v) || v < 0 || v > Store.MAX_PRICE) return null;
            return v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseInt(String s, String what) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " must be a whole number.");
        }
    }

    private static double parsePrice(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("That's not a valid price (example: 45.50).");
        }
    }

    private static LocalDate parseDate(String s, String what) {
        try {
            return LocalDate.parse(s.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(what + ": use the format YYYY-MM-DD (example: 2026-12-31).");
        }
    }

    /** Tints inventory rows: red = out of stock / expired, orange = low stock; formats prices. */
    private static class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, selected, focus, row, col);
            if (value instanceof Double) setText(String.format("%.2f", (Double) value));
            setHorizontalAlignment(value instanceof Number ? RIGHT : LEFT);

            if (!selected) {
                String status = String.valueOf(table.getModel().getValueAt(table.convertRowIndexToModel(row), 7));
                if (status.equals("OUT OF STOCK") || status.equals("EXPIRED")) c.setBackground(new Color(255, 215, 215));
                else if (status.equals("LOW STOCK"))                          c.setBackground(new Color(255, 236, 200));
                else                                                          c.setBackground(Color.WHITE);
            }
            return c;
        }
    }

    // ==================================================================== main

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // fall back to the default look
            }
            new GroceryGUI().setVisible(true);
        });
    }
}
