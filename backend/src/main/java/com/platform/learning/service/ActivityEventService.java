package com.platform.learning.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.learning.entity.ActivityEvent;
import com.platform.learning.repository.ActivityAttemptRepository;
import com.platform.learning.repository.ActivityEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ActivityEventService {

    private final ActivityEventRepository repository;
    private final ActivityAttemptRepository activityAttemptRepository;

    public ActivityEventService(ActivityEventRepository repository, ActivityAttemptRepository activityAttemptRepository) {
        this.repository = repository;
        this.activityAttemptRepository = activityAttemptRepository;
    }

    @Transactional
    public ActivityEvent record(UUID activityAttemptId, String eventType, Map<String, Object> payload) {
        if (!activityAttemptRepository.existsById(activityAttemptId)) {
            throw new ResourceNotFoundException("ActivityAttempt", activityAttemptId);
        }
        return repository.save(ActivityEvent.record(activityAttemptId, eventType, payload));
    }

    @Transactional(readOnly = true)
    public List<ActivityEvent> listForAttempt(UUID activityAttemptId) {
        return repository.findByActivityAttemptIdOrderByOccurredAtAsc(activityAttemptId);
    }

}