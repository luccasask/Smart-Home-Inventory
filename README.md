# Smart Home Inventory

Lokalt inventariesystem för att organisera och hitta föremål i hemmet.
Teknik: Java, JavaFX och SQLite. Ingen molntjänst krävs för grundfunktionerna.

## Status: körbar testversion 0.2

Domänmodellen innehåller `Item`, `Location` och `ItemObject`. Både platser och
föremål kan vara föräldrar. Flytt skyddar mot cirkulära relationer och innehållet
behåller sin koppling till en behållare när den flyttas.

SQLite-lagring finns nu via `InventoryDatabase` och `ItemRepository`.
Databasen genererar ID:n och sparar namn, beskrivning, typ och parent-relation.
Hela hierarkin kan läsas tillbaka efter omstart. Ett JavaFX-gränssnitt finns
för registrering, sökning, bläddring, redigering, flytt och kontrollerad borttagning.
Taggar och inventarieservice kommer i en senare etapp.

## Starta Windows-versionen

Öppna leveransmappen `Hemregistret-Windows-v0.2` och dubbelklicka på
`Starta Hemregistret.cmd`. Java ingår. Behåll mapparna `app` och `runtime`
tillsammans med startfilen. Ingen installation eller internetanslutning behövs.

Startsidan visar en centrerad sökbar och en menyknapp. Skriv söktext och tryck
Enter eller använd sökknappen. Sökningen gäller hela inventariet och matchar
delsträngar i namn, hela placeringsvägen och beskrivning, utan skillnad på stora
och små bokstäver. Klicka på `+` i sökbaren för att skapa ett föremål med
söktexten som förifyllt namn; inget sparas innan du väljer Spara i formuläret.

Menyknappen öppnar en separat vy med hela hierarkin för manuell bläddring.
Gränssnittets färger följer referenspaletten: blått `#5B6D92`, ljusblått
`#D5E3E6`, beige `#F0E2D2`, varm offwhite `#EFEEE5`, terrakotta `#D18266`
och mörk text `#364042`.

Inventariet sparas i `%LOCALAPPDATA%\SmartHomeInventory\inventory.db`.
Båda Windows-versionerna använder samma inventarium och samma databasformat.
Stäng den gamla versionen innan du börjar använda den nya. Databasen följer
inte med programfilerna och ersätts inte vid paketering.

## Bygg och testa på Windows

Installera ett JDK version 21 eller senare. Ange `JAVA_HOME` till JDK-mappen
om Java inte hittas. Maven Wrapper hämtar Maven vid första körningen;
internet behövs då även för byggberoenden.

Kör från projektmappen i PowerShell:

```powershell
.\mvnw.cmd test
```

Bygg en ny Windows-leverans med medföljande Java:

```powershell
.\package-windows.ps1
```

Paketeringen kräver Windows och ett JDK med `jlink`. Välj en ny sökväg med
`-Destination` om leveransmappen redan innehåller en runtime.

## Struktur och ansvar

```text
src/main/java/se/smarthome/inventory/domain/  Domänmodell
src/main/java/se/smarthome/inventory/persistence/  SQLite och repository
src/main/java/se/smarthome/inventory/ui/  JavaFX-gränssnitt och sökfilter
src/main/resources/se/smarthome/inventory/ui/  Stilmall
src/test/java/se/smarthome/inventory/domain/  Tester för hierarkin
src/test/java/se/smarthome/inventory/persistence/  Tester med riktiga databasfiler
```

`Item` validerar namn och ändringar av parent-relationen. `Location` och
`ItemObject` ger objekten sin betydelse. Parent är den enda lagrade riktningen
för relationen; ingen separat barnlista behöver hållas synkroniserad.
Objekt utan parent ligger på rotnivån. Namn behöver inte vara unika.

`InventoryDatabase` skapar databasfilen och schema version 1. Den aktiverar
främmande nycklar för varje anslutning. `ItemRepository` hanterar SQL och
återskapar objekt i två steg: först alla objekt, sedan deras parent-relationer.
Ingen ORM eller separat databasserver behövs.

### Exempel på användning från Java

```java
var database = new InventoryDatabase(Path.of(
    System.getenv("LOCALAPPDATA"), "SmartHomeInventory", "inventory.db"));
var repository = new ItemRepository(database);

Item room = repository.save(new Location("Sovrum"));
var drawer = new ItemObject("Översta lådan");
drawer.moveTo(room);
Item savedDrawer = repository.save(drawer);

var mask = new ItemObject("Sovmask");
mask.moveTo(savedDrawer);
Item savedMask = repository.save(mask);

Item restored = repository.findById(savedMask.getId()).orElseThrow();
// restored.getParent().getParent().getName() är "Sovrum".
```

Exemplet kräver import av `java.nio.file.Path`, domänklasserna och
lagringsklasserna. Anropande kod hanterar `IOException` och `SQLException`.
Det är ett Windows-exempel; databasens sökväg väljs uttryckligen av anroparen.

### Regler för lagring

- Nya objekt har `null` som ID. `save` returnerar ett nytt objekt med SQLite-ID;
  använd returvärdet vid fortsatt arbete. Originalobjektet ändras inte.
- Spara föräldern före barnet. `save` sparar bara ett objekt åt gången.
- Läsoperationer ger nya ögonblicksbilder. Använd `findAll` om flera objekt ska
  dela samma objektinstanser och läs tillbaka efter ändringar.
- Vid flytt sparas bara behållarens parent-ID. Innehållets relationer består.
- En SQL-trigger kontrollerar cykler vid ändring av parent även om Java-objekten
  är inaktuella. Uteblivna föräldrar och borttagning av behållare med innehåll
  blockeras av främmande nycklar.
- `save` gör skrivning och återläsning i en transaktion. Ett fel rullar tillbaka
  databasändringen. Redigeringar i anroparens Java-objekt återställs inte;
  läs tillbaka objektet efter ett misslyckat sparförsök.
- `findById` läser för närvarande hela inventariet för att återställa parent-kedjan.
  Det håller första implementationen enkel; riktade frågor kan införas vid behov.

SQLite kopplas till Java med [Xerial SQLite JDBC](https://github.com/xerial/sqlite-jdbc).

## Fortsatt plan

1. Klart: domänmodell och tester för hierarkin.
2. Klart: SQLite, databasgenererade ID:n och ItemRepository.
3. Nästa: taggar och en central inventarieservice för sökning och hantering.
4. Körbar JavaFX-testversion finns; nästa gränssnittsetapp kopplar till taggar och servicen.
5. Senare: säkerhetskopiering, återställning, export och import.

SQLite-filer ska sparas separat från källkoden och aldrig checkas in i Git.
Tester använder tillfälliga databasfiler och påverkar inte något eget inventarium.
