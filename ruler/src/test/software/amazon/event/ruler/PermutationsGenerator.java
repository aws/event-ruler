package software.amazon.event.ruler;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class PermutationsGenerator {

    private PermutationsGenerator() { }

    public static <T> List<T[]> generateAllPermutations(T[] array) {
        int numPermutations = IntStream.rangeClosed(1, array.length).reduce((x, y) -> x * y).getAsInt();
        List<T[]> result = new ArrayList<>(numPermutations);
        generateAllPermutationsRecursive(array.length, array, result);
        return result;
    }

    private static <T> void generateAllPermutationsRecursive(int n, T[] array, List<T[]> result) {
        if (n == 1) {
            result.add(array.clone());
        } else {
            for (int i = 0; i < n - 1; i++) {
                generateAllPermutationsRecursive(n - 1, array, result);
                if (n % 2 == 0) {
                    swap(array, i, n - 1);
                } else {
                    swap(array, 0, n - 1);
                }
            }
            generateAllPermutationsRecursive(n - 1, array, result);
        }
    }

    private static <T> void swap(T[] input, int a, int b) {
        T tmp = input[a];
        input[a] = input[b];
        input[b] = tmp;
    }
}