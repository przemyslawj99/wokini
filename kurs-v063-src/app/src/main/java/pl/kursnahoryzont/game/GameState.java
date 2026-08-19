package pl.kursnahoryzont.game;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

final class GameState {
    static final float PI = (float)Math.PI;
    static final int SHIP_SLOOP = 0;
    static final int SHIP_BRIG = 1;
    static final int SHIP_FRIGATE = 2;
    static final int SLOOP_CARGO_CAPACITY = 12;
    static final int BRIG_CARGO_CAPACITY = 30;
    static final int FRIGATE_CARGO_CAPACITY = 56;
    static final int BRIG_PRICE = 250;
    static final int FRIGATE_PRICE = 900;
    static final int TEST_GOLD_GRANT = 10000;
    static final String TEST_GOLD_CODE = "HORYZONT";
    static final int TARGET_NONE = 0;
    static final int TARGET_HELM = 1;
    static final int TARGET_HATCH = 2;
    static final int TARGET_SHEET = 3;
    static final int TARGET_HALYARD = 4;
    static final int TARGET_ANCHOR = 5;
    static final int TARGET_MOORING = 6;
    static final int TARGET_GANGWAY = 7;
    static final String[] GOODS = {"Przyprawy", "Drewno", "Tkaniny"};
    static final String[] PORT_NAMES = {"Port Brzask", "Przystań Mgieł", "Zielony Przylądek"};
    static final float[] PORT_X = {0f, 0f, 340f};
    static final float[] PORT_Z = {-35f, 420f, 180f};
    static final float[] PORT_R = {30f, 42f, 38f};
    static final int[][] PRICES = {
            {18, 42, 28},
            {52, 17, 34},
            {31, 36, 15}
    };

    volatile float moveX;
    volatile float moveY;
    volatile float cameraDeltaX;
    volatile float cameraDeltaY;

    volatile float shipX = 0f;
    volatile float shipZ = 0f;
    volatile float shipHeading = 0f;
    volatile float shipSpeed = 0f;
    volatile float shipForwardSpeed = 0f;
    volatile float shipLateralSpeed = 0f;
    volatile float rudder = 0f;
    volatile float sailTrim = 0.58f;
    volatile int shipType = SHIP_SLOOP;
    volatile boolean brigOwned = false;
    volatile boolean frigateOwned = false;
    volatile boolean sailRaised = false;
    volatile boolean oarsActive = false;
    volatile boolean anchorDropped = false;
    volatile float anchorProgress = 0f;
    volatile boolean moored = false;
    volatile int mooredPort = -1;

    volatile float playerX = 0f;
    volatile float playerZ = -4.5f;
    volatile float playerWorldX = 0f;
    volatile float playerWorldZ = -8f;
    volatile float lookYaw = 0f;
    volatile float lookPitch = 0f;
    volatile boolean atHelm = false;
    volatile boolean helmThirdPerson = false;
    volatile boolean handlingSheet = false;
    volatile boolean belowDeck = false;
    volatile int interiorDeckLevel = 0;
    volatile boolean onLand = false;
    volatile boolean hatchOpen = false;
    volatile float hatchProgress = 0f;

    volatile float windDirection = 0.72f;
    volatile float windStrength = 7.5f;
    volatile float storm = 0f;
    volatile float worldHours = 8.2f;
    volatile float fatigue = 0f;

    volatile int gold = 160;
    final int[] cargo = {0, 0, 0};
    final int[] quayCargo = {0, 0, 0};
    volatile int quayPort = -1;
    volatile int currentPort = 0;
    volatile boolean tradeOpen = false;
    volatile boolean mapOpen = false;
    volatile boolean tutorialSeen = false;
    volatile boolean paused = false;
    volatile boolean journalOpen = false;
    volatile float cameraSensitivity = 1f;
    volatile float masterVolume = 0.7f;
    volatile int graphicsQuality = 1;
    volatile int journalStage = 0;
    volatile int routeOriginPort = -1;
    volatile String message = "Obejdź kabinę i poznaj stanowiska slupa";
    volatile float messageTimer = 8f;
    volatile float saveNoticeTimer = 0f;

    private final SharedPreferences prefs;

    GameState(Context context) {
        prefs = context.getSharedPreferences("kurs_na_horyzont_save", Context.MODE_PRIVATE);
    }

    void step(float dt) {
        dt = clamp(dt, 0f, 0.05f);
        if (paused) {
            cameraDeltaX = 0f;
            cameraDeltaY = 0f;
            return;
        }
        worldHours += dt * 0.035f;
        if (worldHours >= 24f) worldHours -= 24f;
        fatigue = clamp(fatigue + dt * 0.012f, 0f, 1f);
        if (messageTimer > 0f) messageTimer -= dt;
        if (saveNoticeTimer > 0f) saveNoticeTimer -= dt;

        float weatherWave = (float)Math.sin(worldHours * 0.48f + 1.3f);
        float targetStorm = weatherWave > 0.56f
                ? clamp((weatherWave - 0.56f) * 2.25f, 0f, 1f) : 0f;
        storm += (targetStorm - storm) * dt * 0.18f;
        windStrength = 6.5f + storm * 11f + (float)Math.sin(worldHours * 0.27f) * 1.4f;
        windDirection = wrapAngle(windDirection + dt * (0.004f + storm * 0.012f));
        hatchProgress += ((hatchOpen ? 1f : 0f) - hatchProgress) * clamp(dt * 3.8f, 0f, 1f);
        anchorProgress += ((anchorDropped ? 1f : 0f) - anchorProgress) * clamp(dt * 2.2f, 0f, 1f);

        float dx = cameraDeltaX;
        float dy = cameraDeltaY;
        cameraDeltaX = 0f;
        cameraDeltaY = 0f;
        lookYaw -= dx * 0.0032f * cameraSensitivity;
        lookPitch = clamp(lookPitch - dy * 0.0026f * cameraSensitivity, -1.05f, 1.05f);

        if (atHelm) {
            rudder += (moveX - rudder) * clamp(dt * 5f, 0f, 1f);
        } else {
            rudder *= Math.max(0f, 1f - dt * 2.2f);
            if (handlingSheet) {
                sailTrim = clamp(sailTrim + moveX * dt * 0.36f, 0.05f, 1f);
            } else {
                updateWalking(dt);
            }
        }
        updateShip(dt);
    }

