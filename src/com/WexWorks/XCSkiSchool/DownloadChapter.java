package com.WexWorks.XCSkiSchool;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ListView;
import android.widget.Toast;

import com.amazon.aws.tvmclient.AmazonTVMClient;
import com.amazon.aws.tvmclient.AmazonSharedPreferencesWrapper;
import com.amazon.aws.tvmclient.Response;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.ObjectMetadata;

public class DownloadChapter extends AsyncTask<Integer, Integer, String> {
  XCSkiSchoolActivity mXCSkiSchoolActivity;
  int mChapterIdx;
  String mFinishedText;
  static final int mReportInterval = 2;
  static AWSCredentials mCredentials;
  static AmazonS3Client mS3Client;
  static final String mBucketName = "wwnordicskischool";
  static final String TAG = "XCSkiSchool";
  
  DownloadChapter(XCSkiSchoolActivity activity) {
    mXCSkiSchoolActivity = activity;
    mChapterIdx = -1;
  }
  
  public Response validateCredentials() {
    Response ableToGetToken = Response.SUCCESSFUL;
    SharedPreferences prefs = mXCSkiSchoolActivity.getPreferences(Context.MODE_PRIVATE);
    
    if (AmazonSharedPreferencesWrapper.areCredentialsExpired(prefs)) {
        Log.i(TAG, "Credentials were expired.");
        mCredentials = null;
        mS3Client = null;
        boolean useSSL = false;
        String tvmURL = "http://xsstvm.elasticbeanstalk.com/";
        AmazonTVMClient tvm = new AmazonTVMClient(prefs,tvmURL, useSSL);
        ableToGetToken = tvm.anonymousRegister();
        if (ableToGetToken.requestWasSuccessful()) {
            ableToGetToken = tvm.getToken();            
        }
    }

    if (ableToGetToken.requestWasSuccessful() && mS3Client == null) {        
        Log.i(TAG, "Creating New Credentials.");
        mCredentials = AmazonSharedPreferencesWrapper.getCredentialsFromSharedPreferences(prefs);
        mS3Client = new AmazonS3Client(mCredentials);
    }

    return ableToGetToken;        
  }
  
  @Override
  protected String doInBackground(Integer... chapterIdx) {
    mChapterIdx = chapterIdx[0];
    if (mChapterIdx < 0 || mChapterIdx >= mXCSkiSchoolActivity.mChapter.length)
      return null;
    if (mXCSkiSchoolActivity.mChapter[mChapterIdx].downloading)
      return null;
    mXCSkiSchoolActivity.mChapter[mChapterIdx].downloading = true;
    Log.d("Downloader", "Initiating download of \""
        + mXCSkiSchoolActivity.mChapter[mChapterIdx].name + "\"");
    mFinishedText = "";
    publishProgress(0);
    int count;
    try {
      S3Object s3obj = mS3Client.getObject(mBucketName,
          mXCSkiSchoolActivity.mChapter[mChapterIdx].file);
      InputStream input = s3obj.getObjectContent();
      ObjectMetadata meta = s3obj.getObjectMetadata();
      long length = meta.getContentLength();
      OutputStream output = new FileOutputStream(
          mXCSkiSchoolActivity.mChapter[mChapterIdx].path);
      byte data[] = new byte[1024];
      int total = 0;
      int report = 0;
      while (!isCancelled() && (count = input.read(data)) != -1) {
        total += count;
        int p = (int) (100 * total / length);
        if (p > report + mReportInterval) {
          report = p;
          publishProgress(p);
        }
        output.write(data, 0, count);
      }
      output.flush();
      output.close();
      input.close();
      if (total < length) {
        mFinishedText = "Download failed for \"" +
          mXCSkiSchoolActivity.mChapter[mChapterIdx].name + "\"";
        Log.e("Downloader", mFinishedText);
        mXCSkiSchoolActivity.mChapter[mChapterIdx].cached = false;
      } else {
        mFinishedText ="Completed download of \""
          + mXCSkiSchoolActivity.mChapter[mChapterIdx].name + "\""; 
        Log.i("Downloader", mFinishedText);
        mXCSkiSchoolActivity.mChapter[mChapterIdx].cached = true;
      }
      mXCSkiSchoolActivity.mChapter[mChapterIdx].downloadPct = 0;
      mXCSkiSchoolActivity.mChapter[mChapterIdx].downloading = false;
      mXCSkiSchoolActivity.mChapter[mChapterIdx].dl = null;
      publishProgress(100);
    } catch (Exception e) {
      String s = e.getMessage();
      Log.e("WexWorks", s);
    }
    return null;
  }

  @Override
  protected void onProgressUpdate(Integer... values) {
    mXCSkiSchoolActivity.mChapter[mChapterIdx].downloadPct = values[0];
    if (!mFinishedText.equals(""))
      Toast.makeText(mXCSkiSchoolActivity, mFinishedText, Toast.LENGTH_SHORT);
    ListView view = mXCSkiSchoolActivity.getListView();
    view.invalidateViews();
  }
}
