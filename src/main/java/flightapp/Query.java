package flightapp;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.security.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import javax.xml.transform.Result;

/**
 * Runs queries against a back-end database
 */
public class Query {
  // DB Connection
  private Connection conn;

  // Password hashing parameter constants
  private static final int HASH_STRENGTH = 65536;
  private static final int KEY_LENGTH = 128;

  // Canned queries
  private static final String CHECK_FLIGHT_CAPACITY = "SELECT capacity FROM Flights WHERE fid = ?";
  private PreparedStatement checkFlightCapacityStatement;

  // For check dangling
  private static final String TRANCOUNT_SQL = "SELECT @@TRANCOUNT AS tran_count";
  private PreparedStatement tranCountStatement;

  private static final String CLEAR_TABLES = "DELETE FROM Store_Rid; DELETE FROM Reservations; DELETE FROM Users;";
  private PreparedStatement clearTablesStatement;

  private static final String CHECK_USERNAME_UNIQUENESS = "SELECT COUNT(*) FROM Users WHERE uname = ?";
  private PreparedStatement checkUsernameStatement;

  private static final String CREATE_NEW_USER = "INSERT INTO Users VALUES (?, ?, ?, ?)";
  private PreparedStatement createNewUserStatement;

  private static final String USER_SALT = "SELECT U.salt FROM Users AS U WHERE U.uname = ?";
  private PreparedStatement userSaltStatement;

  private static final String USER_LOG_IN =
          "SELECT COUNT(*) AS count FROM Users AS U WHERE U.uname = ? AND U.hashCode = ?";
  private PreparedStatement userLogInStatement;

  private static final String ONE_HOP_FLIGHT = "SELECT TOP (" + "?"
          + ") day_of_month,fid,carrier_id,flight_num,origin_city,dest_city,actual_time,capacity,price"
          + " FROM Flights WHERE origin_city = ? AND dest_city = ? AND day_of_month = ? AND canceled = 0"
          + " ORDER BY actual_time, fid ASC";
  private PreparedStatement oneHopFlightStatement;

  private static final String TWO_HOPS_FLIGHTS = "SELECT DISTINCT TOP (?)"
          + "F1.day_of_month dom1,F1.fid fid1,F1.carrier_id cid1,F1.flight_num fn1,F1.origin_city oc1,"
          + "F1.dest_city dc1,F1.actual_time at1, F1.actual_time + F2.actual_time AS total_time,"
          + "F1.capacity c1,F1.price p1,F2.day_of_month dom2,F2.fid fid2,F2.carrier_id cid2,F2.flight_num fn2,"
          + "F2.origin_city oc2,"
          + "F2.dest_city dc2,F2.actual_time at2,F2.capacity c2,F2.price p2"
          + " FROM Flights AS F1, Flights AS F2"
          + " WHERE F1.origin_city = ? AND F1.dest_city != ?"
          + " AND F1.day_of_month = ? AND F2.day_of_month = ?"
          + " AND F2.dest_city = ? AND F1.dest_city = F2.origin_city"
          + " AND F1.canceled = 0 AND F2.canceled = 0 ORDER BY F1.actual_time + F2.actual_time,f1.fid";
  private PreparedStatement twoHopsFlightsStatement;

  private static final String CHECK_DUPLICATE_RESERVATION = "SELECT COUNT(*) AS count"
          + " FROM Reservations AS R"
          + " WHERE R.uname = ? AND R.flight_date = ? AND R.canceled = 0";
  private PreparedStatement checkDuplicateReservationStatement;

  private static final String CHECK_SEATS_AVAILABILITY = "SELECT COUNT(*) AS count"
          + " FROM Reservations AS R " + "WHERE R.fid1 = ? AND canceled = 0";
  private PreparedStatement checkSeatsAvailabilityStatement;

  private static final String CHECK_SEATS_AVAILABILITY_SECOND_FLIGHT = "SELECT COUNT(*) AS count"
          + " FROM Reservations AS R " + "WHERE R.fid2 = ? AND canceled = 0";
  private PreparedStatement checkSeatsAvailabilitySecondFlightStatement;

  private static final String CHECK_UNPAID_RESERVATION = "SELECT COUNT(*) AS count"
          + " FROM Reservations AS R"
          + " WHERE R.rid = ? AND R.uname = ? AND R.paid_status = 0";
  private PreparedStatement checkReservationStatement;

  private static final String CHECK_ALL_RESERVATION = "SELECT COUNT(*) AS count"
          + " FROM Reservations AS R"
          + " WHERE R.uname = ? AND R.canceled = 0";
  private PreparedStatement checkAllReservationStatement;

  private static final String GET_RESERVATION = "SELECT R.rid AS rid, R.paid_status AS paid_status,"
          + " R.fid1 AS fid1, R.fid2 AS fid2, R.stopover AS stopover"
          + " FROM Reservations AS R"
          + " WHERE R.uname = ? AND R.canceled = 0";
  private PreparedStatement getReservationStatement;

  private static final String GET_STORED_RID = "SELECT rid FROM Store_rid";
  private PreparedStatement getStoredRidStatement;

  private static final String ADD_RESERVATION = "INSERT INTO Reservations VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
  private PreparedStatement addReservationStatement;

  private static final String GET_FLIGHT_INFO = "SELECT day_of_month, carrier_id, origin_city, dest_city,"
          + " actual_time, flight_num, capacity, price FROM Flights where fid = ?";
  private PreparedStatement getFlightInfoStatement;

