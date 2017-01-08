package ca.menushka.leaguewatchface;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.preference.PreferenceManager;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

public class DataListenerService extends WearableListenerService {

    private String USERNAME_TAG = "username";
    private String CHAMPION_OVERRIDE_TAG = "champion_override";

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        InputStream is = getResources().openRawResource(R.raw.key); //Text file with just key
        LeagueData.API_KEY = readTextFile(is);
        try {
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                DataItem item = event.getDataItem();

                GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(this)
                        .addApi(Wearable.API)
                        .build();

                mGoogleApiClient.blockingConnect();

                if (item.getUri().getPath().equals("/league")) {

                    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

                    // Get Summoner Info
                    try {
                        if (pref.getString(CHAMPION_OVERRIDE_TAG, "").equals("")) {
                            String user = pref.getString(USERNAME_TAG, "Faker");
                            JSONObject json = LeagueAPI.makeRequest(LeagueData.getSummonerInfoUrl(user.equals("") ? "Faker" : user));
                            JSONObject summoner = json.getJSONObject(json.keys().next());

                            JSONObject currentGame = LeagueAPI.makeRequest(LeagueData.getCurrentGameByIdUrl((int) summoner.getLong("id")));
                            JSONObject championJson = new JSONObject();

                            if (currentGame == null || currentGame.has("status")) {
                                JSONObject matchesJson = LeagueAPI.makeRequest(LeagueData.getRecentMatchesUrl((int) summoner.getLong("id")));
                                JSONObject matchJson = matchesJson.getJSONArray("games").getJSONObject(0);

                                championJson = LeagueAPI.makeRequest(LeagueData.getChampionByIdUrl(matchJson.getInt("championId")));
                            } else {
                                JSONArray participants = currentGame.getJSONArray("participants");
                                for (int i = 0; i < participants.length(); i++) {
                                    JSONObject participant = participants.getJSONObject(i);
                                    if (participant.getString("summonerName").equals(user)) {
                                        championJson = LeagueAPI.makeRequest(LeagueData.getChampionByIdUrl(participant.getInt("championId")));
                                        break;
                                    }
                                }
                            }

                            Bitmap bitmap = BitmapFactory.decodeStream(new URL(LeagueData.getChampionSplashUrl(championJson.getString("name"), 0)).openConnection().getInputStream());
                            Asset asset = createAssetFromBitmap(bitmap);

                            PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/league_back");
                            putDataMapReq.getDataMap().putAsset("image", asset);
                            putDataMapReq.getDataMap().putString("text", user);
                            putDataMapReq.getDataMap().putLong("time", System.currentTimeMillis());
                            PutDataRequest request = putDataMapReq.asPutDataRequest();
                            Wearable.DataApi.putDataItem(mGoogleApiClient, request);
                        } else {
                            Bitmap bitmap = BitmapFactory.decodeStream(new URL(LeagueData.getChampionSplashUrl(pref.getString(CHAMPION_OVERRIDE_TAG, ""),  0)).openConnection().getInputStream());
                            Asset asset = createAssetFromBitmap(bitmap);

                            PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/league_back");
                            putDataMapReq.getDataMap().putAsset("image", asset);
                            putDataMapReq.getDataMap().putString("text", pref.getString(CHAMPION_OVERRIDE_TAG, ""));
                            putDataMapReq.getDataMap().putLong("time", System.currentTimeMillis());
                            PutDataRequest request = putDataMapReq.asPutDataRequest();
                            Wearable.DataApi.putDataItem(mGoogleApiClient, request);
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                mGoogleApiClient.disconnect();
            }
        }
    }

    private static Asset createAssetFromBitmap(Bitmap bitmap) {
        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
        return Asset.createFromBytes(byteStream.toByteArray());
    }

    public String readTextFile(InputStream inputStream) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        byte buf[] = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                outputStream.write(buf, 0, len);
            }
            outputStream.close();
            inputStream.close();
        } catch (IOException e) {

        }
        return outputStream.toString();
    }
}
