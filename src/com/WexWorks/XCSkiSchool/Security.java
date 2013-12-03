// Copyright 2010 Google Inc. All Rights Reserved.

package com.WexWorks.XCSkiSchool;

import com.WexWorks.XCSkiSchool.Consts.PurchaseState;
import com.WexWorks.XCSkiSchool.util.Base64;
import com.WexWorks.XCSkiSchool.util.Base64DecoderException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.text.TextUtils;
import android.util.Log;

import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.HashSet;


/**
 * Security-related methods. For a secure implementation, all of this code
 * should be implemented on a server that communicates with the application on
 * the device. For the sake of simplicity and clarity of this example, this code
 * is included here and is executed on the device. If you must verify the
 * purchases on the phone, you should obfuscate this code to make it harder for
 * an attacker to replace the code with stubs that treat all purchases as
 * verified.
 */
public class Security {
  private static final String TAG = "Security";

  private static final String KEY_FACTORY_ALGORITHM = "RSA";
  private static final String SIGNATURE_ALGORITHM = "SHA1withRSA";
  private static final SecureRandom RANDOM = new SecureRandom();

  /**
   * This keeps track of the nonces that we generated and sent to the server. We
   * need to keep track of these until we get back the purchase state and send a
   * confirmation message back to Android Market. If we are killed and lose this
   * list of nonces, it is not fatal. Android Market will send us a new "notify"
   * message and we will re-generate a new nonce. This has to be "static" so
   * that the {@link BillingReceiver} can check if a nonce exists.
   */
  private static HashSet<Long> sKnownNonces = new HashSet<Long>();

  /**
   * A class to hold the verified purchase information.
   */
  public static class VerifiedPurchase {
    public PurchaseState purchaseState;
    public String notificationId;
    public String productId;
    public String orderId;
    public long purchaseTime;
    public String developerPayload;

    public VerifiedPurchase(PurchaseState purchaseState, String notificationId,
        String productId, String orderId, long purchaseTime,
        String developerPayload) {
      this.purchaseState = purchaseState;
      this.notificationId = notificationId;
      this.productId = productId;
      this.orderId = orderId;
      this.purchaseTime = purchaseTime;
      this.developerPayload = developerPayload;
    }
  }

  /** Generates a nonce (a random number used once). */
  public static long generateNonce() {
    long nonce = RANDOM.nextLong();
    sKnownNonces.add(nonce);
    return nonce;
  }

  public static void removeNonce(long nonce) {
    sKnownNonces.remove(nonce);
  }

  public static boolean isNonceKnown(long nonce) {
    return sKnownNonces.contains(nonce);
  }

  private static String xorMessage(String message, String key) {
    try {
      if (message == null || key == null)
        return null;

      char[] keys = key.toCharArray();
      char[] mesg = message.toCharArray();

      int ml = mesg.length;
      int kl = keys.length;
      char[] newmsg = new char[ml];

      for (int i = 0; i < ml; i++) {
        newmsg[i] = (char) (mesg[i] ^ keys[i % kl]);
      }// for i
      mesg = null;
      keys = null;
      return new String(newmsg);
    } catch (Exception e) {
      return null;
    }
  }
  
