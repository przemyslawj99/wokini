package pl.kursnahoryzont.game;

import java.util.ArrayList;

final class ShipGeometry {
    private ShipGeometry() {}

    static float[] detailedHull() {
        float[] z = {-8.2f, -6.9f, -5.0f, -2.2f, 1.2f, 4.6f, 6.9f, 8.4f};
        float[] topW = {2.00f, 2.28f, 2.46f, 2.58f, 2.52f, 2.12f, 1.20f, 0.10f};
        float[] topY = {1.00f, 1.12f, 1.20f, 1.24f, 1.22f, 1.12f, 0.92f, 0.64f};
        float[] chineW = {1.42f, 1.72f, 1.94f, 2.02f, 1.96f, 1.54f, 0.70f, 0.04f};
        float[] chineY = {-0.18f, -0.42f, -0.68f, -0.88f, -0.94f, -0.76f, -0.36f, 0.44f};
        float[] keelW = {0.58f, 0.72f, 0.82f, 0.86f, 0.80f, 0.52f, 0.18f, 0f};
        float[] keelY = {-1.02f, -1.30f, -1.56f, -1.70f, -1.62f, -1.18f, -0.56f, 0.30f};
        Builder b = new Builder();
        for (int i = 0; i < z.length - 1; i++) {
            for (int side = -1; side <= 1; side += 2) {
                quad(b,
                        side * topW[i], topY[i], z[i],
                        side * topW[i + 1], topY[i + 1], z[i + 1],
                        side * chineW[i + 1], chineY[i + 1], z[i + 1],
                        side * chineW[i], chineY[i], z[i], side > 0);
                quad(b,
                        side * chineW[i], chineY[i], z[i],
                        side * chineW[i + 1], chineY[i + 1], z[i + 1],
                        side * keelW[i + 1], keelY[i + 1], z[i + 1],
                        side * keelW[i], keelY[i], z[i], side > 0);
            }
            quad(b,
                    -keelW[i], keelY[i], z[i],
                    -keelW[i + 1], keelY[i + 1], z[i + 1],
                    keelW[i + 1], keelY[i + 1], z[i + 1],
                    keelW[i], keelY[i], z[i], false);
        }
        cap(b, z[0], topW[0], topY[0], chineW[0], chineY[0], keelW[0], keelY[0], true);
        cap(b, z[z.length - 1], topW[z.length - 1], topY[z.length - 1],
                chineW[z.length - 1], chineY[z.length - 1],
                keelW[z.length - 1], keelY[z.length - 1], false);
        return b.toArray();
    }

    static float[] upperHullBand() {
        float[] z = {-8.2f, -6.9f, -5.0f, -2.2f, 1.2f, 4.6f, 6.9f, 8.4f};
        float[] topW = {2.00f, 2.28f, 2.46f, 2.58f, 2.52f, 2.12f, 1.20f, 0.10f};
        float[] topY = {1.00f, 1.12f, 1.20f, 1.24f, 1.22f, 1.12f, 0.92f, 0.64f};
        float[] chineW = {1.42f, 1.72f, 1.94f, 2.02f, 1.96f, 1.54f, 0.70f, 0.04f};
        float[] chineY = {-0.18f, -0.42f, -0.68f, -0.88f, -0.94f, -0.76f, -0.36f, 0.44f};
        Builder b = new Builder();
        for (int i = 0; i < z.length - 1; i++) {
            for (int side = -1; side <= 1; side += 2) {
                float ax = side * topW[i] * 1.006f;
                float bx = side * topW[i + 1] * 1.006f;
                float cx = side * chineW[i + 1] * 1.006f;
                float dx = side * chineW[i] * 1.006f;
                quad(b,
                        ax, topY[i] + 0.004f, z[i],
                        bx, topY[i + 1] + 0.004f, z[i + 1],
                        cx, chineY[i + 1] + 0.004f, z[i + 1],
                        dx, chineY[i] + 0.004f, z[i], side > 0);
            }
        }
        return b.toArray();
    }

