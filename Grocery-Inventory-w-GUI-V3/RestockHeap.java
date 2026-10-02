import java.time.*;
import java.time.temporal.ChronoUnit;

/**
 * Custom max-heap (binary heap stored in an array) of items that need attention,
 * so the MOST URGENT one is always on top and can be taken out in O(log n).
 *
 * ---------------------------------------------------------------- PRIORITY FORMULA
 *
 *     priority = 0.6 x stockShortage + 0.4 x expiryRisk          (0 = no action, 1 = most urgent)
 *
 *   stockShortage (0..1): how far the SELLABLE stock is below the reorder level.
 *       1 = nothing sellable left, 0 = at or above the reorder level.
 *       Expired stock counts as zero, because the store must not sell it.
 *   expiryRisk (0..1): 0 when the expiry date is further away than the alert window
 *       (Store.EXPIRY_ALERT_DAYS), rising to 1 on the expiry date; 1 if already expired.
 *       An empty shelf has nothing to expire, so its risk is 0.
 *
 * Why these two attributes and these weights:
 *   - A product that is missing from the shelf loses sales right now and for certain, so stock
 *     shortage gets the larger weight (0.6).
 *   - Stock that is about to expire is a loss that can still be reduced (discount it, sell it first),
 *     so it gets the smaller weight (0.4).
 *   - Expired stock scores highest of all (1.0): it is unsellable (shortage 1) AND has to be removed (risk 1).
 *   - Scaling both to 0..1 first stops one attribute from swamping the other just because its numbers are bigger.
 */
public class RestockHeap {

    public static final double STOCK_WEIGHT = 0.6;
    public static final double EXPIRY_WEIGHT = 0.4;

    /** One item waiting in the heap, with its score and why. */
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

    /** Works out an item's priority entry (priority 0 means it needs no attention). */
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

    // ----------------------------------------------------------------- the heap

    private Entry[] heap = new Entry[16];     // heap[0] is the top; children of i are 2i+1 and 2i+2
    private int size = 0;

    /** Adds an entry and lets it float up to its place. O(log n). */
    public void insert(Entry e) {
        if (size == heap.length) heap = java.util.Arrays.copyOf(heap, size * 2);
        heap[size] = e;
        siftUp(size);
        size++;
    }

    /** The most urgent entry without removing it, or null if empty. O(1). */
    public Entry peek() {
        return (size == 0) ? null : heap[0];
    }

    /** Removes and returns the most urgent entry, or null if empty. O(log n). */
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

    /** True if a should be served before b: higher priority first, then emptier shelf, then name. */
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
