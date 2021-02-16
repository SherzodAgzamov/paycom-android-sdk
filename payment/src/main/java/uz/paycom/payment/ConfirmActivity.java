package uz.paycom.payment;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

import uz.paycom.payment.api.JsonParser;
import uz.paycom.payment.api.JsonRpcRequest;
import uz.paycom.payment.model.Confirm;
import uz.paycom.payment.utils.LocaleHelper;

import static uz.paycom.payment.PaymentActivity.EXTRA_LANG;

public class ConfirmActivity extends AppCompatActivity {

  public final static String ARG_CONFIRM = "CONFIRM";
  public final static String ARG_TOKEN = "TOKEN";

  private String token;

  private Button activityConfirmButton;
  private ImageView activityRepeatImage;
  private TextView activityConfirmError;
  private TextView activityConfirmClose;
  private TextView activityConfirmTimer;
  private EditText activityConfirmCodeConfirm;
  private TextView activityConfirmPhoneNumber;
  private ProgressBar activityConfirmProgress;
  private TextView activityConfirmErrorMessage;
  private TextView activityConfirmPhoneNumberTitle;
  private TextView activityConfirmCodeConfirmTitle;
  private RelativeLayout activityConfirmErrorLayout;

  private final int CONSENT_REQUEST = 123;
  private final Pattern codePattern = Pattern.compile("(\\d{6})");

