package com.WexWorks.XCSkiSchool;

import com.WexWorks.XCSkiSchool.BillingService.RequestPurchase;
import com.WexWorks.XCSkiSchool.BillingService.RestoreTransactions;
import com.WexWorks.XCSkiSchool.Consts.PurchaseState;
import com.WexWorks.XCSkiSchool.Consts.ResponseCode;
import com.amazon.aws.tvmclient.Response;

import android.annotation.SuppressLint;
import android.app.ListActivity;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StrictMode;
import android.net.Uri;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.List;
import java.io.File;

class Chapter {
  String name;
  String file;
  String path;
  String sku;
  boolean cached;
  boolean owned;
  boolean downloading;
  int downloadPct;
  DownloadChapter dl;
}

public class XCSkiSchoolActivity extends ListActivity {
  private static final String TAG = "XCSkiSchool";
  private static final String DB_INITIALIZED = "db_initialized";
  private static final String SKU_SUFFIX = "_003";
  private static final int FREE_CHAPTER_COUNT = 3;
  private static final int MIN_BUY_ALL = 1;
  private static final int BUY_ALL_DISCOUNT = 13;
  public Chapter[] mChapter;
  private boolean mBillingSupported;
  private BillingService mBillingService;
  private Handler mHandler;
  private XCSkiSchoolPurchaseObserver mPurchaseObserver;
  private String mBuyAllSKU;
  private boolean[] mBoughtAllOwned;

  private static String buyAllSKU(int dollars) {
    if (dollars < 0)
      dollars = 0;
    return "buyall_" + dollars + "99" + SKU_SUFFIX;
  }
  
  private static void di(String msg) {
    if (Consts.DEBUG)
      Log.i(TAG, msg);
  }
  
  private class XCSkiSchoolPurchaseObserver extends PurchaseObserver {
    public XCSkiSchoolPurchaseObserver(Handler handler) {
      super(XCSkiSchoolActivity.this, handler);
    }

    @Override
    public void onBillingSupported(boolean supported) {
      mBillingSupported = supported;
      di("Billing supported: " + mBillingSupported);
      if (supported)
        restoreDatabase();
    }

    @Override
    public void onPurchaseStateChange(PurchaseState purchaseState,
        String itemId, int quantity, long purchaseTime, String developerPayload) {
      di("onPurchaseStateChange() itemId: " + itemId + " "
          + purchaseState + " (" + developerPayload + ")");
      // Revert purchase of item, since it was already marked as purchased
      if (purchaseState != PurchaseState.PURCHASED)
        reversePurchasedItem(itemId);
    }

    @Override
    public void onRequestPurchaseResponse(RequestPurchase request,
        ResponseCode responseCode) {
      di(request.mProductId + ": " + responseCode);
      if (responseCode == ResponseCode.RESULT_OK) {
        di("purchase was successfully sent to server");
        // Mark item as owned so we don't have to wait for the
        // response from the market server, which can take *minutes*
        purchaseItem(request.mProductId);
      } else if (responseCode == ResponseCode.RESULT_USER_CANCELED) {
        di("user canceled purchase");
      } else if (responseCode == ResponseCode.RESULT_DEVELOPER_ERROR) {
      } else if (responseCode == ResponseCode.RESULT_BILLING_UNAVAILABLE) {
      } else if (responseCode == ResponseCode.RESULT_ITEM_UNAVAILABLE) {
      } else if (responseCode == ResponseCode.RESULT_SERVICE_UNAVAILABLE) {
      } else if (responseCode == ResponseCode.RESULT_USER_CANCELED) {
      } else {
        di("purchase failed");
      }
    }

    @Override
    public void onRestoreTransactionsResponse(RestoreTransactions request,
        ResponseCode responseCode) {
      if (responseCode == ResponseCode.RESULT_OK) {
        di("completed RestoreTransactions request");
        // Only update database once on new install.
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putBoolean(DB_INITIALIZED, true);
        edit.commit();
      } else {
        Log.e(TAG, "RestoreTransactions error: " + responseCode);
      }
    }
  }