    private void updateShip(float dt) {
        float relativeWind = wrapAngle(windDirection - shipHeading);
        float reach = (float)Math.abs(Math.sin(relativeWind));
        float downwind = Math.max(0f, -(float)Math.cos(relativeWind));
        float upwindPenalty = (float)Math.cos(relativeWind) > 0.68f ? 0.05f : 1f;
        float trimBest = clamp(0.18f + reach * 0.68f + downwind * 0.16f, 0.12f, 1f);
        float trimEfficiency = clamp(1f - Math.abs(sailTrim - trimBest) * 1.55f, 0.06f, 1f);
        float drive = sailRaised ? (0.2f + reach * 0.65f + downwind * 0.3f)
                * upwindPenalty * trimEfficiency * windStrength * 0.92f : 0f;
        // Temporary solo rowing: independent of wind. A later crew system can gate this flag.
        float oarDrive = oarsActive ? (shipType == SHIP_FRIGATE ? 0.85f : (shipType == SHIP_BRIG ? 1.15f : 1.65f)) : 0f;
        float maxForward = shipType == SHIP_FRIGATE ? 11.4f : (shipType == SHIP_BRIG ? 10.2f : 8.6f);
        float targetForward = clamp(Math.max(drive, oarDrive), 0f, maxForward);

        if (moored) {
            shipForwardSpeed = 0f;
            shipLateralSpeed = 0f;
        } else if (anchorDropped) {
            shipForwardSpeed *= Math.max(0f, 1f - dt * 2.4f);
            shipLateralSpeed *= Math.max(0f, 1f - dt * 3.2f);
        } else {
            float sailAccel = shipType == SHIP_FRIGATE ? 0.16f : (shipType == SHIP_BRIG ? 0.20f : 0.28f);
            float oarAccel = shipType == SHIP_FRIGATE ? 0.30f : (shipType == SHIP_BRIG ? 0.46f : 0.90f);
            float accel = targetForward > shipForwardSpeed ? (oarsActive ? oarAccel : sailAccel) : 0.64f;
            shipForwardSpeed += (targetForward - shipForwardSpeed) * dt * accel;
            if (!sailRaised && !oarsActive) shipForwardSpeed *= Math.max(0f, 1f - dt * 0.13f);
            float windLeeway = (float)Math.sin(relativeWind) * windStrength
                    * (sailRaised ? 0.055f : 0.024f);
            shipLateralSpeed += (windLeeway - shipLateralSpeed) * dt * 0.22f;
            shipLateralSpeed *= Math.max(0f, 1f - dt * 0.09f);
        }

        // Keep some rudder authority through a tack so the boat does not stall dead head-to-wind.
        float steeringFlow = Math.max(shipForwardSpeed,
                oarsActive ? 1.45f : (sailRaised ? 0.72f : 0f));
        float steeringRate;
        if (shipType == SHIP_FRIGATE) steeringRate = oarsActive ? 0.040f : 0.034f;
        else if (shipType == SHIP_BRIG) steeringRate = oarsActive ? 0.052f : 0.046f;
        else steeringRate = oarsActive ? 0.085f : 0.068f;
        shipHeading = wrapAngle(shipHeading + rudder * steeringFlow * dt * steeringRate);
        float velocityX = (float)Math.sin(shipHeading) * shipForwardSpeed
                + (float)Math.cos(shipHeading) * shipLateralSpeed;
        float velocityZ = (float)Math.cos(shipHeading) * shipForwardSpeed
                - (float)Math.sin(shipHeading) * shipLateralSpeed;
        float nextX = shipX + velocityX * dt;
        float nextZ = shipZ + velocityZ * dt;
        boolean hit = false;
        for (int i = 0; i < PORT_X.length; i++) {
            float ddx = nextX - PORT_X[i];
            float ddz = nextZ - PORT_Z[i];
            float safe = PORT_R[i] + (shipType == SHIP_FRIGATE ? 5.4f : (shipType == SHIP_BRIG ? 4.2f : 2.8f));
            if (ddx * ddx + ddz * ddz < safe * safe) {
                hit = true;
                break;
            }
        }
        if (!hit || moored) {
            if (!moored) {
                shipX = nextX;
                shipZ = nextZ;
            }
        } else {
            shipForwardSpeed *= -0.08f;
            shipLateralSpeed *= 0.12f;
            showMessage("Płycizna — odpadnij od brzegu");
        }
        shipSpeed = (float)Math.sqrt(shipForwardSpeed * shipForwardSpeed
                + shipLateralSpeed * shipLateralSpeed);
    }

