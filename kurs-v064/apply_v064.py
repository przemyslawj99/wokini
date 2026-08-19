from pathlib import Path
import re

ROOT = Path('build-project')
GS = ROOT / 'app/src/main/java/pl/kursnahoryzont/game/GameState.java'
WR = ROOT / 'app/src/main/java/pl/kursnahoryzont/game/WorldRenderer.java'
BG = ROOT / 'app/build.gradle'


def sub_once(text, pattern, repl, label, flags=re.S):
    new, count = re.subn(pattern, repl, text, count=1, flags=flags)
    if count != 1:
        raise RuntimeError(f'{label}: expected 1 replacement, got {count}')
    return new

# --- version ---
bg = BG.read_text()
bg = bg.replace('versionCode 26', 'versionCode 27')
bg = bg.replace("versionName '0.6.3'", "versionName '0.6.4'")
if "versionCode 27" not in bg or "versionName '0.6.4'" not in bg:
    raise RuntimeError('version update failed')
BG.write_text(bg)

# --- GameState: ship-specific deck collisions, safe spawns and interaction positions ---
gs = GS.read_text()

gs = gs.replace(
'''        if (belowDeck) moveInteriorWithCollisions(vx, vz);
        else moveDeckWithCollisions(vx, vz);''',
'''        if (!belowDeck && !validDeckPosition(playerX, playerZ)) {
            placePlayerSafelyOnDeck(helmExitX(), helmZ() + 1.20f);
        }
        if (belowDeck) moveInteriorWithCollisions(vx, vz);
        else moveDeckWithCollisions(vx, vz);''')

collision_block = r'''    private boolean validDeckPosition\(float x, float z\) \{.*?\n    \}\n\n    private void moveInteriorWithCollisions'''
collision_repl = '''    private boolean validDeckPosition(float x, float z) {
        float radius = 0.24f;
        float minZ = shipType == SHIP_FRIGATE ? -11.55f : (shipType == SHIP_BRIG ? -8.75f : -7.05f);
        float maxZ = shipType == SHIP_FRIGATE ? 11.10f : (shipType == SHIP_BRIG ? 8.45f : 7.05f);
        if (z < minZ + radius || z > maxZ - radius) return false;
        float halfWidth = deckHalfWidthForCollision(z);
        if (Math.abs(x) > halfWidth - radius) return false;

        if (shipType == SHIP_SLOOP) {
            if (insideRect(x, z, -1.50f, 1.50f, -3.72f, -1.34f, radius)) return false;
            if (insideCircle(x, z, 0f, -0.25f, 0.46f + radius)) return false;
        } else if (shipType == SHIP_BRIG) {
            // Low companionway only; both side decks and the route to the helm stay open.
            if (insideRect(x, z, -0.92f, 0.92f, -3.78f, -1.72f, radius)) return false;
            if (insideCircle(x, z, 0f, -1.05f, 0.43f + radius)) return false;
            if (insideCircle(x, z, 0f, 2.95f, 0.42f + radius)) return false;
        } else {
            // The frigate has a wide weather deck. The stern cabin is below the quarterdeck,
            // so only the small companionway blocks the walking surface.
            if (insideRect(x, z, -1.00f, 1.00f, -4.36f, -2.02f, radius)) return false;
            if (insideCircle(x, z, 0f, -4.10f, 0.42f + radius)) return false;
            if (insideCircle(x, z, 0f, -0.10f, 0.44f + radius)) return false;
            if (insideCircle(x, z, 0f, 4.65f, 0.42f + radius)) return false;
        }
        if (insideCircle(x, z, 0f, anchorControlZ() + 0.55f, 0.38f + radius)) return false;
        return true;
    }

    private float deckHalfWidthForCollision(float z) {
        if (shipType == SHIP_FRIGATE) {
            if (z < -11.0f) return 3.08f + (z + 11.0f) * 0.27f;
            if (z < -9.0f) return 3.08f + (z + 11.0f) * 0.19f;
            if (z < -6.3f) return 3.46f + (z + 9.0f) * 0.089f;
            if (z < 1.2f) return 3.72f;
            if (z < 5.2f) return 3.74f - (z - 1.2f) * 0.10f;
            if (z < 8.5f) return 3.34f - (z - 5.2f) * 0.224f;
            if (z < 10.8f) return 2.60f - (z - 8.5f) * 0.574f;
            return Math.max(0.72f, 1.28f - (z - 10.8f) * 0.70f);
        }
        if (shipType == SHIP_BRIG) {
            if (z < -8.0f) return 2.72f + (z + 8.0f) * 0.31f;
            if (z < -6.4f) return 2.72f + (z + 8.0f) * 0.15f;
            if (z < -4.0f) return 2.96f + (z + 6.4f) * 0.042f;
            if (z < -0.8f) return 3.06f - (z + 4.0f) * 0.013f;
            if (z < 2.8f) return 3.02f - (z + 0.8f) * 0.083f;
            if (z < 5.9f) return 2.72f - (z - 2.8f) * 0.20f;
            if (z < 8.0f) return 2.10f - (z - 5.9f) * 0.452f;
            return Math.max(0.62f, 1.15f - (z - 8.0f) * 0.86f);
        }
        float halfWidth = 2.16f - Math.max(0f, z - 5f) * 0.18f;
        return Math.max(0.72f, halfWidth);
    }

    float helmZ() {
        if (shipType == SHIP_FRIGATE) return -7.35f;
        if (shipType == SHIP_BRIG) return -6.10f;
        return -5.70f;
    }

    float helmCameraZ() { return helmZ() - (shipType == SHIP_FRIGATE ? 0.82f : 0.65f); }

    float helmEyeHeight() {
        if (shipType == SHIP_FRIGATE) return 3.62f;
        if (shipType == SHIP_BRIG) return 3.36f;
        return 2.75f;
    }

    float deckEyeHeightAt(float z) {
        if (shipType == SHIP_FRIGATE) {
            if (z <= -5.95f) return 3.62f;
            if (z < -4.85f) return 2.90f + (-4.85f - z) * (0.72f / 1.10f);
            if (z >= 7.05f) return 3.48f;
            if (z > 5.95f) return 2.90f + (z - 5.95f) * (0.58f / 1.10f);
        } else if (shipType == SHIP_BRIG) {
            if (z <= -4.85f) return 3.48f;
            if (z < -3.85f) return 2.90f + (-3.85f - z) * 0.58f;
            if (z >= 5.05f) return 3.40f;
            if (z > 4.15f) return 2.90f + (z - 4.15f) * (0.50f / 0.90f);
        }
        return 2.90f;
    }

    float helmExitX() {
        if (shipType == SHIP_FRIGATE) return 1.85f;
        if (shipType == SHIP_BRIG) return 1.45f;
        return 0.95f;
    }

    float gangwayX() {
        if (shipType == SHIP_FRIGATE) return 3.42f;
        if (shipType == SHIP_BRIG) return 2.55f;
        return 1.95f;
    }

    float mooringX() {
        if (shipType == SHIP_FRIGATE) return 3.10f;
        if (shipType == SHIP_BRIG) return 2.35f;
        return 1.62f;
    }

    float anchorControlZ() {
        if (shipType == SHIP_FRIGATE) return 7.75f;
        if (shipType == SHIP_BRIG) return 5.65f;
        return 4.75f;
    }

    private void moveInteriorWithCollisions'''
