# CN201 ข้อ 1.4 One Refactoring — LittleFarm

## 1. Bad Smell ที่เลือก

**Long Method** (เมธอดยาวเกินไป ทำหลายหน้าที่ในที่เดียว)

## 2. ตำแหน่งของปัญหาใน Source Code เดิม

- ไฟล์: `src/SaveGame.java`
- คลาส: `SaveGame`
- เมธอด: `public void save(Farm farm, Farmer farmer, GameClock clock)` (บรรทัด 57–97 ในโค้ดเดิม ยาว 41 บรรทัด)

## 3. เหตุผลที่ถือว่าเป็น Bad Smell

- เมธอดเดียวบันทึกข้อมูลถึง 6 เรื่องที่ไม่เกี่ยวกัน: เวลา/อากาศ, ข้อมูลชาวนา (ทอง, บัวรดน้ำ, เมล็ด, ตะกร้า), ชาวบ้านและคำขอ, อาหารสัตว์, สัตว์ในคอก และแปลงดินทั้งแผนที่
- มี loop ซ้อนหลายชั้นปนกับ `if` ต้องอ่านทั้งก้อนถึงจะรู้ว่าบรรทัดไหนเขียนข้อมูลอะไร
- ไม่สอดคล้องกับฝั่งโหลดในคลาสเดียวกัน ซึ่งแยกงานยาก ๆ ออกเป็น `loadSoil()` และ `loadAnimal()` แล้ว
- ถ้าเกมเพิ่มข้อมูลใหม่ (เช่น สัตว์หรือเครื่องมือใหม่) เมธอดนี้จะยิ่งยาวขึ้นเรื่อย ๆ

## 4. Refactoring Technique ที่ใช้

**Extract Method** (แยกโค้ดแต่ละส่วนออกเป็นเมธอดย่อยที่มีชื่อบอกหน้าที่)

## 5. ส่วนที่แก้ไข

แก้ไฟล์เดียวคือ `src/SaveGame.java` เฉพาะเมธอด `save()` โดยย้ายโค้ดเดิมไปไว้ในเมธอด `private` ใหม่ 5 เมธอด **โดยไม่เปลี่ยนข้อความหรือลำดับบรรทัดที่เขียนลงไฟล์**

| เมธอดใหม่ | หน้าที่ | บรรทัดที่เขียนลงไฟล์ |
|---|---|---|
| `saveClock(out, clock)` | วันและสภาพอากาศ | `day`, `weather` |
| `saveFarmer(out, farmer)` | ทอง รายได้ บัวรดน้ำ เมล็ด ตะกร้า | `gold`, `earned`, `can`, `seeds`, `basket` |
| `saveVillager(out, villager)` | มิตรภาพและคำขอของชาวบ้าน | `villager`, `request` |
| `saveAnimals(out, farm)` | สัตว์ทุกตัวในทุกคอก | `animal` |
| `saveSoil(out, farm)` | แปลงดินทุกแปลง | `soil` |

บรรทัด `feed` สั้นแค่บรรทัดเดียว จึงคงไว้ใน `save()` ตามตำแหน่งเดิม เพื่อรักษาลำดับในไฟล์เซฟ (`feed` อยู่ระหว่าง `request` กับ `animal`)

**เมธอด `save()` หลัง Refactoring**

```java
public void save(Farm farm, Farmer farmer, GameClock clock) throws IOException {
    try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file))) {
        out.println(HEADER);
        saveClock(out, clock);
        saveFarmer(out, farmer);
        saveVillager(out, farm.getVillager());
        out.println("feed " + farmer.getFeed());
        saveAnimals(out, farm);
        saveSoil(out, farm);
    }
}
```

## 6. โค้ดหลัง Refactoring ดีขึ้นอย่างไร

