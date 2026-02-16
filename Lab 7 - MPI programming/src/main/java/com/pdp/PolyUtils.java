package com.pdp;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class PolyUtils {
    private static final Random random = new Random();

    private PolyUtils() {}

    public static Polynomial randomPolynomial(int size, int maxCoeff) {
        List<Integer> coeffs = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            coeffs.add(random.nextInt(maxCoeff));
        }
        return new Polynomial(coeffs);
    }

    public static int[] toIntArray(Polynomial p) {
        int[] a = new int[p.powers.size()];
        for (int i = 0; i < p.powers.size(); i++) {
            a[i] = p.powers.get(i);
        }
        return a;
    }

    public static Polynomial fromIntArray(int[] a) {
        List<Integer> coeffs = new ArrayList<>(a.length);
        for (int v : a) {
            coeffs.add(v);
        }
        return new Polynomial(coeffs);
    }

    public static boolean equalsPoly(Polynomial p, Polynomial q) {
        if (p == null || q == null) return false;
        if (p.powers.size() != q.powers.size()) return false;
        for (int i = 0; i < p.powers.size(); i++) {
            if (!p.powers.get(i).equals(q.powers.get(i))) return false;
        }
        return true;
    }
}