gs = sub_once(gs, collision_block, collision_repl, 'deck collision block')

interior_pattern = r'''    private boolean insideInteriorObstacle\(float x, float z\) \{.*?\n    \}\n\n    void interact\(\)'''
interior_repl = '''    private boolean insideInteriorObstacle(float x, float z) {
        float r = 0.18f;
        if (shipType == SHIP_SLOOP) {
            return insideCircle(x, z, 0f, -0.30f, 0.40f + r);
        }
        if (shipType == SHIP_BRIG) {
            if (insideCircle(x, z, 0f, -1.05f, 0.42f + r)
                    || insideCircle(x, z, 0f, 2.95f, 0.42f + r)) return true;
            if (interiorDeckLevel == 0 && Math.abs(x) > 1.30f) {
                for (float gunZ : new float[]{-4.90f, -3.35f, -1.80f, -0.25f}) {
                    if (Math.abs(z - gunZ) < 0.45f) return true;
                }
            }
            return false;
        }
        if (insideCircle(x, z, 0f, -4.10f, 0.42f + r)
                || insideCircle(x, z, 0f, -0.10f, 0.44f + r)
                || insideCircle(x, z, 0f, 4.65f, 0.42f + r)) return true;
        if (interiorDeckLevel == 0 && Math.abs(x) > 1.55f) {
            for (float gunZ : new float[]{-6.20f, -4.75f, -3.30f, -1.85f, -0.40f, 1.05f, 2.50f}) {
                if (Math.abs(z - gunZ) < 0.46f) return true;
            }
        }
        if (interiorDeckLevel == 1) {
            if (Math.abs(x) > 0.72f && (Math.abs(z + 5.65f) < 1.15f || Math.abs(z + 2.75f) < 1.15f)) return true;
            if (insideRect(x, z, -1.12f, 1.12f, 0.82f, 1.88f, r)) return true;
        }
        return false;
    }

    void interact()'''
