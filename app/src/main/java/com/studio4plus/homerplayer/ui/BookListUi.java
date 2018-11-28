package com.studio4plus.homerplayer.ui;

import com.studio4plus.homerplayer.model.AudioBook;

import java.util.List;

public interface BookListUi {

    void updateBookList(List<AudioBook> audiobooks, int currentBookIndex);
    @SuppressWarnings("EmptyMethod")
    void updateCurrentBook(@SuppressWarnings("unused") int currentBookIndex);

    void initWithController(UiControllerBookList uiControllerBookList);
}
