import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Saves the farm as plain text you can open and read, e.g.
 * day 4
 * weather RAINY
 * gold 380
 * seeds Turnip 2
 * request Turnip 3 360 4 (crop amount reward daysLeft)
 * soil 5 6 Turnip 1 wet (col row crop daysGrown wet/dry; "-" for an empty plot)
 * feed 12
 * animal Chicken 0 3 1 no (kind slot love fedDays ready yes/no)
 */
public class SaveGame {
    private static final String HEADER = "little-farm save 1";
    private final Path file;

    public SaveGame(String fileName) {
        this.file = Paths.get(fileName);
    }

    public boolean exists() {
        return Files.exists(file);
    }

    /**
     * A one-line description for the title menu, e.g. "Day 5, 780G", or null if
     * there is no save.
     */
    public String describe() {
        if (!exists())
            return null;
        String day = "?", gold = "?";
        try {
            for (String line : Files.readAllLines(file)) {
                if (line.startsWith("day "))
                    day = line.substring(4).trim();
                else if (line.startsWith("gold "))
                    gold = line.substring(5).trim();
            }
        } catch (IOException e) {
            return "unreadable save";
        }
        return "Day " + day + ", " + gold + "G";
    }

    public void delete() throws IOException {
        Files.deleteIfExists(file);
    }

