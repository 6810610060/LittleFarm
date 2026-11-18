/** crisis: prices drop to 60% (x0.6), rounded to the nearest gold. */
public class CrisisPrice implements MarketPriceStrategy {
    @Override
    public int applyPrice(int basePrice) {
        return (int) Math.round(basePrice * 0.6);
    }

    @Override
    public String getName() {
        return "crisis";
    }
}