  private static String goForAWalk() {
    char[] jibber = { 4, 47, 105, 59, 38, 31, 97, 45, 35, 9, 75, 6, 9, 7, 2,
        103, 64, 24, 69, 98, 34, 48, 43, 102, 50, 42, 38, 10, 39, 113, 65, 46,
        56, 105, 42, 35, 45, 71, 60, 34, 45, 58, 101, 56, 29, 52, 79, 91, 3,
        31, 103, 52, 59, 89, 57, 55, 11, 18, 60, 6, 18, 21, 50, 13, 71, 56, 18,
        40, 93, 21, 19, 24, 47, 79, 6, 45, 31, 23, 49, 7, 35, 27, 85, 118, 56,
        43, 71, 66, 4, 59, 95, 85, 6, 84, 8, 62, 84, 31, 68, 7, 98, 42, 87, 52,
        99, 66, 59, 28, 26, 19, 19, 48, 93, 62, 17, 43, 14, 86, 87, 49, 40, 9,
        51, 78, 58, 0, 36, 118, 8, 87, 4, 21, 57, 5, 29, 45, 18, 111, 86, 41,
        18, 67, 11, 49, 20, 84, 38, 83, 9, 51, 105, 52, 29, 27, 66, 45, 23, 1,
        88, 4, 37, 12, 51, 17, 119, 82, 21, 13, 110, 52, 23, 10, 97, 1, 27, 32,
        19, 109, 73, 41, 31, 68, 45, 12, 58, 17, 37, 30, 10, 10, 44, 78, 40,
        38, 48, 90, 76, 55, 92, 111, 65, 39, 43, 8, 99, 65, 54, 68, 90, 32, 43,
        12, 80, 43, 82, 93, 6, 44, 120, 53, 7, 7, 66, 25, 40, 28, 105, 62, 15,
        35, 83, 22, 3, 28, 58, 82, 14, 45, 63, 116, 74, 61, 27, 62, 7, 65, 62,
        32, 4, 66, 40, 52, 12, 87, 5, 44, 85, 68, 113, 45, 32, 2, 71, 15, 44,
        31, 113, 65, 28, 36, 120, 8, 78, 58, 86, 69, 116, 10, 9, 4, 73, 37, 82,
        25, 4, 25, 86, 25, 4, 107, 48, 14, 33, 120, 17, 26, 80, 127, 50, 118,
        10, 30, 23, 106, 38, 23, 8, 119, 7, 15, 88, 92, 117, 41, 8, 1, 112, 42,
        54, 4, 111, 41, 3, 38, 60, 81, 74, 18, 68, 56, 121, 46, 5, 52, 17, 62,
        44, 63, 56, 103, 47, 10, 28, 111, 15, 54, 94, 66, 5, 2, 38, 48, 80,
        107, 61, 28, 52, 24, 54, 59, 26, 68, 62, 52, 91, 33, 105, 30, 46, 6,
        16, 13, 7, 13, 122, 26, 64, 58, 49, 30, 82, 40, 55, 2, 19, 50, 40, 42,
        97, 38, 32, 46, };
    String jabber = new String(jibber);
    String woky = "If you can walk you can ski";
    return xorMessage(jabber, woky);
  }
  
