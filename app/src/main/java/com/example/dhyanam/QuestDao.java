package com.example.dhyanam;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface QuestDao {
    @Query("SELECT * FROM quests ORDER BY completed ASC, created_at DESC, id DESC")
    LiveData<List<Quest>> observeAll();

    @Insert
    void insert(Quest quest);

    @Query("UPDATE quests SET title = :title WHERE id = :id")
    void updateTitle(long id, String title);

    @Query("UPDATE quests SET completed = :completed WHERE id = :id")
    void setCompleted(long id, boolean completed);

    @Query("DELETE FROM quests WHERE id = :id")
    void delete(long id);
}
