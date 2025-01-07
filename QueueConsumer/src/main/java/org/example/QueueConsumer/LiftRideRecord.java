package org.example.QueueConsumer;

public class LiftRideRecord {
  private final int time;
  private final int liftId;
  private final int resortId;
  private final String seasonId;
  private final String dayId;
  private final int skierId;

  public LiftRideRecord(int time, int liftId, int resortId, String seasonId, String dayId, int skierId) {
    this.time = time;
    this.liftId = liftId;
    this.resortId = resortId;
    this.seasonId = seasonId;
    this.dayId = dayId;
    this.skierId = skierId;
  }

  @Override
  public String toString() {
    return "LiftRideRecord{" +
        "time=" + time +
        ", liftId=" + liftId +
        ", resortId=" + resortId +
        ", seasonId='" + seasonId + '\'' +
        ", dayId='" + dayId + '\'' +
        ", skierId=" + skierId +
        '}';
  }
}

