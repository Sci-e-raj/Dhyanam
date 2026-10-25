package com.example.dhyanam;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {
    private static final int TODAY = 0;
    private static final int FOCUS = 1;
    private static final int PROGRESS = 2;
    private static final int SETTINGS = 3;
    private static final String SCREEN_KEY = "screen";
    private static final String PREVIOUS_SCREEN_KEY = "previous_screen";

    private int currentScreen = TODAY;
    private int previousScreen = TODAY;
    private FrameLayout screenContent;
    private TextView screenTitle;
    private Button settingsButton;
    private View bottomNavigation;
    private Button todayButton;
    private Button focusButton;
    private Button progressButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        screenContent = findViewById(R.id.screen_content);
        screenTitle = findViewById(R.id.screen_title);
        settingsButton = findViewById(R.id.settings_button);
        bottomNavigation = findViewById(R.id.bottom_navigation);
        todayButton = findViewById(R.id.nav_today);
        focusButton = findViewById(R.id.nav_focus);
        progressButton = findViewById(R.id.nav_progress);

        todayButton.setOnClickListener(view -> showScreen(TODAY));
        focusButton.setOnClickListener(view -> showScreen(FOCUS));
        progressButton.setOnClickListener(view -> showScreen(PROGRESS));
        settingsButton.setOnClickListener(view -> {
            if (currentScreen == SETTINGS) {
                showScreen(previousScreen);
            } else {
                previousScreen = currentScreen;
                showScreen(SETTINGS);
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentScreen == SETTINGS) {
                    showScreen(previousScreen);
                } else if (currentScreen != TODAY) {
                    showScreen(TODAY);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        if (savedInstanceState != null) {
            currentScreen = savedInstanceState.getInt(SCREEN_KEY, TODAY);
            previousScreen = savedInstanceState.getInt(PREVIOUS_SCREEN_KEY, TODAY);
        }
        showScreen(currentScreen);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(SCREEN_KEY, currentScreen);
        outState.putInt(PREVIOUS_SCREEN_KEY, previousScreen);
        super.onSaveInstanceState(outState);
    }

    private void showScreen(int screen) {
        currentScreen = screen;
        int layout;
        int title;
        switch (screen) {
            case FOCUS:
                layout = R.layout.screen_focus;
                title = R.string.focus_title;
                break;
            case PROGRESS:
                layout = R.layout.screen_progress;
                title = R.string.progress_title;
                break;
            case SETTINGS:
                layout = R.layout.screen_settings;
                title = R.string.settings_title;
                break;
            default:
                layout = R.layout.screen_today;
                title = R.string.today_title;
                currentScreen = TODAY;
        }
        screenContent.removeAllViews();
        getLayoutInflater().inflate(layout, screenContent, true);
        if (currentScreen == TODAY) {
            findViewById(R.id.add_quest_button).setOnClickListener(view ->
                    Toast.makeText(this, R.string.add_quest_unavailable, Toast.LENGTH_SHORT).show());
        } else if (currentScreen == FOCUS) {
            findViewById(R.id.start_focus_button).setOnClickListener(view ->
                    Toast.makeText(this, R.string.start_focus_unavailable, Toast.LENGTH_SHORT).show());
        }
        screenTitle.setText(title);
        boolean inSettings = currentScreen == SETTINGS;
        settingsButton.setText(inSettings ? R.string.back : R.string.settings_title);
        bottomNavigation.setVisibility(inSettings ? View.GONE : View.VISIBLE);
        todayButton.setSelected(currentScreen == TODAY);
        focusButton.setSelected(currentScreen == FOCUS);
        progressButton.setSelected(currentScreen == PROGRESS);
    }
}