  private static final String CHECK_USER_BALANCE = "SELECT U.balance AS balance"
          + " FROM Users AS U"
          + " WHERE U.uname = ?";
  private PreparedStatement checkUserBalanceStatement;

  private static final String TICKET_PRICE = "SELECT R.total_price"
          + " FROM Reservations AS R"
          + " WHERE R.rid = ?";
  private PreparedStatement ticketPriceStatement;

  private static final String UPDATE_BALANCE = "UPDATE Users SET balance = ? WHERE uname = ?";
  private PreparedStatement updateBalanceStatement;

  private static final String UPDATE_RESERVATION_STATUS = "UPDATE Reservations SET paid_status = ? WHERE rid = ?";
  private PreparedStatement updateReservationStatusStatement;

  private static final String CANCEL_RESERVATION = "UPDATE Reservations"
          + " SET canceled = 1"
          + " WHERE uname = ? AND rid = ?";
  private PreparedStatement cancelReservationStatement;

  private static final String CHECK_PAID_STATUS = "SELECT R.paid_status"
          + " FROM Reservations AS R"
          + " WHERE R.rid = ?";
  private PreparedStatement checkPaidStatusStatement;

  private static final String CHECK_EXISTING_RESERVATION = "SELECT COUNT(*) AS count"
          + " FROM Reservations AS R"
          + " WHERE R.rid = ? AND R.uname = ? AND R.canceled = 0";
  private PreparedStatement checkExistingReservationStatement;

  private static final String UPDATE_STORE_RID = "UPDATE Store_Rid SET rid = ?";
  private PreparedStatement addRidNumStatement;

  private static final String INSERT_FIRST_RID = "INSERT INTO Store_Rid VALUES (1)";
  private PreparedStatement insertFirstRidStatement;

  private String username;

  private ArrayList<SearchResult> searchedItineraries;

  private int itineraryNum = 0;

  public Query() throws SQLException, IOException {
    this(null, null, null, null);
  }

  protected Query(String serverURL, String dbName, String adminName, String password)
          throws SQLException, IOException {
    conn = serverURL == null ? openConnectionFromDbConn()
            : openConnectionFromCredential(serverURL, dbName, adminName, password);

    prepareStatements();
  }

  /**
   * Return a connecion by using dbconn.properties file
   *
   * @throws SQLException
   * @throws IOException
   */
  public static Connection openConnectionFromDbConn() throws SQLException, IOException {
    // Connect to the database with the provided connection configuration
    Properties configProps = new Properties();
    configProps.load(new FileInputStream("dbconn.properties"));
    String serverURL = configProps.getProperty("flightapp.server_url");
    String dbName = configProps.getProperty("flightapp.database_name");
    String adminName = configProps.getProperty("flightapp.username");
    String password = configProps.getProperty("flightapp.password");
    return openConnectionFromCredential(serverURL, dbName, adminName, password);
  }

  /**
   * Return a connecion by using the provided parameter.
   *
   * @param serverURL example: example.database.widows.net
   * @param dbName    database name
   * @param adminName username to login server
   * @param password  password to login server
   *
   * @throws SQLException
   */
  protected static Connection openConnectionFromCredential(String serverURL, String dbName,
                                                           String adminName, String password) throws SQLException {
    String connectionUrl =
            String.format("jdbc:sqlserver://%s:1433;databaseName=%s;user=%s;password=%s", serverURL,
                    dbName, adminName, password);
    Connection conn = DriverManager.getConnection(connectionUrl);

    // By default, automatically commit after each statement
    conn.setAutoCommit(true);

    // By default, set the transaction isolation level to serializable
    conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

    return conn;
  }

  /**
   * Get underlying connection
   */
  public Connection getConnection() {
    return conn;
  }

  /**
   * Closes the application-to-database connection
   */
  public void closeConnection() throws SQLException {
    conn.close();
  }

