package com.kuxhausen.huemore;

import java.util.ArrayList;
import java.util.PriorityQueue;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.util.Pair;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.kuxhausen.huemore.automation.FireReceiver;
import com.kuxhausen.huemore.network.NetworkMethods;
import com.kuxhausen.huemore.persistence.DatabaseDefinitions.InternalArguments;
import com.kuxhausen.huemore.persistence.HueUrlEncoder;
import com.kuxhausen.huemore.state.Event;
import com.kuxhausen.huemore.state.Mood;
import com.kuxhausen.huemore.state.QueueEvent;
import com.kuxhausen.huemore.state.api.BulbState;
import com.kuxhausen.huemore.timing.AlarmReciever;

public class MoodExecuterService extends Service {

	/**
	 * Class used for the client Binder. Because we know this service always
	 * runs in the same process as its clients, we don't need to deal with IPC.
	 */
	public class LocalBinder extends Binder {
		MoodExecuterService getService() {
			// Return this instance of LocalService so clients can call public
			// methods
			return MoodExecuterService.this;
		}
	}
	MoodExecuterService me = this;
	private RequestQueue volleyRQ;

	int notificationId = 1337;

	// Binder given to clients
	private final IBinder mBinder = new LocalBinder();

	private BulbState[] transientStateChanges = new BulbState[50];

	{
		for (int i = 0; i < transientStateChanges.length; i++)
			transientStateChanges[i] = new BulbState();
	}

	private boolean[] flagTransientChanges = new boolean[50];

	private Pair<Integer[], Mood> moodPair;

	private CountDownTimer countDownTimer;

	int time;

	PriorityQueue<QueueEvent> queue = new PriorityQueue<QueueEvent>();

	PriorityQueue<QueueEvent> highPriorityQueue = new PriorityQueue<QueueEvent>();
	
	WakeLock wakelock;

	int transientIndex = 0;
	public MoodExecuterService() {
	}
	public void createNotification(String secondaryText) {
		// Creates an explicit intent for an Activity in your app
		Intent resultIntent = new Intent(this, MainActivity.class);
		PendingIntent resultPendingIntent = PendingIntent.getActivity(this, 0,
				resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
				this)
				.setSmallIcon(R.drawable.huemore)
				.setContentTitle(
						this.getResources().getString(R.string.app_name))
				.setContentText(secondaryText)
				.setContentIntent(resultPendingIntent);
		this.startForeground(notificationId, mBuilder.build());

	}

	public RequestQueue getRequestQueue() {
		return volleyRQ;
	}

	private boolean hasTransientChanges() {
		boolean result = false;
		for (boolean b : flagTransientChanges)
			result |= b;
		return result;
	}
	private void loadMoodIntoQueue() {
		Integer[] bulbS = moodPair.first;
		Mood m = moodPair.second;

		ArrayList<Integer>[] channels = new ArrayList[m.numChannels];
		for (int i = 0; i < channels.length; i++)
			channels[i] = new ArrayList<Integer>();

		for (int i = 0; i < bulbS.length; i++) {
			channels[i % m.numChannels].add(bulbS[i]);
		}

		for (Event e : m.events) {
			e.time += (int) System.nanoTime() / 1000000;// divide by 1 million
														// to convert to millis
			for (Integer bNum : channels[e.channel]) {
				QueueEvent qe = new QueueEvent(e);
				qe.bulb = bNum;
				queue.add(qe);
			}
		}
	}
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		volleyRQ = Volley.newRequestQueue(this);
		restartCountDownTimer();
		createNotification("");
		
		//acquire wakelock
		PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
		wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.getClass().getName());
		wakelock.acquire();
	}
	@Override
	public void onDestroy() {
		countDownTimer.cancel();
		volleyRQ.cancelAll(InternalArguments.TRANSIENT_NETWORK_REQUEST);
		volleyRQ.cancelAll(InternalArguments.PERMANENT_NETWORK_REQUEST);
		wakelock.release();
		super.onDestroy();
	}
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			//remove any possible launched wakelocks
			AlarmReciever.completeWakefulIntent(intent);
			FireReceiver.completeWakefulIntent(intent);

			
			String encodedMood = intent
					.getStringExtra(InternalArguments.ENCODED_MOOD);
			String encodedTransientMood = intent
					.getStringExtra(InternalArguments.ENCODED_TRANSIENT_MOOD);

			
			if (encodedMood != null) {
				moodPair = HueUrlEncoder.decode(encodedMood);
				queue.clear();
				loadMoodIntoQueue();

				String moodName = intent
						.getStringExtra(InternalArguments.MOOD_NAME);
				moodName = (moodName == null) ? "Unknown Mood" : moodName;
				createNotification(moodName);

			} else if (encodedTransientMood != null) {
				Pair<Integer[], Mood> decodedValues = HueUrlEncoder
						.decode(encodedTransientMood);
				Integer[] bulbS = decodedValues.first;
				Mood m = decodedValues.second;

				ArrayList<Integer>[] channels = new ArrayList[m.numChannels];
				for (int i = 0; i < channels.length; i++)
					channels[i] = new ArrayList<Integer>();

				for (int i = 0; i < bulbS.length; i++) {
					channels[i % m.numChannels].add(bulbS[i]);
				}

				for (Event e : m.events) {
					for (Integer bNum : channels[e.channel]) {
						BulbState toModify = transientStateChanges[bNum - 1];
						toModify.merge(e.state);
						if (toModify.toString() != e.state.toString())
							flagTransientChanges[bNum - 1] = true;
					}
				}

			}
		}
		restartCountDownTimer();
		return super.onStartCommand(intent, flags, startId);
	}

	public void restartCountDownTimer() {
		if (countDownTimer != null)
			countDownTimer.cancel();

		// runs at the rate to execute 15 op/sec
		countDownTimer = new CountDownTimer(Integer.MAX_VALUE, (1000 / 15)) {

			@Override
			public void onFinish() {

			}

			@Override
			public void onTick(long millisUntilFinished) {
				Log.e("executor",
						"highPriorityQueue:" + highPriorityQueue.size()
								+ "   queue:" + queue.size());

				if (highPriorityQueue.peek() != null) {
					QueueEvent e = highPriorityQueue.poll();
					NetworkMethods.PreformTransmitGroupMood(getRequestQueue(),
							me, null, e.bulb, e.state);
				} else if (hasTransientChanges()) {
					boolean addedSomethingToQueue = false;
					while (!addedSomethingToQueue) {
						if (flagTransientChanges[transientIndex]) {
							// Note the +1 to account for the 1-based real bulb
							// numbering
							NetworkMethods.PreformTransmitGroupMood(
									getRequestQueue(), me, null,
									transientIndex + 1,
									transientStateChanges[transientIndex]);
							flagTransientChanges[transientIndex] = false;
							addedSomethingToQueue = true;
						}
						transientIndex = (transientIndex + 1) % 50;
					}
				} else if (queue.peek() == null) {
					if (moodPair != null && moodPair.second.isInfiniteLooping()) {
						loadMoodIntoQueue();
					} else {
						createNotification("");

						me.stopSelf();
					}
				} else if (queue.peek().time <= System.nanoTime() / 1000000) {
					ArrayList<QueueEvent> eList = new ArrayList<QueueEvent>();
					eList.add(queue.poll());
					while (queue.peek() != null
							&& queue.peek().compareTo(eList.get(0)) == 0) {
						QueueEvent e = queue.poll();
						eList.add(e);
					}

					for (QueueEvent e : eList)
						highPriorityQueue.add(e);

				}
			}
		};
		countDownTimer.start();

	}

}