    static float[] taperedDeck() {
        // The deck deliberately overlaps the hull sheer. The old deck was narrower than the
        // hull and stopped before the bow, which exposed water between the deck and both sides.
        float[] z = {-8.2f, -6.9f, -5.0f, -2.2f, 1.2f, 4.6f, 6.9f, 8.4f};
        float[] hullW = {2.00f, 2.28f, 2.46f, 2.58f, 2.52f, 2.12f, 1.20f, 0.10f};
        float[] hullY = {1.00f, 1.12f, 1.20f, 1.24f, 1.22f, 1.12f, 0.92f, 0.64f};
        float[] deckW = new float[hullW.length];
        for (int i = 0; i < hullW.length; i++) deckW[i] = hullW[i] + 0.055f;

        Builder b = new Builder();
        for (int i = 0; i < z.length - 1; i++) {
            // Flat walking surface kept at the original height so all deck fittings stay aligned.
            quad(b, -deckW[i], 1.13f, z[i], -deckW[i + 1], 1.13f, z[i + 1],
                    deckW[i + 1], 1.13f, z[i + 1], deckW[i], 1.13f, z[i], false);

            // Sheer-cover strips close the vertical seam between the flat deck and curved hull.
            quad(b,
                    -deckW[i], 1.13f, z[i],
                    -deckW[i + 1], 1.13f, z[i + 1],
                    -hullW[i + 1], hullY[i + 1], z[i + 1],
                    -hullW[i], hullY[i], z[i], false);
            quad(b,
                    deckW[i], 1.13f, z[i],
                    hullW[i], hullY[i], z[i],
                    hullW[i + 1], hullY[i + 1], z[i + 1],
                    deckW[i + 1], 1.13f, z[i + 1], false);
        }

        // Close the visible seams at the transom and the sharp bow as well.
        quad(b,
                -deckW[0], 1.13f, z[0], deckW[0], 1.13f, z[0],
                hullW[0], hullY[0], z[0], -hullW[0], hullY[0], z[0], true);
        int last = z.length - 1;
        quad(b,
                -deckW[last], 1.13f, z[last], -hullW[last], hullY[last], z[last],
                hullW[last], hullY[last], z[last], deckW[last], 1.13f, z[last], false);
        return b.toArray();
    }


    static float[] brigDetailedHull() {
        // Dedicated brig hull: deeper bilge, fuller stern and taller freeboard than the sloop.
        float[] z = {-9.2f, -8.0f, -6.4f, -4.0f, -0.8f, 2.8f, 5.9f, 8.0f, 9.2f};
        float[] topW = {2.35f, 2.72f, 2.96f, 3.06f, 3.02f, 2.72f, 2.10f, 1.15f, 0.12f};
        float[] topY = {1.18f, 1.28f, 1.34f, 1.36f, 1.34f, 1.28f, 1.18f, 0.98f, 0.70f};
        float[] chineW = {1.80f, 2.18f, 2.46f, 2.60f, 2.54f, 2.18f, 1.46f, 0.58f, 0.04f};
        float[] chineY = {-0.24f, -0.50f, -0.78f, -1.00f, -1.08f, -0.98f, -0.66f, -0.12f, 0.50f};
        float[] keelW = {0.74f, 0.94f, 1.06f, 1.10f, 1.02f, 0.80f, 0.40f, 0.10f, 0f};
        float[] keelY = {-1.52f, -1.88f, -2.18f, -2.34f, -2.40f, -2.18f, -1.56f, -0.72f, 0.34f};
        Builder b = new Builder();
        for (int i = 0; i < z.length - 1; i++) {
            for (int side = -1; side <= 1; side += 2) {
                quad(b,
                        side * topW[i], topY[i], z[i],
                        side * topW[i + 1], topY[i + 1], z[i + 1],
                        side * chineW[i + 1], chineY[i + 1], z[i + 1],
                        side * chineW[i], chineY[i], z[i], side > 0);
                quad(b,
                        side * chineW[i], chineY[i], z[i],
                        side * chineW[i + 1], chineY[i + 1], z[i + 1],
                        side * keelW[i + 1], keelY[i + 1], z[i + 1],
                        side * keelW[i], keelY[i], z[i], side > 0);
            }
            quad(b,
                    -keelW[i], keelY[i], z[i],
                    -keelW[i + 1], keelY[i + 1], z[i + 1],
                    keelW[i + 1], keelY[i + 1], z[i + 1],
                    keelW[i], keelY[i], z[i], false);
        }
        cap(b, z[0], topW[0], topY[0], chineW[0], chineY[0], keelW[0], keelY[0], true);
        cap(b, z[z.length - 1], topW[z.length - 1], topY[z.length - 1],
                chineW[z.length - 1], chineY[z.length - 1],
                keelW[z.length - 1], keelY[z.length - 1], false);
        return b.toArray();
    }

