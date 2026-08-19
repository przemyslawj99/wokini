package pl.kursnahoryzont.game;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

final class WorldRenderer implements GLSurfaceView.Renderer {
    // Jib luff geometry: both sail anchors lie directly on the visible forestay.
    private static final float JIB_STAY_TOP_Y = 10.45f;
    private static final float JIB_STAY_TOP_Z = -0.22f;
    private static final float JIB_STAY_TACK_Y = 1.62f;
    private static final float JIB_STAY_TACK_Z = 6.35f;
    private static final float JIB_HEAD_Y = 8.85f;
    private static final float JIB_HEAD_Z = 0.9705f;
    private static final float JIB_TACK_Y = 1.76f;
    private static final float JIB_TACK_Z = 6.2458f;
    private static final float JIB_CLEW_Y = 3.25f;
    private static final float JIB_CLEW_Z = 0.70f;
    private static final float[] BROWN = {0.18f, 0.08f, 0.032f, 1f};
    private static final float[] WOOD = {0.33f, 0.18f, 0.075f, 1f};
    private static final float[] LIGHT_WOOD = {0.48f, 0.29f, 0.12f, 1f};
    private static final float[] DARK = {0.06f, 0.035f, 0.024f, 1f};
    private static final float[] SAIL = {0.90f, 0.84f, 0.72f, 1f};
    private static final float[] SAIL_DARK = {0.67f, 0.59f, 0.40f, 1f};
    private static final float[] WATER = {0.028f, 0.27f, 0.39f, 1f};
    private static final float[] FOAM = {0.82f, 0.93f, 0.96f, 0.86f};
    private static final float[] FOAM_SOFT = {0.62f, 0.82f, 0.88f, 0.48f};
    private static final float[] FOAM_FAINT = {0.48f, 0.70f, 0.78f, 0.24f};
    private static final float[] SAND = {0.72f, 0.61f, 0.34f, 1f};
    private static final float[] GRASS = {0.16f, 0.39f, 0.14f, 1f};
    private static final float[] LEAF = {0.10f, 0.29f, 0.09f, 1f};
    private static final float[] WHITE = {0.88f, 0.82f, 0.69f, 1f};
    private static final float[] RED = {0.43f, 0.10f, 0.06f, 1f};
    private static final float[] ROPE = {0.56f, 0.42f, 0.22f, 1f};
    private static final float[] BRASS = {0.58f, 0.42f, 0.20f, 1f};
    private static final float[] GLASS = {0.23f, 0.49f, 0.57f, 1f};
    private static final float[] IRON = {0.16f, 0.18f, 0.17f, 1f};
    private static final float[] MOON = {0.88f, 0.91f, 0.82f, 1f};
    private static final float[] SUN = {1f, 0.52f, 0.16f, 1f};
    private static final float[] CLOUD = {0.72f, 0.76f, 0.76f, 1f};
    private static final float[] HIGHLIGHT = {1f, 0.72f, 0.16f, 1f};
    private static final float[] HULL_RED = {0.21f, 0.07f, 0.04f, 1f};
    private static final float[] OCHRE = {0.53f, 0.36f, 0.16f, 1f};
    private static final float[] COPPER = {0.39f, 0.19f, 0.09f, 1f};
    private static final float[] LANTERN = {1f, 0.68f, 0.18f, 1f};

    private final GameState state;
    private final float[] projection = new float[16];
    private final float[] view = new float[16];
    private final float[] vp = new float[16];
    private final float[] identity = new float[16];
    private final float[] shipMatrix = new float[16];
    private int program;
    private int aPosition;
    private int uMvp;
    private int uColor;
    private int uTime;
    private int uWater;
    private int uStorm;
    private int uShipHeading;
    private Mesh cube;
    private Mesh plane;
    private Mesh ocean;
    private Mesh oceanLow;
    private Mesh oceanMedium;
    private Mesh oceanHigh;
    private int activeWaterQuality = -1;
    private Mesh hull;
    private Mesh upperHull;
    private Mesh deck;
    private Mesh brigHull;
    private Mesh brigUpperHull;
    private Mesh brigDeck;
    private Mesh frigateHull;
    private Mesh frigateUpperHull;
    private Mesh frigateDeck;
    private Mesh cone;
    private Mesh sphere;
    private Mesh cylinder;
    private Mesh torus;
    private Mesh mainSail;
    private Mesh jib;
    private Mesh brigSail;
    private Mesh triSail;
    private long lastNanos;
    private float elapsed;
    private float frameDt;
    private float visualBoomAngle;
    private float visualJibClewX;
    private int currentSailSide = 1;
    private int width;
    private int height;

    WorldRenderer(GameState state) { this.state = state; }

