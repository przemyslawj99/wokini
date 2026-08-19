package pl.kursnahoryzont.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

public final class MainActivity extends Activity {
    private GameState state;
    private GLSurfaceView surface;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersive();

        state = new GameState(this);
        state.load();

        surface = new GLSurfaceView(this);
        surface.setEGLContextClientVersion(2);
        surface.setPreserveEGLContextOnPause(true);
        surface.setRenderer(new WorldRenderer(state));
        surface.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        FrameLayout root = new FrameLayout(this);
        root.addView(surface, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(new GameOverlay(this, state), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        if (!state.tutorialSeen) showTutorial();
    }

    private void showTutorial() {
        new AlertDialog.Builder(this)
                .setTitle("Kurs na Horyzont — pokład slupa")
                .setMessage("Lewym kręgiem chodzisz, a po prawej stronie rozglądasz się. Wszystko obsługujesz fizycznie na pokładzie: koło na rufie, szot po lewej stronie masztu, fał po prawej, kotwicę na dziobie, cumę przy prawej burcie i trapy przy obu burtach. Otwórz zejściówkę, aby wejść do ładowni. Towar kupiony na targu odbierz z magazynu na kei, załaduj, przewieź, wyładuj i dopiero sprzedaj.")
                .setPositiveButton("Wyruszam", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        state.tutorialSeen = true;
                        state.save();
                        immersive();
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override protected void onResume() {
        super.onResume();
        immersive();
        surface.onResume();
    }

    @Override protected void onPause() {
        state.save();
        surface.onPause();
        super.onPause();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    @Override public void onBackPressed() {
        if (state.tradeOpen) state.tradeOpen = false;
        else if (state.mapOpen) state.mapOpen = false;
        else if (state.journalOpen) state.journalOpen = false;
        else state.togglePause();
        immersive();
    }
}