    static float[] brigUpperHullBand() {
        float[] z = {-9.2f, -8.0f, -6.4f, -4.0f, -0.8f, 2.8f, 5.9f, 8.0f, 9.2f};
        float[] topW = {2.35f, 2.72f, 2.96f, 3.06f, 3.02f, 2.72f, 2.10f, 1.15f, 0.12f};
        float[] topY = {1.18f, 1.28f, 1.34f, 1.36f, 1.34f, 1.28f, 1.18f, 0.98f, 0.70f};
        float[] chineW = {1.80f, 2.18f, 2.46f, 2.60f, 2.54f, 2.18f, 1.46f, 0.58f, 0.04f};
        float[] chineY = {-0.24f, -0.50f, -0.78f, -1.00f, -1.08f, -0.98f, -0.66f, -0.12f, 0.50f};
        Builder b = new Builder();
        for (int i = 0; i < z.length - 1; i++) {
            for (int side = -1; side <= 1; side += 2) {
                quad(b,
                        side * topW[i] * 1.005f, topY[i] + 0.004f, z[i],
                        side * topW[i + 1] * 1.005f, topY[i + 1] + 0.004f, z[i + 1],
                        side * chineW[i + 1] * 1.005f, chineY[i + 1] + 0.004f, z[i + 1],
                        side * chineW[i] * 1.005f, chineY[i] + 0.004f, z[i], side > 0);
            }
        }
        return b.toArray();
    }

    static float[] brigTaperedDeck() {
        float[] z = {-9.2f, -8.0f, -6.4f, -4.0f, -0.8f, 2.8f, 5.9f, 8.0f, 9.2f};
        float[] hullW = {2.35f, 2.72f, 2.96f, 3.06f, 3.02f, 2.72f, 2.10f, 1.15f, 0.12f};
        float[] hullY = {1.18f, 1.28f, 1.34f, 1.36f, 1.34f, 1.28f, 1.18f, 0.98f, 0.70f};
        float[] deckW = new float[hullW.length];
        for (int i = 0; i < hullW.length; i++) deckW[i] = hullW[i] + 0.055f;
        Builder b = new Builder();
        for (int i = 0; i < z.length - 1; i++) {
            quad(b, -deckW[i], 1.18f, z[i], -deckW[i + 1], 1.18f, z[i + 1],
                    deckW[i + 1], 1.18f, z[i + 1], deckW[i], 1.18f, z[i], false);
            quad(b,
                    -deckW[i], 1.18f, z[i],
                    -deckW[i + 1], 1.18f, z[i + 1],
                    -hullW[i + 1], hullY[i + 1], z[i + 1],
                    -hullW[i], hullY[i], z[i], false);
            quad(b,
                    deckW[i], 1.18f, z[i],
                    hullW[i], hullY[i], z[i],
                    hullW[i + 1], hullY[i + 1], z[i + 1],
                    deckW[i + 1], 1.18f, z[i + 1], false);
        }
        quad(b,
                -deckW[0], 1.18f, z[0], deckW[0], 1.18f, z[0],
                hullW[0], hullY[0], z[0], -hullW[0], hullY[0], z[0], true);
        int last = z.length - 1;
        quad(b,
                -deckW[last], 1.18f, z[last], -hullW[last], hullY[last], z[last],
                hullW[last], hullY[last], z[last], deckW[last], 1.18f, z[last], false);
        return b.toArray();
    }