    private void updateWalking(float dt) {
        float mx = moveX;
        float my = moveY;
        float length = (float)Math.sqrt(mx * mx + my * my);
        if (length > 1f) { mx /= length; my /= length; }
        float speed = onLand ? 4.2f : 3.1f;
        float forwardX = (float)Math.sin(lookYaw);
        float forwardZ = (float)Math.cos(lookYaw);
        float rightX = (float)Math.cos(lookYaw);
        float rightZ = -(float)Math.sin(lookYaw);
        float vx = (forwardX * my + rightX * mx) * speed * dt;
        float vz = (forwardZ * my + rightZ * mx) * speed * dt;

        if (onLand) {
            float nx = playerWorldX + vx;
            float nz = playerWorldZ + vz;
            int island = nearestPort(nx, nz);
            if (island >= 0) {
                playerWorldX = nx;
                playerWorldZ = nz;
                currentPort = island;
            }
            return;
        }

        if (belowDeck) moveInteriorWithCollisions(vx, vz);
        else moveDeckWithCollisions(vx, vz);
    }

    private void moveDeckWithCollisions(float vx, float vz) {
        float nx = playerX + vx;
        if (validDeckPosition(nx, playerZ)) playerX = nx;
        float nz = playerZ + vz;
        if (validDeckPosition(playerX, nz)) playerZ = nz;
    }

    private boolean validDeckPosition(float x, float z) {
        float radius = 0.27f;
        if (z < -7.05f + radius || z > 7.05f - radius) return false;
        float halfWidth = 2.16f - Math.max(0f, z - 5f) * 0.18f;
        if (Math.abs(x) > halfWidth - radius) return false;
        if (insideRect(x, z, -1.55f, 1.55f, -3.75f, -1.35f, radius)) return false;
        if (insideCircle(x, z, 0f, -0.25f, 0.55f + radius)) return false;
        if (insideCircle(x, z, 0f, -5.72f, 0.78f + radius)) return false;
        if (insideCircle(x, z, 0f, 5.3f, 0.52f + radius)) return false;
        // Cargo is stored below deck. It must not create invisible obstacles on the walking deck.
        return true;
    }

    private void moveInteriorWithCollisions(float vx, float vz) {
        float halfWidth = shipType == SHIP_FRIGATE ? 2.35f : (shipType == SHIP_BRIG ? 2.02f : 1.65f);
        float minZ = shipType == SHIP_FRIGATE ? -8.0f : (shipType == SHIP_BRIG ? -6.6f : -5.65f);
        float maxZ = shipType == SHIP_FRIGATE ? 7.2f : (shipType == SHIP_BRIG ? 5.8f : 4.65f);
        float nx = clamp(playerX + vx, -halfWidth, halfWidth);
        if (!insideInteriorObstacle(nx, playerZ)) playerX = nx;
        float nz = clamp(playerZ + vz, minZ, maxZ);
        if (!insideInteriorObstacle(playerX, nz)) playerZ = nz;
    }

    private boolean insideInteriorObstacle(float x, float z) {
        float r = 0.18f;
        // Mast trunks / structural posts. Keep the central passage usable.
        if (shipType == SHIP_SLOOP) {
            return insideCircle(x, z, 0f, -0.30f, 0.40f + r);
        }
        if (shipType == SHIP_BRIG) {
            return insideCircle(x, z, 0f, -1.05f, 0.42f + r)
                    || insideCircle(x, z, 0f, 2.95f, 0.42f + r);
        }
        return insideCircle(x, z, 0f, -4.10f, 0.42f + r)
                || insideCircle(x, z, 0f, -0.10f, 0.44f + r)
                || insideCircle(x, z, 0f, 4.65f, 0.42f + r);
    }

    void interact() {
        if (tradeOpen) { tradeOpen = false; return; }
        if (mapOpen) { mapOpen = false; return; }
        if (atHelm) {
            atHelm = false;
            oarsActive = false;
            playerX = 0.95f;
            playerZ = -5.0f;
            showMessage("Puściłeś koło sterowe");
            return;
        }
        if (handlingSheet) {
            handlingSheet = false;
            showMessage("Obłożyłeś szot na knadze");
            return;
        }
        if (onLand) { interactOnLand(); return; }
        if (belowDeck) { interactBelowDeck(); return; }

        if (nearHelm()) {
            atHelm = true;
            lookYaw = 0f;
            showMessage("Koło reaguje na wychylenie drążka");
            return;
        }
        if (near(playerX, playerZ, 0f, -0.92f, 1.25f)) {
            if (!hatchOpen) {
                hatchOpen = true;
                showMessage("Otwierasz zejściówkę");
            } else if (hatchProgress > 0.72f) {
                belowDeck = true;
                interiorDeckLevel = 0;
                playerX = 0f;
                playerZ = 0.85f;
                showMessage("Zszedłeś pod pokład — " + interiorDeckName());
            }
            return;
        }
        if (near(playerX, playerZ, -1.66f, -0.15f, 0.85f)) {
            handlingSheet = true;
            showMessage("Trzymasz szot — drążek reguluje żagiel");
            return;
        }
        if (near(playerX, playerZ, 1.62f, 0.35f, 0.85f)) {
            sailRaised = !sailRaised;
            showMessage(sailRaised ? "Wybrałeś fał — żagiel postawiony"
                    : "Luzujesz fał — żagiel opuszczony");
            return;
        }
        if (near(playerX, playerZ, 0f, 4.75f, 1.1f)) { toggleAnchor(); return; }
        if (near(playerX, playerZ, 1.62f, 3.75f, 0.9f)) { toggleMooring(); return; }
        if (moored && nearEitherGangway(playerX, playerZ)) { disembark(); return; }
        showMessage("Szukaj oznaczonej liny, steru, zejściówki, kotwicy lub trapu");
    }

