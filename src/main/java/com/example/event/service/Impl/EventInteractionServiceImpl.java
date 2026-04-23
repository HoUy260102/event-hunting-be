package com.example.event.service.Impl;

import com.example.event.constant.ErrorCode;
import com.example.event.constant.InteractionType;
import com.example.event.entity.Event;
import com.example.event.entity.EventInteraction;
import com.example.event.entity.User;
import com.example.event.exception.AppException;
import com.example.event.projection.EventTrendingProjection;
import com.example.event.repository.EventInteractionRepository;
import com.example.event.repository.EventRepository;
import com.example.event.repository.UserRepository;
import com.example.event.service.EventInteractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventInteractionServiceImpl implements EventInteractionService {
    private final EventInteractionRepository interactionRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void addInteraction(String eventId, String userId, InteractionType type) {
        Event event = Optional.ofNullable(eventRepository.findEventById(eventId))
            .orElseThrow(() -> new AppException(ErrorCode.EVENT_NOT_FOUND));

        User user = Optional.ofNullable(userRepository.findUserById(userId))
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // VIEW — chỉ lưu 1 lần duy nhất
        if (type == InteractionType.VIEW) {
            boolean alreadyViewed = interactionRepository
                    .existsByEvent_IdAndUser_IdAndType(eventId, userId, type);
            if (alreadyViewed) return;
        }

        // FAVORITE — toggle (bấm lần 2 thì xóa)
        if (type == InteractionType.FAVORITE) {
            boolean alreadyFavorited = interactionRepository
                    .existsByEvent_IdAndUser_IdAndType(eventId, userId, type);
            if (alreadyFavorited) {
                interactionRepository.deleteByEvent_IdAndUser_IdAndType(eventId, userId, type);
                return;
            }
        }

        EventInteraction interaction = new EventInteraction();
        interaction.setEvent(event);
        interaction.setType(type);
        interaction.setUser(user);
        interactionRepository.save(interaction);
    }

    @Override
    public List<EventTrendingProjection> getTopTrendingEvents() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        return interactionRepository.findTrendingEvents(since, 10);
    }
}