gs = sub_once(gs, interior_pattern, interior_repl, 'interior collisions')

# Safe release from helm.
gs = gs.replace(
'''            playerX = 0.95f;
            playerZ = -5.0f;
            showMessage("Puściłeś koło sterowe");''',
'''            placePlayerSafelyOnDeck(helmExitX(), helmZ() + 1.15f);
            showMessage("Puściłeś koło sterowe");''')

# Dynamic anchor and mooring interaction points.
gs = gs.replace('near(playerX, playerZ, 0f, 4.75f, 1.1f)', 'near(playerX, playerZ, 0f, anchorControlZ(), 1.15f)')
gs = gs.replace('near(playerX, playerZ, 1.62f, 3.75f, 0.9f)', 'near(playerX, playerZ, mooringX(), 3.75f, 1.0f)')

# Spawn loaded games at a clear position for the active ship, then validate it.
gs = gs.replace(
'''        playerX = 0f;
        playerZ = -4.5f;
        onLand = false;''',
'''        playerX = shipType == SHIP_FRIGATE ? 1.85f : (shipType == SHIP_BRIG ? 1.45f : 0.95f);
        playerZ = shipType == SHIP_FRIGATE ? -6.20f : (shipType == SHIP_BRIG ? -5.10f : -5.0f);
        onLand = false;''')
gs = gs.replace(
'''        belowDeck = false;
        interiorDeckLevel = 0;
        hatchProgress = hatchOpen ? 1f : 0f;''',
'''        belowDeck = false;
        interiorDeckLevel = 0;
        placePlayerSafelyOnDeck(playerX, playerZ);
        hatchProgress = hatchOpen ? 1f : 0f;''')

# Dynamic gangway target.
gs = sub_once(gs,
             r'''    private boolean nearEitherGangway\(float x, float z\) \{\n.*?\n    \}''',
             '''    private boolean nearEitherGangway(float x, float z) {
        float gx = gangwayX();
        return near(x, z, -gx, 2.1f, 1.15f) || near(x, z, gx, 2.1f, 1.15f);
    }''',
             'gangway target')

# Stronger safe-position search used by boarding, loading old saves and releasing the wheel.
gs = sub_once(gs,
             r'''    private void placePlayerSafelyOnDeck\(float preferredX, float preferredZ\) \{.*?\n    \}\n\n    private boolean nearHelm''',
             '''    private void placePlayerSafelyOnDeck(float preferredX, float preferredZ) {
        float side = preferredX >= 0f ? 1f : -1f;
        float gx = gangwayX();
        float[][] candidates = {
                {preferredX, preferredZ},
                {side * (gx - 0.45f), 2.10f},
                {helmExitX(), helmZ() + 1.15f},
                {-helmExitX(), helmZ() + 1.15f},
                {side * 1.25f, -0.20f},
                {-side * 1.25f, -0.20f},
                {0f, 0.95f},
                {0f, 3.25f}
        };
        for (float[] candidate : candidates) {
            if (validDeckPosition(candidate[0], candidate[1])) {
                playerX = candidate[0];
                playerZ = candidate[1];
                return;
            }
        }
        playerX = 0f;
        playerZ = 0.95f;
    }

    private boolean nearHelm''',
             'safe deck placement')

gs = sub_once(gs,
             r'''    private boolean nearHelm\(\) \{\n.*?\n    \}''',
             '''    private boolean nearHelm() {
        float radius = shipType == SHIP_FRIGATE ? 1.75f : (shipType == SHIP_BRIG ? 1.60f : 2.15f);
        return near(playerX, playerZ, 0f, helmZ(), radius);
    }''',
             'helm target')

# Sanity checks.
for token in ['deckHalfWidthForCollision', 'helmCameraZ()', 'gangwayX()', 'anchorControlZ()', 'interiorDeckLevel == 0 && Math.abs(x) > 1.55f']:
    if token not in gs:
        raise RuntimeError(f'GameState sanity missing {token}')
GS.write_text(gs)

# --- WorldRenderer: remove sloop trim from big ships, align rails/equipment/helm/camera ---
wr = WR.read_text()