    private void interactBelowDeck() {
        if (near(playerX, playerZ, 0f, 0.85f, 1.15f)) {
            if (interiorDeckLevel <= 0) {
                belowDeck = false;
                interiorDeckLevel = 0;
                playerX = 0f;
                playerZ = 0.75f;
                showMessage("Wyszedłeś na pokład górny");
            } else {
                interiorDeckLevel--;
                playerX = 0f;
                playerZ = 0.85f;
                showMessage("Wchodzisz wyżej — " + interiorDeckName());
            }
            return;
        }
        if (interiorDeckLevel < maxInteriorDeckLevel()
                && near(playerX, playerZ, 0f, 3.85f, 1.05f)) {
            interiorDeckLevel++;
            playerX = 0f;
            playerZ = 2.95f;
            showMessage("Schodzisz niżej — " + interiorDeckName());
            return;
        }
        boolean sleepDeck = shipType == SHIP_SLOOP
                || (shipType == SHIP_BRIG && interiorDeckLevel == 0)
                || (shipType == SHIP_FRIGATE && interiorDeckLevel == 1);
        if (sleepDeck && playerZ < -3.0f) {
            worldHours += 7.5f;
            if (worldHours >= 24f) worldHours -= 24f;
            fatigue = 0f;
            showMessage("Odpocząłeś w hamaku");
            saveAndNotify();
        } else {
            showMessage(interiorDeckName() + " — drabinka w górę przy zejściówce"
                    + (interiorDeckLevel < maxInteriorDeckLevel() ? ", zejście niżej na dziobie" : ""));
        }
    }

    int maxInteriorDeckLevel() {
        if (shipType == SHIP_FRIGATE) return 2;
        if (shipType == SHIP_BRIG) return 1;
        return 0;
    }

    String interiorDeckName() {
        if (shipType == SHIP_SLOOP) return "Ładownia";
        if (shipType == SHIP_BRIG) return interiorDeckLevel == 0 ? "Pokład działowy" : "Ładownia";
        if (interiorDeckLevel == 0) return "Pokład działowy";
        if (interiorDeckLevel == 1) return "Pokład mieszkalny";
        return "Ładownia dolna";
    }

    private void interactOnLand() {
        int p = nearestPort(playerWorldX, playerWorldZ);
        if (p >= 0) currentPort = p;
        if (p >= 0 && near(playerWorldX, playerWorldZ, cargoPointX(p), cargoPointZ(p), 4.2f)) {
            if (quayPort == p && quayCargoTotal() > 0) loadCargoFromQuay();
            else if (cargoTotal() > 0) unloadCargoToQuay(p);
            else showMessage("Przy magazynie nie ma ładunku");
            return;
        }
        if (p >= 0 && near(playerWorldX, playerWorldZ, marketX(p), marketZ(p), 8.5f)) {
            tradeOpen = true;
            showMessage("Targ: " + PORT_NAMES[p]);
            return;
        }
        float dsx = playerWorldX - shipX;
        float dsz = playerWorldZ - shipZ;
        if (dsx * dsx + dsz * dsz < 70f) {
            onLand = false;
            belowDeck = false;
            float side = preferredBoardingSide();
            placePlayerSafelyOnDeck(side * 1.55f, 1.55f);
            lookYaw = 0f;
            showMessage("Wróciłeś trapem na pokład");
            return;
        }
        showMessage("Wróć do statku, magazynu na kei albo budynku targu");
    }

    private void toggleAnchor() {
        if (moored) {
            showMessage("Najpierw oddaj cumy na knadze przy prawej burcie");
            return;
        }
        anchorDropped = !anchorDropped;
        showMessage(anchorDropped ? "Kotwica rzucona — statek wytraca ruch"
                : "Kotwica wybrana na pokład");
    }

    private void toggleMooring() {
        if (moored) {
            moored = false;
            mooredPort = -1;
            showMessage("Cumy oddane");
            return;
        }
        int p = dockablePort();
        if (p < 0 || shipSpeed > 0.55f) {
            showMessage("Podejdź bliżej kei i zatrzymaj " + shipName().toLowerCase(Locale.ROOT));
            return;
        }
        moored = true;
        mooredPort = p;
        anchorDropped = false;
        shipForwardSpeed = 0f;
        shipLateralSpeed = 0f;
        shipSpeed = 0f;
        currentPort = p;
        showMessage(shipName() + " zacumowany w " + PORT_NAMES[p]);
        saveAndNotify();
    }

    private void disembark() {
        if (!moored || mooredPort < 0) {
            showMessage("Najpierw zacumuj przy kei");
            return;
        }
        currentPort = mooredPort;
        onLand = true;
        belowDeck = false;
        updateJournalFromLocation();
        float vx = shipX - PORT_X[currentPort];
        float vz = shipZ - PORT_Z[currentPort];
        float len = Math.max(0.001f, (float)Math.sqrt(vx * vx + vz * vz));
        playerWorldX = PORT_X[currentPort] + vx / len * (PORT_R[currentPort] + 1.5f);
        playerWorldZ = PORT_Z[currentPort] + vz / len * (PORT_R[currentPort] + 1.5f);
        lookYaw = shipHeading + PI;
        showMessage("Zszedłeś trapem na " + PORT_NAMES[currentPort]);
    }

