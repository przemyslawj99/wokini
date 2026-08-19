from pathlib import Path

root = Path('build-project')
wr = root / 'app/src/main/java/pl/kursnahoryzont/game/WorldRenderer.java'
bg = root / 'app/build.gradle'

text = wr.read_text()

start = text.index('    private void drawWakeAndSpray() {')
end = text.index('    private void drawShip() {', start)
new_wake = r'''    private float wakeHullHalfLength() {
        if (state.shipType == GameState.SHIP_FRIGATE) return 12.4f * state.shipScaleZ();
        if (state.shipType == GameState.SHIP_BRIG) return 9.2f * state.shipScaleZ();
        return 7.35f * state.shipScaleZ();
    }

    private float wakeHullHalfBeam() {
        if (state.shipType == GameState.SHIP_FRIGATE) return 3.70f * state.shipScaleX();
        if (state.shipType == GameState.SHIP_BRIG) return 3.02f * state.shipScaleX();
        return 2.15f * state.shipScaleX();
    }

    private void drawWakeAndSpray() {
        if (state.shipSpeed < 0.08f || state.moored || state.anchorDropped) return;
        float[] wakeMatrix = new float[16];
        Matrix.setIdentityM(wakeMatrix, 0);
        Matrix.translateM(wakeMatrix, 0, state.shipX, -0.24f, state.shipZ);
        Matrix.rotateM(wakeMatrix, 0, state.shipHeading * 180f / GameState.PI, 0f, 1f, 0f);

        float strength = GameState.clamp(state.shipSpeed / 5.2f, 0.10f, 1f);
        float halfLength = wakeHullHalfLength();
        float halfBeam = wakeHullHalfBeam();
        float shipMassFoam = state.shipType == GameState.SHIP_FRIGATE ? 1.35f
                : (state.shipType == GameState.SHIP_BRIG ? 1.16f : 1f);
        int segments = state.graphicsQuality == 0 ? 7 : state.graphicsQuality == 1 ? 11 : 16;

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDepthMask(false);

        float wakeStartZ = -halfLength - 0.45f;
        float wakeSpacing = 1.15f + halfLength * 0.018f;
        for (int i = 0; i < segments; i++) {
            float progress = i / (float)Math.max(1, segments - 1);
            float z = wakeStartZ - i * wakeSpacing;
            float spread = halfBeam * 0.30f + i * (0.16f + halfBeam * 0.018f);
            float wobble = (float)Math.sin(elapsed * 1.65f + i * 1.37f) * (0.10f + strength * 0.08f);
            float patchW = (0.40f + progress * 0.72f) * shipMassFoam;
            float patchL = (0.92f + progress * 1.18f) * shipMassFoam;
            float[] color = i < 3 ? FOAM_SOFT : FOAM_FAINT;
            for (int sideIndex = 0; sideIndex < 2; sideIndex++) {
                float side = sideIndex == 0 ? -1f : 1f;
                float x = side * (spread + wobble * side);
                draw(sphere, model(wakeMatrix, x, 0.006f, z, 0f,
                        patchW, 0.026f, patchL), color);
                if (i < segments * 2 / 3 && i % 2 == 0) {
                    draw(sphere, model(wakeMatrix, x * 0.56f, 0.003f,
                            z - wakeSpacing * 0.30f, 0f,
                            patchW * 0.58f, 0.020f, patchL * 0.66f), FOAM_FAINT);
                }
            }
        }

        int bowPatches = state.graphicsQuality == 0 ? 4 : state.graphicsQuality == 1 ? 6 : 8;
        float bowTipZ = halfLength + 0.22f;
        for (int sideIndex = 0; sideIndex < 2; sideIndex++) {
            float side = sideIndex == 0 ? -1f : 1f;
            for (int i = 0; i < bowPatches; i++) {
                float p = i / (float)Math.max(1, bowPatches - 1);
                float x = side * (halfBeam * (0.18f + p * 0.72f)
                        + strength * (0.10f + p * 0.22f));
                float z = bowTipZ - p * (2.25f + halfLength * 0.075f);
                float scale = (0.34f + p * 0.36f + strength * 0.16f) * shipMassFoam;
                draw(sphere, model(wakeMatrix, x, 0.010f, z, 0f,
                        scale, 0.024f, scale * (1.34f + p * 0.35f)),
                        p < 0.58f ? FOAM_SOFT : FOAM_FAINT);
            }
            for (int i = 0; i < 3; i++) {
                float p = (i + 1) / 4f;
                float x = side * (halfBeam * (0.58f + p * 0.44f) + strength * 0.20f);
                float z = halfLength - p * (1.55f + halfLength * 0.045f);
                draw(sphere, model(wakeMatrix, x, 0.012f, z, 0f,
                        0.52f * shipMassFoam, 0.020f,
                        (1.10f + p * 0.46f) * shipMassFoam), FOAM_SOFT);
            }
        }

        if (strength > 0.34f || state.storm > 0.22f) {
            int splashes = state.graphicsQuality == 0 ? 2 : state.graphicsQuality == 1 ? 4 : 6;
            for (int i = 0; i < splashes; i++) {
                float side = i % 2 == 0 ? -1f : 1f;
                float phase = (elapsed * (2.0f + state.storm * 1.8f) + i * 0.41f) % 1f;
                float x = side * (halfBeam * (0.36f + phase * 0.30f));
                float y = (float)Math.sin(phase * Math.PI)
                        * (0.12f + strength * 0.34f + state.storm * 0.22f) * shipMassFoam;
                float z = halfLength - phase * (0.95f + halfLength * 0.025f);
                draw(sphere, model(wakeMatrix, x, y, z, 0f,
                        0.11f * shipMassFoam,
                        0.13f + y * 0.30f,
                        0.18f * shipMassFoam), FOAM_SOFT);
            }
        }

        if (state.oarsActive) {
            float stroke = (float)Math.sin(elapsed * 3.6f);
            float oz = 1.15f + stroke * 0.72f;
            for (int sideIndex = 0; sideIndex < 2; sideIndex++) {
                float side = sideIndex == 0 ? -1f : 1f;
                draw(sphere, model(wakeMatrix, side * 4.35f, 0.01f, oz, 0f,
                        0.34f, 0.025f, 0.58f), FOAM_SOFT);
            }
        }

        GLES20.glDepthMask(true);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

'''
text = text[:start] + new_wake + text[end:]