  /**
   * Clear the data in any custom tables created.
   *
   * WARNING! Do not drop any tables and do not clear the flights table.
   */
  public void clearTables() {
    try {
      clearTablesStatement.execute();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /*
   * prepare all the SQL statements in this method.
   */
  private void prepareStatements() throws SQLException {
    checkFlightCapacityStatement = conn.prepareStatement(CHECK_FLIGHT_CAPACITY);
    tranCountStatement = conn.prepareStatement(TRANCOUNT_SQL);
    clearTablesStatement = conn.prepareStatement(CLEAR_TABLES);
    checkUsernameStatement = conn.prepareStatement(CHECK_USERNAME_UNIQUENESS);
    createNewUserStatement = conn.prepareStatement(CREATE_NEW_USER);
    userSaltStatement = conn.prepareStatement(USER_SALT);
    userLogInStatement = conn.prepareStatement(USER_LOG_IN);
    oneHopFlightStatement = conn.prepareStatement(ONE_HOP_FLIGHT);
    twoHopsFlightsStatement = conn.prepareStatement(TWO_HOPS_FLIGHTS);
    checkDuplicateReservationStatement = conn.prepareStatement(CHECK_DUPLICATE_RESERVATION);
    checkSeatsAvailabilityStatement = conn.prepareStatement(CHECK_SEATS_AVAILABILITY);
    checkReservationStatement = conn.prepareStatement(CHECK_UNPAID_RESERVATION);
    getReservationStatement = conn.prepareStatement(GET_RESERVATION);
    checkSeatsAvailabilitySecondFlightStatement = conn.prepareStatement(CHECK_SEATS_AVAILABILITY_SECOND_FLIGHT);
    getStoredRidStatement = conn.prepareStatement(GET_STORED_RID);
    addReservationStatement = conn.prepareStatement(ADD_RESERVATION);
    checkUserBalanceStatement = conn.prepareStatement(CHECK_USER_BALANCE);
    ticketPriceStatement = conn.prepareStatement(TICKET_PRICE);
    updateBalanceStatement = conn.prepareStatement(UPDATE_BALANCE);
    updateReservationStatusStatement = conn.prepareStatement(UPDATE_RESERVATION_STATUS);
    checkAllReservationStatement = conn.prepareStatement(CHECK_ALL_RESERVATION);
    cancelReservationStatement = conn.prepareStatement(CANCEL_RESERVATION);
    checkPaidStatusStatement = conn.prepareStatement(CHECK_PAID_STATUS);
    getFlightInfoStatement = conn.prepareStatement(GET_FLIGHT_INFO);
    checkExistingReservationStatement = conn.prepareStatement(CHECK_EXISTING_RESERVATION);
    addRidNumStatement = conn.prepareStatement(UPDATE_STORE_RID);
    insertFirstRidStatement = conn.prepareStatement(INSERT_FIRST_RID);
  }

  /**
   * Takes a user's username and password and attempts to log the user in.
   *
   * @param username user's username
   * @param password user's password
   *
   * @return If someone has already logged in, then return "User already logged in\n" For all other
   *         errors, return "Login failed\n". Otherwise, return "Logged in as [username]\n".
   */
  public String transaction_login(String username, String password) {
    if (this.username != null) {
      return "User already logged in\n";
    }
    try {
      userSaltStatement.clearParameters();
      userSaltStatement.setString(1, username);
      ResultSet saltResult = userSaltStatement.executeQuery();
      if (!saltResult.next()) {
        saltResult.close();
        return "Login failed\n";
      } else {
        byte[] salt = saltResult.getBytes("salt");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, HASH_STRENGTH, KEY_LENGTH);
        // Generate the hash
        SecretKeyFactory factory = null;
        byte[] hash = null;
        try {
          factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
          hash = factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
          throw new IllegalStateException();
        }

        userLogInStatement.clearParameters();
        userLogInStatement.setString(1, username);
        userLogInStatement.setBytes(2, hash);
        ResultSet correctPairs = userLogInStatement.executeQuery();
        correctPairs.next();
        this.username = username;
        if (correctPairs.getInt("count") == 1) {
          return "Logged in as " + username + "\n";
        }
        correctPairs.close();
      }
      return "Login failed\n";
    } catch (SQLException e){
      e.printStackTrace();
      return "Login failed\n";
    } finally {
      checkDanglingTransaction();
    }
  }

  /**
   * Implement the create user function.
   *
   * @param username   new user's username. User names are unique the system.
   * @param password   new user's password.
   * @param initAmount initial amount to deposit into the user's account, should be >= 0 (failure
   *                   otherwise).
   *
   * @return either "Created user {@code username}\n" or "Failed to create user\n" if failed.
   */
  public String transaction_createCustomer(String username, String password, int initAmount) {
    try {
      conn.setAutoCommit(false);
      if (initAmount < 0) {
        return "Failed to create user\n";
      } else {
        // Generate a random cryptographic salt
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);

        // Specify the hash parameters
        KeySpec spec= new PBEKeySpec(password.toCharArray(), salt, HASH_STRENGTH, KEY_LENGTH);

        // Generate the hash
        SecretKeyFactory factory = null;
        byte[] hash = null;
        try {
          factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
          hash = factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
          throw new IllegalStateException();
        }
        try {
          createNewUserStatement.clearParameters();
          createNewUserStatement.setString(1, username);
          createNewUserStatement.setBytes(2, salt);
          createNewUserStatement.setBytes(3, hash);
          createNewUserStatement.setInt(4, initAmount);
          createNewUserStatement.executeUpdate();
          conn.commit();
          conn.setAutoCommit(true);
          return "Created user " + username + "\n";
        } catch (SQLException deadlock) {
          conn.rollback();
          conn.setAutoCommit(true);
          return "Failed to create user\n";
        }
      }
    } catch (SQLException e) {
      try {
        conn.rollback();
        conn.setAutoCommit(true);
        e.printStackTrace();
        return "Failed to create user\n";
      } catch (SQLException e1) {
        return "Failed to create user\n";
      }
    }finally {
      checkDanglingTransaction();
    }
  }

  private int checkFlightCapacity(int fid) throws SQLException {
    checkFlightCapacityStatement.clearParameters();
    checkFlightCapacityStatement.setInt(1, fid);
    ResultSet results = checkFlightCapacityStatement.executeQuery();
    results.next();
    int capacity = results.getInt("capacity");
    results.close();

    return capacity;
  }

