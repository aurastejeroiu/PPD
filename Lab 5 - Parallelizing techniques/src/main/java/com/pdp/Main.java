package com.pdp;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

public class Main {

    private static final Random random = new Random();
    private static final int THREAD_COUNT = 12;

    public static void main(String[] args) throws InterruptedException {

//      Polynomial p1 = new Polynomial(randomIntList(32768, 40)); Polynomial p2 = new Polynomial(randomIntList(32768, 40));
        Polynomial p1 = new Polynomial(randomIntList(4, 4)); Polynomial p2 = new Polynomial(randomIntList(4, 4));

        System.out.println(p1);
        System.out.println(p2);

        long normalSequentialTime = System.currentTimeMillis();
        Polynomial normalSequential = normalSequential(p1, p2);
        normalSequentialTime = System.currentTimeMillis() - normalSequentialTime;
        System.out.println("Normal Sequential:    " + normalSequential + " time elapsed: " + normalSequentialTime);

        long normalParallelTime = System.currentTimeMillis();
        Polynomial normalParallel = normalParallel(p1, p2);
        normalParallelTime = System.currentTimeMillis() - normalParallelTime;
        System.out.println("Normal Parallel:      " + normalParallel + " time elapsed: " + normalParallelTime);

        long karatsubaSequentialTime = System.currentTimeMillis();
        Polynomial karatsubaSequential = karatsubaSequential(p1, p2);
        karatsubaSequentialTime = System.currentTimeMillis() - karatsubaSequentialTime;
        System.out.println("Karatsuba Sequential: " + karatsubaSequential + " time elapsed: " + karatsubaSequentialTime);

        long karatsubaParallelTime = System.currentTimeMillis();
        Polynomial karatsubaParallel = karatsubaParallel(p1, p2, 0);
        karatsubaParallelTime = System.currentTimeMillis() - karatsubaParallelTime;
        System.out.println("Karatsuba Parallel:   " + karatsubaParallel + " time elapsed: " + karatsubaParallelTime);


        System.out.println("Normal Sequential time elapsed: " + normalSequentialTime);
        System.out.println("Normal Parallel time elapsed: " + normalParallelTime);
        System.out.println("Karatsuba Sequential time elapsed: " + karatsubaSequentialTime);
        System.out.println("Karatsuba Parallel time elapsed: " + karatsubaParallelTime);

    }

    private static Polynomial karatsubaParallel(Polynomial p, Polynomial q, int depth) throws InterruptedException {
        if (depth > 4) {
            return karatsubaSequential(p, q);
        }

        if (p.degree() < 3 || q.degree() < 3) {
            return p.multiply(q);
        }

        int split = Math.max(p.degree(), q.degree()) / 2 + 1;
        Polynomial p1 = new Polynomial(p.powers.subList(0, split));
        Polynomial p2 = new Polynomial(p.powers.subList(split, p.powers.size()));
        Polynomial q1 = new Polynomial(q.powers.subList(0, split));
        Polynomial q2 = new Polynomial(q.powers.subList(split, q.powers.size()));


//        Polynomial r1 = karatsubaSequential(p1, q1);
//        Polynomial r2 = karatsubaSequential(p2, q2);
//        Polynomial r3 = karatsubaSequential(p1.add(p2),q1.add(q2)).subtract(r1).subtract(r2);
//
//        return r1.toPower(2 * split).add(r3.toPower(split)).add(r2);

        AtomicReference<Polynomial> z0 = new AtomicReference<>();
        AtomicReference<Polynomial> z1 = new AtomicReference<>();
        AtomicReference<Polynomial> z2 = new AtomicReference<>();
        Thread thread0 = new Thread(() -> {
            try {
                z0.set(karatsubaParallel(p1, q1, depth + 1));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread0.start();
        Thread thread1 = new Thread(() -> {
            try {
                z1.set(karatsubaParallel(p1.add(p2), q1.add(q2), depth + 1));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread1.start();
        Thread thread2 = new Thread(() -> {
            try {
                z2.set(karatsubaParallel(p2, q2, depth + 1));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread2.start();
        thread0.join();
        thread1.join();
        thread2.join();

        return z2.get().toPower(2 * split).add(z1.get().subtract(z2.get()).subtract(z0.get()).toPower(split)).add(z0.get());

    }

    private static Polynomial karatsubaSequential(Polynomial p, Polynomial q) {
        if (p.degree() < 3 || q.degree() < 3) {
            return p.multiply(q);
        }

        int split = Math.max(p.degree(), q.degree()) / 2 + 1;
        Polynomial p1 = new Polynomial(p.powers.subList(0, split));
        Polynomial p2 = new Polynomial(p.powers.subList(split, p.powers.size()));
        Polynomial q1 = new Polynomial(q.powers.subList(0, split));
        Polynomial q2 = new Polynomial(q.powers.subList(split, q.powers.size()));


//        Polynomial r1 = karatsubaSequential(p1, q1);
//        Polynomial r2 = karatsubaSequential(p2, q2);
//        Polynomial r3 = karatsubaSequential(p1.add(p2),q1.add(q2)).subtract(r1).subtract(r2);
//
//        return r1.toPower(2 * split).add(r3.toPower(split)).add(r2);

        Polynomial z0 = karatsubaSequential(p1, q1);
        Polynomial z1 = karatsubaSequential(p1.add(p2), q1.add(q2));
        Polynomial z2 = karatsubaSequential(p2, q2);

        return z2.toPower(2 * split).add(z1.subtract(z2).subtract(z0).toPower(split)).add(z0);
    }

    private static Polynomial normalParallel(Polynomial p1, Polynomial p2) throws InterruptedException {
        return p1.multiplyParallel(p2, THREAD_COUNT);
    }

    private static Polynomial normalSequential(Polynomial p1, Polynomial p2) {
        return p1.multiply(p2);
    }

    private static List<Integer> randomIntList(int size, int max) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(random.nextInt(max));
        }
        return list;
    }

}
