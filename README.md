<div align="center">

<img src="app/src/main/res/drawable-xhdpi/banner.png" height="120px" alt="Baner aplikacji">

# Dziennik na Android TV

**Nieoficjalny klient Librus Synergia na Android TV i Google TV. Port aplikacji [Szkolny.eu](https://github.com/szkolny-eu/szkolny-android)**

![Android TV](https://img.shields.io/badge/Android%20TV-8%2B-3DDC84?style=for-the-badge&logo=androidtv&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![UI](https://img.shields.io/badge/UI-Compose%20for%20TV-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![E-dziennik](https://img.shields.io/badge/e--dziennik-Librus-2EA44F?style=for-the-badge)
![Licencja](https://img.shields.io/badge/licencja-GPL--3.0-blue?style=for-the-badge&logo=gnu&logoColor=white)
![Status](https://img.shields.io/badge/status-beta-yellowgreen?style=for-the-badge)

</div>

## Ważna informacja

Ta aplikacja **nie jest oficjalną aplikacją Librus** i nie jest powiązana z Librus Sp. z o.o. ani z autorami Szkolny.eu.
Korzysta z otwartego kodu [Szkolny.eu dla Androida](https://github.com/szkolny-eu/szkolny-android) i obsługuje tylko
dziennik **Librus® Synergia**.

Twórcy Szkolny.eu piszą, że **firma Librus zabroniła im** publikowania aplikacji z obsługą Librusa w Google Play.
Dlatego ta wersja **nie jest i nie będzie publikowana** w Google Play. Jest do instalacji na własnych urządzeniach,
do prywatnego użytku. **Może przestać działać** po zmianach po stronie Librusa.

Wersja na telewizory LG (webOS): [dziennik-webos](https://github.com/wadamczewski/dziennik-webos).

> [!NOTE]
> **Stan weryfikacji.** Sprawdzone: testy jednostkowe na fikcyjnych danych (11/11), logowanie na lokalnym fałszywym
> serwerze, wszystkie ekrany w emulatorze Android TV 14 w trybie demo. **Nie sprawdzone:** logowanie na prawdziwym
> koncie (ścieżka logowania jest ta sama co w działającej wersji iOS) i instalacja na fizycznym urządzeniu.

## Funkcje

Aplikacja jest do **czytania** dziennika z kanapy i obsługuje się ją pilotem.

- **Start**: plan na dziś (bieżąca lekcja podświetlona), szczęśliwy numerek, najbliższe sprawdziany, ostatnie oceny, frekwencja
- **plan lekcji** całego tygodnia w siatce, z odwołanymi lekcjami, zastępstwami i przeniesieniami
- **oceny** ze **średnimi ważonymi** (jak w Szkolny.eu), podział na semestry, oceny przewidywane i końcowe, poprawy, komentarze
- **terminarz**: nadchodzące i minione sprawdziany, kartkówki, wydarzenia
- **frekwencja**: procent obecności, statystyki, podział na przedmioty, lista nieobecności i spóźnień
- **wiadomości** (odebrane i wysłane) do czytania na dużym ekranie
- **uwagi** i **ogłoszenia** szkolne
- **kilku uczniów** na jednym urządzeniu i szybkie przełączanie (karta ucznia w menu)
- działanie **offline**: ostatnio pobrane dane są widoczne od razu, odświeżanie w tle co 30 minut
- **tryb demonstracyjny** na fikcyjnych danych (przycisk „Wypróbuj demo” na ekranie logowania)

**Pilot:** strzałki i OK, Wstecz wraca do menu, a z menu wychodzi z aplikacji. Na ekranie logowania OK na polu otwiera
klawiaturę. Jeśli pilot ma kolorowe przyciski: 🔴 odśwież, 🟢 zmień ucznia, 🟡 plan lekcji, 🔵 oceny. CH+/CH− zmienia
tydzień planu, ▶❚❚ odświeża dane.

Czego nie ma: wysyłania wiadomości, pobierania załączników i logowania Kontem Librus (e-mail). Do tego służy telefon.

## Logowanie

Logujesz się **loginem i hasłem Synergii** (np. `1234567u`), tak jak na synergia.librus.pl.

Logowanie z oryginalnego Szkolny.eu (`api.librus.pl/OAuth/Token`, `grant_type=password`) już nie działa: Librus zwraca
`unsupported_grant_type`. Dlatego aplikacja loguje się przez formularz synergia.librus.pl (`client_id 46`, jak w
librus-apix), a dane pobiera z `synergia.librus.pl/gateway/api/2.0`. Logowanie Kontem Librus (e-mail) wymaga strony
z reCAPTCHA, której nie da się wygodnie obsłużyć pilotem.

## Instalacja

1. Pobierz `dziennik-androidtv-1.0.0.apk` z [Releases](https://github.com/wadamczewski/dziennik-androidtv/releases/latest).
2. Na urządzeniu z Android TV / Google TV otwórz **Ustawienia → System** (na starszych: **Ustawienia urządzenia**)
   **→ Informacje** i kliknij 7× numer kompilacji. Potem w **Opcjach programisty** włącz **Debugowanie USB** albo
   **Debugowanie bezprzewodowe**.
3. Z komputera z [adb](https://developer.android.com/tools/releases/platform-tools):

```bash
adb connect adres-urzadzenia:5555
```

```bash
adb install -r dziennik-androidtv-1.0.0.apk
```

Możesz też wgrać APK aplikacją typu „Downloader” albo „Send files to TV”, jeśli pozwolisz jej instalować nieznane aplikacje.

APK jest podpisany kluczem debug. To wystarcza do prywatnej instalacji. Jeśli zbudujesz aplikację sam na innym komputerze,
odinstaluj najpierw poprzednią wersję, bo klucze będą różne.

## Kompilacja

Potrzebny jest **JDK 17** i Android SDK (zmienna `ANDROID_HOME` albo plik `local.properties` z `sdk.dir=…`).

```bash
./gradlew assembleRelease
```

Plik: `app/build/outputs/apk/release/app-release.apk`. Testy jednostkowe (parsery, logowanie na fałszywym serwerze,
dane fikcyjne):

```bash
./gradlew testDebugUnitTest
```

<details>
<summary>Struktura projektu</summary>

```
app/src/main/java/pl/prywatny/dziennik/tv/
  librus/   logika: logowanie Synergii, API gateway, parsery, wiadomości (port Szkolny.eu, OkHttp, Jsoup)
  data/     konta (szyfrowane kluczem Android Keystore) i pamięć podręczna danych
  ui/       ekrany Compose for TV: menu, Start, plan, oceny, terminarz, frekwencja, wiadomości, konto
app/src/test/  testy jednostkowe
fixtures/      fikcyjne odpowiedzi Librusa używane w testach
```

</details>

## Prywatność

- Aplikacja łączy się wyłącznie z `*.librus.pl`, nie z serwerami Szkolny.eu ani z innymi.
- **Hasło Synergii jest zapisywane**, bo bez niego nie da się odnowić sesji. Lista kont jest szyfrowana kluczem AES
  z Android Keystore (klucz nie opuszcza urządzenia). Kopie zapasowe aplikacji są wyłączone.
- **Konto → Usuń z urządzenia** kasuje login, hasło i pobrane dane ucznia.
- Dane w `fixtures/` i w trybie demo są fikcyjne. **Nie wklejaj w zgłoszeniach danych logowania ani prawdziwych danych uczniów.**

## Gdy coś nie działa

- **„Nieprawidłowy login lub hasło”**: zaloguj się tymi samymi danymi na synergia.librus.pl. Konto Librus (e-mail)
  nie działa, potrzebny jest login Synergii.
- **„Librus odrzucił logowanie”** lub błędy API: Librus mógł zmienić logowanie. Kod jest w `librus/Session.kt`.
- **Wiadomości się nie wczytują**: selektory HTML są w `librus/Synergia.kt`.
- Część sekcji (np. szczęśliwy numerek) może być wyłączona w szkole. Wtedy jest pusta.

## Licencja

Dziennik, tak jak Szkolny.eu, jest publikowany na licencji [GNU GPLv3](LICENSE). Możesz modyfikować kod i dystrybuować
zbudowane wersje razem z kodem źródłowym, nie możesz zmieniać licencji ani informacji o prawach autorskich.
Za opracowanie komunikacji z Librusem odpowiadają autorzy [Szkolny.eu](https://github.com/szkolny-eu/szkolny-android).

Nazwy Librus® i Android TV™ są znakami towarowymi ich właścicieli. Używamy ich wyłącznie w celu wskazania, z czym
współpracuje aplikacja.

*Kod został przygotowany z pomocą AI. Przejrzyj go przed użyciem.*