    public void save(Farm farm, Farmer farmer, GameClock clock) throws IOException {
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file))) {
            out.println(HEADER);
            out.println("day " + clock.getDay());
            out.println("weather " + clock.getWeather().name());
            out.println("gold " + farmer.getGold());
            out.println("earned " + farmer.getTotalEarned());
            out.println("can " + farmer.getCan().getWater());
            for (String name : CropFactory.NAMES)
                out.println("seeds " + name + " " + farmer.getSeedBag(name).getSeeds());
            for (Map.Entry<String, Integer> entry : farmer.getBasket().asMap().entrySet()) {
                out.println("basket " + entry.getKey() + " " + entry.getValue());
            }
            Villager villager = farm.getVillager();
            out.println("villager " + villager.getFriendship() + " " + villager.getRequestsDone());
            Request request = villager.getRequest();
            if (request != null) {
                out.println("request " + request.getCropName() + " " + request.getAmount() + " "
                        + request.getReward() + " " + request.getDaysLeft());
            }
            out.println("feed " + farmer.getFeed());
            for (String kind : AnimalFactory.NAMES) {
                Pen pen = farm.penFor(kind);
                for (Animal animal : pen.getAnimals()) {
                    out.println("animal " + kind + " " + pen.slotOf(animal) + " " + animal.getLove() + " "
                            + animal.getFedDays() + (animal.isProductReady() ? " yes" : " no"));
                }
            }
            for (int r = 0; r < Farm.ROWS; r++) {
                for (int c = 0; c < Farm.COLS; c++) {
                    Tile tile = farm.tileAt(c, r);
                    if (tile.getType() != TileType.SOIL)
                        continue;
                    Crop crop = tile.getCrop();
                    out.println("soil " + c + " " + r + " "
                            + (crop == null ? "- 0" : crop.getName() + " " + crop.getDaysGrown())
                            + (tile.isWatered() ? " wet" : " dry"));
                }
            }
        }
    }

    /** Fills a brand-new farm, farmer and clock from the file. */
    public void loadInto(Farm farm, Farmer farmer, GameClock clock) throws IOException {
        List<String> lines = Files.readAllLines(file);
        if (lines.isEmpty() || !lines.get(0).trim().equals(HEADER)) {
            throw new IOException("line 1: invalid header");
        }

        for (int i = 1; i < lines.size(); i++) {
            int lineNumber = i + 1;
            String trimmed = lines.get(i).trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String[] parts = trimmed.split("\\s+");

            try {
                switch (parts[0]) {
                    case "day":
                        requireLength(parts, 2);
                        clock.setDay(parsePositiveInt(parts[1], "day"));
                        break;

                    case "weather":
                        requireLength(parts, 2);
                        clock.setWeather(Weather.valueOf(parts[1]));
                        break;

                    case "gold":
                        requireLength(parts, 2);
                        farmer.setGold(parseNonNegativeInt(parts[1], "gold"));
                        break;

                    case "earned":
                        requireLength(parts, 2);
                        farmer.setTotalEarned(parseNonNegativeInt(parts[1], "earned"));
                        break;

                    case "villager":
                        requireLength(parts, 3);
                        int friendship = parseNonNegativeInt(parts[1], "friendship");
                        int requestsDone = parseNonNegativeInt(parts[2], "requestsDone");
                        farm.getVillager().restore(friendship, requestsDone);
                        break;

                    case "request":
                        requireLength(parts, 5);
                        if (!isValidCropName(parts[1])) {
                            throw new IllegalArgumentException("unknown crop '" + parts[1] + "'");
                        }
                        int amount = parsePositiveInt(parts[2], "amount");
                        int reward = parseNonNegativeInt(parts[3], "reward");
                        int daysLeft = parsePositiveInt(parts[4], "daysLeft");
                        farm.getVillager().setRequest(new Request(parts[1], amount, reward, daysLeft));
                        break;

                    case "can":
                        requireLength(parts, 2);
                        farmer.getCan().setWater(parseNonNegativeInt(parts[1], "can water"));
                        break;

                    case "seeds":
                        requireLength(parts, 3);
                        SeedBag bag = farmer.getSeedBag(parts[1]);
                        if (bag == null) {
                            throw new IllegalArgumentException("unknown seed '" + parts[1] + "'");
                        }
                        bag.setSeeds(parseNonNegativeInt(parts[2], "seeds"));
                        break;

                    case "basket":
                        requireLength(parts, 3);
                        int qty = parsePositiveInt(parts[2], "basket quantity");
                        farmer.getBasket().add(parts[1], qty);
                        break;

                    case "soil":
                        loadSoil(farm, parts);
                        break;

                    case "feed":
                        requireLength(parts, 2);
                        farmer.setFeed(parseNonNegativeInt(parts[1], "feed"));
                        break;

                    case "animal":
                        loadAnimal(farm, parts);
                        break;

                    default:
                        throw new IllegalArgumentException("unknown entry '" + parts[0] + "'");
                }
            } catch (Exception e) {
                throw new IOException("line " + lineNumber + ": " + e.getMessage(), e);
            }
        }
    }

    private void loadAnimal(Farm farm, String[] parts) {
        requireLength(parts, 6);
        Pen pen = farm.penFor(parts[1]);
        if (pen == null) {
            throw new IllegalArgumentException("unknown animal '" + parts[1] + "'");
        }
        int slot = parseNonNegativeInt(parts[2], "slot");
        int love = parseNonNegativeInt(parts[3], "love");
        int fedDays = parseNonNegativeInt(parts[4], "fedDays");

        boolean ready;
        if (parts[5].equals("yes")) {
            ready = true;
        } else if (parts[5].equals("no")) {
            ready = false;
        } else {
            throw new IllegalArgumentException("ready must be 'yes' or 'no', got '" + parts[5] + "'");
        }
        pen.restoreAt(slot, love, fedDays, ready);
    }

    private void loadSoil(Farm farm, String[] parts) throws FarmException {
        requireLength(parts, 6);
        int col = parseNonNegativeInt(parts[1], "col");
        int row = parseNonNegativeInt(parts[2], "row");

        Tile tile = farm.tileAt(col, row);
        if (tile == null) {
            throw new FarmException("soil is off the map");
        }
        if (tile.getType() != TileType.SOIL) {
            tile.till();
        }

        if (parts[5].equals("wet")) {
            tile.soak();
        } else if (!parts[5].equals("dry")) {
            throw new IllegalArgumentException("soil state must be 'wet' or 'dry', got '" + parts[5] + "'");
        }

        String cropName = parts[3];
        int daysGrown = parseNonNegativeInt(parts[4], "daysGrown");

        if (cropName.equals("-")) {
            if (daysGrown != 0) {
                throw new IllegalArgumentException("empty soil must have 0 daysGrown");
            }
            return;
        }

        Crop crop = CropFactory.create(cropName);
        if (crop == null) {
            throw new IllegalArgumentException("unknown crop '" + cropName + "'");
        }
        crop.setDaysGrown(daysGrown);
        tile.plant(crop);
    }

    // --- Helper Methods ---

    private boolean isValidCropName(String name) {
        for (String validName : CropFactory.NAMES) {
            if (validName.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private void requireLength(String[] parts, int expected) {
        if (parts.length != expected) {
            throw new IllegalArgumentException(
                    "expected " + expected + " items for '" + parts[0] + "', got " + parts.length);
        }
    }

    private int parseNonNegativeInt(String value, String fieldName) {
        int num = Integer.parseInt(value);
        if (num < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative");
        }
        return num;
    }

    private int parsePositiveInt(String value, String fieldName) {
        int num = Integer.parseInt(value);
        if (num <= 0) {
            throw new IllegalArgumentException(fieldName + " must be > 0");
        }
        return num;
    }
}