    static float[] frigateDetailedHull() {
        // Dedicated frigate hull built from scratch: longer, deeper and finer than the brig.
        float[] z = {-12.4f, -11.0f, -9.0f, -6.3f, -2.8f, 1.2f, 5.2f, 8.5f, 10.8f, 12.4f};
        float[] topW = {2.70f, 3.08f, 3.46f, 3.70f, 3.78f, 3.74f, 3.34f, 2.60f, 1.28f, 0.14f};
        float[] topY = {1.32f, 1.42f, 1.50f, 1.58f, 1.62f, 1.60f, 1.52f, 1.38f, 1.04f, 0.72f};
        float[] chineW = {2.18f, 2.58f, 3.02f, 3.28f, 3.38f, 3.34f, 2.88f, 2.04f, 0.76f, 0.04f};
        float[] chineY = {-0.34f, -0.64f, -0.94f, -1.18f, -1.30f, -1.22f, -1.00f, -0.60f, -0.06f, 0.56f};
        float[] keelW = {0.96f, 1.18f, 1.36f, 1.46f, 1.52f, 1.42f, 1.08f, 0.56f, 0.14f, 0f};
        float[] keelY = {-1.98f, -2.42f, -2.78f, -3.02f, -3.12f, -2.98f, -2.42f, -1.54f, -0.72f, 0.36f};
        Builder b = new Builder();
        for (int i = 0; i < z.length - 1; i++) {
            for (int side = -1; side <= 1; side += 2) {
                quad(b,
                        side * topW[i], topY[i], z[i],
                        side * topW[i + 1], topY[i + 1], z[i + 1],
                        side * chineW[i + 1], chineY[i + 1], z[i + 1],
                        side * chineW[i], chineY[i], z[i], side > 0);
                quad(b,
                        side * chineW[i], chineY[i], z[i],
                        side * chineW[i + 1], chineY[i + 1], z[i + 1],
                        side * keelW[i + 1], keelY[i + 1], z[i + 1],
                        side * keelW[i], keelY[i], z[i], side > 0);
            }
            quad(b,
                    -keelW[i], keelY[i], z[i],
                    -keelW[i + 1], keelY[i + 1], z[i + 1],
                    keelW[i + 1], keelY[i + 1], z[i + 1],
                    keelW[i], keelY[i], z[i], false);
        }
        cap(b, z[0], topW[0], topY[0], chineW[0], chineY[0], keelW[0], keelY[0], true);
        cap(b, z[z.length - 1], topW[z.length - 1], topY[z.length - 1],
                chineW[z.length - 1], chineY[z.length - 1],
                keelW[z.length - 1], keelY[z.length - 1], false);
        return b.toArray();
    }

    static float[] frigateUpperHullBand() {
        float[] z = {-12.4f, -11.0f, -9.0f, -6.3f, -2.8f, 1.2f, 5.2f, 8.5f, 10.8f, 12.4f};
        float[] topW = {2.70f, 3.08f, 3.46f, 3.70f, 3.78f, 3.74f, 3.34f, 2.60f, 1.28f, 0.14f};
        float[] topY = {1.32f, 1.42f, 1.50f, 1.58f, 1.62f, 1.60f, 1.52f, 1.38f, 1.04f, 0.72f};
        float[] chineW = {2.18f, 2.58f, 3.02f, 3.28f, 3.38f, 3.34f, 2.88f, 2.04f, 0.76f, 0.04f};
        float[] chineY = {-0.34f, -0.64f, -0.94f, -1.18f, -1.30f, -1.22f, -1.00f, -0.60f, -0.06f, 0.56f};
        Builder b = new Builder();
        for (int i = 0; i < z.length - 1; i++) {
            for (int side = -1; side <= 1; side += 2) {
                quad(b,
                        side * topW[i] * 1.005f, topY[i] + 0.004f, z[i],
                        side * topW[i + 1] * 1.005f, topY[i + 1] + 0.004f, z[i + 1],
                        side * chineW[i + 1] * 1.005f, chineY[i + 1] + 0.004f, z[i + 1],
                        side * chineW[i] * 1.005f, chineY[i] + 0.004f, z[i], side > 0);
            }
        }
        return b.toArray();
    }

    static float[] frigateTaperedDeck() {
        float[] z = {-12.4f, -11.0f, -9.0f, -6.3f, -2.8f, 1.2f, 5.2f, 8.5f, 10.8f, 12.4f};
        float[] hullW = {2.70f, 3.08f, 3.46f, 3.70f, 3.78f, 3.74f, 3.34f, 2.60f, 1.28f, 0.14f};
        float[] hullY = {1.32f, 1.42f, 1.50f, 1.58f, 1.62f, 1.60f, 1.52f, 1.38f, 1.04f, 0.72f};
        float[] deckW = new float[hullW.length];
        for (int i = 0; i < hullW.length; i++) deckW[i] = hullW[i] + 0.055f;
        Builder b = new Builder();
        for (int i = 0; i < z.length - 1; i++) {
            quad(b, -deckW[i], 1.42f, z[i], -deckW[i + 1], 1.42f, z[i + 1],
                    deckW[i + 1], 1.42f, z[i + 1], deckW[i], 1.42f, z[i], false);
            quad(b,
                    -deckW[i], 1.42f, z[i],
                    -deckW[i + 1], 1.42f, z[i + 1],
                    -hullW[i + 1], hullY[i + 1], z[i + 1],
                    -hullW[i], hullY[i], z[i], false);
            quad(b,
                    deckW[i], 1.42f, z[i],
                    hullW[i], hullY[i], z[i],
                    hullW[i + 1], hullY[i + 1], z[i + 1],
                    deckW[i + 1], 1.42f, z[i + 1], false);
        }
        quad(b,
                -deckW[0], 1.42f, z[0], deckW[0], 1.42f, z[0],
                hullW[0], hullY[0], z[0], -hullW[0], hullY[0], z[0], true);
        int last = z.length - 1;
        quad(b,
                -deckW[last], 1.42f, z[last], -hullW[last], hullY[last], z[last],
                hullW[last], hullY[last], z[last], deckW[last], 1.42f, z[last], false);
        return b.toArray();
    }

