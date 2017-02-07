package hw.demo.piclayoutmanager;

import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class PicLayoutManager extends RecyclerView.LayoutManager {
    private static final String TAG = "PicLayoutManager";

    public static final int TYPE_STICKY_HEADER = -0xff;
    public static final int TYPE_PICITEM = TYPE_STICKY_HEADER + 1;
    public static final int TYPE_CUSTOM_START = 0;

    private static final int REQUEST_POSITION_NONE = -1;

    /**
     * divide RecyclerView to {@link PicLayoutManager#MAX_DIV_COL} columns
     */
    private static final int MAX_DIV_COL = 6;
    /**
     * Every item view must occupies {@link PicLayoutManager#MIN_OCP_COL} at least
     */
    private static final int MIN_OCP_COL = 2;
    /**
     * Every group has {@link PicLayoutManager#MAX_GROUP_ITEM_COUNT} at most
     */
    private static final int MAX_GROUP_ITEM_COUNT = 10;

    private DataChangeListener mDataChangeListener;

    private int mUnitWidth;
    private int mFirstVisiblePosition;
    private int mRequestPosition;
    private int mTopOffSet;

    private HashMap<Integer, OcpList> mOcpItemsMap = new HashMap<Integer, OcpList>();

    private SparseArray<View> mViewCache = new SparseArray<View>();
    private SparseArray<HeaderCache> mHeaderCache = new SparseArray<HeaderCache>();
    private HashMap<View, Integer> mViewPosMap = new HashMap<View, Integer>();

    private PicAdapter mAdapter;

    private int mItemCount;

    private boolean mNeedKeepCurrentPos;

    public void keepCurrentPosAfterDataChangedOnlyOnce() {
        mNeedKeepCurrentPos = true;
    }

    private static class HeaderCache {
        Rect bound;
        View header;
        boolean isFromCache;
    }

    public static class OcpSize {
        int startOcpW;
        int startOcpH;
        int ocpW;
        int ocpH;

        public OcpSize(int w, int h) {
            ocpW = w;
            ocpH = h;
        }
    }

    private static class OcpList {
        ArrayList<OcpSize> ocps;
        int firstIndex;
        int maxOcpH;

        OcpList(int firstIndex, ArrayList<OcpSize> ocpList) {
            this.firstIndex = firstIndex;
            this.ocps = ocpList;

            maxOcpH = -1;
            for (OcpSize size : ocpList) {
                maxOcpH = Math.max(maxOcpH, size.ocpH + size.startOcpH);
            }
        }
    }

    private static class SearchNode {
        ArrayList<OcpSize> branchOcps;
        int searchIndex;

        SearchNode(int oriW, int oriH, int pos) {
            branchOcps = new ArrayList<OcpSize>();
            for (int i = MIN_OCP_COL; i <= MAX_DIV_COL; ++i) {
                for (int j = MIN_OCP_COL; j <= MAX_DIV_COL; ++j) {
                    // TODO 优化，这里如果w为全长，那么ratio不如此项之后的都可以不加入branch队列
                    branchOcps.add(new OcpSize(i, j));
                }
            }

            Collections.sort(branchOcps, new BranchComparator(oriW, oriH));
            searchIndex = 0;

            // 按实际的长宽和位置做假随机
            int firstGroupSize = 1;
            float firstGroupRatio = (float) branchOcps.get(0).ocpW / (float) branchOcps.get(0).ocpH;
            for (; firstGroupSize < branchOcps.size(); ++firstGroupSize) {
                OcpSize size = branchOcps.get(firstGroupSize);
                float ratio = (float) size.ocpW / (float) size.ocpH;
                if (ratio != firstGroupRatio) {
                    break;
                }
            }
            int firstIndex = (oriW + oriH + pos) % firstGroupSize;
            for (int i = 1; i <= firstIndex; ++i) {
                OcpSize size = branchOcps.remove(i);
                branchOcps.add(0, size);
            }
        }

        private class BranchComparator implements Comparator<OcpSize> {
            int oriW;
            int oriH;
            float ratio;

            BranchComparator(int oriW, int oriH) {
                this.oriW = oriW;
                this.oriH = oriH;
                ratio = (float) oriW / (float) oriH;
            }

            @Override
            public int compare(OcpSize lhs, OcpSize rhs) {
                float diffL = Math.abs(ratio - (float) lhs.ocpW / (float) lhs.ocpH);
                float diffR = Math.abs(ratio - (float) rhs.ocpW / (float) rhs.ocpH);
                if (diffL < diffR) {
                    return -1;
                } else if (diffL > diffR) {
                    return 1;
                }
                return 0;
            }
        }
    }

    public PicLayoutManager() {
        mRequestPosition = REQUEST_POSITION_NONE;
    }

    public void setDataChangeListener(DataChangeListener l) {
        mDataChangeListener = l;
    }

    private void setPicAdapter(PicAdapter adapter) {
        mAdapter = adapter;
    }

    private void relayoutPicItems() {
        mOcpItemsMap.clear();
        ArrayList<OcpSize> oris = new ArrayList<OcpSize>();
        int itemSize = mAdapter.getItemCount();
        int firstIndex = -1;
        for (int i = 0; i < itemSize; ++i) {
            boolean isGroupEnd = false;
            int type = mAdapter.getItemType(i);
            int groupId = mAdapter.getPicGroupId(i);
            if (type == TYPE_PICITEM) {
                if (firstIndex == -1) {
                    firstIndex = i;
                }
                oris.add(new OcpSize(mAdapter.getOriWidth(i), mAdapter.getOriHeight(i)));

                if (i + 1 < itemSize) {
                    if (mAdapter.getItemType(i + 1) != TYPE_PICITEM) {
                        isGroupEnd = true;
                    } else {
                        if (mAdapter.getPicGroupId(i + 1) != groupId) {
                            isGroupEnd = true;
                        }
                    }
                } else {
                    isGroupEnd = true;
                }
            }

            if (isGroupEnd && oris.size() > 0) {
                if (mOcpItemsMap.get(groupId) == null) {
                    mOcpItemsMap.put(groupId, new OcpList(firstIndex, generateOcpList(oris)));
                    oris.clear();
                } else {
                    throw new RuntimeException("Do not divide a group for groupId: " + groupId);
                }
                firstIndex = -1;
            }
        }
    }

    private ArrayList<OcpSize> generateOcpList(ArrayList<OcpSize> oriItems) {
        ArrayList<SearchNode> tree = new ArrayList<SearchNode>();
        for (int i = 0; i < oriItems.size(); ++i) {
            OcpSize size = oriItems.get(i);
            SearchNode node = new SearchNode(size.ocpW, size.ocpH, i);
            tree.add(node);
        }

        int[] colHs = new int[MAX_DIV_COL];
        for (int i = 0; i < colHs.length; ++i) {
            colHs[i] = 0;
        }

        int searchedDp = 0;
        while (true) {
            searchedDp = searchOcps(tree, searchedDp, 0, colHs);
            if (searchedDp == -1 || searchedDp >= tree.size()) {
                break;
            }
        }

        ArrayList<OcpSize> ocpList = null;
        if (searchedDp != -1) {
            ocpList = new ArrayList<OcpSize>();
            for (int i = 0; i < tree.size(); ++i) {
                SearchNode node = tree.get(i);
                OcpSize size = node.branchOcps.get(node.searchIndex);
                ocpList.add(size);
            }
        } else {
            Log.e(TAG, "Could not find a way to display this view");
        }
        return ocpList;
    }

    private int searchOcps(ArrayList<SearchNode> tree, int baseDp, int dp, int[] colHs) {
        if (tree == null || tree.size() == 0) {
            return -1;
        }

        if (dp >= MAX_GROUP_ITEM_COUNT) {
            return -1;
        }

        int firstCol = 0;
        boolean isALine = true;
        for (int i = 1; i < colHs.length; ++i) {
            if (colHs[i] < colHs[firstCol]) {
                firstCol = i;
            }
            if (isALine && colHs[i - 1] != colHs[i]) {
                isALine = false;
            }
        }

        if (isALine && dp != 0) {
            return baseDp + dp;
        }

        if (tree.size() <= baseDp + dp) {
            return -1;
        }

        int searchedDp = -1;
        SearchNode node = tree.get(baseDp + dp);
        for (node.searchIndex = 0; node.searchIndex < node.branchOcps.size(); ++node.searchIndex) {
            OcpSize size = node.branchOcps.get(node.searchIndex);
            boolean canPlace = true;
            for (int j = 1; j < size.ocpW; ++j) {
                if (firstCol + j >= colHs.length || colHs[firstCol + j] != colHs[firstCol]) {
                    canPlace = false;
                    break;
                }
            }
            if (canPlace) {
                size.startOcpW = firstCol;
                size.startOcpH = colHs[firstCol];
                for (int j = 0; j < size.ocpW; ++j) {
                    colHs[firstCol + j] += size.ocpH;
                }

                searchedDp = searchOcps(tree, baseDp, dp + 1, colHs);
                if (searchedDp == -1) {
                    for (int j = 0; j < size.ocpW; ++j) {
                        colHs[firstCol + j] = size.startOcpH;
                    }
                } else {
                    break;
                }
            }
        }

        return searchedDp;
    }

    @Override
    public LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public boolean canScrollVertically() {
        return true;
    }

    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        int cntChild = getChildCount();
        if (cntChild <= 0) {
            return 0;
        }

        View topView = null;
        View bottomView = null;
        for (int i = 0; i < getChildCount(); ++i) {
            View child = getChildAt(i);
            if (topView == null || getDecoratedTop(topView) > getDecoratedTop(child)) {
                topView = child;
            }

            if (bottomView == null || getDecoratedBottom(bottomView) < getDecoratedBottom(child)) {
                bottomView = child;
            } else if (getDecoratedBottom(bottomView) == getDecoratedBottom(child)) {
                Integer i1 = mViewPosMap.get(bottomView);
                Integer i2 = mViewPosMap.get(child);
                if (i1 != null && i2 != null) {
                    if (i2 > i1) {
                        bottomView = child;
                    }
                } else {
                    Log.e(TAG, "i1 = " + i1 + " i2 = " + i2 + " should not be null");
                }
            }
        }

        if (getDecoratedBottom(bottomView) - getDecoratedTop(topView) < getHeight()) {
            return 0;
        }

        int delta = dy;
        if (dy > 0) {
            // Scroll up
            Integer dataPos = mViewPosMap.get(bottomView);
            if (dataPos != null) {
                if (dataPos >= mItemCount - 1) {
                    delta = Math.min(dy, getDecoratedBottom(bottomView) - getHeight());
                }
            } else {
                Log.e(TAG, "dataPos is null in scrollVerticallyBy dy: " + dy);
            }
        } else {
            // Scroll down
            Integer dataPos = mViewPosMap.get(topView);
            if (dataPos != null) {
                if (dataPos <= 0) {
                    int topLine = getDecoratedTop(topView);
                    int topViewBottom = getDecoratedBottom(topView);
                    if (mAdapter.getItemType(dataPos) == TYPE_STICKY_HEADER) {
                        for (int i = 0; i < cntChild; ++i) {
                            View v = getChildAt(i);
                            if (v != topView) {
                                topLine = Math.min(topViewBottom - getDecoratedTop(v), topLine);
                            }
                        }
                    }
                    delta = Math.max(dy, topLine);
                }
            } else {
                Log.e(TAG, "dataPos is null in scrollVerticallyBy dy: " + dy);
            }
        }

        offsetChildrenVertical(-delta);
        mTopOffSet -= delta;

        if (delta != 0) {
            fill(recycler);
            if (mTopOffSet > 0) {
                offsetChildrenVertical(-mTopOffSet);
                mTopOffSet = 0;
                fill(recycler);
            }
        }

        return delta;
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        boolean dataSetChanged = state.didStructureChange();

        mUnitWidth = getWidth() / MAX_DIV_COL;

        int count = getItemCount();
        if (count <= 0) {
            detachAndScrapAttachedViews(recycler);
            return;
        }

        if (dataSetChanged) {
            detachAndScrapAttachedViews(recycler);
            // dataSetChanged的时候检查是否要停留在当前位置，防止列表内容跳过去
            if (mNeedKeepCurrentPos) {
                mNeedKeepCurrentPos = false;
                final int newCount = count - mItemCount;
                if (newCount > 0 && mFirstVisiblePosition != 0 && mTopOffSet != 0) {
                    mFirstVisiblePosition += newCount;
                }
            }
        }
        mItemCount = count;

        fill(recycler);

        if (mRequestPosition != REQUEST_POSITION_NONE) {
            if (mAdapter.getItemType(mRequestPosition) != TYPE_STICKY_HEADER) {
                int headerIndex = mAdapter.getHeaderIndex(mRequestPosition);
                int offSet = 0;
                if (headerIndex >= 0 && headerIndex < mItemCount) {
                    View header = getChildByDataPos(headerIndex);
                    offSet = header == null ? 0 : getDecoratedBottom(header);
                }
                View requestView = getChildByDataPos(mRequestPosition);
                if (requestView != null) {
                    offSet = offSet - getDecoratedTop(requestView);
                    if (offSet != mTopOffSet) {
                        mTopOffSet = offSet;
                        mRequestPosition = REQUEST_POSITION_NONE;
                        fill(recycler);
                    }
                }
            }
            int bottom = getChildrendBottom();
            if (bottom < getHeight()) {
                int offSet = getHeight() - bottom + mTopOffSet;
                if (mTopOffSet != offSet) {
                    mTopOffSet = offSet;
                    fill(recycler);
                }
            }
        }

        if (dataSetChanged) {
            if (mDataChangeListener != null) {
                mDataChangeListener.onDataChanged(mAdapter);
            }
        }
    }

    private View getChildByDataPos(int dataPos) {
        View child = null;
        for (int i = 0; i < getChildCount(); ++i) {
            View v = getChildAt(i);
            Integer pos = mViewPosMap.get(v);
            if (pos != null && pos == dataPos) {
                child = v;
                break;
            }
        }
        return child;
    }

    @Override
    public void scrollToPosition(int position) {
        if (position < 0) {
            position = 0;
        }

        if (position >= mItemCount) {
            position = mItemCount - 1;
        }
        if (getChildrendBottom() >= getHeight()) {
            mRequestPosition = position;
            int[] top = new int[MAX_DIV_COL];
            for (int i = 0; i < top.length; ++i) {
                top[i] = -1;
            }

            if (isPicItem(position)) {
                while (true) {
                    int groupId = mAdapter.getPicGroupId(position);
                    OcpList ocpList = mOcpItemsMap.get(groupId);
                    if (ocpList == null) {
                        break;
                    }

                    OcpSize ocp = ocpList.ocps.get(position - ocpList.firstIndex);
                    for (int i = 0; i < ocp.ocpW; ++i) {
                        top[ocp.startOcpW + i] = ocp.startOcpH;
                    }

                    boolean isTopEnd = true;
                    for (int i = 1; i < top.length; ++i) {
                        if (top[i] != top[i - 1]) {
                            isTopEnd = false;
                            break;
                        }
                    }

                    if (isTopEnd || position <= ocpList.firstIndex) {
                        break;
                    }
                    --position;
                }
            }
            mFirstVisiblePosition = position;
            mTopOffSet = 0;

            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    requestLayout();
                }
            }, 10);
        }
    }

    private int getChildrendBottom() {
        int bottom = 0;
        for (int i = 0; i < getChildCount(); ++i) {
            View child = getChildAt(i);
            int b = getDecoratedBottom(child);
            if (bottom < b) {
                bottom = b;
            }
        }
        return bottom;
    }

    private void fill(RecyclerView.Recycler recycler) {
        mViewCache.clear();

        int cntChild = getChildCount();
        for (int i = cntChild - 1; i >= 0; --i) {
            View child = getChildAt(i);
            Integer dataPos = mViewPosMap.get(child);
            if (dataPos == null) {
                Log.e(TAG, "Error occur! Could not find the view's position");
            }
            mViewCache.put(dataPos, child);
            detachView(child);
        }
        mViewPosMap.clear();
        mHeaderCache.clear();

        fillToBottom(recycler);
        fillToTop(recycler);
        fillHeader();

        mHeaderCache.clear();

        for (int i = 0; i < mViewCache.size(); ++i) {
            recycler.recycleView(mViewCache.valueAt(i));
        }
    }

    private void fillHeader() {
        for (int i = 0; i < mHeaderCache.size(); ++i) {
            int pos = mHeaderCache.keyAt(i);
            HeaderCache cache = mHeaderCache.get(pos);

            int hl = cache.bound.left;
            int hr = cache.bound.right;
            int ht = cache.bound.top;
            int hb = cache.bound.bottom;

            if (cache.isFromCache) {
                attachView(cache.header);
                mViewCache.remove(pos);
            } else {
                addView(cache.header);
            }

            layoutDecorated(cache.header, hl, ht, hr, hb);
            mViewPosMap.put(cache.header, pos);
        }

    }

    private void fillToTop(RecyclerView.Recycler recycler) {
        if (mTopOffSet <= 0) {
            return;
        }

        int dataPos = mFirstVisiblePosition - 1;
        int baseOcpH = -1;
        int picItemBottomBaseLine = mTopOffSet;
        int top = picItemBottomBaseLine;
        Integer picGroupId = null;
        int[] picItemTops = new int[MAX_DIV_COL];
        OcpList ocpList = null;

        while (dataPos >= 0) {
            View v = mViewCache.get(dataPos);
            int l;
            int t;
            int r;
            int b;
            boolean isFromCache = v != null;

            int type = mAdapter.getItemType(dataPos);
            boolean isTopEnd = true;
            if (type == TYPE_PICITEM) {
                if (picGroupId == null || mAdapter.getPicGroupId(dataPos) != picGroupId) {
                    picGroupId = mAdapter.getPicGroupId(dataPos);
                    ocpList = mOcpItemsMap.get(picGroupId);
                    int firstIndex = ocpList.firstIndex;
                    baseOcpH = ocpList.ocps.get(dataPos - firstIndex).startOcpH + ocpList.ocps.get(dataPos - firstIndex).ocpH;
                    picItemBottomBaseLine = top;
                }
                OcpSize ocp = ocpList.ocps.get(dataPos - ocpList.firstIndex);
                l = ocpList.ocps.get(dataPos - ocpList.firstIndex).startOcpW * mUnitWidth;
                b = picItemBottomBaseLine + ((ocp.startOcpH + ocp.ocpH) - baseOcpH) * mUnitWidth;
                r = l + ocp.ocpW * mUnitWidth;
                t = b - ocp.ocpH * mUnitWidth;

                top = Math.min(top, t);
                for (int i = 0; i < ocp.ocpW; ++i) {
                    picItemTops[ocp.startOcpW + i] = t;
                }
                for (int i = 1; i < picItemTops.length; ++i) {
                    if (picItemTops[i] != picItemTops[i - 1]) {
                        isTopEnd = false;
                        break;
                    }
                }
            } else if (type == TYPE_STICKY_HEADER) {
                if (picGroupId != null) {
                    picGroupId = null;
                }
                l = 0;
                b = top;
                if (v == null) {
                    v = recycler.getViewForPosition(dataPos);
                    measureChild(v, 0, 0);
                    r = l + v.getMeasuredWidth();
                    t = b - v.getMeasuredHeight();
                } else {
                    r = l + v.getMeasuredWidth();
                    t = b - v.getMeasuredHeight();
                }
                top = t;
                baseOcpH = -1;
                HeaderCache cache = mHeaderCache.get(dataPos);
                if (cache == null) {
                    cache = new HeaderCache();
                    cache.header = v;
                    cache.bound = new Rect(l, t, r, b);
                    cache.isFromCache = isFromCache;
                    mHeaderCache.put(dataPos, cache);
                }
            } else {
                if (picGroupId != null) {
                    picGroupId = null;
                }

                l = 0;
                b = top;
                if (v == null) {
                    v = recycler.getViewForPosition(dataPos);
                    measureChild(v, 0, 0);
                    r = v.getMeasuredWidth();
                    t = b - v.getMeasuredHeight();
                } else {
                    r = l + v.getMeasuredWidth();
                    t = b - v.getMeasuredHeight();
                }
                top = t;
                baseOcpH = -1;
            }

            int headerPos = mAdapter.getHeaderIndex(dataPos);
            if (headerPos >= 0 && headerPos < mItemCount) {
                if (mAdapter.getItemType(headerPos) == TYPE_STICKY_HEADER) {
                    if (mHeaderCache.get(headerPos) == null) {
                        HeaderCache cache = new HeaderCache();
                        cache.header = mViewCache.get(headerPos);
                        cache.isFromCache = true;
                        if (cache.header == null) {
                            cache.header = recycler.getViewForPosition(headerPos);
                            cache.isFromCache = false;
                            measureChild(cache.header, 0, 0);
                        }
                        int ht = Math.min(b - cache.header.getMeasuredHeight(), 0);
                        int hl = 0;
                        int hr = cache.header.getMeasuredWidth();
                        int hb = ht + cache.header.getMeasuredHeight();
                        cache.bound = new Rect(hl, ht, hr, hb);
                        mHeaderCache.put(headerPos, cache);
                    }
                } else {
                    throw new RuntimeException("Invalid header index dataPos: " + dataPos + " headerPos: " + headerPos);
                }
            }

            if (b > 0 && t < getHeight()) {
                if (isFromCache) {
                    if (type != TYPE_STICKY_HEADER) {
                        attachView(v);
                        mViewCache.remove(dataPos);
                    }
                } else {
                    if (type != TYPE_STICKY_HEADER) {
                        if (v == null) {
                            v = recycler.getViewForPosition(dataPos);
                            addView(v);
                            measureChildDirectly(v, r - l, b - t);
                        } else {
                            addView(v);
                        }
                    }
                }
                layoutDecorated(v, l, t, r, b);
                mViewPosMap.put(v, dataPos);
            }

            if (isTopEnd) {
                mFirstVisiblePosition = dataPos;
                mTopOffSet = top;
                if (top <= 0) {
                    break;
                }
            }

            --dataPos;
        }
    }

    private void fillToBottom(RecyclerView.Recycler recycler) {
        int dataPos = mFirstVisiblePosition;
        int baseOcpH = -1;
        int picItemBaseLine = mTopOffSet;
        int bottom = picItemBaseLine;
        Integer picGroupId = null;
        OcpList ocpList = null;
        int lastHeaderPos = -1;

        int[] picItemBottoms = new int[MAX_DIV_COL];
        for (int i = 0; i < picItemBottoms.length; ++i) {
            picItemBottoms[i] = Integer.MIN_VALUE;
        }

        while (dataPos < mItemCount) {
            View v = mViewCache.get(dataPos);
            int l;
            int t;
            int r;
            int b;
            boolean isFromCache = v != null;

            int type = mAdapter.getItemType(dataPos);
            boolean isBottomEnd = true;
            if (type == TYPE_PICITEM) {
                if (picGroupId == null || mAdapter.getPicGroupId(dataPos) != picGroupId) {
                    picGroupId = mAdapter.getPicGroupId(dataPos);
                    ocpList = mOcpItemsMap.get(picGroupId);
                    int firstIndex = ocpList.firstIndex;
                    baseOcpH = ocpList.ocps.get(dataPos - firstIndex).startOcpH;
                    picItemBaseLine = bottom;
                }
                OcpSize ocp = ocpList.ocps.get(dataPos - ocpList.firstIndex);
                l = ocp.startOcpW * mUnitWidth;
                t = picItemBaseLine + (ocp.startOcpH - baseOcpH) * mUnitWidth;
                r = l + ocpList.ocps.get(dataPos - ocpList.firstIndex).ocpW * mUnitWidth;
                b = t + ocpList.ocps.get(dataPos - ocpList.firstIndex).ocpH * mUnitWidth;

                bottom = Math.max(bottom, b);
                for (int i = 0; i < ocp.ocpW; ++i) {
                    picItemBottoms[ocp.startOcpW + i] = b;
                }
                for (int i = 1; i < picItemBottoms.length; ++i) {
                    if (picItemBottoms[i] != picItemBottoms[i - 1]) {
                        isBottomEnd = false;
                        break;
                    }
                }
            } else if (type == TYPE_STICKY_HEADER) {
                if (picGroupId != null) {
                    picGroupId = null;
                }
                l = 0;
                t = bottom;
                if (v == null) {
                    v = recycler.getViewForPosition(dataPos);
                    measureChild(v, 0, 0);
                    r = v.getMeasuredWidth();
                    b = bottom + v.getMeasuredHeight();
                } else {
                    r = l + v.getMeasuredWidth();
                    b = t + v.getMeasuredHeight();
                }
                bottom = b;
                baseOcpH = -1;

                HeaderCache cache = new HeaderCache();
                cache.header = v;
                int ht = Math.max(0, t);
                int hb = ht + (b - t);
                cache.bound = new Rect(l, ht, r, hb);
                cache.isFromCache = isFromCache;
                mHeaderCache.put(dataPos, cache);
            } else {
                if (picGroupId != null) {
                    picGroupId = null;
                }
                l = 0;
                t = bottom;
                if (v == null) {
                    v = recycler.getViewForPosition(dataPos);
                    measureChild(v, 0, 0);
                    r = v.getMeasuredWidth();
                    b = bottom + v.getMeasuredHeight();
                } else {
                    r = l + v.getMeasuredWidth();
                    b = t + v.getMeasuredHeight();
                }
                bottom = b;
                baseOcpH = -1;
            }

            int headerPos = mAdapter.getHeaderIndex(dataPos);
            if (headerPos != lastHeaderPos) {
                HeaderCache cache = mHeaderCache.get(lastHeaderPos);
                if (cache != null) {
                    int hb = Math.min(t, Math.max(cache.bound.bottom, cache.bound.height()));
                    int hl = cache.bound.left;
                    int hr = cache.bound.right;
                    int ht = hb - cache.bound.height();
                    if (hb >= 0) {
                        cache.bound.left = hl;
                        cache.bound.top = ht;
                        cache.bound.right = hr;
                        cache.bound.bottom = hb;
                    } else {
                        mHeaderCache.remove(lastHeaderPos);
                    }
                }
                lastHeaderPos = -1;
            }
            if (headerPos >= 0 && headerPos < mItemCount) {
                if (mAdapter.getItemType(headerPos) == TYPE_STICKY_HEADER) {
                    lastHeaderPos = headerPos;
                    if (mHeaderCache.get(headerPos) == null) {
                        HeaderCache cache = new HeaderCache();
                        cache.header = mViewCache.get(headerPos);
                        cache.isFromCache = true;
                        if (cache.header == null) {
                            cache.header = recycler.getViewForPosition(headerPos);
                            cache.isFromCache = false;
                            measureChild(cache.header, 0, 0);
                        }
                        cache.bound = new Rect(0, 0, cache.header.getMeasuredWidth(), cache.header.getMeasuredHeight());
                        mHeaderCache.put(headerPos, cache);
                    }
                } else {
                    throw new RuntimeException("Invalid header index dataPos: " + dataPos + " headerPos: " + headerPos);
                }
            }

            if (b > 0 && t < getHeight() || dataPos <= mRequestPosition) {
                if (isFromCache) {
                    if (type != TYPE_STICKY_HEADER) {
                        attachView(v);
                        mViewCache.remove(dataPos);
                    }
                } else {
                    if (type != TYPE_STICKY_HEADER) {
                        if (v == null) {
                            v = recycler.getViewForPosition(dataPos);
                            addView(v);
                            measureChildDirectly(v, r - l, b - t);
                        } else {
                            addView(v);
                        }
                    }
                }
                layoutDecorated(v, l, t, r, b);
                mViewPosMap.put(v, dataPos);
            } else {
                if (isBottomEnd) {
                    if (bottom <= 0) {
                        mFirstVisiblePosition = dataPos + 1;
                        mTopOffSet = b;
                    }
                }
            }

            if (dataPos > mRequestPosition && isBottomEnd && bottom >= getHeight()) {
                break;
            }

            ++dataPos;
        }
    }

    public OcpSize getPicItemOcp(int pos) {
        if (mAdapter.getItemType(pos) != TYPE_PICITEM) {
            return null;
        }

        int groupId = mAdapter.getPicGroupId(pos);
        OcpList ocpList = mOcpItemsMap.get(groupId);
        return ocpList == null ? null : ocpList.ocps.get(pos - ocpList.firstIndex);
    }

    public int getMaxDivCol() {
        return MAX_DIV_COL;
    }

    public boolean isPicItem(int pos) {
        return mAdapter.getItemType(pos) == TYPE_PICITEM;
    }

    public boolean isPicItemJustifyLeft(int pos) {
        OcpSize ocp = getPicItemOcp(pos);
        return ocp == null ? true : ocp.startOcpW == 0;
    }

    public boolean isPicItemJustifyRight(int pos) {
        OcpSize ocp = getPicItemOcp(pos);
        return ocp == null ? true : ocp.startOcpW + ocp.ocpW == getMaxDivCol();
    }

    public boolean isPicItemJustifyTop(int pos) {
        OcpSize ocp = getPicItemOcp(pos);
        return ocp == null ? true : ocp.startOcpH == 0;
    }

    public boolean isPicItemJustifyBottom(int pos) {
        OcpSize ocp = getPicItemOcp(pos);

        if (ocp != null) {
            int groupId = mAdapter.getPicGroupId(pos);
            OcpList ocpList = mOcpItemsMap.get(groupId);
            return ocpList.maxOcpH == ocp.ocpH + ocp.startOcpH;
        }
        return true;
    }

    public void measureChildDirectly(View child, int w, int h) {
        final Rect insets = new Rect();
        calculateItemDecorationsForChild(child, insets);
        int decW = insets.left + insets.right;
        int decH = insets.top + insets.bottom;

        final int widthSpec = getChildMeasureSpec(getWidth(), 0, w - decW, canScrollHorizontally());
        final int heightSpec = getChildMeasureSpec(getHeight(), 0, h - decH, canScrollVertically());
        child.measure(widthSpec, heightSpec);
    }

    public class LayoutParams extends RecyclerView.LayoutParams {
        public LayoutParams(int width, int height) {
            super(width, height);
        }
    }

    public static abstract class PicAdapter<VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {
        private PicLayoutManager mLayoutManager;

        public PicAdapter(PicLayoutManager plm) {
            mLayoutManager = plm;
            mLayoutManager.setPicAdapter(this);
        }

        public abstract int getOriWidth(int position);

        public abstract int getOriHeight(int position);

        public abstract int getPicGroupId(int position);

        public abstract int getHeaderIndex(int position);

        public abstract int getItemType(int position);

        public void relayoutPicItems() {
            mLayoutManager.relayoutPicItems();
            notifyDataSetChanged();
        }
    }

    public static class PicDecoration extends RecyclerView.ItemDecoration {
        private int mPicSpan;

        public PicDecoration(int picSpan) {
            mPicSpan = picSpan;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            PicLayoutManager lm = (PicLayoutManager) parent.getLayoutManager();
            int pos = parent.getChildLayoutPosition(view);
            if (lm.isPicItem(pos)) {
                int span = mPicSpan / 2;

                if (!lm.isPicItemJustifyLeft(pos)) {
                    outRect.left = span;
                }

                if (!lm.isPicItemJustifyRight(pos)) {
                    outRect.right = span;
                }

                if (!lm.isPicItemJustifyTop(pos)) {
                    outRect.top = span;
                }

                if (!lm.isPicItemJustifyBottom(pos)) {
                    outRect.bottom = span;
                }
            }
        }
    }

    public interface DataChangeListener {
        void onDataChanged(PicAdapter adapter);
    }
}