old_road = '''        float roadZ = z + dockSign * (r * 0.58f);\n        drawBox(identity, x, terrainHeight(x, roadZ) + 0.06f, roadZ,\n                7.0f, 0.08f, r * 0.56f, SAND);\n        drawBox(identity, x - 8f, terrainHeight(x - 8f, marketZ) + 0.06f, marketZ,\n                18f, 0.08f, 3.0f, SAND);\n'''
new_road = '''        // Drogi składają się z krótkich odcinków dopasowanych do nachylenia wyspy.\n        // Nad wodą zaczyna się już drewniana keja zamiast zawieszonego pasa piasku.\n        float quayLandZ = z + dockSign * (r - 3.4f);\n        float roadStartZ = z + dockSign * (r * 0.18f);\n        drawTerrainRoad(x + 1.5f, roadStartZ, x + 1.5f, quayLandZ, 6.2f);\n        drawTerrainRoad(marketX, marketZ, x + 1.5f, roadStartZ, 3.4f);\n        drawTerrainRoad(tavernX, tavernZ, x + 1.5f, roadStartZ, 3.0f);\n'''
if old_road not in text:
    raise SystemExit('road block not found')
text = text.replace(old_road, new_road, 1)

marker = '    private float terrainHeight(float x, float z) {'
helper = r'''    private void drawTerrainRoad(float x1, float z1, float x2, float z2, float width) {
        float dx = x2 - x1;
        float dz = z2 - z1;
        float length = (float)Math.sqrt(dx * dx + dz * dz);
        if (length < 0.1f) return;
        int segments = Math.max(4, (int)(length / 3.0f));
        float yaw = (float)Math.atan2(dx, dz) * 180f / GameState.PI;
        for (int i = 0; i < segments; i++) {
            float t0 = i / (float)segments;
            float t1 = (i + 1) / (float)segments;
            float ax = x1 + dx * t0;
            float az = z1 + dz * t0;
            float bx = x1 + dx * t1;
            float bz = z1 + dz * t1;
            float ay = terrainHeight(ax, az);
            float by = terrainHeight(bx, bz);
            float cx = (ax + bx) * 0.5f;
            float cz = (az + bz) * 0.5f;
            float cy = (ay + by) * 0.5f + 0.045f;
            float segmentLength = (float)Math.sqrt((bx - ax) * (bx - ax) + (bz - az) * (bz - az));
            float pitch = -(float)Math.atan2(by - ay, Math.max(0.001f, segmentLength))
                    * 180f / GameState.PI;
            float[] local = new float[16];
            Matrix.setIdentityM(local, 0);
            Matrix.translateM(local, 0, cx, cy, cz);
            Matrix.rotateM(local, 0, yaw, 0f, 1f, 0f);
            Matrix.rotateM(local, 0, pitch, 1f, 0f, 0f);
            Matrix.scaleM(local, 0, width, 0.07f, segmentLength + 0.20f);
            draw(cube, local, SAND);
        }
    }

'''
if marker not in text:
    raise SystemExit('terrain marker missing')
text = text.replace(marker, helper + marker, 1)
wr.write_text(text)

b = bg.read_text()
b = b.replace("versionCode 28", "versionCode 29")
b = b.replace("versionName '0.6.5'", "versionName '0.6.6'")
bg.write_text(b)