  private final BroadcastReceiver receiver = new BroadcastReceiver() {
    @Override public void onReceive(Context context, Intent intent) {
      if (SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.getAction())) {
        Bundle extras = intent.getExtras();
        Status status = (Status) extras.get(SmsRetriever.EXTRA_STATUS);

        switch (status.getStatusCode()) {
          case CommonStatusCodes.SUCCESS: {

            Intent consentIntent = extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT);
            try {
              if(consentIntent != null){
                onSuccess(consentIntent);
              }else{
                onError();
              }
            } catch (ActivityNotFoundException e) {
              onError();
            }
          }
          case CommonStatusCodes.TIMEOUT: {
            onError();
          }
        }

      }
    }
  };

  private void onSuccess(Intent intent){
    startActivityForResult(intent, CONSENT_REQUEST);
  }

  private void onError(){
    Toast.makeText(this, getString(R.string.failed_get_code), Toast.LENGTH_SHORT).show();
  }

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.paycom_payment_confirm);

    if(getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

    activityRepeatImage = findViewById(R.id.activity_repeat_image);
    activityConfirmError = findViewById(R.id.activity_confirm_error);
    activityConfirmClose = findViewById(R.id.activity_confirm_close);
    activityConfirmTimer = findViewById(R.id.activity_confirm_timer);
    activityConfirmButton = findViewById(R.id.activity_confirm_button);
    activityConfirmProgress = findViewById(R.id.activity_confirm_progress);
    activityConfirmErrorLayout = findViewById(R.id.activity_confirm_errorLayout);
    activityConfirmCodeConfirm = findViewById(R.id.activity_confirm_codeConfirm);
    activityConfirmPhoneNumber = findViewById(R.id.activity_confirm_phoneNumber);
    activityConfirmErrorMessage = findViewById(R.id.activity_confirm_errorMessage);
    activityConfirmCodeConfirmTitle = findViewById(R.id.activity_confirm_codeConfirmTitle);
    activityConfirmPhoneNumberTitle = findViewById(R.id.activity_confirm_phoneNumberTitle);

    Context context = LocaleHelper.onAttach(this, getIntent().getStringExtra(EXTRA_LANG));
    Resources resources = context.getResources();
    this.setTitle(resources.getString(R.string.paycomTitle));
    activityConfirmError.setText(resources.getString(R.string.error));
    activityConfirmClose.setText(resources.getString(R.string.close));
    activityConfirmCodeConfirmTitle.setText(resources.getString(R.string.codeConfirm));
    activityConfirmPhoneNumberTitle.setText(resources.getString(R.string.codeSent));
    activityConfirmButton.setText(resources.getString(R.string.confirm));
    activityConfirmCodeConfirmTitle.setText(resources.getString(R.string.codeConfirm));

    initUI(null);
    token = getIntent().getStringExtra(ARG_TOKEN);
    //Shared OnClickListener not allowed in library module project
    activityConfirmButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        activityConfirmErrorLayout.setVisibility(View.GONE);
        activityConfirmButton.setEnabled(false);
        activityConfirmProgress.setVisibility(View.VISIBLE);
        new ConfirmTask().execute();
      }
    });

    activityConfirmClose.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        activityConfirmErrorLayout.setVisibility(View.GONE);
      }
    });

    activityRepeatImage.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        activityConfirmErrorLayout.setVisibility(View.GONE);
        activityConfirmButton.setEnabled(false);
        activityConfirmProgress.setVisibility(View.VISIBLE);
        new RetryTask().execute();
      }
    });
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      finish();
    }
    return super.onOptionsItemSelected(item);
  }

  private void initUI(Confirm confirm) {
    if (confirm == null) {
      confirm = getIntent().getParcelableExtra(ARG_CONFIRM);
    }

    activityConfirmPhoneNumber.setText(confirm.getPhone());
    activityRepeatImage.setVisibility(View.GONE);
    activityConfirmTimer.setVisibility(View.VISIBLE);
    new CountDownTimer(confirm.getWait(), 1000) {
      public void onTick(long millisUntilFinished) {
        activityConfirmTimer.setText("0:" + String.valueOf(millisUntilFinished / 1000));
      }
      public void onFinish() {
        activityRepeatImage.setVisibility(View.VISIBLE);
        activityConfirmTimer.setVisibility(View.GONE);
      }
    }.start();
  }

  private class ConfirmTask extends AsyncTask<Void, Void, String> {

    private String code;
    private boolean hasError;
    private JsonParser jsonParser;

    public ConfirmTask() {
      this.jsonParser = new JsonParser();
    }

    @Override protected void onPreExecute() {
      code = activityConfirmCodeConfirm.getText().toString();
    }

    @Override protected String doInBackground(Void... params) {
      JsonRpcRequest jsonRpcRequest = new JsonRpcRequest(PaymentActivity.id);

      JSONObject jsonObject = jsonParser.getCardsVerify(token, code);
      String result = jsonRpcRequest.callApiMethod(jsonObject, JsonRpcRequest.cardsCreateVerifyMethod);

      if (result == null) return null;
      if (jsonParser.checkError(result) != null) {
        hasError = true;
        return jsonParser.checkError(result);
      }

      return result;
    }

    @Override protected void onPostExecute(String s) {
      if (s == null) {
        showError(getString(R.string.tryAgainMessage));
      } else if (hasError) {
          showError(s);
      } else {
          Intent intent = new Intent();
          intent.putExtra(PaymentActivity.EXTRA_RESULT, jsonParser.getResult(s));
          setResult(RESULT_OK, intent);
          finish();
      }
      activityConfirmButton.setEnabled(true);
      activityConfirmProgress.setVisibility(View.GONE);
    }
  }

  private class RetryTask extends AsyncTask<Void, Void, String> {

    private boolean hasError;
    private JsonParser jsonParser;

    public RetryTask() {
      jsonParser = new JsonParser();
    }

    @Override
    protected String doInBackground(Void... params) {
      JsonRpcRequest jsonRpcRequest = new JsonRpcRequest(PaymentActivity.id);
      JSONObject jsonObject = jsonParser.getCardsVerifyCode(token);

      String result = jsonRpcRequest.callApiMethod(jsonObject, JsonRpcRequest.cardsGetVerifyCodeMethod);
      if (result == null) return null;
      if (jsonParser.checkError(result) != null) {
        hasError = true;
        return jsonParser.checkError(result);
      }

      return result;
    }

    @Override
    protected void onPostExecute(String s) {
      if (s == null) {
        showError(getString(R.string.tryAgainMessage));
      } else if (hasError) {
          showError(s);
      } else {
          initUI(jsonParser.getConfirm(s));
      }
      activityConfirmButton.setEnabled(true);
      activityConfirmProgress.setVisibility(View.GONE);
    }
  }

  private void showError(String s) {
    if (!TextUtils.isEmpty(s)) {
      activityConfirmErrorLayout.setVisibility(View.VISIBLE);
      activityConfirmErrorMessage.setText(s);
    }
  }

  private void parseOneTimeCode(String message) {
    Matcher matcher = codePattern.matcher(message);

    if(matcher.find()){
      activityConfirmCodeConfirm.setText(matcher.group());
    }
  }

  @Override protected void onResume() {
    super.onResume();
    IntentFilter filter = new IntentFilter("com.google.android.gms.auth.api.phone.SMS_RETRIEVED");
    registerReceiver(receiver, filter);
  }

  @Override protected void onPause() {
    super.onPause();
    unregisterReceiver(receiver);
  }

  @Override
  public void onBackPressed() {
    super.onBackPressed();
    setResult(RESULT_CANCELED);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if(requestCode == CONSENT_REQUEST){
      if(resultCode == Activity.RESULT_OK && data != null){
        String message = data.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE);
        parseOneTimeCode(message);
      }else{
        Toast.makeText(this, getString(R.string.failed_get_code), Toast.LENGTH_SHORT).show();
      }
    }
  }
}