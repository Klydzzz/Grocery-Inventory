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
import javax.swing.plaf.FontUIResource;
import javax.swing.text.JTextComponent;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.table.*;
import java.util.List;


// File overview: Swing-based storefront and admin interface.
// This class keeps the related logic together for easier reading and maintenance.
public class GroceryGUI extends JFrame {

    private static final int DEFAULT_FONT = 16;
    private static final int MIN_FONT = 8;
    private static final int MAX_FONT = 40;
    private static final int FONT_STEP = 1;

    private static Color BG = new Color(243, 247, 245);
    private static Color CARD = Color.WHITE;
    private static Color LINE = new Color(214, 222, 218);
    private static Color LINE_DARK = new Color(178, 192, 186);
    private static Color TEXT = new Color(30, 41, 38);
    private static Color MUTED = new Color(92, 108, 101);
    private static Color PRIMARY = new Color(31, 122, 90);
    private static Color PRIMARY_DARK = new Color(18, 84, 62);
    private static Color PRIMARY_LIGHT = new Color(214, 236, 226);
    private static Color DANGER = new Color(188, 48, 58);
    private static Color STRIPE = new Color(246, 250, 248);
    private static Color SEL_BG = new Color(200, 230, 216);
    private static Color TABLE_HEAD = new Color(228, 240, 234);
    private static Color GOOD_BG = new Color(224, 244, 233);
    private static Color BAD_BG = new Color(255, 230, 230);
    private static Color HINT_BG = new Color(255, 249, 230);
    private static Color HINT_ACCENT = new Color(231, 170, 40);
    private static Color GREEN = PRIMARY;
    private static Color RED = DANGER;

    private static Color ACCENT_TEXT = new Color(18, 84, 62);
    private static Color ALERT_BG = new Color(255, 222, 222);
    private static Color WARN_BG = new Color(255, 239, 205);
    private static Color NEUTRAL_FILL = new Color(238, 244, 241);
    private static Color NEUTRAL_HOVER = new Color(220, 236, 228);
    private static Color DISABLED_FILL = new Color(228, 232, 230);
    private static Color DISABLED_TEXT = new Color(110, 122, 117);
    private static Color RECEIPT_BG = new Color(255, 253, 246);
    private static final Color HEADER_TIP = new Color(232, 245, 238);
    private boolean darkMode = false;

    private static final Border FIELD_BORDER = new CompoundBorder(
            new LineBorder(LINE_DARK, 1, true), new EmptyBorder(6, 9, 6, 9));

    private final Store store = new Store();
    private Cart cart = new Cart();
    private final UndoStack basketUndo = new UndoStack();
    private int fontSize = DEFAULT_FONT;

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

    private static final int MAX_PREDICTIONS = 8;
    private final DefaultListModel<Item> predictModel = new DefaultListModel<>();
    private final JList<Item> predictList = new JList<>(predictModel);
    private final JPopupMenu predictPopup = new JPopupMenu();
    private boolean suppressPredict = false;

    private final JTextField searchField = new JTextField(16);
    private final JLabel inventoryCountLabel = new JLabel(" ");
    private final DefaultTableModel invModel =
            newModel("Code", "Name", "Section", "Qty", "Low-stock alert at", "Expires", "Price", "Status");
    private final JTable invTable = new JTable(invModel);

    private final JComboBox<String> sortKeyBox =
            new JComboBox<>(new String[]{"Added order", "Name", "Price", "Quantity", "Expiry date"});
    private final JComboBox<String> sortAlgoBox =
            new JComboBox<>(new String[]{"Insertion sort", "Selection sort", "Bubble sort"});
    private final JLabel sortInfoLabel = new JLabel(" ");

    private final DefaultTableModel priorityModel =
            newModel("Rank", "Code", "Name", "Action", "Qty", "Low-stock alert at", "Expires", "Score");

    private final DefaultTableModel benchModel = newModel("Dataset Size", "Operation",
            "Algorithm / Structure", "Time (ns)", "Comparisons / Movements", "Observation");
    private final JButton benchRunBtn = new PillButton("Run speed test", PillButton.Kind.PRIMARY);
    private final JButton benchExportBtn = new PillButton("Save results (CSV)", PillButton.Kind.NEUTRAL);
    private final JLabel benchStatus = new JLabel("Press \"Run speed test\" (takes a few seconds).");
    private List<BenchmarkRunner.Row> benchRows = new ArrayList<>();

    private final DefaultTableModel testCaseModel = newModel("ID", "Test case", "Result", "Details");
    private final JButton testCasesRunBtn = new PillButton("Run test cases", PillButton.Kind.PRIMARY);
    private final JLabel testCasesStatus = new JLabel("Run the test cases to check the data structures.");

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
    private JLabel headerTitle;
    private JLabel headerTip;