    String actionLabel() {
        if (tradeOpen || mapOpen) return "ZAMKNIJ";
        if (atHelm) return "PUŚĆ KOŁO";
        if (handlingSheet) return "OBŁÓŻ SZOT";
        if (onLand) {
            int p = nearestPort(playerWorldX, playerWorldZ);
            if (p >= 0 && near(playerWorldX, playerWorldZ, cargoPointX(p), cargoPointZ(p), 4.2f))
                return quayPort == p && quayCargoTotal() > 0 ? "ZAŁADUJ" : "WYŁADUJ";
            if (p >= 0 && near(playerWorldX, playerWorldZ, marketX(p), marketZ(p), 8.5f)) return "HANDEL";
            float dx = playerWorldX - shipX;
            float dz = playerWorldZ - shipZ;
            if (dx * dx + dz * dz < 70f) return "NA STATEK";
            return "AKCJA";
        }
        if (belowDeck) {
            if (near(playerX, playerZ, 0f, 0.85f, 1.15f))
                return interiorDeckLevel == 0 ? "NA POKŁAD" : "WYŻEJ";
            if (interiorDeckLevel < maxInteriorDeckLevel()
                    && near(playerX, playerZ, 0f, 3.85f, 1.05f)) return "NIŻEJ";
            boolean sleepDeck = shipType == SHIP_SLOOP
                    || (shipType == SHIP_BRIG && interiorDeckLevel == 0)
                    || (shipType == SHIP_FRIGATE && interiorDeckLevel == 1);
            if (sleepDeck && playerZ < -3.0f) return "ŚPIJ";
            return "AKCJA";
        }
        if (nearHelm()) return "PRZEJMIJ KOŁO";
        if (near(playerX, playerZ, 0f, -0.92f, 1.25f)) return hatchOpen ? "ZEJDŹ" : "OTWÓRZ";
        if (near(playerX, playerZ, -1.66f, -0.15f, 0.85f)) return "CHWYĆ SZOT";
        if (near(playerX, playerZ, 1.62f, 0.35f, 0.85f)) return sailRaised ? "LUZUJ FAŁ" : "WYBIERZ FAŁ";
        if (near(playerX, playerZ, 0f, 4.75f, 1.1f)) return anchorDropped ? "WYBIERZ KOTWICĘ" : "RZUĆ KOTWICĘ";
        if (near(playerX, playerZ, 1.62f, 3.75f, 0.9f)) return moored ? "ODDAJ CUMY" : "ZACUMUJ";
        if (moored && nearEitherGangway(playerX, playerZ)) return "ZEJDŹ NA LĄD";
        return "AKCJA";
    }

    void toggleSail() {
        if (!atHelm) return;
        sailRaised = !sailRaised;
        showMessage(sailRaised ? "Żagiel postawiony" : "Żagiel zrzucony");
    }

    void toggleOars() {
        if (!atHelm) return;
        if (shipType == SHIP_FRIGATE) {
            showMessage("Fregata wymaga większej załogi do pracy na wiosłach");
            return;
        }
        if (moored) {
            showMessage("Oddaj cumy przed wiosłowaniem");
            return;
        }
        if (anchorDropped) {
            showMessage("Wybierz kotwicę przed wiosłowaniem");
            return;
        }
        oarsActive = !oarsActive;
        showMessage(oarsActive ? "Wiosła pracują — napęd niezależny od wiatru"
                : "Wiosła zatrzymane");
    }

    void trimSail(float amount) {
        if (!atHelm) return;
        sailTrim = clamp(sailTrim + amount, 0.05f, 1f);
        showMessage(amount > 0f ? "Wybierasz szot" : "Luzujesz szot");
    }

    void toggleHelmView() {
        if (!atHelm) return;
        helmThirdPerson = !helmThirdPerson;
        lookYaw = 0f;
        lookPitch = 0f;
        showMessage(helmThirdPerson ? "Widok zza statku" : "Widok z pokładu");
    }

    synchronized void buy(int good) {
        if (!tradeOpen || good < 0 || good >= GOODS.length) return;
        if (quayPort >= 0 && quayPort != currentPort && quayCargoTotal() > 0) {
            showMessage("Towar czeka na kei w " + PORT_NAMES[quayPort]);
            return;
        }
        int price = PRICES[currentPort][good];
        if (cargoTotal() + quayCargoTotal() >= cargoCapacity()) {
            showMessage("Ładownia i partia na kei są pełne");
            return;
        }
        if (gold < price) { showMessage("Brakuje monet"); return; }
        gold -= price;
        quayPort = currentPort;
        quayCargo[good]++;
        if (journalStage == 0 || journalStage == 5) {
            journalStage = 1;
            routeOriginPort = currentPort;
        }
        showMessage("Kupiono: " + GOODS[good] + " — odbierz z magazynu na kei");
        save();
    }

    synchronized void sell(int good) {
        if (!tradeOpen || good < 0 || good >= GOODS.length) return;
        if (quayPort != currentPort || quayCargo[good] <= 0) {
            showMessage("Najpierw wyładuj ten towar w magazynie na kei");
            return;
        }
        quayCargo[good]--;
        gold += PRICES[currentPort][good];
        if (quayCargoTotal() == 0) {
            quayPort = -1;
            journalStage = 5;
        }
        showMessage("Sprzedano: " + GOODS[good]);
        save();
    }

    private synchronized void loadCargoFromQuay() {
        int room = cargoCapacity() - cargoTotal();
        int moved = 0;
        for (int i = 0; i < cargo.length && room > 0; i++) {
            int amount = Math.min(room, quayCargo[i]);
            cargo[i] += amount;
            quayCargo[i] -= amount;
            moved += amount;
            room -= amount;
        }
        if (quayCargoTotal() == 0) quayPort = -1;
        if (moved > 0) journalStage = 2;
        showMessage(moved > 0 ? "Załadowano " + moved + " szt. do ładowni" : "Brak miejsca w ładowni");
        save();
    }

