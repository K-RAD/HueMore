package com.kuxhausen.huemore.net;

import java.util.ArrayList;
import java.util.HashMap;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.kuxhausen.huemore.net.hue.HubConnection;
import com.kuxhausen.huemore.persistence.DatabaseDefinitions.NetConnectionColumns;
import com.kuxhausen.huemore.state.Group;

public class DeviceManager {

  private ArrayList<Connection> mConnections;
  private Context mContext;
  private Group selectedGroup;
  private String selectedGroupName;
  private ArrayList<OnConnectionStatusChangedListener> connectionListeners =
      new ArrayList<OnConnectionStatusChangedListener>();
  public ArrayList<OnStateChangedListener> brightnessListeners =
      new ArrayList<OnStateChangedListener>();
  public ArrayList<OnStateChangedListener> stateListeners = new ArrayList<OnStateChangedListener>();
  private HashMap<Long, NetworkBulb> bulbMap;
  private MyObserver mConnectionObserver;

  /**
   * true if devicemanager should try to maintain connectivity & sych with devices (ex: app is open)
   */
  private boolean mSycMode;

  public void setSycMode(boolean result) {
    mSycMode = result;
  }

  public boolean getSycMode() {
    return mSycMode;
  }

  public DeviceManager(Context c, boolean sycMode) {
    mContext = c;
    mSycMode = sycMode;

    mConnectionObserver = new MyObserver(new Handler(Looper.getMainLooper()));
    mContext.getContentResolver().registerContentObserver(NetConnectionColumns.URI, true,
        mConnectionObserver);

    loadEverythingFromDatabase();
  }

  public void loadEverythingFromDatabase() {

    // load all connections from the database
    mConnections = new ArrayList<Connection>();
    mConnections.addAll(HubConnection.loadHubConnections(mContext, this));

    // load all network bulbs from the connections
    bulbMap = new HashMap<Long, NetworkBulb>();
    for (Connection con : mConnections) {
      for (NetworkBulb bulb : con.getBulbs()) {
        bulbMap.put(bulb.getBaseId(), bulb);
      }
    }

    onBulbsListChanged();
  }

  public void onDestroy() {
    mContext.getContentResolver().unregisterContentObserver(mConnectionObserver);
    for (Connection c : mConnections)
      c.onDestroy();
  }

  public Group getSelectedGroup() {
    return selectedGroup;
  }

  public String getSelectedGroupName() {
    return selectedGroupName;
  }

  public void onGroupSelected(Group group, Integer optionalBri) {
    selectedGroup = group;
    selectedGroupName = group.getName();

    // TODO
  }

  public ArrayList<Connection> getConnections() {
    return mConnections;
  }

  public void addOnConnectionStatusChangedListener(OnConnectionStatusChangedListener l) {
    connectionListeners.add(l);
  }

  public void removeOnConnectionStatusChangedListener(OnConnectionStatusChangedListener l) {
    connectionListeners.remove(l);
  }

  public void onConnectionChanged() {
    for (OnConnectionStatusChangedListener l : connectionListeners)
      l.onConnectionStatusChanged();
  }

  public interface OnStateChangedListener {
    public void onStateChanged();
  }

  /** announce state changes to any listeners **/
  public void onStateChanged() {
    // only send brightnessListeners brightness state changes
    for (OnStateChangedListener l : brightnessListeners) {
      l.onStateChanged();
    }

    for (OnStateChangedListener l : stateListeners) {
      l.onStateChanged();
    }
  }

  public void registerStateListener(OnStateChangedListener l) {
    stateListeners.add(l);
  }

  public void removeStateListener(OnStateChangedListener l) {
    stateListeners.remove(l);
  }

  public void registerBrightnessListener(OnStateChangedListener l) {
    brightnessListeners.add(l);
    l.onStateChanged();
  }

  public void removeBrightnessListener(OnStateChangedListener l) {
    brightnessListeners.remove(l);
  }

  public Integer getBrightness(Group g) {
    if (g == null)
      return null;
    int briSum = 0;
    int briNum = 0;

    for (Long bulbId : g.getNetworkBulbDatabaseIds()) {
      briSum += bulbMap.get(bulbId).getCurrentMaxBrightness();
      briNum++;
    }

    int bri = 100;
    if (briNum > 0) {
      bri = briSum / briNum;
    }
    return briSum;
  }

  /** doesn't notify listeners **/
  public void setBrightness(Group g, int bri, boolean maxBriMode) {
    if (g == null)
      return;
    for (Long bulbId : g.getNetworkBulbDatabaseIds()) {
      // upon upgrading from v2.7, bulbs may not exist until reconnection
      if (bulbMap.containsKey(bulbId)) {
        bulbMap.get(bulbId).setCurrentMaxBrightness(bri, maxBriMode);
      }
    }
  }

  public void onBulbsListChanged() {
    bulbMap.clear();
    for (Connection con : mConnections) {
      ArrayList<NetworkBulb> conBulbs = con.getBulbs();
      for (NetworkBulb bulb : conBulbs) {
        bulbMap.put(bulb.getBaseId(), bulb);
      }
    }
  }

  public NetworkBulb getNetworkBulb(Long bulbDeviceId) {
    return bulbMap.get(bulbDeviceId);
  }

  class MyObserver extends ContentObserver {
    public MyObserver(Handler handler) {
      super(handler);
    }

    @Override
    public void onChange(boolean selfChange) {
      this.onChange(selfChange, null);
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
      loadEverythingFromDatabase();
    }
  }

  /**
   * Safely deletes references to connection's bulbs & connection, then deletes from database
   * 
   * @param selected connection to delete
   */
  public void delete(Connection selected) {
    // TODO Auto-generated method stub
    for (NetworkBulb nb : selected.getBulbs()) {
      bulbMap.remove(nb.getBaseId());
    }
    this.mConnections.remove(selected);

    selected.delete();
    this.onConnectionChanged();
  }
}