# Camera now follows the real wheel and raised period decks.
wr = wr.replace(
'''            float lx = (state.atHelm ? 0f : state.playerX) * state.shipScaleX();
            float lz = (state.atHelm ? -6.35f : state.playerZ) * state.shipScaleZ();
            float ly = (state.belowDeck ? 0.92f : (state.atHelm ? 2.75f : 2.9f)) * state.shipScaleY();''',
'''            float lx = (state.atHelm ? 0f : state.playerX) * state.shipScaleX();
            float lz = (state.atHelm ? state.helmCameraZ() : state.playerZ) * state.shipScaleZ();
            float localEyeY = state.belowDeck ? 0.92f
                    : (state.atHelm ? state.helmEyeHeight() : state.deckEyeHeightAt(state.playerZ));
            float ly = localEyeY * state.shipScaleY();''')

# Big ships already have dedicated trim. The old generic sloop transom/rudder created an internal wall.
wr = wr.replace(
'''            draw(frigateDeck, shipMatrix, LIGHT_WOOD);
            drawHullTrim();
            drawFrigateHullAccents();''',
'''            draw(frigateDeck, shipMatrix, LIGHT_WOOD);
            drawFrigateHullAccents();''')
wr = wr.replace(
'''            draw(brigDeck, shipMatrix, LIGHT_WOOD);
            drawHullTrim();
            drawBrigHullAccents();''',
'''            draw(brigDeck, shipMatrix, LIGHT_WOOD);
            drawBrigHullAccents();''')

# Ship-specific stern lamps instead of sloop positions inside larger hulls.
wr = sub_once(wr,
             r'''        drawWheel\(\);\n        drawRoundBeam\(shipMatrix, 0f, 1\.5f, 5\.3f, 0f, 2\.35f, 5\.3f, 0\.25f, WOOD\);\n        drawRoundBeam\(shipMatrix, -1\.95f, 1\.38f, -6\.6f, -1\.95f, 1\.92f, -6\.6f, 0\.10f, BRASS\);\n        drawRoundBeam\(shipMatrix, 1\.95f, 1\.38f, -6\.6f, 1\.95f, 1\.92f, -6\.6f, 0\.10f, BRASS\);\n        draw\(sphere, model\(shipMatrix, -1\.95f, 2\.0f, -6\.6f, 0f, 0\.16f, 0\.22f, 0\.16f\), LANTERN\);\n        draw\(sphere, model\(shipMatrix, 1\.95f, 2\.0f, -6\.6f, 0f, 0\.16f, 0\.22f, 0\.16f\), LANTERN\);''',
             '''        drawWheel();
        float windlassZ = state.anchorControlZ() + 0.55f;
        float windlassBaseY = state.shipType == GameState.SHIP_FRIGATE ? 2.00f
                : (state.shipType == GameState.SHIP_BRIG ? 1.68f : 1.50f);
        drawRoundBeam(shipMatrix, 0f, windlassBaseY, windlassZ,
                0f, windlassBaseY + 0.85f, windlassZ, 0.25f, WOOD);
        float lampX = state.shipType == GameState.SHIP_FRIGATE ? 3.00f
                : (state.shipType == GameState.SHIP_BRIG ? 2.35f : 1.95f);
        float lampZ = state.shipType == GameState.SHIP_FRIGATE ? -9.85f
                : (state.shipType == GameState.SHIP_BRIG ? -7.55f : -6.60f);
        float lampBaseY = state.shipType == GameState.SHIP_FRIGATE ? 2.05f
                : (state.shipType == GameState.SHIP_BRIG ? 1.70f : 1.38f);
        drawRoundBeam(shipMatrix, -lampX, lampBaseY, lampZ, -lampX, lampBaseY + 0.54f, lampZ, 0.10f, BRASS);
        drawRoundBeam(shipMatrix, lampX, lampBaseY, lampZ, lampX, lampBaseY + 0.54f, lampZ, 0.10f, BRASS);
        draw(sphere, model(shipMatrix, -lampX, lampBaseY + 0.62f, lampZ, 0f, 0.16f, 0.22f, 0.16f), LANTERN);
        draw(sphere, model(shipMatrix, lampX, lampBaseY + 0.62f, lampZ, 0f, 0.16f, 0.22f, 0.16f), LANTERN);''',
             'stern equipment')

