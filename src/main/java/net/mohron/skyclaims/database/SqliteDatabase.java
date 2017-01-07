package net.mohron.skyclaims.database;

import com.flowpowered.math.vector.Vector3i;
import net.mohron.skyclaims.SkyClaims;
import net.mohron.skyclaims.config.type.DatabaseConfig;
import net.mohron.skyclaims.config.type.SqliteConfig;
import net.mohron.skyclaims.util.ConfigUtil;
import net.mohron.skyclaims.world.Island;
import org.sqlite.SQLiteConfig;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SqliteDatabase implements IDatabase {
	private SqliteConfig config;
	private String databaseName;
	private String databaseLocation;

	public SqliteDatabase() {
		this.config = ConfigUtil.getSqliteDatabaseConfig();
		this.databaseName = config.databaseName;
		this.databaseLocation = config.location;

		// Load the SQLite JDBC driver
		try {
			Class.forName("org.sqlite.JDBC");
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			SkyClaims.getInstance().getLogger().error("Unable to load the JDBC driver");
		}

		try (Statement statement = getConnection().createStatement()) {
			statement.setQueryTimeout(30);

			// Create the database schema
			String table = "CREATE TABLE IF NOT EXISTS islands (" +
				"island			STRING PRIMARY KEY," +
				"owner			STRING," +
				"claim			STRING," +
				"spawnX			INT," +
				"spawnY			INT," +
				"spawnZ			INT," +
				"locked			BOOLEAN" +
			")";

			// Create the islands table (execute statement)
			statement.executeUpdate(table);

			migrate();
		} catch (SQLException e) {
			e.printStackTrace();
			SkyClaims.getInstance().getLogger().error("Unable to create SkyClaims database");
		}
	}

	/**
	 * Returns a Connection to the database, by name
	 *
	 * @return A Connection object to the database
	 * @throws SQLException Thrown if connection issues are encountered
	 */
	private Connection getConnection() throws SQLException {
		return DriverManager.getConnection(String.format("jdbc:sqlite:%s%s%s.db", databaseLocation, File.separator, databaseName));
	}

	/**
	 * Migrates the database from an old schema to a new one
	 */
	public void migrate() {
		if (countColumns() == 7)
			saveData(loadLegacyData());
	}

	/**
	 * Creates Island objects and stores them in a DataStore to be loaded into memory
	 *
	 * @return Returns a new DataStore generated from the database data
	 */
	public HashMap<UUID, Island> loadData() {
		HashMap<UUID, Island> islands = new HashMap<>();

		try (Statement statement = getConnection().createStatement()) {
			ResultSet results = statement.executeQuery("SELECT * FROM islands");

			while (results.next()) {
				UUID islandId = UUID.fromString(results.getString("island"));
				UUID ownerId = UUID.fromString(results.getString("owner"));
				UUID claimId = UUID.fromString(results.getString("claim"));
				int x = results.getInt("spawnX");
				int y = results.getInt("spawnY");
				int z = results.getInt("spawnZ");
				boolean locked = results.getBoolean("locked");

				Vector3i spawnLocation = new Vector3i(x, y, z);
				Island island = new Island(islandId, ownerId, claimId, spawnLocation, locked);

				islands.put(islandId, island);
			}
		} catch (SQLException e) {
			e.printStackTrace();
			SkyClaims.getInstance().getLogger().error("Unable to read from the database.");
		}

		return islands;
	}

	/**
	 * Load legacy data from the database from the previous schema
	 * @return A hashmap of the ported islands
	 */
	private HashMap<UUID, Island> loadLegacyData() {
		HashMap<UUID, Island> islands = new HashMap<>();

		try (Statement statement = getConnection().createStatement()) {
			ResultSet results = statement.executeQuery("SELECT * FROM islands");

			while (results.next()) {
				UUID ownerId = UUID.fromString(results.getString("owner"));
				UUID claimId = UUID.fromString(results.getString("id"));
				int x = results.getInt("spawnX");
				int y = results.getInt("spawnY");
				int z = results.getInt("spawnZ");

				UUID id = UUID.randomUUID();
				Vector3i spawnLocation = new Vector3i(x, y, z);
				Island island = new Island(id, ownerId, claimId, spawnLocation, false);

				islands.put(id, island);
			}
		} catch (SQLException e) {
			e.printStackTrace();
			SkyClaims.getInstance().getLogger().error("Unable to read from the database.");
		}

		return islands;
	}

	/**
	 * Inserts/Updates the database with the data-storage in memory
	 *
	 * @param islands The map in memory to pull the data from
	 */
	public void saveData(Map<UUID, Island> islands) {
		for (Island island : islands.values())
			saveIsland(island);
	}

	/**
	 * Saves an individual island to the database
	 *
	 * @param island the island to save
	 */
	public void saveIsland(Island island) {
		String sql = "REPLACE INTO islands(island, owner, claim, spawnX, spawnY, spawnZ, locked) VALUES(?, ?, ?, ?, ?, ?)";

		try (PreparedStatement statement = getConnection().prepareStatement(sql)) {
			statement.setString(1, island.getUniqueId().toString());
			statement.setString(2, island.getOwnerUniqueId().toString());
			statement.setString(3, island.getClaim().getUniqueId().toString());
			statement.setInt(4, island.getSpawn().getBlockX());
			statement.setInt(5, island.getSpawn().getBlockY());
			statement.setInt(6, island.getSpawn().getBlockZ());
			statement.setBoolean(7, island.isLocked());

			statement.execute();
		} catch (SQLException e) {
			SkyClaims.getInstance().getLogger().error(String.format("Error inserting Island into the database: %s", e.getMessage()));
		}
	}

	/**
	 * remove an individual island to the database
	 *
	 * @param island the island to remove
	 */
	public void removeIsland(Island island) {
		String sql = "DELETE FROM islands WHERE island = '?'";

		try (PreparedStatement statement = getConnection().prepareStatement(sql)) {
			statement.setString(1, island.getOwnerUniqueId().toString());

			statement.execute();
		} catch (SQLException e) {
			SkyClaims.getInstance().getLogger().error(String.format("Error removing Island from the database: %s", e.getMessage()));
		}
	}

	/**
	 * Count the columns of a row in the database
	 * @return The column count of the schema
	 */
	private int countColumns() {
		int total = 0;

		String sql = "SELECT COUNT(*) AS total FROM islands";
		try (PreparedStatement statement = getConnection().prepareStatement(sql)) {
			return statement.executeQuery().getMetaData().getColumnCount();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return total;
	}
}