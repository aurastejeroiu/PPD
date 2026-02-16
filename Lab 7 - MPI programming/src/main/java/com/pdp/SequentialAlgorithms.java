package com.pdp;

/**
 * Same CPU algorithms as in Lab 5 (regular O(n^2) and Karatsuba), kept local to Lab 7.
 */
public final class SequentialAlgorithms {

    private SequentialAlgorithms() {}

    public static Polynomial regularSequential(Polynomial p, Polynomial q) {
        return p.multiply(q);
    }

    public static Polynomial karatsubaSequential(Polynomial p, Polynomial q) {
        if (p.degree() < 3 || q.degree() < 3) {
            return p.multiply(q);
        }

        int split = Math.max(p.degree(), q.degree()) / 2 + 1;
        Polynomial p1 = new Polynomial(p.powers.subList(0, split));
        Polynomial p2 = new Polynomial(p.powers.subList(split, p.powers.size()));
        Polynomial q1 = new Polynomial(q.powers.subList(0, split));
        Polynomial q2 = new Polynomial(q.powers.subList(split, q.powers.size()));

        Polynomial z0 = karatsubaSequential(p1, q1);
        Polynomial z1 = karatsubaSequential(p1.add(p2), q1.add(q2));
        Polynomial z2 = karatsubaSequential(p2, q2);

        return z2.toPower(2 * split)
                .add(z1.subtract(z2).subtract(z0).toPower(split))
                .add(z0);
    }
}
