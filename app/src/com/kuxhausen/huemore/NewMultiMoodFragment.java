package com.kuxhausen.huemore;

import java.util.ArrayList;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;

import com.google.gson.Gson;
import com.kuxhausen.huemore.EditMoodPagerDialogFragment.OnCreateMoodListener;
import com.kuxhausen.huemore.persistence.DatabaseDefinitions;
import com.kuxhausen.huemore.persistence.HueUrlEncoder;
import com.kuxhausen.huemore.persistence.DatabaseDefinitions.InternalArguments;
import com.kuxhausen.huemore.persistence.Utils;
import com.kuxhausen.huemore.state.Event;
import com.kuxhausen.huemore.state.Mood;
import com.kuxhausen.huemore.state.api.BulbState;

public class NewMultiMoodFragment extends ListFragment implements
		OnClickListener, OnCreateMoodListener {

	MoodRowAdapter rayAdapter;
	ArrayList<MoodRow> moodRowArray;
	Gson gson = new Gson();

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		moodRowArray = new ArrayList<MoodRow>();

		View groupView = inflater.inflate(R.layout.edit_multi_mood, null);
		rayAdapter = new MoodRowAdapter(this.getActivity(), moodRowArray);
		setListAdapter(rayAdapter);

		Button addColor = (Button) groupView.findViewById(R.id.addColor);
		addColor.setOnClickListener(this);

		Bundle args = getArguments();
		if (args != null && args.containsKey(InternalArguments.MOOD_NAME)) {
			Mood mood = Utils.getMoodFromDatabase(args.getString(InternalArguments.MOOD_NAME), this.getActivity());
			for (Event e : mood.events) {
				MoodRow mr = new MoodRow();
				mr.hs = e.state;
				if (e.state.ct != null) {
					mr.color = Color.WHITE;
				} else {
					float[] hsv = { (e.state.hue * 360) / 65535, e.state.sat / 255f, 1 };
					mr.color = Color.HSVToColor(hsv);

				}
				moodRowArray.add(mr);
				rayAdapter.add(mr);
			}
		}

		return groupView;
	}

	private void addState() {
		MoodRow mr = new MoodRow();
		mr.color = 0xff000000;
		BulbState example = new BulbState();
		example.on = false;
		mr.hs = example;
		moodRowArray.add(mr);
		rayAdapter.add(mr);
		NewColorPagerDialogFragment cpdf = new NewColorPagerDialogFragment();
		// cpdf.setPreviewGroups(bulbS);//TODO fix
		cpdf.setTargetFragment(this, rayAdapter.getPosition(mr));
		cpdf.show(getFragmentManager(),
				InternalArguments.FRAG_MANAGER_DIALOG_TAG);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		rayAdapter.getItem(requestCode).color = resultCode;
		rayAdapter.notifyDataSetChanged();
		rayAdapter.getItem(requestCode).hs = gson.fromJson(
				data.getStringExtra(InternalArguments.HUE_STATE),
				BulbState.class);

		String[] states = new String[moodRowArray.size()];
		for (int i = 0; i < moodRowArray.size(); i++) {
			states[i] = gson.toJson(moodRowArray.get(i).hs);
		}
		//TODO fix
		//		((GodObject) getActivity()).testMood(states);

	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.addColor:
			addState();
			break;
		}
	}

	@Override
	public void onCreateMood(String groupname) {
		//TODO implement
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {

		NewColorPagerDialogFragment cpdf = new NewColorPagerDialogFragment();
		Bundle args = new Bundle();
		args.putString(InternalArguments.PREVIOUS_STATE,
				gson.toJson(moodRowArray.get(position).hs));
		cpdf.setArguments(args);
		// cpdf.show(getFragmentManager(), "dialog");
		rayAdapter.remove(moodRowArray.get(position));
		moodRowArray.remove(moodRowArray.get(position));

	}
}
