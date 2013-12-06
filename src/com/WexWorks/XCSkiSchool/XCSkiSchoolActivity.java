package com.WexWorks.XCSkiSchool;

import com.WexWorks.XCSkiSchool.util.IabHelper;
import com.WexWorks.XCSkiSchool.util.IabResult;
import com.WexWorks.XCSkiSchool.util.Inventory;
import com.WexWorks.XCSkiSchool.util.Purchase;

import com.amazon.aws.tvmclient.Response;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.net.Uri;
import android.content.Intent;
import android.content.Context;
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
  private static final String SKU_SUFFIX = "_003";
  private static final int FREE_CHAPTER_COUNT = 3;
  private static final int MIN_BUY_ALL = 1;
  private static final int BUY_ALL_DISCOUNT = 13;
  private static final int RC_REQUEST = 10001; // arbitrary code for purchase
  public Chapter[] mChapter;
  IabHelper mHelper;
  private String mBuyAllSKU;
  private boolean[] mBoughtAllOwned;

  private static String buyAllSKU(int dollars) {
    if (dollars < 0)
      dollars = 0;
    return "buyall_" + dollars + "99" + SKU_SUFFIX;
  }
  
  private static void di(String msg) {
    Log.i(TAG, msg);
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
      mChapter[i].owned = mChapter[i].cached;   // Initialize before restoring
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
    
    // In-App Billing setup
    String base64EncodedPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArAo8bqGGP0pQ+kSs2vScgOsD65jwZoeLq7BlJR3VAD2bgZ1uq5dUtf+rBI6ZC1PuSu3I2K1Ho8wFIeXnCoQVk6j5JntdtO/FgchPztQ2eXIMrnbNvoxwNezwW+zxNWvdAvzLxM0FjdNmT1VucCJnQIEz/V2O6FGcC8Y1zCJbpX94OJXLhrbzIrIInO86zsOrmLQT9VrwaaGOqbKUbwrM9/QTOwglMqQ2wM1nnC90TihjiR3uo9/vqKSoOXbq96TVsqbJEvfWpn47UPgtPIWjOZhOu7jk+MYMdZ1IMSSGVeiOlW0bviOy6KDsA8UZtdIU7JIgAs0nfcZi+SxxrQXw3QIDAQAB";
    mHelper = new IabHelper(this, base64EncodedPublicKey);
    mHelper.enableDebugLogging(false);
    
    // Start setup. This is asynchronous and the specified listener
    // will be called once setup completes.
    Log.d(TAG, "Starting setup.");
    mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
      public void onIabSetupFinished(IabResult result) {
        Log.d(TAG, "Setup finished.");

        if (!result.isSuccess()) {
          // Oh noes, there was a problem.
          alert("Problem setting up in-app billing: " + result);
          return;
        }

        // Have we been disposed of in the meantime? If so, quit.
        if (mHelper == null) return;

        // IAB is fully set up. Now, let's get an inventory of stuff we own.
        Log.d(TAG, "Setup successful. Querying inventory.");
        mHelper.queryInventoryAsync(mGotInventoryListener);
      }
    });
  }

  // We're being destroyed. It's important to dispose of the helper here!
  @Override
  public void onDestroy() {
      super.onDestroy();

      // very important:
      Log.d(TAG, "Destroying helper.");
      if (mHelper != null) {
          mHelper.dispose();
          mHelper = null;
      }
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

  boolean verifyDeveloperPayload(Purchase p) {
    String payload = p.getDeveloperPayload();

    /*
     * TODO: verify that the developer payload of the purchase is correct. It will be
     * the same one that you sent when initiating the purchase.
     *
     * WARNING: Locally generating a random string when starting a purchase and
     * verifying it here might seem like a good approach, but this will fail in the
     * case where the user purchases an item on one device and then uses your app on
     * a different device, because on the other device you will not have access to the
     * random string you originally generated.
     *
     * So a good developer payload has these characteristics:
     *
     * 1. If two different users purchase an item, the payload is different between them,
     *    so that one user's purchase can't be replayed to another user.
     *
     * 2. The payload must be such that you can verify it even when the app wasn't the
     *    one who initiated the purchase flow (so that items purchased by the user on
     *    one device work on other devices owned by the user).
     *
     * Using your own server to store and verify developer payloads across app
     * installations is recommended.
     */

    return true;
  }

  // Listener that's called when we finish querying the items and subscriptions we own
  IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
    public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
      Log.d(TAG, "Query inventory finished.");

      // Have we been disposed of in the meantime? If so, quit.
      if (mHelper == null) return;

      // Is it a failure?
      if (result.isFailure()) {
        alert("Failed to query inventory: " + result);
        return;
      }

      Log.d(TAG, "Query inventory was successful.");
      List<String> ownedSkuList = inventory.getAllOwnedSkus();
      for (String sku : ownedSkuList) {
        purchaseItem(sku);
      }
      Log.d(TAG, "Initial inventory query finished; enabling main UI.");
    }
  };

  // Callback for when a purchase is finished
  IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
    public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
      Log.d(TAG, "Purchase finished: " + result + ", purchase: " + purchase);

      // if we were disposed of in the meantime, quit.
      if (mHelper == null) return;

      if (result.isFailure()) {
        alert("Error purchasing: " + result);
        return;
      }
      if (!verifyDeveloperPayload(purchase)) {
        alert("Error purchasing. Authenticity verification failed.");
        return;
      }

      Log.d(TAG, "Purchase successful.");
      purchaseItem(purchase.getSku());
    }
  };

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
      Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);
      if (mHelper == null) return;

      // Pass on the activity result to the helper for handling
      if (!mHelper.handleActivityResult(requestCode, resultCode, data)) {
          // not handled, so handle it ourselves (here's where you'd
          // perform any handling of activity results not related to in-app
          // billing...
          super.onActivityResult(requestCode, resultCode, data);
      }
      else {
          Log.d(TAG, "onActivityResult handled by IABUtil.");
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
      /* TODO: for security, generate your payload here for verification. See the comments on
       *        verifyDeveloperPayload() for more info. Since this is a SAMPLE, we just use
       *        an empty string, but on a production app you should carefully generate this. */
      String payload = "";
      mHelper.launchPurchaseFlow(this, mChapter[position].sku, RC_REQUEST,
          mPurchaseFinishedListener, payload);
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
      /* TODO: for security, generate your payload here for verification. See the comments on
       *        verifyDeveloperPayload() for more info. Since this is a SAMPLE, we just use
       *        an empty string, but on a production app you should carefully generate this. */
      String payload = "";
      mHelper.launchPurchaseFlow(this, mBuyAllSKU, RC_REQUEST,
          mPurchaseFinishedListener, payload);
    }
  }

  // Iterate over all chapters applying the specified operation.
  // Used to purchase and reverse-purchase items.
  private interface ChapterOp {
    public void allChapters(String itemId);
    public void chapter(String itemId, int i);
  }
  
  private void applyItem(String itemId, ChapterOp op) {
    int i = 0;
    for (i = 0; i < mChapter.length; ++i) {
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
        for (int j = FREE_CHAPTER_COUNT; j < mChapter.length; ++j) {
          mBoughtAllOwned[j] = mChapter[j].owned;
          mChapter[j].owned = true;
        }
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
          for (int j = FREE_CHAPTER_COUNT; j < mChapter.length; ++j) {
            mChapter[j].owned = mBoughtAllOwned[j];
            if (mChapter[j].downloading && mChapter[j].dl != null)
              mChapter[j].dl.cancel(true);
            File file = new File(mChapter[j].path);
            if (file.exists())
              file.delete();
            mChapter[j].cached = false;
          }
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
  
  void alert(String message) {
    AlertDialog.Builder bld = new AlertDialog.Builder(this);
    bld.setMessage(message);
    bld.setNeutralButton("OK", null);
    Log.d(TAG, "Showing alert dialog: " + message);
    bld.create().show();
  }

}
