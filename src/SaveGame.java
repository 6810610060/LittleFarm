import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SaveGame {
    private static final String HEADER = "little-farm save 1";
    private static final String SECRET_SALT = "LittleFarm_AntiTamper_2026_#991";
    private final Path file;
    private final Path sigFile; // ไฟล์สำหรับเก็บรหัสตรวจสอบแยกต่างหาก

    public SaveGame(String fileName) {
        this.file = Paths.get(fileName);
        this.sigFile = Paths.get("backup.sig");
    }

    public boolean exists() {
        return Files.exists(file);
    }

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
        Files.deleteIfExists(sigFile); // ลบไฟล์ตรวจสอบทิ้งด้วยเมื่อลบเซฟ
    }

    // เขียนข้อมูลลงไฟล์เซฟตามปกติ และเขียนรหัส Hash ลงไฟล์ .sig พร้อมกัน
    private void writeLine(PrintWriter out, PrintWriter sigOut, int lineNumber, String content) {
        out.println(content);
        sigOut.println(computeSignature(lineNumber, content));
    }

    public void save(Farm farm, Farmer farmer, GameClock clock) throws IOException {
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file));
                PrintWriter sigOut = new PrintWriter(Files.newBufferedWriter(sigFile))) {

            int lineNum = 1;
            writeLine(out, sigOut, lineNum++, HEADER);
            writeLine(out, sigOut, lineNum++, "day " + clock.getDay());
            writeLine(out, sigOut, lineNum++, "weather " + clock.getWeather().name());
            writeLine(out, sigOut, lineNum++, "gold " + farmer.getGold());
            writeLine(out, sigOut, lineNum++, "earned " + farmer.getTotalEarned());
            writeLine(out, sigOut, lineNum++, "can " + farmer.getCan().getWater());

            for (String name : CropFactory.NAMES) {
                writeLine(out, sigOut, lineNum++, "seeds " + name + " " + farmer.getSeedBag(name).getSeeds());
            }
            for (Map.Entry<String, Integer> entry : farmer.getBasket().asMap().entrySet()) {
                writeLine(out, sigOut, lineNum++, "basket " + entry.getKey() + " " + entry.getValue());
            }

            Villager villager = farm.getVillager();
            writeLine(out, sigOut, lineNum++,
                    "villager " + villager.getFriendship() + " " + villager.getRequestsDone());

            Request request = villager.getRequest();
            if (request != null) {
                writeLine(out, sigOut, lineNum++, "request " + request.getCropName() + " " + request.getAmount() + " "
                        + request.getReward() + " " + request.getDaysLeft());
            }

            writeLine(out, sigOut, lineNum++, "feed " + farmer.getFeed());

            for (String kind : AnimalFactory.NAMES) {
                Pen pen = farm.penFor(kind);
                for (Animal animal : pen.getAnimals()) {
                    writeLine(out, sigOut, lineNum++, "animal " + kind + " " + pen.slotOf(animal) + " "
                            + animal.getLove() + " " + animal.getFedDays()
                            + (animal.isProductReady() ? " yes" : " no"));
                }
            }

            for (int r = 0; r < Farm.ROWS; r++) {
                for (int c = 0; c < Farm.COLS; c++) {
                    Tile tile = farm.tileAt(c, r);
                    if (tile.getType() != TileType.SOIL)
                        continue;
                    Crop crop = tile.getCrop();
                    writeLine(out, sigOut, lineNum++, "soil " + c + " " + r + " "
                            + (crop == null ? "- 0" : crop.getName() + " " + crop.getDaysGrown())
                            + (tile.isWatered() ? " wet" : " dry"));
                }
            }
        }
    }

    public void loadInto(Farm farm, Farmer farmer, GameClock clock) throws IOException {
        List<String> lines = Files.readAllLines(file);
        if (lines.isEmpty()) {
            throw new IOException("Line 1: save file is empty");
        }

        // 1. ตรวจสอบว่าไฟล์รหัสตรวจสอบ (.sig) ยังอยู่ครบถ้วน
        if (!Files.exists(sigFile)) {
            throw new IOException("Save integrity file is missing or deleted");
        }
        List<String> signatures = Files.readAllLines(sigFile);

        // 2. ตรวจสอบทีละบรรทัดเทียบระหว่างไฟล์เซฟกับไฟล์ .sig
        int maxCheck = Math.min(lines.size(), signatures.size());
        for (int i = 0; i < maxCheck; i++) {
            int lineNumber = i + 1;
            String expectedSig = computeSignature(lineNumber, lines.get(i));
            if (!expectedSig.equals(signatures.get(i).trim())) {
                throw new IOException("Line " + lineNumber + " has been modified");
            }
        }

        // กรณีมีการแอบลบบรรทัดท้ายทิ้ง หรือแอบเพิ่มบรรทัดใหม่ต่อท้าย
        if (lines.size() != signatures.size()) {
            int alteredLine = maxCheck + 1;
            throw new IOException("Line " + alteredLine + " has been added or deleted");
        }

        // 3. ตรวจสอบ Header
        if (!lines.get(0).trim().equals(HEADER)) {
            throw new IOException("Line 1 has been modified (invalid header)");
        }

        int expectedSoilIndex = 0;
        Set<String> seenUniqueKeys = new HashSet<>();

        // 4. ตรวจสอบเงื่อนไขค่าติดลบและกติกาเกมเดิมทั้งหมด
        for (int i = 1; i < lines.size(); i++) {
            int lineNumber = i + 1;
            String trimmed = lines.get(i).trim();
            if (trimmed.isEmpty()) {
                throw new IOException("Line " + lineNumber + " has been modified (unexpected empty line)");
            }

            String[] parts = trimmed.split("\\s+");

            try {
                switch (parts[0]) {
                    case "day":
                        checkDuplicateKey(seenUniqueKeys, "day");
                        requireLength(parts, 2);
                        int day = parsePositiveInt(parts[1], "day");
                        if (day > 30) {
                            throw new IllegalArgumentException("day value is out of range");
                        }
                        clock.setDay(day);
                        break;

                    case "weather":
                        checkDuplicateKey(seenUniqueKeys, "weather");
                        requireLength(parts, 2);
                        try {
                            Weather.valueOf(parts[1]);
                        } catch (IllegalArgumentException e) {
                            throw new IllegalArgumentException("invalid weather type '" + parts[1] + "'");
                        }
                        clock.setWeather(Weather.valueOf(parts[1]));
                        break;

                    case "gold":
                        checkDuplicateKey(seenUniqueKeys, "gold");
                        requireLength(parts, 2);
                        int gold = parseNonNegativeInt(parts[1], "gold");
                        if (gold > 99999) {
                            throw new IllegalArgumentException("gold amount is suspiciously high");
                        }
                        farmer.setGold(gold);
                        break;

                    case "earned":
                        checkDuplicateKey(seenUniqueKeys, "earned");
                        requireLength(parts, 2);
                        farmer.setTotalEarned(parseNonNegativeInt(parts[1], "earned"));
                        break;

                    case "villager":
                        checkDuplicateKey(seenUniqueKeys, "villager");
                        requireLength(parts, 3);
                        int friendship = parseNonNegativeInt(parts[1], "friendship");
                        int requestsDone = parseNonNegativeInt(parts[2], "requestsDone");
                        farm.getVillager().restore(friendship, requestsDone);
                        break;

                    case "request":
                        checkDuplicateKey(seenUniqueKeys, "request");
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
                        checkDuplicateKey(seenUniqueKeys, "can");
                        requireLength(parts, 2);
                        farmer.getCan().setWater(parseNonNegativeInt(parts[1], "can water"));
                        break;

                    case "seeds":
                        requireLength(parts, 3);
                        checkDuplicateKey(seenUniqueKeys, "seeds_" + parts[1]);
                        if (!isValidCropName(parts[1])) {
                            throw new IllegalArgumentException("unknown seed type '" + parts[1] + "'");
                        }
                        SeedBag bag = farmer.getSeedBag(parts[1]);
                        if (bag == null) {
                            throw new IllegalArgumentException("unknown seed bag '" + parts[1] + "'");
                        }
                        bag.setSeeds(parseNonNegativeInt(parts[2], "seeds"));
                        break;

                    case "basket":
                        requireLength(parts, 3);
                        checkDuplicateKey(seenUniqueKeys, "basket_" + parts[1]);
                        int qty = parsePositiveInt(parts[2], "basket quantity");
                        farmer.getBasket().add(parts[1], qty);
                        break;

                    case "soil":
                        loadSoilStrict(farm, parts, expectedSoilIndex);
                        expectedSoilIndex++;
                        break;

                    case "feed":
                        checkDuplicateKey(seenUniqueKeys, "feed");
                        requireLength(parts, 2);
                        farmer.setFeed(parseNonNegativeInt(parts[1], "feed"));
                        break;

                    case "animal":
                        requireLength(parts, 6);
                        checkDuplicateKey(seenUniqueKeys, "animal_" + parts[1] + "_" + parts[2]);
                        loadAnimal(farm, parts);
                        break;

                    default:
                        throw new IllegalArgumentException("unrecognized or modified entry command '" + parts[0] + "'");
                }
            } catch (Exception e) {
                throw new IOException("Line " + lineNumber + " has been modified or is corrupted: " + e.getMessage(),
                        e);
            }
        }
    }

    private String computeSignature(int lineNumber, String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = SECRET_SALT + ":" + lineNumber + ":" + content;
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }

    private void checkDuplicateKey(Set<String> seenKeys, String key) {
        if (!seenKeys.add(key)) {
            throw new IllegalArgumentException("duplicate entry '" + key + "' detected");
        }
    }

    // --- Helper Methods เดิม ---

    private void loadAnimal(Farm farm, String[] parts) {
        requireLength(parts, 6);
        Pen pen = farm.penFor(parts[1]);
        if (pen == null) {
            throw new IllegalArgumentException("unknown animal type '" + parts[1] + "'");
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
            throw new IllegalArgumentException("ready state must be 'yes' or 'no', got '" + parts[5] + "'");
        }
        pen.restoreAt(slot, love, fedDays, ready);
    }

    private void loadSoilStrict(Farm farm, String[] parts, int soilIndex) throws FarmException {
        requireLength(parts, 6);
        int col = parseNonNegativeInt(parts[1], "col");
        int row = parseNonNegativeInt(parts[2], "row");

        int currentFoundIndex = 0;
        boolean matched = false;
        int expectedCol = -1;
        int expectedRow = -1;

        for (int r = 0; r < Farm.ROWS; r++) {
            for (int c = 0; c < Farm.COLS; c++) {
                Tile t = farm.tileAt(c, r);
                if (t != null && t.getType() == TileType.SOIL) {
                    if (currentFoundIndex == soilIndex) {
                        expectedCol = c;
                        expectedRow = r;
                        if (c == col && r == row) {
                            matched = true;
                        }
                    }
                    currentFoundIndex++;
                }
            }
        }

        if (!matched) {
            throw new IllegalArgumentException("soil coordinates (" + col + "," + row
                    + ") do not match expected sequence (" + expectedCol + "," + expectedRow + ")");
        }

        Tile tile = farm.tileAt(col, row);
        if (tile == null) {
            throw new FarmException("soil coordinates are off the map");
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

        if (!isValidCropName(cropName)) {
            throw new IllegalArgumentException("unknown crop name '" + cropName + "'");
        }

        Crop crop = CropFactory.create(cropName);
        if (crop == null) {
            throw new IllegalArgumentException("failed to create crop '" + cropName + "'");
        }
        crop.setDaysGrown(daysGrown);
        tile.plant(crop);
    }

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
                    "expected " + expected + " arguments, but got " + parts.length);
        }
    }

    private int parseNonNegativeInt(String value, String fieldName) {
        try {
            int num = Integer.parseInt(value);
            if (num < 0) {
                throw new IllegalArgumentException(fieldName + " cannot be negative (" + value + ")");
            }
            return num;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid numeric format for " + fieldName + " ('" + value + "')");
        }
    }

    private int parsePositiveInt(String value, String fieldName) {
        try {
            int num = Integer.parseInt(value);
            if (num <= 0) {
                throw new IllegalArgumentException(fieldName + " must be greater than 0 (" + value + ")");
            }
            return num;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid numeric format for " + fieldName + " ('" + value + "')");
        }
    }
}