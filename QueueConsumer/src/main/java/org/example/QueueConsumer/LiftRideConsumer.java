package org.example.QueueConsumer;

import com.rabbitmq.client.*;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class LiftRideConsumer {
  private static final Logger logger = Logger.getLogger(LiftRideConsumer.class.getName());
  private static final String QUEUE_NAME = "liftRides";
  private static final String RABBITMQ_HOST = "35.160.166.15";
  private static final String DB_URL = "jdbc:mysql://database-ski.cwlezssx9amb.us-west-2.rds.amazonaws.com:3306/skiers_db";
  private static final String DB_USERNAME = "admin";
  private static final String DB_PASSWORD = "8f<5w4Nwi(PHU.dwv5Yb[3ir2f71";
  private static final ConcurrentHashMap<Integer, List<LiftRideRecord>> skierRides = new ConcurrentHashMap<>();
  private static final int THREAD_NUMBER = 300;

  private static HikariDataSource dataSource;

  public static void main(String[] args) throws Exception {
    initDataSource();
    LiftRideConsumer consumer = new LiftRideConsumer();
    consumer.startConsumers();
  }

  private static void initDataSource() {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(DB_URL);
    config.setUsername(DB_USERNAME);
    config.setPassword(DB_PASSWORD);
    config.setDriverClassName("com.mysql.cj.jdbc.Driver");
    config.setMaximumPoolSize(THREAD_NUMBER); // Match thread count
    config.setMinimumIdle(10);
    config.setIdleTimeout(30000);
    config.setConnectionTimeout(30000);
    config.setLeakDetectionThreshold(15000);

    dataSource = new HikariDataSource(config);
  }

  public void startConsumers() throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(RABBITMQ_HOST);
    factory.setUsername("clara");
    factory.setPassword("Hyzh990615");
    Connection connection = factory.newConnection();

    // Create a thread pool for consumers
    ExecutorService executor = Executors.newFixedThreadPool(THREAD_NUMBER); // Adjust the pool size as needed

    // Start multiple consumer threads
    for (int i = 0; i < THREAD_NUMBER; i++) {
      executor.submit(() -> {
        try {
          Channel channel = connection.createChannel();
          channel.queueDeclare(QUEUE_NAME, true, false, false, null);

          // Consume messages from the queue
          channel.basicConsume(QUEUE_NAME, true, (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            processMessage(message);
          }, consumerTag -> {});
        } catch (IOException e) {
          e.printStackTrace();
        }
      });
    }
  }

  // Process each message and update the ConcurrentHashMap
  private void processMessage(String message) {
    try (java.sql.Connection conn = dataSource.getConnection()){
      // Parse JSON to HashMap
      message = message.substring(1, message.length() - 1);
      String[] pairs = message.split(", ");
      String[] days = pairs[4].split("=");
      String[] seasons = pairs[2].split("=");
      String[] resorts = pairs[3].split("=");
      String[] skier = pairs[0].split("=");
      String[] lifts = pairs[1].split("=");
      String[] times = pairs[5].split("=");

      int skierId = Integer.parseInt(skier[1]);
      int liftId = (int) Double.parseDouble(lifts[1]);
      int time = (int)Double.parseDouble(times[1]);
      int resortId = Integer.parseInt(resorts[1]);
      String seasonId = seasons[1];
      String dayId = days[1];
      String insertSQL = "INSERT INTO SkiData (ResortID, SeasonID, DayID, SkierID, Time, LiftID) " +
          "VALUES (?, ?, ?, ?, ?, ?)";
      PreparedStatement stmt = conn.prepareStatement(insertSQL);
      stmt.setInt(1, resortId);
      stmt.setString(2, seasonId);
      stmt.setString(3, dayId);
      stmt.setInt(4, skierId);
      stmt.setInt(5, time);
      stmt.setInt(6, liftId);
      stmt.executeUpdate();

      logger.info("Record inserted for SkierID: " + skierId);
      //LiftRideRecord record = new LiftRideRecord(time, liftId, resortId, seasonId, dayId, skierId);
      //skierRides.computeIfAbsent(skierId, k -> new ArrayList<>()).add(record);
    } catch (SQLException e) {
      logger.severe("Database error: " + e.getMessage());
    } catch (Exception e) {
      logger.severe("Error processing message: " + e.getMessage());
    }

  }

}
