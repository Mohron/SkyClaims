package net.mohron.skyclaims;

import net.mohron.skyclaims.island.Island;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Database {
	private String databaseName;

	public Database(String databaseName) {
		this.databaseName = databaseName;

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
					"owner		STRING PRIMARY KEY," +
					"id			STRING," +
					"x			INT," +
					"y			INT," +
					"z			INT," +
					"world		STRING" +
					")";

			// Create the islands table (execute statement)
			statement.executeUpdate(table);
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
		return DriverManager.getConnection(String.format("jdbc:sqlite:%s", databaseName));
	}

	/**
	 * Creates Island objects and stores them in a DataStore to be loaded into memory
	 *
	 * @return Returns a new DataStore generated from the database data
	 */
	public Map<UUID, Island> loadData() {
		Map<UUID, Island> islands = new HashMap<>();

		try (Statement statement = getConnection().createStatement()) {
			ResultSet results = statement.executeQuery("SELECT * FROM islands");

			while (results.next()) {
				UUID claimId = UUID.fromString(results.getString("id"));
				UUID ownerId = UUID.fromString(results.getString("owner"));
				UUID worldId = UUID.fromString(results.getString("world"));
				int x = results.getInt("x");
				int y = results.getInt("y");

				Island island = new Island(ownerId, worldId, claimId);
				islands.put(ownerId, island);
			}

			return islands;
		} catch (SQLException e) {
			e.printStackTrace();
			SkyClaims.getInstance().getLogger().error("Unable to read from the database.");
		}

		return null;
	}

	/**
	 * Inserts/Updates the database with the data-storage in memory
	 *
	 * @param islands The map in memory to pull the data from
	 */
	public void saveData(Map<UUID, Island> islands) {
		for (Island island : islands.values()) {
			String sql = "INSERT OR UPDATE INTO islands(owner, id, x, y, z, world) VALUES(?, ?, ?, ?, ?, ?)";

			try (PreparedStatement statement = getConnection().prepareStatement(sql)) {
				statement.setString(1, island.getOwner().toString());
				statement.setString(2, island.getClaimId().toString());
				statement.setInt(3, island.getSpawn().getBlockX());
				statement.setInt(4, island.getSpawn().getBlockY());
				statement.setInt(5, island.getSpawn().getBlockZ());
				statement.setString(6, island.getClaim().world.getUniqueId().toString());

				statement.execute();
			} catch (SQLException e) {
				SkyClaims.getInstance().getLogger().error(String.format("Error updating the database: %s", e.getMessage()));
			}
		}
	}

	/**
	 * Saves an individual island to the database
	 *
	 * @param island the island to save
	 */
	public void saveIsland(Island island) {
		String sql = "INSERT OR UPDATE INTO islands(owner, id, x, y, z, world) VALUES(?, ?, ?, ?, ?, ?)";

		try (PreparedStatement statement = getConnection().prepareStatement(sql)) {
			statement.setString(1, island.getOwner().toString());
			statement.setString(2, island.getClaimId().toString());
			statement.setInt(3, island.getSpawn().getBlockX());
			statement.setInt(4, island.getSpawn().getBlockY());
			statement.setInt(5, island.getSpawn().getBlockZ());
			statement.setString(6, island.getClaim().world.getUniqueId().toString());

			statement.execute();
		} catch (SQLException e) {
			SkyClaims.getInstance().getLogger().error(String.format("Error inserting Island into the database: %s", e.getMessage()));
		}
	}
}