# Frigate upper structure: low companionway, open quarterdeck around the wheel.
wr = sub_once(wr,
             r'''    private void drawFrigateCabin\(\) \{.*?\n    \}\n\n    private void drawFrigateHatch''',
             '''    private void drawFrigateCabin() {
        // Period frigates did not carry a modern boxy deckhouse here. Keep the quarterdeck open.
        drawBox(shipMatrix, 0f, 1.62f, -3.18f, 2.16f, 0.40f, 2.30f, BROWN);
        drawBox(shipMatrix, 0f, 1.86f, -3.18f, 2.42f, 0.10f, 2.54f, DARK);
        drawBox(shipMatrix, -0.58f, 1.94f, -3.18f, 0.78f, 0.08f, 1.30f, GLASS);
        drawBox(shipMatrix, 0.58f, 1.94f, -3.18f, 0.78f, 0.08f, 1.30f, GLASS);
        drawRoundBeam(shipMatrix, -1.12f, 1.48f, -4.30f, -1.12f, 1.92f, -4.30f, 0.035f, OCHRE);
        drawRoundBeam(shipMatrix, 1.12f, 1.48f, -4.30f, 1.12f, 1.92f, -4.30f, 0.035f, OCHRE);
        drawRoundBeam(shipMatrix, -1.12f, 1.48f, -2.06f, -1.12f, 1.92f, -2.06f, 0.035f, OCHRE);
        drawRoundBeam(shipMatrix, 1.12f, 1.48f, -2.06f, 1.12f, 1.92f, -2.06f, 0.035f, OCHRE);
    }

    private void drawFrigateHatch''',
             'frigate cabin')

# Brig also gets a lower companionway to keep side decks and helm route open.
wr = sub_once(wr,
             r'''    private void drawBrigCabin\(\) \{.*?\n    \}\n\n    private void drawBrigHatch''',
             '''    private void drawBrigCabin() {
        drawBox(shipMatrix, 0f, 1.40f, -2.74f, 1.92f, 0.54f, 2.04f, WOOD);
        drawBox(shipMatrix, 0f, 1.72f, -2.74f, 2.16f, 0.10f, 2.28f, DARK);
        drawBox(shipMatrix, -0.52f, 1.79f, -2.74f, 0.66f, 0.08f, 1.10f, GLASS);
        drawBox(shipMatrix, 0.52f, 1.79f, -2.74f, 0.66f, 0.08f, 1.10f, GLASS);
        drawBox(shipMatrix, 0f, 1.48f, -3.70f, 0.76f, 0.54f, 0.04f, DARK);
        drawBox(shipMatrix, 0.25f, 1.48f, -3.74f, 0.05f, 0.05f, 0.03f, BRASS);
    }

    private void drawBrigHatch''',
             'brig cabin')

# Replace period railings so they follow the real hull edges and keep open gangway gaps.
wr = sub_once(wr,
             r'''    private void drawFrigateRailings\(\) \{.*?\n    \}\n\n    private void drawFrigateCabin''',
             '''    private void drawFrigateRailings() {
        drawPeriodRailings(true);
    }

    private void drawFrigateCabin''',
             'frigate railings')
wr = sub_once(wr,
             r'''    private void drawBrigRailings\(\) \{.*?\n    \}\n\n    private void drawBrigCabin''',
             '''    private void drawBrigRailings() {
        drawPeriodRailings(false);
    }

    private void drawBrigCabin''',
             'brig railings')

