import java.util.Map;

public class Bill {
    private Map<Product, Integer> productQuantityMap;
    private int total;

    public Bill(Map<Product, Integer> productQuantityMap) {
        this.productQuantityMap = productQuantityMap;
        this.total = 0;
    }

    public Map<Product, Integer> getProductQuantityMap() {
        return productQuantityMap;
    }

    public void setProductQuantityMap(Map<Product, Integer> productQuantityMap) {
        this.productQuantityMap = productQuantityMap;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    @Override
    public String toString() {
        return "Bill{" +
                "productQuantityMap=" + productQuantityMap +
                ", total=" + total +
                '}';
    }
}
