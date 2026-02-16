package com.pdp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

//@Data
public class Polynomial {
    public List<Integer> powers;
    List<Lock> locks = new ArrayList<>();

    public Polynomial(List<Integer> powers) {
        this.powers = powers;
    }

    public Polynomial() {

    }

    public Polynomial multiply(Polynomial other) {
        Polynomial result = new Polynomial(
                new ArrayList<>(Collections.nCopies(this.powers.size() + other.powers.size() - 1, 0)));

        for (int i = 0; i < this.powers.size(); i++) {
            for (int j = 0; j < other.powers.size(); j++) {
                result.powers.set(i + j, result.powers.get(i + j) + this.powers.get(i) * other.powers.get(j));
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return "Polynomial{" +
                "powers=" + powers +
                '}';
    }

    public Polynomial multiplyParallel(Polynomial other, int threadCount) throws InterruptedException {
        Polynomial result = new Polynomial(
                new ArrayList<>(Collections.nCopies(this.powers.size() + other.powers.size() - 1, 0)));
        result.powers.forEach(ignored -> locks.add(new ReentrantLock()));

        List<Thread> threads = new ArrayList<>();
        int batchSize = this.powers.size() / threadCount + 1;

        for (int i = 0; i < this.powers.size(); i += batchSize) {
            int startIndex = i;
            int finalIndex = Math.min(i + batchSize, this.powers.size());
            Thread thread = new Thread(() -> multiplyParallelForIndexes(startIndex, finalIndex, other, result));
            thread.start();
            threads.add(thread);
        }

        for (Thread thread : threads) {
            thread.join();
        }

        return result;
    }

    private void multiplyParallelForIndexes(int startIndex, int finalIndex, Polynomial other, Polynomial result) {
        for (int i = startIndex; i < finalIndex; i++) {
            for (int j = 0; j < other.powers.size(); j++) {
                locks.get(i + j).lock();
                result.powers.set(i + j, result.powers.get(i + j) + this.powers.get(i) * other.powers.get(j));
                locks.get(i + j).unlock();
            }
        }
    }

    public int degree() {
        return this.powers.size() - 1;
    }

    public Polynomial add(Polynomial other) {
        Polynomial result = new Polynomial(new ArrayList<>(Collections.nCopies(this.powers.size(), 0)));
        int i, index;
        for (i = 0; i < Math.min(this.powers.size(), other.powers.size()); i++) {
            result.powers.set(i, this.powers.get(i) + other.powers.get(i));
        }
        index = i;
        while (index < this.powers.size()){
            result.powers.set(index, this.powers.get(index));
            index++;
        }
        index = i;
        while (index < other.powers.size()){
            result.powers.set(index, other.powers.get(index));
            index++;
        }
        return result;
    }

    public Polynomial toPower(int power) {
        Polynomial result = new Polynomial(new ArrayList<>(Collections.nCopies(this.powers.size() + power, 0)));
        for (int i = 0; i < this.powers.size(); i++) {
            result.powers.set(i + power, this.powers.get(i));
        }
        return result;
    }

    public Polynomial subtract(Polynomial other) {
        Polynomial result = new Polynomial(new ArrayList<>(Collections.nCopies(this.powers.size(), 0)));
        for (int i = 0; i < Math.min(this.powers.size(), other.powers.size()); i++) {
            result.powers.set(i, this.powers.get(i) - other.powers.get(i));
        }
        return result;
    }
}
