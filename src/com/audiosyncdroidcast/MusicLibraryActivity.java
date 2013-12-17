package com.audiosyncdroidcast;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;


public class MusicLibraryActivity extends Activity 
{
	private ListView musiclist;
	private Cursor musiccursor;
	private int music_column_index;
	private int count;

	/** Called when the activity is first created. */
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.music_library_test);
		
		init_phone_music_grid();
	}

	private void init_phone_music_grid() {
		System.gc();

		String[] proj = { MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Video.Media.SIZE };

		musiccursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, null, null, null);

		count = musiccursor.getCount();

		musiclist = (ListView) findViewById(R.id.PhoneMusicList);
		musiclist.setAdapter(new MusicAdapter(getApplicationContext()));

		musiclist.setOnItemClickListener(musicgridlistener);
		musiclist.setBackgroundColor(Color.BLACK);
	}

	private AdapterView.OnItemClickListener musicgridlistener = new AdapterView.OnItemClickListener() 
	{
		public void onItemClick(AdapterView parent, View v, int position,long id)
		{
			System.gc();
			music_column_index = musiccursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
			musiccursor.moveToPosition(position);
			String filename = musiccursor.getString(music_column_index);

			Intent in = new Intent(getApplicationContext(),
					ServerActivity.class);

			setResult(100, in);

			// Sending songIndex to PlayerActivity
			in.putExtra("songTitle", filename);
			setResult(100, in);
			finish();
		}
	};

	public class MusicAdapter extends BaseAdapter 
	{
		private Context mContext;

		public MusicAdapter(Context c) 
		{
			mContext = c;
		}

		public int getCount()
		{
			return count;
		}

		public Object getItem(int position)
		{
			return position;
		}

		public long getItemId(int position) 
		{
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) 
		{
			System.gc();
			TextView tv = new TextView(mContext.getApplicationContext());
			tv.setPadding(10, 10, 10, 10);
			tv.setBackgroundColor(Color.rgb(55, 55, 55));
			tv.setTextColor(Color.WHITE);
			tv.setTextSize(20.0f);

			String id = null;
			if (true) 
			{
				musiccursor.moveToPosition(position);
				music_column_index = musiccursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
				musiccursor.moveToPosition(position);
				id = musiccursor.getString(music_column_index);

				int mp3Index;
				if((mp3Index = id.indexOf(".mp3")) != -1)
				{
					id = id.substring(0, mp3Index);
				}
				
				int hashIndex;
				if((hashIndex = id.indexOf("#")) != -1)
				{
					id = id.substring(0, hashIndex);
				}

				tv.setText(id);
			} 

			return tv;
		}
	}
}