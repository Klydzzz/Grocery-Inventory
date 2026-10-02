import java.util.*;

/**
 * The customer's basket for one sale, kept as a singly linked list of lines.
 * Nothing in the inventory changes while items are in the cart; stock is only
 * deducted when the cashier completes the payment.
 */
public class Cart implements Iterable<Cart.Line> {

    /** One product in the basket and how many of it the customer is buying. */
    public static class Line {
        private final Item item;
        private int quantity;
        private final double unitPrice;     // price at the moment it was scanned
        private Line next;

        Line(Item item, int quantity) {
            this.item = item;
            this.quantity = quantity;
            this.unitPrice = item.getPrice();
        }

        public Item getItem()        { return item; }
        public int getQuantity()     { return quantity; }
        public double getUnitPrice() { return unitPrice; }
        public double getLineTotal() { return round2(unitPrice * quantity); }
    }

    private Line head;
    private Line tail;
    private int size = 0;       // number of lines (different products)

    /** Rounds a money amount to 2 decimal places. */
    public static double round2(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }

    /** Adds the item; if it is already in the basket its quantity goes up instead. */
    public void add(Item item, int quantity) {
        for (Line l = head; l != null; l = l.next) {
            if (l.item.getCode().equals(item.getCode())) {
                l.quantity += quantity;
                return;
            }
        }
        Line line = new Line(item, quantity);
        if (head == null) {
            head = line;
            tail = line;
        } else {
            tail.next = line;
            tail = line;
        }
        size++;
    }

    /** How many of this product are already in the basket. */
    public int qtyOf(String code) {
        for (Line l = head; l != null; l = l.next) {
            if (l.item.getCode().equals(code)) return l.quantity;
        }
        return 0;
    }

    /** Returns the line at position index (0-based), or null if out of range. */
    public Line lineAt(int index) {
        int i = 0;
        for (Line l = head; l != null; l = l.next, i++) {
            if (i == index) return l;
        }
        return null;
    }

    /** Removes the whole line for this product code. Returns true if it was found. */
    public boolean remove(String code) {
        Line prev = null;
        for (Line l = head; l != null; prev = l, l = l.next) {
            if (l.item.getCode().equals(code)) {
                if (prev == null) head = l.next;
                else prev.next = l.next;
                if (l == tail) tail = prev;
                size--;
                return true;
            }
        }
        return false;
    }

    public int size()        { return size; }
    public boolean isEmpty() { return size == 0; }

    /** Total number of units across all lines. */
    public int totalUnits() {
        int units = 0;
        for (Line l = head; l != null; l = l.next) units += l.quantity;
        return units;
    }

    /** Amount the customer has to pay. */
    public double total() {
        double sum = 0;
        for (Line l = head; l != null; l = l.next) sum += l.getLineTotal();
        return round2(sum);
    }

    @Override
    public Iterator<Line> iterator() {
        return new Iterator<Line>() {
            private Line current = head;

            @Override
            public boolean hasNext() {
                return current != null;
            }

            @Override
            public Line next() {
                if (current == null) throw new NoSuchElementException();
                Line line = current;
                current = current.next;
                return line;
            }
        };
    }
}