  private class ChapterAdapter extends ArrayAdapter<Chapter> {
    public ChapterAdapter() {
      // Should reference a TextView?
      super(XCSkiSchoolActivity.this, R.layout.rowlayout, mChapter);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      LayoutInflater inflater = (LayoutInflater)getSystemService(
          Context.LAYOUT_INFLATER_SERVICE);
      View rowView;
      if (mChapter[position].downloading) {
        rowView = inflater.inflate(R.layout.dllayout, parent, false);
        ProgressBar progress = (ProgressBar) rowView
            .findViewById(R.id.progress);
        if (progress != null) {
          boolean indeterminate = mChapter[position].downloadPct == 0;
          progress.setIndeterminate(indeterminate);
          if (mChapter[position].downloadPct > 0)
            progress.setProgress(mChapter[position].downloadPct);
        }
      } else {
        rowView = inflater.inflate(R.layout.rowlayout, parent, false);
        ImageView imageView = (ImageView) rowView.findViewById(R.id.icon);
        if (imageView != null) {
          if (mChapter[position].cached && mChapter[position].owned)
            imageView.setImageResource(R.drawable.ic_play);
          else if (mChapter[position].owned)
            imageView.setImageResource(R.drawable.ic_download);
          else
            imageView.setImageResource(R.drawable.ic_purchase);
        }
      }
      TextView textView = (TextView) rowView.findViewById(R.id.label);
      if (textView != null)
        textView.setText(mChapter[position].name);
      return rowView;
    }
  }

  @SuppressLint("NewApi")
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Hack to allow HTTP requests on the main thread.
    // Now deprecated, but we'd need to change a bunch of
    // logic in the AWS communication to defer processing.
    if (android.os.Build.VERSION.SDK_INT > 9) {
      StrictMode.ThreadPolicy policy = 
              new StrictMode.ThreadPolicy.Builder().permitAll().build();
      StrictMode.setThreadPolicy(policy);
    } 
        