    @Override public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        String vertex = "uniform mat4 uMVP;uniform float uTime,uWater,uStorm,uShipHeading;attribute vec3 aPosition;"
                + "varying float vWave;varying vec2 vBoat;varying vec2 vSea;void main(){vec3 p=aPosition;"
                + "float ch=cos(uShipHeading),sh=sin(uShipHeading);vBoat=vec2(ch*p.x-sh*p.z,sh*p.x+ch*p.z);"
                + "vSea=p.xz;vWave=p.y;if(uWater>.5){"
                + "float s1=sin(p.x*.041+p.z*.020+uTime*.78);"
                + "float s2=sin(-p.x*.025+p.z*.058-uTime*.91)*.72;"
                + "float s3=sin(p.x*.018+p.z*.034+uTime*.47)*.48;"
                + "float c1=sin(p.x*.15+p.z*.11+uTime*1.86)*.22;"
                + "float c2=sin(p.x*.26-p.z*.14-uTime*2.28)*.13;"
                + "float w=s1+s2+s3+c1+c2;float amp=.18+uStorm*.32;p.y+=w*amp;vWave=w;}"
                + "gl_Position=uMVP*vec4(p,1.);}";
        String fragment = "precision mediump float;uniform vec4 uColor;uniform float uWater,uTime,uStorm;"
                + "varying float vWave;varying vec2 vBoat;varying vec2 vSea;void main(){if(uWater>.5){"
                + "float micro=sin(vSea.x*.27+vSea.y*.18+uTime*1.45)*.5+sin(vSea.y*.33-vSea.x*.11-uTime*1.12)*.5;"
                + "float light=clamp(.46+vWave*.17+micro*.045,0.,1.);"
                + "vec3 deep=uColor.rgb*vec3(.38,.58,.73);vec3 mid=uColor.rgb*vec3(.86,1.05,1.14);"
                + "vec3 c=mix(deep,mid,light);float crest=smoothstep(.68,1.42,vWave+micro*.12);"
                + "float gloss=pow(max(0.,.48+vWave*.20+micro*.09),5.)*.20;"
                + "c+=vec3(gloss*.34,gloss*.42,gloss*.47);"
                + "float whitecap=crest*clamp(.08+uStorm*.74,0.,.62);c=mix(c,vec3(.80,.91,.94),whitecap);"
                + "gl_FragColor=vec4(c,1.);return;}float l=.94+vWave*.12;gl_FragColor=vec4(uColor.rgb*l,uColor.a);}";
        program = createProgram(vertex, fragment);
        aPosition = GLES20.glGetAttribLocation(program, "aPosition");
        uMvp = GLES20.glGetUniformLocation(program, "uMVP");
        uColor = GLES20.glGetUniformLocation(program, "uColor");
        uTime = GLES20.glGetUniformLocation(program, "uTime");
        uWater = GLES20.glGetUniformLocation(program, "uWater");
        uStorm = GLES20.glGetUniformLocation(program, "uStorm");
        uShipHeading = GLES20.glGetUniformLocation(program, "uShipHeading");
        cube = new Mesh(CUBE);
        plane = new Mesh(PLANE);
        oceanLow = makeOceanGrid(32, 620f);
        oceanMedium = makeOceanGrid(48, 620f);
        oceanHigh = makeOceanGrid(64, 620f);
        ocean = oceanMedium;
        hull = new Mesh(ShipGeometry.detailedHull());
        upperHull = new Mesh(ShipGeometry.upperHullBand());
        deck = new Mesh(ShipGeometry.taperedDeck());
        brigHull = new Mesh(ShipGeometry.brigDetailedHull());
        brigUpperHull = new Mesh(ShipGeometry.brigUpperHullBand());
        brigDeck = new Mesh(ShipGeometry.brigTaperedDeck());
        frigateHull = new Mesh(ShipGeometry.frigateDetailedHull());
        frigateUpperHull = new Mesh(ShipGeometry.frigateUpperHullBand());
        frigateDeck = new Mesh(ShipGeometry.frigateTaperedDeck());
        cone = makeCone(12);
        sphere = makeSphere(12, 8);
        cylinder = new Mesh(ShipGeometry.cylinder(12));
        torus = new Mesh(ShipGeometry.torus(18, 6));
        mainSail = new Mesh(buildMainSail(0f));
        jib = new Mesh(buildJib(0f));
        brigSail = new Mesh(BRIG_SAIL);
        triSail = new Mesh(TRI_SAIL);
        Matrix.setIdentityM(identity, 0);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glClearDepthf(1f);
        lastNanos = System.nanoTime();
    }

    @Override public void onSurfaceChanged(GL10 unused, int w, int h) {
        width = Math.max(1, w);
        height = Math.max(1, h);
        GLES20.glViewport(0, 0, width, height);
        Matrix.perspectiveM(projection, 0, 64f, width / (float)height, 0.08f, 1250f);
    }

    @Override public void onDrawFrame(GL10 unused) {
        long now = System.nanoTime();
        float dt = (now - lastNanos) / 1_000_000_000f;
        lastNanos = now;
        state.step(dt);
        frameDt = Math.min(dt, 0.05f);
        if (!state.paused) elapsed += frameDt;

        float sunHeight = (float)Math.sin((state.worldHours - 6f) / 12f * Math.PI);
        float daylight = GameState.clamp(sunHeight, 0f, 1f);
        float sunset = Math.max(GameState.clamp(1f - Math.abs(state.worldHours - 18f) / 2.2f, 0f, 1f),
                GameState.clamp(1f - Math.abs(state.worldHours - 6f) / 1.7f, 0f, 1f));
        float stormShade = 1f - state.storm * 0.55f;
        float skyR = (0.018f + daylight * 0.12f + sunset * 0.34f) * stormShade;
        float skyG = (0.035f + daylight * 0.34f + sunset * 0.11f) * stormShade;
        float skyB = (0.09f + daylight * 0.46f - sunset * 0.18f) * stormShade;
        GLES20.glClearColor(skyR, skyG, Math.max(0.025f, skyB), 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glUseProgram(program);

        syncQualityAssets();
        setupCamera();
        drawSky(daylight, sunset);
        drawOcean();
        drawWakeAndSpray();
        for (int i = 0; i < GameState.PORT_X.length; i++) drawIsland(i);
        drawShip();
    }

    private void setupCamera() {
        float eyeX;
        float eyeY;
        float eyeZ;
        float yaw;
        float wave = (float)Math.sin(elapsed * 1.2f) * (0.055f + state.storm * 0.38f);
        float cameraRoll = (float)Math.sin(elapsed * 1.7f) * (0.006f + state.storm * 0.055f);
        if (state.onLand) {
            eyeX = state.playerWorldX;
            eyeZ = state.playerWorldZ;
            eyeY = terrainHeight(eyeX, eyeZ) + 1.7f;
            yaw = state.lookYaw;
        } else if (state.atHelm && state.helmThirdPerson) {
            float orbit = state.shipHeading + state.lookYaw;
            float cameraDistance = 13.5f * state.shipScaleZ();
            eyeX = state.shipX - (float)Math.sin(orbit) * cameraDistance;
            eyeZ = state.shipZ - (float)Math.cos(orbit) * cameraDistance;
            eyeY = 6.4f * state.shipScaleY() + wave + (float)Math.sin(state.lookPitch) * 3.0f;
            Matrix.setLookAtM(view, 0, eyeX, eyeY, eyeZ,
                    state.shipX, 1.8f * state.shipScaleY() + wave, state.shipZ,
                    (float)Math.sin(cameraRoll), (float)Math.cos(cameraRoll), 0f);
            Matrix.multiplyMM(vp, 0, projection, 0, view, 0);
            return;
        } else {
            float lx = (state.atHelm ? 0f : state.playerX) * state.shipScaleX();
            float lz = (state.atHelm ? -6.35f : state.playerZ) * state.shipScaleZ();
            float ly = (state.belowDeck ? 0.92f : (state.atHelm ? 2.75f : 2.9f)) * state.shipScaleY();
            float c = (float)Math.cos(state.shipHeading);
            float s = (float)Math.sin(state.shipHeading);
            eyeX = state.shipX + c * lx + s * lz;
            eyeZ = state.shipZ - s * lx + c * lz;
            eyeY = ly + wave;
            yaw = state.shipHeading + state.lookYaw;
        }
        float cp = (float)Math.cos(state.lookPitch);
        float dirX = (float)Math.sin(yaw) * cp;
        float dirZ = (float)Math.cos(yaw) * cp;
        float dirY = (float)Math.sin(state.lookPitch);
        Matrix.setLookAtM(view, 0, eyeX, eyeY, eyeZ,
                eyeX + dirX, eyeY + dirY, eyeZ + dirZ,
                state.onLand ? 0f : (float)Math.sin(cameraRoll),
                state.onLand ? 1f : (float)Math.cos(cameraRoll), 0f);
        Matrix.multiplyMM(vp, 0, projection, 0, view, 0);
    }

    private void drawSky(float daylight, float sunset) {
        float night = 1f - daylight;
        if (night > 0.18f) {
            int starCount = state.graphicsQuality == 0 ? 18 : state.graphicsQuality == 1 ? 34 : 56;
            float[] star = {0.68f + night * 0.28f, 0.70f + night * 0.27f,
                    0.72f + night * 0.27f, 1f};
            for (int i = 0; i < starCount; i++) {
                float angle = i * 2.39996f;
                float distance = 260f + (i % 7) * 19f;
                float x = state.shipX + (float)Math.cos(angle) * distance;
                float z = state.shipZ + (float)Math.sin(angle) * distance;
                float y = 42f + ((i * 37) % 115);
                float size = 0.32f + (i % 4) * 0.13f;
                drawBox(identity, x, y, z, size, size, size, star);
            }
            float moonAngle = (state.worldHours - 18f) / 12f * GameState.PI;
            float moonX = state.shipX + (float)Math.cos(moonAngle) * 285f;
            float moonZ = state.shipZ + (float)Math.sin(moonAngle) * 285f;
            float moonY = 52f + (float)Math.sin(moonAngle) * 105f;
            draw(sphere, model(identity, moonX, moonY, moonZ, 0f, 8.5f, 8.5f, 8.5f), MOON);
        }

        if (daylight > 0.08f || sunset > 0.12f) {
            float sunAngle = (state.worldHours - 6f) / 12f * GameState.PI;
            float sunX = state.shipX + (float)Math.cos(sunAngle) * 300f;
            float sunZ = state.shipZ - 180f;
            float sunY = 34f + Math.max(0f, (float)Math.sin(sunAngle)) * 155f;
            draw(sphere, model(identity, sunX, sunY, sunZ, 0f, 7.2f, 7.2f, 7.2f), SUN);
        }

        int cloudCount = state.graphicsQuality == 0 ? 4 : state.graphicsQuality == 1 ? 7 : 11;
        float cloudShade = 0.58f + daylight * 0.34f - state.storm * 0.25f;
        float[] cloudColor = {CLOUD[0] * cloudShade, CLOUD[1] * cloudShade,
                CLOUD[2] * cloudShade, 1f};
        for (int i = 0; i < cloudCount; i++) {
            float drift = elapsed * (1.4f + state.windStrength * 0.13f) + i * 61f;
            float x = state.shipX + wrapOffset(drift, 360f) - 180f;
            float z = state.shipZ - 125f + (i % 5) * 62f;
            float y = 28f + (i % 4) * 8f;
            float scale = 6f + (i % 3) * 2.2f;
            draw(sphere, model(identity, x, y, z, 0f, scale * 1.8f, scale * 0.55f, scale),
                    cloudColor);
            draw(sphere, model(identity, x + scale, y + 1.2f, z + 1.5f, 0f,
                    scale * 1.25f, scale * 0.68f, scale * 0.82f), cloudColor);
            draw(sphere, model(identity, x - scale, y - 0.4f, z - 1.2f, 0f,
                    scale * 1.1f, scale * 0.48f, scale * 0.76f), cloudColor);
        }
    }

    private float wrapOffset(float value, float span) {
        float result = value % span;
        if (result < 0f) result += span;
        return result;
    }

    private void syncQualityAssets() {
        if (activeWaterQuality == state.graphicsQuality) return;
        activeWaterQuality = state.graphicsQuality;
        ocean = activeWaterQuality == 0 ? oceanLow : activeWaterQuality == 1 ? oceanMedium : oceanHigh;
    }

    private void drawOcean() {
        float seaLevel = -0.40f;
        float[] m = model(identity, state.shipX, seaLevel, state.shipZ, 0f, 1f, 1f, 1f);
        drawWater(ocean, m, WATER);
    }

    private void drawWakeAndSpray() {
        if (state.shipSpeed < 0.12f || state.moored || state.anchorDropped) return;
        float[] wakeMatrix = new float[16];
        Matrix.setIdentityM(wakeMatrix, 0);
        Matrix.translateM(wakeMatrix, 0, state.shipX, -0.24f, state.shipZ);
        Matrix.rotateM(wakeMatrix, 0, state.shipHeading * 180f / GameState.PI, 0f, 1f, 0f);
        float strength = GameState.clamp(state.shipSpeed / 6f, 0.12f, 1f);
        int segments = state.graphicsQuality == 0 ? 5 : state.graphicsQuality == 1 ? 8 : 12;

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDepthMask(false);

        // Soft foam patches build a widening V without rigid white bars.
        for (int i = 0; i < segments; i++) {
            float progress = i / (float)Math.max(1, segments - 1);
            float z = -7.35f - i * 1.45f;
            float spread = 0.55f + i * 0.21f;
            float wobble = (float)Math.sin(elapsed * 1.75f + i * 1.37f) * 0.13f;
            float patchW = 0.42f + progress * 0.62f;
            float patchL = 0.85f + progress * 0.90f;
            float[] color = i < 2 ? FOAM_SOFT : (i < segments / 2 ? FOAM_FAINT : FOAM_FAINT);
            for (int sideIndex = 0; sideIndex < 2; sideIndex++) {
                float side = sideIndex == 0 ? -1f : 1f;
                float x = side * (spread + wobble * side);
                draw(sphere, model(wakeMatrix, x, 0.005f, z, 0f,
                        patchW, 0.025f, patchL), color);
                if (i < segments / 2 && i % 2 == 0) {
                    draw(sphere, model(wakeMatrix, x * 0.55f, 0.002f, z - 0.38f, 0f,
                            patchW * 0.55f, 0.018f, patchL * 0.62f), FOAM_FAINT);
                }
            }
        }

        // Bow water hugs the hull as low translucent patches instead of two protruding rods.
        int bowPatches = state.graphicsQuality == 0 ? 2 : state.graphicsQuality == 1 ? 3 : 4;
        for (int sideIndex = 0; sideIndex < 2; sideIndex++) {
            float side = sideIndex == 0 ? -1f : 1f;
            for (int i = 0; i < bowPatches; i++) {
                float p = i / (float)Math.max(1, bowPatches - 1);
                float x = side * (0.34f + p * (1.20f + strength * 0.55f));
                float z = 7.55f - p * 1.95f;
                float scale = 0.34f + p * 0.30f + strength * 0.10f;
                draw(sphere, model(wakeMatrix, x, 0.008f, z, 0f,
                        scale, 0.022f, scale * 1.35f), p < 0.45f ? FOAM_SOFT : FOAM_FAINT);
            }
        }

        // Fine spray appears only when speed or sea state justifies it.
        if (strength > 0.52f || state.storm > 0.30f) {
            int splashes = state.graphicsQuality == 0 ? 1 : state.graphicsQuality == 1 ? 2 : 4;
            for (int i = 0; i < splashes; i++) {
                float side = i % 2 == 0 ? -1f : 1f;
                float phase = (elapsed * (2.0f + state.storm * 1.8f) + i * 0.41f) % 1f;
                float x = side * (0.48f + phase * 0.58f);
                float y = (float)Math.sin(phase * Math.PI) * (0.10f + strength * 0.26f + state.storm * 0.20f);
                float z = 7.30f - phase * 0.75f;
                draw(sphere, model(wakeMatrix, x, y, z, 0f,
                        0.10f, 0.11f + y * 0.28f, 0.16f), FOAM_SOFT);
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

    private void drawShip() {
        float massMotion = state.shipType == GameState.SHIP_FRIGATE ? 0.56f
                : (state.shipType == GameState.SHIP_BRIG ? 0.72f : 1f);
        float bob = (float)Math.sin(elapsed * 1.2f) * (0.055f + state.storm * 0.38f) * massMotion;
        float roll = ((float)Math.sin(elapsed * 1.7f) * (0.45f + state.storm * 5.0f)
                + state.shipLateralSpeed * 0.65f) * massMotion;
        float pitch = (float)Math.sin(elapsed * 1.05f + 0.9f) * (0.22f + state.storm * 2.4f) * massMotion;
        Matrix.setIdentityM(shipMatrix, 0);
        Matrix.translateM(shipMatrix, 0, state.shipX, bob, state.shipZ);
        Matrix.rotateM(shipMatrix, 0, state.shipHeading * 180f / GameState.PI, 0f, 1f, 0f);
        Matrix.rotateM(shipMatrix, 0, roll, 0f, 0f, 1f);
        Matrix.rotateM(shipMatrix, 0, pitch, 1f, 0f, 0f);
        Matrix.scaleM(shipMatrix, 0, state.shipScaleX(), state.shipScaleY(), state.shipScaleZ());

        if (state.belowDeck && !state.onLand) {
            drawInterior();
            return;
        }

        if (state.shipType == GameState.SHIP_FRIGATE) {
            draw(frigateHull, shipMatrix, HULL_RED);
            draw(frigateUpperHull, shipMatrix, BROWN);
            draw(frigateDeck, shipMatrix, LIGHT_WOOD);
            drawHullTrim();
            drawFrigateHullAccents();
            drawFrigateRailings();
            drawFrigateCabin();
            drawFrigateHatch();
            drawFrigateDeckDetails();
        } else if (state.shipType == GameState.SHIP_BRIG) {
            draw(brigHull, shipMatrix, HULL_RED);
            draw(brigUpperHull, shipMatrix, BROWN);
            draw(brigDeck, shipMatrix, LIGHT_WOOD);
            drawHullTrim();
            drawBrigHullAccents();
            drawBrigRailings();
            drawBrigCabin();
            drawBrigHatch();
            drawBrigDeckDetails();
        } else {
            draw(hull, shipMatrix, HULL_RED);
            draw(upperHull, shipMatrix, BROWN);
            draw(deck, shipMatrix, LIGHT_WOOD);
            drawHullTrim();
            drawRailings();
            drawCabin();
            drawHatch();
            drawDeckDetails();
        }
        if (state.shipType != GameState.SHIP_FRIGATE) drawOars();

        if (state.shipType == GameState.SHIP_FRIGATE) drawFrigateCannons();
        else if (state.shipType == GameState.SHIP_BRIG) drawBrigCannons();
        else drawSloopCannons();

        if (state.shipType == GameState.SHIP_FRIGATE) drawFrigateRig();
        else if (state.shipType == GameState.SHIP_BRIG) drawBrigRig();
        else drawSloopRig();


        drawWheel();
        drawRoundBeam(shipMatrix, 0f, 1.5f, 5.3f, 0f, 2.35f, 5.3f, 0.25f, WOOD);
        drawRoundBeam(shipMatrix, -1.95f, 1.38f, -6.6f, -1.95f, 1.92f, -6.6f, 0.10f, BRASS);
        drawRoundBeam(shipMatrix, 1.95f, 1.38f, -6.6f, 1.95f, 1.92f, -6.6f, 0.10f, BRASS);
        draw(sphere, model(shipMatrix, -1.95f, 2.0f, -6.6f, 0f, 0.16f, 0.22f, 0.16f), LANTERN);
        draw(sphere, model(shipMatrix, 1.95f, 2.0f, -6.6f, 0f, 0.16f, 0.22f, 0.16f), LANTERN);

        drawAnchorAndMooring();

        // Loaded cargo is rendered in the hold, not on the walking deck.
    }


    private void drawSloopCannons() {
        drawBroadsideCannons(1.16f, 2.03f,
                new float[]{-2.95f, 0.10f}, 0.92f, 0.070f, 0.46f, 0.15f);
    }

    private void drawBrigCannons() {
        // Four guns per side sit on the lower gun deck and fire through the gunports.
        drawBroadsideCannons(0.40f, 2.15f,
                new float[]{-4.90f, -3.35f, -1.80f, -0.25f}, 1.02f, 0.078f, 0.52f, 0.17f);
    }

    private void drawFrigateCannons() {
        // Seven guns per side on the main gun deck; the upper deck remains clear for working the ship.
        drawBroadsideCannons(0.52f, 2.32f,
                new float[]{-6.20f, -4.75f, -3.30f, -1.85f, -0.40f, 1.05f, 2.50f},
                1.18f, 0.088f, 0.60f, 0.18f);
    }

    private void drawBroadsideCannons(float deckY, float sideX, float[] zPositions,
                                      float barrelLength, float barrelRadius,
                                      float carriageLength, float wheelRadius) {
        for (float z : zPositions) {
            for (int sideIndex = 0; sideIndex < 2; sideIndex++) {
                float side = sideIndex == 0 ? -1f : 1f;
                float centerX = side * sideX;
                float inboardX = side * (sideX - barrelLength * 0.30f);
                float muzzleX = side * (sideX + barrelLength * 0.62f);
                float barrelY = deckY + 0.17f;

                drawRoundBeam(shipMatrix, inboardX, barrelY, z,
                        muzzleX, barrelY + 0.02f, z, barrelRadius, IRON);
                drawBox(shipMatrix, side * (sideX + barrelLength * 0.68f), barrelY + 0.02f, z,
                        0.05f, 0.05f, 0.05f, BRASS);

                drawBox(shipMatrix, centerX, deckY + 0.03f, z,
                        carriageLength, 0.16f, 0.46f, WOOD);
                drawBox(shipMatrix, side * (sideX - 0.02f), deckY + 0.10f, z,
                        carriageLength * 0.50f, 0.08f, 0.34f, DARK);

                float axleZ = 0.20f;
                float inboardWheelX = side * (sideX - carriageLength * 0.22f);
                float outboardWheelX = side * (sideX + carriageLength * 0.22f);
                draw(sphere, model(shipMatrix, inboardWheelX, deckY - 0.02f, z - axleZ,
                        0f, wheelRadius, wheelRadius, wheelRadius), DARK);
                draw(sphere, model(shipMatrix, inboardWheelX, deckY - 0.02f, z + axleZ,
                        0f, wheelRadius, wheelRadius, wheelRadius), DARK);
                draw(sphere, model(shipMatrix, outboardWheelX, deckY - 0.02f, z - axleZ,
                        0f, wheelRadius, wheelRadius, wheelRadius), DARK);
                draw(sphere, model(shipMatrix, outboardWheelX, deckY - 0.02f, z + axleZ,
                        0f, wheelRadius, wheelRadius, wheelRadius), DARK);

                drawBeam(shipMatrix, centerX, deckY + 0.15f, z,
                        side * (sideX - 0.52f), deckY + 0.28f, z + 0.22f,
                        0.018f, ROPE);
                drawBeam(shipMatrix, centerX, deckY + 0.15f, z,
                        side * (sideX - 0.52f), deckY + 0.28f, z - 0.22f,
                        0.018f, ROPE);
            }
        }
    }

    private void drawSloopRig() {
        drawRoundBeam(shipMatrix, 0f, 1.15f, -0.3f, 0f, 11.55f, -0.3f, 0.16f, DARK);
        drawRoundBeam(shipMatrix, 0f, 1.68f, 5.65f, 0f, 1.92f, 10.15f, 0.095f, WOOD);

        float relativeWind = state.relativeWind();
        float sideForce = (float)Math.sin(relativeWind);
        int windSide = sideForce >= 0f ? 1 : -1;
        int previousSailSide = currentSailSide;
        if (state.sailRaised && Math.abs(sideForce) > 0.12f) currentSailSide = windSide;
        float targetBoomAngle = (10f + state.sailTrim * 52f) * currentSailSide;
        boolean deadDownwind = Math.abs(Math.abs(relativeWind) - GameState.PI) < (12f * GameState.PI / 180f);
        int jibSide = deadDownwind ? currentSailSide : -currentSailSide;
        float targetJibClewX = (0.48f + state.sailTrim * 1.95f) * jibSide;
        float sailSnap = currentSailSide != previousSailSide ? 10.5f : 6.5f;
        visualBoomAngle += (targetBoomAngle - visualBoomAngle) * GameState.clamp(frameDt * sailSnap, 0f, 1f);
        visualJibClewX += (targetJibClewX - visualJibClewX)
                * GameState.clamp(frameDt * (sailSnap + 1.8f), 0f, 1f);

        float[] sailParent = copy(shipMatrix);
        Matrix.translateM(sailParent, 0, 0f, 0f, -0.3f);
        Matrix.rotateM(sailParent, 0, visualBoomAngle, 0f, 1f, 0f);
        if (state.sailRaised) {
            float flutter = (float)Math.sin(elapsed * (2.8f + state.windStrength * 0.18f))
                    * (1f - state.sailBillow()) * 0.16f;
            mainSail.update(buildMainSail(currentSailSide * state.sailBillow() * 0.72f + flutter));
            jib.update(buildJib(visualJibClewX));
            draw(mainSail, sailParent, SAIL);
            draw(jib, shipMatrix, SAIL_DARK);
            drawSailDetails(sailParent, visualJibClewX);
        } else {
            drawRoundBeam(sailParent, 0f, 3.16f, -0.25f,
                    0f, 3.16f, -4.75f, 0.18f, SAIL_DARK);
        }
        drawRoundBeam(sailParent, 0f, 3.0f, -0.15f,
                0f, 3.0f, -5.1f, 0.11f, DARK);
        drawRiggingAndLines(visualBoomAngle);
    }

    private void drawBrigRig() {
        float relativeWind = state.relativeWind();
        float sideForce = (float)Math.sin(relativeWind);
        int windSide = sideForce >= 0f ? 1 : -1;
        if (state.sailRaised && Math.abs(sideForce) > 0.10f) currentSailSide = windSide;
        float targetBrace = (10f + state.sailTrim * 30f) * currentSailSide;
        visualBoomAngle += (targetBrace - visualBoomAngle)
                * GameState.clamp(frameDt * 4.3f, 0f, 1f);

        float aftZ = -1.05f;
        float foreZ = 2.95f;
        float bowZ = 7.65f;
        drawRoundBeam(shipMatrix, 0f, 1.14f, aftZ, 0f, 14.2f, aftZ, 0.18f, DARK);
        drawRoundBeam(shipMatrix, 0f, 1.14f, foreZ, 0f, 13.1f, foreZ, 0.17f, DARK);
        drawRoundBeam(shipMatrix, 0f, 1.72f, bowZ, 0f, 2.62f, 11.95f, 0.11f, WOOD);
        drawBeam(shipMatrix, 0f, 2.58f, 11.75f, -1.08f, 1.52f, 7.15f, 0.022f, ROPE);
        drawBeam(shipMatrix, 0f, 2.58f, 11.75f, 1.08f, 1.52f, 7.15f, 0.022f, ROPE);
        drawBeam(shipMatrix, 0f, 2.26f, 10.8f, -0.65f, -0.10f, 7.3f, 0.018f, ROPE);
        drawBeam(shipMatrix, 0f, 2.26f, 10.8f, 0.65f, -0.10f, 7.3f, 0.018f, ROPE);

        drawBeam(shipMatrix, 0f, 13.9f, aftZ, -2.25f, 1.56f, -5.95f, 0.03f, ROPE);
        drawBeam(shipMatrix, 0f, 13.9f, aftZ, 2.25f, 1.56f, -5.95f, 0.03f, ROPE);
        drawBeam(shipMatrix, 0f, 12.8f, foreZ, -1.70f, 1.48f, 6.35f, 0.03f, ROPE);
        drawBeam(shipMatrix, 0f, 12.8f, foreZ, 1.70f, 1.48f, 6.35f, 0.03f, ROPE);
        drawBeam(shipMatrix, 0f, 13.3f, aftZ, 0f, 12.1f, foreZ, 0.025f, ROPE);
        drawBeam(shipMatrix, 0f, 12.8f, foreZ, 0f, 2.55f, 11.65f, 0.026f, ROPE);

        drawBrigYardAndSail(aftZ, 3.15f, 3.15f, 2.95f, visualBoomAngle, SAIL);
        drawBrigYardAndSail(aftZ, 6.72f, 2.55f, 2.28f, visualBoomAngle, SAIL_DARK);
        drawBrigYardAndSail(aftZ, 9.65f, 1.85f, 1.45f, visualBoomAngle, SAIL);
        drawBrigYardAndSail(foreZ, 2.95f, 3.05f, 3.10f, visualBoomAngle, SAIL);
        drawBrigYardAndSail(foreZ, 6.48f, 2.60f, 2.40f, visualBoomAngle, SAIL_DARK);
        drawBrigYardAndSail(foreZ, 9.48f, 1.95f, 1.60f, visualBoomAngle, SAIL);

        if (state.sailRaised) {
            drawBrigHeadsail(visualBoomAngle * 0.040f);
        } else {
            drawRoundBeam(shipMatrix, -2.7f, 6.35f, aftZ, 2.7f, 6.35f, aftZ, 0.13f, SAIL_DARK);
            drawRoundBeam(shipMatrix, -2.55f, 6.10f, foreZ, 2.55f, 6.10f, foreZ, 0.13f, SAIL_DARK);
            drawRoundBeam(shipMatrix, -1.70f, 10.35f, aftZ, 1.70f, 10.35f, aftZ, 0.10f, SAIL_DARK);
            drawRoundBeam(shipMatrix, -1.82f, 10.45f, foreZ, 1.82f, 10.45f, foreZ, 0.10f, SAIL_DARK);
        }
    }


    private void drawFrigateRig() {
        float relativeWind = state.relativeWind();
        float sideForce = (float)Math.sin(relativeWind);
        int windSide = sideForce >= 0f ? 1 : -1;
        if (state.sailRaised && Math.abs(sideForce) > 0.08f) currentSailSide = windSide;
        float targetBrace = (12f + state.sailTrim * 24f) * currentSailSide;
        visualBoomAngle += (targetBrace - visualBoomAngle)
                * GameState.clamp(frameDt * 3.4f, 0f, 1f);

        float mizzenZ = -4.1f;
        float mainZ = -0.1f;
        float foreZ = 4.65f;
        float bowZ = 10.2f;
        drawRoundBeam(shipMatrix, 0f, 1.36f, mizzenZ, 0f, 13.2f, mizzenZ, 0.17f, DARK);
        drawRoundBeam(shipMatrix, 0f, 1.36f, mainZ, 0f, 16.8f, mainZ, 0.19f, DARK);
        drawRoundBeam(shipMatrix, 0f, 1.36f, foreZ, 0f, 15.4f, foreZ, 0.18f, DARK);
        drawRoundBeam(shipMatrix, 0f, 1.90f, bowZ, 0f, 2.85f, 15.10f, 0.12f, WOOD);

        drawBeam(shipMatrix, 0f, 16.3f, mainZ, -2.65f, 1.72f, -7.85f, 0.03f, ROPE);
        drawBeam(shipMatrix, 0f, 16.3f, mainZ, 2.65f, 1.72f, -7.85f, 0.03f, ROPE);
        drawBeam(shipMatrix, 0f, 14.9f, foreZ, -2.28f, 1.70f, 8.20f, 0.03f, ROPE);
        drawBeam(shipMatrix, 0f, 14.9f, foreZ, 2.28f, 1.70f, 8.20f, 0.03f, ROPE);
        drawBeam(shipMatrix, 0f, 12.9f, mizzenZ, -2.25f, 1.62f, -7.95f, 0.028f, ROPE);
        drawBeam(shipMatrix, 0f, 12.9f, mizzenZ, 2.25f, 1.62f, -7.95f, 0.028f, ROPE);
        drawBeam(shipMatrix, 0f, 15.9f, mainZ, 0f, 14.6f, foreZ, 0.028f, ROPE);
        drawBeam(shipMatrix, 0f, 15.0f, foreZ, 0f, 2.70f, 14.85f, 0.028f, ROPE);
        drawBeam(shipMatrix, 0f, 13.0f, mizzenZ, 0f, 15.6f, mainZ, 0.024f, ROPE);
        drawBeam(shipMatrix, 0f, 2.78f, 14.85f, -1.16f, 1.62f, 9.55f, 0.022f, ROPE);
        drawBeam(shipMatrix, 0f, 2.78f, 14.85f, 1.16f, 1.62f, 9.55f, 0.022f, ROPE);

        drawBrigYardAndSail(mainZ, 2.90f, 3.45f, 3.55f, visualBoomAngle, SAIL);
        drawBrigYardAndSail(mainZ, 7.15f, 2.85f, 2.85f, visualBoomAngle, SAIL_DARK);
        drawBrigYardAndSail(mainZ, 10.55f, 2.10f, 2.05f, visualBoomAngle, SAIL);

        drawBrigYardAndSail(foreZ, 2.80f, 3.30f, 3.25f, visualBoomAngle * 0.98f, SAIL);
        drawBrigYardAndSail(foreZ, 6.90f, 2.70f, 2.62f, visualBoomAngle * 0.98f, SAIL_DARK);
        drawBrigYardAndSail(foreZ, 10.20f, 2.00f, 1.90f, visualBoomAngle * 0.98f, SAIL);

        if (state.sailRaised) {
            float clewX = currentSailSide * (1.10f + state.sailTrim * 1.28f);
            drawFilledTriSail(0f, 11.65f, mizzenZ, 0f, 2.35f, mizzenZ + 0.25f,
                    clewX, 5.10f, mizzenZ - 3.05f, SAIL_DARK);
            drawBeam(shipMatrix, 0f, 11.65f, mizzenZ, 0f, 2.35f, mizzenZ + 0.25f, 0.024f, ROPE);
            drawBeam(shipMatrix, 0f, 11.65f, mizzenZ, clewX, 5.10f, mizzenZ - 3.05f, 0.020f, ROPE);
            drawBeam(shipMatrix, 0f, 2.35f, mizzenZ + 0.25f, clewX, 5.10f, mizzenZ - 3.05f, 0.020f, ROPE);
            drawFrigateHeadsails();
        } else {
            drawRoundBeam(shipMatrix, -3.15f, 6.55f, mainZ, 3.15f, 6.55f, mainZ, 0.13f, SAIL_DARK);
            drawRoundBeam(shipMatrix, -2.95f, 6.28f, foreZ, 2.95f, 6.28f, foreZ, 0.13f, SAIL_DARK);
            drawRoundBeam(shipMatrix, -1.88f, 12.15f, mainZ, 1.88f, 12.15f, mainZ, 0.10f, SAIL_DARK);
            drawRoundBeam(shipMatrix, -1.72f, 11.85f, foreZ, 1.72f, 11.85f, foreZ, 0.10f, SAIL_DARK);
            drawRoundBeam(shipMatrix, -1.0f, 7.55f, mizzenZ - 1.05f, 1.0f, 7.55f, mizzenZ - 1.05f, 0.09f, SAIL_DARK);
        }
    }

    private void drawFrigateHeadsails() {
        float jibClew = -currentSailSide * (1.20f + state.sailTrim * 1.10f);
        drawFilledTriSail(0f, 14.1f, 4.65f, 0f, 2.72f, 14.75f,
                jibClew, 5.05f, 10.35f, SAIL_DARK);
        drawBeam(shipMatrix, 0f, 14.1f, 4.65f, 0f, 2.72f, 14.75f, 0.024f, ROPE);
        drawBeam(shipMatrix, 0f, 14.1f, 4.65f, jibClew, 5.05f, 10.35f, 0.020f, ROPE);
        drawBeam(shipMatrix, 0f, 2.72f, 14.75f, jibClew, 5.05f, 10.35f, 0.020f, ROPE);

        float staysailClew = -currentSailSide * (0.88f + state.sailTrim * 0.80f);
        drawFilledTriSail(0f, 12.8f, 0.1f, 0f, 2.55f, 12.8f,
                staysailClew, 4.35f, 8.75f, SAIL);
        drawBeam(shipMatrix, 0f, 12.8f, 0.1f, 0f, 2.55f, 12.8f, 0.022f, ROPE);
        drawBeam(shipMatrix, 0f, 12.8f, 0.1f, staysailClew, 4.35f, 8.75f, 0.018f, ROPE);
        drawBeam(shipMatrix, 0f, 2.55f, 12.8f, staysailClew, 4.35f, 8.75f, 0.018f, ROPE);
    }

    private void drawFilledTriSail(float headX, float headY, float headZ,
                                   float tackX, float tackY, float tackZ,
                                   float clewX, float clewY, float clewZ,
                                   float[] color) {
        float bellyX = (headX + tackX + clewX) / 3f + (clewX - headX) * 0.08f;
        float bellyY = (headY + tackY + clewY) / 3f;
        float bellyZ = (headZ + tackZ + clewZ) / 3f + 0.18f;
        float[] sail = new float[]{
                headX, headY, headZ, tackX, tackY, tackZ, bellyX, bellyY, bellyZ,
                headX, headY, headZ, bellyX, bellyY, bellyZ, clewX, clewY, clewZ,
                tackX, tackY, tackZ, clewX, clewY, clewZ, bellyX, bellyY, bellyZ
        };
        triSail.update(sail);
        draw(triSail, shipMatrix, color);
    }

    private void drawFrigateHullAccents() {
        // Long quarterdeck and forecastle.
        drawBox(shipMatrix, 0f, 2.02f, -7.10f, 4.55f, 0.24f, 3.72f, DARK);
        drawBox(shipMatrix, 0f, 2.16f, -7.10f, 3.86f, 0.10f, 3.12f, WOOD);
        drawBox(shipMatrix, 0f, 1.88f, 8.15f, 3.75f, 0.22f, 2.58f, DARK);
        drawBox(shipMatrix, 0f, 2.00f, 8.15f, 3.24f, 0.10f, 2.08f, WOOD);

        // Heavy wales.
        for (float y : new float[]{0.52f, 0.06f, -0.46f, -0.98f}) {
            drawRoundBeam(shipMatrix, -2.92f, y, -10.20f, -3.28f, y - 0.02f, -0.30f, 0.032f, DARK);
            drawRoundBeam(shipMatrix, 2.92f, y, -10.20f, 3.28f, y - 0.02f, -0.30f, 0.032f, DARK);
            drawRoundBeam(shipMatrix, -3.28f, y - 0.02f, -0.30f, -1.68f, y + 0.10f, 9.35f, 0.028f, DARK);
            drawRoundBeam(shipMatrix, 3.28f, y - 0.02f, -0.30f, 1.68f, y + 0.10f, 9.35f, 0.028f, DARK);
        }
        drawRoundBeam(shipMatrix, -2.98f, 0.68f, -10.00f, -3.32f, 0.72f, -0.08f, 0.046f, OCHRE);
        drawRoundBeam(shipMatrix, 2.98f, 0.68f, -10.00f, 3.32f, 0.72f, -0.08f, 0.046f, OCHRE);
        drawRoundBeam(shipMatrix, -3.32f, 0.72f, -0.08f, -1.78f, 0.56f, 9.40f, 0.040f, OCHRE);
        drawRoundBeam(shipMatrix, 3.32f, 0.72f, -0.08f, 1.78f, 0.56f, 9.40f, 0.040f, OCHRE);

        // Single long gun deck.
        for (int side = -1; side <= 1; side += 2) {
            for (float z : new float[]{-6.20f, -4.75f, -3.30f, -1.85f, -0.40f, 1.05f, 2.50f}) {
                drawBox(shipMatrix, side * 2.42f, 0.58f, z, 0.10f, 0.34f, 0.78f, OCHRE);
                drawBox(shipMatrix, side * 2.30f, 0.58f, z, 0.05f, 0.24f, 0.56f, DARK);
            }
        }

        // High transom / stern gallery.
        drawBox(shipMatrix, 0f, 1.08f, -11.38f, 5.72f, 1.36f, 0.20f, BROWN);
        drawBox(shipMatrix, 0f, 1.76f, -11.50f, 4.58f, 0.22f, 0.10f, OCHRE);
        for (float x : new float[]{-2.08f, -0.72f, 0.72f, 2.08f}) {
            drawBox(shipMatrix, x, 1.60f, -10.58f, 0.52f, 0.48f, 0.36f, WOOD);
            drawBox(shipMatrix, x, 1.60f, -10.92f, 0.42f, 0.30f, 0.04f, GLASS);
        }

        // Beakhead bulkhead and headrails.
        drawBox(shipMatrix, 0f, 1.60f, 9.55f, 1.48f, 0.44f, 0.26f, WOOD);
        drawRoundBeam(shipMatrix, -0.62f, 1.42f, 8.52f, -1.88f, 2.16f, 10.36f, 0.042f, WOOD);
        drawRoundBeam(shipMatrix, 0.62f, 1.42f, 8.52f, 1.88f, 2.16f, 10.36f, 0.042f, WOOD);
        drawRoundBeam(shipMatrix, -0.18f, 1.06f, 9.05f, -1.48f, 1.74f, 10.18f, 0.028f, ROPE);
        drawRoundBeam(shipMatrix, 0.18f, 1.06f, 9.05f, 1.48f, 1.74f, 10.18f, 0.028f, ROPE);
    }

    private void drawFrigateRailings() {
        drawRailings();
        drawRoundBeam(shipMatrix, -2.15f, 2.30f, -7.55f, 2.15f, 2.30f, -7.55f, 0.052f, DARK);
        drawRoundBeam(shipMatrix, -1.75f, 2.08f, 7.95f, 1.75f, 2.08f, 7.95f, 0.048f, DARK);
        for (int side = -1; side <= 1; side += 2) {
            drawRoundBeam(shipMatrix, side * 2.15f, 1.62f, -7.50f, side * 2.15f, 2.30f, -7.50f, 0.04f, DARK);
            drawRoundBeam(shipMatrix, side * 1.75f, 1.54f, 7.95f, side * 1.75f, 2.08f, 7.95f, 0.04f, DARK);
        }
    }

    private void drawFrigateCabin() {
        drawBox(shipMatrix, 0f, 1.46f, -3.55f, 3.10f, 1.18f, 4.10f, WOOD);
        drawBox(shipMatrix, 0f, 2.38f, -3.85f, 3.50f, 0.18f, 3.48f, DARK);
        drawBox(shipMatrix, 0f, 2.66f, -3.85f, 2.70f, 0.16f, 2.58f, WOOD);
        for (int side = -1; side <= 1; side += 2) {
            for (float z : new float[]{-4.95f, -3.70f, -2.45f, -1.20f}) {
                drawBox(shipMatrix, side * 1.68f, 1.70f, z, 0.03f, 0.34f, 0.70f, GLASS);
            }
            drawRoundBeam(shipMatrix, side * 1.74f, 1.18f, -5.25f, side * 1.74f, 2.18f, -5.25f, 0.04f, OCHRE);
            drawRoundBeam(shipMatrix, side * 1.74f, 1.18f, -1.05f, side * 1.74f, 2.18f, -1.05f, 0.04f, OCHRE);
        }
        drawBox(shipMatrix, 0f, 1.60f, -5.06f, 1.04f, 0.92f, 0.04f, DARK);
        drawBox(shipMatrix, 0.34f, 1.60f, -5.10f, 0.06f, 0.06f, 0.035f, BRASS);
    }

    private void drawFrigateHatch() {
        drawBox(shipMatrix, 0f, 1.44f, -0.85f, 1.75f, 0.05f, 1.08f, DARK);
        drawBox(shipMatrix, 0f, 1.485f, -0.85f, 1.54f, 0.05f, 0.88f, WOOD);
        drawBox(shipMatrix, 0f, 1.48f, 5.18f, 1.55f, 0.05f, 0.98f, DARK);
        drawBox(shipMatrix, 0f, 1.525f, 5.18f, 1.34f, 0.04f, 0.78f, WOOD);
    }

    private void drawFrigateDeckDetails() {
        for (int i = -10; i <= 10; i++) {
            float z = i * 0.95f;
            float width = Math.max(0.55f, deckHalfWidth(z) * 2.35f);
            if (z > 5.8f || z < -6.4f) width *= 0.84f;
            drawBox(shipMatrix, 0f, 1.405f, z, width, 0.018f, 0.034f, DARK);
        }
        for (int lane = -2; lane <= 2; lane++) {
            float x = lane * 0.60f;
            drawBox(shipMatrix, x, 1.406f, 0.20f, 0.014f, 0.016f, 16.6f, BROWN);
        }
        for (int lane = -1; lane <= 1; lane++) {
            float x = lane * 0.62f;
            drawBox(shipMatrix, x, 1.86f, -6.65f, 0.014f, 0.016f, 2.55f, BROWN);
            drawBox(shipMatrix, x, 1.74f, 6.95f, 0.014f, 0.016f, 1.72f, BROWN);
        }
        drawBox(shipMatrix, 0f, 1.50f, 2.30f, 2.22f, 0.09f, 1.85f, DARK);
        drawBox(shipMatrix, 0f, 1.57f, 2.30f, 1.96f, 0.06f, 1.58f, WOOD);
        drawRoundBeam(shipMatrix, -0.95f, 1.68f, 1.42f, -0.95f, 1.68f, 3.15f, 0.035f, BRASS);
        drawRoundBeam(shipMatrix, 0.95f, 1.68f, 1.42f, 0.95f, 1.68f, 3.15f, 0.035f, BRASS);
        drawBarrel(-1.22f, 2.55f, -4.15f, 0.34f, 0.68f);
        drawBarrel(1.22f, 2.55f, -4.15f, 0.34f, 0.68f);
        drawRopeCoil(-1.92f, 1.48f, 1.10f, 0.46f);
        drawRopeCoil(1.92f, 1.48f, 1.10f, 0.46f);
        drawBox(shipMatrix, 0f, 1.98f, -6.65f, 0.70f, 0.34f, 0.60f, DARK);
        drawRoundBeam(shipMatrix, 0f, 2.18f, -6.65f, 0f, 2.82f, -6.65f, 0.09f, BRASS);
    }

    private void drawBrigHeadsail(float billow) {
        float headX = 0f;
        float headY = 11.85f;
        float headZ = 2.95f;
        float tackX = 0f;
        float tackY = 2.52f;
        float tackZ = 11.55f;
        float clewX = -currentSailSide * (1.05f + state.sailTrim * 1.35f);
        float clewY = 4.25f;
        float clewZ = 7.70f;
        float bellyX = clewX * 0.38f + billow;
        float[] headsail = new float[]{
                clewX, clewY, clewZ,
                headX, headY, headZ,
                tackX, tackY, tackZ,
                clewX, clewY, clewZ,
                tackX, tackY, tackZ,
                bellyX, (headY + tackY) * 0.5f, (headZ + tackZ) * 0.5f + 0.20f,
                headX, headY, headZ,
                bellyX, (headY + tackY) * 0.5f, (headZ + tackZ) * 0.5f + 0.20f,
                tackX, tackY, tackZ
        };
        triSail.update(headsail);
        draw(triSail, shipMatrix, SAIL_DARK);
        drawBeam(shipMatrix, headX, headY, headZ, tackX, tackY, tackZ, 0.026f, ROPE);
        drawBeam(shipMatrix, headX, headY, headZ, clewX, clewY, clewZ, 0.022f, ROPE);
        drawBeam(shipMatrix, tackX, tackY, tackZ, clewX, clewY, clewZ, 0.022f, ROPE);
        drawBeam(shipMatrix, 0f, 2.55f, 11.55f, clewX, 3.55f, 6.20f, 0.018f, ROPE);
    }

    private void drawBrigYardAndSail(float mastZ, float bottomY, float height,
                                     float halfWidth, float braceAngle, float[] sailColor) {
        float topY = bottomY + height;
        float[] yardParent = copy(shipMatrix);
        Matrix.translateM(yardParent, 0, 0f, 0f, mastZ);
        Matrix.rotateM(yardParent, 0, braceAngle, 0f, 1f, 0f);
        drawRoundBeam(yardParent, -halfWidth * 1.08f, topY + 0.10f, 0f,
                halfWidth * 1.08f, topY + 0.10f, 0f, 0.075f, DARK);
        if (!state.sailRaised) return;
        float[] sail = copy(yardParent);
        Matrix.translateM(sail, 0, 0f, bottomY, 0.02f);
        Matrix.scaleM(sail, 0, halfWidth, height, 1f);
        draw(brigSail, sail, sailColor);
        drawBeam(yardParent, -halfWidth, bottomY, 0f, -halfWidth * 0.82f, topY, 0f, 0.022f, ROPE);
        drawBeam(yardParent, halfWidth, bottomY, 0f, halfWidth * 0.82f, topY, 0f, 0.022f, ROPE);
    }

    private void drawOars() {
        if (!state.oarsActive) return;
        float stroke = (float)Math.sin(elapsed * 3.6f);
        float lift = (float)Math.cos(elapsed * 3.6f) * 0.12f;
        for (int sideIndex = 0; sideIndex < 2; sideIndex++) {
            float side = sideIndex == 0 ? -1f : 1f;
            float innerX = side * 1.20f;
            float outerX = side * 4.35f;
            float innerZ = 1.10f;
            float outerZ = 1.15f + stroke * 0.72f;
            float outerY = 0.17f + lift;
            drawRoundBeam(shipMatrix, innerX, 1.18f, innerZ,
                    outerX, outerY, outerZ, 0.075f, WOOD);
            drawBox(shipMatrix, outerX, outerY, outerZ, 0.42f, 0.075f, 0.62f, LIGHT_WOOD);
        }
    }

    private void drawHullTrim() {
        // Generic age-of-sail sheer line, wales and beakhead details shared by all hulls.
        drawRoundBeam(shipMatrix, -2.10f, 0.86f, -7.55f, -2.42f, 0.92f, -0.35f, 0.070f, OCHRE);
        drawRoundBeam(shipMatrix, 2.10f, 0.86f, -7.55f, 2.42f, 0.92f, -0.35f, 0.070f, OCHRE);
        drawRoundBeam(shipMatrix, -2.42f, 0.92f, -0.35f, -1.24f, 0.82f, 6.10f, 0.062f, OCHRE);
        drawRoundBeam(shipMatrix, 2.42f, 0.92f, -0.35f, 1.24f, 0.82f, 6.10f, 0.062f, OCHRE);
        drawRoundBeam(shipMatrix, -1.24f, 0.82f, 6.10f, -0.10f, 0.50f, 8.18f, 0.048f, OCHRE);
        drawRoundBeam(shipMatrix, 1.24f, 0.82f, 6.10f, 0.10f, 0.50f, 8.18f, 0.048f, OCHRE);

        drawRoundBeam(shipMatrix, -1.88f, 0.20f, -7.10f, -2.12f, 0.24f, 0.20f, 0.052f, COPPER);
        drawRoundBeam(shipMatrix, 1.88f, 0.20f, -7.10f, 2.12f, 0.24f, 0.20f, 0.052f, COPPER);
        drawRoundBeam(shipMatrix, -2.12f, 0.24f, 0.20f, -0.92f, 0.06f, 6.85f, 0.046f, COPPER);
        drawRoundBeam(shipMatrix, 2.12f, 0.24f, 0.20f, 0.92f, 0.06f, 6.85f, 0.046f, COPPER);

        for (float y : new float[]{0.42f, 0.06f, -0.30f}) {
            drawRoundBeam(shipMatrix, -2.00f, y, -7.35f, -2.22f, y + 0.02f, 0.05f, 0.028f, DARK);
            drawRoundBeam(shipMatrix, 2.00f, y, -7.35f, 2.22f, y + 0.02f, 0.05f, 0.028f, DARK);
            drawRoundBeam(shipMatrix, -2.22f, y + 0.02f, 0.05f, -1.04f, y - 0.02f, 6.22f, 0.024f, DARK);
            drawRoundBeam(shipMatrix, 2.22f, y + 0.02f, 0.05f, 1.04f, y - 0.02f, 6.22f, 0.024f, DARK);
        }

        // Cathead / headrail feel at the bow.
        drawRoundBeam(shipMatrix, -0.28f, 0.56f, 7.76f, -1.18f, 1.08f, 8.88f, 0.034f, WOOD);
        drawRoundBeam(shipMatrix, 0.28f, 0.56f, 7.76f, 1.18f, 1.08f, 8.88f, 0.034f, WOOD);
        drawRoundBeam(shipMatrix, -0.12f, 0.34f, 7.98f, -0.92f, 0.74f, 8.78f, 0.026f, ROPE);
        drawRoundBeam(shipMatrix, 0.12f, 0.34f, 7.98f, 0.92f, 0.74f, 8.78f, 0.026f, ROPE);

        drawBox(shipMatrix, 0f, -1.46f, 0.35f, 0.24f, 0.42f, 5.25f, DARK);
        float[] rudder = copy(shipMatrix);
        Matrix.translateM(rudder, 0, 0f, -0.38f, -8.04f);
        Matrix.rotateM(rudder, 0, state.rudder * 24f, 0f, 1f, 0f);
        drawBox(rudder, 0f, 0f, -0.26f, 0.16f, 1.80f, 0.88f, DARK);

        // Taffrail / transom.
        drawBox(shipMatrix, 0f, 0.72f, -8.06f, 4.15f, 0.86f, 0.10f, BROWN);
        drawBox(shipMatrix, 0f, 0.88f, -8.12f, 1.54f, 0.34f, 0.04f, OCHRE);
        drawBox(shipMatrix, -1.44f, 0.84f, -8.14f, 0.18f, 0.46f, 0.04f, BRASS);
        drawBox(shipMatrix, 1.44f, 0.84f, -8.14f, 0.18f, 0.46f, 0.04f, BRASS);
        drawBeam(shipMatrix, 0f, 0.94f, 7.90f, 0f, 1.24f, 10.05f, 0.016f, ROPE);
    }

    private void drawBrigHullAccents() {
        // Raised quarterdeck and forecastle for a period brig silhouette.
        drawBox(shipMatrix, 0f, 1.66f, -5.20f, 3.95f, 0.24f, 2.85f, DARK);
        drawBox(shipMatrix, 0f, 1.78f, -5.20f, 3.30f, 0.10f, 2.34f, WOOD);
        drawBox(shipMatrix, 0f, 1.56f, 5.58f, 3.36f, 0.22f, 2.24f, DARK);
        drawBox(shipMatrix, 0f, 1.68f, 5.58f, 2.86f, 0.10f, 1.78f, WOOD);
        for (float x : new float[]{-1.48f, 1.48f}) {
            drawBox(shipMatrix, x, 0.98f, -4.42f, 0.16f, 1.12f, 0.18f, BROWN);
            drawBox(shipMatrix, x, 0.98f, 4.85f, 0.16f, 0.84f, 0.18f, BROWN);
        }

        // Main wale and lower wale.
        drawRoundBeam(shipMatrix, -2.34f, 0.34f, -8.15f, -2.60f, 0.38f, -0.20f, 0.044f, OCHRE);
        drawRoundBeam(shipMatrix, 2.34f, 0.34f, -8.15f, 2.60f, 0.38f, -0.20f, 0.044f, OCHRE);
        drawRoundBeam(shipMatrix, -2.60f, 0.38f, -0.20f, -1.34f, 0.26f, 6.25f, 0.038f, OCHRE);
        drawRoundBeam(shipMatrix, 2.60f, 0.38f, -0.20f, 1.34f, 0.26f, 6.25f, 0.038f, OCHRE);
        drawRoundBeam(shipMatrix, -1.34f, 0.26f, 6.25f, -0.12f, 0.02f, 8.72f, 0.030f, OCHRE);
        drawRoundBeam(shipMatrix, 1.34f, 0.26f, 6.25f, 0.12f, 0.02f, 8.72f, 0.030f, OCHRE);

        for (float y : new float[]{-0.24f, -0.76f, -1.26f}) {
            drawRoundBeam(shipMatrix, -2.28f, y, -8.05f, -2.48f, y - 0.03f, 0.10f, 0.028f, DARK);
            drawRoundBeam(shipMatrix, 2.28f, y, -8.05f, 2.48f, y - 0.03f, 0.10f, 0.028f, DARK);
            drawRoundBeam(shipMatrix, -2.48f, y - 0.03f, 0.10f, -1.22f, y + 0.06f, 6.08f, 0.024f, DARK);
            drawRoundBeam(shipMatrix, 2.48f, y - 0.03f, 0.10f, 1.22f, y + 0.06f, 6.08f, 0.024f, DARK);
        }

        // Gunports.
        for (int side = -1; side <= 1; side += 2) {
            for (float z : new float[]{-4.90f, -3.35f, -1.80f, -0.25f}) {
                drawBox(shipMatrix, side * 2.20f, 0.46f, z, 0.10f, 0.32f, 0.72f, OCHRE);
                drawBox(shipMatrix, side * 2.09f, 0.46f, z, 0.05f, 0.22f, 0.52f, DARK);
            }
        }

        // Stern gallery and windows.
        drawBox(shipMatrix, 0f, 0.98f, -8.92f, 4.55f, 1.18f, 0.18f, BROWN);
        drawBox(shipMatrix, 0f, 1.52f, -9.02f, 3.82f, 0.22f, 0.10f, OCHRE);
        drawBox(shipMatrix, -1.72f, 1.40f, -8.10f, 0.56f, 0.46f, 0.34f, WOOD);
        drawBox(shipMatrix, 0f, 1.48f, -8.18f, 0.72f, 0.52f, 0.34f, WOOD);
        drawBox(shipMatrix, 1.72f, 1.40f, -8.10f, 0.56f, 0.46f, 0.34f, WOOD);
        drawBox(shipMatrix, -1.72f, 1.40f, -8.42f, 0.46f, 0.28f, 0.04f, GLASS);
        drawBox(shipMatrix, 0f, 1.48f, -8.50f, 0.58f, 0.32f, 0.04f, GLASS);
        drawBox(shipMatrix, 1.72f, 1.40f, -8.42f, 0.46f, 0.28f, 0.04f, GLASS);

        // Beakhead rails.
        drawRoundBeam(shipMatrix, -0.42f, 1.08f, 7.35f, -1.52f, 1.72f, 8.95f, 0.038f, WOOD);
        drawRoundBeam(shipMatrix, 0.42f, 1.08f, 7.35f, 1.52f, 1.72f, 8.95f, 0.038f, WOOD);
    }

    private void drawBrigRailings() {
        drawRailings();
        drawRoundBeam(shipMatrix, -1.82f, 2.02f, -6.05f, 1.82f, 2.02f, -6.05f, 0.052f, DARK);
        drawRoundBeam(shipMatrix, -1.55f, 1.92f, 5.95f, 1.55f, 1.92f, 5.95f, 0.048f, DARK);
        for (int side = -1; side <= 1; side += 2) {
            drawRoundBeam(shipMatrix, side * 1.82f, 1.46f, -6.02f, side * 1.82f, 2.02f, -6.02f, 0.04f, DARK);
            drawRoundBeam(shipMatrix, side * 1.55f, 1.42f, 5.95f, side * 1.55f, 1.92f, 5.95f, 0.04f, DARK);
        }
    }

    private void drawBrigCabin() {
        drawBox(shipMatrix, 0f, 1.22f, -2.70f, 2.72f, 1.02f, 3.32f, WOOD);
        drawBox(shipMatrix, 0f, 2.02f, -2.92f, 3.12f, 0.16f, 2.96f, DARK);
        drawBox(shipMatrix, 0f, 2.28f, -2.92f, 2.28f, 0.14f, 2.18f, WOOD);
        drawRoundBeam(shipMatrix, -1.52f, 1.04f, -4.18f, -1.52f, 1.86f, -4.18f, 0.04f, OCHRE);
        drawRoundBeam(shipMatrix, 1.52f, 1.04f, -4.18f, 1.52f, 1.86f, -4.18f, 0.04f, OCHRE);
        drawRoundBeam(shipMatrix, -1.52f, 1.04f, -1.50f, -1.52f, 1.86f, -1.50f, 0.04f, OCHRE);
        drawRoundBeam(shipMatrix, 1.52f, 1.04f, -1.50f, 1.52f, 1.86f, -1.50f, 0.04f, OCHRE);
        for (int side = -1; side <= 1; side += 2) {
            drawBox(shipMatrix, side * 1.46f, 1.42f, -3.58f, 0.03f, 0.30f, 0.62f, GLASS);
            drawBox(shipMatrix, side * 1.46f, 1.42f, -2.52f, 0.03f, 0.30f, 0.62f, GLASS);
            drawBox(shipMatrix, side * 1.46f, 1.42f, -1.82f, 0.03f, 0.30f, 0.44f, GLASS);
        }
        drawBox(shipMatrix, -0.74f, 1.40f, -1.52f, 0.52f, 0.28f, 0.035f, GLASS);
        drawBox(shipMatrix, 0.74f, 1.40f, -1.52f, 0.52f, 0.28f, 0.035f, GLASS);
        drawBox(shipMatrix, 0f, 1.30f, -4.12f, 0.92f, 0.72f, 0.04f, DARK);
        drawBox(shipMatrix, 0.30f, 1.30f, -4.16f, 0.06f, 0.06f, 0.035f, BRASS);
    }

    private void drawBrigHatch() {
        drawBox(shipMatrix, 0f, 1.20f, -0.95f, 1.55f, 0.04f, 0.98f, DARK);
        drawBox(shipMatrix, 0f, 1.235f, -0.95f, 1.36f, 0.05f, 0.80f, WOOD);
        drawBox(shipMatrix, 0f, 1.30f, 4.65f, 1.28f, 0.05f, 0.88f, DARK);
        drawBox(shipMatrix, 0f, 1.34f, 4.65f, 1.12f, 0.04f, 0.72f, WOOD);
    }

    private void drawBrigDeckDetails() {
        for (int i = -7; i <= 7; i++) {
            float z = i * 0.92f;
            float width = Math.max(0.52f, deckHalfWidth(z) * 1.90f);
            if (z > 3.9f || z < -4.1f) width *= 0.86f;
            drawBox(shipMatrix, 0f, 1.145f, z, width, 0.018f, 0.032f, DARK);
        }
        for (int lane = -2; lane <= 2; lane++) {
            float x = lane * 0.54f;
            drawBox(shipMatrix, x, 1.146f, -0.30f, 0.014f, 0.016f, 12.6f, BROWN);
        }
        // Raised quarterdeck and forecastle planks.
        for (int lane = -1; lane <= 1; lane++) {
            float x = lane * 0.58f;
            drawBox(shipMatrix, x, 1.54f, -5.10f, 0.014f, 0.016f, 2.00f, BROWN);
            drawBox(shipMatrix, x, 1.50f, 5.05f, 0.014f, 0.016f, 1.40f, BROWN);
        }
        drawBox(shipMatrix, 0f, 1.17f, 2.10f, 1.95f, 0.08f, 1.55f, DARK);
        drawBox(shipMatrix, 0f, 1.23f, 2.10f, 1.74f, 0.06f, 1.30f, WOOD);
        drawRoundBeam(shipMatrix, -0.86f, 1.30f, 1.34f, -0.86f, 1.30f, 2.90f, 0.035f, BRASS);
        drawRoundBeam(shipMatrix, 0.86f, 1.30f, 1.34f, 0.86f, 1.30f, 2.90f, 0.035f, BRASS);
        drawBarrel(-1.04f, 2.25f, -3.10f, 0.32f, 0.62f);
        drawBarrel(1.04f, 2.25f, -3.10f, 0.32f, 0.62f);
        drawRopeCoil(-1.72f, 1.24f, 0.82f, 0.40f);
        drawRopeCoil(1.72f, 1.24f, 0.82f, 0.40f);
        drawBox(shipMatrix, 0f, 1.65f, -5.00f, 0.62f, 0.30f, 0.54f, DARK);
        drawRoundBeam(shipMatrix, 0f, 1.82f, -5.00f, 0f, 2.35f, -5.00f, 0.08f, BRASS);
        for (int side = -1; side <= 1; side += 2) {
            drawRoundBeam(shipMatrix, side * 1.62f, 1.25f, 5.18f,
                    side * 1.62f, 1.62f, 5.18f, 0.09f, DARK);
            drawRoundBeam(shipMatrix, side * 1.42f, 1.58f, -5.78f,
                    side * 1.42f, 1.92f, -5.78f, 0.09f, DARK);
            drawBox(shipMatrix, side * 1.84f, 0.58f, -3.85f, 0.08f, 0.26f, 0.78f, OCHRE);
            drawBox(shipMatrix, side * 1.84f, 0.58f, -2.65f, 0.08f, 0.26f, 0.78f, OCHRE);
            drawBox(shipMatrix, side * 1.84f, 0.58f, -1.45f, 0.08f, 0.26f, 0.78f, OCHRE);
        }
    }

    private void drawRailings() {
        float[] stations = {-6.55f, -5.0f, -3.9f, -0.65f, 1.1f, 3.0f, 4.65f, 5.85f};
        for (int side = -1; side <= 1; side += 2) {
            float previousX = 0f;
            float previousZ = 0f;
            boolean hasPrevious = false;
            for (float z : stations) {
                float x = side * (deckHalfWidth(z) - 0.04f);
                drawRoundBeam(shipMatrix, x, 1.16f, z, x, 1.78f, z, 0.045f, DARK);
                if (hasPrevious) {
                    boolean gangwayGap = previousZ < 2.1f && z > 2.1f;
                    if (!gangwayGap) {
                        drawRoundBeam(shipMatrix, previousX, 1.78f, previousZ,
                                x, 1.78f, z, 0.052f, DARK);
                        drawRoundBeam(shipMatrix, previousX, 1.48f, previousZ,
                                x, 1.48f, z, 0.033f, ROPE);
                    }
                }
                previousX = x;
                previousZ = z;
                hasPrevious = true;
            }
        }
        drawRoundBeam(shipMatrix, -2.0f, 1.78f, -6.85f, 2.0f, 1.78f, -6.85f, 0.052f, DARK);
        drawRoundBeam(shipMatrix, -0.85f, 1.70f, 6.55f, 0.85f, 1.70f, 6.55f, 0.045f, DARK);
    }

    private float deckHalfWidth(float z) {
        if (z < -6f) return 2.12f + (z + 7.5f) * 0.12f;
        if (z < 1f) return 2.34f;
        if (z < 4.5f) return 2.34f - (z - 1f) * 0.14f;
        return Math.max(0.72f, 1.85f - (z - 4.5f) * 0.40f);
    }

    private void drawCabin() {
        // Lower deckhouse, closer to the compact wooden sailboats used as visual reference.
        drawBox(shipMatrix, 0f, 1.48f, -2.45f, 2.72f, 0.52f, 2.18f, WOOD);
        drawBox(shipMatrix, 0f, 1.88f, -2.45f, 3.04f, 0.13f, 2.42f, DARK);
        drawRoundBeam(shipMatrix, -1.42f, 1.40f, -3.58f, -1.42f, 1.94f, -3.58f, 0.04f, OCHRE);
        drawRoundBeam(shipMatrix, 1.42f, 1.40f, -3.58f, 1.42f, 1.94f, -3.58f, 0.04f, OCHRE);
        drawRoundBeam(shipMatrix, -1.42f, 1.40f, -1.30f, -1.42f, 1.94f, -1.30f, 0.04f, OCHRE);
        drawRoundBeam(shipMatrix, 1.42f, 1.40f, -1.30f, 1.42f, 1.94f, -1.30f, 0.04f, OCHRE);
        for (int side = -1; side <= 1; side += 2) {
            drawBox(shipMatrix, side * 1.36f, 1.66f, -2.92f, 0.03f, 0.24f, 0.48f, GLASS);
            drawBox(shipMatrix, side * 1.36f, 1.66f, -2.05f, 0.03f, 0.24f, 0.48f, GLASS);
        }
        drawBox(shipMatrix, -0.63f, 1.66f, -1.34f, 0.43f, 0.24f, 0.035f, GLASS);
        drawBox(shipMatrix, 0.63f, 1.66f, -1.34f, 0.43f, 0.24f, 0.035f, GLASS);
        drawBox(shipMatrix, 0f, 1.57f, -3.52f, 0.82f, 0.62f, 0.04f, DARK);
        drawBox(shipMatrix, 0.28f, 1.57f, -3.57f, 0.06f, 0.06f, 0.035f, BRASS);
    }

    private void drawDeckDetails() {
        for (int i = -7; i <= 7; i++) {
            float z = i * 0.92f;
            float width = Math.max(0.45f, deckHalfWidth(z) * 1.88f);
            drawBox(shipMatrix, 0f, 1.145f, z, width, 0.018f, 0.032f, DARK);
        }
        for (int lane = -2; lane <= 2; lane++) {
            float x = lane * 0.48f;
            drawBox(shipMatrix, x, 1.146f, -0.55f, 0.014f, 0.016f, 12.35f, BROWN);
        }
        drawBox(shipMatrix, 0f, 1.17f, 2.25f, 1.55f, 0.07f, 1.25f, DARK);
        drawBox(shipMatrix, 0f, 1.22f, 2.25f, 1.34f, 0.06f, 1.04f, WOOD);
        drawRoundBeam(shipMatrix, -0.68f, 1.28f, 1.65f, -0.68f, 1.28f, 2.85f, 0.035f, BRASS);
        drawRoundBeam(shipMatrix, 0.68f, 1.28f, 1.65f, 0.68f, 1.28f, 2.85f, 0.035f, BRASS);

        drawBarrel(-0.92f, 2.49f, -2.70f, 0.34f, 0.70f);
        drawBarrel(0.92f, 2.49f, -2.70f, 0.34f, 0.70f);
        drawRopeCoil(-1.55f, 1.24f, 0.90f, 0.42f);
        drawRopeCoil(1.55f, 1.24f, 0.90f, 0.42f);

        for (int side = -1; side <= 1; side += 2) {
            drawRoundBeam(shipMatrix, side * 1.55f, 1.25f, 5.0f,
                    side * 1.55f, 1.62f, 5.0f, 0.09f, DARK);
            drawRoundBeam(shipMatrix, side * 1.28f, 1.25f, -5.75f,
                    side * 1.28f, 1.62f, -5.75f, 0.09f, DARK);
        }
    }

    private void drawBarrel(float x, float y, float z, float radius, float height) {
        float[] body = copy(shipMatrix);
        Matrix.translateM(body, 0, x, y, z);
        Matrix.rotateM(body, 0, 90f, 1f, 0f, 0f);
        Matrix.scaleM(body, 0, radius, radius, height);
        draw(cylinder, body, WOOD);
        for (float ring : new float[]{-0.34f, 0f, 0.34f}) {
            float[] band = copy(shipMatrix);
            Matrix.translateM(band, 0, x, y + ring * height, z);
            Matrix.rotateM(band, 0, 90f, 1f, 0f, 0f);
            Matrix.scaleM(band, 0, radius * 1.05f, radius * 1.05f, 0.55f);
            draw(torus, band, IRON);
        }
    }

    private void drawRopeCoil(float x, float y, float z, float scale) {
        float[] coil = copy(shipMatrix);
        Matrix.translateM(coil, 0, x, y, z);
        Matrix.rotateM(coil, 0, 90f, 1f, 0f, 0f);
        Matrix.scaleM(coil, 0, scale, scale, scale * 0.55f);
        draw(torus, coil, ROPE);
        draw(torus, model(coil, 0f, 0f, 0f, 0f, 0.72f, 0.72f, 0.72f), ROPE);
    }

    private void drawSailDetails(float[] sailParent, float jibClewX) {
        drawBeam(sailParent, 0f, 3.27f, -0.22f, 0f, 10.28f, -0.22f, 0.025f, ROPE);
        drawBeam(sailParent, 0f, 3.27f, -0.22f, 0f, 3.27f, -5.23f, 0.025f, ROPE);
        drawBeam(sailParent, 0f, 3.27f, -5.23f, 0f, 10.28f, -2.17f, 0.025f, ROPE);
        for (int i = 1; i <= 4; i++) {
            float v = i / 5f;
            float y = 3.25f + v * 7.05f;
            float width = 5.05f * (1f - v * 0.61f);
            drawBeam(sailParent, 0f, y, -0.22f, 0f, y, -0.2f - width, 0.018f, SAIL_DARK);
        }
        for (int i = 1; i <= 4; i++) {
            drawBox(sailParent, 0.04f, 4.68f, -i * 0.88f, 0.09f, 0.07f, 0.16f, DARK);
        }
        // The front edge (luff) is fixed to the forestay. Only the clew moves across the deck.
        drawBeam(shipMatrix, 0f, JIB_HEAD_Y, JIB_HEAD_Z,
                0f, JIB_TACK_Y, JIB_TACK_Z, 0.024f, ROPE);
        drawBeam(shipMatrix, 0f, JIB_HEAD_Y, JIB_HEAD_Z,
                jibClewX, JIB_CLEW_Y, JIB_CLEW_Z, 0.022f, ROPE);
        drawBeam(shipMatrix, 0f, JIB_TACK_Y, JIB_TACK_Z,
                jibClewX, JIB_CLEW_Y, JIB_CLEW_Z, 0.022f, ROPE);
        float seamY = 5.25f;
        float luffT = (JIB_HEAD_Y - seamY) / (JIB_HEAD_Y - JIB_TACK_Y);
        float luffZ = JIB_HEAD_Z + (JIB_TACK_Z - JIB_HEAD_Z) * luffT;
        float leechT = (JIB_HEAD_Y - seamY) / (JIB_HEAD_Y - JIB_CLEW_Y);
        float leechX = jibClewX * leechT;
        float leechZ = JIB_HEAD_Z + (JIB_CLEW_Z - JIB_HEAD_Z) * leechT;
        drawBeam(shipMatrix, 0f, seamY, luffZ,
                leechX, seamY, leechZ, 0.016f, SAIL);
    }

    private void drawHatch() {
        boolean highlighted = state.interactionTarget() == GameState.TARGET_HATCH;
        drawBox(shipMatrix, 0f, 1.205f, -1.06f, 1.45f, 0.035f, 0.92f,
                highlighted ? HIGHLIGHT : DARK);
        float[] lid = copy(shipMatrix);
        Matrix.translateM(lid, 0, 0f, 1.29f, -1.49f);
        Matrix.rotateM(lid, 0, -102f * state.hatchProgress, 1f, 0f, 0f);
        Matrix.translateM(lid, 0, 0f, 0f, 0.43f);
        Matrix.scaleM(lid, 0, 1.55f, 0.10f, 0.88f);
        draw(cube, lid, highlighted ? HIGHLIGHT : WOOD);
        if (state.hatchProgress > 0.25f) {
            drawBox(shipMatrix, -0.47f, 0.72f, -1.08f, 0.08f, 0.95f, 0.08f, ROPE);
            drawBox(shipMatrix, 0.47f, 0.72f, -1.08f, 0.08f, 0.95f, 0.08f, ROPE);
            for (int i = 0; i < 4; i++) {
                drawBox(shipMatrix, 0f, 0.38f + i * 0.22f, -1.08f,
                        1.02f, 0.055f, 0.055f, ROPE);
            }
        }
    }

    private void drawRiggingAndLines(float boomAngleDegrees) {
        boolean sheetHighlight = state.interactionTarget() == GameState.TARGET_SHEET;
        boolean halyardHighlight = state.interactionTarget() == GameState.TARGET_HALYARD;
        drawBeam(shipMatrix, 0f, 11.3f, -0.3f, -2.13f, 1.55f, -5.6f, 0.035f, ROPE);
        drawBeam(shipMatrix, 0f, 11.3f, -0.3f, 2.13f, 1.55f, -5.6f, 0.035f, ROPE);
        drawBeam(shipMatrix, 0f, 11.3f, -0.3f, -1.35f, 1.48f, 6.2f, 0.032f, ROPE);
        drawBeam(shipMatrix, 0f, 11.3f, -0.3f, 1.35f, 1.48f, 6.2f, 0.032f, ROPE);
        // Central forestay; jib luff anchors are calculated on this exact line.
        drawBeam(shipMatrix, 0f, JIB_STAY_TOP_Y, JIB_STAY_TOP_Z,
                0f, JIB_STAY_TACK_Y, JIB_STAY_TACK_Z, 0.035f, ROPE);
        drawBeam(shipMatrix, 0f, 10.45f, -0.22f, 1.62f, 1.43f, 0.35f,
                halyardHighlight ? 0.08f : 0.045f, halyardHighlight ? HIGHLIGHT : ROPE);

        float a = boomAngleDegrees * GameState.PI / 180f;
        float endX = (float)Math.sin(a) * -4.85f;
        float endZ = (float)Math.cos(a) * -4.85f - 0.3f;
        float cleatX = endX >= 0f ? 1.66f : -1.66f;
        drawBeam(shipMatrix, endX, 3.02f, endZ, cleatX, 1.36f, -0.15f,
                state.handlingSheet || sheetHighlight ? 0.075f : 0.05f,
                sheetHighlight ? HIGHLIGHT : state.handlingSheet ? BRASS : ROPE);
        drawBox(shipMatrix, -1.66f, 1.39f, -0.15f, 0.22f, 0.12f, 0.42f,
                sheetHighlight ? HIGHLIGHT : BRASS);
        drawBox(shipMatrix, 1.62f, 1.39f, 0.35f, 0.22f, 0.12f, 0.42f,
                halyardHighlight ? HIGHLIGHT : BRASS);
    }

    private void drawAnchorAndMooring() {
        boolean anchorHighlight = state.interactionTarget() == GameState.TARGET_ANCHOR;
        boolean mooringHighlight = state.interactionTarget() == GameState.TARGET_MOORING;
        boolean gangwayHighlight = state.interactionTarget() == GameState.TARGET_GANGWAY;
        float[] windlass = copy(shipMatrix);
        Matrix.translateM(windlass, 0, 0f, 1.44f, 5.3f);
        Matrix.scaleM(windlass, 0, 0.48f, 0.48f, 0.48f);
        draw(cylinder, windlass, anchorHighlight ? HIGHLIGHT : WOOD);
        for (int i = 0; i < 6; i++) {
            float a = i * 60f * GameState.PI / 180f;
            drawRoundBeam(shipMatrix, 0f, 1.61f, 5.3f,
                    (float)Math.sin(a) * 0.68f, 1.61f,
                    5.3f + (float)Math.cos(a) * 0.68f, 0.045f,
                    anchorHighlight ? HIGHLIGHT : IRON);
        }
        drawBox(shipMatrix, 1.62f, 1.42f, 3.75f, 0.34f, 0.16f, 0.54f,
                mooringHighlight ? HIGHLIGHT : BRASS);
        if (state.anchorProgress > 0.02f) {
            drawBeam(shipMatrix, 0.55f, 1.32f, 6.15f, 0.7f,
                    1.15f - state.anchorProgress * 4.8f, 7.0f,
                    0.055f, IRON);
            drawBox(shipMatrix, 0.7f, 0.95f - state.anchorProgress * 4.8f,
                    7.0f, 0.48f, 0.65f, 0.16f, IRON);
        }
        if (state.moored) {
            drawBeam(shipMatrix, 1.95f, 1.42f, 3.8f, 5.8f, 0.25f, 4.8f,
                    0.075f, ROPE);
            drawBeam(shipMatrix, -1.95f, 1.42f, -0.2f, -5.4f, 0.25f, 0.8f,
                    0.075f, ROPE);
            drawBox(shipMatrix, -2.65f, 1.18f, 2.1f, 1.55f, 0.12f, 0.7f,
                    gangwayHighlight ? HIGHLIGHT : LIGHT_WOOD);
            drawBox(shipMatrix, 2.65f, 1.18f, 2.1f, 1.55f, 0.12f, 0.7f,
                    gangwayHighlight ? HIGHLIGHT : LIGHT_WOOD);
        }
    }

    private void drawInterior() {
        if (state.shipType == GameState.SHIP_FRIGATE) {
            if (state.interiorDeckLevel == 0) drawFrigateGunDeckInterior();
            else if (state.interiorDeckLevel == 1) drawFrigateBerthDeckInterior();
            else drawFrigateHoldInterior();
            return;
        }
        if (state.shipType == GameState.SHIP_BRIG) {
            if (state.interiorDeckLevel == 0) drawBrigGunDeckInterior();
            else drawBrigHoldInterior();
            return;
        }
        drawSloopHoldInterior();
    }

    private void drawInteriorShell(float halfWidth, float minZ, float maxZ, float ceilingY) {
        float length = maxZ - minZ;
        float centerZ = (minZ + maxZ) * 0.5f;
        drawBox(shipMatrix, 0f, -0.24f, centerZ, halfWidth * 2f, 0.18f, length, LIGHT_WOOD);
        drawBox(shipMatrix, -halfWidth - 0.06f, 0.58f, centerZ, 0.16f, 1.70f, length, BROWN);
        drawBox(shipMatrix, halfWidth + 0.06f, 0.58f, centerZ, 0.16f, 1.70f, length, BROWN);
        drawBox(shipMatrix, 0f, ceilingY, centerZ, halfWidth * 2f + 0.12f, 0.14f, length, DARK);
        drawBox(shipMatrix, 0f, 0.58f, minZ - 0.06f, halfWidth * 2f, 1.70f, 0.16f, BROWN);
        drawBox(shipMatrix, 0f, 0.58f, maxZ + 0.06f, halfWidth * 2f, 1.70f, 0.16f, BROWN);
        int beamCount = Math.max(4, (int)(length / 2.1f));
        for (int i = 0; i <= beamCount; i++) {
            float z = minZ + length * i / beamCount;
            drawBox(shipMatrix, 0f, ceilingY - 0.12f, z, halfWidth * 2f + 0.22f, 0.12f, 0.14f, WOOD);
        }
        for (int side = -1; side <= 1; side += 2) {
            for (float z = minZ + 0.9f; z < maxZ - 0.5f; z += 2.1f) {
                drawRoundBeam(shipMatrix, side * (halfWidth - 0.10f), -0.12f, z,
                        side * (halfWidth - 0.10f), ceilingY - 0.18f, z, 0.055f, WOOD);
            }
        }
    }

    private void drawInteriorLadder(float z, boolean down) {
        float baseY = down ? -0.18f : 0.02f;
        drawBox(shipMatrix, -0.48f, 0.58f, z, 0.08f, 1.52f, 0.08f, ROPE);
        drawBox(shipMatrix, 0.48f, 0.58f, z, 0.08f, 1.52f, 0.08f, ROPE);
        for (int i = 0; i < 6; i++) {
            drawBox(shipMatrix, 0f, baseY + i * 0.25f, z, 1.05f, 0.055f, 0.055f, ROPE);
        }
        if (down) {
            drawBox(shipMatrix, 0f, -0.19f, z + 0.42f, 1.20f, 0.08f, 0.82f, DARK);
        }
    }

    private void drawInteriorMast(float z, float radius) {
        drawRoundBeam(shipMatrix, 0f, -0.28f, z, 0f, 1.52f, z, radius, DARK);
    }

    private void drawHammockPair(float z) {
        drawBox(shipMatrix, -1.05f, 0.56f, z, 1.32f, 0.12f, 2.15f, SAIL_DARK);
        drawBox(shipMatrix, 1.05f, 0.56f, z, 1.32f, 0.12f, 2.15f, SAIL_DARK);
        drawBeam(shipMatrix, -1.70f, 0.88f, z - 0.92f, -0.38f, 0.70f, z + 0.92f, 0.018f, ROPE);
        drawBeam(shipMatrix, 1.70f, 0.88f, z - 0.92f, 0.38f, 0.70f, z + 0.92f, 0.018f, ROPE);
    }

    private void drawCargoStacks(int visibleCount, float halfWidth, float startZ, int columns) {
        int count = Math.min(visibleCount, state.cargoTotal());
        float spacingX = columns <= 1 ? 0f : (halfWidth * 1.55f) / (columns - 1);
        for (int i = 0; i < count; i++) {
            int col = i % columns;
            int row = (i / columns) % 5;
            int layer = i / (columns * 5);
            float x = columns <= 1 ? 0f : -halfWidth * 0.78f + col * spacingX;
            float z = startZ + row * 0.86f;
            float y = 0.16f + layer * 0.62f;
            drawBox(shipMatrix, x, y, z, 0.72f, 0.58f, 0.68f, WOOD);
        }
    }

    private void drawSloopHoldInterior() {
        drawInteriorShell(1.84f, -5.85f, 4.85f, 1.46f);
        drawInteriorMast(-0.30f, 0.20f);
        drawHammockPair(-4.15f);
        drawCargoStacks(12, 1.52f, 1.85f, 3);
        drawBox(shipMatrix, 0f, 0.24f, 2.85f, 1.40f, 0.72f, 0.95f, LIGHT_WOOD);
        drawInteriorLadder(0.85f, false);
        drawBox(shipMatrix, 0f, 1.18f, -0.55f, 0.34f, 0.34f, 0.34f, BRASS);
        drawBox(shipMatrix, 0f, 0.88f, -0.55f, 0.13f, 0.52f, 0.13f, ROPE);
    }

    private void drawBrigGunDeckInterior() {
        drawInteriorShell(2.08f, -6.65f, 5.85f, 1.48f);
        drawInteriorMast(-1.05f, 0.22f);
        drawInteriorMast(2.95f, 0.21f);
        drawBroadsideCannons(0.02f, 1.78f,
                new float[]{-4.90f, -3.35f, -1.80f, -0.25f}, 0.98f, 0.075f, 0.50f, 0.16f);
        for (int side = -1; side <= 1; side += 2) {
            for (float z : new float[]{-4.90f, -3.35f, -1.80f, -0.25f}) {
                drawBox(shipMatrix, side * 2.00f, 0.22f, z, 0.08f, 0.36f, 0.74f, OCHRE);
                drawBox(shipMatrix, side * 2.07f, 0.22f, z, 0.04f, 0.25f, 0.54f, DARK);
            }
        }
        drawHammockPair(-5.45f);
        drawInteriorLadder(0.85f, false);
        drawInteriorLadder(3.85f, true);
    }

    private void drawBrigHoldInterior() {
        drawInteriorShell(1.95f, -6.45f, 5.55f, 1.32f);
        drawInteriorMast(-1.05f, 0.22f);
        drawInteriorMast(2.95f, 0.21f);
        drawCargoStacks(30, 1.65f, -2.25f, 4);
        for (int side = -1; side <= 1; side += 2) {
            drawBarrel(side * 1.22f, 1.55f, -4.65f, 0.32f, 0.64f);
            drawBarrel(side * 1.22f, 1.55f, 4.55f, 0.32f, 0.64f);
        }
        drawInteriorLadder(0.85f, false);
    }

    private void drawFrigateGunDeckInterior() {
        drawInteriorShell(2.48f, -8.05f, 7.25f, 1.52f);
        drawInteriorMast(-4.10f, 0.22f);
        drawInteriorMast(-0.10f, 0.24f);
        drawInteriorMast(4.65f, 0.22f);
        float[] guns = {-6.20f, -4.75f, -3.30f, -1.85f, -0.40f, 1.05f, 2.50f};
        drawBroadsideCannons(0.02f, 2.08f, guns, 1.06f, 0.082f, 0.56f, 0.17f);
        for (int side = -1; side <= 1; side += 2) {
            for (float z : guns) {
                drawBox(shipMatrix, side * 2.38f, 0.24f, z, 0.08f, 0.38f, 0.76f, OCHRE);
                drawBox(shipMatrix, side * 2.46f, 0.24f, z, 0.04f, 0.26f, 0.56f, DARK);
            }
        }
        drawInteriorLadder(0.85f, false);
        drawInteriorLadder(3.85f, true);
        drawBox(shipMatrix, 0f, 0.18f, -7.05f, 1.40f, 0.52f, 0.72f, WOOD);
    }

    private void drawFrigateBerthDeckInterior() {
        drawInteriorShell(2.35f, -7.85f, 7.00f, 1.42f);
        drawInteriorMast(-4.10f, 0.21f);
        drawInteriorMast(-0.10f, 0.23f);
        drawInteriorMast(4.65f, 0.21f);
        drawHammockPair(-5.65f);
        drawHammockPair(-2.75f);
        drawBox(shipMatrix, 0f, 0.18f, 1.35f, 2.30f, 0.52f, 1.05f, WOOD);
        drawBox(shipMatrix, -1.45f, 0.22f, 2.90f, 0.95f, 0.72f, 1.25f, LIGHT_WOOD);
        drawBox(shipMatrix, 1.45f, 0.22f, 2.90f, 0.95f, 0.72f, 1.25f, LIGHT_WOOD);
        drawInteriorLadder(0.85f, false);
        drawInteriorLadder(3.85f, true);
    }

    private void drawFrigateHoldInterior() {
        drawInteriorShell(2.25f, -7.55f, 6.70f, 1.28f);
        drawInteriorMast(-4.10f, 0.20f);
        drawInteriorMast(-0.10f, 0.22f);
        drawInteriorMast(4.65f, 0.20f);
        drawCargoStacks(40, 1.90f, -3.20f, 5);
        for (int side = -1; side <= 1; side += 2) {
            drawBarrel(side * 1.55f, 1.42f, -6.20f, 0.34f, 0.68f);
            drawBarrel(side * 1.55f, 1.42f, 5.65f, 0.34f, 0.68f);
            drawRopeCoil(side * 1.65f, 0.08f, 0.15f, 0.38f);
        }
        drawInteriorLadder(0.85f, false);
    }

    private void drawWheel() {
        float[] wheelColor = state.interactionTarget() == GameState.TARGET_HELM ? HIGHLIGHT : DARK;
        float steer = state.rudder * 45f;
        float[] root = copy(shipMatrix);
        Matrix.translateM(root, 0, 0f, 2.25f, -5.7f);
        Matrix.rotateM(root, 0, steer, 0f, 0f, 1f);
        float[] rim = copy(root);
        Matrix.scaleM(rim, 0, 1.02f, 1.02f, 0.18f);
        draw(torus, rim, wheelColor);
        drawRoundBeam(root, 0f, 0f, -0.17f, 0f, 0f, 0.17f, 0.17f, BRASS);
        for (int i = 0; i < 8; i++) {
            float a = i * 45f * GameState.PI / 180f;
            float x = (float)Math.sin(a);
            float y = (float)Math.cos(a);
            drawRoundBeam(root, 0f, 0f, 0f, x * 1.18f, y * 1.18f, 0f,
                    0.045f, wheelColor);
            float[] handle = model(root, x * 1.22f, y * 1.22f, 0f,
                    0f, 0.11f, 0.11f, 0.25f);
            draw(cylinder, handle, wheelColor);
        }
        drawRoundBeam(shipMatrix, -0.75f, 1.12f, -5.7f, -0.75f, 2.15f, -5.7f,
                0.08f, WOOD);
        drawRoundBeam(shipMatrix, 0.75f, 1.12f, -5.7f, 0.75f, 2.15f, -5.7f,
                0.08f, WOOD);
        drawRoundBeam(shipMatrix, -0.88f, 2.13f, -5.7f, 0.88f, 2.13f, -5.7f,
                0.075f, WOOD);
    }

    private void drawIsland(int p) {
        float x = GameState.PORT_X[p];
        float z = GameState.PORT_Z[p];
        float r = GameState.PORT_R[p];
        if (distanceSquared(x, z, state.shipX, state.shipZ) > 900f * 900f
                && !state.onLand) return;
        float[] beach = model(identity, x, -0.28f, z, 0f, r, 1.7f, r, 0f);
        draw(cone, beach, SAND);
        float[] green = model(identity, x, -0.02f, z, 0f, r * 0.78f, 2.9f, r * 0.78f, 0f);
        draw(cone, green, GRASS);

        float dockSign = p == 1 ? -1f : 1f;
        drawBox(identity, x, 0.13f, z + dockSign * (r + 4f), 5.6f, 0.25f, 10.5f, WOOD);
        for (int i = -2; i <= 2; i += 2) {
            drawBox(identity, x + i * 0.65f, -0.35f, z + dockSign * (r + 4f), 0.16f, 1.05f, 0.16f, DARK);
        }

        float marketZ = z + dockSign * (r * 0.42f);
        float marketGround = terrainHeight(x - 4.8f, marketZ);
        drawBox(identity, x - 4.8f, marketGround + 1.2f, marketZ,
                5.2f, 2.4f, 4.1f, WHITE);
        drawBox(identity, x - 4.8f, marketGround + 2.55f, marketZ,
                5.8f, 0.35f, 4.7f, RED);
        float storeZ = marketZ - dockSign * 2.2f;
        float storeGround = terrainHeight(x + 3.8f, storeZ);
        drawBox(identity, x + 3.8f, storeGround + 0.85f, storeZ,
                4.0f, 1.7f, 3.5f, WOOD);
        drawBox(identity, x + 3.8f, storeGround + 1.86f, storeZ,
                4.5f, 0.30f, 4.0f, DARK);

        float cargoX = state.cargoPointX(p);
        float cargoZ = state.cargoPointZ(p);
        // A visible roofed quay warehouse replaces the old bare loading frame.
        drawBox(identity, cargoX, 0.35f, cargoZ, 2.45f, 0.18f, 2.75f, DARK);
        drawBox(identity, cargoX - 1.12f, 1.35f, cargoZ, 0.16f, 2.15f, 2.55f, WOOD);
        drawBox(identity, cargoX + 1.12f, 1.35f, cargoZ, 0.16f, 2.15f, 2.55f, WOOD);
        drawBox(identity, cargoX, 1.35f, cargoZ + dockSign * 1.18f,
                2.20f, 2.15f, 0.16f, BROWN);
        drawBox(identity, cargoX, 2.52f, cargoZ, 2.75f, 0.24f, 3.05f, DARK);
        drawBox(identity, cargoX, 2.18f, cargoZ - dockSign * 1.38f,
                1.75f, 0.42f, 0.12f, OCHRE);
        if (state.quayPort == p) {
            int waiting = Math.min(8, state.quayCargoTotal());
            for (int i = 0; i < waiting; i++) {
                float qx = cargoX - 0.95f + (i % 3) * 0.95f;
                float qz = cargoZ - 0.65f + (i / 3) * 0.75f;
                drawBox(identity, qx, 0.78f, qz, 0.72f, 0.72f, 0.62f, LIGHT_WOOD);
            }
        }

        float lighthouseX = x + r * 0.42f;
        float lighthouseZ = z - r * 0.18f;
        float lighthouseGround = terrainHeight(lighthouseX, lighthouseZ);
        drawBox(identity, lighthouseX, lighthouseGround + 3.0f, lighthouseZ,
                1.2f, 6.0f, 1.2f, WHITE);
        drawBox(identity, lighthouseX, lighthouseGround + 6.1f, lighthouseZ,
                1.65f, 0.35f, 1.65f, RED);
        drawBox(identity, lighthouseX, lighthouseGround + 6.65f, lighthouseZ,
                0.25f, 0.8f, 0.25f, DARK);

        for (int i = 0; i < 9; i++) {
            float a = i * 2.399f + p * 0.7f;
            float rr = r * (0.25f + (i % 4) * 0.11f);
            float tx = x + (float)Math.cos(a) * rr;
            float tz = z + (float)Math.sin(a) * rr;
            float treeGround = terrainHeight(tx, tz);
            drawBox(identity, tx, treeGround + 1.35f, tz, 0.32f, 2.7f, 0.32f, BROWN);
            float[] crown = model(identity, tx, treeGround + 2.4f, tz,
                    0f, 1.45f, 2.8f, 1.45f, 0f);
            draw(cone, crown, LEAF);
        }
    }

    private float terrainHeight(float x, float z) {
        int p = state.nearestPort(x, z);
        if (p < 0) return 0f;
        float dx = x - GameState.PORT_X[p];
        float dz = z - GameState.PORT_Z[p];
        float d = (float)Math.sqrt(dx * dx + dz * dz);
        float r = GameState.PORT_R[p];
        float beach = coneSurfaceHeight(d, r, -0.28f, 1.7f);
        float green = coneSurfaceHeight(d, r * 0.78f, -0.02f, 2.9f);
        return Math.max(0f, Math.max(beach, green));
    }

    private float coneSurfaceHeight(float distance, float radius, float baseY, float height) {
        if (distance > radius) return -1000f;
        float plateauRadius = radius * 0.72f;
        if (distance <= plateauRadius) return baseY + height;
        float slope = (radius - distance) / (radius - plateauRadius);
        return baseY + height * slope;
    }

    private float distanceSquared(float ax, float az, float bx, float bz) {
        float dx = ax - bx;
        float dz = az - bz;
        return dx * dx + dz * dz;
    }

    private void drawBox(float[] parent, float x, float y, float z,
                         float sx, float sy, float sz, float[] color) {
        draw(cube, model(parent, x, y, z, 0f, sx, sy, sz), color);
    }

    private void drawBeam(float[] parent, float x1, float y1, float z1,
                          float x2, float y2, float z2, float thickness, float[] color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float horizontal = (float)Math.sqrt(dx * dx + dz * dz);
        float length = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.001f) return;
        float yaw = (float)Math.atan2(dx, dz) * 180f / GameState.PI;
        float pitch = -(float)Math.atan2(dy, Math.max(0.001f, horizontal))
                * 180f / GameState.PI;
        float[] local = new float[16];
        float[] out = new float[16];
        Matrix.setIdentityM(local, 0);
        Matrix.translateM(local, 0, (x1 + x2) * 0.5f, (y1 + y2) * 0.5f, (z1 + z2) * 0.5f);
        Matrix.rotateM(local, 0, yaw, 0f, 1f, 0f);
        Matrix.rotateM(local, 0, pitch, 1f, 0f, 0f);
        Matrix.scaleM(local, 0, thickness, thickness, length);
        Matrix.multiplyMM(out, 0, parent, 0, local, 0);
        draw(cube, out, color);
    }

    private void drawRoundBeam(float[] parent, float x1, float y1, float z1,
                               float x2, float y2, float z2, float radius, float[] color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float horizontal = (float)Math.sqrt(dx * dx + dz * dz);
        float length = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.001f) return;
        float yaw = (float)Math.atan2(dx, dz) * 180f / GameState.PI;
        float pitch = -(float)Math.atan2(dy, Math.max(0.001f, horizontal))
                * 180f / GameState.PI;
        float[] local = new float[16];
        float[] out = new float[16];
        Matrix.setIdentityM(local, 0);
        Matrix.translateM(local, 0, (x1 + x2) * 0.5f, (y1 + y2) * 0.5f,
                (z1 + z2) * 0.5f);
        Matrix.rotateM(local, 0, yaw, 0f, 1f, 0f);
        Matrix.rotateM(local, 0, pitch, 1f, 0f, 0f);
        Matrix.scaleM(local, 0, radius, radius, length);
        Matrix.multiplyMM(out, 0, parent, 0, local, 0);
        draw(cylinder, out, color);
    }

    private float[] model(float[] parent, float x, float y, float z,
                          float ry, float sx, float sy, float sz) {
        return model(parent, x, y, z, ry, sx, sy, sz, 0f);
    }

    private float[] model(float[] parent, float x, float y, float z,
                          float ry, float sx, float sy, float sz, float rz) {
        float[] local = new float[16];
        float[] out = new float[16];
        Matrix.setIdentityM(local, 0);
        Matrix.translateM(local, 0, x, y, z);
        if (ry != 0f) Matrix.rotateM(local, 0, ry, 0f, 1f, 0f);
        if (rz != 0f) Matrix.rotateM(local, 0, rz, 0f, 0f, 1f);
        Matrix.scaleM(local, 0, sx, sy, sz);
        Matrix.multiplyMM(out, 0, parent, 0, local, 0);
        return out;
    }

    private void draw(Mesh mesh, float[] model, float[] color) {
        draw(mesh, model, color, false);
    }

    private void drawWater(Mesh mesh, float[] model, float[] color) {
        draw(mesh, model, color, true);
    }

    private void draw(Mesh mesh, float[] model, float[] color, boolean water) {
        float[] mvp = new float[16];
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0);
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0);
        GLES20.glUniform4fv(uColor, 1, color, 0);
        GLES20.glUniform1f(uTime, elapsed);
        GLES20.glUniform1f(uWater, water ? 1f : 0f);
        GLES20.glUniform1f(uStorm, state.storm);
        GLES20.glUniform1f(uShipHeading, state.shipHeading);
        GLES20.glEnableVertexAttribArray(aPosition);
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 12, mesh.buffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.count);
    }

    private float[] copy(float[] src) {
        float[] out = new float[16];
        System.arraycopy(src, 0, out, 0, 16);
        return out;
    }

    private static int createProgram(String vertex, String fragment) {
        int vs = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vs, vertex);
        GLES20.glCompileShader(vs);
        int fs = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fs, fragment);
        GLES20.glCompileShader(fs);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, vs);
        GLES20.glAttachShader(p, fs);
        GLES20.glLinkProgram(p);
        return p;
    }

    private static Mesh makeCone(int segments) {
        float[] v = new float[segments * 27];
        int k = 0;
        for (int i = 0; i < segments; i++) {
            float a = (float)(i * Math.PI * 2.0 / segments);
            float b = (float)((i + 1) * Math.PI * 2.0 / segments);
            float ax = (float)Math.cos(a), az = (float)Math.sin(a);
            float bx = (float)Math.cos(b), bz = (float)Math.sin(b);
            float[] tri = {ax,0f,az, bx,0f,bz, bx*0.72f,1f,bz*0.72f,
                    ax,0f,az, bx*0.72f,1f,bz*0.72f, ax*0.72f,1f,az*0.72f,
                    0f,1f,0f, ax*0.72f,1f,az*0.72f, bx*0.72f,1f,bz*0.72f};
            for (float f : tri) v[k++] = f;
        }
        return new Mesh(v);
    }

    private static Mesh makeSphere(int slices, int stacks) {
        float[] vertices = new float[slices * stacks * 18];
        int k = 0;
        for (int stack = 0; stack < stacks; stack++) {
            float v0 = -GameState.PI * 0.5f + GameState.PI * stack / stacks;
            float v1 = -GameState.PI * 0.5f + GameState.PI * (stack + 1) / stacks;
            for (int slice = 0; slice < slices; slice++) {
                float u0 = GameState.PI * 2f * slice / slices;
                float u1 = GameState.PI * 2f * (slice + 1) / slices;
                k = putSpherePoint(vertices, k, u0, v0);
                k = putSpherePoint(vertices, k, u1, v0);
                k = putSpherePoint(vertices, k, u1, v1);
                k = putSpherePoint(vertices, k, u0, v0);
                k = putSpherePoint(vertices, k, u1, v1);
                k = putSpherePoint(vertices, k, u0, v1);
            }
        }
        return new Mesh(vertices);
    }

    private static int putSpherePoint(float[] target, int index, float longitude, float latitude) {
        float cosLat = (float)Math.cos(latitude);
        target[index++] = cosLat * (float)Math.cos(longitude);
        target[index++] = (float)Math.sin(latitude);
        target[index++] = cosLat * (float)Math.sin(longitude);
        return index;
    }

    private static Mesh makeOceanGrid(int cells, float size) {
        float[] vertices = new float[cells * cells * 18];
        float step = size / cells;
        float start = -size * 0.5f;
        int k = 0;
        for (int z = 0; z < cells; z++) {
            for (int x = 0; x < cells; x++) {
                float x0 = start + x * step;
                float x1 = x0 + step;
                float z0 = start + z * step;
                float z1 = z0 + step;
                float[] quad = {x0,0f,z0, x1,0f,z0, x1,0f,z1,
                        x0,0f,z0, x1,0f,z1, x0,0f,z1};
                for (float value : quad) vertices[k++] = value;
            }
        }
        return new Mesh(vertices);
    }

    private static float[] buildMainSail(float billow) {
        int columns = 5;
        int rows = 6;
        float[] vertices = new float[columns * rows * 18];
        int k = 0;
        for (int row = 0; row < rows; row++) {
            float v0 = row / (float)rows;
            float v1 = (row + 1) / (float)rows;
            for (int column = 0; column < columns; column++) {
                float u0 = column / (float)columns;
                float u1 = (column + 1) / (float)columns;
                k = putSailPoint(vertices, k, u0, v0, billow);
                k = putSailPoint(vertices, k, u1, v0, billow);
                k = putSailPoint(vertices, k, u1, v1, billow);
                k = putSailPoint(vertices, k, u0, v0, billow);
                k = putSailPoint(vertices, k, u1, v1, billow);
                k = putSailPoint(vertices, k, u0, v1, billow);
            }
        }
        return vertices;
    }

    private static int putSailPoint(float[] target, int index, float u, float v, float billow) {
        float width = 5.05f * (1f - v * 0.61f);
        target[index++] = billow * (float)Math.sin(Math.PI * u)
                * (float)Math.sin(Math.PI * (0.08f + v * 0.86f));
        target[index++] = 3.25f + v * 7.05f;
        target[index++] = -0.2f - width * u;
        return index;
    }

    private static float[] buildJib(float clewX) {
        return new float[]{
                clewX, JIB_CLEW_Y, JIB_CLEW_Z,
                0f, JIB_HEAD_Y, JIB_HEAD_Z,
                0f, JIB_TACK_Y, JIB_TACK_Z
        };
    }

    private static final class Mesh {
        final FloatBuffer buffer;
        final int count;
        Mesh(float[] vertices) {
            count = vertices.length / 3;
            buffer = ByteBuffer.allocateDirect(vertices.length * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            buffer.put(vertices).position(0);
        }

        void update(float[] vertices) {
            if (vertices.length / 3 != count) throw new IllegalArgumentException("mesh size");
            buffer.position(0);
            buffer.put(vertices);
            buffer.position(0);
        }
    }

    private static final float[] PLANE = {
            -0.5f,0f,-0.5f, 0.5f,0f,-0.5f, 0.5f,0f,0.5f,
            -0.5f,0f,-0.5f, 0.5f,0f,0.5f, -0.5f,0f,0.5f
    };

    private static final float[] CUBE = {
            -0.5f,-0.5f,0.5f, 0.5f,-0.5f,0.5f, 0.5f,0.5f,0.5f,
            -0.5f,-0.5f,0.5f, 0.5f,0.5f,0.5f, -0.5f,0.5f,0.5f,
            0.5f,-0.5f,-0.5f, -0.5f,-0.5f,-0.5f, -0.5f,0.5f,-0.5f,
            0.5f,-0.5f,-0.5f, -0.5f,0.5f,-0.5f, 0.5f,0.5f,-0.5f,
            -0.5f,-0.5f,-0.5f, -0.5f,-0.5f,0.5f, -0.5f,0.5f,0.5f,
            -0.5f,-0.5f,-0.5f, -0.5f,0.5f,0.5f, -0.5f,0.5f,-0.5f,
            0.5f,-0.5f,0.5f, 0.5f,-0.5f,-0.5f, 0.5f,0.5f,-0.5f,
            0.5f,-0.5f,0.5f, 0.5f,0.5f,-0.5f, 0.5f,0.5f,0.5f,
            -0.5f,0.5f,0.5f, 0.5f,0.5f,0.5f, 0.5f,0.5f,-0.5f,
            -0.5f,0.5f,0.5f, 0.5f,0.5f,-0.5f, -0.5f,0.5f,-0.5f,
            -0.5f,-0.5f,-0.5f, 0.5f,-0.5f,-0.5f, 0.5f,-0.5f,0.5f,
            -0.5f,-0.5f,-0.5f, 0.5f,-0.5f,0.5f, -0.5f,-0.5f,0.5f
    };

    private static final float[] HULL = {
            -2.45f,1f,-7.5f, 2.45f,1f,-7.5f, 0.65f,1f,8.2f,
            -2.45f,1f,-7.5f, 0.65f,1f,8.2f, -0.65f,1f,8.2f,
            -2.45f,1f,-7.5f, -0.65f,1f,8.2f, 0f,-1.15f,5.8f,
            -2.45f,1f,-7.5f, 0f,-1.15f,5.8f, -1.25f,-1.0f,-6.7f,
            2.45f,1f,-7.5f, 1.25f,-1.0f,-6.7f, 0f,-1.15f,5.8f,
            2.45f,1f,-7.5f, 0f,-1.15f,5.8f, 0.65f,1f,8.2f,
            -2.45f,1f,-7.5f, -1.25f,-1.0f,-6.7f, 1.25f,-1.0f,-6.7f,
            -2.45f,1f,-7.5f, 1.25f,-1.0f,-6.7f, 2.45f,1f,-7.5f,
            -1.25f,-1.0f,-6.7f, 0f,-1.15f,5.8f, 1.25f,-1.0f,-6.7f
    };

    private static final float[] MAIN_SAIL = {
            0f,3.25f,-0.2f, 0f,10.3f,-0.2f, 0f,8.25f,-5.25f,
            0f,3.25f,-0.2f, 0f,8.25f,-5.25f, 0f,3.25f,-5.25f
    };

    private static final float[] TRI_SAIL = {
            0f,0f,0f, 0f,1f,0f, 1f,0f,0f,
            0f,1f,0f, 1f,1f,0f, 1f,0f,0f,
            0f,1f,0f, 0.5f,0.5f,0.08f, 1f,0f,0f
    };

    private static final float[] BRIG_SAIL = {
            -1f,0f,0f, 1f,0f,0f, 0.82f,1f,0f,
            -1f,0f,0f, 0.82f,1f,0f, -0.82f,1f,0f
    };

}

