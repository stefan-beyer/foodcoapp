package org.baobab.foodcoapp;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.baobab.foodcoapp.fragments.TransactionFragment;
import org.baobab.foodcoapp.util.Nfc;
import org.baobab.foodcoapp.util.Scale;
import org.baobab.foodcoapp.view.StretchableGrid;
import org.baobab.foodcoapp.view.TransactionView;

import java.util.HashSet;

public class CheckoutActivity extends AppCompatActivity
        implements LoaderManager.LoaderCallbacks<Cursor>,
        View.OnClickListener, Scale.ScaleListener, View.OnKeyListener, View.OnLongClickListener, View.OnTouchListener {

    TransactionFragment transactionFragment;
    TransactionView transactionView;
    ViewPager pager;
    Scale scale;
    float weight = -1;
    boolean editable;
    public long time;
    public String comment;
    float currency = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        setContentView(layout());
        if (savedInstanceState == null) {
            onNewIntent(getIntent());
        }
        getSupportLoaderManager().initLoader(23, null, this);
        findViewById(R.id.scanner).setOnKeyListener(this);
        pager = (ViewPager) findViewById(R.id.pager);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
            String msg = Nfc.readTag(intent);
            Toast.makeText(this, "TAG " + msg, Toast.LENGTH_LONG).show();
            handleBarcode(msg.split(": ")[1]);
        }
        if (getIntent().getData() == null) {
            resetTransaction();
        }
    }

    int layout() {
        return R.layout.activity_possimple;
    }

    public void resetTransaction() {
        getSupportLoaderManager().destroyLoader(42);
        Uri uri = getContentResolver().insert(Uri.parse(
                "content://org.baobab.foodcoapp/transactions"), null);
        setIntent(getIntent().setData(uri));
        getSupportActionBar().setTitle(getString(R.string.neues) +
                " " + getString(R.string.transaction) + " " + uri.getLastPathSegment());
        if (transactionFragment != null) {
            transactionFragment.enableEdit(true);
            transactionFragment.reload();
        }
        editable = true;
    }

    @Override
    public void onStart() {
        super.onStart();
//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (transactionFragment == null) {
            transactionFragment = (TransactionFragment)
                    getSupportFragmentManager().findFragmentById(R.id.transaction);
            transactionView = (TransactionView) findViewById(R.id.transaction_view);
        }
        scale = new Scale(this);
        scale.registerForUsb();
    }

    @Override
    protected void onResume() {
        super.onResume();
        transactionFragment.reload();
        Nfc.resume(this, NfcAdapter.ACTION_NDEF_DISCOVERED,
                "application/vnd.kornkammer.products");
        SettingsActivity.crashCheck(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Nfc.pause(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        scale.unregisterUsb();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        switch (id) {
            case 42:
                return new CursorLoader(this, getIntent().getData(), null, null, null, null);
            case 23:
                return new CursorLoader(this,
                        Uri.parse("content://org.baobab.foodcoapp/products"),
                        null, "_id > 5", null, "UPPER(title)");
        }
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, final Cursor data) {
        transactionFragment.enableEdit(false);
        editable = true;
        final int pages;
        if (data.getCount() > 0) {
            pages = (data.getCount() + 1) / 16 + 1;
            data.moveToFirst();
        } else {
            pages = 1;
        }
        pager.setOffscreenPageLimit(42);
        pager.setAdapter(new PagerAdapter() {
            @Override
            public int getCount() {
                return pages;
            }

            @Override
            public Object instantiateItem(ViewGroup container, int position) {
                StretchableGrid page = new StretchableGrid(CheckoutActivity.this, 4, 4);
                for (int i = 1; i <= 16; i++) {
                    int button = (int) position * 16 + i;
                    if (data.getCount() > 0 && !data.isAfterLast()) {
                        page.addView(new ProductButton(
                                CheckoutActivity.this,
                                data.getLong(3),
                                data.getString(7),
                                data.getFloat(5),
                                data.getString(6),
                                data.getString(8), button), i);
                        if (!data.isAfterLast()) {
                            data.moveToNext();
                        }
                    } else {
                        page.addView(new ProductButton(
                                CheckoutActivity.this, 0, "", 0, null, null, button), i);
                    }
                }
                container.addView(page);
                return page;

            }

            @Override
            public void destroyItem(View collection, int position, Object o) {
                View view = (View)o;
                ((ViewPager) collection).removeView(view);
                view = null;
            }

            @Override
            public boolean isViewFromObject(View view, Object object) {
                return view == object;
            }
        });
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    @Override
    public void onWeight(float kilos) {
        if (weight != kilos) {
            transactionView.setWeight(kilos);
            transactionFragment.load();
            weight = kilos;
        }
    }

    @Override
    public void onClick(View v) {
        if (((ProductButton) v).empty) {
            return;
        }
        ProductButton b = (ProductButton) v;
        addProductToTransaction(b.id, b.title, -weight, b.price, b.unit, b.img);
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            EditText scan = (EditText) findViewById(R.id.scanner);
            String ean = scan.getText().toString();
            if (ean.length() > 0) {
                handleBarcode(ean);
                scan.setText("");
                scan.requestFocus();
                return true;
            }
            scan.requestFocus();
        }
        return false;
    }

    void handleBarcode(String ean) {
        Cursor p = getContentResolver().query(Uri.parse(
                        "content://org.baobab.foodcoapp/products"),
                null, "ean IS ?", new String[] { ean }, null);
        if (p.getCount() > 0) {
            p.moveToFirst();
            String title = p.getString(1).replace("AND ", "").replace("Adechser ", "")
                    .replace("Bioland ", "").replace("Demeter ", "");
            addProductToTransaction(p.getLong(0), title, 1, p.getFloat(2), p.getString(3), p.getString(4));
//            Toast.makeText(this, "Found " + p.getString(1) + " " + p.getFloat(2), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Product NOT found! \n" + ean, Toast.LENGTH_LONG).show();
        }
    }

    void addProductToTransaction(long id, String title, float quantity, float price, String unit, String img) {
        addProductToTransaction(id, title, quantity, price, unit, img, "lager");
    }
    void addProductToTransaction(long id, String title, float quantity, float price, String unit, String img, String account) {
        if (!editable) return;
        ContentValues cv = new ContentValues();
        cv.put("account_guid", account);
        cv.put("product_id", id);
        cv.put("title", title);
        if (quantity != 1 && quantity != 0 || !account.equals("lager")) {
            if (unit.equals("Kilo") || weight == -1) {
                cv.put("quantity", quantity);
            }
        }
        cv.put("price", price);
        cv.put("unit", unit);
        cv.put("img", img);
        getContentResolver().insert(getIntent().getData().buildUpon()
                .appendEncodedPath("products").build(), cv);
    }

    @Override
    public void onBackPressed() {
        if (goToDashboard()) {
            startActivity(new Intent(this, DashboardActivity.class));
        } else {
            super.onBackPressed();
        }
    }

    boolean goToDashboard() {
        return getSupportFragmentManager().getBackStackEntryCount() == 0;
    }

    @Override
    public boolean onLongClick(View v) {
        return false;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return false;
    }


    class ProductButton extends FrameLayout {

        boolean empty;
        int button;
        long id;
        String title;
        float price;
        String unit;
        String img;

        public ProductButton(Context context, long id, String title, float price, String unit, String img, int button) {
            super(context);
            this.id = id;
            this.button = button;
            this.title = title;
            this.price = price;
            this.unit = unit;
            this.img = img;
            View.inflate(getContext(), R.layout.view_product_button, this);
            ((TextView) findViewById(R.id.title)).setText(title);
            if (img != null) {
                ((ImageView) findViewById(R.id.image))
                        .setImageURI(Uri.parse(img));
            }
            if (id == 0) {
                empty = true;
            }
            setBackgroundResource(R.drawable.background_product_button);
            setClickable(true);
            setOnClickListener(CheckoutActivity.this);
            setOnLongClickListener(CheckoutActivity.this);
            setOnTouchListener(CheckoutActivity.this);
        }
    }
}