    String state = Environment.getExternalStorageState();
    boolean externalStorageAvailable = false;
    boolean externalStorageWriteable = false;
    if (Environment.MEDIA_MOUNTED.equals(state)) {
      externalStorageAvailable = externalStorageWriteable = true;
    } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
      externalStorageAvailable = true;
    }
    if (!externalStorageAvailable) {
      Toast.makeText(this, "Cannot access external storage -- quitting",
          Toast.LENGTH_LONG).show();
      finish();
    } else if (!externalStorageWriteable) {
      Toast.makeText(this, "External storage is read only -- quitting",
          Toast.LENGTH_LONG).show();
      finish();
    }

    SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);

    File externalDir = Environment.getExternalStorageDirectory();
    File appDir = new File (externalDir.getAbsoluteFile() + 
        "/WexWorks/XCSkiSchool/");
    if (!appDir.exists())
      appDir.mkdirs();
    
    String[] chapterName = getResources().getStringArray(R.array.chapters);
    mChapter = new Chapter[chapterName.length];
    for (int i = 0; i < chapterName.length; ++i) {
      mChapter[i] = new Chapter();
      mChapter[i].name = chapterName[i];
      String token = toCamelCase(mChapter[i].name.replaceAll("\\p{Punct}+", ""));
      mChapter[i].file = token + ".m4v";
      mChapter[i].sku = token.toLowerCase() + SKU_SUFFIX;
      mChapter[i].path = appDir.getAbsolutePath() + "/" + mChapter[i].file;
      File file = new File(mChapter[i].path);
      mChapter[i].cached = file.exists();
      mChapter[i].downloading = false;
      mChapter[i].downloadPct = 0;
      mChapter[i].owned = prefs.getBoolean(mChapter[i].name,
          i < FREE_CHAPTER_COUNT);
    }

    View mainView = getLayoutInflater().inflate(R.layout.main, null);
    setContentView(mainView);

    Button buyAll = (Button) findViewById(R.id.buyAllButton);
    if (buyAll != null) {
      buyAll.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          buyAll(view);
        }
      });
    }
    updateBuyAll();

    ChapterAdapter adapter = new ChapterAdapter();
    setListAdapter(adapter);

    mHandler = new Handler();
    mPurchaseObserver = new XCSkiSchoolPurchaseObserver(mHandler);
    mBillingService = new BillingService();
    mBillingService.setContext(this);

    ResponseHandler.register(mPurchaseObserver);
    mBillingSupported = mBillingService.checkBillingSupported();
  }

  @Override
  protected void onStart() {
    super.onStart();
    ResponseHandler.register(mPurchaseObserver);
  }

  @Override
  protected void onStop() {
    super.onStop();
    ResponseHandler.unregister(mPurchaseObserver);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    mBillingService.unbind();
  }

  /**
   * Save the context of the log so simple things like rotation will not result
   * in the log being cleared.
   */
  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
  }

  /**
   * Restore the contents of the log if it has previously been saved.
   */
  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    if (savedInstanceState != null) {
    }
  }

  @Override
  protected void onListItemClick(ListView list, View view, int position, long id) {
    if (mChapter[position].cached && mChapter[position].owned) { // Play
      //Uri uri = Uri.parse(mChapter[position].path);
      Uri uri = Uri.fromFile(new File(mChapter[position].path));
      Intent intent = new Intent(Intent.ACTION_VIEW);
//      intent.setData(uri);
      intent.setDataAndType(uri, "video/*");
      try {
        startActivity(intent);
      } catch (Exception e) {
        PackageManager p = getPackageManager();
        List<ResolveInfo> a = p.queryIntentActivityOptions(null, null, intent, 0);
        for (ResolveInfo i : a) {
          String s = i.toString();
          Log.i(TAG, "Resolved: " + s);
        }
        String s = e.getMessage();
        Log.e(TAG, "Cannot find video player: " + s);
      }
    } else if (mChapter[position].owned) { // Download
      mChapter[position].dl = new DownloadChapter(this);
//      mChapter[position].dl.wipeCredentials();
      Response awsResponse = mChapter[position].dl.validateCredentials();
      if (awsResponse != null && awsResponse.requestWasSuccessful()) {
        mChapter[position].dl.execute(position);
      } else {
        Log.e(TAG, "Unable to validate AWS for Chapter downloads.");
        Toast.makeText(this, "Unable to validate download credentials",
            Toast.LENGTH_LONG);
      }
    } else { // Purchase
      if (mBillingSupported) {
        if (!mBillingService.requestPurchase(mChapter[position].sku, null)) {
          Log.e(TAG, "Billing failed, disabling");
          mBillingSupported = false;
        }
      } else {
        // Display billing not supported alert
      }
    }
  }

  static String toCamelCase(String s) {
    String[] parts = s.split("\\s");
    String camelCaseString = "";
    for (String part : parts)
      camelCaseString = camelCaseString + toProperCase(part);
    return camelCaseString;
  }

  static String toProperCase(String s) {
    return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
  }

  /**
   * If the database has not been initialized, we send a RESTORE_TRANSACTIONS
   * request to Android Market to get the list of purchased items for this user.
   * This happens if the application has just been installed or the user wiped
   * data. We do not want to do this on every startup, rather, we want to do
   * only when the database needs to be initialized.
   */
  private void restoreDatabase() {
    SharedPreferences prefs = getPreferences(MODE_PRIVATE);
    boolean initialized = prefs.getBoolean(DB_INITIALIZED, false);
    SharedPreferences.Editor edit = prefs.edit();
    edit.putBoolean(DB_INITIALIZED, true);
    edit.commit();
    if (!initialized) {
      mBillingService.restoreTransactions();
      Toast.makeText(this, "Restoring purchase transactions",
          Toast.LENGTH_LONG).show();
    }
  }

  private void updateBuyAll() {
    ViewGroup mainViewGroup = (ViewGroup) findViewById(R.id.mainListLayout);
    if (mainViewGroup != null && mainViewGroup.getChildCount() > 1) {
      int forsaleCount = 0;
      for (int j = 0; j < mChapter.length; ++j) {
        if (!mChapter[j].owned)
          forsaleCount++;
      }
      if (forsaleCount < MIN_BUY_ALL) {
        mainViewGroup.removeViewAt(1);
        mBuyAllSKU = "";
      } else {
        Button buyAllButton = (Button) mainViewGroup.getChildAt(1);
        if (buyAllButton != null) {
          int dollars = forsaleCount - BUY_ALL_DISCOUNT;
          if (dollars < 0)
            dollars = 0;
          String buttonText = "Buy All Chapters for $" + dollars + ".99";
          buyAllButton.setText(buttonText);
          mBuyAllSKU = buyAllSKU(dollars);
        }
      }
    }
  }

  public void buyAll(View view) {
    if (!mBuyAllSKU.equals("")) {
      if (!mBillingService.requestPurchase(mBuyAllSKU, null)) {
        Log.e(TAG, "Billing failed for buy-all, disabling");
        mBillingSupported = false;
      }
    }
  }

  // Iterate over all chapters applying the specified operation.
  // Used to purchase and reverse-purchase items.
  private interface ChapterOp {
    public void allChapters(String itemId);
    public void chapter(String itemId, int i);
  }
  
  private void applyItem(String itemId, ChapterOp op) {
    int i = mChapter.length - FREE_CHAPTER_COUNT - BUY_ALL_DISCOUNT;
    for (/* EMPTY */; i >= MIN_BUY_ALL; --i) {
      String sku = buyAllSKU(i);
      if (sku.equals(itemId)) {
        op.allChapters(itemId);
      }
    }
    for (i = 0; i < mChapter.length; ++i) {
      if (mChapter[i].sku.equals(itemId)) {
        op.chapter(itemId, i);
        break;
      }
    }
    if (i == mChapter.length)
      Log.e(TAG, "Cannot find item \"" + itemId + "\"");
  }
  
  private void purchaseItem(String itemId) {
    applyItem(itemId, new ChapterOp() {
      public void allChapters(String itemId) {
        di("Purchased all chapters successfully!");
        mBoughtAllOwned = new boolean[mChapter.length];
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = prefs.edit();
        for (int j = FREE_CHAPTER_COUNT; j < mChapter.length; ++j) {
          mBoughtAllOwned[j] = mChapter[j].owned;
          mChapter[j].owned = true;
          edit.putBoolean(mChapter[j].name, true);
        }
        edit.commit();
        updateBuyAll();
        getListView().invalidateViews();
      }

      public void chapter(String itemId, int i) {
        if (mChapter[i].owned) {
          Log.e(TAG, "Purchased \"" + mChapter[i].name
              + "\" which is already owned!");
        } else {
          di("Purchased \"" + mChapter[i].name + "\" successfully!");
          mChapter[i].owned = true;
          SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
          SharedPreferences.Editor edit = prefs.edit();
          edit.putBoolean(mChapter[i].name, true);
          edit.commit();
          updateBuyAll();
          getListView().invalidateViews();
        }
      }
    });
  }

  private void reversePurchasedItem(String itemId) {
    applyItem(itemId, new ChapterOp() {
      public void allChapters(String itemId) {
        if (mBoughtAllOwned.length == mChapter.length) { 
          SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
          SharedPreferences.Editor edit = prefs.edit();
          for (int j = FREE_CHAPTER_COUNT; j < mChapter.length; ++j) {
            mChapter[j].owned = mBoughtAllOwned[j];
            edit.putBoolean(mChapter[j].name, mChapter[j].owned);
            if (mChapter[j].downloading && mChapter[j].dl != null)
              mChapter[j].dl.cancel(true);
            File file = new File(mChapter[j].path);
            if (file.exists())
              file.delete();
            mChapter[j].cached = false;
          }
          edit.commit();
          updateBuyAll();
          getListView().invalidateViews();
          di("Reversed buy all purchase.");
          Toast.makeText(XCSkiSchoolActivity.this,
              "Reversed purchase of all chapters", Toast.LENGTH_LONG);
        } else {
          Log.e(TAG, "Cannot reverse buy all purchase.");
        }
        mBoughtAllOwned = null;
      }
      
      public void chapter(String itemId, int i) {
        if (!mChapter[i].owned) {
          Log.e(TAG, "Reversing purchased \"" + mChapter[i].name
              + "\" which is not owned!");
        } else {
          mChapter[i].owned = false;
          if (mChapter[i].downloading && mChapter[i].dl != null)
            mChapter[i].dl.cancel(true);
          File file = new File(mChapter[i].path);
          if (file.exists())
            file.delete();
          mChapter[i].cached = false;
          SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
          SharedPreferences.Editor edit = prefs.edit();
          edit.putBoolean(mChapter[i].name, false);
          edit.commit();
          updateBuyAll();
          getListView().invalidateViews();
          di("Reversed purchased \"" + mChapter[i].name + "\" successfully.");
          Toast.makeText(XCSkiSchoolActivity.this,
              "Reversed purchase of chapter \"" + mChapter[i].name + "\"",
              Toast.LENGTH_LONG);
        }
       }
    });
  }
  
}