# Model helpers inserted before the sloop railings.
rail_helpers = '''    private void drawPeriodRailings(boolean frigate) {
        float[] stations = frigate
                ? new float[]{-10.70f, -9.20f, -7.70f, -6.15f, -4.80f, -3.15f, -1.45f, 0.35f, 1.55f, 2.75f, 4.45f, 6.20f, 7.75f, 9.25f, 10.25f}
                : new float[]{-8.15f, -6.95f, -5.75f, -4.55f, -3.20f, -1.70f, -0.20f, 1.20f, 2.60f, 4.15f, 5.55f, 6.90f, 7.80f};
        for (int sideIndex = 0; sideIndex < 2; sideIndex++) {
            float side = sideIndex == 0 ? -1f : 1f;
            float previousX = 0f, previousY = 0f, previousZ = 0f;
            boolean hasPrevious = false;
            for (float z : stations) {
                float halfWidth = frigate ? frigateDeckHalfWidth(z) : brigDeckHalfWidth(z);
                float deckY = periodDeckSurfaceY(z, frigate);
                float x = side * (halfWidth - 0.08f);
                float topY = deckY + 0.62f;
                drawRoundBeam(shipMatrix, x, deckY + 0.04f, z, x, topY, z, 0.045f, DARK);
                if (hasPrevious) {
                    boolean gangwayGap = previousZ < 2.10f && z > 2.10f;
                    if (!gangwayGap) {
                        drawRoundBeam(shipMatrix, previousX, previousY, previousZ, x, topY, z, 0.052f, DARK);
                        drawRoundBeam(shipMatrix, previousX, previousY - 0.30f, previousZ,
                                x, topY - 0.30f, z, 0.032f, ROPE);
                    }
                }
                previousX = x;
                previousY = topY;
                previousZ = z;
                hasPrevious = true;
            }
        }
        float sternZ = frigate ? -10.75f : -8.15f;
        float sternW = frigate ? 2.95f : 2.38f;
        float sternY = periodDeckSurfaceY(sternZ, frigate) + 0.62f;
        drawRoundBeam(shipMatrix, -sternW, sternY, sternZ, sternW, sternY, sternZ, 0.052f, DARK);
    }

    private float periodDeckSurfaceY(float z, boolean frigate) {
        if (frigate) {
            if (z < -5.30f) return 2.16f;
            if (z > 6.85f) return 2.00f;
            return 1.42f;
        }
        if (z < -4.45f) return 1.78f;
        if (z > 4.45f) return 1.68f;
        return 1.18f;
    }

    private float brigDeckHalfWidth(float z) {
        if (z < -8.0f) return 2.77f + (z + 8.0f) * 0.31f;
        if (z < -6.4f) return 2.77f + (z + 8.0f) * 0.15f;
        if (z < -4.0f) return 3.01f + (z + 6.4f) * 0.042f;
        if (z < -0.8f) return 3.11f - (z + 4.0f) * 0.013f;
        if (z < 2.8f) return 3.07f - (z + 0.8f) * 0.083f;
        if (z < 5.9f) return 2.77f - (z - 2.8f) * 0.20f;
        if (z < 8.0f) return 2.15f - (z - 5.9f) * 0.452f;
        return Math.max(0.58f, 1.20f - (z - 8.0f) * 0.86f);
    }

    private float frigateDeckHalfWidth(float z) {
        if (z < -11.0f) return 3.13f + (z + 11.0f) * 0.27f;
        if (z < -9.0f) return 3.13f + (z + 11.0f) * 0.19f;
        if (z < -6.3f) return 3.51f + (z + 9.0f) * 0.089f;
        if (z < 1.2f) return 3.79f;
        if (z < 5.2f) return 3.79f - (z - 1.2f) * 0.10f;
        if (z < 8.5f) return 3.39f - (z - 5.2f) * 0.224f;
        if (z < 10.8f) return 2.65f - (z - 8.5f) * 0.574f;
        return Math.max(0.62f, 1.33f - (z - 10.8f) * 0.70f);
    }

    private void drawDeckSteps(float centerZ, boolean towardPositiveZ, float width,
                               float startY, float endY) {
        int count = 4;
        for (int i = 0; i < count; i++) {
            float t = (i + 1f) / count;
            float z = centerZ + (towardPositiveZ ? 1f : -1f) * (i - 1.5f) * 0.24f;
            float y = startY + (endY - startY) * t;
            drawBox(shipMatrix, 0f, y, z, width, 0.12f, 0.34f, WOOD);
        }
    }

    private void drawPeriodRudder(float sternZ, float height, float width) {
        float[] rudder = copy(shipMatrix);
        Matrix.translateM(rudder, 0, 0f, -0.52f, sternZ);
        Matrix.rotateM(rudder, 0, state.rudder * 24f, 0f, 1f, 0f);
        drawBox(rudder, 0f, 0f, -0.18f, width, height, 0.78f, DARK);
    }

'''
if '    private void drawRailings() {' not in wr:
    raise RuntimeError('drawRailings marker missing')
wr = wr.replace('    private void drawRailings() {', rail_helpers + '    private void drawRailings() {', 1)

# Deck plank widths now use their actual hull instead of the sloop helper.
wr = wr.replace('float width = Math.max(0.55f, deckHalfWidth(z) * 2.35f);',
                'float width = Math.max(0.55f, frigateDeckHalfWidth(z) * 1.90f);')
wr = wr.replace('float width = Math.max(0.52f, deckHalfWidth(z) * 1.90f);',
                'float width = Math.max(0.52f, brigDeckHalfWidth(z) * 1.90f);')

# Move stern deck furniture away from the central route and add visible steps.
wr = wr.replace(
'''        drawBox(shipMatrix, 0f, 1.98f, -6.65f, 0.70f, 0.34f, 0.60f, DARK);
        drawRoundBeam(shipMatrix, 0f, 2.18f, -6.65f, 0f, 2.82f, -6.65f, 0.09f, BRASS);''',
'''        drawBox(shipMatrix, 1.58f, 1.98f, -6.62f, 0.70f, 0.34f, 0.60f, DARK);
        drawRoundBeam(shipMatrix, 1.58f, 2.18f, -6.62f, 1.58f, 2.82f, -6.62f, 0.09f, BRASS);
        drawDeckSteps(-5.18f, false, 2.10f, 1.46f, 2.10f);
        drawDeckSteps(6.72f, true, 1.88f, 1.46f, 1.94f);''')
