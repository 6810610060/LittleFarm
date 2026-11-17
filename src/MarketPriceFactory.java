public class MarketPriceFactory {
    public static final String[] WORDS = { "normal", "festival", "crisis" };

    public static MarketPriceStrategy create(String word) {
        if (word == null) {
            System.err.println("No market word given - using normal prices.");
            return new NormalPrice();
        }
        switch (word.trim().toLowerCase()) {
            case "normal":
                return new NormalPrice();
            case "festival":
                return new FestivalPrice();
            case "crisis":
                return new CrisisPrice();
            default:
                System.err.println("Unknown market word \"" + word + "\" - using normal prices. Words: "
                        + String.join(", ", WORDS));
                return new NormalPrice();
        }
    }
}