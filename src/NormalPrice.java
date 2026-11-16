/** normal: prices stay as they are (x1.0). */
public class NormalPrice implements MarketPriceStrategy {
    @Override
    public int applyPrice(int basePrice) {
        return basePrice;
    }

    @Override
    public String getName() {
        return "normal";
    }
}