wr = wr.replace(
'''        drawBox(shipMatrix, 0f, 1.65f, -5.00f, 0.62f, 0.30f, 0.54f, DARK);
        drawRoundBeam(shipMatrix, 0f, 1.82f, -5.00f, 0f, 2.35f, -5.00f, 0.08f, BRASS);''',
'''        drawBox(shipMatrix, 1.18f, 1.65f, -5.00f, 0.62f, 0.30f, 0.54f, DARK);
        drawRoundBeam(shipMatrix, 1.18f, 1.82f, -5.00f, 1.18f, 2.35f, -5.00f, 0.08f, BRASS);
        drawDeckSteps(-4.38f, false, 1.82f, 1.22f, 1.72f);
        drawDeckSteps(4.38f, true, 1.62f, 1.22f, 1.62f);''')

# Dedicated period rudders at the actual sterns.
wr = wr.replace(
'''        drawRoundBeam(shipMatrix, 0.18f, 1.06f, 9.05f, 1.48f, 1.74f, 10.18f, 0.028f, ROPE);
    }

    private void drawFrigateRailings''',
'''        drawRoundBeam(shipMatrix, 0.18f, 1.06f, 9.05f, 1.48f, 1.74f, 10.18f, 0.028f, ROPE);
        drawPeriodRudder(-12.58f, 2.32f, 0.24f);
    }

    private void drawFrigateRailings''')
wr = wr.replace(
'''        drawRoundBeam(shipMatrix, 0.42f, 1.08f, 7.35f, 1.52f, 1.72f, 8.95f, 0.038f, WOOD);
    }

    private void drawBrigRailings''',
'''        drawRoundBeam(shipMatrix, 0.42f, 1.08f, 7.35f, 1.52f, 1.72f, 8.95f, 0.038f, WOOD);
        drawPeriodRudder(-9.34f, 1.98f, 0.22f);
    }

    private void drawBrigRailings''')

# Wheel position and size by ship; frigate wheel sits on open quarterdeck, clear of companionway.
wr = sub_once(wr,
             r'''    private void drawWheel\(\) \{.*?\n    \}\n\n    private void drawIsland''',
             '''    private void drawWheel() {
        float[] wheelColor = state.interactionTarget() == GameState.TARGET_HELM ? HIGHLIGHT : DARK;
        float steer = state.rudder * 45f;
        float wheelZ = state.helmZ();
        float wheelY = state.shipType == GameState.SHIP_FRIGATE ? 2.88f
                : (state.shipType == GameState.SHIP_BRIG ? 2.46f : 2.25f);
        float wheelScale = state.shipType == GameState.SHIP_FRIGATE ? 0.82f
                : (state.shipType == GameState.SHIP_BRIG ? 0.92f : 1.0f);
        float deckY = state.shipType == GameState.SHIP_FRIGATE ? 2.16f
                : (state.shipType == GameState.SHIP_BRIG ? 1.78f : 1.12f);
        float[] root = copy(shipMatrix);
        Matrix.translateM(root, 0, 0f, wheelY, wheelZ);
        Matrix.rotateM(root, 0, steer, 0f, 0f, 1f);
        float[] rim = copy(root);
        Matrix.scaleM(rim, 0, 1.02f * wheelScale, 1.02f * wheelScale, 0.18f * wheelScale);
        draw(torus, rim, wheelColor);
        drawRoundBeam(root, 0f, 0f, -0.17f * wheelScale, 0f, 0f, 0.17f * wheelScale,
                0.17f * wheelScale, BRASS);
        for (int i = 0; i < 8; i++) {
            float a = i * 45f * GameState.PI / 180f;
            float x = (float)Math.sin(a);
            float y = (float)Math.cos(a);
            drawRoundBeam(root, 0f, 0f, 0f, x * 1.18f * wheelScale, y * 1.18f * wheelScale, 0f,
                    0.045f * wheelScale, wheelColor);
            float[] handle = model(root, x * 1.22f * wheelScale, y * 1.22f * wheelScale, 0f,
                    0f, 0.11f * wheelScale, 0.11f * wheelScale, 0.25f * wheelScale);
            draw(cylinder, handle, wheelColor);
        }
        float supportX = 0.75f * wheelScale;
        drawRoundBeam(shipMatrix, -supportX, deckY + 0.04f, wheelZ,
                -supportX, wheelY - 0.10f, wheelZ, 0.08f * wheelScale, WOOD);
        drawRoundBeam(shipMatrix, supportX, deckY + 0.04f, wheelZ,
                supportX, wheelY - 0.10f, wheelZ, 0.08f * wheelScale, WOOD);
        drawRoundBeam(shipMatrix, -supportX - 0.10f, wheelY - 0.12f, wheelZ,
                supportX + 0.10f, wheelY - 0.12f, wheelZ, 0.075f * wheelScale, WOOD);
    }

    private void drawIsland''',
             'wheel model')

