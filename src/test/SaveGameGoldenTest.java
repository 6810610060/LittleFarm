import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

/**
 * Golden Output test for CN201 ข้อ 1.4 One Refactoring (SaveGame.save).
 *
 * It builds real game objects (Farm, Farmer, GameClock), plays a few actions through the game's own
 * methods, calls SaveGame.save() and prints exactly what was written to the save file.
 * Run it before and after the refactoring: the console output must be identical.
 *
 * Compile and run from the project folder:
 *   javac -d out src/*.java test/*.java
 *   java -cp out SaveGameGoldenTest
 */
public class SaveGameGoldenTest {

    public static void main(String[] args) throws Exception {
        Path tempFile = Files.createTempFile("littlefarm_golden_", ".txt");
        SaveGame saveGame = new SaveGame(tempFile.toString());
        try {
            caseNewGame(saveGame, tempFile);
            casePlayedGame(saveGame, tempFile);
            caseSaveLoadSave(saveGame, tempFile);
        } finally {
            Files.deleteIfExists(tempFile);   // never touches the real littlefarm_save.txt
        }
    }

    /** Case 1: a brand-new farm, saved straight away. */
    private static void caseNewGame(SaveGame saveGame, Path file) throws IOException {
        Farm farm = new Farm();
        Farmer farmer = new Farmer(2, 4, 300);
        GameClock clock = new GameClock();
        farmer.getSeedBag("Turnip").addSeeds(5);

        saveGame.save(farm, farmer, clock);
        printFile("CASE 1: new game", file);
    }

    /** Case 2: a farm after some days of play - crops, basket, request, animals and feed. */
    private static void casePlayedGame(SaveGame saveGame, Path file) throws Exception {
        Farm farm = new Farm();
        Farmer farmer = new Farmer(2, 4, 300);
        GameClock clock = new GameClock();
        Random rng = new Random(42);   // fixed seed, so every run is the same

        farmer.getSeedBag("Turnip").addSeeds(5);
        farmer.getSeedBag("Potato").addSeeds(2);
        farmer.getSeedBag("Strawberry").addSeeds(1);

        // till two grass tiles next to the soil, then plant on several plots
        farmer.use(new Hoe(), farm.tileAt(9, 6));
        farmer.use(new Hoe(), farm.tileAt(9, 7));
        farmer.use(farmer.getSeedBag("Turnip"), farm.tileAt(4, 6));
        farmer.use(farmer.getSeedBag("Turnip"), farm.tileAt(5, 6));
        farmer.use(farmer.getSeedBag("Potato"), farm.tileAt(6, 6));
        farmer.use(farmer.getSeedBag("Strawberry"), farm.tileAt(9, 6));
        farmer.use(farmer.getCan(), farm.tileAt(4, 6));
        farmer.use(farmer.getCan(), farm.tileAt(5, 6));
        farmer.use(farmer.getCan(), farm.tileAt(9, 6));

        // animals: buy, feed, and let some days pass
        farmer.addFeed(10);
        Animal chicken = farm.penFor("Chicken").buy();
        farm.penFor("Chicken").buy();
        Animal cow = farm.penFor("Cow").buy();
        for (int day = 0; day < 3; day++) {
            farmer.useFeed();
            chicken.feed();
            farmer.useFeed();
            cow.feed();
            printList("news after night " + (day + 1), farm.newDay(Weather.SUNNY, rng));
            clock.nextDay(rng);
            farmer.use(farmer.getCan(), farm.tileAt(4, 6));
        }

        // basket, money and the villager's request
        farmer.getBasket().add("Turnip", 2);
        farmer.getBasket().add("Egg", 1);
        farmer.earn(150);
        farm.getVillager().setRequest(new Request("Turnip", 3, 360, 4));
        farm.getVillager().restore(2, 1);

        saveGame.save(farm, farmer, clock);
        printFile("CASE 2: played game", file);
    }

    /** Case 3: load the case-2 file into fresh objects and save again - the text should round-trip. */
    private static void caseSaveLoadSave(SaveGame saveGame, Path file) throws IOException {
        Farm farm = new Farm();
        Farmer farmer = new Farmer(2, 4, 300);
        GameClock clock = new GameClock();

        saveGame.loadInto(farm, farmer, clock);
        saveGame.save(farm, farmer, clock);
        printFile("CASE 3: load then save again", file);
        System.out.println("describe() = " + saveGame.describe());
    }

    private static void printFile(String title, Path file) throws IOException {
        System.out.println("===== " + title + " =====");
        List<String> lines = Files.readAllLines(file);
        for (String line : lines) System.out.println(line);
        System.out.println("(" + lines.size() + " lines)");
        System.out.println();
    }

    private static void printList(String title, List<String> items) {
        System.out.println("-- " + title + ": " + items);
    }
}
