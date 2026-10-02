import java.awt.*;
import java.awt.event.*;
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
 * Tabs: Sale (checkout), Inventory (manage products), Reports (today's sales + alerts).
 * All the real work is done by Store, so the rules are the same as in the console version.
 */
public class GroceryGUI extends JFrame {

    private final Store store = new Store();
    private Cart cart = new Cart();

    // ---- Sale tab
    private final JTextField itemField = new JTextField(18);
    private final JSpinner qtySpinner = new JSpinner(new SpinnerNumberModel(1, 1, Store.MAX_AMOUNT, 1));
    private final DefaultTableModel cartModel = newModel("#", "Item", "Qty", "Price", "Amount");
    private final JTable cartTable = new JTable(cartModel);
    private final JLabel totalLabel = new JLabel("TOTAL: 0.00");
    private final JTextField cashField = new JTextField(10);
    private final JLabel changeLabel = new JLabel("Change: -");
    private final JLabel statusLabel = new JLabel(" ");

    // ---- Inventory tab
    private final JTextField searchField = new JTextField(20);
    private final DefaultTableModel invModel =
            newModel("Code", "Name", "Section", "Qty", "Reorder at", "Expires", "Price", "Status");
    private final JTable invTable = new JTable(invModel);

    // ---- Reports tab
    private final JLabel salesLabel = new JLabel(" ");
    private final DefaultTableModel lowModel = newModel("Code", "Name", "Section", "Qty", "Reorder at", "Status");
    private final DefaultTableModel expModel = newModel("Code", "Name", "Qty", "Expires", "Status");

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
        tabs.addTab("Sale", buildSaleTab());
        tabs.addTab("Inventory", buildInventoryTab());
        tabs.addTab("Reports", buildReportsTab());
        tabs.addChangeListener(e -> refreshAll());

        add(tabs, BorderLayout.CENTER);
        statusLabel.setBorder(new EmptyBorder(4, 10, 4, 10));
        add(statusLabel, BorderLayout.SOUTH);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                store.save();
            }
        });
        setSize(900, 600);
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
        p.add(top, BorderLayout.NORTH);

        p.add(new JScrollPane(cartTable), BorderLayout.CENTER);

        totalLabel.setFont(totalLabel.getFont().deriveFont(Font.BOLD, 24f));
        JPanel totalRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        totalRow.add(totalLabel);

        JButton removeBtn = new JButton("Remove selected line");
        JButton cancelBtn = new JButton("Cancel sale");
        removeBtn.addActionListener(e -> removeSelectedLine());
        cancelBtn.addActionListener(e -> cancelSale());
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftButtons.add(removeBtn);
        leftButtons.add(cancelBtn);

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
            if (item.getQuantity() == 0) setStatus(item.getName() + " is OUT OF STOCK.", true);
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
        refreshCart();
        setStatus("Removed " + line.getItem().getName() + " from the basket.", false);
    }

    private void cancelSale() {
        if (cart.isEmpty()) return;
        int r = JOptionPane.showConfirmDialog(this, "Cancel this sale and empty the basket?",
                "Cancel sale", JOptionPane.YES_NO_OPTION);
        if (r != JOptionPane.YES_OPTION) return;
        cart = new Cart();
        cashField.setText("");
        refreshCart();
        setStatus("Sale cancelled. No stock was changed.", false);
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
                  .append(" left, reorder level ").append(item.getReorderLevel()).append(").\n");
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
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { refreshInventory(); }
            public void removeUpdate(DocumentEvent e)  { refreshInventory(); }
            public void changedUpdate(DocumentEvent e) { refreshInventory(); }
        });
        p.add(top, BorderLayout.NORTH);

        invTable.setAutoCreateRowSorter(true);              // click a header to sort
        invTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        StatusRenderer renderer = new StatusRenderer();
        invTable.setDefaultRenderer(Object.class, renderer);
        invTable.setDefaultRenderer(Integer.class, renderer);
        invTable.setDefaultRenderer(Double.class, renderer);
        p.add(new JScrollPane(invTable), BorderLayout.CENTER);

        JButton add = new JButton("Add new product...");
        JButton receive = new JButton("Receive delivery...");
        JButton price = new JButton("Set price...");
        JButton writeOff = new JButton("Write off stock...");
        JButton delete = new JButton("Delete product");
        add.addActionListener(e -> addProductDialog());
        receive.addActionListener(e -> receiveDialog());
        price.addActionListener(e -> priceDialog());
        writeOff.addActionListener(e -> writeOffDialog());
        delete.addActionListener(e -> deleteDialog());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(add);
        buttons.add(receive);
        buttons.add(price);
        buttons.add(writeOff);
        buttons.add(delete);
        p.add(buttons, BorderLayout.SOUTH);
        return p;
    }

    private void refreshInventory() {
        String q = searchField.getText().trim().toLowerCase();
        invModel.setRowCount(0);
        for (Item i : store.allItems()) {
            if (!q.isEmpty() && !i.getCode().toLowerCase().contains(q)
                    && !i.getName().toLowerCase().contains(q)) continue;
            invModel.addRow(new Object[]{i.getCode(), i.getName(), Store.SECTIONS[i.getSectionIndex()],
                    i.getQuantity(), i.getReorderLevel(), i.getExpiryDate().toString(),
                    i.getPrice(), Store.statusOf(i)});
        }
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
        form.add(new JLabel("Reorder level (alert at or below):"));  form.add(reorder);
        form.add(new JLabel("Expiry date (YYYY-MM-DD):"));           form.add(expiry);
        form.add(new JLabel("Price:"));                              form.add(price);

        while (true) {
            int r = JOptionPane.showConfirmDialog(this, form, "Add new product",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return;
            try {
                Item item = store.addProduct(name.getText().trim(), section.getSelectedIndex(),
                        parseInt(qty.getText(), "Starting quantity"),
                        parseInt(reorder.getText(), "Reorder level"),
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

        JScrollPane lowPane = new JScrollPane(new JTable(lowModel));
        lowPane.setBorder(new TitledBorder("Low / out of stock"));
        JScrollPane expPane = new JScrollPane(new JTable(expModel));
        expPane.setBorder(new TitledBorder("Expiring within " + Store.EXPIRY_ALERT_DAYS + " days (or already expired)"));

        JPanel center = new JPanel(new GridLayout(2, 1, 8, 8));
        center.add(lowPane);
        center.add(expPane);
        p.add(center, BorderLayout.CENTER);
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
    }

    // ================================================================= helpers

    private void refreshAll() {
        refreshCart();
        refreshInventory();
        refreshReports();
    }

    private void setStatus(String msg, boolean error) {
        statusLabel.setText(msg);
        statusLabel.setForeground(error ? new Color(176, 0, 32) : Color.DARK_GRAY);
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Please check", JOptionPane.ERROR_MESSAGE);
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
