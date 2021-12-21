package com.github.ppotseluev.algorate.util.javafx;

import javafx.scene.shape.Rectangle;

/**
 * Represents an area on the screen that was selected by a mouse drag operation.
 */
public class SelectionRectangle extends Rectangle {

    private static final String STYLE_CLASS_SELECTION_BOX = "chart-selection-rectangle";

    public SelectionRectangle() {

        getStyleClass().addAll(STYLE_CLASS_SELECTION_BOX);
        setVisible(false);
        setManaged(false);
        setMouseTransparent(true);

    }
}
