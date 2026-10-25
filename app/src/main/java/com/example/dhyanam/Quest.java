package com.example.dhyanam;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "quests")
public class Quest {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String title = "";

    public boolean completed;

    @ColumnInfo(name = "created_at")
    public long createdAt;
}
