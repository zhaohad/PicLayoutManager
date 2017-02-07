package hw.demo.piclayoutmanager;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private RecyclerView mRcvPic;
    private PicAdapter mAdapter;
    private PicLayoutManager mPicLayoutManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initUI();
    }

    private void initUI() {
        mRcvPic = (RecyclerView) findViewById(R.id.rcv_pic);

        mPicLayoutManager = new PicLayoutManager();

        mAdapter = new PicAdapter(mPicLayoutManager);
        mRcvPic.setLayoutManager(mPicLayoutManager);
        mRcvPic.setAdapter(mAdapter);
        mRcvPic.addItemDecoration(new PicLayoutManager.PicDecoration(4));

        loadData();
    }

    private void loadData() {
        File dir = new File("/sdcard/DCIM/Camera/");
        ArrayList<File> files = new ArrayList<>();
        for (File f : dir.listFiles()) {
            files.add(f);
        }
        mAdapter.setData(files);
    }

    int[] widths = new int[] {5184, 5184, 2592, 1512, 640, 5184, 5184, 5184, 5184, 1512, 5184, 296, 288, 2448, 5184, 7952, 5184, 2448, 5184, 960, 5184, 3264, 1440, 3696, 2448, 1080, 640, 4608, 1840, 3840, 960, 768, 5184, 2448, 3456, 1836, 3120, 720, 5184, 725, 5184, 5184, 2448, 5184, 5184, 5184, 3872, 3840, 5184, 1080, 4032, 422, 5184, 5184, 5184, 3696, 5184, 5184, 1080, 1536, 3872, 1440, 5184, 1080, 5184, 2448, 2080, 5472, 725, 2048, 5184, 5184, 5721, 1440, 1776, 720, 759, 640, 1080, 720, 1440, 6000, 5184, 754, 1280, 3264, 4160, 3696, 5184, 4928, 1440, 5184, 1512, 5184, 3892, 3872, 1080, 5472, 1280, 2448, 1080, 1085, 5184, 960, 5184, 1936, 2160, 640, 5184, 4592, 3872, 720, 2448, 3696, 720, 1920, 6000, 974, 3552, 4032, 5184, 5184, 4032, 5184, 2080, 5184, 5024, 1080, 5184, 6000, 4160, 7952, 1920, 960, 4608, 1280, 4608, 5184, 3696, 725, 1024, 3696, 3264, 4032, 640, 1423, 3696, 1512, 3264, 2592};
    int[] heights = new int[] {3456, 3456, 1936, 2688, 852, 3456, 3456, 3456, 3456, 2688, 3456, 298, 512, 2584, 3456, 5304, 3456, 2448, 3456, 1280, 3456, 2177, 1920, 2448, 3264, 1920, 640, 3072, 3264, 2160, 960, 1024, 3456, 3264, 2304, 3264, 4160, 1280, 3456, 1280, 3456, 3456, 3264, 3456, 3456, 3456, 2592, 2160, 3456, 1920, 3024, 418, 3456, 3456, 3456, 2448, 3456, 3456, 1920, 2048, 2592, 1920, 3456, 1920, 3456, 2448, 1560, 3648, 1280, 2048, 3456, 3456, 3811, 1920, 1205, 1280, 1024, 640, 1920, 1280, 1920, 4000, 3456, 928, 720, 2448, 3120, 2448, 3456, 3264, 1920, 3456, 2688, 3456, 2595, 2592, 1920, 3648, 950, 3264, 1920, 1920, 3456, 960, 3456, 2592, 3840, 852, 3456, 3056, 2592, 1280, 3264, 2448, 1280, 1080, 4000, 855, 2000, 3024, 3456, 3456, 3024, 3456, 1560, 3456, 2923, 1920, 3456, 4000, 3120, 5304, 1440, 1280, 3072, 854, 3072, 3456, 2448, 1280, 680, 2448, 1840, 3024, 480, 1920, 2448, 2688, 1840, 1936};
    int[] colors = new int[] {0xfffe4365, 0xfffc9d9a, 0xfff9cdad, 0xffc8c8a9, 0xff83af9b, 0xffa0bf7c, 0xff1e293d, 0xff71a5af, 0xff1c7887, 0xffa0bf7c, 0xff26bcd5, 0xff407434, 0xffffe9dc, 0xff65934a, 0xffa7dce0};

    private class PicAdapter extends PicLayoutManager.PicAdapter<ViewHolder> {
        private ArrayList<ListItem> mItems;
        public PicAdapter(PicLayoutManager plm) {
            super(plm);
        }

        public void setData(ArrayList<File> files) {
            ArrayList<ListItem> items = new ArrayList<>();
            /*String width = "{";
            String height = "{";
            int i = 0;
            for (File f : files) {
                if (++i > 150) {
                    break;
                }
                BitmapFactory.Options opt = new BitmapFactory.Options();
                opt.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getAbsolutePath(), opt);
                width += String.valueOf(opt.outWidth) + ", ";
                height += String.valueOf(opt.outHeight) + ", ";
            }
            width += "}";
            height += "}";
            Log.e("hanwei", "width = " + width + " height = " + height);*/

            ListItem item = new NormalItem();
            items.add(item);
            item = new HeaderItem();
            items.add(item);
            int headerIndex = items.size() - 1;
            int group = 1;
            for (int i = 0; i < widths.length; ++i) {
                item = new PicItem(widths[i], heights[i], group, headerIndex, colors[i % colors.length]);
                items.add(item);
            }
            mItems = items;

            relayoutPicItems();
        }

        @Override
        public int getOriWidth(int position) {
            return mItems == null ? 0 : mItems.get(position).width;
        }

        @Override
        public int getOriHeight(int position) {
            return mItems == null ? 0 : mItems.get(position).height;
        }

        @Override
        public int getPicGroupId(int position) {
            return mItems == null ? -1 : mItems.get(position).group;
        }

        @Override
        public int getHeaderIndex(int position) {
            return mItems == null ? -1 : mItems.get(position).headerIndex;
        }

        @Override
        public int getItemType(int position) {
            int itemType = -1;
            if (mItems != null) {
                int type = mItems.get(position).type;
                switch (type) {
                    case ListItem.TYPE_HEADER:
                        itemType = PicLayoutManager.TYPE_STICKY_HEADER;
                        break;
                    case ListItem.TYPE_PIC:
                        itemType = PicLayoutManager.TYPE_PICITEM;
                        break;
                    case ListItem.TYPE_NORMAL:
                        itemType = PicLayoutManager.TYPE_CUSTOM_START + type;
                        break;
                }
            }
            return itemType;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ViewHolder vh = null;
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            int layoutRes = -1;
            switch (viewType) {
                case ListItem.TYPE_HEADER:
                    layoutRes = R.layout.item_header;
                    break;
                case ListItem.TYPE_PIC:
                    layoutRes = R.layout.item_pic;
                    break;
                case ListItem.TYPE_NORMAL:
                    layoutRes = R.layout.item_normal;
                    break;
            }

            if (layoutRes > 0) {
                vh = new ViewHolder(inflater.inflate(layoutRes, parent, false));
            }

            return vh;
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            ListItem item = mItems.get(position);
            switch (item.type) {
                case ListItem.TYPE_HEADER:
                    holder.txvText.setText("Header: " + position);
                    break;
                case ListItem.TYPE_PIC:
                    holder.txvText.setText("PicItem: " + position);
                    holder.lytBg.setBackgroundColor(((PicItem) item).color);
                    break;
                case ListItem.TYPE_NORMAL:
                    holder.txvText.setText("NormalItem: " + position);
                    break;
            }
        }

        @Override
        public int getItemCount() {
            return mItems == null ? 0 : mItems.size();
        }

        @Override
        public int getItemViewType(int position) {
            return mItems == null ? 0 : mItems.get(position).type;
        }
    }

    private abstract static class ListItem {
        static final int TYPE_HEADER = 0;
        static final int TYPE_PIC = 1;
        static final int TYPE_NORMAL = 2;

        ListItem(int type) {
            this.type = type;
            group = -1;
            headerIndex = -1;
        }

        int type;
        int width;
        int height;
        int group;
        int headerIndex;
    }

    private class HeaderItem extends ListItem {
        HeaderItem() {
            super(TYPE_HEADER);
        }
    }

    private class PicItem extends ListItem {
        int color;
        PicItem(int width, int height, int group, int headerIndex, int color) {
            super(TYPE_PIC);
            this.width = width;
            this.height = height;
            this.group = group;
            this.headerIndex = headerIndex;
            this.color = color;
        }
    }

    private class NormalItem extends ListItem {
        NormalItem() {
            super(TYPE_NORMAL);
        }
    }

    private class ViewHolder extends RecyclerView.ViewHolder {
        View lytBg;
        TextView txvText;
        public ViewHolder(View itemView) {
            super(itemView);

            lytBg = itemView;
            txvText = (TextView) itemView.findViewById(R.id.txv_text);
        }
    }
}
