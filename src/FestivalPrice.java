/** festival: everything sells for double (x2). */
public class FestivalPrice implements MarketPriceStrategy {
    @Override
    public int applyPrice(int basePrice) {
        return basePrice * 2;
    }

    @Override
    public String getName() {
        return "festival";
    }
}