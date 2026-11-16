/** The current season is determined by the in-game day; each season lasts ten days. */
public enum Season {
    HOT("Hot"),
    RAINY("Rainy"),
    COOL("Cool");

    private static final int DAYS_PER_SEASON = 10;
    private final String label;

    Season(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }

    /** Day 1 starts the hot season; the three-season cycle repeats every 30 days. */
    public static Season forDay(int day) {
        if (day < 1) throw new IllegalArgumentException("day must be at least 1, not " + day);
        return values()[((day - 1) / DAYS_PER_SEASON) % values().length];
    }
}
