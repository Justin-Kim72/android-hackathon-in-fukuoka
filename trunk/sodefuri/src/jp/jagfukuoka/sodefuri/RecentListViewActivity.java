package jp.jagfukuoka.sodefuri;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jp.jagfukuoka.sodefuri.preference.TwitterPreferenceManager;
import jp.jagfukuoka.sodefuri.provider.RecentContentProvider;
import jp.jagfukuoka.sodefuri.service.BluetoothFoundReceiver;
import jp.jagfukuoka.sodefuri.service.RecentReceiver;
import jp.jagfukuoka.sodefuri.service.RecentService;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import twitter4j.ProfileImage;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;
import android.app.ListActivity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.ContentValues;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
/**
 * �����������ԂɃX�N���[���l�[�����\�������Activity
 * @author shikajiro
 *
 */
public class RecentListViewActivity extends ListActivity {
	private static final String FIND_SCREEN_NAME_URL = "http://sodefuri.appspot.com/find_name";

	boolean debug = true;
	public static final String SCREEN_NAME = "SCREEN_NAME";

	private static final int DEBUG_TOKEN_CLEAR_ID = 1;
	private static final int DEBUG_RECENT_CLEAR_ID = 2;
	private static final int DEBUG_RECENT_ADD_ID = 3;
	
	String[] projection = new String[] { RecentContentProvider.MAC_ADDRESS };
	List<String> screenNames;
	private TwitterPreferenceManager tpm = new TwitterPreferenceManager(this);  

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		//test daba insert
		if(debug){
	        ContentValues values = new ContentValues();
			values.put(RecentContentProvider.MAC_ADDRESS,"00:00:00:00:00:00");
			values.put(RecentContentProvider.MAC_ADDRESS,"E8:E5:D6:4C:52:3A");// _simo
			values.put(RecentContentProvider.MAC_ADDRESS,"F8:DB:7F:02:2E:EE");// shikajiro
	        getContentResolver().insert(RecentContentProvider.CONTENT_URI, values);
		}
		
		//twitterToken��������Γo�^��ʂ֑J�ڂ���
		if(!tpm.isAccessToken()){
			startActivity(new Intent(this,NewAccountActivity.class));
			return;
		}
		