- **อ่านง่าย:** `save()` เหลือ 11 บรรทัด อ่านแล้วเห็นลำดับของไฟล์เซฟทันที เหมือนสารบัญ
- **ชื่อเมธอดอธิบายตัวเอง:** ไม่ต้องอ่าน loop ก็รู้ว่าแต่ละส่วนทำอะไร
- **แก้ไขง่าย:** ถ้าจะเปลี่ยนวิธีบันทึกสัตว์ ก็แก้แค่ `saveAnimals()` ไม่กระทบส่วนอื่น
- **สอดคล้องกับฝั่งโหลด:** ตอนนี้มีทั้ง `saveSoil()`/`loadSoil()` และ `saveAnimals()`/`loadAnimal()` เป็นคู่กัน
- **พฤติกรรมเหมือนเดิม 100%:** ใช้โค้ดเดิมทุกบรรทัด แค่ย้ายที่อยู่

## 7. วิธีสร้าง Golden Output

1. สร้างคลาสทดสอบ `test/SaveGameGoldenTest.java` แยกจากโค้ดเกม (มี `main` ของตัวเอง ไม่ได้แก้ไฟล์เกม)
2. คลาสทดสอบสร้าง Object จริงของเกม (`Farm`, `Farmer`, `GameClock`, `SaveGame`) แล้วเรียกเมธอดจริง ไม่ได้พิมพ์ผลลัพธ์ที่เตรียมไว้ล่วงหน้า มี 3 กรณี
   - **CASE 1:** เกมใหม่ แล้วเซฟทันที
   - **CASE 2:** เล่นไปหลายวัน ได้แก่ ขุดดินด้วย `Hoe`, ปลูกพืช 3 ชนิด, รดน้ำ, ซื้อไก่ 2 ตัวและวัว 1 ตัว, ให้อาหาร, ผ่านคืน 3 คืน (`farm.newDay`, `clock.nextDay`), ใส่ของในตะกร้า, รับเงิน และตั้งคำขอของชาวบ้าน แล้วเซฟ
   - **CASE 3:** โหลดไฟล์จาก CASE 2 (`loadInto`) แล้วเซฟซ้ำ และเรียก `describe()`
3. ใช้ `new Random(42)` (กำหนด seed) ให้ผลลัพธ์เหมือนเดิมทุกครั้ง
4. เซฟลงไฟล์ชั่วคราวแล้วลบทิ้งเอง จึงไม่ยุ่งกับไฟล์เซฟจริงของผู้เล่น
5. รันกับ **Source Code เดิมก่อนแก้** แล้วบันทึกผลจาก Console ลง `test/golden_output.txt`

```
javac -d out src/*.java test/*.java
java -cp out SaveGameGoldenTest > test/golden_output.txt
```

## 8. วิธีเปรียบเทียบผลลัพธ์ก่อนและหลัง

หลัง Refactoring รันคลาสทดสอบเดิมอีกครั้ง บันทึกเป็น `test/after_output.txt` แล้วเปรียบเทียบทุกบรรทัด ทุกตัวอักษร

```
javac -d out src/*.java test/*.java
java -cp out SaveGameGoldenTest > test/after_output.txt
fc test\golden_output.txt test\after_output.txt        (Windows)
diff test/golden_output.txt test/after_output.txt       (macOS / Linux / Git Bash)
```

## 9. ผลการทดสอบ

| การตรวจสอบ | ผล |
|---|---|
| Compile ตัวเกม `javac -d out src/*.java` | ผ่าน (exit 0) |
| Compile ตัวเกม + คลาสทดสอบ | ผ่าน |
| `diff golden_output.txt after_output.txt` | ไม่พบความแตกต่าง |
| เทียบแบบ byte-by-byte (`cmp`) | เหมือนกันทุกไบต์ (92 บรรทัด) |
| SHA-256 ของทั้งสองไฟล์ | `53ed6171…bff4b8e1b` ตรงกัน |
| ทดสอบว่าเทสจับความผิดได้จริง (ลองแก้ `" dry"` เป็น `" DRY"` ในสำเนา) | ผลต่างจาก Golden ทันที แปลว่าเทสใช้งานได้จริง |
| เปิดเกมแบบ headless: สร้าง `GamePanel`, เริ่มเกมใหม่, เดิน 200 เฟรม, วาดหน้าจอ | ทำงานปกติ ไม่มี Exception วาดหน้าจอวันแรกได้ครบ |

**สรุป:** Refactoring ไม่เปลี่ยนพฤติกรรมของเกม ผลลัพธ์ตรงกับ Golden Output ทุกบรรทัด
