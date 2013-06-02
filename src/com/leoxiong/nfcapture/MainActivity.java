package com.leoxiong.nfcapture;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ShareActionProvider;
import android.widget.TextView;

import com.leoxiong.nfcapture.EMV.EMV_Proprietary_Template;
import com.leoxiong.nfcapture.EMV.FCI_Template;

public class MainActivity extends Activity {
	private static final String TAG = "NFCapture";
	private NfcAdapter mNfcAdapter;
	private PendingIntent mPendingIntent;
	private IntentFilter[] mIntentFilters;
	private String[][] mTechLists;
	private Vibrator mVibratorService;
	private ShareActionProvider mShareActionProvider;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		mVibratorService = (Vibrator) getSystemService(VIBRATOR_SERVICE);
		
		mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
		
		mPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
		IntentFilter intentFilter = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
		try {
			intentFilter.addDataType("*/*");
		} catch (MalformedMimeTypeException e) {
			e.printStackTrace();
		}
		mIntentFilters = new IntentFilter[] { intentFilter };
		mTechLists = new String[][] { new String[] { IsoDep.class.getName(), MifareClassic.class.getName(), NfcA.class.getName(), NdefFormatable.class.getName() } };
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		
		MenuItem item = menu.findItem(R.id.share);
		mShareActionProvider = (ShareActionProvider) item.getActionProvider();
		mShareActionProvider.setShareIntent(setShareIntent());
		
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    case R.id.about:
	    	final Dialog dialog = new Dialog(this);
	    	dialog.setTitle("About");
			dialog.setContentView(R.layout.about_dialog);
			dialog.show();
	    default:
	        return super.onOptionsItemSelected(item);
	    }
	}
	
	@Override
	public boolean onMenuOpened(int featureId, Menu menu){
		mShareActionProvider.setShareIntent(setShareIntent());
		return true;
	}
	
	@Override
	public void onPause(){
		mNfcAdapter.disableForegroundDispatch(this);
		
		super.onPause();
	}
	
	@Override
	public void onResume(){
		super.onResume();

		if (!mNfcAdapter.isEnabled()){
			new AlertDialog.Builder(this)
				.setTitle("Enable Near Field Commuication")
				.setMessage("NFCapture requires the NFC antenna to be on")
				.setCancelable(false)
				.setPositiveButton("Settings", new OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
						startActivity(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS));
					}
				})
				.setNegativeButton("Quit", new OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						finish();
					}
				}).show();
		}
		
		mNfcAdapter.enableForegroundDispatch(this, mPendingIntent, mIntentFilters, mTechLists);
	}
	
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState){
		savedInstanceState.putCharSequence("log", ((TextView) findViewById(R.id.editTextLog)).getText());
		
		super.onSaveInstanceState(savedInstanceState);
	}
	
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState){
		super.onRestoreInstanceState(savedInstanceState);

		((TextView) findViewById(R.id.editTextLog)).setText(savedInstanceState.getCharSequence("log"));
	}
	
	@Override
	public void onNewIntent(Intent intent){
		((TextView) findViewById(R.id.editTextLog)).setText("");

		mVibratorService.vibrate(40);

		EMV emv = new EMV((Tag) intent.getParcelableExtra(NfcAdapter.EXTRA_TAG));
		
		FCI_Template selectPSE = null;
		FCI_Template selectApplet = null;
		EMV_Proprietary_Template emvProprietary = null;
		
		try {
			selectPSE = new FCI_Template(emv.SELECT_PSE(EMV.APDU.PSE2));
			selectApplet = new FCI_Template(emv.SELECT_APPLET(selectPSE.proprietary_Template.issuer_Discretionary_Data.application_Template.aid.getBytes()));
			emvProprietary = new EMV_Proprietary_Template(emv.READ_RECORD());
		} catch (Exception e) {
			mVibratorService.vibrate(new long[] { 0, 50, 50, 50}, -1);
			e.printStackTrace();
			print(e.toString());
			emv.dispose();
			return;
		}
		

		mVibratorService.vibrate(100);		
		emv.dispose();
		
		//http://saush.files.wordpress.com/2006/09/img1.png
		print("Select Payment System Environment: " + selectPSE.data.toHex() +
				"\nFCI Template: " + selectPSE.fci.toHex() +
				"\n\tDF Name: " + selectPSE.dfName.toASCII() +
				"\n\tFCI Propritary Template: " + selectPSE.proprietary_Template.data.toHex() +
				"\n\t\tSFI of DEF: " + selectPSE.proprietary_Template.sfi.toHex() +
				"\n\t\tLanguage Preference: " + selectPSE.proprietary_Template.languagePreference.toHex() +
				"\n\t\tIssuer Code Table Index: " + selectPSE.proprietary_Template.issuerCodeTableIndex.toHex() +
				"\n\t\tFCI Issuer Discresionary Data Template: " + selectPSE.proprietary_Template.issuer_Discretionary_Data.data.toHex() +
				"\n\t\t\t\tApplication Identifier: " + selectPSE.proprietary_Template.issuer_Discretionary_Data.application_Template.aid.toHex() +
				"\n\t\t\t\tApplication Label: " + selectPSE.proprietary_Template.issuer_Discretionary_Data.application_Template.apl.toASCII() +
				"\n\t\t\t\tApplication Priority Indicator: " + selectPSE.proprietary_Template.issuer_Discretionary_Data.application_Template.api.toHex());
		
		/*
		 *TODO: GPO if PDOL is null
		if (pdol == null){
		response = emv.GPO(EMV.APDU.PDOL);
			print("Get Processing Options: " + response.toHex());
		}
		*/
		
		//http://saush.files.wordpress.com/2006/09/img3.png
		print("Select CC Application: " + selectApplet.data.toHex() +
				"\nFCI Template: " + selectApplet.fci.toHex() +
				"\n\tDF Name: " + selectApplet.dfName.toHex() +
				"\n\tFCI Propritary Template: " + selectApplet.proprietary_Template.data.toHex() +
				"\n\t\tApplication Label: " + selectApplet.proprietary_Template.applicationLabel.toASCII() +
				"\n\t\t\t\tApplication Priority Indicator: " + selectApplet.proprietary_Template.applicationPriorityIndicator.toHex() +
				"\n\t\t\t\tPDOL: " + selectApplet.proprietary_Template.pdol.toHex() +
				"\n\t\t\t\tLanguage Preference: " + selectApplet.proprietary_Template.languagePreference.toASCII() +
				"\n\t\t\t\tIssuer Code Table Index: " + selectApplet.proprietary_Template.issuerCodeTableIndex.toHex() +
				"\n\t\t\t\tApplication Preferred Name: " + selectApplet.proprietary_Template.applicationPreferredName.toHex() +
				"\n\t\t\t\tFCI Issuer Discretionary Data: " + selectApplet.proprietary_Template.issuer_Discretionary_Data.data.toHex() +
				"\n\t\t\t\t\tLog Entry: " + selectApplet.proprietary_Template.issuer_Discretionary_Data.logEntry.toHex());
		
		print("Read record: " + emvProprietary.data.toHex() +
				"\nTrack 2 Equilvalent Data: " + emvProprietary.track2.data.toHex() +
				"\n\tPrimary Account Number: " + emvProprietary.track2.pan +
				"\n\tExpirary Date (YYMM): " + emvProprietary.track2.expirary +
				"\nCardholder Name: " + emvProprietary.cardholderName +
				"\nTrack 1 Discretionary Data: " + emvProprietary.track1.toHex());
	}
	
	private Intent setShareIntent() {
		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setType("text/plain");
		intent.putExtra(Intent.EXTRA_TEXT, ((TextView) findViewById(R.id.editTextLog)).getText().toString());
    	return intent;
	}
	
	public void print(String message){
		((TextView) findViewById(R.id.editTextLog)).append(message + "\n");
	}

}