  /**
   * Implement the search function.
   *
   * Searches for flights from the given origin city to the given destination city, on the given day
   * of the month. If {@code directFlight} is true, it only searches for direct flights, otherwise
   * is searches for direct flights and flights with two "hops." Only searches for up to the number
   * of itineraries given by {@code numberOfItineraries}.
   *
   * The results are sorted based on total flight time.
   *
   * @param originCity
   * @param destinationCity
   * @param directFlight        if true, then only search for direct flights, otherwise include
   *                            indirect flights as well
   * @param dayOfMonth
   * @param numberOfItineraries number of itineraries to return
   *
   * @return If no itineraries were found, return "No flights match your selection\n". If an error
   *         occurs, then return "Failed to search\n".
   *
   *         Otherwise, the sorted itineraries printed in the following format:
   *
   *         Itinerary [itinerary number]: [number of flights] flight(s), [total flight time]
   *         minutes\n [first flight in itinerary]\n ... [last flight in itinerary]\n
   *
   *         Each flight should be printed using the same format as in the {@code Flight} class.
   *         Itinerary numbers in each search should always start from 0 and increase by 1.
   *
   * @see Flight#toString()
   */
  public String transaction_search(String originCity, String destinationCity, boolean directFlight,
                                   int dayOfMonth, int numberOfItineraries) {
    try {
      if (searchedItineraries == null) {
        searchedItineraries = new ArrayList<>();
      } else {
        searchedItineraries.clear();
      }
      StringBuffer sb = new StringBuffer();
      itineraryNum = 0;
      int directIndex = 0;
      int indirectIndex = 0;
      try {
        // one hop itineraries
        oneHopFlightStatement.clearParameters();
        oneHopFlightStatement.setInt(1, numberOfItineraries);
        oneHopFlightStatement.setString(2, originCity);
        oneHopFlightStatement.setString(3, destinationCity);
        oneHopFlightStatement.setInt(4, dayOfMonth);

        ResultSet oneHopResults = oneHopFlightStatement.executeQuery();

        ArrayList<Flight> directList = new ArrayList<>();

        while (oneHopResults.next()) {
          Flight currFlight = new Flight();
          currFlight.fid = oneHopResults.getInt("fid");
          currFlight.dayOfMonth = oneHopResults.getInt("day_of_month");
          currFlight.carrierId = oneHopResults.getString("carrier_id");
          currFlight.flightNum = oneHopResults.getString("flight_num");
          currFlight.originCity = oneHopResults.getString("origin_city");
          currFlight.destCity = oneHopResults.getString("dest_city");
          currFlight.time = oneHopResults.getInt("actual_time");
          currFlight.capacity = oneHopResults.getInt("capacity");
          currFlight.price = oneHopResults.getInt("price");
          directList.add(currFlight);
        }
        oneHopResults.close();
        int restSize = numberOfItineraries - directList.size();
        if (!directFlight && restSize > 0) {// two hops itineraries
          twoHopsFlightsStatement.clearParameters();
          twoHopsFlightsStatement.setInt(1, restSize);
          twoHopsFlightsStatement.setString(2, originCity);
          twoHopsFlightsStatement.setString(3, destinationCity);
          twoHopsFlightsStatement.setInt(4, dayOfMonth);
          twoHopsFlightsStatement.setInt(5, dayOfMonth);
          twoHopsFlightsStatement.setString(6, destinationCity);
          ResultSet twoHopsResults = twoHopsFlightsStatement.executeQuery();

          ArrayList<IndirectFlights> inDirectList = new ArrayList<>();

          while (twoHopsResults.next()) {
            IndirectFlights currFlight = new IndirectFlights();
            currFlight.fid1 = twoHopsResults.getInt("fid1");
            currFlight.fid2 = twoHopsResults.getInt("fid2");
            currFlight.dayOfMonth1 = twoHopsResults.getInt("dom1");
            currFlight.dayOfMonth2 = twoHopsResults.getInt("dom2");
            currFlight.carrierId1 = twoHopsResults.getString("cid1");
            currFlight.carrierId2 = twoHopsResults.getString("cid2");
            currFlight.flightNum1 = twoHopsResults.getString("fn1");
            currFlight.flightNum2 = twoHopsResults.getString("fn2");
            currFlight.originCity1 = twoHopsResults.getString("oc1");
            currFlight.originCity2 = twoHopsResults.getString("oc2");
            currFlight.destCity1 = twoHopsResults.getString("dc1");
            currFlight.destCity2 = twoHopsResults.getString("dc2");
            currFlight.time1 = twoHopsResults.getInt("at1");
            currFlight.time2 = twoHopsResults.getInt("at2");
            currFlight.capacity1 = twoHopsResults.getInt("c1");
            currFlight.capacity2 = twoHopsResults.getInt("c2");
            currFlight.price1 = twoHopsResults.getInt("p1");
            currFlight.price2 = twoHopsResults.getInt("p2");
            currFlight.totalTime = currFlight.time1 + currFlight.time2;
            inDirectList.add(currFlight);
          }
          twoHopsResults.close();

          if (directList.size() == 0 && inDirectList.size() == 0) {
            return "No flights match your selection\n";
          }

          for (int i = 0; i < numberOfItineraries; i++) {
            if (directIndex >= directList.size() && indirectIndex >= inDirectList.size()) {
              break;
            } else if (indirectIndex >= inDirectList.size()) {
              sb.append("Itinerary " + itineraryNum + ": " + "1 flight(s), " + directList.get(directIndex).time
                      + " minutes\n" + directList.get(directIndex).toString() + '\n');

              searchedItineraries.add(new SearchResult(true, directList.get(directIndex).dayOfMonth,
                      itineraryNum, directList.get(directIndex), null));
              itineraryNum++;
              directIndex++;
            } else if (directIndex >= directList.size()) {
              sb.append("Itinerary " + itineraryNum + ": " + "2 flight(s), "
                      + inDirectList.get(indirectIndex).totalTime + " minutes\n"
                      + inDirectList.get(indirectIndex).toString() + '\n');
              searchedItineraries.add(new SearchResult(false, inDirectList.get(indirectIndex).dayOfMonth1,
                      itineraryNum, null, inDirectList.get(indirectIndex)));
              itineraryNum++;
              indirectIndex++;
            } else {
              if (directList.get(directIndex).time < inDirectList.get(indirectIndex).totalTime) {
                sb.append("Itinerary " + itineraryNum + ": " + "1 flight(s), " + directList.get(directIndex).time
                        + " minutes\n" + directList.get(directIndex).toString() + '\n');
                searchedItineraries.add(new SearchResult(true, directList.get(directIndex).dayOfMonth,
                        itineraryNum, directList.get(directIndex), null));
                itineraryNum++;
                directIndex++;
              } else {
                sb.append("Itinerary " + itineraryNum + ": " + "2 flight(s), "
                        + inDirectList.get(indirectIndex).totalTime + " minutes\n"
                        + inDirectList.get(indirectIndex).toString() + '\n');
                searchedItineraries.add(new SearchResult(false, inDirectList.get(indirectIndex).dayOfMonth1,
                        itineraryNum, null, inDirectList.get(indirectIndex)));
                itineraryNum++;
                indirectIndex++;
              }
            }
          }

        } else { // direct flights only
          if (directList.size() == 0) {
            return "No flights match your selection\n";
          }
          for (int i = 0; i < Math.min(numberOfItineraries, directList.size()); i++) {
            sb.append("Itinerary " + itineraryNum + ": " + "1 flight(s), " + directList.get(directIndex).time
                    + " minutes\n" + directList.get(directIndex).toString() + '\n');
            searchedItineraries.add(new SearchResult(true, directList.get(directIndex).dayOfMonth,
                    itineraryNum, directList.get(directIndex), null));
            itineraryNum++;
            directIndex++;
          }
        }
      } catch (SQLException e) {
        e.printStackTrace();
        return "Failed to search\n";
      }
      return sb.toString();
    } finally {
      checkDanglingTransaction();
    }
  }

