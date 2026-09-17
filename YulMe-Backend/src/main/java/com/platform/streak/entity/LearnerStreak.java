package com.platform.streak.entity;
import com.platform.common.entity.AuditableEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;
@Entity @Table(name="learner_streaks")
public class LearnerStreak extends AuditableEntity {
 @Column(name="child_id",nullable=false,unique=true,updatable=false) private UUID childId;
 @Column(name="current_streak",nullable=false) private int currentStreak;
 @Column(name="longest_streak",nullable=false) private int longestStreak;
 @Column(name="last_activity_date") private LocalDate lastActivityDate;
 protected LearnerStreak(){}
 private LearnerStreak(UUID childId){this.childId=childId;}
 public static LearnerStreak create(UUID childId){return new LearnerStreak(childId);}
 public void record(LocalDate date){
   if(lastActivityDate==null){currentStreak=1;}
   else if(lastActivityDate.equals(date)) return;
   else if(lastActivityDate.plusDays(1).equals(date)) currentStreak++;
   else currentStreak=1;
   lastActivityDate=date; longestStreak=Math.max(longestStreak,currentStreak);
 }
 public UUID getChildId(){return childId;} public int getCurrentStreak(){return currentStreak;} public int getLongestStreak(){return longestStreak;} public LocalDate getLastActivityDate(){return lastActivityDate;}
}
