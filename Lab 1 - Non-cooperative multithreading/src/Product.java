import java.util.concurrent.locks.ReentrantLock;

public class Product {
    private final String name;
    private final int unitPrice;
    private int quantity;
    private final ReentrantLock mutex;

    public Product(String name, int quantity, int unitPrice) {
        this.name = name;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.mutex = new ReentrantLock();
    }

    public String getName() {
        return name;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getUnitPrice() {
        return unitPrice;
    }

    @Override
    public String toString() {
        return "Product{" +
                "name='" + name + '\'' +
                ", quantity=" + quantity +
                ", unitPrice=" + unitPrice +
                '}';
    }

    public ReentrantLock getMutex() {
        return mutex;
    }

    public int buy(Integer quantity) {
        if (quantity > this.quantity)
            quantity = this.quantity;
        this.mutex.lock();
            this.quantity -= quantity;
        this.mutex.unlock();
        return quantity * this.unitPrice;
    }
}