  /**
   * Implements the book itinerary function.
   *
   * @param itineraryId ID of the itinerary to book. This must be one that is returned by search in
   *                    the current session.
   *
   * @return If the user is not logged in, then return "Cannot book reservations, not logged in\n".
   *         If the user is trying to book an itinerary with an invalid ID or without having done a
   *         search, then return "No such itinerary {@code itineraryId}\n". If the user already has
   *         a reservation on the same day as the one that they are trying to book now, then return
   *         "You cannot book two flights in the same day\n". For all other errors, return "Booking
   *         failed\n".
   *
   *         And if booking succeeded, return "Booked flight(s), reservation ID: [reservationId]\n"
   *         where reservationId is a unique number in the reservation system that starts from 1 and
   *         increments by 1 each time a successful reservation is made by any user in the system.
   */
  public String transaction_book(int itineraryId) {
    // check for not login
    if (this.username == null) {
      return "Cannot book reservations, not logged in\n";
    }
    if (itineraryId < 0 || searchedItineraries == null || itineraryNum <= 0) {
      return "No such itinerary " + itineraryId + "\n";
    }

    try {
      // check for duplicate reservation
      conn.setAutoCommit(false);
      SearchResult searchIID = searchedItineraries.get(itineraryId);
      checkDuplicateReservationStatement.clearParameters();
      checkDuplicateReservationStatement.setString(1, this.username);
      checkDuplicateReservationStatement.setInt(2, searchIID.flightDate);
      ResultSet result = checkDuplicateReservationStatement.executeQuery();
      result.next();
      if (result.getInt("count") == 1) {
        result.close();
        conn.rollback();
        conn.setAutoCommit(true);
        return "You cannot book two flights in the same day\n";
      }
      result.close();

      // book reservation
      if (searchIID.direct) { // direct flight
        // check seat capacity
        int Capacity = searchIID.directFlightInfo.capacity;
        int fid = searchIID.directFlightInfo.fid;
        checkSeatsAvailabilityStatement.clearParameters();
        checkSeatsAvailabilityStatement.setInt(1, fid);
        ResultSet result2 = checkSeatsAvailabilityStatement.executeQuery();
        result2.next();
        int cap1 = result2.getInt("count");
        result2.close();
        checkSeatsAvailabilitySecondFlightStatement.clearParameters();
        checkSeatsAvailabilitySecondFlightStatement.setInt(1, fid);
        ResultSet result3 = checkSeatsAvailabilitySecondFlightStatement.executeQuery();
        result3.next();
        cap1 += result3.getInt("count");
        if (Capacity - cap1 >= 1) { // not exceed seat capacity
          // get current reservation number
          int currentRid = 0;
          ResultSet ridNum = getStoredRidStatement.executeQuery();
          if (ridNum.next()) {
            currentRid = ridNum.getInt("rid");
            ridNum.close();
          } else {
            ridNum.close();
            insertFirstRidStatement.executeUpdate();
            currentRid = 1;
          }
          addRidNumStatement.clearParameters();
          addRidNumStatement.setInt(1, currentRid + 1);
          addRidNumStatement.executeUpdate();
          // update reservation table
          addReservationStatement.clearParameters();
          addReservationStatement.setInt(1, currentRid);
          addReservationStatement.setString(2, this.username);
          addReservationStatement.setInt(3, fid);
          addReservationStatement.setInt(4, -1); // -1 = flight not exist
          addReservationStatement.setInt(5, 0); // 0 = no stopover
          addReservationStatement.setInt(6, 0); // 0 = unpaid
          addReservationStatement.setInt(7, searchIID.directFlightInfo.price);
          addReservationStatement.setInt(8, 0); // 0 = not cancelled
          addReservationStatement.setInt(9, searchIID.flightDate);
          addReservationStatement.executeUpdate();
          conn.commit();
          conn.setAutoCommit(true);
          return "Booked flight(s), reservation ID: " + currentRid + "\n";
        } else { // exceeds seat capacity
          conn.rollback();
          conn.setAutoCommit(true);
          return "Booking failed\n";
        }
      } else { // indirect flight
        int capacity1 = searchIID.indirectFlightsInfo.capacity1;
        int capacity2 = searchIID.indirectFlightsInfo.capacity2;
        int fid1 = searchIID.indirectFlightsInfo.fid1;
        int fid2 = searchIID.indirectFlightsInfo.fid2;
        checkSeatsAvailabilityStatement.clearParameters();
        checkSeatsAvailabilityStatement.setInt(1, fid1);
        ResultSet result2 = checkSeatsAvailabilityStatement.executeQuery();
        result2.next();
        int cap1 = result2.getInt("count");
        result2.close();
        checkSeatsAvailabilitySecondFlightStatement.clearParameters();
        checkSeatsAvailabilitySecondFlightStatement.setInt(1, fid1);
        result2 = checkSeatsAvailabilitySecondFlightStatement.executeQuery();
        result2.next();
        cap1 += result2.getInt("count");
        checkSeatsAvailabilityStatement.clearParameters();
        checkSeatsAvailabilityStatement.setInt(1, fid2);
        result2 = checkSeatsAvailabilityStatement.executeQuery();
        result2.next();
        int cap2 = result2.getInt("count");
        result2.close();
        checkSeatsAvailabilitySecondFlightStatement.clearParameters();
        checkSeatsAvailabilitySecondFlightStatement.setInt(1, fid2);
        result2 = checkSeatsAvailabilitySecondFlightStatement.executeQuery();
        result2.next();
        cap2 += result2.getInt("count");
        if (capacity1 - cap1 >= 1 && capacity2 - cap2 >= 1) {
          // get currID
          int currentRid = 0;
          ResultSet ridNum = getStoredRidStatement.executeQuery();
          if (ridNum.next()) {
            currentRid = ridNum.getInt("rid");
            ridNum.close();
          } else {
            ridNum.close();
            insertFirstRidStatement.executeUpdate();
            currentRid = 1;
          }
          addRidNumStatement.clearParameters();
          addRidNumStatement.setInt(1, currentRid + 1);
          addRidNumStatement.executeUpdate();
          // update reservation table
          addReservationStatement.clearParameters();
          addReservationStatement.setInt(1, currentRid);
          addReservationStatement.setString(2, username);
          addReservationStatement.setInt(3, fid1);
          addReservationStatement.setInt(4, fid2);
          addReservationStatement.setInt(5, 0); // 0 = no stopover
          addReservationStatement.setInt(6, 0); // 0 = unpaid
          addReservationStatement.setInt(7, searchIID.indirectFlightsInfo.price1 +
                  searchIID.indirectFlightsInfo.price2);
          addReservationStatement.setInt(8, 0); // 0 = not cancelled
          addReservationStatement.setInt(9, searchIID.flightDate);
          addReservationStatement.executeUpdate();
          conn.commit();
          conn.setAutoCommit(true);
          return "Booked flight(s), reservation ID: " + currentRid + "\n";
        } else {
          conn.rollback();
          conn.setAutoCommit(true);
          return "booking failed\n";
        }
      }
    } catch (SQLException e) {
      e.printStackTrace();
        if (isDeadLock(e)) {
          return this.transaction_book(itineraryId);
        }
        return "Booking failed3\n";
    } finally {
      checkDanglingTransaction();
    }
  }

