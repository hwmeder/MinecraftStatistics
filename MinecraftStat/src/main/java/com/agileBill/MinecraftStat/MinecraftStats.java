package com.agileBill.MinecraftStat;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import net.canarymod.Canary;
import net.canarymod.api.ConfigurationManager;
import net.canarymod.api.PlayerReference;
import net.canarymod.api.Server;
import net.canarymod.api.entity.living.humanoid.Player;
import net.canarymod.api.statistics.Achievements;
import net.canarymod.api.statistics.Statistics;
import net.canarymod.chat.MessageReceiver;
import net.canarymod.commandsys.Command;
import net.canarymod.commandsys.CommandDependencyException;
import net.canarymod.commandsys.CommandListener;
import net.canarymod.plugin.Plugin;//(2)

import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

/**
 * @author Harold Meder
 *
 *         Minecraft Player Statistics Gathering plugin
 * 
 * 
 *         Following are the commands that were used to deploy the plugin jar.
 *         <ul><li>cd C:/workspace/MinecraftStat/target
 *         </li><li>C:/"Program Files"/Java/jdk1.8.0_20/bin/jar -cf MinecraftStat.jar -C
 *         classes . 
 *         </li><li>cp MinecraftStat.jar C:/server/plugins/
 *         </li></ul>
 */
public class MinecraftStats extends Plugin implements CommandListener {

	static final String GROUP = "GROUP";
	static final String EXPERIENCE = "EXPERIENCE";
	static final String LEVEL = "LEVEL";
	static final String PLAYERNAME = "PLAYERNAME";

	private void helpText() {
		server.broadcastMessage("/savePlayerStats [inputFilePath]");
		server.broadcastMessage("inputFilePath: optionally specify an input file path relative to server directory.");
		server.broadcastMessage("The input file contains an ordered list of statistics to be collected.");
		server.broadcastMessage("The input file contains one statistic name per line.");
	}

	private static final char NEW_LINE = '\n';
	private static final char COMMA_SEPARATOR = ',';

	Server server;
	ConfigurationManager configMgr;
	private String outputFilePath = "stats.csv";
	private String inputFilePath = null;

	public static Logger logger;

	public MinecraftStats() {
		logger = getLogman();
		server = Canary.getServer();
		configMgr = server.getConfigurationManager();
	}

	/**
	 * Constructor to be used by Unit Tests.
	 * 
	 * @param server
	 *            to be used during unit testing
	 * @param logger
	 *            to be used during unit testing
	 * @param configMgr
	 */
	public MinecraftStats(Server server, Logger logger, ConfigurationManager configMgr) {
		MinecraftStats.logger = logger;
		this.server = server;
		this.configMgr = configMgr;
	}

	/**
	 * Constructor to be used by Unit Tests.
	 * 
	 * @param server
	 *            to be used during unit testing
	 * @param logger
	 *            to be used during unit testing
	 * @param configMgr
	 *            to be used during unit testing
	 * @param outputFilePath
	 *            path to the csv file into which the Statistics are to be
	 *            written
	 */
	public MinecraftStats(Server server, Logger logger, ConfigurationManager configMgr, String outputFilePath) {
		MinecraftStats.logger = logger;
		this.server = server;
		this.configMgr = configMgr;
		this.outputFilePath = outputFilePath;
	}

	@Command(aliases = { "savePlayerStats" }, description = "Saves player statistics.", permissions = { "" }, toolTip = "/savePlayerStats")
	public void savePlayerStats(MessageReceiver caller, String[] parameters) {
		boolean requestForHelp = true;
		if (parameters.length < 3) {
			requestForHelp = false;
			if (parameters.length == 2) {
				requestForHelp = "help".equals(parameters[1].toLowerCase());
				if (!requestForHelp) {
					inputFilePath = parameters[1];
				}
			}
		} else {
			server.broadcastMessage("Error: Too many parameters.");
		}
		if (!requestForHelp) {
			try {
				saveStatistics();
			} catch (IOException ioe) {
				logger.error("Error: Unable to read input file " + inputFilePath + ".", ioe);
				server.broadcastMessage("Error: Unable to read input file " + inputFilePath + ".");
				requestForHelp = true;
			}
		}
		if (requestForHelp) {
			helpText();
		}
	}

	void saveStatistics() throws IOException {
		Map<String, Map<String, Object>> playerStatsMap = new TreeMap<String, Map<String, Object>>();
		List<String> statNames = null;
		List<Player> players = configMgr.getAllPlayers();
		Set<String> statNameSet = new TreeSet<String>();
		for (Player player : players) {
			UUID uuid = player.getUUID();
			Map<String, Object> playerProfile = new LinkedHashMap<String, Object>();
			PlayerReference playerReference = server.matchKnownPlayer(uuid);
			getPlayerProfile(playerReference, playerProfile);
			if (statNames == null) {
				statNames = new ArrayList<String>(playerProfile.keySet());
			}
			Map<String, Object> playerStats = new LinkedHashMap<String, Object>();
			getPlayerStats("worlds/stats/" + uuid + ".json", playerStats, statNameSet);
			playerStatsMap.put(playerReference.getName(), playerProfile);
		}
		if (inputFilePath == null) {
			statNames.addAll(statNameSet);
		} else {
			statNames = readListOfDesiredStats(inputFilePath);
		}
		transferStats(playerStatsMap, statNames);
		String outputFileName = (inputFilePath == null ? outputFilePath : "filtered." + outputFilePath);
		Writer writer = null;
		try {
			writer = new FileWriter(outputFileName);
			printStats(writer, playerStatsMap);
			server.broadcastMessage("Player Statistics have been output to file " + outputFileName
					+ " in the server directory.");
		} catch (IOException e) {
			server.broadcastMessage(e.getMessage());
			e.printStackTrace();
		} finally {
			try {
				if (writer != null) {
					writer.flush();
					writer.close();
				}
			} catch (IOException e) {
				System.out.println("Error while flushing/closing fileWriter !!!");
				e.printStackTrace();
			}
		}
	}

