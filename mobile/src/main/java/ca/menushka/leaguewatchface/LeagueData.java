package ca.menushka.leaguewatchface;

public class LeagueData {

    public static String summoner_info = "https://na.api.pvp.net/api/lol/na/v1.4/summoner/by-name/%s?api_key=%s";
    public static String recent_matches = "https://na.api.pvp.net/api/lol/na/v1.3/game/by-summoner/%d/recent?api_key=%s";
    public static String champion_splash = "http://ddragon.leagueoflegends.com/cdn/img/champion/splash/%s_%d.jpg";
    public static String champion_by_id = "https://global.api.pvp.net/api/lol/static-data/na/v1.2/champion/%d?api_key=%s";
    public static String current_game_id = "https://na.api.pvp.net/observer-mode/rest/consumer/getSpectatorGameInfo/NA1/%d?api_key=%s";

    public static String API_KEY;

    public static String getSummonerInfoUrl(String summonerName) {
        return String.format(summoner_info, summonerName.replaceAll(" ", ""), API_KEY); // 37846796
    }

    public static String getRecentMatchesUrl(int summonerID) {
        return String.format(recent_matches, summonerID, API_KEY);
    }

    public static String getChampionSplashUrl(String championName, int skinID) {
        return String.format(champion_splash, championName, skinID);
    }

    public static String getChampionByIdUrl(int championId) {
        return String.format(champion_by_id, championId, API_KEY);
    }

    public static String getCurrentGameByIdUrl(int championId) {
        return String.format(current_game_id, championId, API_KEY);
    }
}