  /**
   * Implements the pay function.
   *
   * @param reservationId the reservation to pay for.
   *
   * @return If no user has logged in, then return "Cannot pay, not logged in\n" If the reservation
   *         is not found / not under the logged in user's name, then return "Cannot find unpaid
   *         reservation [reservationId] under user: [username]\n" If the user does not have enough
   *         money in their account, then return "User has only [balance] in account but itinerary
   *         costs [cost]\n" For all other errors, return "Failed to pay for reservation
   *         [reservationId]\n"
   *
   *         If successful, return "Paid reservation: [reservationId] remaining balance:
   *         [balance]\n" where [balance] is the remaining balance in the user's account.
   */
  public String transaction_pay(int reservationId) {
    if (this.username == null) {
      return "Cannot pay, not logged in\n";
    }
    try {
      conn.setAutoCommit(false);
      checkReservationStatement.clearParameters();
      checkReservationStatement.setInt(1, reservationId);
      checkReservationStatement.setString(2, this.username);
      ResultSet existence = checkReservationStatement.executeQuery();
      existence.next();
      if (existence.getInt("count") != 1) {
        existence.close();
        conn.rollback();
        conn.setAutoCommit(true);
        return "Cannot find unpaid reservation " + reservationId
                + " under user: " + this.username + "\n";
      } else {
        existence.close();
        checkUserBalanceStatement.clearParameters();
        checkUserBalanceStatement.setString(1, username);
        ResultSet result = checkUserBalanceStatement.executeQuery();
        result.next();
        int balance = result.getInt("balance");
        result.close();
        ticketPriceStatement.clearParameters();
        ticketPriceStatement.setInt(1, reservationId);
        result =  ticketPriceStatement.executeQuery();
        result.next();
        int total_price = result.getInt("total_price");
        result.close();
        if (total_price > balance) {
          conn.rollback();
          conn.setAutoCommit(true);
          return "User has only " + balance + " in account but itinerary costs " + total_price + "\n";
        } else {
          balance = balance - total_price;
          updateBalanceStatement.clearParameters();
          updateBalanceStatement.setInt(1, balance);
          updateBalanceStatement.setString(2, username);
          updateBalanceStatement.executeUpdate();
          updateReservationStatusStatement.clearParameters();
          updateReservationStatusStatement.setInt(1, 1);
          updateReservationStatusStatement.setInt(2, reservationId);
          updateReservationStatusStatement.executeUpdate();
          conn.commit();
          conn.setAutoCommit(true);
          return "Paid reservation: " + reservationId + " remaining balance: " + balance + "\n";
        }
      }
    } catch (SQLException e) {
        if (isDeadLock(e)) {
          return this.transaction_pay(reservationId);
        }
        return "Failed to pay for reservation " + reservationId + "\n";
    } finally {
      checkDanglingTransaction();
    }
  }

