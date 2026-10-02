import java.util.*;

/**
 * Hand-written sorting algorithms that work on the store's actual Item objects.
 * Bubble sort, selection sort and insertion sort (no Arrays.sort / List.sort used).
 *
 * Every sort counts what it does so the algorithms can be compared:
 *   comparisons = how many times two items were compared
 *   movements   = how many times an item was moved to a new position
 *                 (a swap moves two items, so it counts as 2)
 *
 * Ties are always broken by product code, so every sort gives exactly the same order.
 */
public class ItemSorter {

    /** The algorithms. The order matches the drop-down in the GUI. */
    public enum Algorithm {
        INSERTION("Insertion sort"),
        SELECTION("Selection sort"),
        BUBBLE("Bubble sort");

        public final String label;

        Algorithm(String label) {
            this.label = label;
        }
    }

    /** What a sort did. */
    public static class Result {
        public final long comparisons, movements;

        Result(long comparisons, long movements) {
            this.comparisons = comparisons;
            this.movements = movements;
        }
    }

    // ---- ready-made orderings (each falls back to the product code on a tie)
    public static final Comparator<Item> BY_NAME =
            (a, b) -> tieBreak(a.getName().compareToIgnoreCase(b.getName()), a, b);
    public static final Comparator<Item> BY_PRICE =
            (a, b) -> tieBreak(Double.compare(a.getPrice(), b.getPrice()), a, b);
    public static final Comparator<Item> BY_QUANTITY =
            (a, b) -> tieBreak(Integer.compare(a.getQuantity(), b.getQuantity()), a, b);
    public static final Comparator<Item> BY_EXPIRY =
            (a, b) -> tieBreak(a.getExpiryDate().compareTo(b.getExpiryDate()), a, b);

    private static int tieBreak(int c, Item a, Item b) {
        return (c != 0) ? c : a.getCode().compareTo(b.getCode());
    }

    /** Sorts the array in place with the chosen algorithm. */
    public static Result sort(Item[] a, Comparator<Item> order, Algorithm algorithm) {
        switch (algorithm) {
            case BUBBLE:    return bubbleSort(a, order);
            case SELECTION: return selectionSort(a, order);
            default:        return insertionSort(a, order);
        }
    }

    /** Repeatedly swaps neighbours that are out of order; the biggest item "bubbles" to the end each pass. */
    public static Result bubbleSort(Item[] a, Comparator<Item> order) {
        long comparisons = 0, movements = 0;
        for (int pass = 0; pass < a.length - 1; pass++) {
            boolean swapped = false;
            for (int j = 0; j < a.length - 1 - pass; j++) {
                comparisons++;
                if (order.compare(a[j], a[j + 1]) > 0) {
                    Item tmp = a[j];
                    a[j] = a[j + 1];
                    a[j + 1] = tmp;
                    movements += 2;
                    swapped = true;
                }
            }
            if (!swapped) break;                    // already in order: stop early
        }
        return new Result(comparisons, movements);
    }

    /** Finds the smallest remaining item and swaps it into the next position. */
    public static Result selectionSort(Item[] a, Comparator<Item> order) {
        long comparisons = 0, movements = 0;
        for (int i = 0; i < a.length - 1; i++) {
            int smallest = i;
            for (int j = i + 1; j < a.length; j++) {
                comparisons++;
                if (order.compare(a[j], a[smallest]) < 0) smallest = j;
            }
            if (smallest != i) {
                Item tmp = a[i];
                a[i] = a[smallest];
                a[smallest] = tmp;
                movements += 2;
            }
        }
        return new Result(comparisons, movements);
    }

    /** Takes each item in turn and slides it left into its place among the already-sorted items. */
    public static Result insertionSort(Item[] a, Comparator<Item> order) {
        long comparisons = 0, movements = 0;
        for (int i = 1; i < a.length; i++) {
            Item key = a[i];
            int j = i - 1;
            while (j >= 0) {
                comparisons++;
                if (order.compare(a[j], key) > 0) {
                    a[j + 1] = a[j];                // shift one place right
                    movements++;
                    j--;
                } else {
                    break;
                }
            }
            if (j + 1 != i) {
                a[j + 1] = key;
                movements++;
            }
        }
        return new Result(comparisons, movements);
    }
}
