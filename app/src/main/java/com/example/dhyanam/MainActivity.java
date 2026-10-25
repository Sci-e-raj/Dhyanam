package com.example.dhyanam;

import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Collections;
import java.util.List;

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
    private QuestDao questDao;
    private List<Quest> quests = Collections.emptyList();
    private boolean questsLoaded;

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

        questDao = QuestDatabase.getInstance(this).questDao();
        questDao.observeAll().observe(this, savedQuests -> {
            quests = savedQuests;
            questsLoaded = true;
            renderQuests();
        });
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
            findViewById(R.id.add_quest_button).setOnClickListener(view -> showQuestDialog(null));
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
        renderQuests();
    }

    private void renderQuests() {
        if (currentScreen == TODAY) {
            TextView progress = findViewById(R.id.daily_progress_text);
            TextView empty = findViewById(R.id.quests_empty_view);
            LinearLayout list = findViewById(R.id.quest_list);
            if (!questsLoaded) {
                progress.setText(R.string.quests_loading);
                empty.setText(R.string.quests_loading);
                return;
            }

            int completed = 0;
            for (Quest quest : quests) {
                if (quest.completed) completed++;
            }
            progress.setText(quests.isEmpty()
                    ? getString(R.string.daily_progress_empty)
                    : getString(R.string.daily_progress_count, completed, quests.size()));
            empty.setVisibility(quests.isEmpty() ? View.VISIBLE : View.GONE);
            empty.setText(R.string.quests_empty);
            list.removeAllViews();
            for (Quest quest : quests) {
                View row = getLayoutInflater().inflate(R.layout.item_quest, list, false);
                CheckBox checkBox = row.findViewById(R.id.quest_checkbox);
                checkBox.setText(quest.title);
                checkBox.setChecked(quest.completed);
                if (quest.completed) {
                    checkBox.setPaintFlags(checkBox.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                }
                checkBox.setOnCheckedChangeListener((button, checked) ->
                        QuestDatabase.writeExecutor.execute(() -> questDao.setCompleted(quest.id, checked)));

                Button edit = row.findViewById(R.id.edit_quest_button);
                edit.setContentDescription(getString(R.string.edit_quest_description, quest.title));
                edit.setOnClickListener(view -> showQuestDialog(quest));

                Button delete = row.findViewById(R.id.delete_quest_button);
                delete.setContentDescription(getString(R.string.delete_quest_description, quest.title));
                delete.setOnClickListener(view -> confirmDelete(quest));
                list.addView(row);
            }
        } else if (currentScreen == PROGRESS) {
            TextView empty = findViewById(R.id.completed_quests_empty_view);
            LinearLayout list = findViewById(R.id.completed_quest_list);
            list.removeAllViews();
            if (!questsLoaded) {
                empty.setText(R.string.quests_loading);
                return;
            }
            int completed = 0;
            for (Quest quest : quests) {
                if (quest.completed) {
                    completed++;
                    View row = getLayoutInflater().inflate(R.layout.item_completed_quest, list, false);
                    TextView title = row.findViewById(R.id.completed_quest_title);
                    title.setText(quest.title);
                    list.addView(row);
                }
            }
            empty.setVisibility(completed == 0 ? View.VISIBLE : View.GONE);
            empty.setText(R.string.completed_quests_empty);
        }
    }

    private void showQuestDialog(Quest quest) {
        View form = getLayoutInflater().inflate(R.layout.dialog_quest_title, null);
        EditText titleInput = form.findViewById(R.id.quest_title_input);
        if (quest != null) {
            titleInput.setText(quest.title);
            titleInput.setSelection(titleInput.length());
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(quest == null ? R.string.add_quest : R.string.edit_quest)
                .setView(form)
                .setPositiveButton(quest == null ? R.string.add_quest : R.string.save_quest, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String rawTitle = titleInput.getText().toString();
            int start = 0;
            int end = rawTitle.length();
            while (start < end && isBlankSpace(rawTitle.charAt(start))) start++;
            while (end > start && isBlankSpace(rawTitle.charAt(end - 1))) end--;
            String title = rawTitle.substring(start, end);
            if (title.isEmpty()) {
                titleInput.setError(getString(R.string.blank_quest_title));
                titleInput.requestFocus();
                return;
            }
            if (quest == null) {
                Quest newQuest = new Quest();
                newQuest.title = title;
                newQuest.createdAt = System.currentTimeMillis();
                QuestDatabase.writeExecutor.execute(() -> questDao.insert(newQuest));
            } else {
                QuestDatabase.writeExecutor.execute(() -> questDao.updateTitle(quest.id, title));
            }
            dialog.dismiss();
        }));
        dialog.show();
        titleInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                return true;
            }
            return false;
        });
    }

    private void confirmDelete(Quest quest) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_quest)
                .setMessage(getString(R.string.delete_quest_message, quest.title))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete_quest, (dialog, which) ->
                        QuestDatabase.writeExecutor.execute(() -> questDao.delete(quest.id)))
                .show();
    }

    private static boolean isBlankSpace(char character) {
        return Character.isWhitespace(character) || Character.isSpaceChar(character);
    }
}
