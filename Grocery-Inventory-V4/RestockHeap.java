import java.time.*;
import java.time.temporal.ChronoUnit;


// File overview: Priority heap used to rank replenishment actions.
// This class keeps the related logic together for easier reading and maintenance.
public class RestockHeap {

    public static final double STOCK_WEIGHT = 0.6;
    public static final double EXPIRY_WEIGHT = 0.4;

    public static class Entry {
        public final Item item;
        public final double stockShortage;
        public final double expiryRisk;
        public final double priority;
        public final String action;

        Entry(Item item, double stockShortage, double expiryRisk, double priority, String action) {
            this.item = item;
            this.stockShortage = stockShortage;
            this.expiryRisk = expiryRisk;
            this.priority = priority;
            this.action = action;
        }
    }

    public static Entry entryFor(Item item, LocalDate today) {
        boolean expired = item.getExpiryDate().isBefore(today);
        int sellable = expired ? 0 : item.getQuantity();

        double shortage = 0;
        if (sellable <= item.getReorderLevel()) {
            shortage = (double) (item.getReorderLevel() + 1 - sellable) / (item.getReorderLevel() + 1);
        }

        double risk = 0;
        if (item.getQuantity() > 0) {
            long days = ChronoUnit.DAYS.between(today, item.getExpiryDate());
            if (days <= 0) {
                risk = 1;
            } else if (days <= Store.EXPIRY_ALERT_DAYS) {
                risk = (double) (Store.EXPIRY_ALERT_DAYS + 1 - days) / (Store.EXPIRY_ALERT_DAYS + 1);
            }
        }

        String action;
        if (expired && item.getQuantity() > 0) action = "EXPIRED - write off and reorder";
        else if (item.getQuantity() == 0)      action = "OUT OF STOCK - reorder now";
        else if (shortage > 0)                 action = "LOW STOCK - reorder";
        else if (risk > 0)                     action = "EXPIRING SOON - discount / sell first";
        else                                   action = "OK";

        double priority = STOCK_WEIGHT * shortage + EXPIRY_WEIGHT * risk;
        return new Entry(item, shortage, risk, priority, action);
    }

    private Entry[] heap = new Entry[16];
    private int size = 0;

    public void insert(Entry e) {
        if (size == heap.length) heap = java.util.Arrays.copyOf(heap, size * 2);
        heap[size] = e;
        siftUp(size);
        size++;
    }

    public Entry peek() {
        return (size == 0) ? null : heap[0];
    }

    public Entry extractMax() {
        if (size == 0) return null;
        Entry top = heap[0];
        size--;
        heap[0] = heap[size];
        heap[size] = null;
        if (size > 0) siftDown(0);
        return top;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

    private boolean before(Entry a, Entry b) {
        if (a.priority != b.priority) return a.priority > b.priority;
        if (a.item.getQuantity() != b.item.getQuantity()) return a.item.getQuantity() < b.item.getQuantity();
        return a.item.getName().compareToIgnoreCase(b.item.getName()) < 0;
    }

    private void siftUp(int i) {
        while (i > 0) {
            int parent = (i - 1) / 2;
            if (!before(heap[i], heap[parent])) break;
            swap(i, parent);
            i = parent;
        }
    }

    private void siftDown(int i) {
        while (true) {
            int left = 2 * i + 1, right = left + 1, best = i;
            if (left < size && before(heap[left], heap[best])) best = left;
            if (right < size && before(heap[right], heap[best])) best = right;
            if (best == i) break;
            swap(i, best);
            i = best;
        }
    }

    private void swap(int i, int j) {
        Entry t = heap[i];
        heap[i] = heap[j];
        heap[j] = t;
    }
}