  /**
   * Implements the reservations function.
   *
   * @return If no user has logged in, then return "Cannot view reservations, not logged in\n" If
   *         the user has no reservations, then return "No reservations found\n" For all other
   *         errors, return "Failed to retrieve reservations\n"
   *
   *         Otherwise return the reservations in the following format:
   *
   *         Reservation [reservation ID] paid: [true or false]:\n [flight 1 under the
   *         reservation]\n [flight 2 under the reservation]\n Reservation [reservation ID] paid:
   *         [true or false]:\n [flight 1 under the reservation]\n [flight 2 under the
   *         reservation]\n ...
   *
   *         Each flight should be printed using the same format as in the {@code Flight} class.
   *
   * @see Flight#toString()
   */
  public String transaction_reservations() {
    if (this.username == null) {
      return "Cannot view reservations, not logged in\n";
    }
    StringBuffer sb = new StringBuffer();
    try {
      conn.setAutoCommit(false);
      checkAllReservationStatement.clearParameters();
      checkAllReservationStatement.setString(1, username);
      ResultSet num = checkAllReservationStatement.executeQuery();
      num.next();
      int reservationCount = num.getInt("count");
      num.close();
      if (reservationCount == 0) {
        conn.rollback();
        conn.setAutoCommit(true);
        return "No reservations found\n";
      }
      getReservationStatement.clearParameters();
      getReservationStatement.setString(1, this.username);
      ResultSet result = getReservationStatement.executeQuery();
      while (result.next()) {
        int fid1 = result.getInt("fid1");
        int fid2 = result.getInt("fid2");
        int stopover = result.getInt("stopover");
        int paid_status = result.getInt("paid_status");
        int reservationNum = result.getInt("rid");
        String paid = "false:\n";
        if (paid_status == 1) {
          paid = "true:\n";
        }
        if (stopover == 0) {
          getFlightInfoStatement.clearParameters();
          getFlightInfoStatement.setInt(1, fid1);
          ResultSet flight = getFlightInfoStatement.executeQuery();
          flight.next();
          Flight currFlight = new Flight();
          currFlight.fid = fid1;
          currFlight.flightNum = flight.getString("flight_num");
          currFlight.dayOfMonth = flight.getInt("day_of_month");
          currFlight.carrierId = flight.getString("carrier_id");
          currFlight.originCity = flight.getString("origin_city");
          currFlight.destCity = flight.getString("dest_city");
          currFlight.time = flight.getInt("actual_time");
          currFlight.capacity = flight.getInt("capacity");
          currFlight.price = flight.getInt("price");
          sb.append("Reservation " + reservationNum + " paid: " + paid + currFlight.toString() + "\n");
          flight.close();
        }
        if (stopover == 1) {
          getFlightInfoStatement.clearParameters();
          getFlightInfoStatement.setInt(1, fid1);
          ResultSet flight1 = getFlightInfoStatement.executeQuery();
          flight1.next();
          IndirectFlights flight = new IndirectFlights();
          flight.fid1 = fid1;
          flight.flightNum1 = flight1.getString("flight_num");
          flight.dayOfMonth1 = flight1.getInt("day_of_month");
          flight.carrierId1 = flight1.getString("carrier_id");
          flight.originCity1 = flight1.getString("origin_city");
          flight.destCity1 = flight1.getString("dest_city");
          flight.time1 = flight1.getInt("actual_time");
          flight.capacity1 = flight1.getInt("capacity");
          flight.price1 = flight1.getInt("price");
          flight1.close();
          getFlightInfoStatement.clearParameters();
          getFlightInfoStatement.setInt(1, fid2);
          ResultSet flight2 = getFlightInfoStatement.executeQuery();
          flight2.next();
          flight.fid2 = fid2;
          flight.flightNum2 = flight2.getString("flight_num");
          flight.dayOfMonth2 = flight2.getInt("day_of_month");
          flight.carrierId2 = flight2.getString("carrier_id");
          flight.originCity2 = flight2.getString("origin_city");
          flight.destCity2 = flight2.getString("dest_city");
          flight.time2 = flight2.getInt("actual_time");
          flight.capacity2 = flight2.getInt("capacity");
          flight.price2 = flight2.getInt("price");
          flight2.close();
          sb.append("Reservation " + reservationNum + " paid: " + paid + flight.toString() + "\n");
        }
      }
      result.close();
      conn.commit();
      conn.setAutoCommit(true);
      return sb.toString();
    } catch (SQLException e) {
        if (isDeadLock(e)) {
          return this.transaction_reservations();
        }
        return "Failed to retrieve reservations\n";
    }  finally {
      checkDanglingTransaction();
    }
  }