# Anchor, mooring and gangways track each hull width/length.
wr = sub_once(wr,
             r'''    private void drawAnchorAndMooring\(\) \{.*?\n    \}\n\n    private void drawInterior''',
             '''    private void drawAnchorAndMooring() {
        boolean anchorHighlight = state.interactionTarget() == GameState.TARGET_ANCHOR;
        boolean mooringHighlight = state.interactionTarget() == GameState.TARGET_MOORING;
        boolean gangwayHighlight = state.interactionTarget() == GameState.TARGET_GANGWAY;
        float windlassZ = state.anchorControlZ() + 0.55f;
        float windlassY = state.shipType == GameState.SHIP_FRIGATE ? 2.08f
                : (state.shipType == GameState.SHIP_BRIG ? 1.72f : 1.44f);
        float[] windlass = copy(shipMatrix);
        Matrix.translateM(windlass, 0, 0f, windlassY, windlassZ);
        Matrix.scaleM(windlass, 0, 0.48f, 0.48f, 0.48f);
        draw(cylinder, windlass, anchorHighlight ? HIGHLIGHT : WOOD);
        for (int i = 0; i < 6; i++) {
            float a = i * 60f * GameState.PI / 180f;
            drawRoundBeam(shipMatrix, 0f, windlassY + 0.17f, windlassZ,
                    (float)Math.sin(a) * 0.68f, windlassY + 0.17f,
                    windlassZ + (float)Math.cos(a) * 0.68f, 0.045f,
                    anchorHighlight ? HIGHLIGHT : IRON);
        }
        float mooringX = state.mooringX();
        float cleatY = state.shipType == GameState.SHIP_FRIGATE ? 1.72f
                : (state.shipType == GameState.SHIP_BRIG ? 1.50f : 1.42f);
        drawBox(shipMatrix, mooringX, cleatY, 3.75f, 0.34f, 0.16f, 0.54f,
                mooringHighlight ? HIGHLIGHT : BRASS);
        if (state.anchorProgress > 0.02f) {
            float anchorZ = windlassZ + (state.shipType == GameState.SHIP_FRIGATE ? 1.55f : 0.85f);
            float anchorX = state.shipType == GameState.SHIP_FRIGATE ? 1.05f : 0.70f;
            drawBeam(shipMatrix, 0.55f, windlassY - 0.10f, windlassZ + 0.45f, anchorX,
                    1.15f - state.anchorProgress * 4.8f, anchorZ,
                    0.055f, IRON);
            drawBox(shipMatrix, anchorX, 0.95f - state.anchorProgress * 4.8f,
                    anchorZ, 0.48f, 0.65f, 0.16f, IRON);
        }
        if (state.moored) {
            float gangwayX = state.gangwayX();
            drawBeam(shipMatrix, mooringX, cleatY, 3.8f, mooringX + 3.8f, 0.25f, 4.8f,
                    0.075f, ROPE);
            drawBeam(shipMatrix, -mooringX, cleatY, -0.2f, -mooringX - 3.4f, 0.25f, 0.8f,
                    0.075f, ROPE);
            drawBox(shipMatrix, -gangwayX, 1.18f, 2.1f, 1.55f, 0.12f, 0.7f,
                    gangwayHighlight ? HIGHLIGHT : LIGHT_WOOD);
            drawBox(shipMatrix, gangwayX, 1.18f, 2.1f, 1.55f, 0.12f, 0.7f,
                    gangwayHighlight ? HIGHLIGHT : LIGHT_WOOD);
        }
    }

    private void drawInterior''',
             'anchor/mooring model')

# Sanity checks.
for token in ['drawPeriodRailings(true)', 'drawPeriodRudder(-12.58f', 'state.helmCameraZ()', 'state.gangwayX()', 'Period frigates did not carry']:
    if token not in wr:
        raise RuntimeError(f'WorldRenderer sanity missing {token}')
WR.write_text(wr)

print('v0.6.4 patch applied successfully')
