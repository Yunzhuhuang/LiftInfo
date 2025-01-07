import com.google.gson.Gson;
import java.io.IOException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(value = "/skiers/*")
public class SkierServlet extends HttpServlet {
  private static final String QUEUE_NAME = "liftRides";
  private static final String RABBITMQ_HOST = "35.160.166.15";
  private static final int CHANNEL_POOL_SIZE = 200;

  private Connection connection;
  private BlockingQueue<Channel> channelPool;

  @Override
  public void init(ServletConfig config) throws ServletException {
    try {
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost(RABBITMQ_HOST);
      factory.setUsername("clara");
      factory.setPassword("Hyzh990615");
      connection = factory.newConnection();

      channelPool = new ArrayBlockingQueue<>(CHANNEL_POOL_SIZE);
      for (int i = 0; i < CHANNEL_POOL_SIZE; i++) {
        Channel channel = connection.createChannel();
        channel.queueDeclare(QUEUE_NAME, true, false, false, null);
        channelPool.add(channel);
      }
    } catch (Exception e) {
      throw new ServletException("Failed to initialize RabbitMQ connection and channel pool", e);
    }
  }

  @Override
  public void destroy() {
    try {
      for (Channel channel : channelPool) {
        channel.close();
      }
      if (connection != null) {
        connection.close();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
    res.setContentType("application/json");
    String urlPath = req.getPathInfo();

    if (urlPath == null || urlPath.isEmpty()) {
      res.setStatus(HttpServletResponse.SC_NOT_FOUND);
      res.getWriter().write("missing url");
      return;
    }
    String[] urlParts = urlPath.split("/");
    if (urlParts.length != 8) {
      res.setStatus(HttpServletResponse.SC_NOT_FOUND);
      res.getWriter().write("missing paramterers");
      return;
    }

    if(!isParameterValid(urlParts)) {
      res.setStatus(HttpServletResponse.SC_NOT_FOUND);
      res.getWriter().write("parameter is invalid");
      return;
    }

    Gson gson = new Gson();
    StringBuilder sb;
    try {
      sb = new StringBuilder();
      String s;
      while ((s = req.getReader().readLine()) != null) {
        sb.append(s);
      }
    } catch (Exception e) {
      res.setStatus(HttpServletResponse.SC_NOT_FOUND);
      res.getWriter().write("Invalid JSON payload");
      return;
    }
    HashMap<String, Object> liftRideData = gson.fromJson(sb.toString(), HashMap.class);
    liftRideData.put("skierId", urlParts[7]);
    liftRideData.put("resortId", urlParts[1]);
    liftRideData.put("seasonId", urlParts[3]);
    liftRideData.put("dayId", urlParts[5]);

    Channel channel = null;
    try {
        channel = channelPool.take();
        channel.basicPublish("", QUEUE_NAME, null, liftRideData.toString().getBytes(StandardCharsets.UTF_8));
    } catch (InterruptedException e) {
      res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } finally {
      if (channel != null) {
        try {
          channelPool.offer(channel);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }

    res.setStatus(HttpServletResponse.SC_CREATED);
    res.getOutputStream().print("success");
    res.getOutputStream().flush();
  }

  private boolean isParameterValid(String[] urlPath) {
    String resortId = urlPath[1];
    String seasonId = urlPath[3];
    String dayId = urlPath[5];
    String skierId = urlPath[7];
    if (resortId.isEmpty() || seasonId.isEmpty() || dayId.isEmpty() || skierId.isEmpty()) {
      return false;
    }
    try {
      Integer.parseInt(resortId);
      Integer.parseInt(skierId);
    } catch (NumberFormatException e) {
      return false;
    }
    try {
      int day = Integer.parseInt(dayId);
      if (day < 1 || day > 366) {
        return false;
      }
    } catch (NumberFormatException e) {
      return false;
    }
    if (!urlPath[0].isEmpty() || !urlPath[2].equals("seasons") || !urlPath[4].equals("days")
    || !urlPath[6].equals("skiers")) {
      return false;
    }
    return true;
  }
}