  /**
   * Verifies that the data was signed with the given signature, and returns the
   * list of verified purchases. The data is in JSON format and contains a nonce
   * (number used once) that we generated and that was signed (as part of the
   * whole data string) with a private key. The data also contains the
   * {@link PurchaseState} and product ID of the purchase. In the general case,
   * there can be an array of purchase transactions because there may be delays
   * in processing the purchase on the backend and then several purchases can be
   * batched together.
   * 
   * @param signedData
   *          the signed JSON string (signed, not encrypted)
   * @param signature
   *          the signature for the data, signed with the private key
   */
  public static ArrayList<VerifiedPurchase> verifyPurchase(String signedData,
      String signature) {
    if (signedData == null) {
      Log.e(TAG, "data is null");
      return null;
    }
    if (Consts.DEBUG) {
      Log.i(TAG, "signedData: " + signedData);
    }
    boolean verified = false;
    if (!TextUtils.isEmpty(signature)) {
      /**
       * Compute your public key (that you got from the Android Market publisher
       * site).
       * 
       * Instead of just storing the entire literal string here embedded in the
       * program, construct the key at runtime from pieces or use bit
       * manipulation (for example, XOR with some other string) to hide the
       * actual key. The key itself is not secret information, but we don't want
       * to make it easy for an adversary to replace the public key with one of
       * their own and then fake messages from the server.
       * 
       * Generally, encryption keys / passwords should only be kept in memory
       * long enough to perform the operation they need to perform.
       */
      String base64EncodedPublicKey = goForAWalk();
      PublicKey key = Security.generatePublicKey(base64EncodedPublicKey);
      verified = Security.verify(key, signedData, signature);
      if (!verified) {
        Log.w(TAG, "signature does not match data.");
        return null;
      }
    }

    JSONObject jObject;
    JSONArray jTransactionsArray = null;
    int numTransactions = 0;
    long nonce = 0L;
    try {
      jObject = new JSONObject(signedData);

      // The nonce might be null if the user backed out of the buy page.
      nonce = jObject.optLong("nonce");
      jTransactionsArray = jObject.optJSONArray("orders");
      if (jTransactionsArray != null) {
        numTransactions = jTransactionsArray.length();
      }
    } catch (JSONException e) {
      return null;
    }

    if (!Security.isNonceKnown(nonce)) {
      Log.w(TAG, "Nonce not found: " + nonce);
      return null;
    }

    ArrayList<VerifiedPurchase> purchases = new ArrayList<VerifiedPurchase>();
    try {
      for (int i = 0; i < numTransactions; i++) {
        JSONObject jElement = jTransactionsArray.getJSONObject(i);
        int response = jElement.getInt("purchaseState");
        PurchaseState purchaseState = PurchaseState.valueOf(response);
        String productId = jElement.getString("productId");
        String packageName = jElement.getString("packageName");
        long purchaseTime = jElement.getLong("purchaseTime");
        String orderId = jElement.optString("orderId", "");
        String notifyId = null;
        if (jElement.has("notificationId")) {
          notifyId = jElement.getString("notificationId");
        }
        String developerPayload = jElement.optString("developerPayload", null);

        // If the purchase state is PURCHASED, then we require a
        // verified nonce.
        if (purchaseState == PurchaseState.PURCHASED && !verified) {
          continue;
        }
        purchases.add(new VerifiedPurchase(purchaseState, notifyId, productId,
            orderId, purchaseTime, developerPayload));
      }
    } catch (JSONException e) {
      Log.e(TAG, "JSON exception: ", e);
      return null;
    }
    removeNonce(nonce);
    return purchases;
  }

  /**
   * Generates a PublicKey instance from a string containing the Base64-encoded
   * public key.
   * 
   * @param encodedPublicKey
   *          Base64-encoded public key
   * @throws IllegalArgumentException
   *           if encodedPublicKey is invalid
   */
  public static PublicKey generatePublicKey(String encodedPublicKey) {
    try {
      byte[] decodedKey = Base64.decode(encodedPublicKey);
      KeyFactory keyFactory = KeyFactory.getInstance(KEY_FACTORY_ALGORITHM);
      return keyFactory.generatePublic(new X509EncodedKeySpec(decodedKey));
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    } catch (InvalidKeySpecException e) {
      Log.e(TAG, "Invalid key specification.");
      throw new IllegalArgumentException(e);
    } catch (Base64DecoderException e) {
      Log.e(TAG, "Base64 decoding failed.");
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * Verifies that the signature from the server matches the computed signature
   * on the data. Returns true if the data is correctly signed.
   * 
   * @param publicKey
   *          public key associated with the developer account
   * @param signedData
   *          signed data from server
   * @param signature
   *          server signature
   * @return true if the data and signature match
   */
  public static boolean verify(PublicKey publicKey, String signedData,
      String signature) {
    if (Consts.DEBUG) {
      Log.i(TAG, "signature: " + signature);
    }
    Signature sig;
    try {
      sig = Signature.getInstance(SIGNATURE_ALGORITHM);
      sig.initVerify(publicKey);
      sig.update(signedData.getBytes());
      if (!sig.verify(Base64.decode(signature))) {
        Log.e(TAG, "Signature verification failed.");
        return false;
      }
      return true;
    } catch (NoSuchAlgorithmException e) {
      Log.e(TAG, "NoSuchAlgorithmException.");
    } catch (InvalidKeyException e) {
      Log.e(TAG, "Invalid key specification.");
    } catch (SignatureException e) {
      Log.e(TAG, "Signature exception.");
    } catch (Base64DecoderException e) {
      Log.e(TAG, "Base64 decoding failed.");
    }
    return false;
  }
}
