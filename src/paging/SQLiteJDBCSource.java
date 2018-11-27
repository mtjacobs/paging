package paging;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SQLiteJDBCSource {

	private Connection connection;
	private ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
	
	public SQLiteJDBCSource() {
		try {
			Class.forName("org.sqlite.JDBC");
			connection = DriverManager.getConnection("jdbc:sqlite:" + new File("paging.sqlite").getAbsolutePath());
			connection.setAutoCommit(true);
			Statement s = connection.createStatement();
			s.execute("CREATE TABLE if not exists messages (direction integer, sender text, recipients text, message text, timestamp integer)");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void execute(String sql, String[] parameters) {
		Lock lock = readWriteLock.writeLock();
		lock.lock();
		try {
			PreparedStatement ps = connection.prepareStatement(sql);
			if(parameters != null) {
				for(int i = 0; i < parameters.length; i++) {
					ps.setString(i+1,  parameters[i]);
				}
			}
			ps.execute();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			lock.unlock();
		}
	}
	
	public void executeInTransaction(String[][] sqlWithParameters) {
		Lock lock = readWriteLock.writeLock();
		lock.lock();
		try {
			try {
				connection.setAutoCommit(false);
				for(int i = 0; i < sqlWithParameters.length; i++) {
					PreparedStatement ps = connection.prepareStatement(sqlWithParameters[i][0]);
					for(int j = 1; j < sqlWithParameters[i].length; j++) {
						ps.setString(j,  sqlWithParameters[i][j]);
					}
					ps.execute();
				}
				connection.commit();
			} catch (Exception e) {
				e.printStackTrace();
				connection.rollback();
			} finally {
				connection.setAutoCommit(true);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			lock.unlock();
		}
	}

	public String[][] query(String sql, String[] parameters) {
		List<String[]> results = new ArrayList<String[]>();
		Lock lock = readWriteLock.readLock();
		lock.lock();
		try {
			PreparedStatement ps = connection.prepareStatement(sql);
			if(parameters != null) {
				for(int i = 0; i < parameters.length; i++) {
					ps.setString(i+1,  parameters[i]);
				}
			}
			ResultSet rs = ps.executeQuery();
			int columns = rs.getMetaData().getColumnCount();
			while(rs.next()) {
				String[] result = new String[columns];
				for(int i = 0; i < result.length; i++) result[i] = rs.getString(i+1);
				results.add(result);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			lock.unlock();
		}
		return results.toArray(new String[results.size()][]);
	}
	
}
