public interface MarketPriceStrategy {
int applyPrice(int basePrice);
/** The mode's word, shown on screen. */
String getName();
}