import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {
    private static final int LENGTH = 5;

    public static void main(String[] args) throws InterruptedException {
        List<Integer> v1 = IntStream.range(0, LENGTH).boxed().collect(Collectors.toList());
        List<Integer> v2 = IntStream.range(0, LENGTH).boxed().collect(Collectors.toList());

        Lock lock = new ReentrantLock();
        Condition condition = lock.newCondition();
        Queue<Long> buffer = new LinkedList<>();
        AtomicReference<Boolean> available = new AtomicReference<>();
        AtomicReference<Boolean> done = new AtomicReference<>();
        available.set(false);
        done.set(false);
        AtomicLong dotProduct = new AtomicLong(0);

        Thread producer = new Thread(() -> {
            for (int i = 0; i < LENGTH; i++) {
                long product = (long) v1.get(i) * v2.get(i);
                System.out.println("from producer: " + product);
                buffer.add(product);
                available.set(true);
                System.out.println("buffer: " + buffer);
                synchronized (condition) {
                    condition.notify();
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            done.set(true);
        });

        Thread consumer = new Thread(() -> {
            while (!done.get()) {
                while (!available.get()) {
                    try {
                        synchronized (condition) {
                            condition.wait();
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    while (!buffer.isEmpty()) {
                        System.out.println("from consumer: " + buffer);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        dotProduct.addAndGet(buffer.remove());
                    }
                }
            }
        });

        consumer.start();
        producer.start();

        producer.join();
        consumer.join();

        System.out.println(dotProduct.get());

    }
}
