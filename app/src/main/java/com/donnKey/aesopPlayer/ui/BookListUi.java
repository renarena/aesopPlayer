package com.donnKey.aesopPlayer.ui;

import com.donnKey.aesopPlayer.model.AudioBook;

import java.util.List;

public interface BookListUi {

    void updateBookList(List<AudioBook> audiobooks, int currentBookIndex);
    @SuppressWarnings("EmptyMethod")
    void updateCurrentBook(@SuppressWarnings("unused") int currentBookIndex);

    void initWithController(UiControllerBookList uiControllerBookList);
}
