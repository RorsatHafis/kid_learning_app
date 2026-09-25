package com.platform.challenge.entity;
import com.platform.common.entity.AuditableEntity; import jakarta.persistence.*; import java.time.Instant; import java.util.UUID; import org.springframework.util.Assert;
@Entity @Table(name="challenges") public class Challenge extends AuditableEntity {
 @Column(name="owner_account_id",nullable=false,updatable=false) private UUID ownerAccountId;
 @Column(name="activity_version_id",nullable=false,updatable=false) private UUID activityVersionId;
 @Column(name="title",nullable=false,length=200) private String title;
 @Column(name="description",columnDefinition="text") private String description;
 @Column(name="target_completions",nullable=false) private int targetCompletions;
 @Column(name="starts_at",nullable=false) private Instant startsAt; @Column(name="ends_at") private Instant endsAt;
 @Column(name="status",nullable=false,length=20) private String status;
 protected Challenge(){}
 private Challenge(UUID owner,UUID activity,String title,String description,int target,Instant starts,Instant ends){this.ownerAccountId=owner;this.activityVersionId=activity;this.title=title;this.description=description;this.targetCompletions=target;this.startsAt=starts;this.endsAt=ends;this.status="ACTIVE";}
 public static Challenge create(UUID owner,UUID activity,String title,String description,int target,Instant starts,Instant ends){Assert.notNull(owner,"ownerAccountId must not be null");Assert.notNull(activity,"activityVersionId must not be null");Assert.hasText(title,"title must not be blank");Assert.isTrue(target>0,"targetCompletions must be positive");Assert.notNull(starts,"startsAt must not be null");Assert.isTrue(ends==null||ends.isAfter(starts),"endsAt must be after startsAt");return new Challenge(owner,activity,title,description,target,starts,ends);}
 public UUID getOwnerAccountId(){return ownerAccountId;} public UUID getActivityVersionId(){return activityVersionId;} public String getTitle(){return title;} public String getDescription(){return description;} public int getTargetCompletions(){return targetCompletions;} public Instant getStartsAt(){return startsAt;} public Instant getEndsAt(){return endsAt;} public String getStatus(){return status;}
}
