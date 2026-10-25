package com.example.dhyanam;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {Quest.class}, version = 1, exportSchema = false)
public abstract class QuestDatabase extends RoomDatabase {
    private static volatile QuestDatabase instance;
    public static final ExecutorService writeExecutor = Executors.newSingleThreadExecutor();

    public abstract QuestDao questDao();

    public static QuestDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (QuestDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                            QuestDatabase.class, "focusbuddy_quests").build();
                }
            }
        }
        return instance;
    }
}