  /**
   * Implements the cancel operation.
   *
   * @param reservationId the reservation ID to cancel
   *
   * @return If no user has logged in, then return "Cannot cancel reservations, not logged in\n" For
   *         all other errors, return "Failed to cancel reservation [reservationId]\n"
   *
   *         If successful, return "Canceled reservation [reservationId]\n"
   *
   *         Even though a reservation has been canceled, its ID should not be reused by the system.
   */
  public String transaction_cancel(int reservationId) {
    int currentBalance = 0;
    int price = 0;
    if (this.username == null) {
      return "Cannot cancel reservations, not logged in\n";
    }
    try {
      conn.setAutoCommit(false);
      checkExistingReservationStatement.clearParameters();
      checkExistingReservationStatement.setInt(1, reservationId);
      checkExistingReservationStatement.setString(2, username);
      ResultSet existingReservation = checkExistingReservationStatement.executeQuery();
      existingReservation.next();
      if (existingReservation.getInt("count") < 1) {
        existingReservation.close();
        conn.rollback();
        conn.setAutoCommit(true);
        return "Failed to cancel reservation " + reservationId + "\n";
      }
      existingReservation.close();
      cancelReservationStatement.clearParameters();
      cancelReservationStatement.setString(1, username);
      cancelReservationStatement.setInt(2, reservationId);
      cancelReservationStatement.executeUpdate();
      checkPaidStatusStatement.clearParameters();
      checkPaidStatusStatement.setInt(1, reservationId);
      ResultSet result = checkPaidStatusStatement.executeQuery();
      result.next();
      if (result.getInt("paid_status") == 1) {
        checkUserBalanceStatement.clearParameters();
        checkUserBalanceStatement.setString(1, this.username);
        ResultSet balance = checkUserBalanceStatement.executeQuery();
        balance.next();
        currentBalance = balance.getInt("balance");
        balance.close();
        ticketPriceStatement.clearParameters();
        ticketPriceStatement.setInt(1, reservationId);
        ResultSet ticketPrice = ticketPriceStatement.executeQuery();
        ticketPrice.next();
        price = ticketPrice.getInt("price");
        ticketPrice.close();
        updateBalanceStatement.clearParameters();
        updateBalanceStatement.setInt(1, currentBalance + price);
        updateBalanceStatement.setString(2, this.username);
        updateBalanceStatement.executeUpdate();
      }
      conn.commit();
      conn.setAutoCommit(true);
      return "Canceled reservation " + reservationId + "\n";
    } catch (SQLException e) {
      if (isDeadLock(e)) {
        return this.transaction_cancel(reservationId);
      }
      return "Failed to cancel reservation " + reservationId + "\n";
    } finally {
      checkDanglingTransaction();
    }
  }

  /**
   * Throw IllegalStateException if transaction not completely complete, rollback.
   *
   */
  private void checkDanglingTransaction() {
    try {
      try (ResultSet rs = tranCountStatement.executeQuery()) {
        rs.next();
        int count = rs.getInt("tran_count");
        if (count > 0) {
          throw new IllegalStateException(
                  "Transaction not fully commit/rollback. Number of transaction in process: " + count);
        }
      } finally {
        conn.setAutoCommit(true);
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Database error", e);
    }
  }

  private static boolean isDeadLock(SQLException ex) {
    return ex.getErrorCode() == 1205;
  }

  /**
   * A class to store flight information.
   */
  class Flight {
    public int fid;
    public int dayOfMonth;
    public String carrierId;
    public String flightNum;
    public String originCity;
    public String destCity;
    public int time;
    public int capacity;
    public int price;

    @Override
    public String toString() {
      return "ID: " + fid + " Day: " + dayOfMonth + " Carrier: " + carrierId + " Number: "
              + flightNum + " Origin: " + originCity + " Dest: " + destCity + " Duration: " + time
              + " Capacity: " + capacity + " Price: " + price;
    }
  }

  class IndirectFlights {
    public int fid1;
    public int dayOfMonth1;
    public String carrierId1;
    public String flightNum1;
    public String originCity1;
    public String destCity1;
    public int time1;
    public int capacity1;
    public int price1;
    public int fid2;
    public int dayOfMonth2;
    public String carrierId2;
    public String flightNum2;
    public String originCity2;
    public String destCity2;
    public int time2;
    public int capacity2;
    public int price2;
    public int totalTime;

    @Override
    public String toString() {
      return "ID: " + fid1 + " Day: " + dayOfMonth1 + " Carrier: " + carrierId1 + " Number: "
              + flightNum1 + " Origin: " + originCity1 + " Dest: " + destCity1 + " Duration: " + time1
              + " Capacity: " + capacity1 + " Price: " + price1 + "\n" +
              "ID: " + fid2 + " Day: " + dayOfMonth2 + " Carrier: " + carrierId2 + " Number: "
              + flightNum2 + " Origin: " + originCity2 + " Dest: " + destCity2 + " Duration: " + time2
              + " Capacity: " + capacity2 + " Price: " + price2;
    }
  }

  class SearchResult {
    public boolean direct;
    public int flightDate;
    public int itineraryID;
    public Flight directFlightInfo;
    public IndirectFlights indirectFlightsInfo;

    public SearchResult(boolean direct, int flightDate, int itineraryID, Flight directFlightInfo,
                        IndirectFlights indirectFlightsInfo) {
      this.direct = direct;
      this.flightDate = flightDate;
      this.itineraryID = itineraryID;
      this.directFlightInfo = directFlightInfo;
      this.indirectFlightsInfo = indirectFlightsInfo;
    }
  }
}
