/**
 * This file is part of SmileMeBack.

 SmileMeBack is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 SmileMeBack is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with SmileMeBack.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.smilemeback.activities;

import android.animation.ValueAnimator;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.DragEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.smilemeback.Constants;
import com.smilemeback.R;
import com.smilemeback.storage.Category;
import com.smilemeback.storage.Image;
import com.smilemeback.storage.Storage;
import com.smilemeback.storage.StorageException;
import com.smilemeback.views.IconView;
import com.smilemeback.views.IconViewSide;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


public class GalleryActivity extends Activity implements GallerySelectionModeListener {
    private static Logger logger = Logger.getLogger(GalleryActivity.class.getCanonicalName());
    protected GalleryActivityState state = GalleryActivityState.VIEW;
    protected GallerySelectionMode selectionMode;
    protected MediaPlayer player = new MediaPlayer();

    protected ListView listView;
    protected LinearLayout listViewContainer; // this is required for animation
    protected GridView gridView;

    protected ImageAdapter imageAdapter;
    protected CategoryAdapter categoryAdapter;

    protected List<Category> categories;
    protected Category currentCategory;
    protected List<Image> images;


    protected ImageDragEventListener dragListener = new ImageDragEventListener();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // set up the gallery selection mode callback
        selectionMode = new GallerySelectionMode(this, this);

        // set up adapters
        imageAdapter = new ImageAdapter();
        categoryAdapter = new CategoryAdapter();

        // setup actionbar
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setTitle(getString(R.string.gallery_actionbar_title));

        try {
            loadContents();
        } catch (StorageException e) {
            showStorageExceptionAlertAndFinish(e);
        }
    }

    protected void loadContents() throws StorageException {
        Intent intent = getIntent();
        int category_idx = intent.getIntExtra(Constants.CATEGORY_INDEX, 0);

        loadCategories();
        if (categories.size() > 0) {
            setContentView(R.layout.gallery);
            currentCategory = categories.get(category_idx);
            loadImages(currentCategory);
            initializeGridView();
            initializeListView();
        } else {
            setContentView(R.layout.gallery_empty);
        }

    }

    protected void showStorageExceptionAlertAndFinish(StorageException e) {
        logger.log(Level.SEVERE, e.getMessage());
        new AlertDialog.Builder(this)
            .setTitle("Storage exception")
            .setMessage(e.getMessage())
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            })
            .show();
    }

    protected void initializeGridView() {
        gridView = (GridView) findViewById(R.id.gallery_contents_grid_view);
        gridView.setAdapter(imageAdapter);
    }

    public void initializeListView() {
        listViewContainer = (LinearLayout)findViewById(R.id.gallery_listview_container);
        listView = (ListView) findViewById(R.id.gallery_list_view);
        setListViewWeight(0);
        listView.setAdapter(categoryAdapter);
        listView.setOnDragListener(dragListener);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                view.setSelected(true);
                Category category = categories.get(position);
                if (category != currentCategory) {
                    loadImages(category);
                    imageAdapter.notifyDataSetChanged();
                    setAllGridViewItemsChecked(false);
                    selectionMode.setTotal(images.size());
                    selectionMode.setNumSelected(0);
                }
            }
        });
    }

    protected void loadCategories() {
        Storage storage = new Storage(this);
        try {
            categories = storage.getCategories();
        } catch (StorageException e) {
            showStorageExceptionAlertAndFinish(e);
        }
    }

    public void loadImages(Category category) {
        Storage storage = new Storage(this);
        currentCategory = category;
        try {
            images = storage.getCategoryImages(category);
        } catch (StorageException e) {
            showStorageExceptionAlertAndFinish(e);
        }
    }

    @Override
    public void gallerySelectionModeFinished() {
        state = GalleryActivityState.VIEW;
        setGridViewCheckBoxesVisible(false);
        animateListViewOut();
    }

    @Override
    public void selectAllItems() {
        setAllGridViewItemsChecked(true);
    }

    @Override
    public void deselectAllItems() {
        setAllGridViewItemsChecked(false);
    }

    class ImageAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return images.size();
        }

        @Override
        public Object getItem(int position) {
            return images.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            IconView view;
            if (convertView != null) {
                view = (IconView)convertView;
            } else {
                view = new IconView(GalleryActivity.this, getResources().getXml(R.layout.icon_view), false);
            }
            final Image image = images.get(position);
            view.setImageBitmap(image.getImage());
            view.setLabel(image.getName().toString());

            view.setCheckboxVisible(state == GalleryActivityState.SELECT);

            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    IconView iconView = (IconView)view;
                    switch (state) {
                        case VIEW:
                            try {
                                if (!player.isPlaying()) {
                                    player.reset();
                                    player.setDataSource(new FileInputStream(image.getAudio()).getFD());
                                    player.prepare();
                                    player.start();
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        case SELECT:
                            iconView.toggle();
                            selectionMode.setNumSelected(getNumSelectedInGridView());
                            break;
                        default:
                            break;
                    }
                }
            });

            view.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    IconView iconView = (IconView)view;
                    switch (state) {
                        case VIEW:
                            setAllGridViewItemsChecked(false);
                            iconView.setChecked(true);
                            gotoSelectionModeState();
                            break;
                        case SELECT:
                            // if the iconview is not selected, then ignore
                            if (!iconView.isChecked()) {
                                return true;
                            }
                            setSelectedIconViewsAlpha(Constants.SELECTED_ICONVIEW_ALPHA);
                            ClipData.Item item = new ClipData.Item(Constants.IMAGE_DRAG_TAG);
                            ClipData dragData = new ClipData(Constants.IMAGE_DRAG_TAG, new String[] {ClipDescription.MIMETYPE_TEXT_PLAIN}, item);
                            View.DragShadowBuilder shadow = new ImageDragShadowBuilder(iconView);
                            iconView.setTag(Constants.IMAGE_DRAG_TAG);
                            iconView.startDrag(dragData, shadow, null, 0);
                            vibrate();
                            break;
                        default:
                            break;
                    }
                    return true;
                }
            });

            return view;
        }
    }

    /**
     * Calling this method will enter the image selection state.
     */
    protected void gotoSelectionModeState() {
        animateListViewIn();
        state = GalleryActivityState.SELECT;
        GalleryActivity.this.startActionMode(selectionMode);
        selectionMode.setNumSelected(1);
        selectionMode.setTotal(images.size());
        setGridViewCheckBoxesVisible(true);
        vibrate();
    }

    /**
     * Method that vibrates the phone.
     */
    protected void vibrate() {
        Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vib.hasVibrator()) {
            vib.vibrate(100);
        }
    }

    protected class ImageDragShadowBuilder extends View.DragShadowBuilder {
        private Drawable shadow;

        public ImageDragShadowBuilder(View view) {
            super(view);
            shadow = GalleryActivity.this.getCombinedIconViewDrawable();
        }

        @Override
        public void onProvideShadowMetrics(Point size, Point touch) {
            int width = (int)getResources().getDimension(R.dimen.iconview_side_width);
            int height = (int)getResources().getDimension(R.dimen.iconview_side_height);
            shadow.setBounds(0, 0, shadow.getIntrinsicWidth(), shadow.getIntrinsicHeight());
            size.set(shadow.getIntrinsicWidth(), shadow.getIntrinsicHeight());
            touch.set(width / 2, height / 2);
        }

        @Override
        public void onDrawShadow(Canvas canvas) {
            shadow.draw(canvas);
        }
    }

    protected class ImageDragEventListener implements View.OnDragListener {

        @Override
        public boolean onDrag(View v, DragEvent event) {
            final int action = event.getAction();

            switch (action) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    return true;
                case DragEvent.ACTION_DRAG_LOCATION:
                    int idx = getListViewChildInCoords((int) event.getX(), (int) event.getY());
                    if (idx >= 0) {
                        listView.smoothScrollToPosition(idx);
                        listView.setSelection(idx);
                    }
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    return true;
                case DragEvent.ACTION_DROP:
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    setSelectedIconViewsAlpha(1f);
                    return true;
            }
            return false;
        }
    }

    protected List<IconView> getSelectedIconViews() {
        int n = gridView.getChildCount();
        List<IconView> selected = new ArrayList<>();
        for (int idx=0 ; idx<n ; ++idx) {
            IconView iconView = (IconView)gridView.getChildAt(idx);
            if (iconView.isChecked()) {
                selected.add(iconView);
            }
        }
        return selected;
    }

    /**
     * The the side listview child that is within given coordinates.
     *
     * @param x
     * @param y
     * @return Index of the child or negative integer in case no such child exists.
     */
    protected int getListViewChildInCoords(int x, int y) {
        int n = listView.getChildCount();
        for (int idx = 0; idx < n; ++idx) {
            IconViewSide view = (IconViewSide) listView.getChildAt(idx);
            Rect bounds = new Rect();
            view.getHitRect(bounds);
            if (bounds.contains(x, y)) {
                return idx;
            }
        }
        return -1;
    }

    protected void setSelectedIconViewsAlpha(float alpha) {
        for (IconView selected : getSelectedIconViews()) {
            selected.setAlpha(alpha);
        }
    }

    protected Drawable getCombinedIconViewDrawable() {
        List<IconView> selected = getSelectedIconViews();
        // use the width/height the same as with the IconViewSide dimensions.
        int singleWidth = (int)getResources().getDimension(R.dimen.iconview_side_width);
        int singleHeight = (int)getResources().getDimension(R.dimen.iconview_side_height);
        int nrows = selected.size() / Constants.NUM_COLS_IN_DRAG_SHADOW;
        if (selected.size() % Constants.NUM_COLS_IN_DRAG_SHADOW != 0) {
            nrows += 1;
        }
        // in order to avoid memory errors when there are many iconviews,
        // limit their numbers
        int iconLimit = selected.size();
        if (nrows > Constants.MAX_ROWS_IN_DRAG_SHADOW) {
            iconLimit = Constants.MAX_ROWS_IN_DRAG_SHADOW * Constants.NUM_COLS_IN_DRAG_SHADOW;
            nrows = Constants.MAX_ROWS_IN_DRAG_SHADOW;
        }
        // create a big combined bitmap and erase its contents
        Bitmap combined = Bitmap.createBitmap(
                Constants.NUM_COLS_IN_DRAG_SHADOW*singleWidth,
                nrows*singleHeight,
                Bitmap.Config.ARGB_8888);
        combined.eraseColor(0x00000000);
        Canvas canvas = new Canvas(combined);
        int row = 0;
        int col = 0;
        int iconIndex = 0;
        for (IconView iconView : selected) {
            // in case we should not draw more icons, then exit the loop
            if (iconIndex >= iconLimit) {
                break;
            }
            // draw the iconimage to next free location on combiend bitmap
            Drawable drawable = iconView.getDrawable();
            drawable.setBounds(
                    col*singleWidth,
                    row*singleHeight,
                    col*singleWidth + singleWidth,
                    row*singleHeight + singleHeight);
            drawable.draw(canvas);
            col += 1;
            iconIndex += 1;
            // update col/row counter
            if (col >= Constants.NUM_COLS_IN_DRAG_SHADOW) {
                col = 0;
                row += 1;
            }
        }

        return new BitmapDrawable(getResources(), combined);
    }

    class CategoryAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return categories.size();
        }

        @Override
        public Object getItem(int position) {
            return categories.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final Category category = categories.get(position);
            final IconView view = new IconViewSide(GalleryActivity.this, getResources().getXml(R.layout.icon_view_side), false);
            view.setImageBitmap(category.getThumbnail());
            view.setLabel(category.getName().toString());

            view.setCheckboxVisible(false);

            return view;
        }
    }

    /**
     * @return The number of selected icons in the gridView.
     */
    protected int getNumSelectedInGridView() {
        final int n = gridView.getChildCount();
        int count = 0;
        for (int idx = 0; idx < n; ++idx) {
            IconView view = (IconView) gridView.getChildAt(idx);
            if (view.isChecked()) {
                count += 1;
            }
        }
        return count;
    }

    protected void setAllGridViewItemsChecked(boolean checked) {
        final int n = gridView.getChildCount();
        for (int idx = 0; idx < n; ++idx) {
            IconView view = (IconView) gridView.getChildAt(idx);
            view.setChecked(checked);
            if (!checked) {
                view.setAlpha(1f);
            }
        }
        gridView.invalidate();
    }

    protected void setGridViewCheckBoxesVisible(boolean visible) {
        final int n = gridView.getChildCount();
        for (int idx = 0; idx < n; ++idx) {
            IconView view = (IconView) gridView.getChildAt(idx);
            view.setCheckboxVisible(visible);
        }
    }

    /**
     * Given an {@link com.smilemeback.views.IconView}, find out its position
     * in the gridView.
     * @param iconView
     * @return The position in gridview or -1 in case of not found.
     */
    public int getIconViewPositionInGridView(IconView iconView) {
        for (int idx=0 ; idx<gridView.getChildCount() ; ++idx) {
            if (gridView.getChildAt(idx) == iconView) {
                return idx;
            }
        }
        return -1;
    }


    public void setListViewWeight(float weight) {
        ((LinearLayout.LayoutParams)listViewContainer.getLayoutParams()).weight = weight;
        listViewContainer.requestLayout();
        logger.info("Animating to " + weight);
    }

    protected void animateListViewIn() {
        ValueAnimator va = ValueAnimator.ofFloat(0, 1);
        va.setRepeatMode(ValueAnimator.RESTART);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setListViewWeight((float) animation.getAnimatedValue());
            }
        });
        va.start();
    }

    protected void animateListViewOut() {
        ValueAnimator va = ValueAnimator.ofFloat(1, 0);
        va.setRepeatMode(ValueAnimator.RESTART);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setListViewWeight((float) animation.getAnimatedValue());
            }
        });
        va.start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        //MenuInflater inflater = getMenuInflater();
        //inflater.inflate(R.menu.gallery_selectionmode_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}