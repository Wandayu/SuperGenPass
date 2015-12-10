package info.staticfree.SuperGenPass;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.Toast;

import com.google.zxing.client.android.Intents;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.apache.commons.codec.binary.Base64;

import java.security.SecureRandom;

public class Preferences extends PreferenceFragment {

    public static final String ACTION_SCAN_SALT =
            "info.staticfree.android.supergenpass.action.SCAN_SALT";
    public static final String ACTION_GENERATE_SALT =
            "info.staticfree.android.supergenpass.action.GENERATE_SALT";

    public static final String ACTION_CLEAR_STORED_DOMAINS =
            "info.staticfree.android.supergenpass.action.CLEAR_STORED_DOMAINS";

    public static final String PREF_PW_TYPE = "pw_type";
    public static final String PREF_PW_LENGTH = "pw_length";
    public static final String PREF_PW_SALT = "pw_salt";
    public static final String PREF_CLIPBOARD = "clipboard";
    public static final String PREF_REMEMBER_DOMAINS = "domain_autocomplete";
    public static final String PREF_DOMAIN_NOCHECK = "domain_nocheck";
    public static final String PREF_SHOW_GEN_PW = "show_gen_pw";
    public static final String PREF_PW_CLEAR_TIMEOUT = "pw_clear_timeout";
    public static final String PREF_SHOW_PIN = "show_pin";
    public static final String PREF_PIN_DIGITS = "pw_pin_digits";
    public static final String PREF_VISUAL_HASH = "visual_hash";

    // idea borrowed from
    // http://stackoverflow.com/questions/3206765/number-preferences-in-preference-activity-in
    // -android
    private final OnPreferenceChangeListener integerConformCheck =
            new OnPreferenceChangeListener() {

                @Override
                public boolean onPreferenceChange(final Preference preference,
                        final Object newValue) {
                    if (!isInteger(newValue)) {
                        Toast.makeText(getActivity().getApplicationContext(),
                                R.string.pref_err_not_number, Toast.LENGTH_LONG).show();
                        return false;
                    }
                    return true;
                }
            };
    @Nullable
    private ShowDomainCountTask mDomainCountTask;

    private final ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(final boolean selfChange) {
            showDomainCount();
        }
    };

    public boolean isInteger(final Object newValue) {
        try {
            //noinspection ResultOfMethodCallIgnored
            Integer.parseInt((String) newValue);
        } catch (@NonNull final NumberFormatException e) {
            return false;
        }
        return true;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);
        findPreference(PREF_PW_CLEAR_TIMEOUT).setOnPreferenceChangeListener(integerConformCheck);
        findPreference(PREF_PW_LENGTH).setOnPreferenceChangeListener(integerConformCheck);
    }

    @Override
    public void onResume() {
        super.onResume();

        showDomainCount();

        getActivity().getContentResolver()
                .registerContentObserver(Domain.CONTENT_URI, true, mObserver);
    }

    @Override
    public void onPause() {
        super.onPause();

        getActivity().getContentResolver().unregisterContentObserver(mObserver);
    }


    private void showDomainCount() {
        if (mDomainCountTask == null) {
            mDomainCountTask = new ShowDomainCountTask();
            mDomainCountTask.execute();
        }
    }

    private void scanSalt() {
        final IntentIntegrator qr = new IntentIntegrator(getActivity());
        qr.addExtra(Intents.Scan.PROMPT_MESSAGE,
                getString(R.string.pref_scan_qr_code_to_load_zxing_message));
        qr.addExtra(Intents.Scan.SAVE_HISTORY, false);
        qr.initiateScan(IntentIntegrator.QR_CODE_TYPES);
    }

    private void setSaltPref(final String salt) {
        ((EditTextPreference) findPreference(PREF_PW_SALT)).setText(salt);
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode,
            @NonNull final Intent data) {
        final IntentResult res =
                IntentIntegrator.parseActivityResult(requestCode, resultCode, data);

        if (res != null && res.getContents() != null) {
            final String salt = res.getContents();
            setSaltPref(salt);
        }
    }

    public static int getStringAsInteger(@NonNull final SharedPreferences prefs, final String key,
            final int def) {
        final String defString = Integer.toString(def);
        int retval;
        try {
            retval = Integer.parseInt(prefs.getString(key, defString));

            // in case the value ever gets corrupt, reset it to the default instead of freaking out
        } catch (@NonNull final NumberFormatException e) {
            prefs.edit().putString(key, defString).apply();
            retval = def;
        }
        return retval;
    }

    private class ShowDomainCountTask extends AsyncTask<Void, Void, Integer> {

        @Override
        protected Integer doInBackground(final Void... arg0) {
            final Cursor c = getActivity().getContentResolver()
                    .query(Domain.CONTENT_URI, new String[] {}, null, null, null);
            final int domains;

            if (c != null) {
                domains = c.getCount();
                c.close();
            } else {
                domains = 0;
            }

            return domains;
        }

        @Override
        protected void onPostExecute(final Integer domains) {

            final Preference clear = findPreference("clear_remembered");
            clear.setEnabled(domains > 0);
            clear.setSummary(getResources()
                    .getQuantityString(R.plurals.pref_autocomplete_count, domains, domains));

            mDomainCountTask = null;
        }
    }

    public static class SaltFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(final Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.pref_generate_salt_title)
                    .setMessage(R.string.pref_generate_salt_dialog_message)
                    .setPositiveButton(R.string.pref_generate_salt_and_set,
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(final DialogInterface dialog, final int which) {
                                    generateSalt();
                                }
                            }).setCancelable(true).setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(@NonNull final DialogInterface dialog,
                                        final int which) {
                                    dialog.dismiss();
                                }
                            }).create();
        }

        private void generateSalt() {
            final IntentIntegrator qr = new IntentIntegrator(getActivity());
            final SecureRandom sr = new SecureRandom();
            final byte[] salt = new byte[512];
            sr.nextBytes(salt);
            final String saltb64 = new String(Base64.encodeBase64(salt)).replaceAll("\\s", "");
            ((Preferences)getFragmentManager().findFragmentById(R.id.preferences)).setSaltPref(saltb64);
            qr.addExtra(Intents.Encode.SHOW_CONTENTS, false);
            qr.shareText(saltb64);
        }
    }
}