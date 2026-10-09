import java.util.*;


// File overview: Shopping cart container for basket totals and item quantities.
// This class keeps the related logic together for easier reading and maintenance.
public class Cart implements Iterable<Cart.Line> {

    public static class Line {
        private final Item item;
        private int quantity;
        private final double unitPrice;
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
    private int size = 0;

    public static double round2(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }

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

    public int qtyOf(String code) {
        for (Line l = head; l != null; l = l.next) {
            if (l.item.getCode().equals(code)) return l.quantity;
        }
        return 0;
    }

    public void removeQuantity(String code, int qty) {
        for (Line l = head; l != null; l = l.next) {
            if (l.item.getCode().equals(code)) {
                l.quantity -= qty;
                if (l.quantity <= 0) remove(code);
                return;
            }
        }
    }

    public Line lineAt(int index) {
        int i = 0;
        for (Line l = head; l != null; l = l.next, i++) {
            if (i == index) return l;
        }
        return null;
    }

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

    public int totalUnits() {
        int units = 0;
        for (Line l = head; l != null; l = l.next) units += l.quantity;
        return units;
    }

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