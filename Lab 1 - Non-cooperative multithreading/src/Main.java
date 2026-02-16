import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class Main {

    private static final int PRODUCTS_COUNT = 10000;
    private static final int MAX_PRICE = 100;
    private static final int QUANTITY = 10000;
    private static final int BILLS_COUNT = 10000;
    private static final int MAX_QUANTITY = 10;
    private static final ReentrantLock money_mutex = new ReentrantLock();
    private static final List<Bill> sales = new ArrayList<>();
    private static int money = 0;
    private static List<Product> products;
    private static List<Bill> bills;

    public static void main(String[] args) throws FileNotFoundException {
        products = generateProducts();
//        products = Arrays.asList(
//                new Product("Milk", QUANTITY, 5),
//                new Product("Bread", QUANTITY, 10),
//                new Product("Water", QUANTITY, 7)
//        );

        bills = generateBills(products);
//        bills = Arrays.asList(
//                new Bill(Map.of(
//                        products.get(0), 100,
//                        products.get(1), 1)),
//                new Bill(Map.of(
//                        products.get(1), 10,
//                        products.get(2), 25)),
//                new Bill(Map.of(
//                        products.get(0), 10,
//                        products.get(2), 25)),
//                new Bill(Map.of(
//                        products.get(1), 10,
//                        products.get(0), 25)),
//                new Bill(Map.of(
//                        products.get(1), 50,
//                        products.get(2), 25))
//        );

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        Runnable periodicTask = Main::checkConsistency;
        executor.scheduleAtFixedRate(periodicTask, 0, 10, TimeUnit.MILLISECONDS);

        List<Thread> threads = new ArrayList<>();
        bills.forEach(bill -> {
            Thread thread = new Thread(buy(bill));
            thread.start();
            threads.add(thread);
        });

        threads.forEach(thread -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        executor.shutdown();
//        System.out.println(sales);
//        System.out.println("money " + money);
        checkConsistency();
        PrintWriter out = new PrintWriter("sales.txt");
        sales.forEach(out::println);
    }

    private static void checkConsistency() {
        products.forEach(product -> product.getMutex().lock());
//        int profit = products.stream().mapToInt(product -> (QUANTITY - product.getQuantity()) * product.getUnitPrice()).sum();
        int profit = sales.stream().mapToInt(
                bill -> bill.getProductQuantityMap().entrySet().stream().mapToInt(
                        entry -> entry.getKey().getUnitPrice() * entry.getValue())
                        .sum())
                .sum();
        money_mutex.lock();
        System.out.println("Computed profit: " + profit +
                ", Actual profit: " + money +
                ", Difference: " + (profit - money));
        money_mutex.unlock();
        products.forEach(product -> product.getMutex().unlock());
    }

    private static List<Bill> generateBills(List<Product> products) {
        List<Bill> bills = new ArrayList<>();

        Random random = new Random();
        for (int i = 0; i < BILLS_COUNT; i++) {
            Map<Product, Integer> productQuantityMap = new HashMap<>();
            for (int j = 0; j < random.nextInt(MAX_QUANTITY) + 1; j++) {
                productQuantityMap.put(products.get(random.nextInt(PRODUCTS_COUNT)), random.nextInt(MAX_QUANTITY));
            }
            Bill bill = new Bill(productQuantityMap);
            bills.add(bill);
        }

        return bills;
    }

    private static List<Product> generateProducts() {
        List<Product> products = new ArrayList<>();
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        int targetStringLength = 10;
        Random random = new Random();
        for (int i = 0; i < PRODUCTS_COUNT; i++) {
            String generatedString = random.ints(leftLimit, rightLimit + 1)
                    .limit(targetStringLength)
                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                    .toString();

            Product product = new Product(generatedString, QUANTITY, new Random().nextInt(MAX_PRICE));
            products.add(product);
        }
        return products;
    }

    private static Runnable buy(Bill bill) {
        return () -> {
            AtomicInteger total = new AtomicInteger();
            bill.getProductQuantityMap().forEach((product, quantity) -> total.addAndGet(product.buy(quantity)));
            bill.setTotal(total.get());
            money_mutex.lock();
            money += total.get();
            sales.add(bill);
            money_mutex.unlock();
        };
    }
}