		// bluetooth����service�̋N��
		startService(new Intent(this, RecentService.class));
		// bluetooth��������
		registerReceiver(new RecentReceiver(), new IntentFilter(RecentService.SEARCH));
		// bluetooth�f�o�C�X������������
		registerReceiver(new BluetoothFoundReceiver(), new IntentFilter(BluetoothDevice.ACTION_FOUND));
		//�f�[�^�ǉ���̉�ʕ`�揈��
		ContentObserver contentObserver = new ContentObserver(new Handler()) {
			@Override
			public void onChange(boolean selfChange) {
				//List�ւ̓o�^
				RecentAdapter recentAdapter = new RecentAdapter(getApplicationContext(), R.layout.list_item, getRecentBeans());
				setListAdapter(recentAdapter);
				//notification�ɂ�郆�[�U�[�ւ̒ʒm
				NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
				Notification notification = new Notification(android.R.drawable.btn_default,"����Ⴂ�܂���",System.currentTimeMillis());
				//intent�̐ݒ�
				Intent intent = new Intent(getApplicationContext(),RecentListViewActivity.class);
				PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);
				notification.setLatestEventInfo(getApplicationContext(),"sodefuri","�����̉�������܂����B",contentIntent);
				notificationManager.notify(R.string.app_name, notification);
				super.onChange(selfChange);
			}
		};
		getContentResolver().registerContentObserver(RecentContentProvider.CONTENT_URI, true, contentObserver);
		
		//List�ւ̓o�^
		RecentAdapter recentAdapter = new RecentAdapter(getApplicationContext(), R.layout.list_item, getRecentBeans());
		setListAdapter(recentAdapter);
		
		ListView lv = getListView();
		lv.setTextFilterEnabled(true);
		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				// �I������screename��Intent�œn���B
				TextView textView = (TextView)view.findViewById(R.id.toptext);
				CharSequence screen_name = textView.getText();
				
				Intent intent = new Intent(RecentListViewActivity.this,TimeLineActivity.class);
				intent.putExtra("screen_name", screen_name);
				startActivity(intent);
			}
		});

	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, DEBUG_TOKEN_CLEAR_ID, 0, "token clear");
		menu.add(0, DEBUG_RECENT_CLEAR_ID, 0, "recent clear");
		menu.add(0, DEBUG_RECENT_ADD_ID, 0, "add testdata");
		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case DEBUG_TOKEN_CLEAR_ID:
			tpm.clearAccessToken();
			break;
		case DEBUG_RECENT_CLEAR_ID:
			getContentResolver().delete(null, null, null);
			break;
		case DEBUG_RECENT_ADD_ID:
	        ContentValues values = new ContentValues();
	        values.put(RecentContentProvider.MAC_ADDRESS, "00:11:22:33:44:55");
	        getContentResolver().insert(RecentContentProvider.CONTENT_URI, values);
	    default:
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * server����mac_address�����Ƃ�screen_name���擾����
	 * 
	 * @return
	 */
	private List<RecentBean> getRecentBeans() {

		// json�쐬
		String json = this.getMacAddressJson();

		// json�ŃX�N���[���l�[����₢���킹��
		List<String> list = this.findScreenNames(json);
		List<RecentBean> beans = new ArrayList<RecentBean>();
		for(String str : list){
			RecentBean recentBean = new RecentBean();
			recentBean.setDate(new Date());
			recentBean.setScreenName(str);
			//�Ƃ肠�������� TODO
			String image ="";
			ConfigurationBuilder builder = new ConfigurationBuilder();
			Configuration conf = builder.setOAuthAccessToken(tpm.getAccessToken())
			.setOAuthAccessTokenSecret(tpm.getAccessTokenSercret())
			.setOAuthConsumerKey(TwitterPreferenceManager.CONSUMER_KEY)
			.setOAuthConsumerSecret(TwitterPreferenceManager.CONSUMER_SERCRET)
			.setDebugEnabled(true)
			.build();
			Twitter twitter = new TwitterFactory(conf).getInstance();
			try {
				ProfileImage profileImage = twitter.getProfileImage(str, ProfileImage.NORMAL);
				image = profileImage.getURL();
				Log.d("image", image);
			} catch (TwitterException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			recentBean.setImage(image);
			beans.add(recentBean);
		}
		return beans;
	}

	/**
	 * �X�N���[���l�[���擾����
	 * 
	 * @param json
	 * @param handler
	 * @return
	 */
	public List<String> findScreenNames(String json) {
		List<String> result = new ArrayList<String>();
		try {
			// -----[POST���M]
			// -----[�N���C�A���g�ݒ�]
			List<NameValuePair> nameValuePair = new ArrayList<NameValuePair>();
			nameValuePair.add(new BasicNameValuePair("json", json));
			HttpClient httpclient = new DefaultHttpClient();
			HttpPost httpPost = new HttpPost(FIND_SCREEN_NAME_URL);
			httpPost.setEntity(new UrlEncodedFormEntity(nameValuePair));
			HttpResponse response = httpclient.execute(httpPost);

			// -----[�T�[�o�[����̉������擾]
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				Toast.makeText(RecentListViewActivity.this, "�X�N���[���l�[�����擾���܂���", Toast.LENGTH_LONG).show();

				InputStream is = response.getEntity().getContent();
				BufferedReader br = new BufferedReader(
						new InputStreamReader(is));
				String responsJson = "";
				String line;
				while ((line = br.readLine()) != null) {
					responsJson += line;
				}

				JSONArray jsonArrays = new JSONArray(responsJson);
				for (int i = 0; i < jsonArrays.length(); i++) {
					JSONObject jsonObj = jsonArrays.getJSONObject(i);
					String screen_name = jsonObj.getString("screen_name");
					result.add(screen_name);
				}

		} else {
			Toast.makeText(RecentListViewActivity.this, "[�X�N���[���l�[���擾error]: " + response.getStatusLine(),
					Toast.LENGTH_LONG).show();
		}
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return result;
	}
	
	/**
	 * json�^��mac_address�ꗗ���擾����
	 * @return
	 */
	private String getMacAddressJson() {
		Cursor managedCursor = managedQuery(RecentContentProvider.CONTENT_URI, projection, null, null, null);
		JSONArray array = new JSONArray();
		try {
			List<String> list = getColumnData(managedCursor);
			for (String mac_address : list) {
				JSONObject obj = new JSONObject();
				obj.put(RecentContentProvider.MAC_ADDRESS, mac_address);
				array.put(obj);
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return  array.toString();
	}

	/**
	 * DB����mac_address�̃f�[�^�𒊏o����
	 * 
	 * @param cur
	 * @return
	 */
	private List<String> getColumnData(Cursor cur) {
		List<String> list = new ArrayList<String>();
		if (cur.moveToFirst()) {
			int int_mac_address = cur.getColumnIndex(RecentContentProvider.MAC_ADDRESS);
			do {
				// Get the field values
				String mac_address = cur.getString(int_mac_address);
				// map.put(MAC_ADDRESS, mac_address);
				// map.put(SCREEN_NAME, screen_name);
				list.add(mac_address);
			} while (cur.moveToNext());
		}
		return list;
	}
}