    private synchronized void unloadCargoToQuay(int port) {
        int moved = cargoTotal();
        if (moved <= 0) { showMessage("Ładownia jest pusta"); return; }
        quayPort = port;
        for (int i = 0; i < cargo.length; i++) {
            quayCargo[i] += cargo[i];
            cargo[i] = 0;
        }
        if (moved > 0) journalStage = 4;
        showMessage("Wyładowano " + moved + " szt. do magazynu na kei");
        save();
    }

    int cargoCapacity() {
        if (shipType == SHIP_FRIGATE) return FRIGATE_CARGO_CAPACITY;
        return shipType == SHIP_BRIG ? BRIG_CARGO_CAPACITY : SLOOP_CARGO_CAPACITY;
    }

    String shipName() {
        if (shipType == SHIP_FRIGATE) return "Fregata";
        return shipType == SHIP_BRIG ? "Bryg" : "Slup";
    }

    boolean redeemTestGoldCode(String code) {
        if (code != null && TEST_GOLD_CODE.equalsIgnoreCase(code.trim())) {
            gold += TEST_GOLD_GRANT;
            showMessage("Kod testowy: +" + TEST_GOLD_GRANT + " monet");
            save();
            return true;
        }
        showMessage("Nieprawidłowy kod testowy");
        return false;
    }

    float shipScaleX() {
        if (shipType == SHIP_FRIGATE) return 1.28f;
        return shipType == SHIP_BRIG ? 1.18f : 1f;
    }

    float shipScaleY() {
        if (shipType == SHIP_FRIGATE) return 1.40f;
        return shipType == SHIP_BRIG ? 1.28f : 1f;
    }

    float shipScaleZ() {
        if (shipType == SHIP_FRIGATE) return 1.95f;
        return shipType == SHIP_BRIG ? 1.62f : 1f;
    }

    String brigShipyardButtonText() {
        if (!brigOwned) return "KUP BRYG";
        return shipType == SHIP_BRIG ? "WRÓĆ DO SLUPA" : "WYBIERZ BRYG";
    }

    String frigateShipyardButtonText() {
        if (!frigateOwned) return "KUP FREGATĘ";
        return shipType == SHIP_FRIGATE ? "WRÓĆ DO SLUPA" : "WYBIERZ FREGATĘ";
    }

    synchronized void brigShipyardAction() {
        if (!tradeOpen || !onLand) return;
        if (!brigOwned) {
            if (gold < BRIG_PRICE) {
                showMessage("Brakuje monet na bryg");
                return;
            }
            gold -= BRIG_PRICE;
            brigOwned = true;
            shipType = SHIP_BRIG;
            oarsActive = false;
            atHelm = false;
            showMessage("Kupiono bryg — stoi przy kei i jest teraz aktywny");
            save();
            return;
        }
        if (shipType == SHIP_BRIG) {
            if (cargoTotal() > SLOOP_CARGO_CAPACITY) {
                showMessage("Slup nie pomieści obecnego ładunku");
                return;
            }
            shipType = SHIP_SLOOP;
            showMessage("Wybrano slup");
        } else {
            shipType = SHIP_BRIG;
            showMessage("Wybrano bryg");
        }
        oarsActive = false;
        atHelm = false;
        save();
    }

    synchronized void frigateShipyardAction() {
        if (!tradeOpen || !onLand) return;
        if (!frigateOwned) {
            if (gold < FRIGATE_PRICE) {
                showMessage("Brakuje monet na fregatę");
                return;
            }
            gold -= FRIGATE_PRICE;
            frigateOwned = true;
            shipType = SHIP_FRIGATE;
            oarsActive = false;
            atHelm = false;
            showMessage("Kupiono fregatę — stoi przy kei i jest teraz aktywna");
            save();
            return;
        }
        if (shipType == SHIP_FRIGATE) {
            if (cargoTotal() > SLOOP_CARGO_CAPACITY) {
                showMessage("Slup nie pomieści obecnego ładunku");
                return;
            }
            shipType = SHIP_SLOOP;
            showMessage("Wybrano slup");
        } else {
            shipType = SHIP_FRIGATE;
            showMessage("Wybrano fregatę");
        }
        oarsActive = false;
        atHelm = false;
        save();
    }

    int cargoTotal() { return cargo[0] + cargo[1] + cargo[2]; }
    int quayCargoTotal() { return quayCargo[0] + quayCargo[1] + quayCargo[2]; }

    int dockablePort() {
        if (shipSpeed > 0.8f) return -1;
        for (int i = 0; i < PORT_X.length; i++) {
            float dx = shipX - PORT_X[i];
            float dz = shipZ - PORT_Z[i];
            float max = PORT_R[i] + 15f;
            if (dx * dx + dz * dz < max * max) return i;
        }
        return -1;
    }

    int nearestPort(float x, float z) {
        for (int i = 0; i < PORT_X.length; i++) {
            float dx = x - PORT_X[i];
            float dz = z - PORT_Z[i];
            float max = PORT_R[i] + 8f;
            if (dx * dx + dz * dz < max * max) return i;
        }
        return -1;
    }

    float distanceToPort(int p) {
        float dx = shipX - PORT_X[p];
        float dz = shipZ - PORT_Z[p];
        return (float)Math.sqrt(dx * dx + dz * dz);
    }

    float marketX(int p) { return PORT_X[p] - 4.8f; }
    float marketZ(int p) { return PORT_Z[p] + dockSign(p) * (PORT_R[p] * 0.42f); }
    float cargoPointX(int p) { return PORT_X[p] + 1.25f; }
    float cargoPointZ(int p) { return PORT_Z[p] + dockSign(p) * (PORT_R[p] + 1.5f); }
    float dockSign(int p) { return p == 1 ? -1f : 1f; }