    static float[] cylinder(int segments) {
        Builder b = new Builder();
        for (int i = 0; i < segments; i++) {
            float a = GameState.PI * 2f * i / segments;
            float n = GameState.PI * 2f * (i + 1) / segments;
            float ax = (float)Math.cos(a), ay = (float)Math.sin(a);
            float bx = (float)Math.cos(n), by = (float)Math.sin(n);
            quad(b, ax, ay, -0.5f, bx, by, -0.5f, bx, by, 0.5f, ax, ay, 0.5f, false);
            triangle(b, 0f, 0f, -0.5f, bx, by, -0.5f, ax, ay, -0.5f);
            triangle(b, 0f, 0f, 0.5f, ax, ay, 0.5f, bx, by, 0.5f);
        }
        return b.toArray();
    }

    static float[] torus(int majorSegments, int minorSegments) {
        Builder b = new Builder();
        for (int major = 0; major < majorSegments; major++) {
            float a0 = GameState.PI * 2f * major / majorSegments;
            float a1 = GameState.PI * 2f * (major + 1) / majorSegments;
            for (int minor = 0; minor < minorSegments; minor++) {
                float b0 = GameState.PI * 2f * minor / minorSegments;
                float b1 = GameState.PI * 2f * (minor + 1) / minorSegments;
                float[] p00 = torusPoint(a0, b0);
                float[] p10 = torusPoint(a1, b0);
                float[] p11 = torusPoint(a1, b1);
                float[] p01 = torusPoint(a0, b1);
                quad(b, p00[0], p00[1], p00[2], p10[0], p10[1], p10[2],
                        p11[0], p11[1], p11[2], p01[0], p01[1], p01[2], false);
            }
        }
        return b.toArray();
    }

    private static float[] torusPoint(float major, float minor) {
        float radius = 0.76f + 0.10f * (float)Math.cos(minor);
        return new float[]{radius * (float)Math.cos(major), radius * (float)Math.sin(major),
                0.10f * (float)Math.sin(minor)};
    }

    private static void cap(Builder b, float z, float topW, float topY,
                            float chineW, float chineY, float keelW, float keelY,
                            boolean reverse) {
        quad(b, -topW, topY, z, topW, topY, z,
                chineW, chineY, z, -chineW, chineY, z, reverse);
        quad(b, -chineW, chineY, z, chineW, chineY, z,
                keelW, keelY, z, -keelW, keelY, z, reverse);
    }

    private static void quad(Builder b,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             boolean reverse) {
        if (reverse) {
            triangle(b, ax, ay, az, cx, cy, cz, bx, by, bz);
            triangle(b, ax, ay, az, dx, dy, dz, cx, cy, cz);
        } else {
            triangle(b, ax, ay, az, bx, by, bz, cx, cy, cz);
            triangle(b, ax, ay, az, cx, cy, cz, dx, dy, dz);
        }
    }

    private static void triangle(Builder b,
                                 float ax, float ay, float az, float bx, float by, float bz,
                                 float cx, float cy, float cz) {
        b.add(ax, ay, az); b.add(bx, by, bz); b.add(cx, cy, cz);
    }

    private static final class Builder {
        private final ArrayList<Float> values = new ArrayList<>();
        void add(float x, float y, float z) { values.add(x); values.add(y); values.add(z); }
        float[] toArray() {
            float[] result = new float[values.size()];
            for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
            return result;
        }
    }
}