	@Override
	public boolean enable() {
		logger.info("Starting up");
		try {
			Canary.commands().registerCommands(this, this, false);
		} catch (CommandDependencyException e) {
			logger.error("Duplicate command name");
		}
		return true;
	}

	@Override
	public void disable() {
	}

	static void getPlayerProfile(PlayerReference pref, Map<String, Object> playerProfile) {
		getPlayerIdentity(pref, playerProfile);
		getPlayerStatistics(pref, playerProfile);
		getPlayerAchievements(pref, playerProfile);
	}

	static void getPlayerIdentity(PlayerReference pref, Map<String, Object> playerProfile) {
		playerProfile.put(PLAYERNAME, pref.getName());
		playerProfile.put(LEVEL, Integer.toString(pref.getLevel()));
		playerProfile.put(EXPERIENCE, Integer.toString(pref.getExperience()));
		playerProfile.put(GROUP, pref.getGroup().getName());
	}

	static void getPlayerAchievements(PlayerReference pref, Map<String, Object> playerProfile) {
		Map<String, Long> playerAchievements = new TreeMap<String, Long>();
		for (Achievements achievement : Achievements.values()) {
			playerAchievements.put(achievement.name(), new Long(pref.hasAchievement(achievement) ? 1 : 0));
		}
		playerProfile.putAll(playerAchievements);
	}

	private static void getPlayerStatistics(PlayerReference pref, Map<String, Object> playerProfile) {
		Map<String, Long> playerStatistics = new TreeMap<String, Long>();
		for (Statistics statistic : Statistics.values()) {
			playerStatistics.put(statistic.name(), new Long(pref.getStat(statistic)));
		}
		playerProfile.putAll(playerStatistics);
	}

	private void printStats(Writer writer, Map<String, Map<String, Object>> playerStatsMap) throws IOException {
		for (Map<String, Object> stats : playerStatsMap.values()) {
			for (String key : stats.keySet()) {
				writer.append(key);
				writer.append(COMMA_SEPARATOR);
			}
			writer.append(NEW_LINE);
			for (Object stat : stats.values()) {
				writer.append(stat.toString());
				writer.append(COMMA_SEPARATOR);
			}
			writer.append(NEW_LINE);
		}
	}

	private void transferStats(Map<String, Map<String, Object>> playerStatsMap, List<String> statNames)
			throws IOException {
		for (Entry<String, Map<String, Object>> entry : playerStatsMap.entrySet()) {
			Map<String, Object> statMap = entry.getValue();
			Map<String, Object> playerStats = new LinkedHashMap<String, Object>();
			for (String stat : statNames) {
				Object value = statMap.get(stat);
				playerStats.put(stat, value == null ? "-" : value);
			}
			playerStatsMap.put(entry.getKey(), playerStats);
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	static void flatten(Object data, String prefix, Map<String, Object> flattenedMap) {
		if (data instanceof Map) {
			for (Entry<String, Object> entry : ((Map<String, Object>) data).entrySet()) {
				flatten(entry.getValue(), prefix + '.' + entry.getKey(), flattenedMap);
			}
		} else if (data instanceof List) {
			Set<Object> orderedList = new TreeSet<Object>((List) data);
			for (Object element : orderedList) {
				flattenedMap.put(prefix + "." + element, 1l);
			}
		} else {
			flattenedMap.put(prefix, data);
		}
	}

	static void getPlayerStats(String jsonFilePath, Map<String, Object> playerProfile, Set<String> statNameSet) {
		Map<String, Object> rawStats = readPlayerStatFile(jsonFilePath);
		Map<String, Object> flattenedMap = new HashMap<String, Object>();
		flatten(rawStats, "", flattenedMap);
		statNameSet.addAll(flattenedMap.keySet());
		playerProfile.putAll(flattenedMap);
	}

	@SuppressWarnings("unchecked")
	static Map<String, Object> readPlayerStatFile(String filePath) {
		JsonFileReader jsonFileReader = new JsonFileReader();
		JSONObject jsonObject = null;
		try {
			jsonObject = jsonFileReader.read(filePath);
		} catch (IOException | ParseException e) {
			e.printStackTrace();
			jsonObject = new JSONObject();
			jsonObject.put("Error", "Unable to Read JSON file.");
			jsonObject.put("Message", e.getMessage());
		}
		return (Map<String, Object>) jsonObject;
	}

	static List<String> readListOfDesiredStats(String inputFilePath) throws IOException {
		List<String> list = new ArrayList<String>();
		BufferedReader in = new BufferedReader(new FileReader(inputFilePath));
		String stat;
		while ((stat = in.readLine()) != null) {
			list.add(stat);
		}
		in.close();
		return list;
	}
}