    float relativeWind() { return wrapAngle(windDirection - shipHeading); }
    float sailBillow() {
        if (!sailRaised) return 0f;
        float pressure = (float)Math.abs(Math.sin(relativeWind())) * 0.7f
                + Math.max(0f, -(float)Math.cos(relativeWind())) * 0.45f;
        return clamp(pressure * (0.5f + windStrength / 24f), 0.08f, 1f);
    }

    void showMessage(String value) { message = value; messageTimer = 4.5f; }

    String headingName() {
        float deg = shipHeading * 180f / PI;
        while (deg < 0f) deg += 360f;
        while (deg >= 360f) deg -= 360f;
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return names[((int)((deg + 22.5f) / 45f)) & 7];
    }

    String timeText() {
        int h = (int)worldHours;
        int m = (int)((worldHours - h) * 60f);
        return String.format(Locale.US, "%02d:%02d", h, m);
    }

    String weatherText() {
        if (storm > 0.62f) return "sztorm";
        if (storm > 0.2f) return "szkwały";
        return "spokojnie";
    }

    int interactionTarget() {
        if (paused || journalOpen || tradeOpen || mapOpen || onLand || belowDeck || atHelm || handlingSheet)
            return TARGET_NONE;
        if (nearHelm()) return TARGET_HELM;
        if (near(playerX, playerZ, 0f, -0.92f, 1.25f)) return TARGET_HATCH;
        if (near(playerX, playerZ, -1.66f, -0.15f, 0.85f)) return TARGET_SHEET;
        if (near(playerX, playerZ, 1.62f, 0.35f, 0.85f)) return TARGET_HALYARD;
        if (near(playerX, playerZ, 0f, 4.75f, 1.1f)) return TARGET_ANCHOR;
        if (near(playerX, playerZ, 1.62f, 3.75f, 0.9f)) return TARGET_MOORING;
        if (moored && nearEitherGangway(playerX, playerZ)) return TARGET_GANGWAY;
        return TARGET_NONE;
    }

    String nearbyStationLabel() {
        if (onLand) {
            int p = nearestPort(playerWorldX, playerWorldZ);
            if (p >= 0 && near(playerWorldX, playerWorldZ,
                    cargoPointX(p), cargoPointZ(p), 5.2f)) return "MAGAZYN KEI";
            if (p >= 0 && near(playerWorldX, playerWorldZ,
                    marketX(p), marketZ(p), 9.5f)) return "TARG";
        }
        switch (interactionTarget()) {
            case TARGET_HELM: return "KOŁO STEROWE";
            case TARGET_HATCH: return "ZEJŚCIÓWKA";
            case TARGET_SHEET: return "SZOT GROTA";
            case TARGET_HALYARD: return "FAŁ GROTA";
            case TARGET_ANCHOR: return "KABESTAN KOTWICZNY";
            case TARGET_MOORING: return "KNAGA CUMOWNICZA";
            case TARGET_GANGWAY: return "TRAP";
            default: return "";
        }
    }

    String journalObjective() {
        switch (journalStage) {
            case 0: return "Kup towar na targu";
            case 1: return "Odbierz zakup z magazynu i załaduj slup";
            case 2: return "Oddaj cumy i dopłyń do innego portu";
            case 3: return "Zejdź na ląd przy magazynie kei";
            case 4: return "Wyładuj towar i sprzedaj go na targu";
            default: return "Trasa zakończona — możesz rozpocząć następny handel";
        }
    }

    void updateJournalFromLocation() {
        if (journalStage == 2 && onLand && currentPort != routeOriginPort) journalStage = 3;
    }

    void togglePause() {
        paused = !paused;
        journalOpen = false;
        moveX = 0f;
        moveY = 0f;
    }

    void adjustSensitivity(float delta) {
        cameraSensitivity = clamp(cameraSensitivity + delta, 0.45f, 1.8f);
        save();
    }

    void adjustVolume(float delta) {
        masterVolume = clamp(masterVolume + delta, 0f, 1f);
        save();
    }

    void cycleQuality() {
        graphicsQuality = (graphicsQuality + 1) % 3;
        save();
    }

    String qualityText() {
        return graphicsQuality == 0 ? "NISKA" : graphicsQuality == 1 ? "ŚREDNIA" : "WYSOKA";
    }

