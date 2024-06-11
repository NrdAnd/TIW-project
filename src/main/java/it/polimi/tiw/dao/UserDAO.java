package it.polimi.tiw.dao;

import it.polimi.tiw.beans.User;

import java.sql.*;

public class UserDAO {
	
    private final Connection connection;

    public UserDAO(Connection connection){
        this.connection = connection;
    }

    public User checkLogin(String username, String password) throws SQLException {
        String query = "SELECT user_id, email FROM User WHERE username = ? AND password = ?";
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setString(1, username);
        statement.setString(2, password);

        ResultSet result = statement.executeQuery();

        if (!result.isBeforeFirst()) // no results, credential check failed
            return null;

        result.next();
        User utente = new User();

        utente.setUsername(username);
        utente.setPassword(password);
        utente.setUserID(result.getInt("user_id"));
        utente.setEmail(result.getString("email"));

        return utente;
    }

    public boolean checkRegister(String username) throws SQLException {
        String query = "SELECT * FROM User WHERE username = ?";
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setString(1, username);
        
        ResultSet result = statement.executeQuery();

        return result.isBeforeFirst();
    }

    public int registerUser(String email, String password, String username) throws SQLException {
        String query = "INSERT INTO User(username, email, password) VALUES (?, ?, ?)";
        PreparedStatement statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        statement.setString(1, username);
        statement.setString(2, email);
        statement.setString(3, password);

        int code = statement.executeUpdate();
        //System.out.println("CODE:" + code);

        if(code == 0) throw new SQLException("Registration failed, no rows affected");

        int key = -1;
        try(ResultSet generatedKey = statement.getGeneratedKeys()){
            if(generatedKey.next()) {
                key = generatedKey.getInt(1);
              //  System.console().printf("KEY: " + key);
            }
        }

        return key;
    }

    public User getUtenteById(int IDUtente) throws SQLException {
        String query = "SELECT username, email, password FROM User WHERE user_id = ?";
        PreparedStatement statement = connection.prepareStatement(query);

        statement.setInt(1, IDUtente);

        ResultSet result = statement.executeQuery();

        if(!result.isBeforeFirst())
            return null;

        result.next();
        User utente = new User();
        utente.setUserID(IDUtente);
        utente.setUsername(result.getString("username"));
        utente.setEmail(result.getString("email"));
        utente.setPassword(result.getString("password"));

        return utente;
    }

    public User getUtenteByEmail(String email) throws SQLException {
        String query = "SELECT user_id, username, password FROM User WHERE email = ?";
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setString(1, email);

        ResultSet result = statement.executeQuery();

        if(!result.isBeforeFirst())
            return null;

        result.next();
        User utente = new User();
        utente.setEmail(email);
        utente.setUserID(result.getInt("user_id"));
        utente.setUsername(result.getString("username"));
        utente.setPassword(result.getString("password"));

        return utente;
    }
    
    public User getUtenteByUsername(String username) throws SQLException {
        String query = "SELECT user_id, email, password FROM User WHERE username = ?";
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setString(1, username);
 
        ResultSet result = statement.executeQuery();

        if(!result.isBeforeFirst())
            return null;

        result.next();
        User utente = new User();
        utente.setUsername(username);
        utente.setUserID(result.getInt("user_id"));
        utente.setEmail(result.getString("email"));
        utente.setPassword(result.getString("password"));

        return utente;
    }
    
    public boolean emailIsDuplicate(String email) throws SQLException {
    	String query = "SELECT * FROM User WHERE email = ?";
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setString(1, email);
 
        ResultSet resultSet = statement.executeQuery();
        
        while(resultSet.next()) {
        	String queryEmail = resultSet.getString("email");
        	if(queryEmail.equalsIgnoreCase(email)) {
        		return false;
        	}
        }
        return true;
    }
}
