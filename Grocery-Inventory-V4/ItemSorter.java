import java.util.*;


// File overview: Sorting utilities for benchmarking and comparing item orderings.
// This class keeps the related logic together for easier reading and maintenance.
public class ItemSorter {

    public enum Algorithm {
        INSERTION("Insertion sort"),
        SELECTION("Selection sort"),
        BUBBLE("Bubble sort");

        public final String label;

        Algorithm(String label) {
            this.label = label;
        }
    }

    public static class Result {
        public final long comparisons, movements;

        Result(long comparisons, long movements) {
            this.comparisons = comparisons;
            this.movements = movements;
        }
    }

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

    public static Result sort(Item[] a, Comparator<Item> order, Algorithm algorithm) {
        switch (algorithm) {
            case BUBBLE:    return bubbleSort(a, order);
            case SELECTION: return selectionSort(a, order);
            default:        return insertionSort(a, order);
        }
    }

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
            if (!swapped) break;
        }
        return new Result(comparisons, movements);
    }

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

    public static Result insertionSort(Item[] a, Comparator<Item> order) {
        long comparisons = 0, movements = 0;
        for (int i = 1; i < a.length; i++) {
            Item key = a[i];
            int j = i - 1;
            while (j >= 0) {
                comparisons++;
                if (order.compare(a[j], key) > 0) {
                    a[j + 1] = a[j];
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