    void load() {
        boolean migratedFromPrototype = !prefs.contains("saveSchemaV2");
        shipX = prefs.getFloat("shipX", shipX);
        shipZ = prefs.getFloat("shipZ", shipZ);
        shipHeading = prefs.getFloat("shipHeading", shipHeading);
        shipForwardSpeed = prefs.getFloat("shipForwardSpeed", 0f);
        shipLateralSpeed = prefs.getFloat("shipLateralSpeed", 0f);
        sailTrim = prefs.getFloat("sailTrim", sailTrim);
        shipType = prefs.getInt("shipType", SHIP_SLOOP);
        brigOwned = prefs.getBoolean("brigOwned", false);
        frigateOwned = prefs.getBoolean("frigateOwned", false);
        if (shipType == SHIP_BRIG && !brigOwned) shipType = SHIP_SLOOP;
        if (shipType == SHIP_FRIGATE && !frigateOwned) shipType = brigOwned ? SHIP_BRIG : SHIP_SLOOP;
        sailRaised = prefs.getBoolean("sailRaised", false);
        anchorDropped = prefs.getBoolean("anchorDropped", false);
        moored = prefs.getBoolean("moored", false);
        mooredPort = prefs.getInt("mooredPort", -1);
        hatchOpen = prefs.getBoolean("hatchOpen", false);
        worldHours = prefs.getFloat("worldHours", worldHours);
        windDirection = prefs.getFloat("windDirection", windDirection);
        windStrength = prefs.getFloat("windStrength", windStrength);
        storm = prefs.getFloat("storm", storm);
        gold = prefs.getInt("gold", gold);
        quayPort = prefs.getInt("quayPort", -1);
        for (int i = 0; i < cargo.length; i++) {
            cargo[i] = prefs.getInt("cargo" + i, 0);
            quayCargo[i] = prefs.getInt("quayCargo" + i, 0);
        }
        tutorialSeen = prefs.getBoolean("tutorialSeenV2", false);
        cameraSensitivity = prefs.getFloat("cameraSensitivity", 1f);
        masterVolume = prefs.getFloat("masterVolume", 0.7f);
        graphicsQuality = prefs.getInt("graphicsQuality", 1);
        helmThirdPerson = prefs.getBoolean("helmThirdPerson", false);
        journalStage = prefs.getInt("journalStage", 0);
        routeOriginPort = prefs.getInt("routeOriginPort", -1);
        if (migratedFromPrototype && Math.abs(shipX) < 1.5f && Math.abs(shipZ) < 2f) {
            shipX = 5.2f;
            shipZ = 0f;
        }
        playerX = 0f;
        playerZ = -4.5f;
        onLand = false;
        atHelm = false;
        oarsActive = false;
        handlingSheet = false;
        belowDeck = false;
        interiorDeckLevel = 0;
        hatchProgress = hatchOpen ? 1f : 0f;
        anchorProgress = anchorDropped ? 1f : 0f;
        shipSpeed = (float)Math.sqrt(shipForwardSpeed * shipForwardSpeed
                + shipLateralSpeed * shipLateralSpeed);
    }

    synchronized void save() {
        SharedPreferences.Editor editor = prefs.edit()
                .putFloat("shipX", shipX).putFloat("shipZ", shipZ)
                .putFloat("shipHeading", shipHeading)
                .putFloat("shipForwardSpeed", shipForwardSpeed)
                .putFloat("shipLateralSpeed", shipLateralSpeed)
                .putFloat("sailTrim", sailTrim).putBoolean("sailRaised", sailRaised)
                .putInt("shipType", shipType).putBoolean("brigOwned", brigOwned)
                .putBoolean("frigateOwned", frigateOwned)
                .putBoolean("anchorDropped", anchorDropped).putBoolean("moored", moored)
                .putInt("mooredPort", mooredPort).putBoolean("hatchOpen", hatchOpen)
                .putFloat("worldHours", worldHours).putFloat("windDirection", windDirection)
                .putFloat("windStrength", windStrength).putFloat("storm", storm)
                .putInt("gold", gold).putInt("quayPort", quayPort)
                .putBoolean("tutorialSeenV2", tutorialSeen)
                .putBoolean("saveSchemaV2", true)
                .putFloat("cameraSensitivity", cameraSensitivity)
                .putFloat("masterVolume", masterVolume)
                .putInt("graphicsQuality", graphicsQuality)
                .putBoolean("helmThirdPerson", helmThirdPerson)
                .putInt("journalStage", journalStage)
                .putInt("routeOriginPort", routeOriginPort);
        for (int i = 0; i < cargo.length; i++) {
            editor.putInt("cargo" + i, cargo[i]);
            editor.putInt("quayCargo" + i, quayCargo[i]);
        }
        editor.apply();
    }

    synchronized void saveAndNotify() {
        save();
        saveNoticeTimer = 3.2f;
    }

    private static boolean near(float x, float z, float tx, float tz, float radius) {
        float dx = x - tx;
        float dz = z - tz;
        return dx * dx + dz * dz <= radius * radius;
    }

    private boolean nearEitherGangway(float x, float z) {
        return near(x, z, -1.62f, 2.1f, 1.0f) || near(x, z, 1.62f, 2.1f, 1.0f);
    }

    private float preferredBoardingSide() {
        int p = mooredPort >= 0 ? mooredPort : currentPort;
        if (p < 0 || p >= PORT_X.length) return -1f;
        float toPortX = PORT_X[p] - shipX;
        float toPortZ = PORT_Z[p] - shipZ;
        float localX = toPortX * (float)Math.cos(shipHeading)
                - toPortZ * (float)Math.sin(shipHeading);
        return localX >= 0f ? 1f : -1f;
    }

    private void placePlayerSafelyOnDeck(float preferredX, float preferredZ) {
        float[][] candidates = {
                {preferredX, preferredZ},
                {preferredX * 0.72f, 1.25f},
                {0f, 1.55f},
                {-preferredX * 0.72f, 1.25f},
                {0f, 0.95f}
        };
        for (float[] candidate : candidates) {
            if (validDeckPosition(candidate[0], candidate[1])) {
                playerX = candidate[0];
                playerZ = candidate[1];
                return;
            }
        }
        // Last-resort known clear position, retained only for malformed future deck layouts.
        playerX = 0f;
        playerZ = 1.55f;
    }

    private boolean nearHelm() {
        return near(playerX, playerZ, 0f, -5.35f, 2.15f);
    }

    private static boolean insideRect(float x, float z, float minX, float maxX,
                                      float minZ, float maxZ, float margin) {
        return x > minX - margin && x < maxX + margin
                && z > minZ - margin && z < maxZ + margin;
    }

    private static boolean insideCircle(float x, float z, float cx, float cz, float radius) {
        float dx = x - cx;
        float dz = z - cz;
        return dx * dx + dz * dz < radius * radius;
    }

    static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    static float wrapAngle(float a) {
        while (a > PI) a -= PI * 2f;
        while (a < -PI) a += PI * 2f;
        return a;
    }
}