    public GroceryGUI() {
        super("Grocery Point of Sale & Inventory");

        boolean seeded = store.startup();
        if (!store.getWarnings().isEmpty()) {
            JOptionPane.showMessageDialog(null, String.join("\n", store.getWarnings()),
                    "Problems while loading", JOptionPane.WARNING_MESSAGE);
            store.getWarnings().clear();
        }

        getContentPane().setBackground(BG);

        tabs = new JTabbedPane();
        tabs.setUI(new ModernTabUI());
        tabs.setOpaque(true);
        tabs.setBackground(CARD);
        tabs.setFont(tabs.getFont().deriveFont(Font.BOLD));
        tabs.addTab("Reports", buildReportsTab());
        tabs.addTab("Checkout", buildSaleTab());
        tabs.addTab("Products", buildInventoryTab());
        tabs.addTab("Needs Attention", buildPriorityTab());
        tabs.addTab("Speed Test", buildBenchmarkTab());
        tabs.addTab("Test Cases", buildTestCasesTab());
        String[] tabHelp = {"Sales, low stock and expiry reports (Alt+1)", "Ring up a customer (Alt+2)",
                "See and change the products in the store (Alt+3)", "What to restock or sell first (Alt+4)",
                "Compare how fast the sorting and searching methods are (Alt+5)",
                "Check the data structures with 10 test cases (Alt+6)"};
        for (int i = 0; i < tabHelp.length; i++) {
            tabs.setMnemonicAt(i, KeyEvent.VK_1 + i);
            tabs.setToolTipTextAt(i, tabHelp[i]);
        }
        tabs.setSelectedIndex(0);
        tabs.addChangeListener(e -> refreshAll());

        add(buildHeader(), BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        statusLabel.setOpaque(true);
        statusLabel.setBorder(statusBorder(GREEN));
        add(statusLabel, BorderLayout.SOUTH);
        applyControlSizing(getContentPane());
        installTextSizeShortcuts();
        ToolTipManager.sharedInstance().setInitialDelay(300);
        ToolTipManager.sharedInstance().setDismissDelay(12000);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                store.save();
            }
        });
        setSize(1200, 800);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);

        refreshAll();
        if (seeded) setStatus("No save file found. Started with sample data.", false);
        else setStatus("Welcome! Loaded " + store.itemCount() + " item(s).", false);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, PRIMARY_DARK, getWidth(), 0, PRIMARY));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(12, 20, 12, 20));

        headerTitle = new JLabel("Grocery Store");
        headerTitle.setForeground(Color.WHITE);
        headerTitle.setFont(headerTitle.getFont().deriveFont(Font.BOLD, fontSize * 1.4f));
        headerTip = new JLabel("Tip: hold the mouse over any button to see what it does.");
        headerTip.setForeground(HEADER_TIP);
        JPanel text = panel(new GridLayout(0, 1, 0, 2));
        text.add(headerTitle);
        text.add(headerTip);
        header.add(text, BorderLayout.WEST);

        JButton smaller = new PillButton("A-", PillButton.Kind.NEUTRAL);
        JButton larger = new PillButton("A+", PillButton.Kind.NEUTRAL);
        JButton themeToggle = new PillButton(darkMode ? "Light mode" : "Dark mode", PillButton.Kind.NEUTRAL);
        smaller.setToolTipText("Make all text smaller (Ctrl and minus)");
        larger.setToolTipText("Make all text bigger (Ctrl and plus)");
        themeToggle.setToolTipText("Switch between light and dark mode");
        smaller.addActionListener(e -> changeTextSize(fontSize - FONT_STEP));
        larger.addActionListener(e -> changeTextSize(fontSize + FONT_STEP));
        themeToggle.addActionListener(e -> {
            applyTheme(!darkMode);
            themeToggle.setText(darkMode ? "Light mode" : "Dark mode");
        });
        JPanel size = panel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        size.add(themeToggle);
        size.add(smaller);
        size.add(larger);
        header.add(size, BorderLayout.EAST);
        return header;
    }

    private JPanel buildSaleTab() {
        JPanel p = page();

        JPanel top = panel(new WrapLayout(FlowLayout.LEFT, 8, 4));
        top.add(new JLabel("Item name or code:"));
        top.add(itemField);
        top.add(new JLabel("How many:"));
        ((JSpinner.DefaultEditor) qtySpinner.getEditor()).getTextField().setColumns(4);
        top.add(qtySpinner);
        JButton addBtn = new PillButton("Add to Basket", PillButton.Kind.PRIMARY);
        top.add(addBtn);
        addBtn.addActionListener(e -> addToCart());
        itemField.addActionListener(e -> {
            if (!acceptPrediction()) {
                hidePredictions();
                addToCart();
            }
        });
        setupPredictiveSearch();
        itemField.setToolTipText("Type a product code, or part of the name (like \"milk\"). Matching products are listed below: use the Up/Down arrow keys or the mouse to pick one, then press Enter.");
        qtySpinner.setToolTipText("Choose how many of this item to add.");
        addBtn.setToolTipText("Put the item into the customer's basket (or press Enter).");
        cashField.setToolTipText("Type the money the customer hands over, then press Enter to finish.");

        JPanel entryCard = card(new BorderLayout());
        entryCard.add(top, BorderLayout.CENTER);

        JPanel north = panel(new BorderLayout(0, 8));
        north.add(hint("How to ring up a sale:   1) Type an item name or code and press Enter.   "
                + "2) Check the basket below.   3) Type the cash received and press Pay Now."), BorderLayout.NORTH);
        north.add(entryCard, BorderLayout.CENTER);
        p.add(north, BorderLayout.NORTH);

        configureTable(cartTable);
        p.add(tableCard(cartTable, "Basket"), BorderLayout.CENTER);

        suggestList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, selected, false);
                l.setBorder(new EmptyBorder(7, 10, 7, 10));
                l.setForeground(TEXT);
                l.setBackground(selected ? SEL_BG : (index % 2 == 0 ? CARD : STRIPE));
                return l;
            }
        });
        suggestList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) useSuggestion();
            }
        });
        JScrollPane suggestPane = new JScrollPane(suggestList);
        suggestPane.setBorder(titled("Customers also buy (double-click one to use it)"));
        suggestPane.getViewport().setBackground(CARD);
        suggestPane.setPreferredSize(new Dimension(340, 100));
        suggestList.setToolTipText("Double-click a suggestion to put its product code in the item box.");
        p.add(suggestPane, BorderLayout.EAST);

        totalLabel.setFont(totalLabel.getFont().deriveFont(Font.BOLD, fontSize * 1.6f));
        totalLabel.setForeground(ACCENT_TEXT);
        JPanel totalRow = panel(new FlowLayout(FlowLayout.RIGHT));
        totalRow.add(totalLabel);

        JButton removeBtn = new PillButton("Remove Selected Item", PillButton.Kind.NEUTRAL);
        JButton cancelBtn = new PillButton("Cancel Sale", PillButton.Kind.DANGER);
        JButton undoBtn = new PillButton("Undo Last Action", PillButton.Kind.NEUTRAL);
        removeBtn.setToolTipText("Click a line in the basket first, then press this to take it out.");
        cancelBtn.setToolTipText("Throw away this whole basket. No stock is changed.");
        undoBtn.setToolTipText("Reverse the last thing you did in this basket.");
        removeBtn.addActionListener(e -> removeSelectedLine());
        cancelBtn.addActionListener(e -> cancelSale());
        undoBtn.addActionListener(e -> undoBasketAction());
        JPanel leftButtons = panel(new WrapLayout(FlowLayout.LEFT, 8, 4));
        leftButtons.add(removeBtn);
        leftButtons.add(undoBtn);
        leftButtons.add(cancelBtn);

        JButton payBtn = new PillButton("Pay Now", PillButton.Kind.PRIMARY);
        payBtn.setFont(payBtn.getFont().deriveFont(Font.BOLD, fontSize * 1.2f));
        payBtn.setToolTipText("Finish the sale and show the receipt.");
        JButton exactBtn = new PillButton("Exact Amount", PillButton.Kind.NEUTRAL);
        exactBtn.setToolTipText("Fill in the cash box with the exact total (no change needed).");
        exactBtn.addActionListener(e -> {
            if (cart.isEmpty()) {
                setStatus("The basket is empty. Add at least one item first.", true);
                return;
            }
            cashField.setText(String.format("%.2f", cart.total()));
            cashField.requestFocusInWindow();
        });
        payBtn.addActionListener(e -> pay());
        cashField.addActionListener(e -> pay());
        cashField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { updateChange(); }
            public void removeUpdate(DocumentEvent e)  { updateChange(); }
            public void changedUpdate(DocumentEvent e) { updateChange(); }
        });
        changeLabel.setFont(changeLabel.getFont().deriveFont(Font.BOLD));
        JPanel payRow = panel(new WrapLayout(FlowLayout.RIGHT, 8, 4));
        payRow.add(new JLabel("Cash received:"));
        payRow.add(cashField);
        payRow.add(exactBtn);
        payRow.add(changeLabel);
        payRow.add(payBtn);

        JPanel bottom = card(null);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.add(totalRow);
        bottom.add(payRow);
        bottom.add(leftButtons);
        p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    private void addToCart() {
        String input = itemField.getText().trim();
        if (input.isEmpty()) {
            setStatus("Type an item name or code first, then press Enter.", true);
            itemField.requestFocusInWindow();
            return;
        }

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

    private Item chooseItem(String input) {
        Item item = store.get(input.toUpperCase());
        if (item != null) return item;

        List<Item> matches = store.findByName(input);
        if (matches.isEmpty()) {
            setStatus("No item matched \"" + input + "\". Try a shorter part of the name.", true);
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
            setStatus("Click a line in the basket first, then press \"Remove Selected Item\".", true);
            return;
        }
        Cart.Line line = cart.lineAt(cartTable.convertRowIndexToModel(row));
        cart.remove(line.getItem().getCode());
        final Item removedItem = line.getItem();
        final int removedQty = line.getQuantity();
        basketUndo.push(UndoStack.of("removed " + removedQty + " x " + removedItem.getName(),
                () -> cart.add(removedItem, removedQty)));
        refreshCart();
        setStatus("Removed " + line.getItem().getName() + " from the basket. (You can press Undo Last Action.)", false);
    }

    private void cancelSale() {
        if (cart.isEmpty()) {
            setStatus("The basket is already empty.", false);
            return;
        }
        if (!confirm("Cancel this sale and empty the basket?\nNo stock will be changed.",
                "Cancel sale", "Yes, cancel the sale", "No, keep the basket", JOptionPane.QUESTION_MESSAGE)) return;
        cart = new Cart();
        basketUndo.clear();
        cashField.setText("");
        refreshCart();
        setStatus("Sale cancelled. No stock was changed.", false);
    }

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
                setStatus("Type the cash received as a number (example: 500).", true);
                cashField.requestFocusInWindow();
                return;
            }
            paid = entered;
        }

        Store.Sale sale;
        try {
            sale = store.completeSale(cart, paid);
        } catch (IllegalArgumentException ex) {
            setStatus(ex.getMessage(), true);
            return;
        }

        String text = buildReceipt(sale) + stockAlertsText();
        int lines = text.split("\n", -1).length;
        JTextArea area = new JTextArea(text, Math.min(lines + 1, 28), 46);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, fontSize));
        area.setEditable(false);
        area.setBackground(RECEIPT_BG);
        area.setCaretColor(TEXT);
        area.setForeground(TEXT);
        area.setBorder(new EmptyBorder(10, 14, 10, 14));
        cart = new Cart();
        basketUndo.clear();
        cashField.setText("");
        refreshAll();
        setStatus(String.format("Sale #%04d completed. Give the customer change: %.2f", sale.receiptNo, sale.change), false);
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

    private void useSuggestion() {
        Store.Suggestion s = suggestList.getSelectedValue();
        if (s == null) return;
        suppressPredict = true;
        itemField.setText(s.item.getCode());
        suppressPredict = false;
        hidePredictions();
        itemField.requestFocusInWindow();
        setStatus("Code for " + s.item.getName() + " filled in. Press Enter to add it.", false);
    }

    private void setupPredictiveSearch() {
        predictList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        predictList.setFocusable(false);
        predictList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, selected, false);
                Item i = (Item) value;
                String status = Store.statusOf(i);
                String stock;
                if (status.equals("OK"))             stock = i.getQuantity() + " in stock";
                else if (status.equals("LOW STOCK")) stock = i.getQuantity() + " left (low)";
                else                                 stock = status;
                l.setText(String.format("%s   [%s]   %.2f   -   %s", i.getName(), i.getCode(), i.getPrice(), stock));
                l.setBorder(new EmptyBorder(7, 12, 7, 12));
                boolean cannotSell = status.equals("OUT OF STOCK") || status.equals("EXPIRED");
                l.setForeground(cannotSell ? DANGER : TEXT);
                l.setBackground(selected ? SEL_BG : (index % 2 == 0 ? CARD : STRIPE));
                l.setOpaque(true);
                return l;
            }
        });
        predictList.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int i = rowAt(e.getPoint());
                if (i >= 0) predictList.setSelectedIndex(i);
            }
        });
        predictList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int i = rowAt(e.getPoint());
                if (i < 0) return;
                predictList.setSelectedIndex(i);
                acceptPrediction();
            }
        });

        predictPopup.setFocusable(false);
        predictPopup.setBorder(new LineBorder(LINE_DARK, 1));
        predictPopup.add(predictList);

        itemField.getDocument().addDocumentListener(new DocumentListener() {
            private void typed() {
                if (suppressPredict) return;
                SwingUtilities.invokeLater(() -> updatePredictions());
            }
            public void insertUpdate(DocumentEvent e)  { typed(); }
            public void removeUpdate(DocumentEvent e)  { typed(); }
            public void changedUpdate(DocumentEvent e) { typed(); }
        });
        itemField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                hidePredictions();
            }
        });
        itemField.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !itemField.isShowing()) {
                hidePredictions();
            }
        });

        InputMap im = itemField.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = itemField.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "predictDown");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "predictUp");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "predictClose");
        am.put("predictDown", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { movePredictionSelection(1); }
        });
        am.put("predictUp", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { movePredictionSelection(-1); }
        });
        am.put("predictClose", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { hidePredictions(); }
        });
    }

    private int rowAt(Point p) {
        int i = predictList.locationToIndex(p);
        if (i < 0) return -1;
        Rectangle r = predictList.getCellBounds(i, i);
        return (r != null && r.contains(p)) ? i : -1;
    }

    private void updatePredictions() {
        String q = itemField.getText().trim().toLowerCase();
        if (q.isEmpty() || !itemField.isShowing() || !itemField.hasFocus()) {
            hidePredictions();
            return;
        }

        List<Item> startsWith = new ArrayList<>();
        List<Item> contains = new ArrayList<>();
        for (Item i : store.allItems()) {
            String name = i.getName().toLowerCase();
            String code = i.getCode().toLowerCase();
            if (name.startsWith(q) || code.startsWith(q))      startsWith.add(i);
            else if (name.contains(q) || code.contains(q))     contains.add(i);
        }
        startsWith.addAll(contains);
        if (startsWith.isEmpty()) {
            hidePredictions();
            return;
        }

        predictModel.clear();
        for (int n = 0; n < startsWith.size() && n < MAX_PREDICTIONS; n++) predictModel.addElement(startsWith.get(n));
        predictList.clearSelection();

        predictList.setFont(itemField.getFont());
        predictList.setPreferredSize(null);
        Dimension pref = predictList.getPreferredSize();
        predictList.setPreferredSize(new Dimension(Math.max(pref.width, itemField.getWidth()), pref.height));

        predictList.setBackground(CARD);
        predictList.setForeground(TEXT);
        predictPopup.setBackground(CARD);
        predictPopup.setBorder(new LineBorder(LINE_DARK, 1));

        if (predictPopup.isVisible()) predictPopup.pack();
        else predictPopup.show(itemField, 0, itemField.getHeight() + 2);
    }

    private void hidePredictions() {
        if (predictPopup.isVisible()) predictPopup.setVisible(false);
        predictList.clearSelection();
    }

    private void movePredictionSelection(int delta) {
        if (!predictPopup.isVisible()) {
            if (delta > 0) updatePredictions();
            return;
        }
        int i = predictList.getSelectedIndex() + delta;
        i = Math.max(-1, Math.min(predictModel.size() - 1, i));
        if (i < 0) {
            predictList.clearSelection();
        } else {
            predictList.setSelectedIndex(i);
            predictList.ensureIndexIsVisible(i);
        }
    }

    private boolean acceptPrediction() {
        if (!predictPopup.isVisible()) return false;
        Item item = predictList.getSelectedValue();
        if (item == null) return false;

        suppressPredict = true;
        itemField.setText(item.getCode());
        suppressPredict = false;
        hidePredictions();
        itemField.requestFocusInWindow();
        itemField.setCaretPosition(itemField.getText().length());
        setStatus("Selected " + item.getName() + " [" + item.getCode() + "]. Press Enter to add it to the basket.", false);
        return true;
    }

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
            changeLabel.setText(String.format("Not enough: short by %.2f", Cart.round2(cart.total() - paid)));
        }
    }

    private JPanel buildInventoryTab() {
        JPanel p = page();

        JPanel top = panel(new WrapLayout(FlowLayout.LEFT, 8, 4));
        top.add(new JLabel("Search:"));
        top.add(searchField);
        JButton clearBtn = new PillButton("Clear", PillButton.Kind.NEUTRAL);
        clearBtn.setToolTipText("Clear the search box and show every product.");
        clearBtn.addActionListener(e -> {
            searchField.setText("");
            searchField.requestFocusInWindow();
        });
        top.add(clearBtn);
        top.add(new JLabel("Sort by:"));
        top.add(sortKeyBox);
        top.add(new JLabel("Sorting method:"));
        top.add(sortAlgoBox);
        top.add(inventoryCountLabel);
        top.add(sortInfoLabel);
        inventoryCountLabel.setForeground(MUTED);
        sortInfoLabel.setForeground(MUTED);
        searchField.setToolTipText("Type part of a product's code or name to filter the list.");
        sortKeyBox.setToolTipText("Choose what to sort the products by.");
        sortAlgoBox.setToolTipText("Choose which sorting method to use (the result looks the same; the counts differ).");
        sortKeyBox.addActionListener(e -> refreshInventory());
        sortAlgoBox.addActionListener(e -> refreshInventory());
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { refreshInventory(); }
            public void removeUpdate(DocumentEvent e)  { refreshInventory(); }
            public void changedUpdate(DocumentEvent e) { refreshInventory(); }
        });

        JPanel searchCard = card(new BorderLayout());
        searchCard.add(top, BorderLayout.CENTER);

        JPanel north = panel(new BorderLayout(0, 8));
        north.add(hint("Click a product in the list, then choose a button at the bottom.   "
                + "Pink rows = out of stock or expired.   Orange rows = running low.   "
                + "The Status column always spells it out. You can also click a column heading to sort."),
                BorderLayout.NORTH);
        north.add(searchCard, BorderLayout.CENTER);
        p.add(north, BorderLayout.NORTH);

        invTable.setAutoCreateRowSorter(true);
        invTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        configureTable(invTable);
        StatusRenderer renderer = new StatusRenderer();
        invTable.setDefaultRenderer(Object.class, renderer);
        invTable.setDefaultRenderer(Integer.class, renderer);
        invTable.setDefaultRenderer(Double.class, renderer);
        p.add(tableCard(invTable, null), BorderLayout.CENTER);

        JButton add = new PillButton("Add New Product", PillButton.Kind.PRIMARY);
        JButton receive = new PillButton("Receive Delivery", PillButton.Kind.NEUTRAL);
        JButton price = new PillButton("Change Price", PillButton.Kind.NEUTRAL);
        JButton writeOff = new PillButton("Remove Damaged / Expired Stock", PillButton.Kind.NEUTRAL);
        JButton delete = new PillButton("Delete Product", PillButton.Kind.DANGER);
        JButton undo = new PillButton("Undo Last Change", PillButton.Kind.NEUTRAL);
        undo.setToolTipText("Undoes the last delivery, stock removal, or price change. Does not undo product deletions or sales.");
        add.addActionListener(e -> addProductDialog());
        receive.addActionListener(e -> receiveDialog());
        price.addActionListener(e -> priceDialog());
        writeOff.addActionListener(e -> writeOffDialog());
        delete.addActionListener(e -> deleteDialog());
        undo.addActionListener(e -> undoInventoryChange());
        add.setToolTipText("Create a new product with its starting stock and expiry date.");
        receive.setToolTipText("Select a product first. Adds delivered units to its stock.");
        price.setToolTipText("Select a product first. Changes its selling price.");
        writeOff.setToolTipText("Select a product first. Takes damaged, expired, or missing units out of stock.");
        delete.setToolTipText("Select a product first. Permanently removes it from the store.");

        JPanel buttons = panel(new WrapLayout(FlowLayout.LEFT, 8, 4));
        buttons.add(add);
        buttons.add(receive);
        buttons.add(price);
        buttons.add(writeOff);
        buttons.add(undo);
        buttons.add(delete);
        JPanel buttonCard = card(new BorderLayout());
        buttonCard.add(buttons, BorderLayout.CENTER);
        p.add(buttonCard, BorderLayout.SOUTH);
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
        inventoryCountLabel.setText(invModel.getRowCount() + " product(s) shown");
    }

    private Item[] sortedForDisplay() {
        Item[] items = store.allItems().toArray(new Item[0]);
        int key = sortKeyBox.getSelectedIndex();
        if (key <= 0) {
            sortInfoLabel.setText(" ");
            return items;
        }
        invTable.getRowSorter().setSortKeys(null);
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

    private Item selectedItem() {
        int row = invTable.getSelectedRow();
        if (row < 0) {
            setStatus("Click a product in the list first, then press the button again.", true);
            return null;
        }
        String code = (String) invModel.getValueAt(invTable.convertRowIndexToModel(row), 0);
        return store.get(code);
    }

    private void addProductDialog() {
        JTextField name = new JTextField(18);
        JComboBox<String> section = new JComboBox<>(Store.SECTIONS);
        JTextField qty = new JTextField("0");
        JTextField reorder = new JTextField("0");
        JTextField expiry = new JTextField();
        JTextField price = new JTextField();
        expiry.setToolTipText("Type the year, month and day, like 2026-12-31.");
        price.setToolTipText("Type the price as a number, like 45.50.");

        JPanel form = new JPanel(new GridLayout(0, 2, 10, 10));
        form.add(new JLabel("Product name:"));                                   form.add(name);
        form.add(new JLabel("Section:"));                                        form.add(section);
        form.add(new JLabel("Starting quantity:"));                              form.add(qty);
        form.add(new JLabel("Warn me when stock is this low or less:"));         form.add(reorder);
        form.add(new JLabel("Expiry date (YYYY-MM-DD, e.g. 2026-12-31):"));      form.add(expiry);
        form.add(new JLabel("Price (e.g. 45.50):"));                             form.add(price);
        applyControlSizing(form);

        while (true) {
            int r = JOptionPane.showConfirmDialog(this, form, "Add new product",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return;
            try {
                Item item = store.addProduct(name.getText().trim(), section.getSelectedIndex(),
                        parseInt(qty.getText(), "Starting quantity"),
                        parseInt(reorder.getText(), "Low-stock warning level"),
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
                    + ".\nRemove it first (\"Remove Damaged / Expired Stock\"), then receive the delivery.");
            return;
        }

        boolean needsDate = item.getQuantity() == 0;
        JTextField qty = new JTextField("1");
        JTextField expiry = new JTextField();
        JPanel form = new JPanel(new GridLayout(0, 2, 10, 10));
        form.add(new JLabel("Current stock of " + item.getName() + ":"));
        form.add(new JLabel(String.valueOf(item.getQuantity())));
        form.add(new JLabel("How many units arrived:"));
        form.add(qty);
        if (needsDate) {
            form.add(new JLabel("Expiry date of this delivery (YYYY-MM-DD):"));
            form.add(expiry);
        }
        applyControlSizing(form);

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
                    String.format("New price for %s (current price %.2f):", item.getName(), item.getPrice()),
                    "Change price", JOptionPane.QUESTION_MESSAGE);
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
            showError(item.getName() + " has no stock to remove.");
            return;
        }
        while (true) {
            String s = JOptionPane.showInputDialog(this,
                    "How many " + item.getName() + " are damaged, expired or lost?\nCurrent stock: "
                            + item.getQuantity(), "Remove damaged / expired stock", JOptionPane.QUESTION_MESSAGE);
            if (s == null) return;
            try {
                int amount = parseInt(s, "Amount");
                store.writeOff(item, amount);
                refreshAll();
                setStatus("Removed " + amount + ". New stock of " + item.getName() + ": " + item.getQuantity(), false);
                return;
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
            }
        }
    }

    private void deleteDialog() {
        Item item = selectedItem();
        if (item == null) return;
        if (!confirm("Delete " + item.getName() + " [" + item.getCode() + "] from the store permanently?\n"
                        + "This cannot be undone.",
                "Delete product", "Yes, delete it", "No, keep it", JOptionPane.WARNING_MESSAGE)) return;
        store.deleteItem(item);
        refreshAll();
        setStatus(item.getName() + " was deleted.", false);
    }

    private JPanel buildReportsTab() {
        JPanel p = page();

        salesLabel.setFont(salesLabel.getFont().deriveFont(Font.BOLD, fontSize * 1.15f));
        salesLabel.setForeground(ACCENT_TEXT);
        JPanel summaryCard = card(new BorderLayout());
        summaryCard.add(salesLabel, BorderLayout.CENTER);
        p.add(summaryCard, BorderLayout.NORTH);

        JPanel groupControls = panel(new WrapLayout(FlowLayout.LEFT, 8, 4));
        groupControls.add(new JLabel("Show products that were bought together at least"));
        groupControls.add(strengthSpinner);
        groupControls.add(new JLabel("time(s)"));
        JButton demoBtn = new PillButton("Add Demo Purchases", PillButton.Kind.NEUTRAL);
        demoBtn.setToolTipText("Adds made-up purchases so you can see how groups form. Not saved and not counted as sales.");
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

        JTable lowTable = new JTable(lowModel);
        JTable expTable = new JTable(expModel);
        JTable salesLogTable = new JTable(salesLogModel);
        JTable groupTable = new JTable(groupModel);
        configureTable(lowTable);
        configureTable(expTable);
        configureTable(salesLogTable);
        configureTable(groupTable);
        JScrollPane lowPane = tableCard(lowTable, "Products at or below their low-stock alert level");
        JScrollPane expPane = tableCard(expTable,
                "Expiring within " + Store.EXPIRY_ALERT_DAYS + " days (or already expired)");
        JScrollPane salesLogPane = tableCard(salesLogTable, "Completed sales in the chosen dates");
        JScrollPane groupPane = tableCard(groupTable, "Products usually bought together");

        JButton salesSearchBtn = new PillButton("Show Sales", PillButton.Kind.PRIMARY);
        JButton todayBtn = new PillButton("Today", PillButton.Kind.NEUTRAL);
        JButton monthBtn = new PillButton("This Month", PillButton.Kind.NEUTRAL);
        todayBtn.setToolTipText("Show only today's sales.");
        monthBtn.setToolTipText("Show sales from the 1st of this month until today.");
        salesSearchBtn.addActionListener(e -> refreshSalesLogReport());
        todayBtn.addActionListener(e -> {
            salesStartField.setText(LocalDate.now().toString());
            salesEndField.setText(LocalDate.now().toString());
            refreshSalesLogReport();
        });
        monthBtn.addActionListener(e -> {
            salesStartField.setText(LocalDate.now().withDayOfMonth(1).toString());
            salesEndField.setText(LocalDate.now().toString());
            refreshSalesLogReport();
        });
        salesStartField.addActionListener(e -> refreshSalesLogReport());
        salesEndField.addActionListener(e -> refreshSalesLogReport());
        salesStartField.setToolTipText("Year-month-day, like 2026-10-01.");
        salesEndField.setToolTipText("Year-month-day, like 2026-10-31.");
        salesLogStatus.setForeground(MUTED);
        JPanel salesControls = panel(new WrapLayout(FlowLayout.LEFT, 8, 4));
        salesControls.add(new JLabel("From (YYYY-MM-DD):"));
        salesControls.add(salesStartField);
        salesControls.add(new JLabel("To (YYYY-MM-DD):"));
        salesControls.add(salesEndField);
        salesControls.add(salesSearchBtn);
        salesControls.add(todayBtn);
        salesControls.add(monthBtn);
        salesControls.add(salesLogStatus);
        JPanel salesControlsCard = card(new BorderLayout());
        salesControlsCard.add(salesControls, BorderLayout.CENTER);
        JPanel salesReport = page();
        salesReport.add(salesControlsCard, BorderLayout.NORTH);
        salesReport.add(salesLogPane, BorderLayout.CENTER);

        JPanel groupControlsCard = card(new BorderLayout());
        groupControlsCard.add(groupControls, BorderLayout.CENTER);
        JPanel groupsReport = page();
        groupsReport.add(groupControlsCard, BorderLayout.NORTH);
        groupsReport.add(groupPane, BorderLayout.CENTER);

        JPanel lowPage = page();
        lowPage.add(lowPane, BorderLayout.CENTER);
        JPanel expPage = page();
        expPage.add(expPane, BorderLayout.CENTER);

        JTabbedPane reportTabs = new JTabbedPane();
        reportTabs.setUI(new ModernTabUI());
        reportTabs.setOpaque(true);
        reportTabs.setBackground(CARD);
        reportTabs.addTab("Running Low", lowPage);
        reportTabs.addTab("Expiring Soon", expPage);
        reportTabs.addTab("Past Sales", salesReport);
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
                salesLogStatus.setText("The \"To\" date must be on or after the \"From\" date.");
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

    private JPanel buildPriorityTab() {
        JPanel p = page();

        p.add(hint("These products need attention, most urgent first. Products that are running out, "
                + "or close to their expiry date, appear at the top.\n\n"
                + "How the score is worked out: Score = 0.6 x stock shortage + 0.4 x expiry risk, each from 0 to 1 "
                + "(taken out of a custom max-heap). Stock shortage = how far sellable stock is below the low-stock "
                + "alert level (expired stock counts as 0). Expiry risk = rises from 0 to 1 over the last "
                + Store.EXPIRY_ALERT_DAYS + " days before expiry, and is 1 if already expired. "
                + "Stock has the bigger weight because a missing product loses sales right now, "
                + "while expiring stock can still be discounted."), BorderLayout.NORTH);

        JTable table = new JTable(priorityModel);
        configureTable(table);
        table.getColumnModel().getColumn(3).setPreferredWidth(260);
        p.add(tableCard(table, null), BorderLayout.CENTER);
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

    private JPanel buildBenchmarkTab() {
        JPanel p = page();

        JPanel top = panel(new WrapLayout(FlowLayout.LEFT, 8, 4));
        top.add(benchRunBtn);
        top.add(benchExportBtn);
        benchStatus.setForeground(MUTED);
        top.add(benchStatus);
        benchExportBtn.setEnabled(false);
        benchRunBtn.setToolTipText("Time the sorting and searching methods on test data. Your products are not touched.");
        benchExportBtn.setToolTipText("Save the results to a file called benchmark_results.csv (opens in Excel).");
        benchRunBtn.addActionListener(e -> runBenchmark());
        benchExportBtn.addActionListener(e -> exportBenchmark());
        JPanel topCard = card(new BorderLayout());
        topCard.add(top, BorderLayout.CENTER);
        p.add(topCard, BorderLayout.NORTH);

        JTable table = new JTable(benchModel);
        configureTable(table);
        table.setRowSorter(null);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {110, 300, 250, 110, 220, 460};
        for (int c = 0; c < widths.length; c++) table.getColumnModel().getColumn(c).setPreferredWidth(widths[c]);
        p.add(tableCard(table, null), BorderLayout.CENTER);

        p.add(hint("This test uses its own temporary test items (100, 500, 1,000 and 5,000), so your inventory is never touched. "
                + "Time is the median of several runs; search and delete rows are averages per operation. "
                + "Comparisons / movements are counted inside the algorithms, so they are exactly repeatable "
                + "(times vary a little from run to run)."), BorderLayout.SOUTH);
        return p;
    }

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
                    benchStatus.setText("The speed test failed: " + ex);
                }
                benchRunBtn.setEnabled(true);
            }
        }.execute();
    }

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

    private JPanel buildTestCasesTab() {
        JPanel p = page();
        JPanel top = panel(new WrapLayout(FlowLayout.LEFT, 8, 4));
        top.add(testCasesRunBtn);
        testCasesStatus.setForeground(MUTED);
        top.add(testCasesStatus);
        testCasesRunBtn.setToolTipText("Run ten checks on temporary data. Your inventory is not changed.");
        testCasesRunBtn.addActionListener(e -> runTestCases());
        JPanel topCard = card(new BorderLayout());
        topCard.add(top, BorderLayout.CENTER);
        p.add(topCard, BorderLayout.NORTH);

        JTable table = new JTable(testCaseModel);
        configureTable(table);
        table.setRowSorter(null);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {70, 230, 90, 700};
        for (int c = 0; c < widths.length; c++) table.getColumnModel().getColumn(c).setPreferredWidth(widths[c]);
        p.add(tableCard(table, null), BorderLayout.CENTER);
        p.add(hint("These checks use temporary in-memory data and do not change your inventory. "
                + "The cases cover empty and single-record structures, duplicate keys, searching, "
                + "hash collisions, graph traversal, BST deletion, and heap extraction."), BorderLayout.SOUTH);
        return p;
    }

    private void runTestCases() {
        testCasesRunBtn.setEnabled(false);
        testCaseModel.setRowCount(0);
        testCasesStatus.setText("Running test cases...");
        new SwingWorker<List<TestCaseRunner.Result>, Void>() {
            @Override
            protected List<TestCaseRunner.Result> doInBackground() {
                return TestCaseRunner.run();
            }

            @Override
            protected void done() {
                try {
                    List<TestCaseRunner.Result> results = get();
                    int passed = 0;
                    for (TestCaseRunner.Result result : results) {
                        if (result.passed) passed++;
                        testCaseModel.addRow(new Object[]{result.id, result.name,
                                result.passed ? "PASS" : "FAIL", result.details});
                    }
                    testCasesStatus.setText(passed + " of " + results.size() + " test cases passed.");
                } catch (Exception ex) {
                    testCasesStatus.setText("The test cases could not run: " + ex.getMessage());
                }
                testCasesRunBtn.setEnabled(true);
            }
        }.execute();
    }

    private void refreshAll() {
        refreshCart();
        refreshInventory();
        refreshReports();
        refreshPriority();
    }

    private static Border statusBorder(Color accent) {
        return new CompoundBorder(new MatteBorder(0, 6, 0, 0, accent), new EmptyBorder(9, 14, 9, 14));
    }

    private void setStatus(String msg, boolean error) {
        statusLabel.setText(" " + (error ? "Please note:  " : "") + msg);
        statusLabel.setForeground(error ? RED : TEXT);
        statusLabel.setBackground(error ? BAD_BG : GOOD_BG);
        statusLabel.setBorder(statusBorder(error ? RED : GREEN));
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Please check", JOptionPane.ERROR_MESSAGE);
    }

    private boolean confirm(String message, String title, String yes, String no, int messageType) {
        Object[] options = {yes, no};
        int r = JOptionPane.showOptionDialog(this, message, title, JOptionPane.DEFAULT_OPTION,
                messageType, null, options, no);
        return r == 0;
    }

    private static Color contrastText(Color bg) {
        double luminance = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) / 255.0;
        return luminance > 0.62 ? Color.BLACK : Color.WHITE;
    }

    private void changeTextSize(int newSize) {
        newSize = Math.max(MIN_FONT, Math.min(MAX_FONT, newSize));
        if (newSize == fontSize) {
            setStatus(newSize == MAX_FONT ? "The text is already as big as it goes."
                    : "The text is already as small as it goes.", false);
            return;
        }
        float ratio = newSize / (float) fontSize;
        fontSize = newSize;
        scaleDefaults(ratio);
        scaleTree(getContentPane(), ratio);
        revalidate();
        repaint();
    }

    private void applyTheme(boolean dark) {
        darkMode = dark;
        if (dark) {
            BG = new Color(15, 21, 29);
            CARD = new Color(28, 38, 48);
            LINE = new Color(59, 74, 88);
            LINE_DARK = new Color(78, 94, 110);
            TEXT = new Color(235, 240, 244);
            MUTED = new Color(170, 184, 195);
            PRIMARY = new Color(82, 165, 125);
            PRIMARY_DARK = new Color(55, 120, 93);
            PRIMARY_LIGHT = new Color(172, 222, 196);
            DANGER = new Color(215, 103, 89);
            STRIPE = new Color(22, 35, 45);
            SEL_BG = new Color(46, 76, 67);
            TABLE_HEAD = new Color(37, 50, 62);
            GOOD_BG = new Color(27, 59, 46);
            BAD_BG = new Color(66, 32, 31);
            HINT_BG = new Color(48, 42, 32);
            HINT_ACCENT = new Color(224, 173, 74);
            ACCENT_TEXT = new Color(150, 214, 182);
            ALERT_BG = new Color(104, 44, 48);
            WARN_BG = new Color(98, 74, 28);
            NEUTRAL_FILL = new Color(44, 58, 71);
            NEUTRAL_HOVER = new Color(60, 78, 94);
            DISABLED_FILL = new Color(36, 46, 56);
            DISABLED_TEXT = new Color(140, 155, 166);
            RECEIPT_BG = CARD;
        } else {
            BG = new Color(243, 247, 245);
            CARD = Color.WHITE;
            LINE = new Color(214, 222, 218);
            LINE_DARK = new Color(178, 192, 186);
            TEXT = new Color(30, 41, 38);
            MUTED = new Color(92, 108, 101);
            PRIMARY = new Color(31, 122, 90);
            PRIMARY_DARK = new Color(18, 84, 62);
            PRIMARY_LIGHT = new Color(214, 236, 226);
            DANGER = new Color(188, 48, 58);
            STRIPE = new Color(246, 250, 248);
            SEL_BG = new Color(200, 230, 216);
            TABLE_HEAD = new Color(228, 240, 234);
            GOOD_BG = new Color(224, 244, 233);
            BAD_BG = new Color(255, 230, 230);
            HINT_BG = new Color(255, 249, 230);
            HINT_ACCENT = new Color(231, 170, 40);
            ACCENT_TEXT = new Color(18, 84, 62);
            ALERT_BG = new Color(255, 222, 222);
            WARN_BG = new Color(255, 239, 205);
            NEUTRAL_FILL = new Color(238, 244, 241);
            NEUTRAL_HOVER = new Color(220, 236, 228);
            DISABLED_FILL = new Color(228, 232, 230);
            DISABLED_TEXT = new Color(110, 122, 117);
            RECEIPT_BG = new Color(255, 253, 246);
        }
        GREEN = PRIMARY;
        RED = DANGER;

        getContentPane().setBackground(BG);
        if (tabs != null) {
            tabs.setBackground(CARD);
            tabs.setForeground(TEXT);
        }
        if (statusLabel != null) {
            statusLabel.setBackground(GOOD_BG);
            statusLabel.setForeground(contrastText(GOOD_BG));
            statusLabel.setBorder(statusBorder(GREEN));
        }
        applyThemeToTree(getContentPane());

        if (headerTitle != null) {
            headerTitle.setForeground(Color.WHITE);
            headerTitle.setFont(headerTitle.getFont().deriveFont(Font.BOLD, fontSize * 1.4f));
        }
        if (headerTip != null) {
            headerTip.setForeground(HEADER_TIP);
        }
        if (totalLabel != null) totalLabel.setForeground(ACCENT_TEXT);
        if (salesLabel != null) salesLabel.setForeground(ACCENT_TEXT);
        if (inventoryCountLabel != null) inventoryCountLabel.setForeground(MUTED);
        if (sortInfoLabel != null) sortInfoLabel.setForeground(MUTED);
        if (salesLogStatus != null) salesLogStatus.setForeground(MUTED);
        if (benchStatus != null) benchStatus.setForeground(MUTED);
        revalidate();
        repaint();
    }

    private void applyThemeToTree(Component component) {
        if (component instanceof JComponent) {
            JComponent j = (JComponent) component;
            if (component instanceof JPanel) {
                j.setBackground(BG);
            }
            if (component instanceof JButton) {
                j.setForeground(TEXT);
            } else if (component instanceof JLabel) {
                j.setForeground(TEXT);
            } else if (component instanceof JTextArea || component instanceof JTextField || component instanceof JComboBox
                    || component instanceof JSpinner || component instanceof JTable || component instanceof JList) {
                j.setBackground(CARD);
                j.setForeground(TEXT);
                if ("hint".equals(j.getName())) j.setBackground(HINT_BG);
            }
            if (component instanceof JTextComponent) {
                ((JTextComponent) component).setCaretColor(TEXT);
                ((JTextComponent) component).setDisabledTextColor(MUTED);
            }
            if (j.getBorder() instanceof TitledBorder) {
                TitledBorder tb = (TitledBorder) j.getBorder();
                tb.setTitleColor(ACCENT_TEXT);
                tb.setBorder(new LineBorder(LINE, 1, true));
            }
            if (component instanceof JScrollPane) {
                JScrollPane pane = (JScrollPane) component;
                pane.getViewport().setBackground(CARD);
            }
            if (component instanceof JTable) {
                JTable table = (JTable) component;
                table.setBackground(CARD);
                table.setForeground(TEXT);
                table.setSelectionBackground(SEL_BG);
                table.setSelectionForeground(contrastText(SEL_BG));
                table.setGridColor(LINE);
                if (table.getTableHeader() != null) {
                    table.getTableHeader().setBackground(TABLE_HEAD);
                    table.getTableHeader().setForeground(contrastText(TABLE_HEAD));
                }
            }
            if (component instanceof JTabbedPane) {
                JTabbedPane pane = (JTabbedPane) component;
                pane.setBackground(CARD);
                pane.setForeground(TEXT);
            }
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                applyThemeToTree(child);
            }
        }
    }

    private void installTextSizeShortcuts() {
        JRootPane root = getRootPane();
        InputMap in = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap act = root.getActionMap();
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        Action bigger = new AbstractAction() {
            public void actionPerformed(ActionEvent e) { changeTextSize(fontSize + FONT_STEP); }
        };
        Action smaller = new AbstractAction() {
            public void actionPerformed(ActionEvent e) { changeTextSize(fontSize - FONT_STEP); }
        };
        in.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, mask), "textBigger");
        in.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, mask), "textBigger");
        in.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, mask), "textBigger");
        in.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, mask), "textSmaller");
        in.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, mask), "textSmaller");
        act.put("textBigger", bigger);
        act.put("textSmaller", smaller);
    }

    private static void scaleDefaults(float ratio) {
        for (Object key : Collections.list(UIManager.getDefaults().keys())) {
            Object v = UIManager.get(key);
            if (v instanceof Font) {
                Font f = (Font) v;
                UIManager.put(key, new FontUIResource(f.deriveFont(f.getSize2D() * ratio)));
            }
        }
    }

    private static void applyFontFamily(String family) {
        for (Object key : Collections.list(UIManager.getDefaults().keys())) {
            Object v = UIManager.get(key);
            if (v instanceof Font) {
                Font f = (Font) v;
                UIManager.put(key, new FontUIResource(new Font(family, f.getStyle(), f.getSize())));
            }
        }
    }

    private static void scaleTree(Component c, float ratio) {
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) scaleTree(child, ratio);
        }
        Font f = c.getFont();
        if (f != null) c.setFont(f.deriveFont(f.getSize2D() * ratio));
        if (c instanceof JComponent) {
            Border b = ((JComponent) c).getBorder();
            if (b instanceof TitledBorder) {
                TitledBorder tb = (TitledBorder) b;
                Font bf = tb.getTitleFont();
                if (bf != null) tb.setTitleFont(bf.deriveFont(bf.getSize2D() * ratio));
            }
        }
        if (c instanceof JTable) {
            JTable t = (JTable) c;
            t.setRowHeight(t.getFontMetrics(t.getFont()).getHeight() + 14);
        }
    }

    private static JPanel panel(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setOpaque(false);
        return p;
    }

    private static JPanel page() {
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(14, 16, 14, 16));
        return p;
    }

    private static JPanel card(LayoutManager layout) {
        JPanel p = new JPanel(layout) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.setColor(LINE);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(10, 14, 10, 14));
        return p;
    }

    private static Border titled(String title) {
        Font f = UIManager.getFont("Label.font");
        f = f == null ? new Font(Font.SANS_SERIF, Font.PLAIN, DEFAULT_FONT) : f;
        return BorderFactory.createTitledBorder(new LineBorder(LINE, 1, true), " " + title + " ",
                TitledBorder.LEFT, TitledBorder.TOP, f.deriveFont(Font.BOLD), ACCENT_TEXT);
    }

    private static JScrollPane tableCard(JTable table, String title) {
        JScrollPane pane = new JScrollPane(table);
        pane.getViewport().setBackground(CARD);
        pane.setBorder(title == null ? new LineBorder(LINE, 1, true) : titled(title));
        return pane;
    }

    private static JTextArea hint(String text) {
        JTextArea a = new JTextArea(text);
        a.setEditable(false);
        a.setFocusable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setFont(UIManager.getFont("Label.font"));
        a.setForeground(TEXT);
        a.setBackground(HINT_BG);
        a.setName("hint");
        a.setCaretColor(TEXT);
        a.setBorder(new CompoundBorder(new MatteBorder(0, 5, 0, 0, HINT_ACCENT), new EmptyBorder(10, 14, 10, 14)));
        return a;
    }

    private static void configureTable(JTable table) {
        table.setRowHeight(table.getFontMetrics(table.getFont()).getHeight() + 14);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowSelectionAllowed(true);
        table.setColumnSelectionAllowed(false);
        table.setShowGrid(false);
        table.setShowHorizontalLines(true);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setGridColor(LINE);
        table.setSelectionBackground(SEL_BG);
        table.setSelectionForeground(contrastText(SEL_BG));
        table.setForeground(TEXT);
        table.setBackground(CARD);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);

        ZebraRenderer zebra = new ZebraRenderer();
        table.setDefaultRenderer(Object.class, zebra);
        table.setDefaultRenderer(Integer.class, zebra);
        table.setDefaultRenderer(Double.class, zebra);

        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setFont(header.getFont().deriveFont(Font.BOLD));
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, value, false, false, row, col);
                String text = String.valueOf(value);
                RowSorter<?> rs = t.getRowSorter();
                if (rs != null) {
                    for (RowSorter.SortKey k : rs.getSortKeys()) {
                        if (k.getColumn() == t.convertColumnIndexToModel(col)
                                && k.getSortOrder() != SortOrder.UNSORTED) {
                            text += k.getSortOrder() == SortOrder.ASCENDING ? "  \u25B2" : "  \u25BC";
                            break;
                        }
                    }
                }
                l.setText(text);
                l.setOpaque(true);
                l.setBackground(TABLE_HEAD);
                l.setForeground(TEXT);
                l.setFont(t.getTableHeader().getFont());
                l.setHorizontalAlignment(LEFT);
                l.setBorder(new CompoundBorder(new MatteBorder(0, 0, 2, 0, PRIMARY), new EmptyBorder(8, 8, 8, 8)));
                return l;
            }
        });
    }

    private static void applyControlSizing(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof AbstractButton) {
                AbstractButton button = (AbstractButton) component;
                if (!(button instanceof PillButton)) {
                    Insets margin = button.getMargin();
                    button.setMargin(new Insets(margin.top + 5, margin.left + 10,
                            margin.bottom + 5, margin.right + 10));
                }
                button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
            if (component instanceof JTextField && !(component.getParent() instanceof JSpinner.DefaultEditor)) {
                ((JTextField) component).setBorder(FIELD_BORDER);
            }
            if (component instanceof Container) {
                applyControlSizing((Container) component);
            }
        }
    }

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
            throw new IllegalArgumentException(what + " must be a whole number (like 12).");
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

    private static class ZebraRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, selected, false, row, col);
            if (value instanceof Double) setText(String.format("%.2f", (Double) value));
            setHorizontalAlignment(value instanceof Number ? RIGHT : LEFT);
            setBorder(new EmptyBorder(0, 8, 0, 8));
            c.setForeground(TEXT);
            c.setBackground(selected ? SEL_BG : (row % 2 == 0 ? CARD : STRIPE));
            return c;
        }
    }

    private static class StatusRenderer extends ZebraRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, selected, focus, row, col);
            if (!selected) {
                String status = String.valueOf(table.getModel().getValueAt(table.convertRowIndexToModel(row), 7));
                if (status.equals("OUT OF STOCK") || status.equals("EXPIRED")) c.setBackground(ALERT_BG);
                else if (status.equals("LOW STOCK"))                          c.setBackground(WARN_BG);
            }
            return c;
        }
    }

    private static class PillButton extends JButton {
        enum Kind { PRIMARY, DANGER, NEUTRAL }

        private final Kind kind;
        private boolean hover;

        PillButton(String text, Kind kind) {
            super(text);
            this.kind = kind;
            setUI(new BasicButtonUI());
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setBorder(new EmptyBorder(9, 20, 9, 20));
            setFont(getFont().deriveFont(Font.BOLD));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Color fill, textColor;
            if (!isEnabled()) {
                fill = DISABLED_FILL;
                textColor = DISABLED_TEXT;
            } else {
                switch (kind) {
                    case PRIMARY: fill = PRIMARY; break;
                    case DANGER:  fill = DANGER; break;
                    default:      fill = NEUTRAL_FILL; break;
                }
                if (getModel().isPressed()) fill = fill.darker();
                else if (hover) fill = kind == Kind.NEUTRAL ? NEUTRAL_HOVER : fill.brighter();
                textColor = contrastText(fill);
            }

            int w = getWidth(), h = getHeight();
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 14, 14);
            if (kind == Kind.NEUTRAL || !isEnabled()) {
                g2.setColor(LINE_DARK);
                g2.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);
            }
            if (hasFocus() && isFocusable()) {
                g2.setColor(new Color(231, 170, 40));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(2, 2, w - 5, h - 5, 12, 12);
            }

            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            String t = getText();
            int tx = (w - fm.stringWidth(t)) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.setColor(textColor);
            g2.drawString(t, tx, ty);
            g2.dispose();
        }
    }

    private static class ModernTabUI extends BasicTabbedPaneUI {
        @Override
        protected void installDefaults() {
            super.installDefaults();
            tabAreaInsets = new Insets(6, 10, 0, 10);
            tabInsets = new Insets(9, 20, 9, 20);
            selectedTabPadInsets = new Insets(0, 0, 0, 0);
            contentBorderInsets = new Insets(1, 0, 0, 0);
        }

        @Override
        protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex,
                                          int x, int y, int w, int h, boolean isSelected) {
            g.setColor(CARD);
            g.fillRect(x, y, w, h);
            if (isSelected) {
                g.setColor(PRIMARY);
                g.fillRect(x, y + h - 3, w, 3);
            }
        }

        @Override
        protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex,
                                      int x, int y, int w, int h, boolean isSelected) { }

        @Override
        protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects, int tabIndex,
                                           Rectangle iconRect, Rectangle textRect, boolean isSelected) { }

        @Override
        protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics, int tabIndex,
                                 String title, Rectangle textRect, boolean isSelected) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(font);
            g2.setColor(!tabPane.isEnabledAt(tabIndex) ? LINE_DARK : (isSelected ? ACCENT_TEXT : MUTED));
            BasicGraphicsUtils.drawStringUnderlineCharAt(g2, title, tabPane.getDisplayedMnemonicIndexAt(tabIndex),
                    textRect.x, textRect.y + metrics.getAscent());
        }

        @Override
        protected int getTabLabelShiftX(int tabPlacement, int tabIndex, boolean isSelected) { return 0; }

        @Override
        protected int getTabLabelShiftY(int tabPlacement, int tabIndex, boolean isSelected) { return 0; }

        @Override
        protected void paintContentBorderTopEdge(Graphics g, int tabPlacement, int selectedIndex,
                                                 int x, int y, int w, int h) {
            g.setColor(LINE);
            g.drawLine(x, y, x + w - 1, y);
        }

        @Override
        protected void paintContentBorderLeftEdge(Graphics g, int tabPlacement, int selectedIndex,
                                                  int x, int y, int w, int h) { }

        @Override
        protected void paintContentBorderRightEdge(Graphics g, int tabPlacement, int selectedIndex,
                                                   int x, int y, int w, int h) { }

        @Override
        protected void paintContentBorderBottomEdge(Graphics g, int tabPlacement, int selectedIndex,
                                                    int x, int y, int w, int h) { }
    }

    private static class WrapLayout extends FlowLayout {
        WrapLayout(int align, int hgap, int vgap) {
            super(align, hgap, vgap);
        }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, true);
        }

        @Override
        public Dimension minimumLayoutSize(Container target) {
            Dimension min = layoutSize(target, false);
            min.width -= getHgap() + 1;
            return min;
        }

        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                Container container = target;
                while (container.getSize().width == 0 && container.getParent() != null) {
                    container = container.getParent();
                }
                int targetWidth = container.getSize().width;
                if (targetWidth == 0) targetWidth = Integer.MAX_VALUE;

                int hgap = getHgap();
                int vgap = getVgap();
                Insets insets = target.getInsets();
                int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
                int maxWidth = targetWidth - horizontalInsetsAndGap;

                Dimension dim = new Dimension(0, 0);
                int rowWidth = 0;
                int rowHeight = 0;
                for (int i = 0; i < target.getComponentCount(); i++) {
                    Component m = target.getComponent(i);
                    if (!m.isVisible()) continue;
                    Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                    if (rowWidth + d.width > maxWidth && rowWidth > 0) {
                        addRow(dim, rowWidth, rowHeight);
                        rowWidth = 0;
                        rowHeight = 0;
                    }
                    if (rowWidth != 0) rowWidth += hgap;
                    rowWidth += d.width;
                    rowHeight = Math.max(rowHeight, d.height);
                }
                addRow(dim, rowWidth, rowHeight);
                dim.width += horizontalInsetsAndGap;
                dim.height += insets.top + insets.bottom + vgap * 2;
                return dim;
            }
        }

        private void addRow(Dimension dim, int rowWidth, int rowHeight) {
            dim.width = Math.max(dim.width, rowWidth);
            if (dim.height > 0) dim.height += getVgap();
            dim.height += rowHeight;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {

            }

            Set<String> have = new HashSet<>(Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            String family = Font.DIALOG;
            for (String f : new String[]{"Segoe UI", "SF Pro Text", "Helvetica Neue", "Inter", "Noto Sans", "Ubuntu"}) {
                if (have.contains(f)) { family = f; break; }
            }
            applyFontFamily(family);

            Font base = UIManager.getFont("Label.font");
            float baseSize = base != null ? base.getSize2D() : 12f;
            scaleDefaults(DEFAULT_FONT / baseSize);

            UIManager.put("Panel.background", BG);
            UIManager.put("OptionPane.background", BG);
            UIManager.put("OptionPane.messageForeground", TEXT);

            GroceryGUI gui = new GroceryGUI();
            gui.setVisible(true);
            gui.itemField.requestFocusInWindow();
        });
    }
}